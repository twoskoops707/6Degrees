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
            val ageDisplay = metadata["fps_age"] ?: metadata["tt_ages"]?.split(", ")?.firstOrNull()?.trim()
                ?: metadata["uspb_age"] ?: metadata["demographics_age_estimate"]
            binding.jobTitle.text = buildString {
                ageDisplay?.let { append("Age: $it") }
                metadata["demographics_gender"]?.let { g -> if (isNotEmpty()) append(" · "); append(g) }
                metadata["ftn_birth_year"]?.let { y -> if (isNotEmpty()) append(" · "); append("b.~$y") }
            }
            binding.location.text = metadata["uspb_addresses"]?.split(" | ")?.firstOrNull()?.trim()
                ?: metadata["tt_locations"]?.split(" | ")?.firstOrNull()?.trim()
                ?: metadata["fps_locations"]?.split(" | ")?.firstOrNull()?.trim() ?: ""
            binding.bio.text = buildString {
                val allPhones = linkedSetOf<String>()
                metadata["tt_phones"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allPhones.add(it) }
                metadata["uspb_phones"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allPhones.add(it) }
                metadata["fps_phones"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allPhones.add(it) }
                if (allPhones.isNotEmpty()) append("Phone: ${allPhones.first()}")
                val allRel = linkedSetOf<String>()
                metadata["tt_relatives"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allRel.add(it) }
                metadata["fps_relatives"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allRel.add(it) }
                metadata["ftn_relatives"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allRel.add(it) }
                if (allRel.isNotEmpty()) { if (isNotEmpty()) append("\n"); append("Associates: ${allRel.take(3).joinToString(", ")}") }
                if (isNotEmpty()) append("\n")
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

        val ipqueryRisk = meta["ipquery_risk_score"]?.toIntOrNull() ?: 0
        if (ipqueryRisk > 30) { score += minOf(ipqueryRisk / 2, 30); flags.add("risk score $ipqueryRisk (IPQuery)") }

        val ipqsIpFraud = meta["ipqs_ip_fraud_score"]?.toIntOrNull() ?: 0
        if (ipqsIpFraud > 30) { score += minOf(ipqsIpFraud / 2, 30); flags.add("IP fraud score $ipqsIpFraud") }

        val ipqsEmailFraud = meta["ipqs_email_fraud_score"]?.toIntOrNull() ?: 0
        if (ipqsEmailFraud > 50) { score += minOf(ipqsEmailFraud / 3, 20); flags.add("email fraud score $ipqsEmailFraud") }

        val ipqsPhoneFraud = meta["ipqs_phone_fraud_score"]?.toIntOrNull() ?: 0
        if (ipqsPhoneFraud > 50) { score += minOf(ipqsPhoneFraud / 3, 20); flags.add("phone fraud score $ipqsPhoneFraud") }

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

    private fun themeBase(): String {
        val prefs = requireContext().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        return prefs.getString("pref_theme_base", "modern") ?: "modern"
    }

    private fun sec(label: String): Pair<String, String> {
        val clean = label.trimStart().removePrefix("◈ ").removePrefix("─── ").removeSuffix(" ───").trim()
        val prefix = when (themeBase()) {
            "hacker"   -> "> "
            "tactical" -> "══ "
            else       -> "◈ "
        }
        val suffix = if (themeBase() == "tactical") " ══" else ""
        return "$prefix$clean$suffix" to ""
    }

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
                        rows.add("⚠ HIBP Breaches" to "$hibpCount breach${if (hibpCount != 1) "es" else ""} found")
                        val nsfwDomains = setOf("ashleymadison.com","adultfriendfinder.com","fling.com","mate1.com","penthouse.com","brazzers.com","naughtyamerica.com","xvideos.com","pornhub.com")
                        val nsfwDataClasses = setOf("Sexual fetishes","Sexual preferences","Sexual orientation","Adult content purchases","Intimate photos","Nude photos","Profile photos")
                        meta["hibp_breach_details"]?.takeIf { it.isNotBlank() }?.let {
                            it.lines().filter { l -> l.isNotBlank() }.forEach { detail ->
                                val lDetail = detail.lowercase()
                                val isNsfw = nsfwDomains.any { d -> lDetail.contains(d) }
                                    || nsfwDataClasses.any { dc -> detail.contains(dc, ignoreCase = true) }
                                    || detail.contains("[SENSITIVE]", ignoreCase = true)
                                    || lDetail.contains("ashley madison") || lDetail.contains("adult friend finder")
                                    || lDetail.contains("fling.com") || lDetail.contains("penthouse")
                                val label = if (isNsfw) "⚠ NSFW Breach" else "Breach"
                                rows.add(label to detail)
                            }
                        } ?: meta["hibp_breaches"]?.takeIf { it.isNotBlank() }?.let { rows.add("Breach Names" to it) }
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

                val hasFullContact = !meta["fullcontact_name"].isNullOrBlank() || !meta["fullcontact_location"].isNullOrBlank()
                if (hasFullContact) {
                    rows.add(sec("◈ IDENTITY ENRICHMENT"))
                    meta["fullcontact_name"]?.takeIf { it.isNotBlank() }?.let { rows.add("Full Name" to it) }
                    meta["fullcontact_location"]?.takeIf { it.isNotBlank() }?.let { rows.add("Location" to it) }
                    meta["fullcontact_title"]?.takeIf { it.isNotBlank() }?.let { rows.add("Job Title" to it) }
                    meta["fullcontact_org"]?.takeIf { it.isNotBlank() }?.let { rows.add("Employer" to it) }
                    meta["fullcontact_age_range"]?.takeIf { it.isNotBlank() }?.let { rows.add("Age Range" to it) }
                    meta["fullcontact_gender"]?.takeIf { it.isNotBlank() }?.let { rows.add("Gender" to it) }
                    meta["fullcontact_twitter"]?.takeIf { it.isNotBlank() }?.let { rows.add("Twitter" to "@$it") }
                    meta["fullcontact_linkedin"]?.takeIf { it.isNotBlank() }?.let { rows.add("LinkedIn" to it) }
                }

                val ipqsLeaked = meta["ipqs_email_leaked"]?.toBooleanStrictOrNull() ?: false
                val ipqsFraud = meta["ipqs_email_fraud_score"]?.toIntOrNull() ?: -1
                if (ipqsLeaked || ipqsFraud >= 0) {
                    rows.add(sec("◈ IPQS INTEL"))
                    if (ipqsFraud >= 0) rows.add("IPQS Fraud Score" to "$ipqsFraud / 100${if (ipqsFraud > 70) " ⚠ HIGH RISK" else ""}")
                    if (ipqsLeaked) rows.add("⚠ Dark Web Leaked" to "Email found in dark web leaks")
                    meta["ipqs_email_suspect"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Suspect" to "Flagged as suspect by IPQS") }
                    meta["ipqs_email_disposable"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("Disposable" to "Temporary email service") }
                    meta["ipqs_email_domain"]?.takeIf { it.isNotBlank() }?.let { rows.add("Email Domain" to it) }
                }

                val hasThreat = !meta["threatcrowd_email_domains"].isNullOrBlank() || !meta["hackertarget_email_hosts"].isNullOrBlank()
                if (hasThreat) {
                    rows.add(sec("◈ THREAT INTEL"))
                    meta["threatcrowd_email_domains"]?.takeIf { it.isNotBlank() }?.let { rows.add("Linked Domains" to it) }
                    meta["threatcrowd_email_refs"]?.let { r -> if ((r.toIntOrNull() ?: 0) > 0) rows.add("ThreatCrowd References" to r) }
                    meta["hackertarget_email_hosts"]?.takeIf { it.isNotBlank() }?.let { rows.add("Associated Hosts" to it) }
                }

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
                meta["ipquery_isp"]?.takeIf { it.isNotBlank() }?.let { rows.add("ISP (IPQuery)" to it) }
                meta["ipquery_asn"]?.takeIf { it.isNotBlank() }?.let { rows.add("ASN (IPQuery)" to it) }
                meta["ipquery_city"]?.takeIf { it.isNotBlank() }?.let { rows.add("City (IPQuery)" to it) }
                meta["ipquery_country"]?.takeIf { it.isNotBlank() }?.let { rows.add("Country (IPQuery)" to it) }
                meta["ipqs_ip_isp"]?.takeIf { it.isNotBlank() }?.let { rows.add("ISP (IPQS)" to it) }
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

                val ipqueryRisk = meta["ipquery_risk_score"]?.toIntOrNull() ?: -1
                val ipqsIpFraud = meta["ipqs_ip_fraud_score"]?.toIntOrNull() ?: -1
                val hasThreats = !meta["greynoise_classification"].isNullOrBlank()
                    || (meta["abuseipdb_score"]?.toIntOrNull() ?: 0) > 0
                    || (meta["urlhaus_urls_count"]?.toIntOrNull() ?: 0) > 0
                    || (meta["otx_pulse_count"]?.toIntOrNull() ?: 0) > 0
                    || ipqueryRisk > 0 || ipqsIpFraud > 0
                if (hasThreats) {
                    rows.add(sec("◈ THREAT INTEL"))
                    if (ipqueryRisk >= 0) {
                        rows.add("IPQuery Risk Score" to "$ipqueryRisk / 100${if (ipqueryRisk > 70) " ⚠ HIGH" else if (ipqueryRisk > 30) " ⚠ Moderate" else " — Low"}")
                        meta["ipquery_vpn"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ VPN Detected" to "IP is a known VPN exit node") }
                        meta["ipquery_proxy"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Proxy Detected" to "IP is a known proxy") }
                        meta["ipquery_tor"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Tor Exit Node" to "IP is a Tor exit node") }
                        meta["ipquery_datacenter"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("Datacenter IP" to "Hosted in a datacenter") }
                    }
                    if (ipqsIpFraud >= 0) {
                        rows.add("IPQS Fraud Score" to "$ipqsIpFraud / 100${if (ipqsIpFraud > 70) " ⚠ HIGH" else ""}")
                        meta["ipqs_ip_vpn"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ VPN (IPQS)" to "Known VPN") }
                        meta["ipqs_ip_proxy"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Proxy (IPQS)" to "Known proxy") }
                        meta["ipqs_ip_tor"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Tor (IPQS)" to "Tor node") }
                        meta["ipqs_ip_bot"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Bot (IPQS)" to "Bot activity detected") }
                    }
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

                val domainsdbTotal = meta["domainsdb_total"]?.toIntOrNull() ?: 0
                val hasDomain = !meta["rdap_registrar"].isNullOrBlank() || !meta["whois"].isNullOrBlank()
                    || !meta["subdomains"].isNullOrBlank() || (meta["cert_count"]?.toIntOrNull() ?: 0) > 0
                    || domainsdbTotal > 0
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
                    if (domainsdbTotal > 0) {
                        rows.add("DomainsDB Registrations" to "$domainsdbTotal domain${if (domainsdbTotal != 1) "s" else ""} found")
                        meta["domainsdb_domains"]?.takeIf { it.isNotBlank() }?.let { rows.add("Registered Domains" to it) }
                    }
                    meta["hackertarget_hostsearch"]?.takeIf { it.isNotBlank() }?.let {
                        it.lines().filter { l -> l.isNotBlank() }.take(10).forEach { line -> rows.add("Host" to line) }
                    }
                    meta["dns"]?.takeIf { it.isNotBlank() }?.let { rows.add("DNS Records" to it.take(500)) }
                    meta["whois"]?.takeIf { it.isNotBlank() }?.let { rows.add("WHOIS" to it.take(600)) }
                }

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
                    val siteDesc = mapOf(
                        "GitHub" to "Code hosting & developer collaboration",
                        "GitLab" to "DevOps & source code management",
                        "Bitbucket" to "Git code hosting by Atlassian",
                        "Reddit" to "Social news aggregation & discussion forum",
                        "Twitter" to "Microblogging & social media platform",
                        "Instagram" to "Photo & video sharing social network",
                        "TikTok" to "Short-form video sharing platform",
                        "YouTube" to "Video sharing & streaming platform",
                        "Facebook" to "Social networking platform",
                        "LinkedIn" to "Professional networking & career platform",
                        "Pinterest" to "Visual discovery & idea sharing platform",
                        "Snapchat" to "Ephemeral photo & video messaging app",
                        "Twitch" to "Live streaming platform, primarily gaming",
                        "Discord" to "Voice, video & text community platform",
                        "Telegram" to "Encrypted messaging & channel platform",
                        "Patreon" to "Creator monetization & membership platform",
                        "Etsy" to "Marketplace for handmade & vintage goods",
                        "Steam" to "Video game distribution & community platform",
                        "Spotify" to "Music & podcast streaming service",
                        "SoundCloud" to "Music sharing & audio streaming platform",
                        "Bandcamp" to "Music publishing & fan support platform",
                        "Medium" to "Online publishing & blogging platform",
                        "Substack" to "Newsletter & subscription writing platform",
                        "Dev.to" to "Developer blogging & community platform",
                        "Hashnode" to "Developer blogging platform",
                        "HackerNews" to "Tech news & startup discussion forum (Y Combinator)",
                        "ProductHunt" to "Platform to discover new tech products",
                        "AngelList" to "Startup & investor networking platform",
                        "Crunchbase" to "Business information & startup database",
                        "Keybase" to "Encrypted messaging & identity verification",
                        "Gravatar" to "Globally recognized avatar service tied to email",
                        "About.me" to "Personal profile & bio hosting",
                        "Linktree" to "Link aggregation bio page service",
                        "Behance" to "Creative portfolio showcase by Adobe",
                        "Dribbble" to "Designer portfolio & community showcase",
                        "Flickr" to "Photo sharing & hosting community",
                        "500px" to "Photography community & portfolio platform",
                        "Vimeo" to "High-quality video hosting & sharing platform",
                        "Dailymotion" to "Video sharing & streaming platform",
                        "Tumblr" to "Microblogging & social media platform",
                        "Blogger" to "Google-owned blogging platform",
                        "WordPress" to "Content management & blogging platform",
                        "Quora" to "Q&A knowledge sharing platform",
                        "StackOverflow" to "Developer Q&A and knowledge community",
                        "StackExchange" to "Network of expert Q&A communities",
                        "Fiverr" to "Freelance services marketplace",
                        "Upwork" to "Freelance work & hiring platform",
                        "Freelancer" to "Online freelancing & crowdsourcing marketplace",
                        "Replit" to "Browser-based coding & collaboration platform",
                        "CodePen" to "Frontend code editor & social dev platform",
                        "JSFiddle" to "JavaScript testing & sharing platform",
                        "npm" to "Node.js package manager & software registry",
                        "PyPI" to "Python package index & software repository",
                        "DockerHub" to "Container image registry & sharing platform",
                        "Last.fm" to "Music tracking & social recommendation service",
                        "Goodreads" to "Book discovery & reader community platform",
                        "Chess.com" to "Online chess platform & community",
                        "Lichess" to "Free & open-source online chess server",
                        "Duolingo" to "Language learning & gamification platform",
                        "Wikipedia" to "Free online encyclopedia",
                        "Wattpad" to "Story sharing & reading community",
                        "Roblox" to "Online game platform & creation system",
                        "Minecraft" to "Sandbox game with online community features",
                        "Fortnite" to "Online battle royale gaming platform"
                    )
                    it.lines().filter { l -> l.isNotBlank() }.forEach { line ->
                        val parts = line.split(": ", limit = 2)
                        val siteName = parts.firstOrNull() ?: "Platform"
                        val url = parts.getOrNull(1) ?: line
                        val desc = siteDesc[siteName]
                        val label = if (desc != null) "✓ $siteName — $desc" else "✓ $siteName"
                        rows.add(label to url)
                    }
                }
            }
            "person" -> {
                rows.add(sec("SUBJECT PROFILE"))
                val bestAge = meta["fps_age"] ?: meta["tt_ages"]?.split(", ")?.firstOrNull()?.trim()
                    ?: meta["uspb_age"] ?: meta["tps_age"] ?: meta["zaba_age"] ?: meta["411_age"] ?: meta["demographics_age_estimate"]
                bestAge?.takeIf { it.isNotBlank() }?.let { rows.add("Age" to it) }
                meta["ftn_birth_year"]?.takeIf { it.isNotBlank() }?.let { rows.add("Birth Year" to "~${it}") }
                meta["demographics_gender"]?.takeIf { it.isNotBlank() }?.let { rows.add("Gender" to it) }
                meta["demographics_nationality"]?.takeIf { it.isNotBlank() }?.let { rows.add("Nationality Est." to it) }

                val tpsNames = meta["tps_names"]?.takeIf { it.isNotBlank() }
                val ddgAbstract = meta["ddg_abstract"]?.takeIf { it.isNotBlank() }
                if (tpsNames != null || ddgAbstract != null) {
                    rows.add(sec("ADDITIONAL IDENTITY DATA"))
                    tpsNames?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { rows.add("Matched Name" to it) }
                    meta["tps_age"]?.takeIf { it.isNotBlank() }?.let { if (bestAge == null) rows.add("Age" to it) }
                    meta["tps_locations"]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { rows.add("Location" to it) }
                    meta["tps_relatives"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { rows.add("Associated Name" to it) }
                    meta["tps_link"]?.takeIf { it.isNotBlank() }?.let { rows.add("TruePeopleSearch →" to it) }
                    ddgAbstract?.let {
                        rows.add(sec("PUBLIC PROFILE (DuckDuckGo)"))
                        it.lines().filter { l -> l.isNotBlank() }.forEach { line -> rows.add("Profile" to line.trim()) }
                        meta["ddg_source"]?.takeIf { it.isNotBlank() }?.let { s -> rows.add("Source" to s) }
                        meta["ddg_url"]?.takeIf { it.isNotBlank() }?.let { u -> rows.add("Source Link →" to u) }
                        meta["ddg_infobox"]?.takeIf { it.isNotBlank() }?.let { info ->
                            info.lines().filter { l -> l.isNotBlank() }.forEach { fact -> rows.add("Fact" to fact) }
                        }
                    }
                }

                val sanctionsTotal = meta["opensanctions_total"]?.toIntOrNull() ?: 0
                if (sanctionsTotal > 0) {
                    rows.add(sec("⚠ SANCTIONS / PEP DATABASE"))
                    rows.add("⚠ OpenSanctions Hits" to "$sanctionsTotal match${if (sanctionsTotal != 1) "es" else ""} in global sanctions/PEP data")
                    meta["opensanctions_names"]?.takeIf { it.isNotBlank() }?.let { rows.add("Matched Names" to it) }
                    meta["opensanctions_datasets"]?.takeIf { it.isNotBlank() }?.let { rows.add("Datasets" to it) }
                    meta["opensanctions_countries"]?.takeIf { it.isNotBlank() }?.let { rows.add("Countries" to it) }
                    meta["opensanctions_link"]?.takeIf { it.isNotBlank() }?.let { rows.add("OpenSanctions →" to it) }
                }

                val allPhones = linkedSetOf<String>()
                meta["tt_phones"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allPhones.add(it) }
                meta["uspb_phones"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allPhones.add(it) }
                meta["fps_phones"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allPhones.add(it) }
                meta["tps_phones"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allPhones.add(it) }
                meta["zaba_phones"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allPhones.add(it) }
                meta["411_phones"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allPhones.add(it) }
                if (allPhones.isNotEmpty()) {
                    rows.add(sec("CONTACT INTEL"))
                    allPhones.forEach { rows.add("Phone Number" to it) }
                }

                val allAddresses = linkedSetOf<String>()
                meta["uspb_addresses"]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allAddresses.add(it) }
                meta["tt_locations"]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allAddresses.add(it) }
                meta["fps_locations"]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allAddresses.add(it) }
                meta["ftn_locations"]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allAddresses.add(it) }
                meta["tps_locations"]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allAddresses.add(it) }
                meta["zaba_addresses"]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allAddresses.add(it) }
                meta["zaba_locations"]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allAddresses.add(it) }
                meta["411_locations"]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allAddresses.add(it) }
                if (allAddresses.isNotEmpty()) {
                    rows.add(sec("ALL KNOWN ADDRESSES"))
                    allAddresses.forEach { rows.add("Address / Location" to it) }
                }

                val allRelatives = linkedSetOf<String>()
                meta["tt_relatives"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allRelatives.add(it) }
                meta["fps_relatives"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allRelatives.add(it) }
                meta["ftn_relatives"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allRelatives.add(it) }
                meta["tps_relatives"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allRelatives.add(it) }
                meta["411_relatives"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allRelatives.add(it) }
                meta["corpwiki_associates"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allRelatives.add(it) }
                if (allRelatives.isNotEmpty()) {
                    rows.add(sec("KNOWN RELATIVES / ASSOCIATES"))
                    allRelatives.forEach { rows.add("Name" to it) }
                }

                val judyCount = meta["judyrecords_count"]?.toIntOrNull() ?: 0
                val arrestCount = meta["arrest_count"]?.toIntOrNull() ?: 0
                val courtCount = meta["courtlistener_count"]?.toIntOrNull() ?: 0
                if (arrestCount > 0 || courtCount > 0 || judyCount > 0 || !meta["judyrecords_cases"].isNullOrBlank()) {
                    rows.add(sec("LEGAL RECORDS"))
                    if (arrestCount > 0) {
                        rows.add("⚠ Arrests on File" to "$arrestCount record${if (arrestCount != 1) "s" else ""}")
                        meta["arrest_records"]?.takeIf { it.isNotBlank() }?.let {
                            it.lines().filter { l -> l.isNotBlank() }.forEach { record -> rows.add("Arrest Record" to record) }
                        }
                    }
                    if (courtCount > 0) rows.add("CourtListener" to "$courtCount case${if (courtCount != 1) "s" else ""} found")
                    if (judyCount > 0) rows.add("JudyRecords" to "$judyCount court record${if (judyCount != 1) "s" else ""} found")
                    meta["judyrecords_cases"]?.takeIf { it.isNotBlank() }?.let {
                        it.lines().filter { l -> l.isNotBlank() }.forEach { c -> rows.add("Case" to c) }
                    }
                    meta["judyrecords_courts"]?.takeIf { it.isNotBlank() }?.let { rows.add("Courts" to it) }
                }

                val officerCount = meta["officer_matches"]?.toIntOrNull() ?: 0
                val secHits = meta["sec_person_hits"]?.toIntOrNull() ?: 0
                val secFulltextHits = meta["sec_fulltext_hits"]?.toIntOrNull() ?: 0
                val fecCount = meta["fec_candidate_count"]?.toIntOrNull() ?: 0
                val gleifEntities = meta["gleif_entities"]?.takeIf { it.isNotBlank() }
                val sunbizOfficerCount = meta["sunbiz_officer_count"]?.toIntOrNull() ?: 0
                val samEntityCount = meta["sam_entity_count"]?.toIntOrNull() ?: 0
                val caOfficerCount = meta["ca_sos_officer_count"]?.toIntOrNull() ?: 0
                val corpwikiCount = meta["corpwiki_person_companies"]?.lines()?.filter { it.isNotBlank() }?.size ?: 0
                if (officerCount > 0 || secHits > 0 || secFulltextHits > 0 || fecCount > 0 || gleifEntities != null
                    || sunbizOfficerCount > 0 || samEntityCount > 0 || caOfficerCount > 0 || corpwikiCount > 0) {
                    rows.add(sec("CORPORATE & FINANCIAL"))
                    meta["officer_details"]?.takeIf { it.isNotBlank() }?.let {
                        it.lines().filter { l -> l.isNotBlank() }.forEach { detail -> rows.add("Corporate Role" to detail) }
                    }
                    if (caOfficerCount > 0) {
                        rows.add("CA SOS Officer Records" to "$caOfficerCount California entit${if (caOfficerCount != 1) "ies" else "y"}")
                        meta["ca_sos_officer_entities"]?.takeIf { it.isNotBlank() }?.let {
                            it.lines().filter { l -> l.isNotBlank() }.forEach { e -> rows.add("CA Entity" to e) }
                        }
                    }
                    if (sunbizOfficerCount > 0) {
                        rows.add("FL SOS Officer Records" to "$sunbizOfficerCount Florida filing${if (sunbizOfficerCount != 1) "s" else ""}")
                        meta["sunbiz_officer_companies"]?.takeIf { it.isNotBlank() }?.let {
                            it.lines().filter { l -> l.isNotBlank() }.forEach { co -> rows.add("FL Company" to co) }
                        }
                    }
                    if (corpwikiCount > 0) {
                        rows.add("Corporations Wiki" to "$corpwikiCount record${if (corpwikiCount != 1) "s" else ""}")
                        meta["corpwiki_person_companies"]?.takeIf { it.isNotBlank() }?.let {
                            it.lines().filter { l -> l.isNotBlank() }.forEach { co -> rows.add("Company" to co) }
                        }
                        meta["corpwiki_person_states"]?.takeIf { it.isNotBlank() }?.let { rows.add("States" to it) }
                    }
                    if (samEntityCount > 0) {
                        rows.add("SAM.gov Federal Entities" to "$samEntityCount entit${if (samEntityCount != 1) "ies" else "y"}")
                        meta["sam_entities"]?.takeIf { it.isNotBlank() }?.let {
                            it.lines().filter { l -> l.isNotBlank() }.forEach { e -> rows.add("Federal Entity" to e) }
                        }
                        meta["sam_uei_codes"]?.takeIf { it.isNotBlank() }?.let { rows.add("UEI Code(s)" to it) }
                    }
                    if (secHits > 0) {
                        rows.add("SEC EDGAR Form-4" to "$secHits filing${if (secHits != 1) "s" else ""} (insider trading)")
                        meta["sec_person_entities"]?.takeIf { it.isNotBlank() }?.let { rows.add("Affiliated Companies" to it) }
                    }
                    if (secFulltextHits > 0) {
                        rows.add("SEC EDGAR All Forms" to "$secFulltextHits filing${if (secFulltextHits != 1) "s" else ""}")
                        meta["sec_fulltext_forms"]?.takeIf { it.isNotBlank() }?.let { rows.add("Form Types" to it) }
                        meta["sec_fulltext_entities"]?.takeIf { it.isNotBlank() }?.let { rows.add("SEC Entities" to it) }
                    }
                    if (gleifEntities != null) {
                        rows.add("GLEIF Legal Entities" to "${gleifEntities.lines().filter { it.isNotBlank() }.size} found")
                        gleifEntities.lines().filter { it.isNotBlank() }.forEach { e -> rows.add("Legal Entity" to e) }
                    }
                    meta["fec_candidates"]?.takeIf { it.isNotBlank() }?.let {
                        it.lines().filter { l -> l.isNotBlank() }.forEach { r -> rows.add("FEC Campaign" to r) }
                    }
                }

                val wikiHits = meta["wikipedia_hits"]?.toIntOrNull() ?: 0
                val newsCount = meta["news_article_count"]?.toIntOrNull() ?: 0
                val gnewsCount = meta["gnews_count"]?.toIntOrNull() ?: 0
                if (wikiHits > 0 || newsCount > 0 || gnewsCount > 0 || !meta["wikidata_descriptions"].isNullOrBlank()) {
                    rows.add(sec("NEWS & PUBLIC RECORDS"))
                    meta["wikipedia_titles"]?.takeIf { it.isNotBlank() }?.let { rows.add("Wikipedia" to it) }
                    meta["wikidata_descriptions"]?.takeIf { it.isNotBlank() }?.let { rows.add("WikiData" to it) }
                    if (newsCount > 0) {
                        meta["news_titles"]?.takeIf { it.isNotBlank() }?.let {
                            it.lines().filter { l -> l.isNotBlank() }.forEach { t -> rows.add("Headline" to t) }
                        }
                    }
                    if (gnewsCount > 0) {
                        meta["gnews_articles"]?.takeIf { it.isNotBlank() }?.let {
                            it.split("\n---\n").filter { a -> a.isNotBlank() }.forEach { article ->
                                val lines = article.lines().filter { l -> l.isNotBlank() }
                                if (lines.isNotEmpty()) rows.add("News" to lines.joinToString(" "))
                            }
                        }
                    }
                }

                val hasSearchIntel = !meta["cse_snippets"].isNullOrBlank() || !meta["bing_snippets"].isNullOrBlank()
                if (hasSearchIntel) {
                    rows.add(sec("SEARCH ENGINE INTEL"))
                    meta["bing_total"]?.let { t -> (t.toLongOrNull() ?: 0L).let { if (it > 0) rows.add("Bing Total Results" to t) } }
                    meta["cse_snippets"]?.takeIf { it.isNotBlank() }?.let {
                        it.split("\n---\n").filter { s -> s.isNotBlank() }.forEach { snippet ->
                            rows.add("Google CSE" to snippet.trim())
                        }
                    }
                    meta["bing_snippets"]?.takeIf { it.isNotBlank() }?.let {
                        it.split("\n---\n").filter { s -> s.isNotBlank() }.forEach { snippet ->
                            rows.add("Bing" to snippet.trim())
                        }
                    }
                }


                val voterNames = meta["voter_names"]?.takeIf { it.isNotBlank() }
                val voterAddresses = meta["voter_addresses"]?.takeIf { it.isNotBlank() }
                if (voterNames != null || voterAddresses != null) {
                    rows.add(sec("VOTER REGISTRATION RECORDS"))
                    voterNames?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { rows.add("Registered Voter" to it) }
                    voterAddresses?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { rows.add("Registered Address" to it) }
                    meta["voter_party"]?.takeIf { it.isNotBlank() }?.let { rows.add("Party Affiliation" to it) }
                    meta["voter_age"]?.takeIf { it.isNotBlank() }?.let { rows.add("Voter Age" to it) }
                    meta["voter_link"]?.takeIf { it.isNotBlank() }?.let { rows.add("VoterRecords.com →" to it) }
                }

                val ahmiaCount = meta["ahmia_count"]?.toIntOrNull() ?: 0
                if (ahmiaCount > 0) {
                    rows.add(sec("⚠ DARK WEB MENTIONS"))
                    rows.add("⚠ Dark Web Hits" to "$ahmiaCount result${if (ahmiaCount != 1) "s" else ""} indexed on Tor-accessible sites")
                    meta["ahmia_titles"]?.takeIf { it.isNotBlank() }?.let {
                        it.lines().filter { l -> l.isNotBlank() }.forEach { t -> rows.add("⚠ Tor Site" to t) }
                    }
                }

                meta["ai_summary"]?.takeIf { it.isNotBlank() }?.let { summary ->
                    rows.add(sec("AI INTELLIGENCE SYNTHESIS"))
                    summary.lines().filter { it.isNotBlank() }.forEach { line ->
                        rows.add("AI Analysis" to line)
                    }
                    rows.add("Note" to "AI-synthesized from public data — verify independently")
                }

                val pivotPhones = linkedSetOf<String>()
                meta["tt_phones"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { pivotPhones.add(it) }
                meta["uspb_phones"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { pivotPhones.add(it) }
                meta["fps_phones"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { pivotPhones.add(it) }
                val pivotEmails = linkedSetOf<String>()
                meta["email"]?.takeIf { it.isNotBlank() }?.let { pivotEmails.add(it) }
                meta["cse_email_hits"]?.split(",")?.map { it.trim() }?.filter { it.contains("@") }?.forEach { pivotEmails.add(it) }
                val pivotUsernames = meta["found_urls"]?.lines()?.filter { it.isNotBlank() }?.mapNotNull {
                    it.split(": ", limit = 2).firstOrNull()
                }?.take(5) ?: emptyList()
                val searchQuery = arguments?.getString("searchQuery") ?: ""

                if (pivotPhones.isNotEmpty() || pivotEmails.isNotEmpty() || pivotUsernames.isNotEmpty()) {
                    rows.add(sec("PIVOT SEARCHES (tap to search)"))
                    pivotPhones.take(5).forEach { phone -> rows.add("⟶ Phone Search" to "pivot://phone/$phone") }
                    pivotEmails.take(3).forEach { email -> rows.add("⟶ Email Search" to "pivot://email/$email") }
                    pivotUsernames.take(3).forEach { uname -> rows.add("⟶ Username Search" to "pivot://username/$uname") }
                    if (searchQuery.isNotBlank()) {
                        val nameParts = searchQuery.trim().split(" ")
                        if (nameParts.size >= 2) {
                            rows.add("⟶ Reverse Name (Last, First)" to "pivot://person/${nameParts.last()} ${nameParts.first()}")
                        }
                    }
                }
            }
            "company" -> {
                rows.add(sec("◈ COMPANY RECORDS"))
                meta["companies"]?.takeIf { it.isNotBlank() }?.let {
                    it.lines().filter { l -> l.isNotBlank() }.forEach { company -> rows.add("OpenCorporates" to company) }
                }
                val sunbizCount = meta["sunbiz_count"]?.toIntOrNull() ?: 0
                if (sunbizCount > 0) {
                    rows.add("FL SOS Records" to "$sunbizCount Florida entit${if (sunbizCount != 1) "ies" else "y"}")
                    meta["sunbiz_entities"]?.takeIf { it.isNotBlank() }?.let {
                        it.lines().filter { l -> l.isNotBlank() }.take(5).forEach { e -> rows.add("FL Entity" to e) }
                    }
                    meta["sunbiz_doc_numbers"]?.takeIf { it.isNotBlank() }?.let { rows.add("FL Doc #" to it) }
                }
                val samCount = meta["sam_entity_count"]?.toIntOrNull() ?: 0
                if (samCount > 0) {
                    rows.add("SAM.gov Federal Entities" to "$samCount registered entit${if (samCount != 1) "ies" else "y"}")
                    meta["sam_entities"]?.takeIf { it.isNotBlank() }?.let {
                        it.lines().filter { l -> l.isNotBlank() }.take(5).forEach { e -> rows.add("Federal Entity" to e) }
                    }
                    meta["sam_uei_codes"]?.takeIf { it.isNotBlank() }?.let { rows.add("UEI Code(s)" to it) }
                    meta["sam_cage_codes"]?.takeIf { it.isNotBlank() }?.let { rows.add("CAGE Code(s)" to it) }
                }
                val caSosCount = meta["ca_sos_count"]?.toIntOrNull() ?: 0
                if (caSosCount > 0) {
                    rows.add("CA SOS Records" to "$caSosCount California entit${if (caSosCount != 1) "ies" else "y"}")
                    meta["ca_sos_entities"]?.takeIf { it.isNotBlank() }?.let {
                        it.lines().filter { l -> l.isNotBlank() }.take(10).forEach { e -> rows.add("CA Entity" to e) }
                    }
                    meta["ca_sos_types"]?.takeIf { it.isNotBlank() }?.let { rows.add("CA Entity Types" to it) }
                }
                meta["corpwiki_companies"]?.takeIf { it.isNotBlank() }?.let {
                    rows.add("Corporations Wiki" to "${it.lines().size} record${if (it.lines().size != 1) "s" else ""}")
                    it.lines().filter { l -> l.isNotBlank() }.take(8).forEach { co -> rows.add("Corp Wiki" to co) }
                    meta["corpwiki_states"]?.takeIf { it.isNotBlank() }?.let { s -> rows.add("Incorporated States" to s) }
                    meta["corpwiki_officers"]?.takeIf { it.isNotBlank() }?.let { o -> rows.add("Officers" to o) }
                }
                meta["gleif_company_entities"]?.takeIf { it.isNotBlank() }?.let {
                    rows.add("GLEIF (Global LEI)" to "${it.lines().size} international entit${if (it.lines().size != 1) "ies" else "y"}")
                    it.lines().filter { l -> l.isNotBlank() }.take(5).forEach { e -> rows.add("Global Entity" to e) }
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

            }
            "phone" -> {
                rows.add(sec("◈ LINE VALIDATION"))
                meta["numverify_valid"]?.let { v -> rows.add("Valid Number" to if (v == "true") "Yes ✓" else "No ✗") }
                meta["numverify_country"]?.takeIf { it.isNotBlank() }?.let { rows.add("Country" to it) }
                meta["numverify_carrier"]?.takeIf { it.isNotBlank() }?.let { rows.add("Carrier" to it) }
                meta["numverify_line_type"]?.takeIf { it.isNotBlank() }?.let { rows.add("Line Type" to it) }
                meta["numverify_location"]?.takeIf { it.isNotBlank() }?.let { rows.add("Location" to it) }
                meta["numverify_intl"]?.takeIf { it.isNotBlank() }?.let { rows.add("Intl Format" to it) }
                meta["veriphone_carrier"]?.takeIf { it.isNotBlank() }?.let { rows.add("Carrier (Veriphone)" to it) }
                meta["veriphone_line_type"]?.takeIf { it.isNotBlank() }?.let { rows.add("Line Type (Veriphone)" to it) }
                meta["veriphone_country"]?.takeIf { it.isNotBlank() }?.let { rows.add("Country (Veriphone)" to it) }
                meta["veriphone_international"]?.takeIf { it.isNotBlank() }?.let { rows.add("Intl Format (Veriphone)" to it) }

                val ipqsFraud = meta["ipqs_phone_fraud_score"]?.toIntOrNull() ?: -1
                if (ipqsFraud >= 0) {
                    rows.add(sec("◈ FRAUD / RISK SCORING"))
                    rows.add("IPQS Fraud Score" to "$ipqsFraud / 100${if (ipqsFraud > 70) " ⚠ HIGH RISK" else if (ipqsFraud > 40) " ⚠ Moderate" else " — Low"}")
                    meta["ipqs_phone_risky"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Risky" to "Phone flagged as risky by IPQS") }
                    meta["ipqs_phone_spam"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Spammer" to "Associated with spam/scam activity") }
                    meta["ipqs_phone_voip"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("VoIP" to "Voice over IP number") }
                    meta["ipqs_phone_line_type"]?.takeIf { it.isNotBlank() }?.let { rows.add("Line Type (IPQS)" to it) }
                    meta["ipqs_phone_carrier"]?.takeIf { it.isNotBlank() }?.let { rows.add("Carrier (IPQS)" to it) }
                    meta["ipqs_phone_country"]?.takeIf { it.isNotBlank() }?.let { rows.add("Country (IPQS)" to it) }
                }

            }
            "image" -> {
                rows.add(sec("◈ FACE RECOGNITION SEARCH"))
                rows.add("Instructions" to "Tap any link below → open in browser → upload your photo → get instant results")
                meta["face_facecheck_id_link"]?.let { rows.add("⟶ FaceCheck.id →" to it) }
                meta["face_pimeyes_link"]?.let { rows.add("⟶ PimEyes (Best Face Match) →" to it) }
                meta["face_search4faces_link"]?.let { rows.add("⟶ Search4Faces (Social Media) →" to it) }
                meta["face_lenso_ai_link"]?.let { rows.add("⟶ Lenso.ai (AI Face Search) →" to it) }
                rows.add(sec("◈ REVERSE IMAGE SEARCH"))
                rows.add("Note" to "Upload image to find where it appears on the web")
                meta["rev_google_lens_link"]?.let { rows.add("⟶ Google Lens →" to it) }
                meta["rev_yandex_images_link"]?.let { rows.add("⟶ Yandex Images (Best for faces) →" to it) }
                meta["rev_tineye_link"]?.let { rows.add("⟶ TinEye →" to it) }
                meta["rev_bing_visual_search_link"]?.let { rows.add("⟶ Bing Visual →" to it) }
                meta["rev_karmadecay_link"]?.let { rows.add("⟶ KarmaDecay (Reddit) →" to it) }
            }
            "comprehensive" -> {
                val name = meta["comp_name"]?.takeIf { it.isNotBlank() }
                val phone = meta["comp_phone"]?.takeIf { it.isNotBlank() }
                val email = meta["comp_email"]?.takeIf { it.isNotBlank() }
                val ip = meta["comp_ip"]?.takeIf { it.isNotBlank() }
                rows.add(sec("◈ COMPREHENSIVE INTEL PROFILE"))
                if (name != null) rows.add("Name Searched" to name)
                if (phone != null) rows.add("Phone Searched" to phone)
                if (email != null) rows.add("Email Searched" to email)
                if (ip != null) rows.add("IP Searched" to ip)
                rows.addAll(buildDataRows(meta, "person"))
                val hibpCount = meta["hibp_breach_count"]?.toIntOrNull() ?: 0
                val proxyCount = meta["proxynova_breach_count"]?.toIntOrNull() ?: 0
                val leakCount = meta["leakcheck_found"]?.toIntOrNull() ?: 0
                if (email != null && (hibpCount > 0 || proxyCount > 0 || leakCount > 0)) {
                    rows.add(sec("◈ EMAIL / BREACH INTEL"))
                    rows.addAll(buildDataRows(meta, "email"))
                }
                val numverifyValid = meta["numverify_valid"]?.toBooleanStrictOrNull() ?: false
                val ipqsPhone = meta["ipqs_phone_fraud_score"]?.toIntOrNull() ?: -1
                if (phone != null && (numverifyValid || ipqsPhone >= 0)) {
                    rows.add(sec("◈ PHONE / SIGNAL INTEL"))
                    rows.addAll(buildDataRows(meta, "phone"))
                }
                val ipCity = meta["ip_city"]?.takeIf { it.isNotBlank() }
                if (ip != null && ipCity != null) {
                    rows.add(sec("◈ IP / NETWORK INTEL"))
                    rows.addAll(buildDataRows(meta, "ip"))
                }
            }
        }
        return rows
    }

    private fun sectionLabel(type: String) = when (type) {
        "email" -> "▶  EMAIL INTELLIGENCE"
        "ip", "domain" -> "▶  NETWORK INTELLIGENCE"
        "username" -> "▶  DIGITAL FOOTPRINT"
        "company" -> "▶  CORPORATE INTELLIGENCE"
        "phone" -> "▶  SIGNAL INTELLIGENCE"
        "image" -> "▶  VISUAL INTELLIGENCE"
        "comprehensive" -> "▶  COMPREHENSIVE DOSSIER"
        else -> "▶  SUBJECT DOSSIER"
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
            val isSectionHeader = value.isEmpty() && (label.startsWith("◈") || label.startsWith("─") || label.startsWith("> ") || label.startsWith("══"))
            val isPivot = value.startsWith("pivot://")
            val isLink = value.startsWith("http://") || value.startsWith("https://")
            val isSearchUrl = isLink && (value.contains("google.com/search") || value.contains("bing.com/search"))
            val ctx = holder.b.root.context
            val themePrefs = ctx.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
            val currentTheme = themePrefs.getString("pref_theme_base", "modern") ?: "modern"

            if (isSectionHeader) {
                holder.b.tvRowLabel.text = ""
                val cleanLabel = label.trimStart().removePrefix("◈ ").removePrefix("> ").removePrefix("══ ").removeSuffix(" ══").trim()
                holder.b.tvRowValue.text = "  $cleanLabel"
                val tv2 = android.util.TypedValue()
                ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv2, true)
                holder.b.tvRowValue.setTextColor(tv2.data)
                holder.b.tvRowValue.textSize = 10f
                holder.b.tvRowValue.letterSpacing = 0.15f
                holder.b.tvRowValue.typeface = android.graphics.Typeface.DEFAULT_BOLD
                holder.b.rowAccentStripe.visibility = View.GONE
                holder.b.root.setBackgroundColor(tv2.data and 0x30FFFFFF or 0x0D000000)
                holder.b.root.setOnClickListener(null)
            } else if (isPivot) {
                holder.b.rowAccentStripe.visibility = View.VISIBLE
                holder.b.root.setBackgroundColor(0)
                val tv3 = android.util.TypedValue()
                ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv3, true)
                holder.b.rowAccentStripe.setBackgroundColor(ContextCompat.getColor(ctx, R.color.accent_cyan))
                holder.b.tvRowLabel.text = label
                val parts = value.removePrefix("pivot://").split("/", limit = 2)
                val pivotType = parts.getOrNull(0) ?: "person"
                val pivotQuery = parts.getOrNull(1) ?: ""
                holder.b.tvRowValue.text = pivotQuery
                holder.b.tvRowValue.textSize = 13f
                holder.b.tvRowValue.letterSpacing = 0f
                holder.b.tvRowValue.typeface = android.graphics.Typeface.DEFAULT
                holder.b.tvRowValue.setTextColor(ContextCompat.getColor(ctx, R.color.accent_cyan))
                holder.b.root.setOnClickListener {
                    val bundle = android.os.Bundle().apply {
                        putString("query", pivotQuery)
                        putString("type", pivotType)
                    }
                    this@ResultsFragment.findNavController().navigate(R.id.action_results_to_progress, bundle)
                }
            } else {
                val isWarning = label.startsWith("⚠")
                holder.b.rowAccentStripe.visibility = if (isWarning || isLink) View.VISIBLE else View.INVISIBLE
                if (isWarning) holder.b.rowAccentStripe.setBackgroundColor(ContextCompat.getColor(ctx, R.color.score_red))
                else if (isLink) {
                    val tvP = android.util.TypedValue()
                    ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tvP, true)
                    holder.b.rowAccentStripe.setBackgroundColor(tvP.data)
                }
                holder.b.root.setBackgroundColor(0)
                holder.b.tvRowLabel.text = label
                val displayValue = if (isSearchUrl) {
                    try { Uri.parse(value).getQueryParameter("q") ?: value }
                    catch (_: Exception) { value }
                } else value
                holder.b.tvRowValue.text = displayValue
                holder.b.tvRowValue.textSize = 13f
                holder.b.tvRowValue.letterSpacing = 0f
                holder.b.tvRowValue.typeface = android.graphics.Typeface.DEFAULT
                if (isLink) {
                    holder.b.tvRowValue.setTextColor(ContextCompat.getColor(holder.b.root.context, R.color.accent_cyan))
                    holder.b.root.setOnClickListener {
                        val linkPrefs = it.context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                        val isDork = value.contains("google.com/search") || value.contains("bing.com/search")
                        val pkg = if (isDork) "com.android.chrome" else when (linkPrefs.getString("pref_browser", "firefox")) {
                            "ddg"     -> "com.duckduckgo.mobile.android"
                            "chrome"  -> "com.android.chrome"
                            "default" -> null
                            else      -> "org.mozilla.firefox"
                        }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(value))
                        if (pkg != null) intent.setPackage(pkg)
                        try { it.context.startActivity(intent) }
                        catch (_: Exception) { it.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value))) }
                    }
                } else {
                    val tv = android.util.TypedValue()
                    holder.b.root.context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true)
                    holder.b.tvRowValue.setTextColor(tv.data)
                    holder.b.root.setOnClickListener(null)
                }
            }
        }

        override fun getItemCount() = rows.size
    }
}
