package com.twoskoops707.sixdegrees.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.databinding.FragmentHistoryBinding

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var historyAdapter: HistoryAdapter

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
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter { report ->
            val bundle = Bundle().apply {
                putString("searchQuery", report.searchQuery)
                putString("searchType", "person")
                putString("reportId", report.id)
            }
            findNavController().navigate(R.id.action_history_to_results, bundle)
        }
        binding.historyRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.reports.observe(viewLifecycleOwner) { reports ->
            historyAdapter.submitList(reports)
            if (reports.isEmpty()) {
                binding.historyContent.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
            } else {
                binding.historyContent.visibility = View.VISIBLE
                binding.emptyState.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
