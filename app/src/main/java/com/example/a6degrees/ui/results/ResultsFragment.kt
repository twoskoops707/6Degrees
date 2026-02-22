package com.example.a6degrees.ui.results

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.a6degrees.databinding.FragmentResultsBinding

class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        showLoading()
        // TODO: Load actual results data
        loadResults()
    }

    private fun setupToolbar() {
        binding.resultsToolbar.setNavigationOnClickListener {
            // Navigate back to search
            parentFragmentManager.popBackStack()
        }
    }

    private fun showLoading() {
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.resultsContent.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
    }

    private fun showResults() {
        binding.loadingIndicator.visibility = View.GONE
        binding.resultsContent.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
    }

    private fun showEmptyState() {
        binding.loadingIndicator.visibility = View.GONE
        binding.resultsContent.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
    }

    private fun loadResults() {
        // Simulate loading delay
        view?.postDelayed({
            // TODO: Replace with actual data loading
            showResults()
            // For now, just populate with sample data
            populateSampleData()
        }, 1000)
    }

    private fun populateSampleData() {
        // Sample data population
        binding.fullName.text = "John Doe"
        binding.jobTitle.text = "Senior Software Engineer at TechCorp"
        binding.location.text = "San Francisco, CA"
        binding.bio.text = "Experienced software engineer with expertise in Android development and open-source intelligence gathering. Passionate about creating tools that help people find information ethically and legally."
        binding.email.text = "john.doe@example.com"
        binding.phone.text = "+1 (555) 123-4567"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}