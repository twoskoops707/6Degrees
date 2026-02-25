package com.twoskoops707.sixdegrees.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.databinding.FragmentSearchBinding

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()
    private lateinit var recentAdapter: RecentSearchAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.getString("searchType")?.let { type -> setChipForType(type) }

        binding.btnWizard.setOnClickListener {
            findNavController().navigate(R.id.action_search_to_wizard)
        }

        binding.searchButton.setOnClickListener {
            val query = binding.searchInput.text?.toString()?.trim() ?: ""
            if (query.isNotBlank()) {
                binding.searchInputLayout.error = null
                viewModel.search(query, getSelectedSearchType())
            } else {
                binding.searchInputLayout.error = "Enter something to search"
            }
        }

        recentAdapter = RecentSearchAdapter { report ->
            findNavController().navigate(
                R.id.action_search_to_results,
                Bundle().apply {
                    putString("searchQuery", report.searchQuery)
                    putString("searchType", "person")
                    putString("reportId", report.id)
                }
            )
        }
        binding.recentSearchesRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentAdapter
        }

        viewModel.searchState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is SearchUiState.Idle -> setLoading(false)
                is SearchUiState.Loading -> setLoading(true)
                is SearchUiState.Success -> {
                    setLoading(false)
                    viewModel.resetState()
                    findNavController().navigate(
                        R.id.action_search_to_results,
                        Bundle().apply {
                            putString("searchQuery", binding.searchInput.text?.toString()?.trim())
                            putString("searchType", getSelectedSearchType())
                            putString("reportId", state.reportId)
                        }
                    )
                }
                is SearchUiState.Error -> {
                    setLoading(false)
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetState()
                }
            }
        }

        viewModel.recentSearches.observe(viewLifecycleOwner) { reports ->
            recentAdapter.submitList(reports)
            binding.noRecentSearches.visibility = if (reports.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.loadRecentSearches()
    }

    private fun getSelectedSearchType(): String {
        return when (binding.chipGroupType.checkedChipId) {
            R.id.chip_email -> "email"
            R.id.chip_phone -> "phone"
            R.id.chip_username -> "username"
            R.id.chip_ip -> "ip"
            R.id.chip_company -> "company"
            R.id.chip_image -> "image"
            else -> "person"
        }
    }

    private fun setChipForType(type: String) {
        val chipId = when (type) {
            "email" -> R.id.chip_email
            "phone" -> R.id.chip_phone
            "username" -> R.id.chip_username
            "ip" -> R.id.chip_ip
            "company" -> R.id.chip_company
            "image" -> R.id.chip_image
            else -> R.id.chip_person
        }
        binding.chipGroupType.check(chipId)
    }

    private fun setLoading(loading: Boolean) {
        binding.searchButton.isEnabled = !loading
        binding.searchButton.text = if (loading) "Searching…" else getString(R.string.search_button)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
