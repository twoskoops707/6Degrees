package com.twoskoops707.sixdegrees.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.data.AppSettings
import com.twoskoops707.sixdegrees.data.repository.SearchProgressEvent
import com.twoskoops707.sixdegrees.databinding.FragmentSearchProgressBinding
import com.twoskoops707.sixdegrees.databinding.ItemSearchSourceBinding
import com.twoskoops707.sixdegrees.ui.common.InvestigationPipelineView
import com.twoskoops707.sixdegrees.ui.common.InvestigationStep
import com.twoskoops707.sixdegrees.domain.SearchPhase
import com.twoskoops707.sixdegrees.domain.SubjectSearchOrchestrator
import com.twoskoops707.sixdegrees.domain.model.CandidateProfile
import com.twoskoops707.sixdegrees.domain.model.SubjectProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SearchProgressFragment : Fragment() {

    private var _binding: FragmentSearchProgressBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SearchProgressViewModel
    private val sourceRows = mutableListOf<SourceRow>()
    private lateinit var adapter: SourceAdapter

    private var hitCount = 0
    private var checkedCount = 0
    private var completedReportId: String? = null
    private var pendingCandidates: List<CandidateProfile>? = null
    private var pendingCandidatesRound: Int = 1
    private var searchStartMs = 0L
    private var estimatedTotal = 0
    private var elapsedJob: Job? = null
    private var partialReportId: String? = null
    private var searchComplete = false
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private var currentType = "person"
    private var currentDisplayQuery = ""
    private var investigatorMode = false
    private var currentRound = 1

    private fun normalizedSearchType(): String = when (currentType) {
        "scan" -> "person"
        else -> currentType
    }

    data class SourceRow(
        val source: String,
        var state: State,
        var detail: String = ""
    ) {
        enum class State { CHECKING, FOUND, NOT_FOUND, FAILED, BLOCKED, SKIPPED }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        investigatorMode = AppSettings.isInvestigatorMode(requireContext())
        if (investigatorMode) {
            InvestigationPipelineView.bind(binding.root, InvestigationStep.COLLECT)
        } else {
            binding.root.findViewById<View>(R.id.pipeline_include)?.visibility = View.GONE
        }

        val rawQuery = arguments?.getString("query") ?: ""
        val type = arguments?.getString("type") ?: "person"
        val round = arguments?.getInt("round") ?: 1
        val displayQuery = arguments?.getString("searchQuery")?.takeIf { it.isNotBlank() } ?: rawQuery

        searchStartMs = System.currentTimeMillis()
        currentRound = round
        val profile = SubjectProfile.fromFields(parseDisplayFields(rawQuery))
        val phase = SubjectSearchOrchestrator.resolvePhase(type, round, profile)
        estimatedTotal = SubjectSearchOrchestrator.estimatedSourceCount(type, phase, profile)

        currentType = type
        currentDisplayQuery = displayQuery
        binding.tvPhase.text = getString(R.string.progress_deep_investigation)
        startElapsedTimer()
        val cleanDisplayQuery = displayQuery.split("|").joinToString(", ") { part ->
            val eqIdx = part.indexOf('=')
            if (eqIdx != -1) part.substring(eqIdx + 1).trim() else part.trim()
        }.replace(Regex(",\\s*,"), ",").trim().trimEnd(',')
        binding.tvSearchQuery.text = if (round > 1) "Round $round: $cleanDisplayQuery" else cleanDisplayQuery
        if (investigatorMode) {
            binding.chipSearchType.visibility = View.VISIBLE
            binding.chipSearchType.text = when {
                type == "scan" && round == 1 -> "PHASE 1 · DISCOVERY"
                type == "comprehensive" || round > 1 -> "PHASE 2 · DEEP INVESTIGATION"
                type == "phone" || type == "email" -> "DEEP INVESTIGATION"
                else -> type.uppercase()
            }
            binding.searchProgressToolbar.title = getString(R.string.progress_collect_title_pro)
        } else {
            applySimpleProgressUi()
        }

        viewModel = ViewModelProvider(
            this,
            SearchProgressViewModel.Factory(requireActivity().application, rawQuery, type, round, displayQuery)
        )[SearchProgressViewModel::class.java]

        adapter = SourceAdapter()
        binding.rvSources.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SearchProgressFragment.adapter
            isNestedScrollingEnabled = false
            itemAnimator = null
        }

        binding.btnPartialResults.setOnClickListener {
            navigateToResults(partialReportId ?: completedReportId)
        }

        binding.fabViewReport.setOnClickListener {
            if (!isAdded || !isResumed) return@setOnClickListener
            val nav = findNavController()
            if (nav.currentDestination?.id != R.id.nav_search_progress) return@setOnClickListener
            val candidates = pendingCandidates
            if (candidates != null) {
                val listType = Types.newParameterizedType(List::class.java, CandidateProfile::class.java)
                val json = try {
                    moshi.adapter<List<CandidateProfile>>(listType).toJson(candidates)
                } catch (_: Exception) { "[]" }
                try {
                    nav.navigate(
                        R.id.action_progress_to_candidates,
                        Bundle().apply {
                            putString("candidatesJson", json)
                            putString("reportId", completedReportId ?: "")
                            putInt("round", pendingCandidatesRound)
                            putString("searchQuery", displayQuery)
                        }
                    )
                } catch (_: Exception) {}
            } else {
                navigateToResults(completedReportId)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collect { event ->
                handleEvent(event)
            }
        }

        viewModel.startSearch()
    }

    private fun parseDisplayFields(rawQuery: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        rawQuery.split("|").forEach { part ->
            val eq = part.indexOf("=")
            if (eq > 0) {
                val k = part.substring(0, eq).trim()
                val v = part.substring(eq + 1).trim()
                if (v.isNotBlank()) result[k] = v
            }
        }
        return result
    }

    private fun startElapsedTimer() {
        elapsedJob?.cancel()
        elapsedJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                updateElapsed()
                delay(1_000)
            }
        }
    }

    private fun updateElapsed() {
        val elapsedSec = ((System.currentTimeMillis() - searchStartMs) / 1000).toInt()
        val mins = elapsedSec / 60
        val secs = elapsedSec % 60
        binding.tvElapsed.text = getString(R.string.progress_elapsed, mins, secs)
    }

    private fun navigateToResults(reportId: String?) {
        if (!isAdded || !isResumed) return
        val id = reportId ?: return
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.nav_search_progress) return
        try {
            nav.navigate(
                R.id.action_progress_to_results,
                Bundle().apply {
                    putString("searchQuery", currentDisplayQuery)
                    putString("searchType", normalizedSearchType())
                    putString("reportId", id)
                }
            )
        } catch (_: Exception) {}
    }

    private fun handleEvent(event: SearchProgressEvent) {
        if (_binding == null) return
        when (event) {
            is SearchProgressEvent.PhaseUpdate -> {
                if (investigatorMode) {
                    binding.tvPhase.text = when (event.phase.lowercase()) {
                        "discovery" -> getString(R.string.progress_phase_discovery)
                        "deep scan", "secondary sweep" -> getString(R.string.progress_phase_deep)
                        "dark web" -> getString(R.string.progress_phase_dark)
                        "ai brief" -> getString(R.string.progress_phase_ai)
                        else -> event.detail.ifBlank { getString(R.string.progress_deep_investigation) }
                    }
                    if (event.detail.isNotBlank() && event.phase.lowercase() !in setOf("discovery", "deep scan")) {
                        binding.tvStatus.text = event.detail
                    }
                } else {
                    binding.tvStatus.text = getString(R.string.progress_simple_status)
                }
            }
            is SearchProgressEvent.PartialResultsReady -> {
                partialReportId = event.reportId
                if (!searchComplete && investigatorMode) {
                    binding.btnPartialResults.visibility = View.VISIBLE
                }
            }
            is SearchProgressEvent.Checking -> {
                if (investigatorMode) {
                    val existing = sourceRows.indexOfFirst { it.source == event.source }
                    if (existing == -1) {
                        sourceRows.add(SourceRow(event.source, SourceRow.State.CHECKING))
                        adapter.notifyItemInserted(sourceRows.lastIndex)
                        binding.rvSources.smoothScrollToPosition(sourceRows.lastIndex)
                    }
                    binding.tvStatus.text = "Checking ${event.source}…"
                } else {
                    binding.tvStatus.text = getString(R.string.progress_simple_status)
                }
            }
            is SearchProgressEvent.Found -> {
                hitCount++
                if (investigatorMode) updateSourceRow(event.source, SourceRow.State.FOUND, event.detail)
                checkedCount++
                updateCounts()
                if (!investigatorMode && hitCount > 0) {
                    binding.tvStatus.text = getString(R.string.progress_simple_found)
                }
            }
            is SearchProgressEvent.NotFound -> {
                if (investigatorMode) updateSourceRow(event.source, SourceRow.State.NOT_FOUND)
                checkedCount++
                updateCounts()
            }
            is SearchProgressEvent.Failed -> {
                if (event.source == "Search") {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEta.text = ""
                    binding.tvStatus.text = "Search failed: ${event.reason}"
                    binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.score_red))
                    Toast.makeText(requireContext(), event.reason, Toast.LENGTH_LONG).show()
                } else {
                    if (investigatorMode) updateSourceRow(event.source, SourceRow.State.FAILED, event.reason)
                    checkedCount++
                    updateCounts()
                }
            }
            is SearchProgressEvent.Blocked -> {
                if (investigatorMode) updateSourceRow(event.source, SourceRow.State.BLOCKED, event.reason)
                checkedCount++
                updateCounts()
            }
            is SearchProgressEvent.Skipped -> {
                if (investigatorMode) updateSourceRow(event.source, SourceRow.State.SKIPPED, event.reason)
                checkedCount++
                updateCounts()
            }
            is SearchProgressEvent.CandidatesReady -> {
                completedReportId = event.reportId
                pendingCandidatesRound = event.round
                binding.progressBar.visibility = View.GONE
                val elapsedSec = ((System.currentTimeMillis() - searchStartMs) / 1000).toInt()
                if (event.autoSelect && event.refinedQuery.isNotBlank()) {
                    binding.tvStatus.text = "1 match found — deepening investigation…"
                    binding.tvEta.text = ""
                    binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_cyan))
                    if (!isAdded || !isResumed) return
                    val nav = findNavController()
                    if (nav.currentDestination?.id != R.id.nav_search_progress) return
                    try {
                    nav.navigate(
                        R.id.action_progress_to_progress,
                        Bundle().apply {
                            putString("query", event.refinedQuery)
                            putString("type", "comprehensive")
                            putInt("round", event.round + 1)
                            putString("searchQuery", currentDisplayQuery)
                        }
                    )
                    } catch (_: Exception) {}
                } else {
                    pendingCandidates = event.candidates
                    val withPhotos = event.candidates.count { it.allPhotoUrls().isNotEmpty() }
                    binding.tvStatus.text = if (investigatorMode) {
                        "${event.candidates.size} people in your area · ${withPhotos} with photos · ${elapsedSec}s"
                    } else {
                        getString(R.string.progress_simple_complete) + " — ${event.candidates.size} possible match${if (event.candidates.size != 1) "es" else ""}"
                    }
                    binding.tvEta.text = ""
                    binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_cyan))
                    binding.fabViewReport.text = if (investigatorMode) {
                        "Choose person (${event.candidates.size})"
                    } else {
                        getString(R.string.progress_simple_choose)
                    }
                    binding.fabViewReport.apply {
                        visibility = View.VISIBLE
                        alpha = 0f
                        animate().alpha(1f).setDuration(400).start()
                    }
                }
            }
            is SearchProgressEvent.BrowserToolsReady -> { /* in-app scraping handles these; no external browser */ }
            is SearchProgressEvent.Complete -> {
                searchComplete = true
                completedReportId = event.reportId
                binding.progressBar.visibility = View.GONE
                binding.btnPartialResults.visibility = View.GONE
                val elapsedSec = ((System.currentTimeMillis() - searchStartMs) / 1000).toInt()
                binding.tvEta.text = ""
                binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
                if (investigatorMode) {
                    val suffix = if (hitCount != 1) "s" else ""
                    binding.tvStatus.text = getString(R.string.progress_findings, hitCount, suffix) +
                        " · ${elapsedSec / 60}m ${elapsedSec % 60}s"
                    binding.tvPhase.text = getString(R.string.progress_phase_ai)
                    binding.fabViewReport.text = "View Full Report"
                } else {
                    binding.tvStatus.text = getString(R.string.progress_simple_complete) +
                        " — ${hitCount} result${if (hitCount != 1) "s" else ""}"
                    binding.fabViewReport.text = getString(R.string.progress_simple_open_dossier)
                }
                binding.fabViewReport.apply {
                    visibility = View.VISIBLE
                    alpha = 0f
                    animate().alpha(1f).setDuration(400).start()
                }
                if (!investigatorMode) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        delay(1_200)
                        navigateToResults(completedReportId)
                    }
                }
            }
        }
    }

    private fun applySimpleProgressUi() {
        binding.chipSearchType.visibility = View.GONE
        binding.layoutCheckedColumn.visibility = View.GONE
        binding.statsDivider.visibility = View.GONE
        binding.tvFoundLabel.text = getString(R.string.progress_simple_complete)
        binding.tvSourceLogHeader.visibility = View.GONE
        binding.rvSources.visibility = View.GONE
        binding.tvPhase.visibility = View.GONE
        binding.tvEta.visibility = View.GONE
        binding.btnPartialResults.visibility = View.GONE
        binding.tvStatus.text = getString(R.string.progress_simple_status)
        binding.searchProgressToolbar.title = getString(R.string.progress_collect_title)
    }

    private fun updateSourceRow(source: String, state: SourceRow.State, detail: String = "") {
        val idx = sourceRows.indexOfFirst { it.source == source }
        if (idx != -1) {
            sourceRows[idx].state = state
            sourceRows[idx].detail = detail
            adapter.notifyItemChanged(idx)
        } else {
            sourceRows.add(SourceRow(source, state, detail))
            adapter.notifyItemInserted(sourceRows.lastIndex)
            binding.rvSources.smoothScrollToPosition(sourceRows.lastIndex)
        }
    }

    private fun updateCounts() {
        if (investigatorMode) {
            val suffix = if (hitCount != 1) "s" else ""
            binding.tvFoundCount.text = getString(R.string.progress_findings, hitCount, suffix)
            val total = maxOf(estimatedTotal, sourceRows.size)
            binding.tvCheckedCount.text = getString(R.string.progress_sources_checked, checkedCount, total)
            val elapsedMs = System.currentTimeMillis() - searchStartMs
            val minMs = SubjectSearchOrchestrator.minimumDurationMs(
                if (currentRound > 1 || currentType == "comprehensive") SearchPhase.DEEP_INVESTIGATION
                else SearchPhase.CANDIDATE_DISCOVERY,
                fastMode = !investigatorMode
            )
            if (checkedCount > 0 && checkedCount < total && elapsedMs < minMs) {
                val remainingMs = (minMs - elapsedMs).coerceAtLeast(0)
                val etaMin = (remainingMs / 60_000).toInt()
                val etaSec = ((remainingMs % 60_000) / 1000).toInt()
                binding.tvEta.text = if (etaMin > 0) "~${etaMin}m ${etaSec}s left" else "~${etaSec}s left"
            } else if (checkedCount < total) {
                binding.tvEta.text = "Sweeping sources…"
            }
        } else {
            binding.tvFoundCount.text = hitCount.toString()
        }
    }

    override fun onDestroyView() {
        elapsedJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    inner class SourceAdapter : RecyclerView.Adapter<SourceAdapter.VH>() {

        inner class VH(val b: ItemSearchSourceBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemSearchSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = sourceRows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = sourceRows[position]
            holder.b.sourceName.text = row.source

            when (row.state) {
                SourceRow.State.CHECKING -> {
                    holder.b.sourceSpinner.visibility = View.VISIBLE
                    holder.b.sourceIcon.visibility = View.GONE
                    holder.b.sourceDetail.visibility = View.GONE
                    holder.b.sourceBadge.visibility = View.GONE
                    (holder.itemView as? com.google.android.material.card.MaterialCardView)
                        ?.strokeColor = ContextCompat.getColor(requireContext(), R.color.border)
                }
                SourceRow.State.FOUND -> {
                    holder.b.sourceSpinner.visibility = View.GONE
                    holder.b.sourceIcon.visibility = View.VISIBLE
                    holder.b.sourceIcon.setImageResource(R.drawable.ic_check)
                    if (row.detail.isNotBlank()) {
                        holder.b.sourceDetail.text = row.detail
                        holder.b.sourceDetail.visibility = View.VISIBLE
                    } else {
                        holder.b.sourceDetail.visibility = View.GONE
                    }
                    holder.b.sourceBadge.visibility = View.VISIBLE
                    holder.b.sourceBadge.text = "HIT"
                    holder.b.sourceBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
                    holder.b.sourceBadge.setBackgroundResource(0)
                    (holder.itemView as? com.google.android.material.card.MaterialCardView)
                        ?.strokeColor = ContextCompat.getColor(requireContext(), R.color.success)
                }
                SourceRow.State.NOT_FOUND -> {
                    holder.b.sourceSpinner.visibility = View.GONE
                    holder.b.sourceIcon.visibility = View.VISIBLE
                    holder.b.sourceIcon.setImageResource(R.drawable.ic_minus_circle)
                    holder.b.sourceDetail.visibility = View.GONE
                    holder.b.sourceBadge.visibility = View.GONE
                    (holder.itemView as? com.google.android.material.card.MaterialCardView)
                        ?.strokeColor = ContextCompat.getColor(requireContext(), R.color.border)
                }
                SourceRow.State.FAILED -> {
                    holder.b.sourceSpinner.visibility = View.GONE
                    holder.b.sourceIcon.visibility = View.VISIBLE
                    holder.b.sourceIcon.setImageResource(R.drawable.ic_close_circle)
                    if (row.detail.isNotBlank()) {
                        holder.b.sourceDetail.text = row.detail
                        holder.b.sourceDetail.visibility = View.VISIBLE
                    } else {
                        holder.b.sourceDetail.visibility = View.GONE
                    }
                    holder.b.sourceBadge.visibility = View.GONE
                    (holder.itemView as? com.google.android.material.card.MaterialCardView)
                        ?.strokeColor = ContextCompat.getColor(requireContext(), R.color.border)
                }
                SourceRow.State.BLOCKED -> {
                    holder.b.sourceSpinner.visibility = View.GONE
                    holder.b.sourceIcon.visibility = View.VISIBLE
                    holder.b.sourceIcon.setImageResource(R.drawable.ic_close_circle)
                    holder.b.sourceDetail.text = "Blocked"
                    holder.b.sourceDetail.visibility = View.VISIBLE
                    holder.b.sourceBadge.visibility = View.VISIBLE
                    holder.b.sourceBadge.text = "BLOCKED"
                    holder.b.sourceBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.score_red))
                    holder.b.sourceBadge.setBackgroundResource(0)
                    (holder.itemView as? com.google.android.material.card.MaterialCardView)
                        ?.strokeColor = ContextCompat.getColor(requireContext(), R.color.border)
                }
                SourceRow.State.SKIPPED -> {
                    holder.b.sourceSpinner.visibility = View.GONE
                    holder.b.sourceIcon.visibility = View.VISIBLE
                    holder.b.sourceIcon.setImageResource(R.drawable.ic_minus_circle)
                    holder.b.sourceDetail.text = "Skipped (${row.detail})"
                    holder.b.sourceDetail.visibility = View.VISIBLE
                    holder.b.sourceBadge.visibility = View.GONE
                    (holder.itemView as? com.google.android.material.card.MaterialCardView)
                        ?.strokeColor = ContextCompat.getColor(requireContext(), R.color.border)
                }
            }
        }
    }
}
