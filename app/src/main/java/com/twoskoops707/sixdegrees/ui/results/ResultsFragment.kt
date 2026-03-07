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
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.card.MaterialCardView
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

        val enrichedMeta = meta.toMutableMap()
        if (person != null) {
            val allJobs = parseAllJobs(person.employmentHistoryJson)
            if (allJobs.isNotEmpty()) enrichedMeta["pipl_employment"] = allJobs.joinToString("\n")
            val allAddrs = parseAllAddresses(person.addressesJson)
            if (allAddrs.isNotEmpty()) enrichedMeta["pipl_addresses"] = allAddrs.joinToString(" | ")
            val allSocials = parseAllSocials(person.socialProfilesJson)
            if (allSocials.isNotEmpty()) enrichedMeta["pipl_socials"] = allSocials.joinToString("\n")
            person.emailAddress?.takeIf { it.isNotBlank() }?.let { enrichedMeta["pipl_email"] = it }
            person.phoneNumber?.takeIf { it.isNotBlank() }?.let { enrichedMeta["pipl_phone"] = it }
            person.gender?.takeIf { it.isNotBlank() }?.let { enrichedMeta.getOrPut("pipl_gender") { it } }
        }

        val tabs = buildTabs(enrichedMeta, searchType)
        val tabNames = tabs.map { it.first }
        val tabData = tabs.map { it.second }

        binding.viewPager.adapter = DossierPagerAdapter(tabData)
        binding.viewPager.offscreenPageLimit = 1

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = tabNames[pos]
        }.attach()

        binding.btnExport.setOnClickListener { shareReport(report.searchQuery, searchType, enrichedMeta) }
    }

    private fun extractBestAge(meta: Map<String, String>): String? =
        meta["tps_age"] ?: meta["zaba_age"] ?: meta["411_age"]
            ?: meta["voter_age"] ?: meta["radaris_age"] ?: meta["peekyou_age"]
            ?: meta["nuwber_age"] ?: meta["fps_age"] ?: meta["tt_ages"]?.split(", ")?.firstOrNull()?.trim()
            ?: meta["uspb_age"] ?: meta["demographics_age_estimate"]

    private fun extractBestLocation(meta: Map<String, String>): String =
        meta["tps_locations"]?.split(" | ")?.firstOrNull()?.trim()
            ?: meta["zaba_locations"]?.split(" | ")?.firstOrNull()?.trim()
            ?: meta["411_locations"]?.split(" | ")?.firstOrNull()?.trim()
            ?: meta["ftn_locations"]?.split(" | ")?.firstOrNull()?.trim()
            ?: meta["voter_addresses"]?.split(" | ")?.firstOrNull()?.trim()
            ?: meta["uspb_addresses"]?.split(" | ")?.firstOrNull()?.trim()
            ?: meta["tt_locations"]?.split(" | ")?.firstOrNull()?.trim()
            ?: meta["fps_locations"]?.split(" | ")?.firstOrNull()?.trim()
            ?: meta["radaris_locations"]?.split(" | ")?.firstOrNull()?.trim()
            ?: meta["peekyou_locations"]?.split(" | ")?.firstOrNull()?.trim()
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
        meta["pipl_gender"]?.takeIf { meta["demographics_gender"].isNullOrBlank() }?.let { rows.add("Gender" to it) }
        meta["demographics_nationality"]?.let { rows.add("Nationality Est." to it) }

        meta["pipl_employment"]?.takeIf { it.isNotBlank() }?.let { emp ->
            rows.add(sec("EMPLOYMENT HISTORY"))
            emp.lines().filter { it.isNotBlank() }.forEach { rows.add("Job" to it) }
        }

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

        val ddgInfobox = meta["ddg_infobox"]?.takeIf { it.isNotBlank() }
        if (ddgInfobox != null) {
            if (meta["ddg_abstract"].isNullOrBlank()) rows.add(sec("PUBLIC PROFILE"))
            ddgInfobox.lines().filter { it.isNotBlank() }.take(6).forEach { rows.add("Detail" to it.trim()) }
        }

        meta["ddg_answer"]?.takeIf { it.isNotBlank() && meta["ddg_abstract"].isNullOrBlank() && ddgInfobox == null }?.let {
            rows.add(sec("QUICK ANSWER"))
            rows.add("Answer" to it)
        }

        val socialLinks = buildSocialLinks(meta)
        if (socialLinks.isNotEmpty()) {
            rows.add(sec("SOCIAL DISCOVERY"))
            socialLinks.take(8).forEach { (label, url) -> rows.add(label to url) }
        }

        if (rows.size <= 2) rows.add("Status" to "No identity data found for this subject")
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
            allRel.forEach { rows.add("Name" to it) }
        }

        val emails = linkedSetOf<String>()
        meta["pipl_email"]?.takeIf { it.isNotBlank() }?.let { emails.add(it) }
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
            meta["wikipedia_link"]?.takeIf { wikiHits > 0 }?.let { rows.add("⟶ View on Wikipedia" to it) }
            meta["wikidata_descriptions"]?.let { rows.add("WikiData" to it) }
            meta["wikidata_link"]?.let { rows.add("⟶ View on WikiData" to it) }
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

        val ahmiaCount = meta["ahmia_count"]?.toIntOrNull() ?: 0
        if (ahmiaCount > 0) {
            rows.add(sec("⚠ DARK WEB MENTIONS"))
            rows.add("⚠ Indexed Hits" to "$ahmiaCount result${if (ahmiaCount != 1) "s" else ""} found via Ahmia.fi Tor index")
            val viaToR = meta["ahmia_via_tor"]?.toBooleanStrictOrNull() == true
            if (viaToR) rows.add("⚠ Connection" to "Fetched via Tor network")
            val titleLines = meta["ahmia_titles"]?.lines()?.filter { it.isNotBlank() } ?: emptyList()
            val urlLines = meta["ahmia_urls"]?.lines()?.filter { it.isNotBlank() } ?: emptyList()
            val descLines = meta["ahmia_descs"]?.split("\n---\n")?.filter { it.isNotBlank() } ?: emptyList()
            titleLines.forEachIndexed { i, t ->
                rows.add("⚠ Tor Site" to t)
                descLines.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { d -> rows.add("  Description" to d) }
                urlLines.getOrNull(i)?.let { u -> rows.add("  .onion URL" to u) }
            }
            rows.add("⚠ Note" to "Ahmia indexes publicly-accessible Tor hidden services. Subject to index freshness.")
        }

        meta["ai_summary"]?.takeIf { it.isNotBlank() }?.let {
            rows.add(sec("AI INTELLIGENCE SYNTHESIS"))
            it.lines().filter { l -> l.isNotBlank() }.forEach { line -> rows.add("AI Analysis" to line) }
            rows.add("⚠ Disclaimer" to "AI-generated summary — may not reflect actual individual. Verify all claims independently.")
        }

        val dorkCount = meta["shadowdork_count"]?.toIntOrNull() ?: 0
        val hasDorkLinks = !meta["dork_identity"].isNullOrBlank()
        if (dorkCount > 0 || hasDorkLinks) {
            rows.add(sec("INVESTIGATIVE SEARCH DORKS"))
            rows.add("Note" to "Tap a dork to run it in your browser — targeted searches pre-built for this subject")
            meta["dork_identity"]?.let { rows.add("⟶ Identity & Age" to it) }
            meta["dork_relatives"]?.let { rows.add("⟶ Relatives & Family" to it) }
            meta["dork_address"]?.let { rows.add("⟶ Address Records" to it) }
            meta["dork_criminal"]?.let { rows.add("⟶ Criminal Records" to it) }
            meta["dork_property"]?.let { rows.add("⟶ Property Records" to it) }
            meta["dork_vehicle"]?.let { rows.add("⟶ Vehicle Trace" to it) }
            meta["dork_social"]?.let { rows.add("⟶ Social Discovery" to it) }
            meta["dork_leaks"]?.let { rows.add("⟶ Leaked Data Search" to it) }
            meta["dork_files"]?.let { rows.add("⟶ Leaked File Dump" to it) }
            val shownDorkKeys = setOf(
                "identity_confirm", "address_records", "relatives_map", "criminal_records",
                "property_records", "vehicle_trace", "social_discovery", "leaked_data", "files_dump"
            )
            val extraDorkLabels = mapOf(
                "financial_exposure" to "Financial Exposure",
                "email_patterns" to "Email Patterns",
                "business_ties" to "Business Ties",
                "court_deep" to "Court Deep Search",
                "voter_records" to "Voter Records",
                "obituary_cross" to "Obituary Cross-Reference",
                "dark_mentions" to "Dark Data Mentions"
            )
            meta["shadowdork_links"]?.lines()?.filter { it.isNotBlank() }?.forEach { line ->
                val keyEnd = line.indexOf("::")
                if (keyEnd > 0) {
                    val key = line.substring(0, keyEnd)
                    if (key !in shownDorkKeys && key in extraDorkLabels) {
                        val gStart = line.indexOf("::G:") + 4
                        val gEnd = line.indexOf("::B:")
                        if (gStart > 4 && gEnd > gStart) {
                            rows.add("⟶ ${extraDorkLabels[key]}" to line.substring(gStart, gEnd))
                        }
                    }
                }
            }
        }

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

        val pivotPhones = extractPhones(meta).take(5)
        val pivotEmails = linkedSetOf<String>()
        meta["email"]?.takeIf { it.isNotBlank() }?.let { pivotEmails.add(it) }
        meta["cse_email_hits"]?.split(",")?.map { it.trim() }?.filter { it.contains("@") }?.forEach { pivotEmails.add(it) }
        meta["radaris_emails"]?.split(",")?.map { it.trim() }?.filter { it.contains("@") }?.forEach { pivotEmails.add(it) }
        meta["nuwber_emails"]?.split(",")?.map { it.trim() }?.filter { it.contains("@") }?.forEach { pivotEmails.add(it) }
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
                val desc = siteDesc[siteName]
                val label = if (isNsfw) "⚠ NSFW/$siteName" else "✓ $siteName"
                rows.add("$label${if (desc != null) " — ${desc.removePrefix("⚠ ")}" else ""}" to url)
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
        val tollfree = setOf("800", "888", "877", "866", "855", "844", "833", "822")
        val areaCodeRegex = Regex("^\\((\\d{3})\\)")
        val set = linkedSetOf<String>()
        meta["pipl_phone"]?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        listOf("tps_phones", "zaba_phones", "411_phones", "tt_phones", "uspb_phones", "fps_phones", "radaris_phones", "nuwber_phones")
            .forEach { key ->
                meta[key]?.split(",")?.map { it.trim() }?.filter { phone ->
                    phone.isNotBlank() && areaCodeRegex.find(phone)?.groupValues?.get(1) !in tollfree
                }?.forEach { set.add(it) }
            }
        return set
    }

    private fun extractAddresses(meta: Map<String, String>): LinkedHashSet<String> {
        val set = linkedSetOf<String>()
        meta["pipl_addresses"]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { set.add(it) }
        listOf("tps_full_addresses", "tps_locations", "zaba_addresses", "zaba_locations", "411_locations",
            "ftn_locations", "voter_addresses", "uspb_addresses", "tt_locations", "fps_locations",
            "radaris_locations", "peekyou_locations", "nuwber_locations")
            .forEach { key -> meta[key]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { set.add(it) } }
        return set
    }

    private fun extractRelatives(meta: Map<String, String>): LinkedHashSet<String> {
        val set = linkedSetOf<String>()
        listOf("tps_relatives", "ftn_relatives", "411_relatives", "tt_relatives", "fps_relatives",
            "corpwiki_associates", "radaris_relatives", "nuwber_relatives")
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
        binding.scoreAccentBar.setBackgroundColor(color)
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

    private fun buildSectionCard(ctx: Context, inflater: LayoutInflater, title: String, rows: List<Pair<String, String>>): View {
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()

        val tv = TypedValue()
        ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
        val colorPrimary = tv.data

        val isWarning = title.startsWith("⚠")
        val accentColor = if (isWarning) ContextCompat.getColor(ctx, R.color.score_red) else colorPrimary
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
                setPadding(dp(14f), dp(9f), dp(16f), dp(9f))
                setBackgroundColor(Color.argb(26, r, g, b))
            }
            header.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(3f), dp(14f)).also { it.marginEnd = dp(10f) }
                setBackgroundColor(accentColor)
            })
            header.addView(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = title
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAllCaps = true
                letterSpacing = 0.15f
                setTextColor(accentColor)
            })
            inner.addView(header)
            inner.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f))
                setBackgroundColor(borderColor)
                alpha = 0.5f
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

        when {
            isWarning -> {
                b.rowAccentStripe.visibility = View.VISIBLE
                b.rowAccentStripe.setBackgroundColor(ContextCompat.getColor(ctx, R.color.score_red))
                b.root.setBackgroundColor(ContextCompat.getColor(ctx, R.color.error_dim))
            }
            isPivot -> {
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
            isWarning -> {
                b.tvRowValue.typeface = Typeface.DEFAULT_BOLD
                b.tvRowValue.textSize = 14f
                b.tvRowValue.setTextColor(ContextCompat.getColor(ctx, R.color.score_red))
            }
            isLink || isPivot -> {
                b.tvRowValue.typeface = Typeface.DEFAULT
                b.tvRowValue.textSize = 13f
                b.tvRowValue.setTextColor(ContextCompat.getColor(ctx, R.color.accent_cyan))
            }
            else -> {
                b.tvRowValue.typeface = Typeface.DEFAULT_BOLD
                b.tvRowValue.textSize = 14f
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
                    val prefs = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    val pkg = when (prefs.getString("pref_browser", "firefox")) {
                        "ddg" -> "com.duckduckgo.mobile.android"
                        "chrome" -> "com.android.chrome"
                        "default" -> null
                        else -> "org.mozilla.firefox"
                    }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(value))
                    if (pkg != null) intent.setPackage(pkg)
                    try { startActivity(intent) }
                    catch (_: Exception) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value))) }
                }
            }
            else -> b.root.setOnClickListener(null)
        }
    }

    private inner class DossierPagerAdapter(
        private val pages: List<List<Pair<String, String>>>
    ) : RecyclerView.Adapter<DossierPagerAdapter.PageVH>() {

        inner class PageVH(val sv: NestedScrollView, val container: LinearLayout) : RecyclerView.ViewHolder(sv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
            val density = parent.context.resources.displayMetrics.density
            fun dp(f: Float) = (f * density).toInt()
            val container = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(dp(10f), dp(8f), dp(10f), resources.getDimensionPixelSize(R.dimen.bottom_nav_padding))
            }
            val sv = NestedScrollView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                clipToPadding = false
                addView(container)
            }
            return PageVH(sv, container)
        }

        override fun onBindViewHolder(holder: PageVH, position: Int) {
            holder.container.removeAllViews()
            val inflater = LayoutInflater.from(holder.container.context)
            val sections = groupIntoSections(pages[position])
            for ((title, rows) in sections) {
                holder.container.addView(buildSectionCard(holder.container.context, inflater, title, rows))
            }
        }

        override fun getItemCount() = pages.size
    }
}
