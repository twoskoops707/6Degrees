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
                meta["emailrep_reputation"]?.let { rows.add("Reputation" to it.replaceFirstChar { c -> c.uppercase() }) }
                meta["emailrep_suspicious"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Suspicious" to "Flagged as suspicious by EmailRep") }
                meta["emailrep_breach"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Data Breach" to "Email involved in known breach") }
                meta["emailrep_references"]?.let { rows.add("References" to "$it threat database reference${if ((it.toIntOrNull() ?: 1) != 1) "s" else ""}") }
                meta["emailrep_profiles"]?.takeIf { it.isNotBlank() }?.let { rows.add("Seen On Platforms" to it) }
                meta["hibp_breach_count"]?.let { c ->
                    val count = c.toIntOrNull() ?: 0
                    if (count > 0) rows.add("HIBP Breaches" to "$count breach${if (count != 1) "es" else ""} found")
                }
                meta["hibp_breaches"]?.takeIf { it.isNotBlank() }?.let { rows.add("Breach Names" to it) }
                meta["leakcheck_found"]?.let { c ->
                    val count = c.toIntOrNull() ?: 0
                    if (count > 0) rows.add("LeakCheck Hits" to "$count breach source${if (count != 1) "s" else ""}")
                }
                meta["leakcheck_sources"]?.takeIf { it.isNotBlank() }?.let { rows.add("Leak Sources" to it) }
                meta["gravatar_name"]?.takeIf { it.isNotBlank() }?.let { rows.add("Gravatar Name" to it) }
                meta["gravatar_location"]?.takeIf { it.isNotBlank() }?.let { rows.add("Gravatar Location" to it) }
                meta["gravatar_bio"]?.takeIf { it.isNotBlank() }?.let { rows.add("Gravatar Bio" to it) }
                meta["gravatar_accounts"]?.takeIf { it.isNotBlank() }?.let { rows.add("Gravatar Linked Accounts" to it) }
                meta["gravatar_url"]?.let { rows.add("Gravatar Avatar" to it) }
                meta["threatcrowd_email_domains"]?.takeIf { it.isNotBlank() }?.let { rows.add("ThreatCrowd Linked Domains" to it) }
                meta["threatcrowd_email_refs"]?.let { r ->
                    val refs = r.toIntOrNull() ?: 0
                    if (refs > 0) rows.add("ThreatCrowd References" to refs.toString())
                }
                meta["hackertarget_email_hosts"]?.takeIf { it.isNotBlank() }?.let { rows.add("Associated Hostnames" to it) }
            }
            "ip", "domain" -> {
                meta["ip_country"]?.takeIf { it.isNotBlank() }?.let { rows.add("Country" to it) }
                meta["ip_city"]?.takeIf { it.isNotBlank() }?.let { rows.add("City" to it) }
                meta["ip_isp"]?.takeIf { it.isNotBlank() }?.let { rows.add("ISP" to it) }
                meta["ip_org"]?.takeIf { it.isNotBlank() }?.let { rows.add("Org" to it) }
                meta["ip_asn"]?.takeIf { it.isNotBlank() }?.let { rows.add("ASN" to it) }
                meta["ip_timezone"]?.takeIf { it.isNotBlank() }?.let { rows.add("Timezone" to it) }
                meta["shodan_ports"]?.takeIf { it.isNotBlank() }?.let { rows.add("Open Ports" to it) }
                meta["shodan_vulns"]?.takeIf { it.isNotBlank() }?.let { rows.add("⚠ CVEs / Vulnerabilities" to it) }
                meta["shodan_hostnames"]?.takeIf { it.isNotBlank() }?.let { rows.add("Shodan Hostnames" to it) }
                meta["shodan_tags"]?.takeIf { it.isNotBlank() }?.let { rows.add("Shodan Tags" to it) }
                meta["greynoise_noise"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Internet Scanner" to "This IP actively scans the internet") }
                meta["greynoise_riot"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("Common Service (RIOT)" to "Benign mass-scanning service") }
                meta["greynoise_classification"]?.takeIf { it.isNotBlank() }?.let { rows.add("GreyNoise Classification" to it.replaceFirstChar { c -> c.uppercase() }) }
                meta["greynoise_name"]?.takeIf { it.isNotBlank() }?.let { rows.add("GreyNoise Actor" to it) }
                meta["greynoise_last_seen"]?.takeIf { it.isNotBlank() }?.let { rows.add("GreyNoise Last Seen" to it) }
                meta["robtex_as_name"]?.takeIf { it.isNotBlank() }?.let { rows.add("AS Name" to it) }
                meta["robtex_bgp_route"]?.takeIf { it.isNotBlank() }?.let { rows.add("BGP Route" to it) }
                meta["robtex_passive_dns"]?.takeIf { it.isNotBlank() }?.let { rows.add("Passive DNS (Robtex)" to it) }
                meta["abuseipdb_score"]?.let { s ->
                    val score = s.toIntOrNull() ?: 0
                    if (score > 0) rows.add("⚠ Abuse Confidence Score" to "$score%")
                }
                meta["abuseipdb_reports"]?.let { r ->
                    val reports = r.toIntOrNull() ?: 0
                    if (reports > 0) rows.add("Abuse Reports" to "$reports report${if (reports != 1) "s" else ""}")
                }
                meta["abuseipdb_domain"]?.takeIf { it.isNotBlank() }?.let { rows.add("AbuseIPDB Domain" to it) }
                meta["abuseipdb_isp"]?.takeIf { it.isNotBlank() }?.let { rows.add("AbuseIPDB ISP" to it) }
                meta["urlhaus_urls_count"]?.let { c ->
                    val count = c.toIntOrNull() ?: 0
                    if (count > 0) rows.add("⚠ URLhaus Malware URLs" to "$count malicious URL${if (count != 1) "s" else ""} tracked")
                }
                meta["threatcrowd_subdomains"]?.takeIf { it.isNotBlank() }?.let { rows.add("ThreatCrowd Subdomains" to it) }
                meta["threatcrowd_emails"]?.takeIf { it.isNotBlank() }?.let { rows.add("ThreatCrowd Emails" to it) }
                meta["threatcrowd_resolutions"]?.takeIf { it.isNotBlank() }?.let { rows.add("ThreatCrowd IP History" to it) }
                meta["threatcrowd_domains"]?.takeIf { it.isNotBlank() }?.let { rows.add("ThreatCrowd Domains" to it) }
                meta["hackertarget_hostsearch"]?.takeIf { it.isNotBlank() }?.let {
                    it.lines().filter { l -> l.isNotBlank() }.take(10).forEach { line ->
                        rows.add("Host" to line)
                    }
                }
                meta["subdomains"]?.takeIf { it.isNotBlank() }?.let { rows.add("SSL Subdomains" to it) }
                meta["cert_count"]?.let { rows.add("SSL Certs Found" to it) }
                meta["wayback_count"]?.let { c ->
                    if ((c.toIntOrNull() ?: 0) > 0) rows.add("Wayback Snapshots" to c)
                }
                meta["wayback_first"]?.takeIf { it.isNotBlank() }?.let { rows.add("First Archived" to it) }
                meta["wayback_last"]?.takeIf { it.isNotBlank() }?.let { rows.add("Last Archived" to it) }
                meta["otx_pulse_count"]?.let { p ->
                    val pulses = p.toIntOrNull() ?: 0
                    if (pulses > 0) rows.add("⚠ OTX Threat Pulses" to "$pulses threat intel hit${if (pulses != 1) "s" else ""}")
                }
                meta["dns"]?.takeIf { it.isNotBlank() }?.let { rows.add("DNS Records" to it.take(500)) }
                meta["whois"]?.takeIf { it.isNotBlank() }?.let { rows.add("WHOIS Data" to it.take(600)) }
                meta["shodan_link"]?.let { rows.add("Shodan →" to it) }
                meta["urlscan_link"]?.let { rows.add("URLScan →" to it) }
                meta["virustotal_link"]?.let { rows.add("VirusTotal →" to it) }
            }
            "username" -> {
                meta["sites_checked"]?.let { rows.add("Sites Checked" to it) }
                meta["sites_found"]?.let { rows.add("Profiles Found" to it) }
                meta["github_profile"]?.takeIf { it.isNotBlank() }?.let { profile ->
                    Regex("\"name\":\\s*\"([^\"]+)\"").find(profile)?.groupValues?.get(1)?.let { rows.add("GitHub Name" to it) }
                    Regex("\"bio\":\\s*\"([^\"]+)\"").find(profile)?.groupValues?.get(1)?.let { rows.add("GitHub Bio" to it) }
                    Regex("\"company\":\\s*\"([^\"]+)\"").find(profile)?.groupValues?.get(1)?.let { rows.add("GitHub Company" to it) }
                    Regex("\"location\":\\s*\"([^\"]+)\"").find(profile)?.groupValues?.get(1)?.let { rows.add("GitHub Location" to it) }
                    Regex("\"followers\":\\s*(\\d+)").find(profile)?.groupValues?.get(1)?.let { rows.add("GitHub Followers" to it) }
                    Regex("\"public_repos\":\\s*(\\d+)").find(profile)?.groupValues?.get(1)?.let { rows.add("GitHub Repos" to it) }
                }
                meta["keybase_name"]?.takeIf { it.isNotBlank() }?.let { rows.add("Keybase Name" to it) }
                meta["keybase_location"]?.takeIf { it.isNotBlank() }?.let { rows.add("Keybase Location" to it) }
                meta["keybase_bio"]?.takeIf { it.isNotBlank() }?.let { rows.add("Keybase Bio" to it) }
                meta["keybase_proofs"]?.takeIf { it.isNotBlank() }?.let { rows.add("Keybase Social Proofs" to it) }
                meta["hackernews_karma"]?.let { k ->
                    val karma = k.toIntOrNull() ?: 0
                    if (karma > 0) rows.add("HackerNews Karma" to karma.toString())
                }
                meta["hackernews_about"]?.takeIf { it.isNotBlank() }?.let { rows.add("HackerNews About" to it) }
                meta["devto_name"]?.takeIf { it.isNotBlank() }?.let { rows.add("Dev.to Name" to it) }
                meta["devto_location"]?.takeIf { it.isNotBlank() }?.let { rows.add("Dev.to Location" to it) }
                meta["devto_summary"]?.takeIf { it.isNotBlank() }?.let { rows.add("Dev.to Bio" to it) }
                meta["devto_joined"]?.takeIf { it.isNotBlank() }?.let { rows.add("Dev.to Joined" to it) }
                meta["found_urls"]?.takeIf { it.isNotBlank() }?.let {
                    it.lines().filter { l -> l.isNotBlank() }.forEach { line ->
                        val parts = line.split(": ", limit = 2)
                        rows.add((parts.firstOrNull() ?: "Site") to (parts.getOrNull(1) ?: line))
                    }
                }
            }
            "person" -> {
                meta["arrest_count"]?.let { c ->
                    val count = c.toIntOrNull() ?: 0
                    if (count > 0) rows.add("⚠ Arrest Records" to "$count arrest record${if (count != 1) "s" else ""} found")
                }
                meta["arrest_records"]?.takeIf { it.isNotBlank() }?.let {
                    it.lines().filter { l -> l.isNotBlank() }.take(5).forEach { record ->
                        rows.add("Arrest" to record)
                    }
                }
                meta["officer_matches"]?.let { c ->
                    val count = c.toIntOrNull() ?: 0
                    if (count > 0) rows.add("Corporate Records" to "$count officer record${if (count != 1) "s" else ""}")
                }
                meta["officer_details"]?.takeIf { it.isNotBlank() }?.let {
                    it.lines().filter { l -> l.isNotBlank() }.forEach { detail ->
                        rows.add("Officer Record" to detail)
                    }
                }
                meta["courtlistener_count"]?.let { c ->
                    val count = c.toIntOrNull() ?: 0
                    if (count > 0) rows.add("Court Records" to "$count court record${if (count != 1) "s" else ""}")
                }
                meta["courtlistener_link"]?.let { rows.add("CourtListener →" to it) }
                meta["judyrecords_link"]?.let { rows.add("JudyRecords →" to it) }
                meta["spokeo_link"]?.let { rows.add("Spokeo →" to it) }
            }
            "company" -> {
                meta["company_count"]?.let { rows.add("Companies Found" to it) }
                meta["companies"]?.takeIf { it.isNotBlank() }?.let {
                    it.lines().filter { l -> l.isNotBlank() }.forEach { company ->
                        rows.add("Company" to company)
                    }
                }
                meta["officer_count"]?.let { c ->
                    val count = c.toIntOrNull() ?: 0
                    if (count > 0) rows.add("Officers Found" to count.toString())
                }
                meta["officers"]?.takeIf { it.isNotBlank() }?.let {
                    it.lines().filter { l -> l.isNotBlank() }.forEach { officer ->
                        rows.add("Officer" to officer)
                    }
                }
                meta["hunter_emails_count"]?.let { c ->
                    val count = c.toIntOrNull() ?: 0
                    if (count > 0) rows.add("Emails Found" to count.toString())
                }
                meta["hunter_emails"]?.takeIf { it.isNotBlank() }?.let { rows.add("Company Emails" to it) }
                meta["sec_link"]?.let { rows.add("SEC Filings →" to it) }
            }
            "phone" -> {
                meta["numverify_valid"]?.let { v -> rows.add("Valid Number" to if (v == "true") "Yes" else "No") }
                meta["numverify_country"]?.takeIf { it.isNotBlank() }?.let { rows.add("Country" to it) }
                meta["numverify_carrier"]?.takeIf { it.isNotBlank() }?.let { rows.add("Carrier" to it) }
                meta["numverify_line_type"]?.takeIf { it.isNotBlank() }?.let { rows.add("Line Type" to it) }
                meta["numverify_location"]?.takeIf { it.isNotBlank() }?.let { rows.add("Location" to it) }
                meta["numverify_intl"]?.takeIf { it.isNotBlank() }?.let { rows.add("Intl Format" to it) }
                meta["truecaller_link"]?.let { rows.add("TrueCaller →" to it) }
                meta["whitepages_link"]?.let { rows.add("WhitePages →" to it) }
                meta["spokeo_link"]?.let { rows.add("Spokeo →" to it) }
            }
            "image" -> {
                meta["tineye_link"]?.let { rows.add("TinEye →" to it) }
                meta["google_lens_link"]?.let { rows.add("Google Lens →" to it) }
                meta["yandex_images_link"]?.let { rows.add("Yandex →" to it) }
                meta["facecheck_id_link"]?.let { rows.add("FaceCheck.id →" to it) }
                meta["bing_visual_search_link"]?.let { rows.add("Bing Visual →" to it) }
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
                setType("text/plain")
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
