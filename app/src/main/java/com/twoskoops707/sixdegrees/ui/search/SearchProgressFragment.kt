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
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.data.repository.SearchProgressEvent
import com.twoskoops707.sixdegrees.databinding.FragmentSearchProgressBinding
import com.twoskoops707.sixdegrees.databinding.ItemSearchSourceBinding
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
    private var searchStartMs = 0L
    private var estimatedTotal = 0

    data class SourceRow(
        val source: String,
        var state: State,
        var detail: String = ""
    ) {
        enum class State { CHECKING, FOUND, NOT_FOUND, FAILED }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val query = arguments?.getString("query") ?: ""
        val type = arguments?.getString("type") ?: "person"

        searchStartMs = System.currentTimeMillis()
        estimatedTotal = when (type) {
            "person" -> 27
            "username" -> 80
            "ip", "domain" -> 20
            "email" -> 12
            "company" -> 10
            "phone" -> 5
            else -> 10
        }

        binding.tvSearchQuery.text = query
        binding.chipSearchType.text = type.uppercase()

        viewModel = ViewModelProvider(
            this,
            SearchProgressViewModel.Factory(requireActivity().application, query, type)
        )[SearchProgressViewModel::class.java]

        adapter = SourceAdapter()
        binding.rvSources.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SearchProgressFragment.adapter
            itemAnimator = null
        }

        binding.fabViewReport.setOnClickListener {
            val reportId = completedReportId ?: return@setOnClickListener
            findNavController().navigate(
                R.id.action_progress_to_results,
                Bundle().apply {
                    putString("searchQuery", query)
                    putString("searchType", type)
                    putString("reportId", reportId)
                }
            )
        }

        lifecycleScope.launch {
            viewModel.events.collect { event ->
                handleEvent(event)
            }
        }

        viewModel.startSearch()
    }

    private fun handleEvent(event: SearchProgressEvent) {
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
            is SearchProgressEvent.Complete -> {
                completedReportId = event.reportId
                binding.progressBar.visibility = View.GONE
                val elapsedSec = ((System.currentTimeMillis() - searchStartMs) / 1000).toInt()
                binding.tvStatus.text = "COMPLETE — ${event.hitCount} hit${if (event.hitCount != 1) "s" else ""} · ${elapsedSec}s"
                binding.tvEta.text = ""
                binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
                binding.fabViewReport.apply {
                    visibility = View.VISIBLE
                    alpha = 0f
                    animate().alpha(1f).setDuration(400).start()
                }
            }
        }
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
            }
        }
    }
}
