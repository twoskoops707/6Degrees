package com.twoskoops707.sixdegrees.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.twoskoops707.sixdegrees.data.repository.SearchProgressEvent
import com.twoskoops707.sixdegrees.databinding.FragmentSearchProgressBinding
import com.twoskoops707.sixdegrees.databinding.ItemSearchSourceBinding
import com.twoskoops707.sixdegrees.domain.model.CandidateProfile
import kotlinx.coroutines.launch

class SearchProgressFragment : Fragment() {

    private var _binding: FragmentSearchProgressBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SearchProgressViewModel
    private val sourceRows = mutableListOf<SourceRow>()
    private lateinit var adapter: SourceAdapter

    private var hitCount = 0
    private var browserToolsShown = false
    private var checkedCount = 0
    private var completedReportId: String? = null
    private var pendingCandidates: List<CandidateProfile>? = null
    private var pendingCandidatesRound: Int = 1
    private var searchStartMs = 0L
    private var estimatedTotal = 0
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private var currentType = "person"
    private var currentDisplayQuery = ""

    data class SourceRow(
        val source: String,
        var state: State,
        var detail: String = ""
    ) {
        enum class State { CHECKING, FOUND, NOT_FOUND, FAILED, BLOCKED }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rawQuery = arguments?.getString("query") ?: ""
        val type = arguments?.getString("type") ?: "person"
        val round = arguments?.getInt("round") ?: 1
        val displayQuery = arguments?.getString("searchQuery")?.takeIf { it.isNotBlank() } ?: rawQuery
        val cityIdx = rawQuery.indexOf("|city=")
        val query = if (cityIdx != -1) rawQuery.substring(0, cityIdx) else rawQuery
        val locationHint = if (cityIdx != -1) rawQuery.substring(cityIdx + 6) else ""

        searchStartMs = System.currentTimeMillis()
        estimatedTotal = when (type) {
            "scan" -> 6
            "person" -> 29
            "username" -> 80
            "ip", "domain" -> 20
            "email" -> 15
            "company" -> 13
            "phone" -> 7
            "comprehensive" -> if (round >= 3) 65 else 35
            "vehicle", "vin" -> 5
            "wifi", "ssid", "mac" -> 4
            "hash" -> 3
            "cve" -> 3
            "trademark" -> 3
            else -> 10
        }

        currentType = type
        currentDisplayQuery = displayQuery
        val cleanDisplayQuery = displayQuery.split("|").joinToString(", ") { part ->
            val eqIdx = part.indexOf('=')
            if (eqIdx != -1) part.substring(eqIdx + 1).trim() else part.trim()
        }.replace(Regex(",\\s*,"), ",").trim().trimEnd(',')
        binding.tvSearchQuery.text = if (round > 1) "Round $round: $cleanDisplayQuery" else cleanDisplayQuery
        binding.chipSearchType.text = when {
            type == "scan" -> "STAGE 1 · DISCOVERY"
            type == "comprehensive" && round == 2 -> "STAGE 2 · DEEP SEARCH"
            type == "comprehensive" && round >= 3 -> "STAGE 3 · FULL DOSSIER"
            round > 1 -> "ROUND $round"
            else -> type.uppercase()
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
                val reportId = completedReportId ?: return@setOnClickListener
                try {
                    nav.navigate(
                        R.id.action_progress_to_results,
                        Bundle().apply {
                            putString("searchQuery", currentDisplayQuery)
                            putString("searchType", currentType)
                            putString("reportId", reportId)
                        }
                    )
                } catch (_: Exception) {}
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collect { event ->
                handleEvent(event)
            }
        }

        viewModel.startSearch()
    }

