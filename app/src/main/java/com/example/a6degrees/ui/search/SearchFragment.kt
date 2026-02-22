package com.example.a6degrees.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.a6degrees.databinding.FragmentSearchBinding
import com.google.android.material.chip.Chip

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

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
    }

    private fun setupChips() {
        // Set up chip group behavior
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
            val searchTerm = binding.searchInput.text.toString()
            if (searchTerm.isNotBlank()) {
                performSearch(searchTerm)
            }
        }
    }

    private fun performSearch(term: String) {
        // Determine search type based on selected chip
        val searchType = when {
            binding.chipPerson.isChecked -> "person"
            binding.chipCompany.isChecked -> "company"
            binding.chipImage.isChecked -> "image"
            else -> "person"
        }

        // TODO: Implement actual search functionality
        // This would involve calling the appropriate repository methods
        // and navigating to the results screen
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}