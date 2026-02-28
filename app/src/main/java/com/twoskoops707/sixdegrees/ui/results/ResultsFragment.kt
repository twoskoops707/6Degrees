package com.twoskoops707.sixdegrees.ui.results

import android.content.Intent
import android.net.Uri
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
import coil.load
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

        val profileImageUrl = person?.profileImageUrl ?: metadata["tt_image_url"]
        if (!profileImageUrl.isNullOrBlank()) {
            binding.profileImage.load(profileImageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_person_placeholder)
                error(R.drawable.ic_person_placeholder)
            }
        }

        if (person != null) {
            binding.personCard.visibility = View.VISIBLE
            binding.fullName.text = person.fullName.ifBlank { "${person.firstName} ${person.lastName}".trim() }
            binding.location.text = parseFirstAddress(person.addressesJson).ifBlank { metadata["tt_locations"]?.split(",")?.firstOrNull()?.trim() ?: "" }
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
            binding.jobTitle.text = metadata["tt_ages"]?.let { "Age: $it" } ?: ""
            binding.location.text = metadata["tt_locations"]?.split(",")?.firstOrNull()?.trim()
                ?: metadata["uspb_addresses"]?.split(" | ")?.firstOrNull()?.trim() ?: ""
            binding.bio.text = buildString {
                metadata["tt_relatives"]?.takeIf { it.isNotBlank() }?.let { append("Relatives: $it") }
                if (isNotEmpty()) append(" · ")
                append("Searched ${dateFormat.format(report.generatedAt)}")
            }
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
            score == 0 -> Triple(ContextCompat.getColor(requireContext(), R.color.score_green), "CLEAR", "No significant indicators found")
            score < 30 -> Triple(ContextCompat.getColor(requireContext(), R.color.score_green), "LOW", flags.joinToString(" · ").ifBlank { "Minor indicators" })
            score < 60 -> Triple(ContextCompat.getColor(requireContext(), R.color.score_yellow), "MODERATE", flags.joinToString(" · "))
            else -> Triple(ContextCompat.getColor(requireContext(), R.color.score_red), "HIGH RISK", flags.joinToString(" · "))
        }.let { (a, b, c) -> Pair(a, Pair(b, c)) }

        binding.tvScoreNumber.text = if (score == 0) "✓" else score.toString()
        binding.tvScoreNumber.setTextColor(color)
        binding.tvScoreVerdict.text = verdict.first
        binding.tvScoreVerdict.setTextColor(color)
        binding.tvScoreDetail.text = verdict.second
    }

    private fun sec(label: String) = label to ""

    private fun buildDataRows(meta: Map<String, String>, type: String): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        when (type) {
            "email" -> {
                rows.add(sec("◈ REPUTATION"))
                meta["emailrep_reputation"]?.let { rows.add("Reputation" to it.replaceFirstChar { c -> c.uppercase() }) }
                meta["emailrep_suspicious"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Suspicious Flag" to "Flagged by EmailRep threat database") }
                meta["emailrep_breach"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Breach Exposure" to "Involved in known data breach") }
                meta["emailrep_references"]?.let { rows.add("Threat DB References" to it) }
                meta["emailrep_profiles"]?.takeIf { it.isNotBlank() }?.let { rows.add("Seen On Platforms" to it) }
                meta["eva_deliverable"]?.let { rows.add("Deliverable" to it.replaceFirstChar { c -> c.uppercase() }) }
                meta["eva_disposable"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Disposable Address" to "Temporary/throwaway email service") }
                meta["eva_spam_trap"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Spam Trap" to "Address is a spam trap") }
                meta["eva_mx_record"]?.let { rows.add("MX Record" to it) }

                val hibpCount = meta["hibp_breach_count"]?.toIntOrNull() ?: 0
                val proxyCount = meta["proxynova_breach_count"]?.toIntOrNull() ?: 0
                val leakCount = meta["leakcheck_found"]?.toIntOrNull() ?: 0
                if (hibpCount > 0 || proxyCount > 0 || leakCount > 0) {
                    rows.add(sec("◈ BREACH EXPOSURE"))
                    if (hibpCount > 0) {
                        rows.add("HIBP Breaches" to "$hibpCount breach${if (hibpCount != 1) "es" else ""} found")
                        meta["hibp_breaches"]?.takeIf { it.isNotBlank() }?.let { rows.add("Breach Names" to it) }
                    }
                    if (proxyCount > 0) {
                        rows.add("COMB Dataset Hits" to "$proxyCount record${if (proxyCount != 1) "s" else ""} in 3.2B leaked credentials")
                        meta["proxynova_samples"]?.takeIf { it.isNotBlank() }?.let { rows.add("Sample Entries" to it) }
                    }
                    if (leakCount > 0) {
                        rows.add("LeakCheck Sources" to "$leakCount breach source${if (leakCount != 1) "s" else ""}")
                        meta["leakcheck_sources"]?.takeIf { it.isNotBlank() }?.let { rows.add("Leak Sources" to it) }
                    }
                }

                val hasGravatar = !meta["gravatar_name"].isNullOrBlank() || !meta["gravatar_location"].isNullOrBlank()
                if (hasGravatar) {
                    rows.add(sec("◈ LINKED IDENTITY"))
                    meta["gravatar_name"]?.takeIf { it.isNotBlank() }?.let { rows.add("Gravatar Name" to it) }
                    meta["gravatar_location"]?.takeIf { it.isNotBlank() }?.let { rows.add("Gravatar Location" to it) }
                    meta["gravatar_bio"]?.takeIf { it.isNotBlank() }?.let { rows.add("Gravatar Bio" to it) }
                    meta["gravatar_accounts"]?.takeIf { it.isNotBlank() }?.let { rows.add("Linked Accounts" to it) }
                }

                val hasThreat = !meta["threatcrowd_email_domains"].isNullOrBlank() || !meta["hackertarget_email_hosts"].isNullOrBlank()
                if (hasThreat) {
                    rows.add(sec("◈ THREAT INTEL"))
                    meta["threatcrowd_email_domains"]?.takeIf { it.isNotBlank() }?.let { rows.add("Linked Domains" to it) }
                    meta["threatcrowd_email_refs"]?.let { r -> if ((r.toIntOrNull() ?: 0) > 0) rows.add("ThreatCrowd References" to r) }
                    meta["hackertarget_email_hosts"]?.takeIf { it.isNotBlank() }?.let { rows.add("Associated Hosts" to it) }
                }

                rows.add(sec("─── EXTERNAL SOURCES ───"))
                meta["gravatar_url"]?.let { rows.add("Gravatar Profile →" to it) }
            }
            "ip", "domain" -> {
                rows.add(sec("◈ GEOLOCATION"))
                meta["ip_city"]?.takeIf { it.isNotBlank() }?.let { rows.add("City" to it) }
                meta["ip_country"]?.takeIf { it.isNotBlank() }?.let { rows.add("Country" to it) }
                meta["ip_timezone"]?.takeIf { it.isNotBlank() }?.let { rows.add("Timezone" to it) }
                meta["ipwhois_city"]?.takeIf { it.isNotBlank() && meta["ip_city"].isNullOrBlank() }?.let { rows.add("City (ipwho.is)" to it) }
                meta["ipwhois_country"]?.takeIf { it.isNotBlank() && meta["ip_country"].isNullOrBlank() }?.let { rows.add("Country (ipwho.is)" to it) }
                meta["ipwhois_timezone"]?.takeIf { it.isNotBlank() }?.let { rows.add("Timezone (ipwho.is)" to it) }

                rows.add(sec("◈ NETWORK"))
                meta["ip_isp"]?.takeIf { it.isNotBlank() }?.let { rows.add("ISP" to it) }
                meta["ip_org"]?.takeIf { it.isNotBlank() }?.let { rows.add("Org" to it) }
                meta["ip_asn"]?.takeIf { it.isNotBlank() }?.let { rows.add("ASN" to it) }
                meta["ipwhois_org"]?.takeIf { it.isNotBlank() && meta["ip_org"].isNullOrBlank() }?.let { rows.add("Org (ipwho.is)" to it) }
                meta["ipinfo_org"]?.takeIf { it.isNotBlank() }?.let { rows.add("Org (ipinfo.io)" to it) }
                meta["ipinfo_hostname"]?.takeIf { it.isNotBlank() }?.let { rows.add("Hostname" to it) }
                meta["ipinfo_postal"]?.takeIf { it.isNotBlank() }?.let { rows.add("Postal Code" to it) }
                meta["robtex_as_name"]?.takeIf { it.isNotBlank() }?.let { rows.add("AS Name" to it) }
                meta["robtex_bgp_route"]?.takeIf { it.isNotBlank() }?.let { rows.add("BGP Route" to it) }
                meta["robtex_passive_dns"]?.takeIf { it.isNotBlank() }?.let { rows.add("Passive DNS" to it) }

                val hasPorts = !meta["shodan_ports"].isNullOrBlank()
                val hasVulns = !meta["shodan_vulns"].isNullOrBlank()
                if (hasPorts || hasVulns) {
                    rows.add(sec("◈ EXPOSURE"))
                    meta["shodan_ports"]?.takeIf { it.isNotBlank() }?.let { rows.add("Open Ports" to it) }
                    meta["shodan_hostnames"]?.takeIf { it.isNotBlank() }?.let { rows.add("Hostnames" to it) }
                    meta["shodan_tags"]?.takeIf { it.isNotBlank() }?.let { rows.add("Tags" to it) }
                    meta["shodan_vulns"]?.takeIf { it.isNotBlank() }?.let { rows.add("⚠ CVEs" to it) }
                }

                val hasThreats = !meta["greynoise_classification"].isNullOrBlank()
                    || (meta["abuseipdb_score"]?.toIntOrNull() ?: 0) > 0
                    || (meta["urlhaus_urls_count"]?.toIntOrNull() ?: 0) > 0
                    || (meta["otx_pulse_count"]?.toIntOrNull() ?: 0) > 0
                if (hasThreats) {
                    rows.add(sec("◈ THREAT INTEL"))
                    meta["greynoise_noise"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Internet Scanner" to "This IP actively scans the internet") }
                    meta["greynoise_riot"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("Common Service (RIOT)" to "Benign mass-scanner") }
                    meta["greynoise_classification"]?.takeIf { it.isNotBlank() }?.let { rows.add("GreyNoise" to it.replaceFirstChar { c -> c.uppercase() }) }
                    meta["greynoise_name"]?.takeIf { it.isNotBlank() }?.let { rows.add("GreyNoise Actor" to it) }
                    meta["greynoise_last_seen"]?.takeIf { it.isNotBlank() }?.let { rows.add("Last Seen" to it) }
                    meta["abuseipdb_score"]?.let { s -> if ((s.toIntOrNull() ?: 0) > 0) rows.add("⚠ Abuse Score" to "$s%") }
                    meta["abuseipdb_reports"]?.let { r -> if ((r.toIntOrNull() ?: 0) > 0) rows.add("Abuse Reports" to r) }
                    meta["abuseipdb_isp"]?.takeIf { it.isNotBlank() }?.let { rows.add("AbuseIPDB ISP" to it) }
                    meta["urlhaus_urls_count"]?.let { c -> if ((c.toIntOrNull() ?: 0) > 0) rows.add("⚠ Malware URLs (URLhaus)" to "$c tracked") }
                    meta["maltiverse_classification"]?.takeIf { it.isNotBlank() }?.let { rows.add("Maltiverse" to it) }
                    meta["maltiverse_blacklists"]?.takeIf { it.isNotBlank() }?.let { rows.add("Blacklists" to it) }
                    meta["otx_pulse_count"]?.let { p -> if ((p.toIntOrNull() ?: 0) > 0) rows.add("⚠ OTX Threat Pulses" to "$p hit${if ((p.toIntOrNull() ?: 1) != 1) "s" else ""}") }
                    meta["threatcrowd_subdomains"]?.takeIf { it.isNotBlank() }?.let { rows.add("ThreatCrowd Subdomains" to it) }
                    meta["threatcrowd_emails"]?.takeIf { it.isNotBlank() }?.let { rows.add("ThreatCrowd Emails" to it) }
                    meta["threatcrowd_resolutions"]?.takeIf { it.isNotBlank() }?.let { rows.add("IP History" to it) }
                }

                val hasDomain = !meta["rdap_registrar"].isNullOrBlank() || !meta["whois"].isNullOrBlank()
                    || !meta["subdomains"].isNullOrBlank() || (meta["cert_count"]?.toIntOrNull() ?: 0) > 0
                if (hasDomain) {
                    rows.add(sec("◈ DOMAIN INTEL"))
                    meta["rdap_registrar"]?.takeIf { it.isNotBlank() }?.let { rows.add("Registrar" to it) }
                    meta["rdap_registered"]?.takeIf { it.isNotBlank() }?.let { rows.add("Registered" to it) }
                    meta["rdap_expiry"]?.takeIf { it.isNotBlank() }?.let { rows.add("Expires" to it) }
                    meta["rdap_nameservers"]?.takeIf { it.isNotBlank() }?.let { rows.add("Nameservers" to it) }
                    meta["subdomains"]?.takeIf { it.isNotBlank() }?.let { rows.add("SSL Subdomains" to it) }
                    meta["cert_count"]?.let { rows.add("SSL Certs Found" to it) }
                    meta["wayback_count"]?.let { c -> if ((c.toIntOrNull() ?: 0) > 0) rows.add("Wayback Snapshots" to c) }
                    meta["wayback_first"]?.takeIf { it.isNotBlank() }?.let { rows.add("First Archived" to it) }
                    meta["wayback_last"]?.takeIf { it.isNotBlank() }?.let { rows.add("Last Archived" to it) }
                    meta["hackertarget_hostsearch"]?.takeIf { it.isNotBlank() }?.let {
                        it.lines().filter { l -> l.isNotBlank() }.take(10).forEach { line -> rows.add("Host" to line) }
                    }
                    meta["dns"]?.takeIf { it.isNotBlank() }?.let { rows.add("DNS Records" to it.take(500)) }
                    meta["whois"]?.takeIf { it.isNotBlank() }?.let { rows.add("WHOIS" to it.take(600)) }
                }

                rows.add(sec("─── EXTERNAL SOURCES ───"))
                meta["shodan_link"]?.let { rows.add("Shodan →" to it) }
                meta["urlscan_link"]?.let { rows.add("URLScan →" to it) }
                meta["virustotal_link"]?.let { rows.add("VirusTotal →" to it) }
            }
            "username" -> {
                val checked = meta["sites_checked"]?.toIntOrNull() ?: 0
                val found = meta["sites_found"]?.toIntOrNull() ?: 0
                if (checked > 0) rows.add(sec("◈ PLATFORM SCAN: $found FOUND / $checked CHECKED"))

                val hasProfile = !meta["github_profile"].isNullOrBlank() || !meta["keybase_name"].isNullOrBlank()
                    || (meta["hackernews_karma"]?.toIntOrNull() ?: 0) > 0 || !meta["devto_name"].isNullOrBlank()
                if (hasProfile) {
                    rows.add(sec("◈ EXTRACTED PROFILE DATA"))
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
                    meta["keybase_proofs"]?.takeIf { it.isNotBlank() }?.let { rows.add("Keybase Proofs" to it) }
                    meta["hackernews_karma"]?.let { k -> if ((k.toIntOrNull() ?: 0) > 0) rows.add("HackerNews Karma" to k) }
                    meta["hackernews_about"]?.takeIf { it.isNotBlank() }?.let { rows.add("HackerNews About" to it) }
                    meta["devto_name"]?.takeIf { it.isNotBlank() }?.let { rows.add("Dev.to Name" to it) }
                    meta["devto_location"]?.takeIf { it.isNotBlank() }?.let { rows.add("Dev.to Location" to it) }
                    meta["devto_summary"]?.takeIf { it.isNotBlank() }?.let { rows.add("Dev.to Bio" to it) }
                    meta["devto_joined"]?.takeIf { it.isNotBlank() }?.let { rows.add("Dev.to Joined" to it) }
                }

                meta["found_urls"]?.takeIf { it.isNotBlank() }?.let {
                    rows.add(sec("◈ CONFIRMED PROFILES ($found)"))
                    it.lines().filter { l -> l.isNotBlank() }.forEach { line ->
                        val parts = line.split(": ", limit = 2)
                        rows.add("✓ ${parts.firstOrNull() ?: "Platform"}" to (parts.getOrNull(1) ?: line))
                    }
                }
            }
            "person" -> {
                rows.add(sec("◈ IDENTITY"))
                val age = meta["tt_ages"] ?: meta["uspb_age"]
                age?.takeIf { it.isNotBlank() }?.let { rows.add("Age" to it) }
                meta["demographics_gender"]?.takeIf { it.isNotBlank() }?.let { rows.add("Gender Est." to it) }
                meta["demographics_age_estimate"]?.takeIf { it.isNotBlank() }?.let {
                    if (age.isNullOrBlank()) rows.add("Age Est." to it)
                }
                meta["demographics_nationality"]?.takeIf { it.isNotBlank() }?.let { rows.add("Nationality Est." to it) }
                meta["tt_relatives"]?.takeIf { it.isNotBlank() }?.let { rows.add("Associates / Relatives" to it) }

                val hasLocations = !meta["tt_locations"].isNullOrBlank() || !meta["uspb_addresses"].isNullOrBlank()
                if (hasLocations) {
                    rows.add(sec("◈ KNOWN LOCATIONS"))
                    meta["tt_locations"]?.takeIf { it.isNotBlank() }?.let {
                        it.split(",").filter { s -> s.isNotBlank() }.take(5).forEach { loc -> rows.add("Location" to loc.trim()) }
                    }
                    meta["uspb_addresses"]?.takeIf { it.isNotBlank() }?.let {
                        it.split(" | ").filter { a -> a.isNotBlank() }.take(4).forEach { addr -> rows.add("Address" to addr.trim()) }
                    }
                }

                val hasContacts = !meta["tt_phones"].isNullOrBlank() || !meta["uspb_phones"].isNullOrBlank()
                if (hasContacts) {
                    rows.add(sec("◈ CONTACT INTEL"))
                    val phone = meta["tt_phones"] ?: meta["uspb_phones"]
                    phone?.takeIf { it.isNotBlank() }?.let { rows.add("Phone" to it) }
                }

                val arrestCount = meta["arrest_count"]?.toIntOrNull() ?: 0
                val courtCount = meta["courtlistener_count"]?.toIntOrNull() ?: 0
                if (arrestCount > 0 || courtCount > 0) {
                    rows.add(sec("◈ LEGAL RECORDS"))
                    if (arrestCount > 0) {
                        rows.add("⚠ Arrests" to "$arrestCount record${if (arrestCount != 1) "s" else ""} on file")
                        meta["arrest_records"]?.takeIf { it.isNotBlank() }?.let {
                            it.lines().filter { l -> l.isNotBlank() }.take(5).forEach { record -> rows.add("Arrest" to record) }
                        }
                    }
                    if (courtCount > 0) rows.add("Court Cases" to "$courtCount case${if (courtCount != 1) "s" else ""} found")
                }

                val officerCount = meta["officer_matches"]?.toIntOrNull() ?: 0
                val secHits = meta["sec_person_hits"]?.toIntOrNull() ?: 0
                val fecCount = meta["fec_candidate_count"]?.toIntOrNull() ?: 0
                if (officerCount > 0 || secHits > 0 || fecCount > 0) {
                    rows.add(sec("◈ CORPORATE & FINANCIAL"))
                    meta["officer_details"]?.takeIf { it.isNotBlank() }?.let {
                        it.lines().filter { l -> l.isNotBlank() }.take(5).forEach { detail -> rows.add("Corporate Role" to detail) }
                    }
                    if (secHits > 0) {
                        rows.add("SEC EDGAR Filings" to "$secHits Form-4 filing${if (secHits != 1) "s" else ""}")
                        meta["sec_person_entities"]?.takeIf { it.isNotBlank() }?.let { rows.add("Affiliated Companies" to it) }
                    }
                    meta["fec_candidates"]?.takeIf { it.isNotBlank() }?.let {
                        it.lines().filter { l -> l.isNotBlank() }.forEach { r -> rows.add("FEC Campaign" to r) }
                    }
                }

                val wikiHits = meta["wikipedia_hits"]?.toIntOrNull() ?: 0
                val newsCount = meta["news_article_count"]?.toIntOrNull() ?: 0
                if (wikiHits > 0 || newsCount > 0 || !meta["wikidata_descriptions"].isNullOrBlank()) {
                    rows.add(sec("◈ PUBLIC RECORDS"))
                    meta["wikipedia_titles"]?.takeIf { it.isNotBlank() }?.let { rows.add("Wikipedia" to it) }
                    meta["wikidata_descriptions"]?.takeIf { it.isNotBlank() }?.let { rows.add("WikiData" to it) }
                    if (newsCount > 0) {
                        rows.add("News Mentions" to "$newsCount article${if (newsCount != 1) "s" else ""}")
                        meta["news_titles"]?.takeIf { it.isNotBlank() }?.let {
                            it.lines().filter { l -> l.isNotBlank() }.take(4).forEach { t -> rows.add("Headline" to t) }
                        }
                    }
                }

                val hasSearchIntel = !meta["cse_snippets"].isNullOrBlank() || !meta["bing_snippets"].isNullOrBlank()
                if (hasSearchIntel) {
                    rows.add(sec("◈ SEARCH ENGINE INTEL"))
                    meta["cse_snippets"]?.takeIf { it.isNotBlank() }?.let {
                        it.split("\n---\n").filter { s -> s.isNotBlank() }.take(5).forEach { snippet ->
                            rows.add("Google CSE" to snippet.trim())
                        }
                    }
                    meta["bing_snippets"]?.takeIf { it.isNotBlank() }?.let {
                        it.split("\n---\n").filter { s -> s.isNotBlank() }.take(4).forEach { snippet ->
                            rows.add("Bing" to snippet.trim())
                        }
                    }
                }

                rows.add(sec("─── EXTERNAL SOURCES ───"))
                meta["courtlistener_link"]?.let { rows.add("CourtListener →" to it) }
                meta["judyrecords_link"]?.let { rows.add("JudyRecords →" to it) }
                meta["wikipedia_link"]?.let { rows.add("Wikipedia →" to it) }
                meta["wikidata_link"]?.let { rows.add("WikiData →" to it) }
                meta["fec_link"]?.let { rows.add("FEC Campaign Finance →" to it) }
                meta["sec_person_link"]?.let { rows.add("SEC EDGAR →" to it) }
                meta["news_link"]?.let { rows.add("Google News →" to it) }
                meta["thatsthem_link"]?.let { rows.add("ThatsThem →" to it) }
                meta["usphonebook_link"]?.let { rows.add("USPhoneBook →" to it) }
                meta["spokeo_link"]?.let { rows.add("Spokeo →" to it) }
                meta["beenverified_link"]?.let { rows.add("BeenVerified →" to it) }
                meta["fastpeoplesearch_link"]?.let { rows.add("FastPeopleSearch →" to it) }
                meta["truthfinder_link"]?.let { rows.add("TruthFinder →" to it) }
                meta["familytreenow_link"]?.let { rows.add("FamilyTreeNow →" to it) }
                meta["intelius_link"]?.let { rows.add("Intelius →" to it) }
                meta["zabasearch_link"]?.let { rows.add("ZabaSearch →" to it) }
                meta["linkedin_person_link"]?.let { rows.add("LinkedIn →" to it) }
                meta["facebook_person_link"]?.let { rows.add("Facebook →" to it) }

                val dorkCount = meta["shadowdork_count"]?.toIntOrNull() ?: 0
                if (dorkCount > 0) {
                    rows.add(sec("─── SHADOWDORK QUERIES ($dorkCount) ───"))
                    meta["dork_identity"]?.let { rows.add("Identity →" to it) }
                    meta["dork_relatives"]?.let { rows.add("Relatives →" to it) }
                    meta["dork_address"]?.let { rows.add("Address →" to it) }
                    meta["dork_criminal"]?.let { rows.add("Criminal →" to it) }
                    meta["dork_property"]?.let { rows.add("Property →" to it) }
                    meta["dork_vehicle"]?.let { rows.add("Vehicle →" to it) }
                    meta["dork_social"]?.let { rows.add("Social →" to it) }
                    meta["dork_leaks"]?.let { rows.add("⚠ Data Leaks →" to it) }
                }
            }
            "company" -> {
                rows.add(sec("◈ COMPANY RECORDS"))
                meta["companies"]?.takeIf { it.isNotBlank() }?.let {
                    it.lines().filter { l -> l.isNotBlank() }.forEach { company -> rows.add("Company" to company) }
                }

                val officerCount = meta["officer_count"]?.toIntOrNull() ?: 0
                if (officerCount > 0) {
                    rows.add(sec("◈ OFFICERS & EXECUTIVES"))
                    meta["officers"]?.takeIf { it.isNotBlank() }?.let {
                        it.lines().filter { l -> l.isNotBlank() }.forEach { officer -> rows.add("Officer" to officer) }
                    }
                }

                val emailCount = meta["hunter_emails_count"]?.toIntOrNull() ?: 0
                if (emailCount > 0) {
                    rows.add(sec("◈ EMAIL DISCOVERY"))
                    rows.add("Emails Found" to "$emailCount address${if (emailCount != 1) "es" else ""}")
                    meta["hunter_emails"]?.takeIf { it.isNotBlank() }?.let { rows.add("Email Addresses" to it) }
                }

                val secFilings = meta["sec_filings_count"]?.toIntOrNull() ?: 0
                if (secFilings > 0 || !meta["wikidata_company_descriptions"].isNullOrBlank()) {
                    rows.add(sec("◈ PUBLIC FILINGS & INTEL"))
                    meta["wikidata_company_descriptions"]?.takeIf { it.isNotBlank() }?.let { rows.add("WikiData Entity" to it) }
                    if (secFilings > 0) {
                        rows.add("SEC Filings" to "$secFilings found")
                        meta["sec_filing_types"]?.takeIf { it.isNotBlank() }?.let { rows.add("Filing Types" to it) }
                    }
                }

                rows.add(sec("─── EXTERNAL SOURCES ───"))
                meta["opencorporates_link"]?.let { rows.add("OpenCorporates →" to it) }
                meta["wikidata_company_link"]?.let { rows.add("WikiData →" to it) }
                meta["sec_link"]?.let { rows.add("SEC EDGAR →" to it) }
                meta["crunchbase_link"]?.let { rows.add("Crunchbase →" to it) }
                meta["linkedin_company_link"]?.let { rows.add("LinkedIn →" to it) }
            }
            "phone" -> {
                rows.add(sec("◈ LINE VALIDATION"))
                meta["numverify_valid"]?.let { v -> rows.add("Valid Number" to if (v == "true") "Yes ✓" else "No ✗") }
                meta["numverify_country"]?.takeIf { it.isNotBlank() }?.let { rows.add("Country" to it) }
                meta["numverify_carrier"]?.takeIf { it.isNotBlank() }?.let { rows.add("Carrier" to it) }
                meta["numverify_line_type"]?.takeIf { it.isNotBlank() }?.let { rows.add("Line Type" to it) }
                meta["numverify_location"]?.takeIf { it.isNotBlank() }?.let { rows.add("Location" to it) }
                meta["numverify_intl"]?.takeIf { it.isNotBlank() }?.let { rows.add("Intl Format" to it) }

                rows.add(sec("─── EXTERNAL LOOKUP ───"))
                meta["truecaller_link"]?.let { rows.add("TrueCaller →" to it) }
                meta["whitepages_link"]?.let { rows.add("WhitePages →" to it) }
                meta["spokeo_link"]?.let { rows.add("Spokeo →" to it) }
            }
            "image" -> {
                rows.add(sec("◈ REVERSE IMAGE SEARCH"))
                rows.add("Instructions" to "Tap each source below to search for this image")
                rows.add(sec("─── SEARCH ENGINES ───"))
                meta["tineye_link"]?.let { rows.add("TinEye →" to it) }
                meta["google_lens_link"]?.let { rows.add("Google Lens →" to it) }
                meta["yandex_images_link"]?.let { rows.add("Yandex Images →" to it) }
                meta["facecheck_id_link"]?.let { rows.add("FaceCheck.id →" to it) }
                meta["bing_visual_search_link"]?.let { rows.add("Bing Visual →" to it) }
            }
        }
        return rows
    }

    private fun sectionLabel(type: String) = when (type) {
        "email" -> "EMAIL INTELLIGENCE"
        "ip", "domain" -> "NETWORK INTELLIGENCE"
        "username" -> "DIGITAL FOOTPRINT"
        "company" -> "CORPORATE INTELLIGENCE"
        "phone" -> "SIGNAL INTELLIGENCE"
        "image" -> "VISUAL INTELLIGENCE"
        else -> "SUBJECT DOSSIER"
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
            val (label, value) = rows[position]
            val isSectionHeader = value.isEmpty() && (label.startsWith("◈") || label.startsWith("─"))
            val isLink = value.startsWith("http://") || value.startsWith("https://")

            if (isSectionHeader) {
                holder.b.tvRowLabel.text = ""
                holder.b.tvRowValue.text = label
                holder.b.tvRowValue.setTextColor(0xFF00FF41.toInt())
                holder.b.tvRowValue.textSize = 10f
                holder.b.tvRowValue.letterSpacing = 0.12f
                holder.b.tvRowValue.typeface = android.graphics.Typeface.MONOSPACE
                holder.b.root.setOnClickListener(null)
            } else {
                holder.b.tvRowLabel.text = label
                holder.b.tvRowValue.text = value
                holder.b.tvRowValue.textSize = 13f
                holder.b.tvRowValue.letterSpacing = 0f
                holder.b.tvRowValue.typeface = android.graphics.Typeface.MONOSPACE
                if (isLink) {
                    holder.b.tvRowValue.setTextColor(0xFFFFB300.toInt())
                    holder.b.root.setOnClickListener {
                        it.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)))
                    }
                } else {
                    holder.b.tvRowValue.setTextColor(ContextCompat.getColor(holder.b.root.context, R.color.text_primary))
                    holder.b.root.setOnClickListener(null)
                }
            }
        }

        override fun getItemCount() = rows.size
    }
}
