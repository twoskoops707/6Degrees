package com.example.a6degrees.ui.results

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.a6degrees.databinding.FragmentResultsBinding
import com.example.a6degrees.domain.model.Employment
import com.example.a6degrees.domain.model.SocialProfile
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.Locale

class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResultsViewModel by viewModels()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val dateFormat = SimpleDateFormat("MMM yyyy", Locale.US)

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
        setupRecyclerViews()
        observeViewModel()

        val reportId = arguments?.getString("reportId")
        if (reportId != null) {
            viewModel.loadReport(reportId)
        } else {
            showEmptyState()
        }
    }

    private fun setupToolbar() {
        binding.resultsToolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerViews() {
        binding.socialProfilesRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = SocialProfileAdapter()
        }
        binding.employmentRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = EmploymentAdapter()
        }
    }

    private fun observeViewModel() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when {
                state.isLoading -> showLoading()
                state.error != null -> showEmptyState()
                state.report != null -> {
                    showResults()
                    populateReport(state)
                }
                else -> showEmptyState()
            }
        }
    }

    private fun populateReport(state: ResultsUiState) {
        val report = state.report ?: return
        val person = state.person

        if (person != null) {
            binding.fullName.text = person.fullName.ifBlank { "${person.firstName} ${person.lastName}".trim() }
            binding.location.text = parseFirstAddress(person.addressesJson)
            binding.email.text = person.emailAddress ?: "Not found"
            binding.phone.text = person.phoneNumber ?: "Not found"
            binding.bio.text = buildBioText(person.dateOfBirth, person.gender)
            binding.jobTitle.text = parseCurrentJob(person.employmentHistoryJson)

            parseSocialProfiles(person.socialProfilesJson).let { profiles ->
                (binding.socialProfilesRecycler.adapter as? SocialProfileAdapter)?.submitList(profiles)
                binding.socialCard.visibility = if (profiles.isEmpty()) View.GONE else View.VISIBLE
            }

            parseEmployment(person.employmentHistoryJson).let { jobs ->
                (binding.employmentRecycler.adapter as? EmploymentAdapter)?.submitList(jobs)
                binding.employmentCard.visibility = if (jobs.isEmpty()) View.GONE else View.VISIBLE
            }
        } else {
            binding.fullName.text = report.searchQuery
            binding.jobTitle.text = "No person data found"
            binding.location.text = ""
            binding.email.text = "Not found"
            binding.phone.text = "Not found"
            binding.bio.text = "Search completed at ${dateFormat.format(report.generatedAt)}. Confidence: ${(report.confidenceScore * 100).toInt()}%"
        }
    }

    private fun parseFirstAddress(json: String): String {
        return try {
            val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Address::class.java)
            val addresses = moshi.adapter<List<com.example.a6degrees.domain.model.Address>>(type).fromJson(json) ?: emptyList()
            addresses.firstOrNull()?.let { addr ->
                listOf(addr.city, addr.state, addr.country).filter { it.isNotBlank() }.joinToString(", ")
            } ?: ""
        } catch (e: Exception) { "" }
    }

    private fun parseCurrentJob(json: String): String {
        return try {
            val jobs = parseEmployment(json)
            jobs.firstOrNull { it.isCurrent }?.let { "${it.jobTitle} at ${it.companyName}" }
                ?: jobs.firstOrNull()?.let { "${it.jobTitle} at ${it.companyName}" }
                ?: ""
        } catch (e: Exception) { "" }
    }

    private fun parseSocialProfiles(json: String): List<SocialProfile> {
        return try {
            val type = Types.newParameterizedType(List::class.java, SocialProfile::class.java)
            moshi.adapter<List<SocialProfile>>(type).fromJson(json) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun parseEmployment(json: String): List<Employment> {
        return try {
            val type = Types.newParameterizedType(List::class.java, Employment::class.java)
            moshi.adapter<List<Employment>>(type).fromJson(json) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun buildBioText(dob: String?, gender: String?): String {
        val parts = mutableListOf<String>()
        if (!dob.isNullOrBlank()) parts.add("Born: $dob")
        if (!gender.isNullOrBlank()) parts.add("Gender: $gender")
        return parts.joinToString(" · ")
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
