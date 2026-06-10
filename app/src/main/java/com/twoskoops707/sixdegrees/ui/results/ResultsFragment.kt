package com.twoskoops707.sixdegrees.ui.results

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import coil.load
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayoutMediator
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.data.AppSettings
import com.twoskoops707.sixdegrees.data.osint.OsintToolRegistry
import com.twoskoops707.sixdegrees.domain.DorkMetadataStore
import com.twoskoops707.sixdegrees.databinding.FragmentResultsBinding
import com.twoskoops707.sixdegrees.databinding.ItemDataRowBinding
import com.twoskoops707.sixdegrees.ui.common.InvestigationPipelineView
import com.twoskoops707.sixdegrees.ui.common.InvestigationStep
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResultsViewModel by viewModels()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private var tabMediator: TabLayoutMediator? = null
    private var displayedReportId: String? = null
    private var reportMeta: Map<String, String> = emptyMap()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.resultsToolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when {
                state.isLoading -> showLoading()
                state.error != null -> showEmptyState(state.error)
                state.report != null -> {
                    showResults()
                    if (displayedReportId != state.report.id) {
                        displayedReportId = state.report.id
                        populateReport(state)
                    }
                }
                else -> showEmptyState()
            }
        }

        val reportId = arguments?.getString("reportId")
        if (!reportId.isNullOrBlank()) viewModel.loadReport(reportId) else showEmptyState()
    }

    private fun populateReport(state: ResultsUiState) {
        val report = state.report ?: return
        val person = state.person

        val meta = try {
            val type = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
            moshi.adapter<Map<String, String>>(type).fromJson(report.companiesJson) ?: emptyMap()
        } catch (_: Exception) { emptyMap() }

        val searchType = arguments?.getString("searchType")?.takeIf { it.isNotBlank() }
            ?: meta["search_type"] ?: "person"

        val subjectName = person?.fullName?.ifBlank { "${person.firstName} ${person.lastName}".trim() }
            ?: run {
                report.searchQuery.split("|").mapNotNull {
                    val p = it.split("=", limit = 2); if (p.size == 2) p[0].trim() to p[1].trim() else null
                }.toMap()["name"] ?: ""
            }
        val avatarUrl = "https://ui-avatars.com/api/?name=${android.net.Uri.encode(subjectName.ifBlank { "?" })}&size=200&format=png&bold=true&color=fff&background=1a2744"
        val profileImageUrl = person?.profileImageUrl
            ?: meta["profile_photo_url"]
            ?: meta["tt_image_url"]
            ?: meta["gravatar_url"]
            ?: avatarUrl
        binding.profileImage.load(profileImageUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_person_placeholder)
            error(R.drawable.ic_person_placeholder)
        }

        binding.personCard.visibility = View.VISIBLE

        if (person != null) {
            binding.fullName.text = person.fullName.ifBlank { "${person.firstName} ${person.lastName}".trim() }
            binding.jobTitle.text = buildString {
                val job = parseCurrentJob(person.employmentHistoryJson)
                if (job.isNotBlank()) append(job)
            }
            binding.location.text = parseFirstAddress(person.addressesJson)
        } else {
            val qFields = report.searchQuery.split("|").mapNotNull {
                val p = it.split("=", limit = 2); if (p.size == 2) p[0].trim() to p[1].trim() else null
            }.toMap()
            val displayName = qFields["name"]?.takeIf { it.isNotBlank() }
                ?: qFields["email"]?.takeIf { it.isNotBlank() }
                ?: qFields["username"]?.let { "@$it" }
                ?: qFields["phone"]?.takeIf { it.isNotBlank() }
                ?: meta["comp_name"]?.takeIf { it.isNotBlank() }
                ?: report.searchQuery.split("|").firstOrNull()?.let {
                    if (it.contains("=")) it.substringAfter("=").trim() else it.trim()
                } ?: report.searchQuery
            binding.fullName.text = displayName
            val bestAge = extractBestAge(meta)
            binding.jobTitle.text = buildString {
                bestAge?.let { append("Age: $it") }
                qFields["dob"]?.takeIf { it.isNotBlank() }?.let { if (isNotEmpty()) append(" · "); append(it) }
                meta["demographics_gender"]?.let { g -> if (isNotEmpty()) append(" · "); append(g) }
                val subFields = listOfNotNull(
                    qFields["phone"]?.takeIf { it.isNotBlank() && qFields["name"]?.isNotBlank() == true }?.let { "☎ $it" },
                    qFields["email"]?.takeIf { it.isNotBlank() && displayName != it }?.let { "✉ $it" },
                    qFields["username"]?.takeIf { it.isNotBlank() }?.let { "@ $it" }
                )
                if (subFields.isNotEmpty() && isEmpty()) append(subFields.joinToString("  ·  "))
            }
            val city = qFields["city"] ?: qFields["location"] ?: ""
            val state = qFields["state"] ?: ""
            val address = qFields["address"] ?: meta["person_entered_address"] ?: ""
            val fullLoc = listOf(address, city, state).filter { it.isNotBlank() }.joinToString(", ")
            binding.location.text = fullLoc.ifBlank { FindingUrlHelper.bestDisplayLocation(meta) }
        }

        val sourceCount = try {
            val type = Types.newParameterizedType(List::class.java, com.twoskoops707.sixdegrees.domain.model.DataSource::class.java)
            moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.DataSource>>(type)
                .fromJson(report.sourcesJson)?.size ?: 0
        } catch (_: Exception) { 0 }
        binding.tvSourcesCount.text = "$sourceCount\nsources"

        val enrichedMeta = state.enrichedMeta.ifEmpty { meta }
        reportMeta = enrichedMeta

        val investigatorMode = AppSettings.isInvestigatorMode(requireContext())
        applyResultsModeUi(investigatorMode)

        applyShadyScore(state.shadyScore, enrichedMeta, searchType, investigatorMode)
        showPartialReportBanner(enrichedMeta)
        setupDossierTabs(state.dossierSections, investigatorMode, enrichedMeta)
        buildCandidateDisambiguation(enrichedMeta)
        setupBackToCandidates()

        binding.btnExport.setOnClickListener { shareReport(report.searchQuery, searchType, enrichedMeta) }

        binding.btnWebHub.setOnClickListener {
            val q = report.searchQuery.split("|").firstOrNull()?.let {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[1].trim() else it.trim()
            } ?: report.searchQuery
            val bundle = Bundle().also { it.putString("query", q) }
            findNavController().navigate(R.id.action_results_to_osint_resources, bundle)
        }
    }

    private fun setupBackToCandidates() {
        val candidatesJson = arguments?.getString("candidatesJson").orEmpty()
        val hasCandidates = candidatesJson.isNotBlank() && candidatesJson != "[]"
        binding.btnBackToCandidates.isVisible = hasCandidates
        if (!hasCandidates) return

        val searchQuery = arguments?.getString("searchQuery").orEmpty()
        val reportId = arguments?.getString("reportId").orEmpty()
        val round = arguments?.getInt("candidateRound") ?: 1

        binding.btnBackToCandidates.setOnClickListener {
            findNavController().navigate(
                R.id.action_results_to_candidates,
                Bundle().apply {
                    putString("candidatesJson", candidatesJson)
                    putString("reportId", reportId)
                    putInt("round", round.coerceAtLeast(1))
                    putString("searchQuery", searchQuery)
                }
            )
        }
    }

    private fun extractBestAge(meta: Map<String, String>): String? =
        meta["search_age"] ?: meta["tps_age"] ?: meta["zaba_age"] ?: meta["411_age"]
            ?: meta["voter_age"] ?: meta["radaris_age"] ?: meta["peekyou_age"]
            ?: meta["nuwber_age"] ?: meta["wp_age"] ?: meta["fps_age"] ?: meta["tt_ages"]?.split(", ")?.firstOrNull()?.trim()
            ?: meta["uspb_age"] ?: meta["demographics_age_estimate"]

    private fun extractBestLocation(meta: Map<String, String>): String =
        FindingUrlHelper.bestDisplayLocation(meta)

    private fun buildTabs(meta: Map<String, String>, type: String): List<Pair<String, List<Pair<String, String>>>> {
        return when (type) {
            "person", "scan" -> listOf(
                getString(R.string.dossier_tab_subject) to buildPersonOverview(meta),
                getString(R.string.dossier_tab_contacts) to buildPersonContacts(meta),
                getString(R.string.dossier_tab_digital) to buildPersonDigitalTrace(meta),
                getString(R.string.dossier_tab_legal) to buildPersonLegal(meta),
                getString(R.string.dossier_tab_intel) to buildPersonIntel(meta)
            )
            "email" -> listOf(
                getString(R.string.dossier_tab_subject) to buildEmailOverview(meta),
                getString(R.string.dossier_tab_breaches) to buildEmailBreaches(meta),
                getString(R.string.dossier_tab_identity) to buildEmailIdentity(meta)
            )
            "ip", "domain" -> {
                val tabs = mutableListOf(
                    getString(R.string.dossier_tab_network) to buildIpNetwork(meta),
                    getString(R.string.dossier_tab_threats) to buildIpThreats(meta)
                )
                val hasDomain = !meta["rdap_registrar"].isNullOrBlank() || !meta["whois"].isNullOrBlank()
                    || !meta["subdomains"].isNullOrBlank()
                if (hasDomain) tabs.add(getString(R.string.dossier_tab_domain) to buildIpDomain(meta))
                tabs
            }
            "username" -> listOf(
                getString(R.string.dossier_tab_handles) to buildUsernameFound(meta),
                getString(R.string.dossier_tab_profiles) to buildUsernameProfiles(meta)
            )
            "phone" -> listOf(
                getString(R.string.dossier_tab_validation) to buildPhoneValidation(meta),
                getString(R.string.dossier_tab_risk) to buildPhoneRisk(meta)
            )
            "company" -> listOf(
                getString(R.string.dossier_tab_corporate) to buildCompanyRecords(meta),
                getString(R.string.dossier_tab_officers) to buildCompanyOfficers(meta),
                getString(R.string.dossier_tab_filings) to buildCompanyFilings(meta)
            )
            "image" -> listOf(
                getString(R.string.dossier_tab_face) to buildImageFace(meta),
                getString(R.string.dossier_tab_reverse) to buildImageReverse(meta)
            )
            "comprehensive" -> {
                val tabs = mutableListOf(
                    getString(R.string.dossier_tab_subject) to buildPersonOverview(meta),
                    getString(R.string.dossier_tab_contacts) to buildPersonContacts(meta),
                    getString(R.string.dossier_tab_digital) to buildPersonDigitalTrace(meta),
                    getString(R.string.dossier_tab_legal) to buildPersonLegal(meta),
                    getString(R.string.dossier_tab_intel) to buildPersonIntel(meta)
                )
                val hasEmail = !meta["comp_email"].isNullOrBlank()
                if (hasEmail) tabs.add(getString(R.string.dossier_tab_breaches) to buildEmailBreaches(meta))
                val hasIp = !meta["comp_ip"].isNullOrBlank() && !meta["ip_city"].isNullOrBlank()
                if (hasIp) tabs.add(getString(R.string.dossier_tab_network) to buildIpNetwork(meta))
                tabs
            }
            else -> listOf(getString(R.string.dossier_tab_data) to buildPersonOverview(meta))
        }
    }

    private fun sec(label: String): Pair<String, String> {
        val clean = label.trimStart()
            .removePrefix("◈ ").removePrefix("> ").removePrefix("══ ")
            .removeSuffix(" ══").trim()
        val prefs = requireContext().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        val base = prefs.getString("pref_theme_base", "modern") ?: "modern"
        val prefix = when (base) { "hacker" -> "> "; "tactical" -> "══ "; else -> "◈ " }
        val suffix = if (base == "tactical") " ══" else ""
        return "$prefix$clean$suffix" to ""
    }

    private fun appendAiSuggestedSearchRows(
        rows: MutableList<Pair<String, String>>,
        meta: Map<String, String>,
        investigatorMode: Boolean = false
    ) {
        if (!investigatorMode) return
        meta["ai_suggested_searches"]?.takeIf { it.isNotBlank() }?.let { links ->
            rows.add(sec("AI SUGGESTED SEARCHES (VERIFY)"))
            links.lines().filter { it.isNotBlank() }.forEach { line ->
                val colonIdx = line.indexOf(": http")
                if (colonIdx > 0) {
                    val label = "Verify ↗ ${line.substring(0, colonIdx)}"
                    val url = line.substring(colonIdx + 2)
                    rows.add(label to url)
                }
            }
        }
    }

    private fun appendGoogleIntelligence(rows: MutableList<Pair<String, String>>, meta: Map<String, String>) {
        val needleLines = meta["dork_needle_findings"]?.lines()?.filter { it.isNotBlank() }.orEmpty()
        val categories = DorkMetadataStore.allCategories(meta)
        val hasIntel = needleLines.isNotEmpty() || categories.isNotEmpty()
            || !meta["dork_corroborated_phones"].isNullOrBlank()
            || !meta["dork_follow_snippets"].isNullOrBlank()
        if (!hasIntel) return

        rows.add(sec("GOOGLE INTELLIGENCE"))
        meta["dork_total_hits"]?.takeIf { it.isNotBlank() }?.let { total ->
            rows.add("Auto-dork hits" to "$total parsed in-app")
        }

        needleLines.take(12).forEach { line ->
            val parsed = DorkMetadataStore.parseNeedleLine(line)
            val type = parsed["type"] ?: "Finding"
            val value = parsed["value"] ?: line
            val confidence = parsed["confidence"] ?: parsed["hits"] ?: "?"
            val label = when (type) {
                "PHONE" -> "Phone (corroborated ×$confidence)"
                "EMAIL" -> "Email (corroborated ×$confidence)"
                "ADDRESS" -> "Address (corroborated ×$confidence)"
                else -> "High-confidence $type"
            }
            rows.add(label to value)
        }

        meta["dork_corroborated_phones"]?.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }
            ?.filter { it.isNotBlank() }?.forEach { phone ->
                rows.add("Corroborated phone" to phone)
            }
        meta["dork_corroborated_emails"]?.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }
            ?.filter { it.isNotBlank() }?.forEach { email ->
                rows.add("Corroborated email" to email)
            }
        meta["dork_employers"]?.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }
            ?.filter { it.isNotBlank() }?.take(6)?.forEach { employer ->
                rows.add("Employer hint" to employer)
            }
        meta["dork_follow_snippets"]?.takeIf { it.isNotBlank() }?.split("\n---\n")
            ?.filter { it.isNotBlank() }?.take(6)?.forEach { snippet ->
                rows.add("Profile page" to snippet.trim())
            }

        categories.forEach { category ->
            val hits = DorkMetadataStore.hitsForCategory(meta, category)
            hits.take(5).forEach { hit ->
                val text = buildString {
                    append(hit.title)
                    if (hit.snippet.isNotBlank()) append(": ${hit.snippet.take(160)}")
                }.trim()
                rows.add(category.displayName to text)
                if (hit.url.startsWith("http")) {
                    rows.add("Verify ↗" to hit.url)
                }
            }
        }
    }

    private fun appendBrowserVerifyLinks(
        rows: MutableList<Pair<String, String>>,
        meta: Map<String, String>,
        investigatorMode: Boolean
    ) {
        if (!investigatorMode) return
        val links = meta["dork_browser_verify_links"] ?: meta["dork_search_links"]
        links?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("BROWSER VERIFY (OPTIONAL)"))
            it.lines().filter { line -> line.isNotBlank() }.forEach { line ->
                val colonIdx = line.indexOf(": http")
                if (colonIdx > 0) {
                    val label = "Verify ↗ ${line.substring(0, colonIdx)}"
                    val url = line.substring(colonIdx + 2)
                    rows.add(label to url)
                }
            }
        }
    }

    private fun appendAiReportRows(rows: MutableList<Pair<String, String>>, meta: Map<String, String>) {
        val hasStructured = meta["ai_executive_summary"]?.isNotBlank() == true
        if (hasStructured) {
            rows.add(sec("AI INTELLIGENCE DOSSIER"))
            meta["ai_provider"]?.takeIf { it.isNotBlank() }?.let {
                rows.add("Provider" to it.replaceFirstChar { c -> c.uppercase() })
            }
            meta["ai_executive_summary"]?.let { rows.add("Executive Summary" to it.trim()) }
            meta["ai_key_findings"]?.lines()?.filter { it.isNotBlank() }?.forEach { rows.add("Finding" to it.trim()) }
            meta["ai_confidence"]?.takeIf { it.isNotBlank() }?.let { conf ->
                val rationale = meta["ai_confidence_rationale"]?.takeIf { it.isNotBlank() }
                rows.add("Confidence" to if (rationale != null) "$conf — $rationale" else conf)
            }
            meta["ai_false_positives"]?.lines()?.filter { it.isNotBlank() }?.forEach {
                rows.add("False Positive" to it.trim())
            }
            meta["ai_next_steps"]?.lines()?.filter { it.isNotBlank() }?.forEach {
                rows.add("Next Step" to it.trim())
            }
            appendAiSuggestedSearchRows(rows, meta, AppSettings.isInvestigatorMode(requireContext()))
            rows.add("⚠ Disclaimer" to "AI-generated synthesis — verify all claims independently.")
            return
        }
        meta["ai_summary"]?.takeIf { it.isNotBlank() }?.let { summary ->
            rows.add(sec("AI INTELLIGENCE BRIEF"))
            summary.lines().filter { it.isNotBlank() }.forEach { rows.add("Brief" to it.trim()) }
            rows.add("⚠ Disclaimer" to "AI-generated summary — verify all claims independently.")
        }
    }

    private fun buildPersonOverview(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        appendAiReportRows(rows, meta)

        rows.add(sec("IDENTITY"))
        val bestAge = extractBestAge(meta)
        bestAge?.let { rows.add("Age" to it) }
        val bestDob = meta["person_dob"]?.takeIf { it.isNotBlank() }
            ?: meta["comp_dob"]?.takeIf { it.isNotBlank() }
            ?: meta["pipl_dob"]?.takeIf { it.isNotBlank() }
        bestDob?.let { rows.add("Date of Birth" to it) }
        meta["ftn_birth_year"]?.takeIf { bestDob.isNullOrBlank() }?.let { rows.add("Birth Year" to "~$it") }
        meta["demographics_gender"]?.let { rows.add("Gender" to it) }
        meta["pipl_gender"]?.takeIf { meta["demographics_gender"].isNullOrBlank() }?.let { rows.add("Gender" to it) }
        meta["pipl_nationalities"]?.takeIf { it.isNotBlank() }
            ?.let { rows.add("Nationalities (verify independently)" to it) }
        meta["pipl_aliases"]?.takeIf { it.isNotBlank() }?.let { rows.add("Known Aliases" to it) }

        meta["pipl_employment"]?.takeIf { it.isNotBlank() }?.let { emp ->
            rows.add(sec("EMPLOYMENT HISTORY"))
            emp.lines().filter { it.isNotBlank() }.forEach { rows.add("Job" to it) }
        }
        meta["pdl_employment"]?.takeIf { meta["pipl_employment"].isNullOrBlank() && it.isNotBlank() }?.let { emp ->
            rows.add(sec("EMPLOYMENT HISTORY (PDL)"))
            emp.lines().filter { it.isNotBlank() }.forEach { rows.add("Job" to it) }
        }
        meta["pdl_company"]?.takeIf { it.isNotBlank() }?.let { rows.add("Current Company" to it) }
        meta["pdl_job_title"]?.takeIf { it.isNotBlank() }?.let { rows.add("Job Title" to it) }
        meta["clearbit_person_title"]?.takeIf { it.isNotBlank() }?.let { rows.add("Title (Clearbit)" to it) }
        meta["clearbit_person_company"]?.takeIf { it.isNotBlank() }?.let { rows.add("Company (Clearbit)" to it) }
        meta["wikidata_employers"]?.takeIf { it.isNotBlank() }?.let { rows.add("Employers (Wikidata)" to it) }

        val allPhones = extractPhones(meta)
        if (allPhones.isNotEmpty()) {
            rows.add(sec("PHONE NUMBERS"))
            allPhones.take(6).forEach { rows.add("Phone" to it) }
        }

        val enteredLoc = meta["person_location"]?.takeIf { it.isNotBlank() }
        val enteredAddress = meta["person_entered_address"]?.takeIf { it.isNotBlank() }
        val scrapedLoc = extractBestLocation(meta)
        val bestLoc = enteredAddress ?: enteredLoc ?: scrapedLoc
        if (bestLoc.isNotBlank()) {
            rows.add(sec("LOCATION"))
            enteredAddress?.let { rows.add("Street Address" to it) }
            enteredLoc?.takeIf { !it.equals(enteredAddress, ignoreCase = true) }?.let { rows.add("Search Location" to it) }
            if (scrapedLoc.isNotBlank() && !scrapedLoc.equals(bestLoc, ignoreCase = true)) {
                rows.add("Records Location" to scrapedLoc)
            } else if (enteredAddress == null && enteredLoc == null) {
                rows.add("Location" to scrapedLoc)
            }
        }

        val allRel = extractRelatives(meta)
        if (allRel.isNotEmpty()) {
            rows.add(sec("KNOWN ASSOCIATES"))
            allRel.take(12).forEach { rows.add("Name" to it) }
        }

        meta["search_snippets"]?.takeIf { it.isNotBlank() }?.let { snips ->
            val lines = snips.lines().filter { it.isNotBlank() }
            if (lines.isNotEmpty()) {
                rows.add(sec("WEB INTELLIGENCE"))
                lines.take(10).forEach { rows.add("Result" to it.trim()) }
            }
        }

        val wikiExtract = meta["wikipedia_extract"]?.takeIf { it.isNotBlank() }
        if (wikiExtract != null) {
            rows.add(sec("WIKIPEDIA"))
            wikiExtract.lines().filter { it.isNotBlank() }.take(4).forEach { rows.add("Info" to it.trim()) }
            meta["wikipedia_title"]?.let { rows.add("Page" to it) }
        }

        val ddgAbstract = meta["ddg_abstract"]?.takeIf { it.isNotBlank() }
        if (ddgAbstract != null) {
            rows.add(sec("WEB INTEL"))
            ddgAbstract.lines().filter { it.isNotBlank() }.take(4).forEach { rows.add("Info" to it.trim()) }
            meta["ddg_source"]?.let { rows.add("Source" to it) }
        }

        meta["ddg_web_snippets"]?.takeIf { it.isNotBlank() && ddgAbstract.isNullOrBlank() }?.let { snippets ->
            rows.add(sec("WEB SNIPPETS"))
            snippets.lines().filter { it.isNotBlank() }.take(5).forEach { rows.add("Result" to it.trim()) }
        }

        val tpsNames = (meta["tps_names"] ?: meta["fps_names"] ?: meta["tt_names"])?.takeIf { it.isNotBlank() }
        if (tpsNames != null) {
            rows.add(sec("MATCHED NAMES"))
            tpsNames.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { rows.add("Name" to it) }
        }

        val ddgInfobox = meta["ddg_infobox"]?.takeIf { it.isNotBlank() }
        if (ddgInfobox != null) {
            if (meta["ddg_abstract"].isNullOrBlank()) rows.add(sec("PUBLIC PROFILE"))
            ddgInfobox.lines().filter { it.isNotBlank() }.take(6).forEach { rows.add("Detail" to it.trim()) }
        }

        meta["ddg_answer"]?.takeIf { it.isNotBlank() && meta["ddg_abstract"].isNullOrBlank() && ddgInfobox == null }?.let {
            rows.add(sec("QUICK ANSWER"))
            rows.add("Answer" to it)
        }

        if (rows.size <= 2) rows.add("Status" to "No identity data found for this subject")
        return rows
    }

    private fun buildPersonDigitalTrace(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        val investigatorMode = AppSettings.isInvestigatorMode(requireContext())

        appendGoogleIntelligence(rows, meta)

        val socialLinks = buildSocialLinks(meta)
        if (socialLinks.isNotEmpty()) {
            rows.add(sec("SOCIAL DISCOVERY"))
            socialLinks.take(8).forEach { (label, url) -> rows.add(label to url) }
        }

        val foundUrls = meta["found_urls"]?.takeIf { it.isNotBlank() }
        val sherlockFound = meta["sherlock_found"]?.takeIf { it.isNotBlank() }
        val sherlockNameFound = meta["sherlock_name_found"]?.takeIf { it.isNotBlank() }
        val maigretFound = meta["maigret_found"]?.takeIf { it.isNotBlank() }
        val sitesFound = meta["sites_found"]?.toIntOrNull() ?: 0
        val sitesChecked = meta["sites_checked"]?.toIntOrNull() ?: 0
        if (sitesFound > 0) {
            rows.add(sec("USERNAME PRESENCE ($sitesFound / $sitesChecked platforms)"))
            meta["username"]?.takeIf { it.isNotBlank() }?.let { rows.add("Handle" to it) }
            parseSocialProfilesFromMeta(meta).filter { !it.statsLabel.isNullOrBlank() }.take(12).forEach { profile ->
                val label = buildString {
                    append("✓ ${profile.platform}")
                    profile.statsLabel?.let { append(" · $it") }
                }
                rows.add(label to (profile.url ?: profile.username))
            }
        }
        if (foundUrls != null || sherlockFound != null || sherlockNameFound != null || maigretFound != null) {
            rows.add(sec("DIGITAL PRESENCE"))
            foundUrls?.lines()?.filter { it.isNotBlank() }?.take(15)?.forEach { line ->
                val isNsfw = line.startsWith("⚠NSFW:")
                val cleanLine = if (isNsfw) line.removePrefix("⚠NSFW:") else line
                val parts = cleanLine.split(": ", limit = 2)
                val siteName = parts.firstOrNull()?.trim() ?: ""
                val url = parts.getOrNull(1) ?: cleanLine
                val desc = PLATFORM_DESCRIPTIONS[siteName]?.removePrefix("⚠ ")
                val label = buildString {
                    append(if (isNsfw) "⚠ $siteName" else "✓ $siteName")
                    if (!desc.isNullOrBlank()) append(" · $desc")
                }
                rows.add(label to url)
            }
            sherlockFound?.lines()?.filter { it.isNotBlank() }?.take(10)?.forEach { line ->
                val parts = line.split(": ", limit = 2)
                rows.add("Sherlock: ${parts.firstOrNull() ?: ""}" to (parts.getOrNull(1) ?: line))
            }
            sherlockNameFound?.lines()?.filter { it.isNotBlank() }?.take(10)?.forEach { line ->
                val parts = line.split(": ", limit = 2)
                rows.add("Likely: ${parts.firstOrNull() ?: ""}" to (parts.getOrNull(1) ?: line))
            }
            maigretFound?.lines()?.filter { it.isNotBlank() }?.take(10)?.forEach { line ->
                val parts = line.split(": ", limit = 2)
                rows.add("Maigret: ${parts.firstOrNull() ?: ""}" to (parts.getOrNull(1) ?: line))
            }
        }

        meta["dork_identity_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("IDENTITY INTEL (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(10).forEach { s -> rows.add("Intel" to s.trim()) }
        }

        appendBrowserVerifyLinks(rows, meta, investigatorMode)

        if (rows.isEmpty()) rows.add("Status" to "No digital footprint found for this subject")
        return rows
    }

    private fun buildSocialLinks(meta: Map<String, String>): List<Pair<String, String>> {
        val links = mutableListOf<Pair<String, String>>()
        meta["peekyou_social"]?.lines()?.filter { it.isNotBlank() }?.forEach { url ->
            val platform = when {
                url.contains("twitter.com") || url.contains("x.com") -> "Twitter/X"
                url.contains("facebook.com") -> "Facebook"
                url.contains("instagram.com") -> "Instagram"
                url.contains("linkedin.com") -> "LinkedIn"
                url.contains("tiktok.com") -> "TikTok"
                else -> "Social"
            }
            links.add("⟶ $platform" to url)
        }
        return links
    }

    private fun buildPersonContacts(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()

        val allPhones = extractPhones(meta)
        if (allPhones.isNotEmpty()) {
            rows.add(sec("ALL PHONE NUMBERS (${allPhones.size})"))
            allPhones.forEach { rows.add("Phone" to it) }
        }

        val allAddresses = extractAddresses(meta)
        if (allAddresses.isNotEmpty()) {
            rows.add(sec("ALL KNOWN ADDRESSES (${allAddresses.size})"))
            allAddresses.forEach { rows.add("Address" to it) }
        }

        val allRel = extractRelatives(meta)
        if (allRel.isNotEmpty()) {
            rows.add(sec("RELATIVES & ASSOCIATES (${allRel.size})"))
            allRel.forEach { rows.add("⟶ Pivot Search" to "pivot://person/$it") }
        }

        run {
            val addrSections = listOf(
                "dork_address_results" to "ADDRESS INTEL",
                "dork_address_full_results" to "STREET ADDRESS INTEL",
                "dork_phone_results" to "PHONE LOOKUP INTEL"
            )
            addrSections.forEach { (key, label) ->
                meta[key]?.takeIf { it.isNotBlank() }?.let {
                    rows.add(sec("$label (AUTO-DORK)"))
                    it.split("\n---\n").filter { s -> s.isNotBlank() }.take(12).forEach { s -> rows.add("Intel" to s.trim()) }
                }
            }
        }
        meta["dork_relatives_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("RELATIVES INTEL (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(12).forEach { s -> rows.add("Relatives Intel" to s.trim()) }
        }
        meta["dork_voter_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("VOTER RECORDS (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(8).forEach { s -> rows.add("Voter" to s.trim()) }
        }
        meta["dork_email_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("EMAIL PATTERNS (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(8).forEach { s -> rows.add("Email Pattern" to s.trim()) }
        }

        val emails = linkedSetOf<String>()
        meta["pipl_email"]?.takeIf { it.isNotBlank() }?.let { emails.add(it) }
        meta["pdl_emails"]?.split(",")?.map { it.trim() }?.filter { it.contains("@") }?.forEach { emails.add(it) }
        meta["clearbit_person_email"]?.takeIf { it.isNotBlank() }?.let { emails.add(it) }
        meta["radaris_emails"]?.split(",")?.map { it.trim() }?.filter { it.contains("@") }?.forEach { emails.add(it) }
        meta["nuwber_emails"]?.split(",")?.map { it.trim() }?.filter { it.contains("@") }?.forEach { emails.add(it) }
        meta["cse_email_hits"]?.split(",")?.map { it.trim() }?.filter { it.contains("@") }?.forEach { emails.add(it) }
        if (emails.isNotEmpty()) {
            rows.add(sec("EMAIL ADDRESSES (${emails.size})"))
            emails.forEach { rows.add("Email" to it) }
        }

        val voterNames = meta["voter_names"]?.takeIf { it.isNotBlank() }
        if (voterNames != null) {
            rows.add(sec("VOTER REGISTRATION"))
            voterNames.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { rows.add("Registered As" to it) }
            meta["voter_addresses"]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { rows.add("Reg. Address" to it) }
            meta["voter_party"]?.let { rows.add("Party" to it) }
            meta["voter_age"]?.let { rows.add("Voter Age" to it) }
        }

        if (rows.isEmpty()) rows.add("Status" to "No contact data found for this subject")
        return rows
    }

    private fun buildPersonLegal(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()

        val arrestCount = meta["arrest_count"]?.toIntOrNull() ?: 0
        val courtCount = meta["courtlistener_count"]?.toIntOrNull() ?: 0
        val judyCount = meta["judyrecords_count"]?.toIntOrNull() ?: 0
        if (arrestCount > 0 || courtCount > 0 || judyCount > 0 || !meta["judyrecords_cases"].isNullOrBlank()) {
            rows.add(sec("CRIMINAL & COURT RECORDS"))
            if (arrestCount > 0) {
                rows.add("⚠ Arrests on File" to "$arrestCount record${if (arrestCount != 1) "s" else ""}")
                meta["arrest_records"]?.takeIf { it.isNotBlank() }?.let {
                    it.lines().filter { l -> l.isNotBlank() }.forEach { r -> rows.add("Arrest Record" to r) }
                }
            }
            if (courtCount > 0) {
                rows.add("CourtListener" to "$courtCount case${if (courtCount != 1) "s" else ""}")
                meta["courtlistener_link"]?.let { rows.add("⟶ View CourtListener" to it) }
            }
            if (judyCount > 0) rows.add("JudyRecords" to "$judyCount court record${if (judyCount != 1) "s" else ""}")
            meta["judyrecords_cases"]?.takeIf { it.isNotBlank() }?.let {
                it.lines().filter { l -> l.isNotBlank() }.forEach { c -> rows.add("Case" to c) }
            }
            meta["judyrecords_courts"]?.takeIf { it.isNotBlank() }?.let { rows.add("Courts" to it) }
        }

        val sanctionsTotal = meta["opensanctions_total"]?.toIntOrNull() ?: 0
        if (sanctionsTotal > 0) {
            rows.add(sec("⚠ SANCTIONS / PEP DATABASE"))
            rows.add("⚠ OpenSanctions Hits" to "$sanctionsTotal match${if (sanctionsTotal != 1) "es" else ""}")
            meta["opensanctions_names"]?.let { rows.add("Matched Names" to it) }
            meta["opensanctions_datasets"]?.let { rows.add("Datasets" to it) }
            meta["opensanctions_countries"]?.let { rows.add("Countries" to it) }
            meta["opensanctions_link"]?.let { rows.add("⟶ View OpenSanctions" to it) }
        }

        meta["dork_criminal_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("CRIMINAL INTEL (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(12).forEach { s -> rows.add("Criminal" to s.trim()) }
        }
        meta["dork_court_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("COURT INTEL (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(12).forEach { s -> rows.add("Court" to s.trim()) }
        }

        val ctx = FindingUrlHelper.subjectContext(meta)
        rows.add("⟶ Search Court Records" to FindingUrlHelper.courtUrl(ctx))
        rows.add("⟶ Search JudyRecords" to OsintToolRegistry.buildUrl(
            "https://www.judyrecords.com/search?search={q-encoded}", ctx.name.ifBlank { ctx.email }
        ))
        if (rows.size <= 2) rows.add(0, sec("LEGAL & COURT RECORDS"))
        if (rows.none { it.first == "Status" } && arrestCount == 0 && courtCount == 0 && judyCount == 0
            && meta["dork_court_results"].isNullOrBlank() && meta["dork_criminal_results"].isNullOrBlank()) {
            rows.add("Status" to "No legal records found — use links above to search")
        }
        return rows
    }

    private fun buildPersonIntel(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()

        appendGoogleIntelligence(rows, meta)

        meta["search_social_links"]?.takeIf { it.isNotBlank() }?.let { links ->
            val linkList = links.lines().filter { it.isNotBlank() }
            if (linkList.isNotEmpty()) {
                rows.add(sec("SOCIAL & PROFILE LINKS"))
                linkList.take(8).forEach { rows.add("Profile" to it.trim()) }
            }
        }
        meta["search_snippets"]?.takeIf { it.isNotBlank() }?.let { snips ->
            val lines = snips.lines().filter { it.isNotBlank() }
            if (lines.isNotEmpty()) {
                rows.add(sec("WEB INTELLIGENCE"))
                lines.take(12).forEach { rows.add("Source" to it.trim()) }
            }
        }
        meta["holehe_services"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("EMAIL REGISTRATIONS (Holehe)"))
            it.split(",").map { s -> s.trim() }.filter { s -> s.isNotBlank() }.forEach { svc ->
                rows.add("Registered" to svc)
            }
        }

        val googleNewsSnippet = meta["google_news_snippet"]?.takeIf { it.isNotBlank() }
        val googleNewsCount = meta["google_news_news_count"]?.toIntOrNull() ?: 0
        if (googleNewsSnippet != null || googleNewsCount > 0) {
            rows.add(sec("GOOGLE NEWS"))
            googleNewsSnippet?.lines()?.filter { it.isNotBlank() }?.take(10)?.forEach { rows.add("Article" to it.trim()) }
        }

        val gnewsCount = meta["gnews_count"]?.toIntOrNull() ?: 0
        val newsCount = meta["news_article_count"]?.toIntOrNull() ?: 0
        val wikiHits = meta["wikipedia_hits"]?.toIntOrNull() ?: 0
        if (gnewsCount > 0 || newsCount > 0 || wikiHits > 0 || !meta["wikidata_descriptions"].isNullOrBlank()) {
            rows.add(sec("NEWS & PUBLIC RECORDS"))
            meta["wikipedia_titles"]?.let { rows.add("Wikipedia" to it) }
            meta["wikipedia_link"]?.takeIf { wikiHits > 0 }?.let { rows.add("⟶ View on Wikipedia" to it) }
            meta["wikidata_descriptions"]?.let { rows.add("WikiData" to it) }
            meta["wikidata_link"]?.let { rows.add("⟶ View on WikiData" to it) }
            meta["wikidata_employers"]?.takeIf { it.isNotBlank() }?.let { rows.add("Employers" to it) }
            meta["wikidata_organizations"]?.takeIf { it.isNotBlank() }?.let { rows.add("Organizations" to it) }
            if (gnewsCount > 0) {
                meta["gnews_articles"]?.takeIf { it.isNotBlank() }?.let {
                    it.split("\n---\n").filter { a -> a.isNotBlank() }.forEach { article ->
                        val lines = article.lines().filter { l -> l.isNotBlank() }
                        if (lines.isNotEmpty()) rows.add("News" to lines.joinToString(" · "))
                    }
                }
            }
            if (newsCount > 0) {
                meta["news_titles"]?.takeIf { it.isNotBlank() }?.let {
                    it.lines().filter { l -> l.isNotBlank() }.forEach { t -> rows.add("Headline" to t) }
                }
            }
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
        if (officerCount > 0 || secHits > 0 || secFulltextHits > 0 || fecCount > 0
            || gleifEntities != null || sunbizOfficerCount > 0 || samEntityCount > 0 || caOfficerCount > 0 || corpwikiCount > 0) {
            rows.add(sec("CORPORATE & FINANCIAL"))
            meta["officer_details"]?.takeIf { it.isNotBlank() }?.let {
                it.lines().filter { l -> l.isNotBlank() }.forEach { d -> rows.add("Corporate Role" to d) }
            }
            if (caOfficerCount > 0) {
                rows.add("CA SOS Records" to "$caOfficerCount California entit${if (caOfficerCount != 1) "ies" else "y"}")
                meta["ca_sos_officer_entities"]?.takeIf { it.isNotBlank() }?.let {
                    it.lines().filter { l -> l.isNotBlank() }.forEach { e -> rows.add("CA Entity" to e) }
                }
            }
            if (sunbizOfficerCount > 0) {
                rows.add("FL SOS Records" to "$sunbizOfficerCount Florida filing${if (sunbizOfficerCount != 1) "s" else ""}")
                meta["sunbiz_officer_companies"]?.takeIf { it.isNotBlank() }?.let {
                    it.lines().filter { l -> l.isNotBlank() }.forEach { co -> rows.add("FL Company" to co) }
                }
            }
            if (corpwikiCount > 0) {
                rows.add("OpenCorporates" to "$corpwikiCount officer record${if (corpwikiCount != 1) "s" else ""}")
                val companies = meta["corpwiki_person_companies"]?.lines()?.filter { it.isNotBlank() } ?: emptyList()
                val positions = meta["opencorp_positions"]?.split(",")?.map { it.trim() } ?: emptyList()
                companies.forEachIndexed { i, co ->
                    val pos = positions.getOrNull(i)
                    rows.add("Company" to if (pos != null && pos.isNotBlank()) "$co — $pos" else co)
                }
                meta["corpwiki_person_states"]?.let { rows.add("Jurisdictions" to it) }
                meta["opencorp_start_dates"]?.let { rows.add("Active Since" to it) }
            }
            if (samEntityCount > 0) {
                rows.add("SAM.gov Entities" to "$samEntityCount entit${if (samEntityCount != 1) "ies" else "y"}")
                meta["sam_entities"]?.takeIf { it.isNotBlank() }?.let {
                    it.lines().filter { l -> l.isNotBlank() }.forEach { e -> rows.add("Federal Entity" to e) }
                }
            }
            if (secHits > 0) {
                rows.add("SEC EDGAR Form-4" to "$secHits filing${if (secHits != 1) "s" else ""} (insider trading)")
                meta["sec_person_entities"]?.let { rows.add("Affiliated Companies" to it) }
            }
            if (secFulltextHits > 0) {
                rows.add("SEC EDGAR All Forms" to "$secFulltextHits filing${if (secFulltextHits != 1) "s" else ""}")
                meta["sec_fulltext_forms"]?.let { rows.add("Form Types" to it) }
                meta["sec_fulltext_entities"]?.let { rows.add("SEC Entities" to it) }
            }
            if (gleifEntities != null) {
                rows.add("GLEIF Entities" to "${gleifEntities.lines().filter { it.isNotBlank() }.size} found")
                gleifEntities.lines().filter { it.isNotBlank() }.forEach { e -> rows.add("Legal Entity" to e) }
            }
            meta["fec_candidates"]?.takeIf { it.isNotBlank() }?.let {
                it.lines().filter { l -> l.isNotBlank() }.forEach { r -> rows.add("FEC Campaign" to r) }
            }
        }

        meta["dork_property_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("PROPERTY INTEL (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(10).forEach { s -> rows.add("Property" to s.trim()) }
        }
        meta["dork_financial_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("FINANCIAL INTEL (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(10).forEach { s -> rows.add("Financial" to s.trim()) }
        }
        meta["dork_vehicle_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("VEHICLE TRACE (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(10).forEach { s -> rows.add("Vehicle" to s.trim()) }
        }
        meta["dork_education_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("EDUCATION & SCHOOL HISTORY (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(10).forEach { s -> rows.add("Education" to s.trim()) }
        }
        meta["dork_yearbook_results"]?.takeIf { it.isNotBlank() }?.let {
            val existing = meta["dork_education_results"]
            if (existing.isNullOrBlank()) rows.add(sec("EDUCATION & SCHOOL HISTORY (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(8).forEach { s -> rows.add("Alumni" to s.trim()) }
        }
        meta["dork_awards_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("AWARDS & RECOGNITION (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(10).forEach { s -> rows.add("Award" to s.trim()) }
        }
        meta["dork_sports_results"]?.takeIf { it.isNotBlank() }?.let {
            val existing = meta["dork_awards_results"]
            if (existing.isNullOrBlank()) rows.add(sec("AWARDS & RECOGNITION (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(8).forEach { s -> rows.add("Sports" to s.trim()) }
        }
        meta["dork_obituary_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("OBITUARY / GENEALOGY (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(10).forEach { s -> rows.add("Obituary" to s.trim()) }
        }
        meta["dork_bio_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("PROFESSIONAL BIO (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(10).forEach { s -> rows.add("Bio" to s.trim()) }
        }
        meta["dork_linkedin_results"]?.takeIf { it.isNotBlank() }?.let {
            val existing = meta["dork_bio_results"]
            if (existing.isNullOrBlank()) rows.add(sec("PROFESSIONAL BIO (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(8).forEach { s -> rows.add("LinkedIn" to s.trim()) }
        }
        meta["dork_philanthropy_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("PHILANTHROPY & BOARD ACTIVITY (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(10).forEach { s -> rows.add("Philanthropy" to s.trim()) }
        }
        meta["dork_news_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("DEEP NEWS MENTIONS (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(10).forEach { s -> rows.add("News" to s.trim()) }
        }
        meta["dork_gov_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("GOVERNMENT DOCUMENT MENTIONS (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(10).forEach { s -> rows.add("Gov Doc" to s.trim()) }
        }
        meta["dork_social_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("SOCIAL MEDIA TRACES (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(10).forEach { s -> rows.add("Social" to s.trim()) }
        }
        meta["dork_leaks_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("⚠ LEAKED DATA (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(10).forEach { s -> rows.add("⚠ Leak" to s.trim()) }
        }
        meta["dork_dark_results"]?.takeIf { it.isNotBlank() }?.let {
            val existing = meta["dork_leaks_results"]
            if (existing.isNullOrBlank()) rows.add(sec("⚠ LEAKED DATA (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(8).forEach { s -> rows.add("⚠ Dark" to s.trim()) }
        }
        meta["dork_files_results"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("DOCUMENT DUMP RESULTS (AUTO-DORK)"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(10).forEach { s -> rows.add("File" to s.trim()) }
        }
        run {
            val tps = meta["dork_tps_results"]; val wp = meta["dork_wp_results"]
            val spk = meta["dork_spk_results"]; val fps = meta["dork_fps_results"]
            val rad = meta["dork_rad_results"]; val z411 = meta["dork_411_results"]
            val zaba = meta["dork_zaba_results"]; val bv = meta["dork_bv_results"]
            val pf = meta["dork_pf_results"]; val ml = meta["dork_ml_results"]
            val snippets = listOfNotNull(tps, wp, spk, fps, rad, z411, zaba, bv, pf, ml)
                .flatMap { it.split("\n---\n") }.filter { it.isNotBlank() }.distinct().take(20)
            if (snippets.isNotEmpty()) {
                rows.add(sec("PEOPLE-SEARCH SITE SNIPPETS (AUTO-DORK)"))
                snippets.forEach { s -> rows.add("Profile Data" to s.trim()) }
            }
        }

        val chroniclingTotal = meta["chronicling_total"]?.toIntOrNull() ?: 0
        if (chroniclingTotal > 0) {
            rows.add(sec("HISTORICAL NEWSPAPERS (Library of Congress)"))
            rows.add("Total Hits" to "$chroniclingTotal historical newspaper mention${if (chroniclingTotal != 1) "s" else ""}")
            meta["chronicling_papers"]?.let { rows.add("Newspapers" to it) }
            meta["chronicling_states"]?.let { rows.add("States Found In" to it) }
            meta["chronicling_dates"]?.let { rows.add("Date Range" to it) }
            meta["chronicling_excerpts"]?.takeIf { it.isNotBlank() }?.let {
                it.split("\n---\n").filter { s -> s.isNotBlank() }.take(5).forEach { s -> rows.add("Excerpt" to s.trim()) }
            }
            meta["chronicling_link"]?.let { rows.add("⟶ View All on LOC" to it) }
        }

        val openLibCount = meta["openlibrary_count"]?.toIntOrNull() ?: 0
        val orcidCount = meta["orcid_count"]?.toIntOrNull() ?: 0
        val crossrefTotal = meta["crossref_total"]?.toIntOrNull() ?: 0
        if (openLibCount > 0 || orcidCount > 0 || crossrefTotal > 0) {
            rows.add(sec("ACADEMIC & PUBLISHED WORKS"))
            if (openLibCount > 0) {
                rows.add("Open Library" to "$openLibCount book${if (openLibCount != 1) "s" else ""} found")
                meta["openlibrary_titles"]?.takeIf { it.isNotBlank() }?.let {
                    it.lines().filter { l -> l.isNotBlank() }.take(5).forEach { t -> rows.add("Book" to t) }
                }
                meta["openlibrary_link"]?.let { rows.add("⟶ Open Library" to it) }
            }
            if (orcidCount > 0) {
                rows.add("ORCID" to "$orcidCount researcher profile${if (orcidCount != 1) "s" else ""}")
                meta["orcid_ids"]?.let { rows.add("ORCID IDs" to it) }
                meta["orcid_link"]?.let { rows.add("⟶ ORCID Search" to it) }
            }
            if (crossrefTotal > 0) {
                rows.add("Crossref" to "$crossrefTotal academic publication${if (crossrefTotal != 1) "s" else ""}")
                meta["crossref_titles"]?.takeIf { it.isNotBlank() }?.let {
                    it.lines().filter { l -> l.isNotBlank() }.take(5).forEach { t -> rows.add("Publication" to t) }
                }
                meta["crossref_journals"]?.let { rows.add("Journals" to it) }
                meta["crossref_link"]?.let { rows.add("⟶ Crossref Search" to it) }
            }
        }

        val hasSearchIntel = !meta["cse_snippets"].isNullOrBlank() || !meta["bing_snippets"].isNullOrBlank()
            || !meta["searx_snippets"].isNullOrBlank() || !meta["ddg_web_snippets"].isNullOrBlank()
        if (hasSearchIntel) {
            rows.add(sec("SEARCH ENGINE INTEL"))
            meta["ddg_web_snippets"]?.takeIf { it.isNotBlank() }?.let {
                it.split("\n---\n").filter { s -> s.isNotBlank() }.take(8).forEach { s ->
                    rows.add("DDG" to s.trim())
                }
            }
            meta["searx_snippets"]?.takeIf { it.isNotBlank() }?.let {
                it.split("\n---\n").filter { s -> s.isNotBlank() }.take(6).forEach { s ->
                    rows.add("Web" to s.trim())
                }
            }
            meta["bing_total"]?.let { t -> if ((t.toLongOrNull() ?: 0L) > 0) rows.add("Bing Results" to t) }
            meta["cse_snippets"]?.takeIf { it.isNotBlank() }?.let {
                it.split("\n---\n").filter { s -> s.isNotBlank() }.take(5).forEach { s ->
                    rows.add("Google CSE" to s.trim())
                }
            }
            meta["cse_error"]?.takeIf { it.isNotBlank() }?.let { rows.add("⚠ Google CSE Error" to it) }
            meta["bing_snippets"]?.takeIf { it.isNotBlank() }?.let {
                it.split("\n---\n").filter { s -> s.isNotBlank() }.take(5).forEach { s ->
                    rows.add("Bing" to s.trim())
                }
            }
        }

        meta["cse_profile_links"]?.takeIf { it.isNotBlank() }?.let { profileBlock ->
            val profileLines = profileBlock.lines().filter { it.isNotBlank() }
            if (profileLines.isNotEmpty()) {
                rows.add(sec("PEOPLE-SEARCH PROFILES FOUND"))
                rows.add("Note" to "Google indexed these profiles — tap to open directly")
                profileLines.take(10).forEach { line ->
                    val parts = line.split(" → ", limit = 2)
                    val site = parts.firstOrNull() ?: "Profile"
                    val url = parts.getOrNull(1) ?: line
                    rows.add("⟶ $site" to url)
                }
            }
        }

        val pasteCount = meta["paste_count"]?.toIntOrNull() ?: 0
        if (pasteCount > 0) {
            rows.add(sec("⚠ PASTE DUMPS"))
            rows.add("⚠ Paste Hits" to "$pasteCount paste dump${if (pasteCount != 1) "s" else ""} mention this subject")
            meta["paste_snippets"]?.split("\n---\n")?.filter { it.isNotBlank() }?.take(5)?.forEach { s ->
                rows.add("Paste Excerpt" to s.trim())
            }
            meta["paste_ids"]?.let { rows.add("Paste IDs" to it) }
        }

        val grepCount = meta["grep_code_count"]?.toIntOrNull() ?: 0
        if (grepCount > 0) {
            rows.add(sec("CODE REPOSITORY MENTIONS"))
            rows.add("Repo Hits" to "$grepCount match${if (grepCount != 1) "es" else ""} in public code repositories")
            meta["grep_code_repos"]?.let { rows.add("Repositories" to it) }
        }

        val darkSearchSnippet = meta["darksearch_snippet"]?.takeIf { it.isNotBlank() }
        val darkSearchLinks = (meta["darksearch_links"] ?: meta["darksearch_dark_links"])?.takeIf { it.isNotBlank() }
        if (darkSearchSnippet != null || darkSearchLinks != null) {
            rows.add(sec("⚠ DARKSEARCH RESULTS"))
            darkSearchSnippet?.lines()?.filter { it.isNotBlank() }?.take(8)?.forEach { rows.add("⚠ Result" to it.trim()) }
            darkSearchLinks?.lines()?.filter { it.isNotBlank() }?.take(8)?.forEach { rows.add("⟶ Link" to it.trim()) }
        }

        val ahmiaCountRaw = meta["ahmia_count"]
        if (ahmiaCountRaw != null) {
            val ahmiaCount = ahmiaCountRaw.toIntOrNull() ?: 0
            rows.add(sec("⚠ DARK WEB INDEX CHECK"))
            val viaToR = meta["ahmia_via_tor"]?.toBooleanStrictOrNull() == true
            if (ahmiaCount > 0) {
                rows.add("⚠ Indexed Hits" to "$ahmiaCount result${if (ahmiaCount != 1) "s" else ""} found via Ahmia.fi Tor index")
                if (viaToR) rows.add("⚠ Connection" to "Fetched via Tor network")
                val titleLines = meta["ahmia_titles"]?.lines()?.filter { it.isNotBlank() } ?: emptyList()
                val urlLines = meta["ahmia_urls"]?.lines()?.filter { it.isNotBlank() } ?: emptyList()
                val descLines = meta["ahmia_descs"]?.split("\n---\n")?.filter { it.isNotBlank() } ?: emptyList()
                titleLines.forEachIndexed { i, t ->
                    rows.add("⚠ Tor Site" to t)
                    descLines.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { d -> rows.add("  Description" to d) }
                    urlLines.getOrNull(i)?.let { u -> rows.add("  .onion URL" to u) }
                }
            } else {
                rows.add("Dark Web Status" to "No mentions found in Ahmia.fi Tor index")
                if (viaToR) rows.add("Connection" to "Searched via Tor network") else rows.add("Connection" to "Searched via clearnet proxy")
            }
            rows.add("⚠ Note" to "Ahmia indexes publicly-accessible Tor hidden services. Subject to index freshness.")
        }

        val torchCount = meta["torch_count"]?.toIntOrNull() ?: 0
        if (torchCount > 0) {
            rows.add(sec("⚠ TORCH DARK WEB (via Tor)"))
            rows.add("⚠ Torch Hits" to "$torchCount result${if (torchCount != 1) "s" else ""} on Torch .onion search engine")
            val torchTitles = meta["torch_titles"]?.lines()?.filter { it.isNotBlank() } ?: emptyList()
            val torchUrls = meta["torch_urls"]?.lines()?.filter { it.isNotBlank() } ?: emptyList()
            val torchDescs = meta["torch_descs"]?.split("\n---\n")?.filter { it.isNotBlank() } ?: emptyList()
            torchTitles.forEachIndexed { i, t ->
                rows.add("⚠ Result" to t)
                torchDescs.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { d -> rows.add("  Excerpt" to d.take(200)) }
                torchUrls.getOrNull(i)?.let { u -> rows.add("  .onion URL" to u) }
            }
        }

        appendAiReportRows(rows, meta)


        val socialLinks = buildSocialLinks(meta)
        val piplSocials = meta["pipl_socials"]?.takeIf { it.isNotBlank() }
        if (piplSocials != null) {
            val allLinks = if (socialLinks.isEmpty()) mutableListOf() else socialLinks.toMutableList()
            piplSocials.lines().filter { it.isNotBlank() }.forEach { line ->
                val parts = line.split(": ", limit = 2)
                val platform = parts.firstOrNull() ?: "Social"
                val url = parts.getOrNull(1) ?: line
                allLinks.add("⟶ $platform" to url)
            }
            if (allLinks.isNotEmpty()) {
                rows.add(sec("SOCIAL & WEB PROFILES"))
                allLinks.distinct().take(15).forEach { (label, url) -> rows.add(label to url) }
            }
        } else if (socialLinks.isNotEmpty()) {
            rows.add(sec("SOCIAL PROFILES (PeekYou)"))
            socialLinks.forEach { (label, url) -> rows.add(label to url) }
        }

        meta["comp_derived_usernames"]?.takeIf { it.isNotBlank() }?.let { unames ->
            rows.add(sec("DERIVED USERNAME CANDIDATES"))
            unames.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { u ->
                rows.add("Username Candidate" to u)
            }
        }
        meta["context_search_snippets"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("CONTEXT SEARCH RESULTS"))
            meta["context_search_titles"]?.lines()?.filter { l -> l.isNotBlank() }?.take(5)
                ?.forEach { t -> rows.add("Context Hit" to t) }
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(8).forEach { s -> rows.add("Context" to s.trim()) }
        }
        meta["ddg_social_snippets"]?.takeIf { it.isNotBlank() }?.let {
            val existing = meta["dork_social_results"]
            if (existing.isNullOrBlank()) rows.add(sec("SOCIAL MEDIA TRACES"))
            it.split("\n---\n").filter { s -> s.isNotBlank() }.take(6).forEach { s -> rows.add("Social Trace" to s.trim()) }
        }

        val pivotPhones = extractPhones(meta).take(5)
        val pivotEmails = linkedSetOf<String>()
        (meta["comp_email"] ?: meta["email"])?.takeIf { it.isNotBlank() }?.let { pivotEmails.add(it) }
        meta["cse_email_hits"]?.split(",")?.map { it.trim() }?.filter { it.contains("@") }?.forEach { pivotEmails.add(it) }
        meta["radaris_emails"]?.split(",")?.map { it.trim() }?.filter { it.contains("@") }?.forEach { pivotEmails.add(it) }
        meta["nuwber_emails"]?.split(",")?.map { it.trim() }?.filter { it.contains("@") }?.forEach { pivotEmails.add(it) }
        val rawSearchQuery = arguments?.getString("searchQuery") ?: ""
        val parsedSearchName = rawSearchQuery.split("|")
            .firstOrNull { it.startsWith("name=") }?.substringAfter("=")?.trim()
            ?: rawSearchQuery.split("|").firstOrNull { !it.contains("=") }?.trim() ?: ""
        if (pivotPhones.isNotEmpty() || pivotEmails.isNotEmpty()) {
            rows.add(sec("PIVOT SEARCHES"))
            pivotPhones.forEach { phone -> rows.add("⟶ Search Phone" to "pivot://phone/$phone") }
            pivotEmails.take(3).forEach { email -> rows.add("⟶ Search Email" to "pivot://email/$email") }
            if (parsedSearchName.isNotBlank()) {
                val parts = parsedSearchName.trim().split(" ")
                if (parts.size >= 2) rows.add("⟶ Reversed Name" to "pivot://person/${parts.last()} ${parts.first()}")
            }
        }

        if (rows.isEmpty()) rows.add("Status" to "No additional intelligence found for this subject")
        return rows
    }

    private fun buildEmailOverview(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        rows.add(sec("REPUTATION"))
        meta["emailrep_reputation"]?.let { rows.add("Reputation" to it.replaceFirstChar { c -> c.uppercase() }) }
        meta["emailrep_suspicious"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Suspicious" to "Flagged by EmailRep threat database") }
        meta["emailrep_breach"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Breach Exposure" to "Involved in known data breach") }
        meta["emailrep_references"]?.let { rows.add("DB References" to it) }
        meta["emailrep_profiles"]?.takeIf { it.isNotBlank() }?.let { rows.add("Seen On" to it) }
        meta["eva_deliverable"]?.let { rows.add("Deliverable" to it.replaceFirstChar { c -> c.uppercase() }) }
        meta["eva_disposable"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Disposable" to "Temporary/throwaway email service") }
        meta["eva_spam_trap"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Spam Trap" to "Address is a spam trap") }
        meta["eva_mx_record"]?.let { rows.add("MX Record" to it) }
        meta["kickbox_disposable"]?.toBooleanStrictOrNull()?.let { disposable ->
            rows.add("Disposable (Kickbox)" to if (disposable) "Yes — temporary provider" else "No")
        }
        meta["kickbox_did_you_mean"]?.takeIf { it.isNotBlank() }?.let { rows.add("Did You Mean (Kickbox)" to it) }
        val hibpCount = meta["hibp_breach_count"]?.toIntOrNull() ?: 0
        val proxyCount = meta["proxynova_breach_count"]?.toIntOrNull() ?: 0
        if (hibpCount > 0 || proxyCount > 0) {
            rows.add(sec("BREACH SUMMARY"))
            if (hibpCount > 0) rows.add("⚠ HIBP Breaches" to "$hibpCount breach${if (hibpCount != 1) "es" else ""}")
            if (proxyCount > 0) rows.add("COMB Dataset" to "$proxyCount record${if (proxyCount != 1) "s" else ""}")
        }
        return rows
    }

    private fun buildEmailBreaches(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        val hibpCount = meta["hibp_breach_count"]?.toIntOrNull() ?: 0
        val proxyCount = meta["proxynova_breach_count"]?.toIntOrNull() ?: 0
        val leakCount = meta["leakcheck_found"]?.toIntOrNull() ?: 0

        if (hibpCount > 0) {
            rows.add(sec("HIBP — $hibpCount BREACH${if (hibpCount != 1) "ES" else ""}"))
            val nsfwDomains = setOf("ashleymadison.com", "adultfriendfinder.com", "fling.com", "penthouse.com")
            val nsfwClasses = setOf("Sexual fetishes", "Sexual preferences", "Sexual orientation", "Intimate photos", "Nude photos")
            meta["hibp_breach_details"]?.takeIf { it.isNotBlank() }?.let {
                it.lines().filter { l -> l.isNotBlank() }.forEach { detail ->
                    val lDetail = detail.lowercase()
                    val isNsfw = nsfwDomains.any { d -> lDetail.contains(d) }
                        || nsfwClasses.any { dc -> detail.contains(dc, ignoreCase = true) }
                        || detail.contains("[SENSITIVE]", ignoreCase = true)
                        || lDetail.contains("ashley madison") || lDetail.contains("adult friend finder")
                    rows.add(if (isNsfw) "⚠ NSFW Breach" to detail else "Breach" to detail)
                }
            } ?: meta["hibp_breaches"]?.takeIf { it.isNotBlank() }?.let { rows.add("Breach Names" to it) }
        }

        if (proxyCount > 0) {
            rows.add(sec("COMB DATASET — $proxyCount TOTAL RECORD${if (proxyCount != 1) "S" else ""}"))
            rows.add("Database" to "Collection of Many Breaches — 3.2B leaked credentials")
            meta["proxynova_samples"]?.takeIf { it.isNotBlank() }?.let { samples ->
                samples.lines().filter { it.isNotBlank() }.forEach { line ->
                    val colonIdx = line.indexOf(':', line.indexOf('@').let { if (it >= 0) it + 1 else 0 })
                    if (colonIdx > 0) {
                        val user = line.substring(0, colonIdx).trim()
                        val pass = line.substring(colonIdx + 1).trim()
                        rows.add("Login" to user)
                        rows.add("Password / Hash" to pass)
                    } else {
                        rows.add("Leaked Record" to line)
                    }
                }
            }
        }

        if (leakCount > 0) {
            rows.add(sec("LEAKCHECK — $leakCount SOURCE${if (leakCount != 1) "S" else ""}"))
            meta["leakcheck_sources"]?.takeIf { it.isNotBlank() }?.let { rows.add("Leak Sources" to it) }
        }

        val ipqsLeaked = meta["ipqs_email_leaked"]?.toBooleanStrictOrNull() ?: false
        val ipqsFraud = meta["ipqs_email_fraud_score"]?.toIntOrNull() ?: -1
        if (ipqsLeaked || ipqsFraud >= 0) {
            rows.add(sec("IPQS RISK SCORING"))
            if (ipqsFraud >= 0) rows.add("Fraud Score" to "$ipqsFraud / 100${if (ipqsFraud > 70) " ⚠ HIGH RISK" else ""}")
            if (ipqsLeaked) rows.add("⚠ Dark Web Leaked" to "Found in dark web leaks")
            meta["ipqs_email_suspect"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Suspect" to "Flagged as suspect") }
        }

        val emailrepBreached = meta["emailrep_breach"]?.toBooleanStrictOrNull() ?: false
        if (emailrepBreached && rows.isEmpty()) {
            rows.add(sec("EMAILREP — BREACH CONFIRMED"))
            rows.add("Status" to "Address found in known data breaches")
            meta["emailrep_profiles"]?.takeIf { it.isNotBlank() }?.let { rows.add("Seen On" to it) }
            meta["emailrep_references"]?.toIntOrNull()?.let { if (it > 0) rows.add("Reference Count" to "$it sources") }
            rows.add("Note" to "Add HIBP key in Settings → API Keys for full breach names and exposed fields")
        }

        if (rows.isEmpty()) rows.add("Status" to "No breach data found for this email")
        return rows
    }

    private fun buildEmailIdentity(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        val hasGravatar = !meta["gravatar_name"].isNullOrBlank() || !meta["gravatar_location"].isNullOrBlank()
        if (hasGravatar) {
            rows.add(sec("GRAVATAR PROFILE"))
            meta["gravatar_name"]?.takeIf { it.isNotBlank() }?.let { rows.add("Name" to it) }
            meta["gravatar_location"]?.takeIf { it.isNotBlank() }?.let { rows.add("Location" to it) }
            meta["gravatar_bio"]?.takeIf { it.isNotBlank() }?.let { rows.add("Bio" to it) }
            meta["gravatar_accounts"]?.takeIf { it.isNotBlank() }?.let { rows.add("Linked Accounts" to it) }
        }
        val hasFullContact = !meta["fullcontact_name"].isNullOrBlank() || !meta["fullcontact_location"].isNullOrBlank()
        if (hasFullContact) {
            rows.add(sec("IDENTITY ENRICHMENT"))
            meta["fullcontact_name"]?.takeIf { it.isNotBlank() }?.let { rows.add("Full Name" to it) }
            meta["fullcontact_location"]?.takeIf { it.isNotBlank() }?.let { rows.add("Location" to it) }
            meta["fullcontact_title"]?.takeIf { it.isNotBlank() }?.let { rows.add("Job Title" to it) }
            meta["fullcontact_org"]?.takeIf { it.isNotBlank() }?.let { rows.add("Employer" to it) }
            meta["fullcontact_age_range"]?.takeIf { it.isNotBlank() }?.let { rows.add("Age Range" to it) }
            meta["fullcontact_twitter"]?.takeIf { it.isNotBlank() }?.let { rows.add("Twitter" to "@$it") }
            meta["fullcontact_linkedin"]?.takeIf { it.isNotBlank() }?.let { rows.add("LinkedIn" to it) }
        }
        val hasThreat = !meta["threatcrowd_email_domains"].isNullOrBlank() || !meta["hackertarget_email_hosts"].isNullOrBlank()
        if (hasThreat) {
            rows.add(sec("THREAT INTEL"))
            meta["threatcrowd_email_domains"]?.takeIf { it.isNotBlank() }?.let { rows.add("Linked Domains" to it) }
            meta["hackertarget_email_hosts"]?.takeIf { it.isNotBlank() }?.let { rows.add("Associated Hosts" to it) }
        }
        meta["holehe_found"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("HOLEHE — REGISTERED SERVICES"))
            rows.add("Services" to it)
        }
        if (rows.isEmpty()) rows.add("Status" to "No identity data linked to this email")
        return rows
    }

    private fun buildIpNetwork(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        rows.add(sec("GEOLOCATION"))
        meta["ip_city"]?.takeIf { it.isNotBlank() }?.let { rows.add("City" to it) }
        meta["ip_country"]?.takeIf { it.isNotBlank() }?.let { rows.add("Country" to it) }
        meta["ip_timezone"]?.takeIf { it.isNotBlank() }?.let { rows.add("Timezone" to it) }
        meta["ipwhois_city"]?.takeIf { it.isNotBlank() && meta["ip_city"].isNullOrBlank() }?.let { rows.add("City (ipwho.is)" to it) }
        meta["ipwhois_country"]?.takeIf { it.isNotBlank() && meta["ip_country"].isNullOrBlank() }?.let { rows.add("Country (ipwho.is)" to it) }
        rows.add(sec("NETWORK"))
        meta["ip_isp"]?.takeIf { it.isNotBlank() }?.let { rows.add("ISP" to it) }
        meta["ip_org"]?.takeIf { it.isNotBlank() }?.let { rows.add("Org" to it) }
        meta["ip_asn"]?.takeIf { it.isNotBlank() }?.let { rows.add("ASN" to it) }
        meta["ipinfo_hostname"]?.takeIf { it.isNotBlank() }?.let { rows.add("Hostname" to it) }
        meta["ipinfo_postal"]?.takeIf { it.isNotBlank() }?.let { rows.add("Postal" to it) }
        meta["robtex_as_name"]?.takeIf { it.isNotBlank() }?.let { rows.add("AS Name" to it) }
        meta["robtex_bgp_route"]?.takeIf { it.isNotBlank() }?.let { rows.add("BGP Route" to it) }
        meta["robtex_passive_dns"]?.takeIf { it.isNotBlank() }?.let { rows.add("Passive DNS" to it) }
        return rows
    }

    private fun buildIpThreats(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        val hasPorts = !meta["shodan_ports"].isNullOrBlank()
        val hasVulns = !meta["shodan_vulns"].isNullOrBlank()
        if (hasPorts || hasVulns) {
            rows.add(sec("SHODAN EXPOSURE"))
            meta["shodan_ports"]?.let { rows.add("Open Ports" to it) }
            meta["shodan_hostnames"]?.let { rows.add("Hostnames" to it) }
            meta["shodan_vulns"]?.takeIf { it.isNotBlank() }?.let { rows.add("⚠ CVEs" to it) }
        }
        val ipqueryRisk = meta["ipquery_risk_score"]?.toIntOrNull() ?: -1
        val ipqsIpFraud = meta["ipqs_ip_fraud_score"]?.toIntOrNull() ?: -1
        if (ipqueryRisk >= 0) {
            rows.add(sec("IPQUERY RISK"))
            rows.add("Risk Score" to "$ipqueryRisk / 100${if (ipqueryRisk > 70) " ⚠ HIGH" else if (ipqueryRisk > 30) " ⚠ Moderate" else " — Low"}")
            meta["ipquery_vpn"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ VPN" to "Known VPN exit node") }
            meta["ipquery_proxy"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Proxy" to "Known proxy") }
            meta["ipquery_tor"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Tor" to "Tor exit node") }
        }
        if (ipqsIpFraud >= 0) {
            rows.add(sec("IPQS FRAUD SCORE"))
            rows.add("Fraud Score" to "$ipqsIpFraud / 100${if (ipqsIpFraud > 70) " ⚠ HIGH" else ""}")
            meta["ipqs_ip_vpn"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ VPN (IPQS)" to "Known VPN") }
            meta["ipqs_ip_proxy"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Proxy (IPQS)" to "Known proxy") }
            meta["ipqs_ip_tor"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Tor (IPQS)" to "Tor node") }
        }
        meta["greynoise_classification"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("GREYNOISE"))
            rows.add("Classification" to it.replaceFirstChar { c -> c.uppercase() })
            meta["greynoise_noise"]?.toBooleanStrictOrNull()?.let { n -> if (n) rows.add("⚠ Internet Scanner" to "This IP actively scans the internet") }
            meta["greynoise_name"]?.takeIf { it.isNotBlank() }?.let { n -> rows.add("Actor" to n) }
            meta["greynoise_last_seen"]?.takeIf { it.isNotBlank() }?.let { d -> rows.add("Last Seen" to d) }
        }
        meta["abuseipdb_score"]?.let { s ->
            if ((s.toIntOrNull() ?: 0) > 0) {
                rows.add(sec("ABUSEIPDB"))
                rows.add("⚠ Abuse Score" to "$s%")
                meta["abuseipdb_reports"]?.let { r -> rows.add("Reports" to r) }
            }
        }
        meta["otx_pulse_count"]?.let { p -> if ((p.toIntOrNull() ?: 0) > 0) rows.add("⚠ OTX Pulses" to p) }
        val vtMalicious = meta["vt_malicious"]?.toIntOrNull() ?: 0
        val vtHarmless = meta["vt_harmless"]?.toIntOrNull() ?: 0
        val vtSuspicious = meta["vt_suspicious"]?.toIntOrNull() ?: 0
        if (vtMalicious > 0 || vtHarmless > 0 || vtSuspicious > 0) {
            rows.add(sec("VIRUSTOTAL"))
            if (vtMalicious > 0) rows.add("⚠ Malicious Detections" to "$vtMalicious engines")
            if (vtSuspicious > 0) rows.add("⚠ Suspicious" to "$vtSuspicious engines")
            if (vtHarmless > 0) rows.add("Clean Detections" to "$vtHarmless engines")
            meta["vt_reputation"]?.let { rows.add("Reputation Score" to it) }
            meta["vt_country"]?.takeIf { it.isNotBlank() }?.let { rows.add("Country" to it) }
            meta["vt_as_owner"]?.takeIf { it.isNotBlank() }?.let { rows.add("AS Owner" to it) }
        }
        val urlscanTotal = meta["urlscan_total_scans"]?.toIntOrNull() ?: 0
        val urlscanMal = meta["urlscan_malicious_scans"]?.toIntOrNull() ?: 0
        if (urlscanTotal > 0) {
            rows.add(sec("URLSCAN"))
            rows.add("Total Scans" to urlscanTotal.toString())
            if (urlscanMal > 0) rows.add("⚠ Malicious Scans" to urlscanMal.toString())
            meta["urlscan_ips"]?.takeIf { it.isNotBlank() }?.let { rows.add("IPs Observed" to it) }
        }
        meta["maltiverse_classification"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("MALTIVERSE"))
            rows.add("Classification" to it)
            meta["maltiverse_as_name"]?.takeIf { it.isNotBlank() }?.let { n -> rows.add("AS Name" to n) }
            meta["maltiverse_blacklists"]?.takeIf { it.isNotBlank() }?.let { b -> rows.add("⚠ Blacklists" to b) }
        }
        meta["urlhaus_status"]?.takeIf { it.isNotBlank() }?.let { status ->
            rows.add(sec("URLHAUS"))
            rows.add("Status" to status)
            meta["urlhaus_urls_count"]?.let { rows.add("URLs on Record" to it) }
        }
        if (rows.isEmpty()) rows.add("Status" to "No threat intel found for this IP")
        return rows
    }

    private fun buildIpDomain(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        rows.add(sec("WHOIS / REGISTRATION"))
        meta["rdap_registrar"]?.takeIf { it.isNotBlank() }?.let { rows.add("Registrar" to it) }
        meta["rdap_registered"]?.takeIf { it.isNotBlank() }?.let { rows.add("Registered" to it) }
        meta["rdap_expiry"]?.takeIf { it.isNotBlank() }?.let { rows.add("Expires" to it) }
        meta["rdap_nameservers"]?.takeIf { it.isNotBlank() }?.let { rows.add("Nameservers" to it) }
        meta["whois"]?.takeIf { it.isNotBlank() }?.let { rows.add("WHOIS" to it.take(500)) }
        rows.add(sec("DNS & CERTIFICATES"))
        meta["subdomains"]?.takeIf { it.isNotBlank() }?.let { rows.add("SSL Subdomains" to it) }
        meta["cert_count"]?.let { rows.add("SSL Certs Found" to it) }
        meta["dns"]?.takeIf { it.isNotBlank() }?.let { rows.add("DNS Records" to it.take(400)) }
        rows.add(sec("ARCHIVE"))
        meta["wayback_count"]?.let { c -> if ((c.toIntOrNull() ?: 0) > 0) rows.add("Wayback Snapshots" to c) }
        meta["wayback_first"]?.takeIf { it.isNotBlank() }?.let { rows.add("First Archived" to it) }
        meta["wayback_last"]?.takeIf { it.isNotBlank() }?.let { rows.add("Last Archived" to it) }
        meta["hackertarget_hostsearch"]?.takeIf { it.isNotBlank() }?.let {
            it.lines().filter { l -> l.isNotBlank() }.take(10).forEach { line -> rows.add("Host" to line) }
        }
        val domainsDbTotal = meta["domainsdb_total"]?.toIntOrNull() ?: 0
        meta["domainsdb_domains"]?.takeIf { it.isNotBlank() }?.let { domains ->
            rows.add(sec("RELATED DOMAINS (DomainsDB)"))
            if (domainsDbTotal > 0) rows.add("Total Found" to domainsDbTotal.toString())
            domains.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(10)
                .forEach { d -> rows.add("Domain" to d) }
        }
        if (rows.size <= 3) rows.add("Status" to "No domain registration data found")
        return rows
    }

    private fun buildUsernameFound(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        val checked = meta["sites_checked"]?.toIntOrNull() ?: 0
        val found = meta["sites_found"]?.toIntOrNull() ?: 0
        if (checked > 0) rows.add(sec("$found PROFILES FOUND ON $checked PLATFORMS"))
        val siteDesc = mapOf(
            "GitHub" to "Code hosting & developer collaboration",
            "Reddit" to "Social news aggregation & discussion",
            "Twitter/X" to "Microblogging & social network",
            "Instagram" to "Photo & video sharing",
            "TikTok" to "Short-form video sharing",
            "YouTube" to "Video sharing & streaming",
            "LinkedIn" to "Professional networking",
            "Pinterest" to "Visual discovery & idea sharing",
            "Twitch" to "Live game streaming platform",
            "Flickr" to "Photo sharing & community",
            "Tumblr" to "Blogging & creative content",
            "Medium" to "Online publishing & blogging",
            "DeviantArt" to "Digital art & creative community",
            "SoundCloud" to "Music sharing & audio streaming",
            "Spotify" to "Music & podcast streaming",
            "GitLab" to "DevOps code repository",
            "Keybase" to "Encrypted identity verification",
            "Replit" to "Browser-based coding environment",
            "HackerNews" to "Tech news & discussion (Y Combinator)",
            "ProductHunt" to "Product launch & discovery",
            "Gravatar" to "Globally recognized avatar service",
            "About.me" to "Personal profile page",
            "Wattpad" to "Story sharing & reading community",
            "Patreon" to "Creator subscription monetization",
            "Venmo" to "Peer-to-peer payment app",
            "Etsy" to "Handmade & vintage marketplace",
            "Behance" to "Creative portfolio (Adobe)",
            "Dribbble" to "Designer portfolio & community",
            "Last.fm" to "Music tracking & social recommendation",
            "Lichess" to "Free open-source chess platform",
            "Chess.com" to "Online chess platform",
            "Codecademy" to "Interactive coding education",
            "Duolingo" to "Language learning platform",
            "NameMC" to "Minecraft username tracker",
            "VSCO" to "Photography & creative community",
            "Snapchat" to "Disappearing photo/video messaging",
            "Xbox Gamertag" to "Xbox gaming profile",
            "PSN Profiles" to "PlayStation Network gaming profile",
            "Cashapp" to "Cash App payment profile",
            "VK" to "Russian social network (VKontakte)",
            "Telegram" to "Encrypted messaging & channels",
            "Mastodon" to "Federated open-source social network",
            "Bluesky" to "Decentralized social network (AT Protocol)",
            "Threads" to "Instagram's text-based social network",
            "Substack" to "Newsletter & subscription publishing",
            "Ko-fi" to "Creator tip jar & supporter platform",
            "Linktree" to "Link aggregator profile page",
            "Letterboxd" to "Film diary & social movie tracking",
            "ArtStation" to "Professional game & film art portfolio",
            "Unsplash" to "Free stock photography platform",
            "Mixcloud" to "DJ mix & podcast streaming",
            "Audiomack" to "Free music streaming & discovery",
            "Bandcamp" to "Music publishing & direct fan support",
            "ReverbNation" to "Musician marketing & promotion",
            "Steemit" to "Blockchain-based social blogging",
            "Odysee" to "Decentralized video platform (LBRY)",
            "Rumble" to "Alternative video hosting platform",
            "Minds" to "Open-source decentralized social network",
            "Kaggle" to "Data science & ML competition platform",
            "Codeforces" to "Competitive programming platform",
            "LeetCode" to "Coding interview prep platform",
            "CodePen" to "Front-end code playground",
            "Angel.co" to "Startup jobs & investor network",
            "GoodReads" to "Book tracking & reading community",
            "OkCupid" to "Dating app & matchmaking service",
            "Xing" to "European professional networking",
            "Exercism" to "Programming practice & mentorship",
            "OnlyFans" to "⚠ Adult content subscription platform",
            "Pornhub" to "⚠ Adult video streaming site",
            "Chaturbate" to "⚠ Adult live cam broadcasting",
            "ManyVids" to "⚠ Adult content creator marketplace",
            "Fansly" to "⚠ Adult content subscription platform",
            "RedGIFs" to "⚠ Adult GIF & video sharing",
            "XVIDEOS" to "⚠ Adult video streaming site",
            "BDSMLR" to "⚠ Adult BDSM-focused social blogging",
            "Stripchat" to "⚠ Adult live cam platform",
            "MyFreeCams" to "⚠ Adult webcam model platform",
            "CamSoda" to "⚠ Adult cam broadcasting platform",
            "Tinder" to "Dating app",
            "Bumble" to "Dating & networking app",
            "Ashley Madison" to "⚠ Extramarital affairs dating platform",
            "Seeking" to "⚠ Sugar dating platform",
            "FurAffinity" to "Furry art & community platform"
        )
        meta["found_urls"]?.takeIf { it.isNotBlank() }?.let {
            it.lines().filter { l -> l.isNotBlank() }.forEach { line ->
                val isNsfw = line.startsWith("⚠NSFW:")
                val cleanLine = if (isNsfw) line.removePrefix("⚠NSFW:") else line
                val parts = cleanLine.split(": ", limit = 2)
                val siteName = parts.firstOrNull() ?: "Platform"
                val url = parts.getOrNull(1) ?: cleanLine
                val desc = siteDesc[siteName]?.removePrefix("⚠ ")
                val label = buildString {
                    append(if (isNsfw) "⚠ NSFW / $siteName" else "✓ $siteName")
                    if (!desc.isNullOrBlank()) append(" · $desc")
                }
                rows.add(label to url)
            }
        }
        if (rows.isEmpty()) rows.add("Status" to "No profiles found on tracked platforms")
        return rows
    }

    private fun buildUsernameProfiles(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        val hasGitHub = !meta["github_url"].isNullOrBlank() || !meta["github_name"].isNullOrBlank()
        if (hasGitHub) {
            rows.add(sec("GITHUB"))
            meta["github_name"]?.takeIf { it.isNotBlank() }?.let { rows.add("Name" to it) }
            meta["github_company"]?.takeIf { it.isNotBlank() }?.let { rows.add("Company" to it) }
            meta["github_location"]?.takeIf { it.isNotBlank() }?.let { rows.add("Location" to it) }
            meta["github_email"]?.takeIf { it.isNotBlank() }?.let { rows.add("Email" to it) }
            meta["github_stats"]?.takeIf { it.isNotBlank() }?.let { rows.add("Stats" to it) }
            meta["github_url"]?.takeIf { it.isNotBlank() }?.let { rows.add("Profile" to it) }
        }
        val hasKeybase = !meta["keybase_name"].isNullOrBlank()
        if (hasKeybase) {
            rows.add(sec("KEYBASE"))
            meta["keybase_name"]?.let { rows.add("Name" to it) }
            meta["keybase_location"]?.let { rows.add("Location" to it) }
            meta["keybase_bio"]?.let { rows.add("Bio" to it) }
            meta["keybase_proofs"]?.let { rows.add("Proofs" to it) }
        }
        meta["hackernews_karma"]?.let { k ->
            if ((k.toIntOrNull() ?: 0) > 0) {
                rows.add(sec("HACKER NEWS"))
                rows.add("Karma" to k)
                meta["hackernews_about"]?.let { rows.add("About" to it) }
            }
        }
        meta["devto_name"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("DEV.TO"))
            rows.add("Name" to it)
            meta["devto_location"]?.let { l -> rows.add("Location" to l) }
            meta["devto_summary"]?.let { s -> rows.add("Bio" to s) }
            meta["devto_joined"]?.let { j -> rows.add("Joined" to j) }
        }
        meta["sherlock_found"]?.takeIf { it.isNotBlank() }?.let { found ->
            rows.add(sec("SHERLOCK"))
            found.lines().filter { it.isNotBlank() }.take(20).forEach { line ->
                val parts = line.split(": ", limit = 2)
                rows.add((parts.firstOrNull() ?: "Profile") to (parts.getOrNull(1) ?: line))
            }
        }
        meta["maigret_found"]?.takeIf { it.isNotBlank() }?.let { found ->
            rows.add(sec("MAIGRET"))
            found.lines().filter { it.isNotBlank() }.take(20).forEach { line ->
                val parts = line.split(": ", limit = 2)
                rows.add((parts.firstOrNull() ?: "Profile") to (parts.getOrNull(1) ?: line))
            }
        }
        parseSocialProfilesFromMeta(meta)
            .filter { profile ->
                when (profile.platform) {
                    "GitHub" -> !hasGitHub
                    "Keybase" -> !hasKeybase
                    "HackerNews" -> meta["hackernews_karma"].isNullOrBlank()
                    "Dev.to" -> meta["devto_name"].isNullOrBlank()
                    else -> true
                }
            }
            .forEach { profile ->
                rows.add(sec(profile.platform.uppercase()))
                rows.add("Profile" to (profile.url ?: profile.username))
                profile.statsLabel?.takeIf { it.isNotBlank() }?.let { rows.add("Stats" to it) }
            }
        if (rows.isEmpty()) rows.add("Status" to "No profile data extracted from found accounts")
        return rows
    }

    private fun parseSocialProfilesFromMeta(meta: Map<String, String>): List<com.twoskoops707.sixdegrees.domain.model.SocialProfile> {
        val json = meta["social_profiles_json"]?.takeIf { it.isNotBlank() } ?: return emptyList()
        return try {
            val type = Types.newParameterizedType(
                List::class.java,
                com.twoskoops707.sixdegrees.domain.model.SocialProfile::class.java
            )
            moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.SocialProfile>>(type)
                .fromJson(json) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildPhoneValidation(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        rows.add(sec("NUMBER VALIDATION"))
        meta["numverify_valid"]?.let { rows.add("Valid" to if (it == "true") "Yes ✓" else "No ✗") }
        meta["numverify_country"]?.takeIf { it.isNotBlank() }?.let { rows.add("Country" to it) }
        meta["numverify_carrier"]?.takeIf { it.isNotBlank() }?.let { rows.add("Carrier" to it) }
        meta["numverify_line_type"]?.takeIf { it.isNotBlank() }?.let { rows.add("Line Type" to it) }
        meta["numverify_location"]?.takeIf { it.isNotBlank() }?.let { rows.add("Location" to it) }
        meta["numverify_intl"]?.takeIf { it.isNotBlank() }?.let { rows.add("Intl Format" to it) }
        meta["libphone_valid"]?.let { rows.add("Valid (libphonenumber)" to if (it == "true") "Yes ✓" else "No ✗") }
        meta["libphone_country"]?.takeIf { it.isNotBlank() }?.let { rows.add("Country (libphonenumber)" to it) }
        meta["libphone_carrier"]?.takeIf { it.isNotBlank() }?.let { rows.add("Carrier (libphonenumber)" to it) }
        meta["libphone_line_type"]?.takeIf { it.isNotBlank() }?.let { rows.add("Line Type (libphonenumber)" to it) }
        meta["libphone_location"]?.takeIf { it.isNotBlank() }?.let { rows.add("Location (libphonenumber)" to it) }
        meta["libphone_timezone"]?.takeIf { it.isNotBlank() }?.let { rows.add("Timezone (libphonenumber)" to it) }
        meta["calltracer_location"]?.takeIf { it.isNotBlank() }?.let { rows.add("Location (CallTracer)" to it) }
        meta["calltracer_line_type"]?.takeIf { it.isNotBlank() }?.let { rows.add("Line Type (CallTracer)" to it) }
        meta["calltracer_spam_score"]?.takeIf { it.isNotBlank() }?.let { rows.add("Spam Score (CallTracer)" to it) }
        meta["calltracer_spam_reports"]?.takeIf { it.isNotBlank() }?.let { rows.add("Spam Reports (CallTracer)" to it) }
        meta["veriphone_carrier"]?.takeIf { it.isNotBlank() }?.let { rows.add("Carrier (Veriphone)" to it) }
        meta["veriphone_line_type"]?.takeIf { it.isNotBlank() }?.let { rows.add("Line Type (Veriphone)" to it) }
        meta["veriphone_country"]?.takeIf { it.isNotBlank() }?.let { rows.add("Country (Veriphone)" to it) }
        meta["opencnam_name"]?.takeIf { it.isNotBlank() }?.let { rows.add("Caller Name (OpenCNAM)" to it) }
        meta["phone_owner_name"]?.takeIf { it.isNotBlank() }?.let { rows.add("Owner Found" to it) }
        meta["phone_owner_address"]?.takeIf { it.isNotBlank() }?.let { addr ->
            addr.split(" | ").filter { it.isNotBlank() }.forEach { rows.add("Owner Address" to it) }
        }
        meta["phone_web_snippets"]?.split("\n---\n")?.filter { it.isNotBlank() }?.take(6)?.forEach {
            rows.add("Web Result" to it.trim())
        }
        if (rows.size <= 1) rows.add("Status" to "No validation data available for this number")
        return rows
    }

    private fun buildPhoneRisk(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        val ipqsFraud = meta["ipqs_phone_fraud_score"]?.toIntOrNull() ?: -1
        if (ipqsFraud >= 0) {
            rows.add(sec("IPQS FRAUD SCORING"))
            rows.add("Fraud Score" to "$ipqsFraud / 100${if (ipqsFraud > 70) " ⚠ HIGH RISK" else if (ipqsFraud > 40) " ⚠ Moderate" else " — Low"}")
            meta["ipqs_phone_risky"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Risky" to "Phone flagged as risky") }
            meta["ipqs_phone_spam"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("⚠ Spammer" to "Associated with spam/scam activity") }
            meta["ipqs_phone_voip"]?.toBooleanStrictOrNull()?.let { if (it) rows.add("VoIP" to "Voice over IP number") }
            meta["ipqs_phone_line_type"]?.takeIf { it.isNotBlank() }?.let { rows.add("Line Type" to it) }
            meta["ipqs_phone_carrier"]?.takeIf { it.isNotBlank() }?.let { rows.add("Carrier" to it) }
        } else {
            rows.add("Status" to "No risk data available for this number")
        }
        return rows
    }

    private fun buildCompanyRecords(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        meta["companies"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("OPENCORPORATES"))
            it.lines().filter { l -> l.isNotBlank() }.forEach { c -> rows.add("Entity" to c) }
        }
        val sunbizCount = meta["sunbiz_count"]?.toIntOrNull() ?: 0
        if (sunbizCount > 0) {
            rows.add(sec("FLORIDA SOS — $sunbizCount FILING${if (sunbizCount != 1) "S" else ""}"))
            meta["sunbiz_entities"]?.takeIf { it.isNotBlank() }?.let {
                it.lines().filter { l -> l.isNotBlank() }.take(5).forEach { e -> rows.add("FL Entity" to e) }
            }
        }
        val caSosCount = meta["ca_sos_count"]?.toIntOrNull() ?: 0
        if (caSosCount > 0) {
            rows.add(sec("CALIFORNIA SOS — $caSosCount FILING${if (caSosCount != 1) "S" else ""}"))
            meta["ca_sos_entities"]?.takeIf { it.isNotBlank() }?.let {
                it.lines().filter { l -> l.isNotBlank() }.take(10).forEach { e -> rows.add("CA Entity" to e) }
            }
        }
        val samCount = meta["sam_entity_count"]?.toIntOrNull() ?: 0
        if (samCount > 0) {
            rows.add(sec("SAM.GOV FEDERAL — $samCount ENTIT${if (samCount != 1) "IES" else "Y"}"))
            meta["sam_entities"]?.takeIf { it.isNotBlank() }?.let {
                it.lines().filter { l -> l.isNotBlank() }.take(5).forEach { e -> rows.add("Federal Entity" to e) }
            }
            meta["sam_uei_codes"]?.let { rows.add("UEI Code(s)" to it) }
            meta["sam_cage_codes"]?.let { rows.add("CAGE Code(s)" to it) }
        }
        if (rows.isEmpty()) rows.add("Status" to "No corporate records found for this entity")
        return rows
    }

    private fun buildCompanyOfficers(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        val officerCount = meta["officer_count"]?.toIntOrNull() ?: 0
        if (officerCount > 0) {
            rows.add(sec("OFFICERS & EXECUTIVES ($officerCount)"))
            meta["officers"]?.takeIf { it.isNotBlank() }?.let {
                it.lines().filter { l -> l.isNotBlank() }.forEach { o -> rows.add("Officer" to o) }
            }
        }
        meta["corpwiki_companies"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("CORPORATIONS WIKI"))
            it.lines().filter { l -> l.isNotBlank() }.take(8).forEach { co -> rows.add("Corp Wiki" to co) }
            meta["corpwiki_officers"]?.let { o -> rows.add("Officers" to o) }
            meta["corpwiki_states"]?.let { s -> rows.add("Incorporated States" to s) }
        }
        val emailCount = meta["hunter_emails_count"]?.toIntOrNull() ?: 0
        if (emailCount > 0) {
            rows.add(sec("EMAIL DISCOVERY ($emailCount)"))
            meta["hunter_emails"]?.let { rows.add("Email Addresses" to it) }
        }
        if (rows.isEmpty()) rows.add("Status" to "No officer data found for this entity")
        return rows
    }

    private fun buildCompanyFilings(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        val secFilings = meta["sec_filings_count"]?.toIntOrNull()
            ?: meta["sec_fulltext_hits"]?.toIntOrNull() ?: 0
        if (secFilings > 0) {
            rows.add(sec("SEC EDGAR — $secFilings FILING${if (secFilings != 1) "S" else ""}"))
            meta["sec_filing_types"]?.let { rows.add("Filing Types" to it) }
                ?: meta["sec_fulltext_forms"]?.let { rows.add("Filing Types" to it) }
            meta["sec_affiliations"]?.let { rows.add("Entities" to it) }
                ?: meta["sec_fulltext_entities"]?.let { rows.add("Entities" to it) }
        }
        meta["gleif_company_entities"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("GLEIF GLOBAL ENTITIES"))
            it.lines().filter { l -> l.isNotBlank() }.take(5).forEach { e -> rows.add("Global Entity" to e) }
        }
        meta["wikidata_company_descriptions"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("WIKIDATA"))
            rows.add("Entity" to it)
        }
        if (rows.isEmpty()) rows.add("Status" to "No filing data found for this entity")
        return rows
    }

    private fun buildImageFace(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        rows.add(sec("FACE RECOGNITION ENGINES"))
        rows.add("Instructions" to "Open each link in browser → upload photo → get matches")
        meta["face_facecheck_id_link"]?.let { rows.add("⟶ FaceCheck.id" to it) }
        meta["face_pimeyes_link"]?.let { rows.add("⟶ PimEyes" to it) }
        meta["face_search4faces_link"]?.let { rows.add("⟶ Search4Faces" to it) }
        meta["face_lenso_ai_link"]?.let { rows.add("⟶ Lenso.ai" to it) }
        return rows
    }

    private fun buildImageReverse(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        rows.add(sec("REVERSE IMAGE SEARCH"))
        rows.add("Instructions" to "Upload image to find where it appears across the web")
        meta["rev_google_lens_link"]?.let { rows.add("⟶ Google Lens" to it) }
        meta["rev_yandex_images_link"]?.let { rows.add("⟶ Yandex Images (best for faces)" to it) }
        meta["rev_tineye_link"]?.let { rows.add("⟶ TinEye" to it) }
        meta["rev_bing_visual_search_link"]?.let { rows.add("⟶ Bing Visual" to it) }
        meta["rev_karmadecay_link"]?.let { rows.add("⟶ KarmaDecay (Reddit)" to it) }
        return rows
    }

    private fun extractPhones(meta: Map<String, String>): LinkedHashSet<String> {
        val tollfree = setOf("800", "888", "877", "866", "855", "844", "833", "822")
        val areaCodeRegex = Regex("^\\((\\d{3})\\)")
        val set = linkedSetOf<String>()
        meta["pipl_phone"]?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        listOf("person_phone", "pipl_phone", "pipl_phones", "pdl_phones").forEach { k ->
            meta[k]?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        }
        listOf("search_phones", "tps_phones", "zaba_phones", "411_phones", "tt_phones", "uspb_phones", "fps_phones", "radaris_phones", "nuwber_phones", "wp_phones", "checkpeople_phones",
               "ddg_person_phones", "ddg_social_phones", "ddg_phones", "cse_phones", "dork_phones", "dork_corroborated_phones", "dork_follow_phones")
            .forEach { key ->
                meta[key]?.split(",")?.map { it.trim() }?.filter { phone ->
                    phone.isNotBlank() && areaCodeRegex.find(phone) != null && areaCodeRegex.find(phone)!!.groupValues[1] !in tollfree
                }?.forEach { set.add(it) }
            }
        return set
    }

    private fun extractAddresses(meta: Map<String, String>): LinkedHashSet<String> =
        LinkedHashSet(FindingUrlHelper.extractFullAddresses(meta))

    private fun extractRelatives(meta: Map<String, String>): LinkedHashSet<String> {
        val set = linkedSetOf<String>()
        listOf("search_relatives", "pipl_relatives", "pdl_associates", "tps_relatives", "ftn_relatives", "411_relatives", "zaba_relatives", "tt_relatives", "fps_relatives",
            "corpwiki_associates", "radaris_relatives", "nuwber_relatives", "wp_relatives", "checkpeople_relatives")
            .forEach { key -> meta[key]?.split(",")?.map { it.trim() }?.filter { it.length > 3 && it.isNotBlank() }?.forEach { set.add(it) } }
        val namePattern = Regex("[A-Z][a-z]{1,20} [A-Z][a-z]{1,20}(?:\\s[A-Z][a-z]{1,20})?")
        listOf("dork_relatives_results", "dork_obituary_results", "dork_identity_results", "dork_address_results",
            "dork_voter_results", "dork_address_full_results").forEach { key ->
            meta[key]?.let { text ->
                namePattern.findAll(text).map { it.value.trim() }.filter { it.length in 5..40 }.take(15).forEach { set.add(it) }
            }
        }
        return set
    }

    private fun showPartialReportBanner(meta: Map<String, String>) {
        val container = binding.dossierAlertsContainer
        val existing = container.findViewWithTag<View>("partial_report_banner")
        if (meta["report_status"] != "partial") {
            existing?.let { container.removeView(it) }
            return
        }
        if (existing != null) return
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()
        val card = MaterialCardView(ctx).apply {
            tag = "partial_report_banner"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(12f) }
            radius = dp(10f).toFloat()
            strokeWidth = dp(1f)
            strokeColor = ContextCompat.getColor(ctx, R.color.score_yellow)
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.warning_dim))
        }
        card.addView(TextView(ctx).apply {
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            text = "Partial report — search may still be running. Re-open from progress when complete for full AI brief and sources."
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, R.color.score_yellow))
            setTextIsSelectable(true)
        })
        container.addView(card, 0)
    }

    private fun applyResultsModeUi(investigatorMode: Boolean) {
        binding.btnWebHub.visibility = if (investigatorMode) View.VISIBLE else View.GONE
        binding.btnExport.visibility = if (investigatorMode) View.VISIBLE else View.GONE
        binding.tvSourcesCount.visibility = if (investigatorMode) View.VISIBLE else View.GONE
    }

    private fun applyShadyScore(shady: ShadyScore?, meta: Map<String, String>, type: String, investigatorMode: Boolean) {
        val score = shady ?: DossierBuilder.computeShadyScore(meta, type)
        val color = when {
            score.score == 0 -> ContextCompat.getColor(requireContext(), R.color.score_green)
            score.score < 30 -> ContextCompat.getColor(requireContext(), R.color.score_green)
            score.score < 60 -> ContextCompat.getColor(requireContext(), R.color.score_yellow)
            else -> ContextCompat.getColor(requireContext(), R.color.score_red)
        }
        binding.tvScoreNumber.text = score.displayValue
        binding.tvScoreNumber.setTextColor(color)
        if (investigatorMode) {
            binding.tvScoreVerdict.text = "[ ${score.verdict} ]"
            binding.tvScoreDetail.text = score.detail
        } else {
            binding.tvScoreVerdict.text = when {
                score.score == 0 -> "Looks okay"
                score.score < 30 -> "Minor concerns"
                score.score < 60 -> "Proceed carefully"
                else -> "Serious concerns"
            }
            binding.tvScoreDetail.text = if (score.score > 0) score.detail else getString(R.string.results_risk_disclaimer)
        }
        binding.tvScoreVerdict.setTextColor(color)
        binding.scoreAccentBar.setBackgroundColor(color)
    }

    private fun computeAndShowShadyScore(meta: Map<String, String>, type: String) {
        val investigatorMode = AppSettings.isInvestigatorMode(requireContext())
        applyShadyScore(DossierBuilder.computeShadyScore(meta, type), meta, type, investigatorMode)
    }

    private fun shareReport(query: String, type: String, meta: Map<String, String>) {
        val sb = StringBuilder()
        sb.appendLine("=== SixDegrees Intelligence Report ===")
        sb.appendLine("Query: $query")
        sb.appendLine("Type: $type")
        sb.appendLine()
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
        return parseAllAddresses(json).firstOrNull().orEmpty()
    }

    private fun parseCurrentJob(json: String): String {
        return try {
            val type = Types.newParameterizedType(List::class.java, com.twoskoops707.sixdegrees.domain.model.Employment::class.java)
            val jobs = moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.Employment>>(type).fromJson(json) ?: emptyList()
            jobs.firstOrNull { it.isCurrent }?.let { "${it.jobTitle} at ${it.companyName}" }
                ?: jobs.firstOrNull()?.let { "${it.jobTitle} at ${it.companyName}" }
                ?: ""
        } catch (_: Exception) { "" }
    }

    private fun parseAllJobs(json: String): List<String> {
        return try {
            val type = Types.newParameterizedType(List::class.java, com.twoskoops707.sixdegrees.domain.model.Employment::class.java)
            moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.Employment>>(type).fromJson(json)
                ?.filter { it.companyName.isNotBlank() || it.jobTitle.isNotBlank() }
                ?.map { e ->
                    buildString {
                        if (e.jobTitle.isNotBlank()) append(e.jobTitle)
                        if (e.companyName.isNotBlank()) { if (isNotEmpty()) append(" at "); else append("Employee at "); append(e.companyName) }
                        if (e.isCurrent) append(" (Current)")
                        else if (e.endDate != null) append(" (until ${e.endDate})")
                    }
                } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun parseAllAddresses(json: String): List<String> {
        return try {
            val type = Types.newParameterizedType(List::class.java, com.twoskoops707.sixdegrees.domain.model.Address::class.java)
            moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.Address>>(type).fromJson(json)
                ?.filter { it.city.isNotBlank() || it.state.isNotBlank() }
                ?.map { a ->
                    listOfNotNull(
                        a.street.takeIf { it.isNotBlank() },
                        a.city.takeIf { it.isNotBlank() },
                        a.state.takeIf { it.isNotBlank() },
                        a.postalCode.takeIf { it.isNotBlank() }
                    ).joinToString(", ")
                }?.filter { it.isNotBlank() } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun parseAllSocials(json: String): List<String> {
        return try {
            val type = Types.newParameterizedType(List::class.java, com.twoskoops707.sixdegrees.domain.model.SocialProfile::class.java)
            moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.SocialProfile>>(type).fromJson(json)
                ?.filter { !it.url.isNullOrBlank() }
                ?.map { s -> "${s.platform}: ${s.url}" }
                ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun buildCandidateDisambiguation(meta: Map<String, String>) {
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()
        val tv = TypedValue()
        ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
        val colorPrimary = tv.data
        val container = binding.dossierAlertsContainer
        container.removeAllViews()

        fun makeCard(): Pair<MaterialCardView, LinearLayout> {
            val card = MaterialCardView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(12f) }
                radius = dp(12f).toFloat()
                strokeWidth = dp(1f)
                strokeColor = colorPrimary
                cardElevation = 0f
                setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface))
            }
            val layout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14f), dp(12f), dp(14f), dp(4f))
            }
            return card to layout
        }

        fun addDivider(layout: LinearLayout) {
            layout.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).also { it.topMargin = dp(4f) }
                setBackgroundColor(ContextCompat.getColor(ctx, R.color.border))
                alpha = 0.5f
            })
        }

        val raw = meta["tps_candidates"]?.takeIf { it.isNotBlank() }
        val candidates = raw?.lines()?.filter { it.isNotBlank() } ?: emptyList()

        if (candidates.size >= 2) {
            val (headerCard, headerLayout) = makeCard()
            headerLayout.addView(TextView(ctx).apply {
                text = "MULTIPLE SUBJECTS FOUND — SELECT ONE"
                textSize = 10f; letterSpacing = 0.1f
                setTypeface(typeface, Typeface.BOLD); setTextColor(colorPrimary); setTextIsSelectable(true)
            })
            headerLayout.addView(TextView(ctx).apply {
                text = "${candidates.size} people found matching this name. Tap one to deep-search that person."
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(4f); it.bottomMargin = dp(8f) }
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary)); setTextIsSelectable(true)
            })
            candidates.forEach { line ->
                val parts = line.split("|")
                val cName = parts.getOrNull(0)?.trim() ?: return@forEach
                val cAge = parts.getOrNull(1)?.trim() ?: ""
                val cLoc = parts.getOrNull(2)?.trim() ?: ""
                val cPhone = parts.getOrNull(3)?.trim() ?: ""
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(0, dp(8f), 0, dp(8f))
                    isClickable = true; isFocusable = true
                    setBackgroundResource(android.R.attr.selectableItemBackground.let { a -> val o = TypedValue(); ctx.theme.resolveAttribute(a, o, true); o.resourceId })
                    setOnClickListener {
                        val stateCode = if (cLoc.contains(",")) cLoc.substringAfter(",").trim() else ""
                        val ageNum = cAge.toIntOrNull()
                        val q = buildString {
                            append("name=$cName")
                            if (stateCode.isNotBlank()) append("|state=$stateCode")
                            if (cPhone.isNotBlank()) append("|phone=$cPhone")
                            if (ageNum != null) append("|dob~age$ageNum")
                        }
                        findNavController().navigate(R.id.action_results_to_progress, Bundle().apply { putString("query", q); putString("type", "comprehensive") })
                    }
                }
                val nameRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                nameRow.addView(TextView(ctx).apply {
                    text = cName; textSize = 14f; setTypeface(typeface, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); setTextIsSelectable(true)
                })
                if (cAge.isNotBlank()) nameRow.addView(TextView(ctx).apply { text = "Age $cAge"; textSize = 12f; setTextColor(colorPrimary); setTextIsSelectable(true) })
                row.addView(nameRow)
                val detailParts = listOfNotNull(cLoc.takeIf { it.isNotBlank() }, cPhone.takeIf { it.isNotBlank() }?.let { "☎ $it" })
                if (detailParts.isNotEmpty()) row.addView(TextView(ctx).apply {
                    text = detailParts.joinToString("   "); textSize = 12f
                    setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(2f) }; setTextIsSelectable(true)
                })
                headerLayout.addView(row); addDivider(headerLayout)
            }
            headerCard.addView(headerLayout); container.addView(headerCard, 0)
        }

        val cseProfiles = meta["cse_profile_links"]?.takeIf { it.isNotBlank() }?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        if (cseProfiles.size >= 2) {
            val (profCard, profLayout) = makeCard()
            profLayout.addView(TextView(ctx).apply {
                text = "MATCHING PROFILES FOUND — TAP TO VIEW"
                textSize = 10f; letterSpacing = 0.1f
                setTypeface(typeface, Typeface.BOLD); setTextColor(colorPrimary); setTextIsSelectable(true)
            })
            profLayout.addView(TextView(ctx).apply {
                text = "${cseProfiles.size} profiles indexed by Google. Tap to open in browser — view full contact info directly on the site."
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(4f); it.bottomMargin = dp(8f) }
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary)); setTextIsSelectable(true)
            })
            cseProfiles.forEach { line ->
                val parts = line.split(" → ", limit = 2)
                val site = parts.firstOrNull()?.trim() ?: "Profile"
                val url = parts.getOrNull(1)?.trim() ?: line.trim()
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10f), 0, dp(10f))
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    isClickable = true; isFocusable = true
                    setBackgroundResource(android.R.attr.selectableItemBackground.let { a -> val o = TypedValue(); ctx.theme.resolveAttribute(a, o, true); o.resourceId })
                    setOnClickListener {
                        val prefs = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        val pkg = when (prefs.getString("pref_browser", "firefox")) {
                            "ddg" -> "com.duckduckgo.mobile.android"; "chrome" -> "com.android.chrome"; "default" -> null; else -> "org.mozilla.firefox"
                        }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        if (pkg != null) intent.setPackage(pkg)
                        try { startActivity(intent) } catch (_: Exception) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    }
                }
                row.addView(TextView(ctx).apply {
                    text = site; textSize = 13f; setTypeface(typeface, Typeface.BOLD)
                    setTextColor(colorPrimary)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); setTextIsSelectable(true)
                })
                row.addView(TextView(ctx).apply { text = "›"; textSize = 20f; setTextColor(colorPrimary) })
                profLayout.addView(row); addDivider(profLayout)
            }
            profCard.addView(profLayout)
            if (candidates.size >= 2) container.addView(profCard, 1) else container.addView(profCard, 0)
        }
    }

    private fun setupDossierTabs(
        sections: List<DossierSection>,
        investigatorMode: Boolean,
        meta: Map<String, String> = emptyMap()
    ) {
        if (sections.isEmpty()) return

        tabMediator?.detach()
        val adapter = DossierSectionAdapter(this, sections, showTechnicalDetails = investigatorMode, meta = meta)
        binding.dossierPager.adapter = adapter
        binding.dossierPager.offscreenPageLimit = 2

        tabMediator = TabLayoutMediator(binding.dossierTabs, binding.dossierPager) { tab, position ->
            val section = sections[position]
            tab.text = section.title
            val count = section.findings.count { finding ->
                !finding.value.lowercase().startsWith("no ") || !finding.value.lowercase().contains("found")
            }
            tab.contentDescription = "${section.title}, $count findings"
        }.also { it.attach() }
    }

    private fun populateSectionContent(container: LinearLayout, rows: List<Pair<String, String>>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        val subSections = groupIntoSections(rows)
        for ((subTitle, subRows) in subSections) {
            container.addView(buildSectionCard(requireContext(), inflater, subTitle, subRows))
        }
        if (subSections.isEmpty()) {
            container.addView(buildSectionCard(requireContext(), inflater, "", listOf("Status" to "No data available")))
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
        InvestigationPipelineView.bind(binding.resultsContent, InvestigationStep.DOSSIER)
    }

    private fun showEmptyState(message: String? = null) {
        binding.loadingIndicator.visibility = View.GONE
        binding.resultsContent.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
        binding.tvEmptyMessage.text = message
            ?: getString(R.string.results_empty_message)
    }

    override fun onDestroyView() {
        tabMediator?.detach()
        tabMediator = null
        displayedReportId = null
        super.onDestroyView()
        _binding = null
    }

    private fun groupIntoSections(rows: List<Pair<String, String>>): List<Pair<String, List<Pair<String, String>>>> {
        val sections = mutableListOf<Pair<String, MutableList<Pair<String, String>>>>()
        var currentTitle = ""
        var currentRows = mutableListOf<Pair<String, String>>()
        for ((label, value) in rows) {
            if (value.isEmpty()) {
                if (currentRows.isNotEmpty() || currentTitle.isNotEmpty()) {
                    sections.add(currentTitle to currentRows)
                    currentRows = mutableListOf()
                }
                currentTitle = label.trimStart()
                    .removePrefix("◈ ").removePrefix("> ").removePrefix("══ ")
                    .removeSuffix(" ══").trim()
            } else {
                currentRows.add(label to value)
            }
        }
        if (currentRows.isNotEmpty() || currentTitle.isNotEmpty()) {
            sections.add(currentTitle to currentRows)
        }
        return sections.map { it.first to it.second.toList() }
    }

    private fun getSectionAccentColor(ctx: Context, title: String): Int {
        val t = title.uppercase()
        return when {
            t.startsWith("⚠") || "CRIMINAL" in t || "LEGAL" in t || "ARREST" in t
                || "COURT" in t || "SANCTIONS" in t || "LEAKED" in t || "BREACH" in t
                || "DARK WEB" in t || "PASTE" in t || "HIBP" in t || "COMB" in t
                || "LEAKCHECK" in t || "IPQS" in t ->
                ContextCompat.getColor(ctx, R.color.score_red)
            "PHONE" in t || "CONTACT" in t || "ADDRESS" in t || "VOTER" in t
                || "EMAIL" in t || "HOLEHE" in t || "NUMBER VALID" in t ->
                ContextCompat.getColor(ctx, R.color.accent_cyan)
            "DIGITAL" in t || "SOCIAL" in t || "SHERLOCK" in t || "MAIGRET" in t
                || "GITHUB" in t || "KEYBASE" in t || "DEV.TO" in t || "PROFILE" in t
                || "HACKER NEWS" in t || "FOUND" in t ->
                ContextCompat.getColor(ctx, R.color.accent_green)
            "NEWS" in t || "INTEL" in t || "DORK" in t || "PROPERTY" in t
                || "FINANCIAL" in t || "VEHICLE" in t || "EDUCATION" in t
                || "ACADEMIC" in t || "HISTORICAL" in t || "LIBRARY" in t
                || "OBITUARY" in t || "AWARDS" in t || "SEARCH ENGINE" in t
                || "INVESTIGATIVE" in t || "PEOPLE-SEARCH" in t ->
                ContextCompat.getColor(ctx, R.color.accent_amber)
            else -> {
                val tv = TypedValue()
                ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
                tv.data
            }
        }
    }

    private fun buildSectionCard(ctx: Context, inflater: LayoutInflater, title: String, rows: List<Pair<String, String>>): View {
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()

        val isWarning = title.startsWith("⚠")
        val accentColor = getSectionAccentColor(ctx, title)
        val cardBg = if (isWarning) ContextCompat.getColor(ctx, R.color.error_dim) else ContextCompat.getColor(ctx, R.color.surface)
        val borderColor = if (isWarning) ContextCompat.getColor(ctx, R.color.score_red) else ContextCompat.getColor(ctx, R.color.border)

        val card = MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dp(8f)
                it.bottomMargin = dp(4f)
            }
            radius = dp(10f).toFloat()
            strokeWidth = dp(1f)
            strokeColor = borderColor
            cardElevation = 0f
            setCardBackgroundColor(cardBg)
        }

        val inner = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        if (title.isNotBlank()) {
            val r = Color.red(accentColor)
            val g = Color.green(accentColor)
            val b = Color.blue(accentColor)
            val header = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14f), dp(10f), dp(16f), dp(10f))
                setBackgroundColor(Color.argb(30, r, g, b))
            }
            header.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(3f), dp(16f)).also { it.marginEnd = dp(10f) }
                setBackgroundColor(accentColor)
            })
            val sectionIcon = when {
                "PHONE" in title.uppercase() || "NUMBER VALID" in title.uppercase() -> "☎ "
                "EMAIL" in title.uppercase() || "BREACH" in title.uppercase() || "HIBP" in title.uppercase() -> "✉ "
                "ADDRESS" in title.uppercase() || "VOTER" in title.uppercase() -> "⌂ "
                title.startsWith("⚠") || "CRIMINAL" in title.uppercase() || "ARREST" in title.uppercase() -> "⚠ "
                "SOCIAL" in title.uppercase() || "DIGITAL" in title.uppercase() || "PROFILE" in title.uppercase() -> "◎ "
                "NEWS" in title.uppercase() -> "◉ "
                "IDENTITY" in title.uppercase() || "SUBJECT" in title.uppercase() -> "◈ "
                else -> "▸ "
            }
            val cleanTitle = title.removePrefix("⚠ ").removePrefix("⚠").trim()
            header.addView(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = "$sectionIcon$cleanTitle"
                textSize = 10f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                isAllCaps = true
                letterSpacing = 0.12f
                setTextColor(accentColor)
                setTextIsSelectable(true)
            })
            val rowCountText = TextView(ctx).apply {
                text = "${rows.size}"
                textSize = 8f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setTextColor(Color.argb(160, r, g, b))
            }
            header.addView(rowCountText)
            inner.addView(header)
            inner.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f))
                setBackgroundColor(accentColor)
                alpha = 0.25f
            })
        }

        for ((label, value) in rows) {
            val rowBinding = ItemDataRowBinding.inflate(inflater, inner, false)
            bindDataRow(rowBinding, label, value, ctx)
            inner.addView(rowBinding.root)
        }

        card.addView(inner)
        return card
    }

    private fun bindDataRow(b: ItemDataRowBinding, label: String, value: String, ctx: Context) {
        val isPivot = value.startsWith("pivot://")
        val isLink = value.startsWith("http://") || value.startsWith("https://")
        val isWarning = label.startsWith("⚠")
        val isCredential = label == "Login" || label == "Password / Hash" || label == "Leaked Record"
        val isNsfwLink = isWarning && isLink
        val isPhone = !isPivot && !isLink && value.matches(Regex("\\+?1?[\\s.\\-]?\\(?\\d{3}\\)?[\\s.\\-]\\d{3}[\\s.\\-]\\d{4}.*"))
        val isEmail = !isPivot && !isLink && !isPhone && value.contains("@") && value.contains(".") && !value.contains(" ") && value.length < 100

        when {
            isNsfwLink -> {
                b.rowAccentStripe.visibility = View.VISIBLE
                b.rowAccentStripe.setBackgroundColor(ContextCompat.getColor(ctx, R.color.score_red))
                b.root.setBackgroundColor(ContextCompat.getColor(ctx, R.color.error_dim))
            }
            isWarning -> {
                b.rowAccentStripe.visibility = View.VISIBLE
                b.rowAccentStripe.setBackgroundColor(ContextCompat.getColor(ctx, R.color.score_red))
                b.root.setBackgroundColor(ContextCompat.getColor(ctx, R.color.error_dim))
            }
            isPivot || isPhone || isEmail -> {
                b.rowAccentStripe.visibility = View.VISIBLE
                b.rowAccentStripe.setBackgroundColor(ContextCompat.getColor(ctx, R.color.accent_cyan))
                b.root.setBackgroundColor(Color.TRANSPARENT)
            }
            isLink -> {
                b.rowAccentStripe.visibility = View.VISIBLE
                val tv = TypedValue()
                ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
                b.rowAccentStripe.setBackgroundColor(tv.data)
                b.root.setBackgroundColor(Color.TRANSPARENT)
            }
            isCredential -> {
                b.rowAccentStripe.visibility = View.GONE
                b.root.setBackgroundColor(ContextCompat.getColor(ctx, R.color.surface_elevated))
            }
            else -> {
                b.rowAccentStripe.visibility = View.GONE
                b.root.setBackgroundColor(Color.TRANSPARENT)
            }
        }

        b.tvRowLabel.text = label
        val displayValue = if (isPivot) value.removePrefix("pivot://").split("/", limit = 2).getOrNull(1) ?: "" else value
        b.tvRowValue.text = displayValue

        when {
            isCredential -> {
                b.tvRowValue.typeface = Typeface.MONOSPACE
                b.tvRowValue.textSize = 12f
                val tv = TypedValue()
                ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true)
                b.tvRowValue.setTextColor(tv.data)
            }
            isWarning && !isLink -> {
                b.tvRowValue.typeface = Typeface.MONOSPACE
                b.tvRowValue.textSize = 13f
                b.tvRowValue.setTextColor(ContextCompat.getColor(ctx, R.color.score_red))
            }
            isPhone -> {
                b.tvRowValue.typeface = Typeface.MONOSPACE
                b.tvRowValue.textSize = 14f
                b.tvRowValue.setTextColor(ContextCompat.getColor(ctx, R.color.accent_cyan))
            }
            isEmail -> {
                b.tvRowValue.typeface = Typeface.MONOSPACE
                b.tvRowValue.textSize = 13f
                b.tvRowValue.setTextColor(ContextCompat.getColor(ctx, R.color.accent_cyan))
            }
            isLink || isPivot -> {
                b.tvRowValue.typeface = Typeface.MONOSPACE
                b.tvRowValue.textSize = 12f
                b.tvRowValue.setTextColor(ContextCompat.getColor(ctx, R.color.accent_cyan))
            }
            else -> {
                b.tvRowValue.typeface = Typeface.MONOSPACE
                b.tvRowValue.textSize = 13f
                val tv = TypedValue()
                ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true)
                b.tvRowValue.setTextColor(tv.data)
            }
        }

        when {
            isPivot -> {
                val parts = value.removePrefix("pivot://").split("/", limit = 2)
                val pivotType = parts.getOrNull(0) ?: "person"
                val pivotQuery = parts.getOrNull(1) ?: ""
                b.root.setOnClickListener {
                    val bundle = Bundle().apply {
                        putString("query", pivotQuery)
                        putString("type", pivotType)
                    }
                    this@ResultsFragment.findNavController().navigate(R.id.action_results_to_progress, bundle)
                }
            }
            isLink -> {
                b.root.setOnClickListener {
                    val url = if (value.startsWith("http")) value else FindingUrlHelper.resolveUrl(
                        DossierFinding(label = label, value = value, source = "", confidence = DossierConfidence.LOW, isLink = true),
                        reportMeta
                    )
                    if (url == null) return@setOnClickListener
                    val prefs = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    val pkg = when (prefs.getString("pref_browser", "firefox")) {
                        "ddg" -> "com.duckduckgo.mobile.android"
                        "chrome" -> "com.android.chrome"
                        "default" -> null
                        else -> "org.mozilla.firefox"
                    }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    if (pkg != null) intent.setPackage(pkg)
                    try { startActivity(intent) }
                    catch (_: Exception) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
            }
            isPhone -> {
                val digits = value.replace(Regex("[^\\d+]"), "")
                b.root.setOnClickListener {
                    try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))) }
                    catch (_: Exception) {}
                }
            }
            isEmail -> {
                b.root.setOnClickListener {
                    try { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$value"))) }
                    catch (_: Exception) {}
                }
            }
            else -> {
                val verifyUrl = FindingUrlHelper.urlForRow(label, value, reportMeta)
                    ?: FindingUrlHelper.resolveUrl(
                        DossierFinding(label = label, value = value, source = "", confidence = DossierConfidence.LOW),
                        reportMeta
                    )
                if (verifyUrl != null) {
                    b.rowAccentStripe.visibility = View.VISIBLE
                    b.rowAccentStripe.setBackgroundColor(ContextCompat.getColor(ctx, R.color.accent_cyan))
                    b.tvRowValue.setTextColor(ContextCompat.getColor(ctx, R.color.accent_cyan))
                    b.root.setOnClickListener {
                        val prefs = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        val pkg = when (prefs.getString("pref_browser", "firefox")) {
                            "ddg" -> "com.duckduckgo.mobile.android"
                            "chrome" -> "com.android.chrome"
                            "default" -> null
                            else -> "org.mozilla.firefox"
                        }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(verifyUrl))
                        if (pkg != null) intent.setPackage(pkg)
                        try { startActivity(intent) }
                        catch (_: Exception) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(verifyUrl))) }
                    }
                } else {
                    b.root.setOnClickListener(null)
                }
            }
        }

        b.tvRowValue.setTextIsSelectable(true)
        b.tvRowLabel.setTextIsSelectable(true)
    }

    companion object {
        val PLATFORM_DESCRIPTIONS = mapOf(
            "GitHub" to "Code hosting & developer collaboration",
            "Reddit" to "Social news aggregation & discussion",
            "Twitter/X" to "Microblogging & social network",
            "Instagram" to "Photo & video sharing",
            "TikTok" to "Short-form video sharing",
            "YouTube" to "Video sharing & streaming",
            "LinkedIn" to "Professional networking",
            "Pinterest" to "Visual discovery & idea sharing",
            "Twitch" to "Live game streaming platform",
            "Flickr" to "Photo sharing & community",
            "Tumblr" to "Blogging & creative content",
            "Medium" to "Online publishing & blogging",
            "DeviantArt" to "Digital art & creative community",
            "SoundCloud" to "Music sharing & audio streaming",
            "Spotify" to "Music & podcast streaming",
            "GitLab" to "DevOps code repository",
            "Keybase" to "Encrypted identity verification",
            "Replit" to "Browser-based coding environment",
            "HackerNews" to "Tech news & discussion (Y Combinator)",
            "ProductHunt" to "Product launch & discovery",
            "Gravatar" to "Globally recognized avatar service",
            "About.me" to "Personal profile page",
            "Wattpad" to "Story sharing & reading community",
            "Patreon" to "Creator subscription monetization",
            "Venmo" to "Peer-to-peer payment app",
            "Etsy" to "Handmade & vintage marketplace",
            "Behance" to "Creative portfolio (Adobe)",
            "Dribbble" to "Designer portfolio & community",
            "Last.fm" to "Music tracking & social recommendation",
            "Lichess" to "Free open-source chess platform",
            "Chess.com" to "Online chess platform",
            "Codecademy" to "Interactive coding education",
            "Duolingo" to "Language learning platform",
            "NameMC" to "Minecraft username tracker",
            "VSCO" to "Photography & creative community",
            "Snapchat" to "Disappearing photo/video messaging",
            "Xbox Gamertag" to "Xbox gaming profile",
            "PSN Profiles" to "PlayStation Network gaming profile",
            "Cashapp" to "Cash App payment profile",
            "VK" to "Russian social network (VKontakte)",
            "Telegram" to "Encrypted messaging & channels",
            "Mastodon" to "Federated open-source social network",
            "Bluesky" to "Decentralized social network (AT Protocol)",
            "Threads" to "Instagram's text-based social network",
            "Substack" to "Newsletter & subscription publishing",
            "Ko-fi" to "Creator tip jar & supporter platform",
            "Linktree" to "Link aggregator profile page",
            "Letterboxd" to "Film diary & social movie tracking",
            "ArtStation" to "Professional game & film art portfolio",
            "Unsplash" to "Free stock photography platform",
            "Mixcloud" to "DJ mix & podcast streaming",
            "Audiomack" to "Free music streaming & discovery",
            "Bandcamp" to "Music publishing & direct fan support",
            "ReverbNation" to "Musician marketing & promotion",
            "Steemit" to "Blockchain-based social blogging",
            "Odysee" to "Decentralized video platform (LBRY)",
            "Rumble" to "Alternative video hosting platform",
            "Minds" to "Open-source decentralized social network",
            "Kaggle" to "Data science & ML competition platform",
            "Codeforces" to "Competitive programming platform",
            "LeetCode" to "Coding interview prep platform",
            "CodePen" to "Front-end code playground",
            "Angel.co" to "Startup jobs & investor network",
            "GoodReads" to "Book tracking & reading community",
            "OkCupid" to "Dating app & matchmaking service",
            "Xing" to "European professional networking",
            "Exercism" to "Programming practice & mentorship",
            "OnlyFans" to "⚠ Adult content subscription platform",
            "Pornhub" to "⚠ Adult video streaming site",
            "Chaturbate" to "⚠ Adult live cam broadcasting",
            "ManyVids" to "⚠ Adult content creator marketplace",
            "Fansly" to "⚠ Adult content subscription platform",
            "RedGIFs" to "⚠ Adult GIF & video sharing",
            "XVIDEOS" to "⚠ Adult video streaming site",
            "BDSMLR" to "⚠ Adult BDSM-focused social blogging",
            "Stripchat" to "⚠ Adult live cam platform",
            "MyFreeCams" to "⚠ Adult webcam model platform",
            "CamSoda" to "⚠ Adult cam broadcasting platform",
            "Tinder" to "Dating app",
            "Bumble" to "Dating & networking app",
            "Ashley Madison" to "⚠ Extramarital affairs dating platform",
            "Seeking" to "⚠ Sugar dating platform",
            "FurAffinity" to "Furry art & community platform"
        )
    }

}
