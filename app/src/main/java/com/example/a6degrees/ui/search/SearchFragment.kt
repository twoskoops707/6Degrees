package com.example.a6degrees.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.a6degrees.R
import com.example.a6degrees.databinding.FragmentSearchBinding

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()
    private lateinit var recentAdapter: RecentSearchAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChips()
        setupSearchButton()
        setupRecentSearches()
        observeViewModel()
        viewModel.loadRecentSearches()
    }

    private fun setupChips() {
        binding.chipPerson.setOnClickListener {
            binding.chipPerson.isChecked = true
            binding.chipCompany.isChecked = false
            binding.chipImage.isChecked = false
        }
        binding.chipCompany.setOnClickListener {
            binding.chipPerson.isChecked = false
            binding.chipCompany.isChecked = true
            binding.chipImage.isChecked = false
        }
        binding.chipImage.setOnClickListener {
            binding.chipPerson.isChecked = false
            binding.chipCompany.isChecked = false
            binding.chipImage.isChecked = true
        }
    }

    private fun setupSearchButton() {
        binding.searchButton.setOnClickListener {
            val query = binding.searchInput.text.toString().trim()
            if (query.isNotBlank()) {
                performSearch(query)
            } else {
                binding.searchInputLayout.error = "Please enter a search term"
            }
        }
    }

    private fun setupRecentSearches() {
        recentAdapter = RecentSearchAdapter { report ->
            val bundle = Bundle().apply {
                putString("searchQuery", report.searchQuery)
                putString("searchType", "person")
                putString("reportId", report.id)
            }
            findNavController().navigate(R.id.action_search_to_results, bundle)
        }
        binding.recentSearchesRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.searchState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is SearchUiState.Idle -> setLoading(false)
                is SearchUiState.Loading -> setLoading(true)
                is SearchUiState.Success -> {
                    setLoading(false)
                    val bundle = Bundle().apply {
                        putString("searchQuery", binding.searchInput.text.toString().trim())
                        putString("searchType", getSelectedSearchType())
                        putString("reportId", state.reportId)
                    }
                    viewModel.resetState()
                    findNavController().navigate(R.id.action_search_to_results, bundle)
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
    }

    private fun performSearch(term: String) {
        binding.searchInputLayout.error = null
        viewModel.search(term, getSelectedSearchType())
    }

    private fun getSelectedSearchType(): String = when {
        binding.chipCompany.isChecked -> "company"
        binding.chipImage.isChecked -> "image"
        else -> "person"
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