    private fun handleEvent(event: SearchProgressEvent) {
        if (_binding == null) return
        when (event) {
            is SearchProgressEvent.Checking -> {
                val existing = sourceRows.indexOfFirst { it.source == event.source }
                if (existing == -1) {
                    sourceRows.add(SourceRow(event.source, SourceRow.State.CHECKING))
                    adapter.notifyItemInserted(sourceRows.lastIndex)
                    binding.rvSources.smoothScrollToPosition(sourceRows.lastIndex)
                }
                binding.tvStatus.text = "Checking ${event.source}…"
            }
            is SearchProgressEvent.Found -> {
                hitCount++
                val idx = sourceRows.indexOfFirst { it.source == event.source }
                if (idx != -1) {
                    sourceRows[idx].state = SourceRow.State.FOUND
                    sourceRows[idx].detail = event.detail
                    adapter.notifyItemChanged(idx)
                } else {
                    sourceRows.add(SourceRow(event.source, SourceRow.State.FOUND, event.detail))
                    adapter.notifyItemInserted(sourceRows.lastIndex)
                }
                checkedCount++
                updateCounts()
            }
            is SearchProgressEvent.NotFound -> {
                val idx = sourceRows.indexOfFirst { it.source == event.source }
                if (idx != -1) {
                    sourceRows[idx].state = SourceRow.State.NOT_FOUND
                    adapter.notifyItemChanged(idx)
                } else {
                    sourceRows.add(SourceRow(event.source, SourceRow.State.NOT_FOUND))
                    adapter.notifyItemInserted(sourceRows.lastIndex)
                }
                checkedCount++
                updateCounts()
            }
            is SearchProgressEvent.Failed -> {
                val idx = sourceRows.indexOfFirst { it.source == event.source }
                if (idx != -1) {
                    sourceRows[idx].state = SourceRow.State.FAILED
                    sourceRows[idx].detail = event.reason
                    adapter.notifyItemChanged(idx)
                } else {
                    sourceRows.add(SourceRow(event.source, SourceRow.State.FAILED, event.reason))
                    adapter.notifyItemInserted(sourceRows.lastIndex)
                }
                checkedCount++
                updateCounts()
            }
            is SearchProgressEvent.Blocked -> {
                val idx = sourceRows.indexOfFirst { it.source == event.source }
                if (idx != -1) {
                    sourceRows[idx].state = SourceRow.State.BLOCKED
                    sourceRows[idx].detail = event.reason
                    adapter.notifyItemChanged(idx)
                } else {
                    sourceRows.add(SourceRow(event.source, SourceRow.State.BLOCKED, event.reason))
                    adapter.notifyItemInserted(sourceRows.lastIndex)
                }
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
                    binding.tvStatus.text = "${event.candidates.size} candidate${if (event.candidates.size != 1) "s" else ""} identified · ${elapsedSec}s"
                    binding.tvEta.text = ""
                    binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_cyan))
                    binding.fabViewReport.text = "Select Candidates (${event.candidates.size})"
                    binding.fabViewReport.apply {
                        visibility = View.VISIBLE
                        alpha = 0f
                        animate().alpha(1f).setDuration(400).start()
                    }
                }
            }
            is SearchProgressEvent.BrowserToolsReady -> showBrowserTools(event.categories)
            is SearchProgressEvent.Complete -> {
                completedReportId = event.reportId
                binding.progressBar.visibility = View.GONE
                val elapsedSec = ((System.currentTimeMillis() - searchStartMs) / 1000).toInt()
                binding.tvStatus.text = "COMPLETE — ${event.hitCount} hit${if (event.hitCount != 1) "s" else ""} · ${elapsedSec}s"
                binding.tvEta.text = ""
                binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
                binding.fabViewReport.text = "View Full Report"
                binding.fabViewReport.apply {
                    visibility = View.VISIBLE
                    alpha = 0f
                    animate().alpha(1f).setDuration(400).start()
                }
            }
        }
    }

    private fun showBrowserTools(categories: LinkedHashMap<String, List<Pair<String, String>>>) {
        if (browserToolsShown || _binding == null) return
        browserToolsShown = true
        val ctx = requireContext()
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()
        val container = binding.browserToolsContainer
        container.visibility = View.VISIBLE
        val totalCount = categories.values.sumOf { it.size }
        val header = android.widget.TextView(ctx).apply {
            text = "── BROWSER TOOLS ──────── $totalCount READY"
            textSize = 9f
            letterSpacing = 0.16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            setPadding(dp(16f), dp(14f), dp(16f), dp(6f))
        }
        container.addView(header)
        val launchAll = com.google.android.material.button.MaterialButton(ctx).apply {
            text = "LAUNCH ALL $totalCount TOOLS"
            textSize = 12f
            val lp = android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).also { it.setMargins(dp(16f), dp(4f), dp(16f), dp(8f)) }
            layoutParams = lp
            setOnClickListener { categories.values.flatten().forEach { (_, url) -> openUrl(url) }; android.widget.Toast.makeText(ctx, "Launched $totalCount tools", android.widget.Toast.LENGTH_LONG).show() }
        }
        container.addView(launchAll)
        categories.forEach { (label, tools) ->
            val btn = com.google.android.material.button.MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "$label  (${tools.size})"
                textSize = 13f
                val lp = android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).also { it.setMargins(dp(16f), dp(3f), dp(16f), dp(3f)) }
                layoutParams = lp
                setOnClickListener { tools.forEach { (_, url) -> openUrl(url) }; android.widget.Toast.makeText(ctx, "Opened ${tools.size} tools", android.widget.Toast.LENGTH_SHORT).show() }
            }
            container.addView(btn)
        }
        container.addView(android.widget.Space(ctx).apply { layoutParams = android.widget.LinearLayout.LayoutParams(1, dp(24f)) })
    }

    private fun openUrl(url: String) {
        try { requireContext().startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
    }

    private fun updateCounts() {
        binding.tvFoundCount.text = "$hitCount HIT${if (hitCount != 1) "S" else ""}"
        val total = maxOf(estimatedTotal, sourceRows.size)
        binding.tvCheckedCount.text = "$checkedCount / $total checked"
        val elapsedMs = System.currentTimeMillis() - searchStartMs
        if (checkedCount > 0 && checkedCount < total) {
            val avgMsPerSource = elapsedMs / checkedCount
            val remaining = total - checkedCount
            val etaSec = (avgMsPerSource * remaining / 1000).toInt().coerceAtMost(300)
            binding.tvEta.text = "ETA ~${etaSec}s remaining"
        }
    }

    override fun onDestroyView() {
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
            }
        }
    }
}
