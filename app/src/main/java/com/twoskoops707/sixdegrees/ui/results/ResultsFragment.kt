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
import com.google.android.material.tabs.TabLayoutMediator
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.databinding.FragmentResultsBinding
import com.twoskoops707.sixdegrees.databinding.ItemDataRowBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResultsViewModel by viewModels()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

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

        val meta = try {
            val type = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
            moshi.adapter<Map<String, String>>(type).fromJson(report.companiesJson) ?: emptyMap()
        } catch (_: Exception) { emptyMap() }

        val searchType = arguments?.getString("searchType") ?: "person"

        computeAndShowShadyScore(meta, searchType)

        val profileImageUrl = person?.profileImageUrl ?: meta["tt_image_url"]
        if (!profileImageUrl.isNullOrBlank()) {
            binding.profileImage.load(profileImageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_person_placeholder)
                error(R.drawable.ic_person_placeholder)
            }
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
            binding.fullName.text = report.searchQuery
            val bestAge = extractBestAge(meta)
            binding.jobTitle.text = buildString {
                bestAge?.let { append("Age: $it") }
                meta["demographics_gender"]?.let { g -> if (isNotEmpty()) append(" · "); append(g) }
                meta["ftn_birth_year"]?.let { y -> if (isNotEmpty()) append(" · "); append("b.~$y") }
            }
            binding.location.text = extractBestLocation(meta)
        }

        val sourceCount = try {
            val type = Types.newParameterizedType(List::class.java, com.twoskoops707.sixdegrees.domain.model.DataSource::class.java)
            moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.DataSource>>(type)
                .fromJson(report.sourcesJson)?.size ?: 0
        } catch (_: Exception) { 0 }
        binding.tvSourcesCount.text = "$sourceCount\nsources"

        val tabs = buildTabs(meta, searchType)
        val tabNames = tabs.map { it.first }
        val tabData = tabs.map { it.second }

        binding.viewPager.adapter = DossierPagerAdapter(tabData)
        binding.viewPager.offscreenPageLimit = 1

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = tabNames[pos]
        }.attach()

        binding.btnExport.setOnClickListener { shareReport(report.searchQuery, searchType, meta) }
    }

    private fun extractBestAge(meta: Map<String, String>): String? =
        meta["fps_age"] ?: meta["tt_ages"]?.split(", ")?.firstOrNull()?.trim()
            ?: meta["uspb_age"] ?: meta["tps_age"] ?: meta["zaba_age"] ?: meta["411_age"]
            ?: meta["demographics_age_estimate"]

    private fun extractBestLocation(meta: Map<String, String>): String =
        meta["uspb_addresses"]?.split(" | ")?.firstOrNull()?.trim()
            ?: meta["tt_locations"]?.split(" | ")?.firstOrNull()?.trim()
            ?: meta["fps_locations"]?.split(" | ")?.firstOrNull()?.trim()
            ?: meta["tps_locations"]?.split(" | ")?.firstOrNull()?.trim()
            ?: meta["zaba_locations"]?.split(" | ")?.firstOrNull()?.trim()
            ?: ""

    private fun buildTabs(meta: Map<String, String>, type: String): List<Pair<String, List<Pair<String, String>>>> {
        return when (type) {
            "person" -> listOf(
                "OVERVIEW" to buildPersonOverview(meta),
                "CONTACTS" to buildPersonContacts(meta),
                "LEGAL" to buildPersonLegal(meta),
                "INTEL" to buildPersonIntel(meta)
            )
            "email" -> listOf(
                "OVERVIEW" to buildEmailOverview(meta),
                "BREACHES" to buildEmailBreaches(meta),
                "IDENTITY" to buildEmailIdentity(meta)
            )
            "ip", "domain" -> {
                val tabs = mutableListOf(
                    "NETWORK" to buildIpNetwork(meta),
                    "THREATS" to buildIpThreats(meta)
                )
                val hasDomain = !meta["rdap_registrar"].isNullOrBlank() || !meta["whois"].isNullOrBlank()
                    || !meta["subdomains"].isNullOrBlank()
                if (hasDomain) tabs.add("DOMAIN" to buildIpDomain(meta))
                tabs
            }
            "username" -> listOf(
                "FOUND" to buildUsernameFound(meta),
                "PROFILES" to buildUsernameProfiles(meta)
            )
            "phone" -> listOf(
                "VALIDATION" to buildPhoneValidation(meta),
                "RISK" to buildPhoneRisk(meta)
            )
            "company" -> listOf(
                "RECORDS" to buildCompanyRecords(meta),
                "OFFICERS" to buildCompanyOfficers(meta),
                "FILINGS" to buildCompanyFilings(meta)
            )
            "image" -> listOf(
                "FACE SEARCH" to buildImageFace(meta),
                "REVERSE IMG" to buildImageReverse(meta)
            )
            "comprehensive" -> {
                val tabs = mutableListOf(
                    "SUBJECT" to buildPersonOverview(meta),
                    "CONTACTS" to buildPersonContacts(meta),
                    "LEGAL" to buildPersonLegal(meta),
                    "INTEL" to buildPersonIntel(meta)
                )
                val hasEmail = !meta["comp_email"].isNullOrBlank()
                if (hasEmail) tabs.add("EMAIL" to buildEmailBreaches(meta))
                val hasIp = !meta["comp_ip"].isNullOrBlank() && !meta["ip_city"].isNullOrBlank()
                if (hasIp) tabs.add("IP" to buildIpNetwork(meta))
                tabs
            }
            else -> listOf("DATA" to buildPersonOverview(meta))
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

    private fun buildPersonOverview(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        rows.add(sec("IDENTITY"))
        val bestAge = extractBestAge(meta)
        bestAge?.let { rows.add("Age" to it) }
        meta["ftn_birth_year"]?.let { rows.add("Birth Year" to "~$it") }
        meta["demographics_gender"]?.let { rows.add("Gender" to it) }
        meta["demographics_nationality"]?.let { rows.add("Nationality Est." to it) }

        val allPhones = extractPhones(meta)
        if (allPhones.isNotEmpty()) {
            rows.add(sec("PHONE NUMBERS"))
            allPhones.take(4).forEach { rows.add("Phone" to it) }
        }

        val bestLoc = extractBestLocation(meta)
        if (bestLoc.isNotBlank()) {
            rows.add(sec("LOCATION"))
            rows.add("Best Match" to bestLoc)
        }

        val allRel = extractRelatives(meta)
        if (allRel.isNotEmpty()) {
            rows.add(sec("KNOWN ASSOCIATES"))
            allRel.take(6).forEach { rows.add("Name" to it) }
        }

        val ddgAbstract = meta["ddg_abstract"]?.takeIf { it.isNotBlank() }
        if (ddgAbstract != null) {
            rows.add(sec("PUBLIC PROFILE"))
            ddgAbstract.lines().filter { it.isNotBlank() }.take(4).forEach { rows.add("Info" to it.trim()) }
            meta["ddg_source"]?.let { rows.add("Source" to it) }
        }

        val tpsNames = meta["tps_names"]?.takeIf { it.isNotBlank() }
        if (tpsNames != null) {
            rows.add(sec("MATCHED NAMES (TruePeopleSearch)"))
            tpsNames.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { rows.add("Name" to it) }
        }

        if (rows.size <= 2) rows.add("Status" to "No identity data found for this subject")
        return rows
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
            allRel.forEach { rows.add("Name" to it) }
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
            if (courtCount > 0) rows.add("CourtListener" to "$courtCount case${if (courtCount != 1) "s" else ""}")
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
        }

        if (rows.isEmpty()) rows.add("Status" to "No legal records found for this subject")
        return rows
    }

    private fun buildPersonIntel(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()

        val gnewsCount = meta["gnews_count"]?.toIntOrNull() ?: 0
        val newsCount = meta["news_article_count"]?.toIntOrNull() ?: 0
        val wikiHits = meta["wikipedia_hits"]?.toIntOrNull() ?: 0
        if (gnewsCount > 0 || newsCount > 0 || wikiHits > 0 || !meta["wikidata_descriptions"].isNullOrBlank()) {
            rows.add(sec("NEWS & PUBLIC RECORDS"))
            meta["wikipedia_titles"]?.let { rows.add("Wikipedia" to it) }
            meta["wikidata_descriptions"]?.let { rows.add("WikiData" to it) }
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
                rows.add("Corporations Wiki" to "$corpwikiCount record${if (corpwikiCount != 1) "s" else ""}")
                meta["corpwiki_person_companies"]?.takeIf { it.isNotBlank() }?.let {
                    it.lines().filter { l -> l.isNotBlank() }.forEach { co -> rows.add("Company" to co) }
                }
                meta["corpwiki_person_states"]?.let { rows.add("States" to it) }
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

        val hasSearchIntel = !meta["cse_snippets"].isNullOrBlank() || !meta["bing_snippets"].isNullOrBlank()
        if (hasSearchIntel) {
            rows.add(sec("SEARCH ENGINE INTEL"))
            meta["bing_total"]?.let { t -> if ((t.toLongOrNull() ?: 0L) > 0) rows.add("Bing Results" to t) }
            meta["cse_snippets"]?.takeIf { it.isNotBlank() }?.let {
                it.split("\n---\n").filter { s -> s.isNotBlank() }.take(5).forEach { s ->
                    rows.add("Google CSE" to s.trim())
                }
            }
            meta["bing_snippets"]?.takeIf { it.isNotBlank() }?.let {
                it.split("\n---\n").filter { s -> s.isNotBlank() }.take(5).forEach { s ->
                    rows.add("Bing" to s.trim())
                }
            }
        }

        val ahmiaCount = meta["ahmia_count"]?.toIntOrNull() ?: 0
        if (ahmiaCount > 0) {
            rows.add(sec("⚠ DARK WEB MENTIONS"))
            rows.add("⚠ Dark Web Hits" to "$ahmiaCount result${if (ahmiaCount != 1) "s" else ""} on Tor sites")
            meta["ahmia_titles"]?.takeIf { it.isNotBlank() }?.let {
                it.lines().filter { l -> l.isNotBlank() }.forEach { t -> rows.add("⚠ Tor Site" to t) }
            }
        }

        meta["ai_summary"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("AI INTELLIGENCE SYNTHESIS"))
            it.lines().filter { l -> l.isNotBlank() }.forEach { line -> rows.add("AI Analysis" to line) }
            rows.add("Note" to "AI-synthesized from public data — verify independently")
        }

        val pivotPhones = extractPhones(meta).take(5)
        val pivotEmails = linkedSetOf<String>()
        meta["email"]?.takeIf { it.isNotBlank() }?.let { pivotEmails.add(it) }
        meta["cse_email_hits"]?.split(",")?.map { it.trim() }?.filter { it.contains("@") }?.forEach { pivotEmails.add(it) }
        val searchQuery = arguments?.getString("searchQuery") ?: ""
        if (pivotPhones.isNotEmpty() || pivotEmails.isNotEmpty()) {
            rows.add(sec("PIVOT SEARCHES"))
            pivotPhones.forEach { phone -> rows.add("⟶ Search Phone" to "pivot://phone/$phone") }
            pivotEmails.take(3).forEach { email -> rows.add("⟶ Search Email" to "pivot://email/$email") }
            if (searchQuery.isNotBlank()) {
                val parts = searchQuery.trim().split(" ")
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
            rows.add(sec("COMB DATASET — $proxyCount RECORD${if (proxyCount != 1) "S" else ""}"))
            rows.add("Source" to "3.2 billion leaked credentials database")
            meta["proxynova_samples"]?.takeIf { it.isNotBlank() }?.let { rows.add("Sample Entries" to it) }
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
            "Twitter" to "Microblogging & social media",
            "Instagram" to "Photo & video sharing",
            "TikTok" to "Short-form video sharing",
            "YouTube" to "Video sharing & streaming",
            "LinkedIn" to "Professional networking",
            "Pinterest" to "Visual discovery & idea sharing",
            "Twitch" to "Live streaming platform",
            "Discord" to "Voice & text communities",
            "Telegram" to "Encrypted messaging & channels",
            "Patreon" to "Creator monetization",
            "Medium" to "Online publishing & blogging",
            "Keybase" to "Encrypted identity verification",
            "Gravatar" to "Globally recognized avatar service",
            "Behance" to "Creative portfolio (Adobe)",
            "Dribbble" to "Designer portfolio & community",
            "StackOverflow" to "Developer Q&A",
            "Quora" to "Q&A knowledge sharing",
            "Replit" to "Browser-based coding",
            "DevTo" to "Developer blogging community",
            "HackerNews" to "Tech news (Y Combinator)",
            "Steam" to "Video game distribution & community",
            "Spotify" to "Music & podcast streaming",
            "SoundCloud" to "Music sharing & audio streaming",
            "Bandcamp" to "Music publishing & fan support",
            "Fiverr" to "Freelance services marketplace",
            "Upwork" to "Freelance work & hiring",
            "Last.fm" to "Music tracking & social recommendation",
            "Chess.com" to "Online chess platform",
            "Duolingo" to "Language learning",
            "Wattpad" to "Story sharing & reading community"
        )
        meta["found_urls"]?.takeIf { it.isNotBlank() }?.let {
            it.lines().filter { l -> l.isNotBlank() }.forEach { line ->
                val parts = line.split(": ", limit = 2)
                val siteName = parts.firstOrNull() ?: "Platform"
                val url = parts.getOrNull(1) ?: line
                val desc = siteDesc[siteName]
                rows.add("✓ $siteName${if (desc != null) " — $desc" else ""}" to url)
            }
        }
        if (rows.isEmpty()) rows.add("Status" to "No profiles found on tracked platforms")
        return rows
    }

    private fun buildUsernameProfiles(meta: Map<String, String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        meta["github_profile"]?.takeIf { it.isNotBlank() }?.let { profile ->
            rows.add(sec("GITHUB"))
            Regex("\"name\":\\s*\"([^\"]+)\"").find(profile)?.groupValues?.get(1)?.let { rows.add("Name" to it) }
            Regex("\"bio\":\\s*\"([^\"]+)\"").find(profile)?.groupValues?.get(1)?.let { rows.add("Bio" to it) }
            Regex("\"company\":\\s*\"([^\"]+)\"").find(profile)?.groupValues?.get(1)?.let { rows.add("Company" to it) }
            Regex("\"location\":\\s*\"([^\"]+)\"").find(profile)?.groupValues?.get(1)?.let { rows.add("Location" to it) }
            Regex("\"followers\":\\s*(\\d+)").find(profile)?.groupValues?.get(1)?.let { rows.add("Followers" to it) }
            Regex("\"public_repos\":\\s*(\\d+)").find(profile)?.groupValues?.get(1)?.let { rows.add("Repos" to it) }
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
        if (rows.isEmpty()) rows.add("Status" to "No profile data extracted from found accounts")
        return rows
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
        meta["veriphone_carrier"]?.takeIf { it.isNotBlank() }?.let { rows.add("Carrier (Veriphone)" to it) }
        meta["veriphone_line_type"]?.takeIf { it.isNotBlank() }?.let { rows.add("Line Type (Veriphone)" to it) }
        meta["veriphone_country"]?.takeIf { it.isNotBlank() }?.let { rows.add("Country (Veriphone)" to it) }
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
        val secFilings = meta["sec_filings_count"]?.toIntOrNull() ?: 0
        if (secFilings > 0) {
            rows.add(sec("SEC EDGAR — $secFilings FILING${if (secFilings != 1) "S" else ""}"))
            meta["sec_filing_types"]?.let { rows.add("Filing Types" to it) }
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
        val set = linkedSetOf<String>()
        listOf("tt_phones", "uspb_phones", "fps_phones", "tps_phones", "zaba_phones", "411_phones")
            .forEach { key -> meta[key]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { set.add(it) } }
        return set
    }

    private fun extractAddresses(meta: Map<String, String>): LinkedHashSet<String> {
        val set = linkedSetOf<String>()
        listOf("uspb_addresses", "tt_locations", "fps_locations", "ftn_locations", "tps_locations", "zaba_addresses", "zaba_locations", "411_locations")
            .forEach { key -> meta[key]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { set.add(it) } }
        return set
    }

    private fun extractRelatives(meta: Map<String, String>): LinkedHashSet<String> {
        val set = linkedSetOf<String>()
        listOf("tt_relatives", "fps_relatives", "ftn_relatives", "tps_relatives", "411_relatives", "corpwiki_associates")
            .forEach { key -> meta[key]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { set.add(it) } }
        return set
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
        if (ipqueryRisk > 30) { score += minOf(ipqueryRisk / 2, 30); flags.add("risk score $ipqueryRisk") }

        val ipqsIpFraud = meta["ipqs_ip_fraud_score"]?.toIntOrNull() ?: 0
        if (ipqsIpFraud > 30) { score += minOf(ipqsIpFraud / 2, 30); flags.add("IP fraud $ipqsIpFraud") }

        val ipqsEmailFraud = meta["ipqs_email_fraud_score"]?.toIntOrNull() ?: 0
        if (ipqsEmailFraud > 50) { score += minOf(ipqsEmailFraud / 3, 20); flags.add("email fraud $ipqsEmailFraud") }

        val ipqsPhoneFraud = meta["ipqs_phone_fraud_score"]?.toIntOrNull() ?: 0
        if (ipqsPhoneFraud > 50) { score += minOf(ipqsPhoneFraud / 3, 20); flags.add("phone fraud $ipqsPhoneFraud") }

        val sanctionsHits = meta["opensanctions_total"]?.toIntOrNull() ?: 0
        if (sanctionsHits > 0) { score += minOf(sanctionsHits * 20, 40); flags.add("$sanctionsHits sanctions hit${if (sanctionsHits != 1) "s" else ""}") }

        score = minOf(score, 100)

        val (color, verdict, detail) = when {
            score == 0 -> Triple(ContextCompat.getColor(requireContext(), R.color.score_green), "CLEAR", "No significant indicators found")
            score < 30 -> Triple(ContextCompat.getColor(requireContext(), R.color.score_green), "LOW", flags.joinToString(" · ").ifBlank { "Minor indicators" })
            score < 60 -> Triple(ContextCompat.getColor(requireContext(), R.color.score_yellow), "MODERATE", flags.joinToString(" · "))
            else -> Triple(ContextCompat.getColor(requireContext(), R.color.score_red), "HIGH RISK", flags.joinToString(" · "))
        }

        binding.tvScoreNumber.text = if (score == 0) "✓" else score.toString()
        binding.tvScoreNumber.setTextColor(color)
        binding.tvScoreVerdict.text = verdict
        binding.tvScoreVerdict.setTextColor(color)
        binding.tvScoreDetail.text = detail
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
        return try {
            val type = Types.newParameterizedType(List::class.java, com.twoskoops707.sixdegrees.domain.model.Address::class.java)
            val addrs = moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.Address>>(type).fromJson(json) ?: emptyList()
            addrs.firstOrNull()?.let { listOf(it.city, it.state, it.country).filter { s -> s.isNotBlank() }.joinToString(", ") } ?: ""
        } catch (_: Exception) { "" }
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

    private inner class DossierPagerAdapter(
        private val pages: List<List<Pair<String, String>>>
    ) : RecyclerView.Adapter<DossierPagerAdapter.PageVH>() {

        inner class PageVH(val rv: RecyclerView) : RecyclerView.ViewHolder(rv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
            val rv = RecyclerView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                layoutManager = LinearLayoutManager(parent.context)
                setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.bottom_nav_padding))
                clipToPadding = false
            }
            return PageVH(rv)
        }

        override fun onBindViewHolder(holder: PageVH, position: Int) {
            holder.rv.adapter = DataRowAdapter(pages[position])
        }

        override fun getItemCount() = pages.size
    }

    private inner class DataRowAdapter(
        private val rows: List<Pair<String, String>>
    ) : RecyclerView.Adapter<DataRowAdapter.VH>() {

        inner class VH(val b: ItemDataRowBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemDataRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (label, value) = rows[position]
            val isSectionHeader = value.isEmpty() && (label.startsWith("◈") || label.startsWith("> ") || label.startsWith("══"))
            val isPivot = value.startsWith("pivot://")
            val isLink = value.startsWith("http://") || value.startsWith("https://")
            val ctx = holder.b.root.context

            if (isSectionHeader) {
                holder.b.tvRowLabel.text = ""
                val clean = label.trimStart()
                    .removePrefix("◈ ").removePrefix("> ").removePrefix("══ ")
                    .removeSuffix(" ══").trim()
                holder.b.tvRowValue.text = "  $clean"
                val tv = android.util.TypedValue()
                ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
                holder.b.tvRowValue.setTextColor(tv.data)
                holder.b.tvRowValue.textSize = 10f
                holder.b.tvRowValue.letterSpacing = 0.15f
                holder.b.tvRowValue.typeface = android.graphics.Typeface.DEFAULT_BOLD
                holder.b.rowAccentStripe.visibility = View.GONE
                holder.b.root.setBackgroundColor(tv.data and 0x30FFFFFF or 0x0D000000)
                holder.b.root.setOnClickListener(null)
            } else if (isPivot) {
                holder.b.rowAccentStripe.visibility = View.VISIBLE
                holder.b.rowAccentStripe.setBackgroundColor(ContextCompat.getColor(ctx, R.color.accent_cyan))
                holder.b.root.setBackgroundColor(0)
                val parts = value.removePrefix("pivot://").split("/", limit = 2)
                val pivotType = parts.getOrNull(0) ?: "person"
                val pivotQuery = parts.getOrNull(1) ?: ""
                holder.b.tvRowLabel.text = label
                holder.b.tvRowValue.text = pivotQuery
                holder.b.tvRowValue.textSize = 13f
                holder.b.tvRowValue.letterSpacing = 0f
                holder.b.tvRowValue.typeface = android.graphics.Typeface.DEFAULT
                holder.b.tvRowValue.setTextColor(ContextCompat.getColor(ctx, R.color.accent_cyan))
                holder.b.root.setOnClickListener {
                    val bundle = Bundle().apply {
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
                    val tv = android.util.TypedValue()
                    ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
                    holder.b.rowAccentStripe.setBackgroundColor(tv.data)
                }
                holder.b.root.setBackgroundColor(0)
                holder.b.tvRowLabel.text = label
                holder.b.tvRowValue.text = value
                holder.b.tvRowValue.textSize = 13f
                holder.b.tvRowValue.letterSpacing = 0f
                holder.b.tvRowValue.typeface = android.graphics.Typeface.DEFAULT
                if (isLink) {
                    holder.b.tvRowValue.setTextColor(ContextCompat.getColor(ctx, R.color.accent_cyan))
                    holder.b.root.setOnClickListener {
                        val prefs = it.context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                        val pkg = when (prefs.getString("pref_browser", "firefox")) {
                            "ddg" -> "com.duckduckgo.mobile.android"
                            "chrome" -> "com.android.chrome"
                            "default" -> null
                            else -> "org.mozilla.firefox"
                        }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(value))
                        if (pkg != null) intent.setPackage(pkg)
                        try { it.context.startActivity(intent) }
                        catch (_: Exception) { it.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value))) }
                    }
                } else {
                    val tv = android.util.TypedValue()
                    ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true)
                    holder.b.tvRowValue.setTextColor(tv.data)
                    holder.b.root.setOnClickListener(null)
                }
            }
        }

        override fun getItemCount() = rows.size
    }
}
