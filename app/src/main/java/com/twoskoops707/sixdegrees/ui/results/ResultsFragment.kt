package com.twoskoops707.sixdegrees.ui.results

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.databinding.FragmentResultsBinding
import com.twoskoops707.sixdegrees.databinding.ItemDataRowBinding
import com.twoskoops707.sixdegrees.domain.model.Employment
import com.twoskoops707.sixdegrees.domain.model.SocialProfile
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.resultsToolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.socialProfilesRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = SocialProfileAdapter()
        }
        binding.employmentRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = EmploymentAdapter()
        }

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

        val reportId = arguments?.getString("reportId")
        if (reportId != null) viewModel.loadReport(reportId) else showEmptyState()
    }

    private fun populateReport(state: ResultsUiState) {
        val report = state.report ?: return
        val person = state.person

        val metadata = try {
            val type = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
            moshi.adapter<Map<String, String>>(type).fromJson(report.companiesJson) ?: emptyMap()
        } catch (_: Exception) { emptyMap() }

        val searchType = arguments?.getString("searchType") ?: "person"

        computeAndShowShadyScore(metadata, searchType)

        if (person != null) {
            binding.personCard.visibility = View.VISIBLE
            binding.fullName.text = person.fullName.ifBlank { "${person.firstName} ${person.lastName}".trim() }
            binding.location.text = parseFirstAddress(person.addressesJson)
            binding.jobTitle.text = parseCurrentJob(person.employmentHistoryJson)
            binding.bio.text = buildBioText(person.dateOfBirth, person.gender)

            if (!person.emailAddress.isNullOrBlank() || !person.phoneNumber.isNullOrBlank()) {
                binding.contactCard.visibility = View.VISIBLE
                binding.email.text = person.emailAddress ?: "—"
                binding.phone.text = person.phoneNumber ?: "—"
            }

            parseSocialProfiles(person.socialProfilesJson).let { profiles ->
                if (profiles.isNotEmpty()) {
                    binding.socialCard.visibility = View.VISIBLE
                    (binding.socialProfilesRecycler.adapter as? SocialProfileAdapter)?.submitList(profiles)
                }
            }

            parseEmployment(person.employmentHistoryJson).let { jobs ->
                if (jobs.isNotEmpty()) {
                    binding.employmentCard.visibility = View.VISIBLE
                    (binding.employmentRecycler.adapter as? EmploymentAdapter)?.submitList(jobs)
                }
            }
        } else {
            binding.personCard.visibility = View.VISIBLE
            binding.fullName.text = report.searchQuery
            binding.jobTitle.text = ""
            binding.location.text = ""
            binding.bio.text = "Searched ${dateFormat.format(report.generatedAt)}"
        }

        val dataRows = buildDataRows(metadata, searchType)
        if (dataRows.isNotEmpty()) {
            binding.dataCard.visibility = View.VISIBLE
            binding.tvDataSectionLabel.text = sectionLabel(searchType)
            binding.rvDataRows.layoutManager = LinearLayoutManager(requireContext())
            binding.rvDataRows.adapter = DataRowAdapter(dataRows)
        }

        val sourceCount = try {
            val type = Types.newParameterizedType(List::class.java, com.twoskoops707.sixdegrees.domain.model.DataSource::class.java)
            moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.DataSource>>(type)
                .fromJson(report.sourcesJson)?.size ?: 0
        } catch (_: Exception) { 0 }
        binding.tvSourcesCount.text = "$sourceCount data source${if (sourceCount != 1) "s" else ""} queried"

        binding.btnExport.setOnClickListener { shareReport(report.searchQuery, searchType, metadata, person) }
    }

    private fun computeAndShowShadyScore(meta: Map<String, String>, type: String) {
        var score = 0
        val flags = mutableListOf<String>()

        val breachCount = meta["hibp_breach_count"]?.toIntOrNull() ?: 0
        if (breachCount > 0) { score += minOf(breachCount * 10, 30); flags.add("$breachCount breach${if (breachCount != 1) "es" else ""}") }

        val arrested = meta["arrest_count"]?.toIntOrNull() ?: 0
        if (arrested > 0) { score += minOf(arrested * 15, 40); flags.add("$arrested arrest record${if (arrested != 1) "s" else ""}") }

        val suspicious = meta["emailrep_suspicious"]?.toBooleanStrictOrNull() ?: false
        if (suspicious) { score += 20; flags.add("suspicious email") }

        val otxPulses = meta["otx_pulse_count"]?.toIntOrNull() ?: 0
        if (otxPulses > 0) { score += minOf(otxPulses * 5, 25); flags.add("$otxPulses threat intel hit${if (otxPulses != 1) "s" else ""}") }

        score = minOf(score, 100)

        val (color, verdict) = when {
            score == 0 -> Triple(ContextCompat.getColor(requireContext(), R.color.score_green), "Clean", "No significant red flags found")
            score < 30 -> Triple(ContextCompat.getColor(requireContext(), R.color.score_green), "Low Risk", "Minor concerns — ${flags.joinToString(", ")}")
            score < 60 -> Triple(ContextCompat.getColor(requireContext(), R.color.score_yellow), "Moderate Risk", "Several concerns — ${flags.joinToString(", ")}")
            else -> Triple(ContextCompat.getColor(requireContext(), R.color.score_red), "High Risk", "Multiple red flags — ${flags.joinToString(", ")}")
        }.let { (a, b, c) -> Pair(a, Pair(b, c)) }

        binding.tvScoreNumber.text = if (score == 0) "✓" else score.toString()
        binding.tvScoreNumber.setTextColor(color)
        binding.tvScoreVerdict.text = verdict.first
        binding.tvScoreVerdict.setTextColor(color)
        binding.tvScoreDetail.text = verdict.second
    }

    private fun buildDataRows(meta: Map<String, String>, type: String): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        when (type) {
            "email" -> {
                meta["emailrep_reputation"]?.let { rows.add("Reputation" to it) }
                meta["emailrep_references"]?.let { rows.add("References" to it) }
                meta["hibp_breaches"]?.takeIf { it.isNotBlank() }?.let { rows.add("Breaches" to it) }
                meta["emailrep_profiles"]?.takeIf { it.isNotBlank() }?.let { rows.add("Linked Profiles" to it) }
                meta["gravatar_url"]?.let { rows.add("Gravatar" to it) }
            }
            "ip", "domain" -> {
                meta["ip_country"]?.takeIf { it.isNotBlank() }?.let { rows.add("Country" to it) }
                meta["ip_city"]?.takeIf { it.isNotBlank() }?.let { rows.add("City" to it) }
                meta["ip_isp"]?.takeIf { it.isNotBlank() }?.let { rows.add("ISP" to it) }
                meta["ip_org"]?.takeIf { it.isNotBlank() }?.let { rows.add("Org" to it) }
                meta["ip_asn"]?.takeIf { it.isNotBlank() }?.let { rows.add("ASN" to it) }
                meta["ip_timezone"]?.takeIf { it.isNotBlank() }?.let { rows.add("Timezone" to it) }
                meta["subdomains"]?.takeIf { it.isNotBlank() }?.let { rows.add("Subdomains" to it) }
                meta["cert_count"]?.let { rows.add("SSL Certs Found" to it) }
                meta["wayback_count"]?.let { rows.add("Wayback Snapshots" to it) }
                meta["otx_pulse_count"]?.let { rows.add("OTX Threat Pulses" to it) }
                meta["shodan_link"]?.let { rows.add("Shodan →" to it) }
                meta["urlscan_link"]?.let { rows.add("URLScan →" to it) }
                meta["virustotal_link"]?.let { rows.add("VirusTotal →" to it) }
                meta["dns"]?.takeIf { it.isNotBlank() }?.let { rows.add("DNS Records" to it.take(300)) }
            }
            "username" -> {
                meta["sites_checked"]?.let { rows.add("Sites Checked" to it) }
                meta["sites_found"]?.let { rows.add("Sites Found On" to it) }
                meta["found_urls"]?.takeIf { it.isNotBlank() }?.let {
                    it.lines().filter { l -> l.isNotBlank() }.forEach { line ->
                        val parts = line.split(": ", limit = 2)
                        rows.add((parts.firstOrNull() ?: "Site") to (parts.getOrNull(1) ?: line))
                    }
                }
            }
            "person" -> {
                meta["arrest_count"]?.takeIf { it != "0" }?.let { rows.add("Arrest Records" to it) }
                meta["arrest_records"]?.takeIf { it.isNotBlank() }?.let { rows.add("Record Details" to it) }
                meta["courtlistener_link"]?.let { rows.add("CourtListener →" to it) }
                meta["judyrecords_link"]?.let { rows.add("JudyRecords →" to it) }
                meta["spokeo_link"]?.let { rows.add("Spokeo →" to it) }
            }
            "company" -> {
                meta["company_count"]?.let { rows.add("Companies Found" to it) }
                meta["companies"]?.takeIf { it.isNotBlank() }?.let { rows.add("Company Results" to it) }
                meta["officer_count"]?.takeIf { it != "0" }?.let { rows.add("Officers Found" to it) }
                meta["officers"]?.takeIf { it.isNotBlank() }?.let { rows.add("Officers" to it) }
                meta["hunter_emails"]?.takeIf { it.isNotBlank() }?.let { rows.add("Emails Found" to it) }
                meta["sec_link"]?.let { rows.add("SEC Filings →" to it) }
            }
            "phone" -> {
                meta["truecaller_link"]?.let { rows.add("TrueCaller →" to it) }
                meta["whitepages_link"]?.let { rows.add("WhitePages →" to it) }
                meta["numverify_raw"]?.takeIf { it.isNotBlank() }?.let { rows.add("Validation Data" to it.take(300)) }
            }
            "image" -> {
                meta["tineye_link"]?.let { rows.add("TinEye →" to it) }
                meta["google_lens_link"]?.let { rows.add("Google Lens →" to it) }
                meta["yandex_link"]?.let { rows.add("Yandex →" to it) }
                meta["facecheck_link"]?.let { rows.add("FaceCheck.id →" to it) }
            }
        }
        return rows
    }

    private fun sectionLabel(type: String) = when (type) {
        "email" -> "Email Intel"
        "ip", "domain" -> "Network Intel"
        "username" -> "Username Results"
        "company" -> "Company Intel"
        "phone" -> "Phone Intel"
        "image" -> "Image Search Links"
        else -> "Person Records"
    }

    private fun shareReport(query: String, type: String, meta: Map<String, String>, person: com.twoskoops707.sixdegrees.data.local.entity.PersonEntity?) {
        val sb = StringBuilder()
        sb.appendLine("=== SixDegrees Report ===")
        sb.appendLine("Query: $query")
        sb.appendLine("Type: $type")
        sb.appendLine()
        if (person != null) {
            sb.appendLine("Name: ${person.fullName}")
            sb.appendLine("Email: ${person.emailAddress ?: "—"}")
            sb.appendLine("Phone: ${person.phoneNumber ?: "—"}")
            sb.appendLine()
        }
        meta.forEach { (k, v) -> if (v.isNotBlank()) sb.appendLine("$k: $v") }
        sb.appendLine()
        sb.appendLine("Generated by SixDegrees")

        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, sb.toString())
                putExtra(Intent.EXTRA_SUBJECT, "SixDegrees Report: $query")
            }, "Share Report"
        ))
    }

    private fun parseFirstAddress(json: String): String {
        return try {
            val type = Types.newParameterizedType(List::class.java, com.twoskoops707.sixdegrees.domain.model.Address::class.java)
            val addrs = moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.Address>>(type).fromJson(json) ?: emptyList()
            addrs.firstOrNull()?.let { listOf(it.city, it.state, it.country).filter { s -> s.isNotBlank() }.joinToString(", ") } ?: ""
        } catch (_: Exception) { "" }
    }

    private fun parseCurrentJob(json: String): String {
        return try {
            parseEmployment(json).firstOrNull { it.isCurrent }
                ?.let { "${it.jobTitle} at ${it.companyName}" }
                ?: parseEmployment(json).firstOrNull()?.let { "${it.jobTitle} at ${it.companyName}" }
                ?: ""
        } catch (_: Exception) { "" }
    }

    private fun parseSocialProfiles(json: String): List<SocialProfile> {
        return try {
            val type = Types.newParameterizedType(List::class.java, SocialProfile::class.java)
            moshi.adapter<List<SocialProfile>>(type).fromJson(json) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun parseEmployment(json: String): List<Employment> {
        return try {
            val type = Types.newParameterizedType(List::class.java, Employment::class.java)
            moshi.adapter<List<Employment>>(type).fromJson(json) ?: emptyList()
        } catch (_: Exception) { emptyList() }
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

    private inner class DataRowAdapter(
        private val rows: List<Pair<String, String>>
    ) : RecyclerView.Adapter<DataRowAdapter.VH>() {

        inner class VH(val b: ItemDataRowBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemDataRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.b.tvRowLabel.text = rows[position].first
            holder.b.tvRowValue.text = rows[position].second
        }

        override fun getItemCount() = rows.size
    }
}
