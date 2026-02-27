package com.twoskoops707.sixdegrees.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.databinding.FragmentHistoryBinding

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var historyAdapter: HistoryAdapter
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        observeViewModel()
        viewModel.loadHistory()
    }

    private fun setupToolbar() {
        binding.historyToolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        binding.btnClearAll.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear All Reports")
                .setMessage("Delete all search history? This cannot be undone.")
                .setPositiveButton("Delete All") { _, _ -> viewModel.deleteAll() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter { report ->
            val searchType = try {
                val type = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
                moshi.adapter<Map<String, String>>(type).fromJson(report.companiesJson)?.get("search_type") ?: "person"
            } catch (_: Exception) { "person" }
            val bundle = Bundle().apply {
                putString("searchQuery", report.searchQuery)
                putString("searchType", searchType)
                putString("reportId", report.id)
            }
            findNavController().navigate(R.id.action_history_to_results, bundle)
        }
        binding.historyRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val report = historyAdapter.currentList[position]
                viewModel.deleteReport(report)
                Snackbar.make(binding.root, "Report deleted", Snackbar.LENGTH_LONG)
                    .setAction("Undo") { viewModel.restoreReport(report) }
                    .show()
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.historyRecycler)
    }

    private fun observeViewModel() {
        viewModel.reports.observe(viewLifecycleOwner) { reports ->
            historyAdapter.submitList(reports)
            val hasReports = reports.isNotEmpty()
            binding.historyContent.visibility = if (hasReports) View.VISIBLE else View.GONE
            binding.emptyState.visibility = if (hasReports) View.GONE else View.VISIBLE
            binding.btnClearAll.visibility = if (hasReports) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
