package com.example.a6degrees.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.a6degrees.databinding.FragmentHistoryBinding

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

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
        loadHistory()
    }

    private fun setupToolbar() {
        binding.historyToolbar.setNavigationOnClickListener {
            // Navigate back to search
            parentFragmentManager.popBackStack()
        }
    }

    private fun loadHistory() {
        // TODO: Load search history from database
        // For now, just show empty state
        showEmptyState()
    }

    private fun showEmptyState() {
        binding.historyContent.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
    }

    private fun showHistory() {
        binding.historyContent.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}