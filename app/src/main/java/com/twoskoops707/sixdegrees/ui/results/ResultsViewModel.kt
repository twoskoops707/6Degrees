package com.twoskoops707.sixdegrees.ui.results

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.twoskoops707.sixdegrees.data.AppSettings
import com.twoskoops707.sixdegrees.data.local.entity.OsintReportEntity
import com.twoskoops707.sixdegrees.data.local.entity.PersonEntity
import com.twoskoops707.sixdegrees.data.repository.OsintRepository
import com.twoskoops707.sixdegrees.domain.SubjectFilter
import kotlinx.coroutines.launch

enum class DossierConfidence { HIGH, MEDIUM, LOW }

data class DossierFinding(
    val label: String? = null,
    val value: String,
    val source: String,
    val confidence: DossierConfidence,
    val isPivot: Boolean = false,
    val isLink: Boolean = false,
    val isWarning: Boolean = false,
    val sourceUrl: String? = null
)

data class DossierSection(
    val id: String,
    val title: String,
    val icon: String,
    val findings: List<DossierFinding>,
    val skippedNotes: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = findings.isEmpty() && skippedNotes.isEmpty()
}

data class ShadyScore(
    val score: Int,
    val verdict: String,
    val detail: String,
    val displayValue: String
)

data class ResultsUiState(
    val isLoading: Boolean = false,
    val report: OsintReportEntity? = null,
    val person: PersonEntity? = null,
    val error: String? = null,
    val dossierSections: List<DossierSection> = emptyList(),
    val shadyScore: ShadyScore? = null
)

class ResultsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = OsintRepository(app)

    private val _state = MutableLiveData<ResultsUiState>(ResultsUiState(isLoading = true))
    val state: LiveData<ResultsUiState> = _state

    fun loadReport(reportId: String) {
        _state.value = ResultsUiState(isLoading = true)
        viewModelScope.launch {
            val report = repository.getReportById(reportId)
            if (report == null) {
                _state.value = ResultsUiState(error = "Report not found.")
                return@launch
            }
            val person = report.personId?.let { repository.getPersonById(it) }
            val meta = DossierBuilder.parseMetadata(report.companiesJson)
            val enriched = DossierBuilder.enrichFromPerson(meta, person)
            val searchType = enriched["search_type"] ?: "person"
            val investigator = AppSettings.isInvestigatorMode(getApplication())
            val sections = DossierBuilder.buildSectionsForMode(enriched, searchType, investigator)
            val shady = DossierBuilder.computeShadyScore(enriched, searchType)
            _state.value = ResultsUiState(
                isLoading = false,
                report = report,
                person = person,
                dossierSections = sections,
                shadyScore = shady
            )
        }
    }

    fun updateDossier(meta: Map<String, String>, searchType: String) {
        val current = _state.value ?: return
        val investigator = AppSettings.isInvestigatorMode(getApplication())
        _state.value = current.copy(
            dossierSections = DossierBuilder.buildSectionsForMode(meta, searchType, investigator),
            shadyScore = DossierBuilder.computeShadyScore(meta, searchType)
        )
    }
}

object DossierBuilder {

    private val SOURCE_LABELS = mapOf(
        "pipl" to "Pipl",
        "pdl" to "People Data Labs",
        "tps" to "TruePeopleSearch",
        "zaba" to "ZabaSearch",
        "411" to "411.com",
        "voter" to "Voter Records",
        "radaris" to "Radaris",
        "nuwber" to "Nuwber",
        "peekyou" to "PeekYou",
        "clearbit" to "Clearbit",
        "sherlock" to "Sherlock",
        "maigret" to "Maigret",
        "hibp" to "Have I Been Pwned",
        "courtlistener" to "CourtListener",
        "judyrecords" to "JudyRecords",
        "opensanctions" to "OpenSanctions",
        "darksearch" to "DarkSearch",
        "ahmia" to "Ahmia",
        "corpwiki" to "OpenCorporates",
        "sec" to "SEC EDGAR",
        "wikipedia" to "Wikipedia",
        "wikidata" to "Wikidata",
        "ddg" to "DuckDuckGo",
        "ai" to "AI Analysis",
        "dork" to "Auto-Dork",
        "search" to "Web Search",
        "holehe" to "Holehe",
        "github" to "GitHub",
        "found_urls" to "Profile Scan"
    )

    private val HIGH_CONFIDENCE = setOf("pipl", "pdl", "voter", "courtlistener", "judyrecords", "opensanctions", "clearbit")
    private val MEDIUM_CONFIDENCE = setOf("tps", "zaba", "411", "radaris", "nuwber", "peekyou", "sherlock", "maigret", "hibp", "corpwiki", "sec", "wikipedia", "wikidata", "holehe", "github")

    fun parseMetadata(companiesJson: String): MutableMap<String, String> {
        return try {
            val json = org.json.JSONObject(companiesJson)
            val map = mutableMapOf<String, String>()
            json.keys().forEach { key -> map[key] = json.optString(key, "") }
            map
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    fun enrichFromPerson(meta: MutableMap<String, String>, person: PersonEntity?): MutableMap<String, String> {
        if (person == null) return meta
        person.emailAddress?.takeIf { it.isNotBlank() }?.let { meta["pipl_email"] = it }
        person.phoneNumber?.takeIf { it.isNotBlank() }?.let { meta["pipl_phone"] = it }
        person.gender?.takeIf { it.isNotBlank() }?.let { meta.getOrPut("pipl_gender") { it } }
        person.dateOfBirth?.takeIf { it.isNotBlank() }?.let { meta["pipl_dob"] = it }
        return meta
    }

    fun buildSectionsForMode(meta: Map<String, String>, searchType: String, investigatorMode: Boolean): List<DossierSection> =
        if (investigatorMode) buildSections(meta, searchType) else buildSimpleSections(meta, searchType)

    fun buildSections(meta: Map<String, String>, searchType: String): List<DossierSection> {
        if (searchType !in setOf("person", "scan", "comprehensive")) {
            return listOf(buildLegacyFallback(meta))
        }
        val city = meta["person_city"] ?: meta["search_city"] ?: ""
        val state = meta["person_state"] ?: meta["search_state"] ?: ""
        val geoLabel = listOf(city, state).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "search area" }

        return listOf(
            buildIdentitySection(meta),
            buildLocationsSection(meta, city, state, geoLabel),
            buildContactSection(meta),
            buildFamilySection(meta),
            buildEmploymentSection(meta),
            buildLegalSection(meta),
            buildDigitalSection(meta),
            buildVehiclesSection(meta),
            buildDarkWebSection(meta),
            buildAiSection(meta)
        )
    }

    fun buildSimpleSections(meta: Map<String, String>, searchType: String): List<DossierSection> {
        if (searchType !in setOf("person", "scan", "comprehensive")) {
            return listOf(buildLegacyFallback(meta))
        }
        val full = buildSections(meta, searchType)
        val byId = full.associateBy { it.id }

        fun findings(vararg ids: String): List<DossierFinding> =
            ids.flatMap { id -> byId[id]?.findings.orEmpty() }
                .filter { f -> !isEmptyPlaceholder(f) }

        val who = findings("identity", "employment", "contact", "family")
        val where = byId["locations"]?.findings.orEmpty().filter { !isEmptyPlaceholder(it) }
        val flags = findings("legal", "darkweb").filter { it.isWarning || isRedFlagFinding(it) }
        val shady = computeShadyScore(meta, searchType)
        val safe = buildSafeToMeetFindings(meta, shady)

        return listOf(
            DossierSection("who", "Who they are", "👤", who.ifEmpty {
                listOf(finding("We couldn't find much — try adding a city or photo", "SixDegrees", DossierConfidence.LOW))
            }),
            DossierSection("where", "Where they've been", "📍", where.ifEmpty {
                listOf(finding("No location history found in public records", "SixDegrees", DossierConfidence.LOW))
            }),
            DossierSection("flags", "Red flags", "⚠", flags.ifEmpty {
                listOf(finding("Nothing alarming turned up in public or indexed sources", "SixDegrees", DossierConfidence.LOW))
            }),
            DossierSection("safe", "Safe to meet?", "✓", safe)
        )
    }

    private fun isEmptyPlaceholder(finding: DossierFinding): Boolean {
        val v = finding.value.lowercase()
        return v.startsWith("no ") && v.contains("found")
    }

    private fun isRedFlagFinding(finding: DossierFinding): Boolean {
        val label = finding.label?.lowercase().orEmpty()
        return label.contains("breach") || label.contains("arrest") || label.contains("sanction")
            || label.contains("court") || label.contains("dark") || label.contains("paste")
    }

    private fun buildSafeToMeetFindings(meta: Map<String, String>, shady: ShadyScore): List<DossierFinding> {
        val findings = mutableListOf<DossierFinding>()
        val verdictLabel = when {
            shady.score == 0 -> "Looks okay from public data"
            shady.score < 30 -> "Minor concerns — use your judgment"
            shady.score < 60 -> "Some red flags — proceed carefully"
            else -> "Serious concerns — trust your instincts"
        }
        findings.add(finding(verdictLabel, "SixDegrees", DossierConfidence.MEDIUM, "Overall"))
        if (shady.detail.isNotBlank() && shady.score > 0) {
            findings.add(finding(shady.detail, "SixDegrees", DossierConfidence.MEDIUM, "Why"))
        }
        meta["ai_executive_summary"]?.takeIf { it.isNotBlank() }?.let { summary ->
            findings.add(finding(summary.trim(), "AI summary", DossierConfidence.MEDIUM, "Summary"))
        } ?: meta["ai_summary"]?.takeIf { it.isNotBlank() }?.lines()?.filter { it.isNotBlank() }?.take(3)?.forEach { line ->
            findings.add(finding(line.trim(), "AI summary", DossierConfidence.MEDIUM, "Note"))
        }
        findings.add(finding(
            "Informational only — not legal advice. Verify anything important yourself.",
            "SixDegrees", DossierConfidence.LOW, "Disclaimer", isWarning = true
        ))
        return findings
    }

    fun computeShadyScore(meta: Map<String, String>, type: String): ShadyScore {
        var score = 0
        val flags = mutableListOf<String>()

        val breachCount = meta["hibp_breach_count"]?.toIntOrNull() ?: 0
        if (breachCount > 0) { score += minOf(breachCount * 10, 30); flags.add("$breachCount breach${if (breachCount != 1) "es" else ""}") }

        val arrested = meta["arrest_count"]?.toIntOrNull() ?: 0
        if (arrested > 0) { score += minOf(arrested * 15, 40); flags.add("$arrested arrest record${if (arrested != 1) "s" else ""}") }

        val ipqueryRisk = meta["ipquery_risk_score"]?.toIntOrNull() ?: 0
        if (ipqueryRisk > 30) { score += minOf(ipqueryRisk / 2, 30); flags.add("risk score $ipqueryRisk") }

        val ipqsIpFraud = meta["ipqs_ip_fraud_score"]?.toIntOrNull() ?: 0
        if (ipqsIpFraud > 30) { score += minOf(ipqsIpFraud / 2, 30); flags.add("IP fraud $ipqsIpFraud") }

        val ipqsEmailFraud = meta["ipqs_email_fraud_score"]?.toIntOrNull() ?: 0
        if (ipqsEmailFraud > 50) { score += minOf(ipqsEmailFraud / 3, 20); flags.add("email fraud $ipqsEmailFraud") }

        val ipqsPhoneFraud = meta["ipqs_phone_fraud_score"]?.toIntOrNull() ?: 0
        if (ipqsPhoneFraud > 50) { score += minOf(ipqsPhoneFraud / 3, 20); flags.add("phone fraud $ipqsPhoneFraud") }

        if (meta["emailrep_suspicious"]?.toBooleanStrictOrNull() == true) { score += 20; flags.add("suspicious email") }

        val otxPulses = meta["otx_pulse_count"]?.toIntOrNull() ?: 0
        if (otxPulses > 0) { score += minOf(otxPulses * 5, 25); flags.add("$otxPulses threat intel hit${if (otxPulses != 1) "s" else ""}") }

        val sanctionsHits = meta["opensanctions_total"]?.toIntOrNull() ?: 0
        if (sanctionsHits > 0) { score += minOf(sanctionsHits * 20, 40); flags.add("$sanctionsHits sanctions hit${if (sanctionsHits != 1) "s" else ""}") }

        val pasteCount = meta["paste_count"]?.toIntOrNull() ?: 0
        if (pasteCount > 0) { score += minOf(pasteCount * 8, 24); flags.add("$pasteCount paste dump${if (pasteCount != 1) "s" else ""}") }

        val ahmiaHits = meta["ahmia_count"]?.toIntOrNull() ?: 0
        if (ahmiaHits > 0) { score += minOf(ahmiaHits * 10, 25); flags.add("dark web index hit${if (ahmiaHits != 1) "s" else ""}") }

        val darkLinkCount = (meta["darksearch_links"] ?: meta["darksearch_dark_links"])
            ?.lines()?.count { it.isNotBlank() } ?: 0
        if (darkLinkCount > 0) { score += minOf(darkLinkCount * 8, 20); flags.add("$darkLinkCount dark web mention${if (darkLinkCount != 1) "s" else ""}") }

        val courtCases = meta["court_case_count"]?.toIntOrNull() ?: meta["arrest_count"]?.toIntOrNull() ?: 0
        if (courtCases > 0 && arrested == 0) { score += minOf(courtCases * 12, 30); flags.add("court record${if (courtCases != 1) "s" else ""}") }

        score = minOf(score, 100)

        val (verdict, detail) = when {
            score == 0 -> "LIKELY OK" to "No red flags in public or indexed sources — informational only"
            score < 30 -> "LOW CONCERN" to flags.joinToString(" · ").ifBlank { "Minor signals — use your judgment" }
            score < 60 -> "RED FLAGS" to flags.joinToString(" · ")
            else -> "HIGH CONCERN" to flags.joinToString(" · ")
        }
        return ShadyScore(
            score = score,
            verdict = verdict,
            detail = detail,
            displayValue = if (score == 0) "✓" else score.toString()
        )
    }

    private fun buildLegacyFallback(meta: Map<String, String>): DossierSection {
        val findings = meta.entries
            .filter { it.value.isNotBlank() && !it.key.startsWith("_") }
            .take(40)
            .map { (k, v) -> finding(v, sourceFromKey(k), confidenceFromKey(k), label = k.replace('_', ' ')) }
        return DossierSection("data", "Report Data", "▸", findings)
    }

    private fun buildIdentitySection(meta: Map<String, String>): DossierSection {
        val findings = mutableListOf<DossierFinding>()
        extractBestAge(meta)?.let { findings.add(finding(it, "Demographics", DossierConfidence.MEDIUM, "Age")) }
        listOf("person_dob", "comp_dob", "pipl_dob").firstNotNullOfOrNull { meta[it]?.takeIf { d -> d.isNotBlank() } }
            ?.let { findings.add(finding(it, "Records", DossierConfidence.HIGH, "Date of Birth")) }
        meta["demographics_gender"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Demographics", DossierConfidence.MEDIUM, "Gender")) }
        meta["pipl_gender"]?.takeIf { meta["demographics_gender"].isNullOrBlank() && it.isNotBlank() }
            ?.let { findings.add(finding(it, "Pipl", DossierConfidence.HIGH, "Gender")) }
        meta["demographics_nationality"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Demographics", DossierConfidence.LOW, "Nationality Est.")) }
        meta["pipl_nationalities"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Pipl", DossierConfidence.HIGH, "Nationalities")) }
        meta["pipl_aliases"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Pipl", DossierConfidence.HIGH, "Known Aliases")) }
        meta["ftn_birth_year"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding("~$it", "FastPeopleSearch", DossierConfidence.MEDIUM, "Birth Year")) }

        val nameSources = listOf("tps_names" to "TruePeopleSearch", "fps_names" to "FastPeopleSearch", "tt_names" to "ThatsThem")
        nameSources.forEach { (key, source) ->
            meta[key]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { name ->
                findings.add(finding(name, source, DossierConfidence.MEDIUM, "Matched Name"))
            }
        }

        meta["wikipedia_extract"]?.lines()?.filter { it.isNotBlank() }?.take(3)?.forEach { line ->
            findings.add(finding(line.trim(), "Wikipedia", DossierConfidence.MEDIUM, "Bio"))
        }
        meta["ddg_abstract"]?.lines()?.filter { it.isNotBlank() }?.take(3)?.forEach { line ->
            findings.add(finding(line.trim(), "DuckDuckGo", DossierConfidence.LOW, "Web Summary"))
        }
        meta["ddg_infobox"]?.lines()?.filter { it.isNotBlank() }?.take(4)?.forEach { line ->
            findings.add(finding(line.trim(), "DuckDuckGo", DossierConfidence.LOW, "Profile Detail"))
        }

        if (findings.isEmpty()) {
            findings.add(finding("No identity data found for this subject", "SixDegrees", DossierConfidence.LOW))
        }
        return DossierSection("identity", "Identity", "◈", findings)
    }

    private fun buildLocationsSection(meta: Map<String, String>, city: String, state: String, geoLabel: String): DossierSection {
        val allAddresses = extractAddresses(meta).toList()
        val filtered = if (city.isBlank() && state.isBlank()) allAddresses else SubjectFilter.filterByGeo(allAddresses, city, state)
        val skipped = mutableListOf<String>()
        skipped.addAll(extractRejectionNotes(meta, geoLabel))

        if (city.isNotBlank() || state.isNotBlank()) {
            val rejected = allAddresses.filter { it !in filtered.toSet() }
            rejected.forEach { addr ->
                skipped.add("Skipped — outside $geoLabel: $addr")
            }
        }

        val findings = mutableListOf<DossierFinding>()
        meta["person_location"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Search Input", DossierConfidence.HIGH, "Target Location")) }
        meta["person_entered_address"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Search Input", DossierConfidence.HIGH, "Entered Address")) }

        filtered.forEach { addr ->
            val source = inferAddressSource(meta, addr)
            findings.add(finding(addr, source, confidenceFromKey(source.lowercase().replace(" ", "")), "Address"))
        }

        if (findings.isEmpty() && skipped.isEmpty()) {
            findings.add(finding("No location data matched your search area", "SixDegrees", DossierConfidence.LOW))
        }
        return DossierSection("locations", "Locations", "⌂", findings, skipped.distinct())
    }

    private fun buildContactSection(meta: Map<String, String>): DossierSection {
        val findings = mutableListOf<DossierFinding>()
        extractPhones(meta).forEach { phone ->
            val source = inferPhoneSource(meta, phone)
            val srcKey = inferPhoneMetaKey(meta, phone)
            findings.add(finding(
                phone, source, confidenceFromKey(source), "Phone",
                sourceUrl = srcKey?.let { sourceUrlFromMeta(meta, it) }
            ))
        }
        meta["company_phone"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { phone ->
            findings.add(finding(
                phone, "Company registry", DossierConfidence.MEDIUM, "Company Phone",
                sourceUrl = sourceUrlFromMeta(meta, "company_phone")
            ))
        }
        extractEmails(meta).forEach { email ->
            val srcKey = inferEmailMetaKey(meta, email)
            findings.add(finding(
                email, inferEmailSource(meta, email), DossierConfidence.HIGH, "Email",
                sourceUrl = srcKey?.let { sourceUrlFromMeta(meta, it) }
            ))
        }
        meta["voter_names"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { name ->
            findings.add(finding(name, "Voter Records", DossierConfidence.HIGH, "Registered As"))
        }
        meta["voter_addresses"]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { addr ->
            findings.add(finding(addr, "Voter Records", DossierConfidence.HIGH, "Registration Address"))
        }
        meta["voter_party"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Voter Records", DossierConfidence.HIGH, "Party")) }

        if (findings.isEmpty()) {
            findings.add(finding("No contact data found", "SixDegrees", DossierConfidence.LOW))
        }
        return DossierSection("contact", "Contact", "☎", findings)
    }

    private fun buildFamilySection(meta: Map<String, String>): DossierSection {
        val findings = mutableListOf<DossierFinding>()
        extractRelatives(meta).forEach { rel ->
            findings.add(
                finding(
                    value = "pivot://person/$rel",
                    source = inferRelativeSource(meta, rel),
                    confidence = DossierConfidence.MEDIUM,
                    label = "Associate",
                    isPivot = true
                )
            )
        }
        meta["dork_relatives_results"]?.split("\n---\n")?.filter { it.isNotBlank() }?.take(8)?.forEach { block ->
            findings.add(finding(block.trim(), "Auto-Dork", DossierConfidence.LOW, "Relatives Intel"))
        }
        if (findings.isEmpty()) {
            findings.add(finding("No family or associates found", "SixDegrees", DossierConfidence.LOW))
        }
        return DossierSection("family", "Family & Associates", "👥", findings)
    }

    private fun buildEmploymentSection(meta: Map<String, String>): DossierSection {
        val findings = mutableListOf<DossierFinding>()
        meta["pipl_employment"]?.lines()?.filter { it.isNotBlank() }?.forEach { job ->
            findings.add(finding(job, "Pipl", DossierConfidence.HIGH, "Employment"))
        }
        meta["pdl_employment"]?.lines()?.filter { it.isNotBlank() }?.forEach { job ->
            findings.add(finding(job, "People Data Labs", DossierConfidence.HIGH, "Employment"))
        }
        meta["pdl_job_title"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "People Data Labs", DossierConfidence.HIGH, "Job Title")) }
        meta["pdl_company"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "People Data Labs", DossierConfidence.HIGH, "Company")) }
        meta["clearbit_person_title"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Clearbit", DossierConfidence.HIGH, "Title")) }
        meta["clearbit_person_company"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Clearbit", DossierConfidence.HIGH, "Company")) }
        meta["wikidata_employers"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Wikidata", DossierConfidence.MEDIUM, "Employers")) }
        meta["corpwiki_person_companies"]?.lines()?.filter { it.isNotBlank() }?.forEach { co ->
            findings.add(finding(co, "OpenCorporates", DossierConfidence.MEDIUM, "Company"))
        }
        meta["officer_details"]?.lines()?.filter { it.isNotBlank() }?.forEach { role ->
            findings.add(finding(role, "Corporate Filings", DossierConfidence.HIGH, "Corporate Role"))
        }
        meta["sec_person_entities"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "SEC EDGAR", DossierConfidence.HIGH, "SEC Affiliations")) }
        if (findings.isEmpty()) {
            findings.add(finding("No employment or company records found", "SixDegrees", DossierConfidence.LOW))
        }
        return DossierSection("employment", "Employment & Companies", "🏢", findings)
    }

    private fun buildLegalSection(meta: Map<String, String>): DossierSection {
        val findings = mutableListOf<DossierFinding>()
        val arrestCount = meta["arrest_count"]?.toIntOrNull() ?: 0
        if (arrestCount > 0) {
            findings.add(finding("$arrestCount record${if (arrestCount != 1) "s" else ""}", "Arrest Database", DossierConfidence.HIGH, "Arrests on File", isWarning = true))
            meta["arrest_records"]?.lines()?.filter { it.isNotBlank() }?.forEach { rec ->
                findings.add(finding(rec, "Arrest Database", DossierConfidence.HIGH, "Arrest Record", isWarning = true))
            }
        }
        val courtCount = meta["courtlistener_count"]?.toIntOrNull() ?: 0
        if (courtCount > 0) {
            findings.add(finding("$courtCount case${if (courtCount != 1) "s" else ""}", "CourtListener", DossierConfidence.HIGH, "Court Cases"))
            meta["courtlistener_link"]?.let { findings.add(finding(it, "CourtListener", DossierConfidence.HIGH, "View Cases", isLink = true)) }
        }
        meta["judyrecords_cases"]?.lines()?.filter { it.isNotBlank() }?.forEach { case ->
            findings.add(finding(case, "JudyRecords", DossierConfidence.HIGH, "Court Case"))
        }
        val sanctions = meta["opensanctions_total"]?.toIntOrNull() ?: 0
        if (sanctions > 0) {
            findings.add(finding("$sanctions match${if (sanctions != 1) "es" else ""}", "OpenSanctions", DossierConfidence.HIGH, "Sanctions / PEP", isWarning = true))
            meta["opensanctions_names"]?.let { findings.add(finding(it, "OpenSanctions", DossierConfidence.HIGH, "Matched Names", isWarning = true)) }
        }
        meta["dork_criminal_results"]?.split("\n---\n")?.filter { it.isNotBlank() }?.take(8)?.forEach { block ->
            findings.add(finding(block.trim(), "Auto-Dork", DossierConfidence.LOW, "Criminal Intel", isWarning = true))
        }
        meta["dork_court_results"]?.split("\n---\n")?.filter { it.isNotBlank() }?.take(8)?.forEach { block ->
            findings.add(finding(block.trim(), "Auto-Dork", DossierConfidence.LOW, "Court Intel"))
        }
        if (findings.isEmpty()) {
            findings.add(finding("No legal or court records found", "SixDegrees", DossierConfidence.LOW))
        }
        return DossierSection("legal", "Legal & Courts", "⚖", findings)
    }

    private fun buildDigitalSection(meta: Map<String, String>): DossierSection {
        val findings = mutableListOf<DossierFinding>()
        meta["found_urls"]?.lines()?.filter { it.isNotBlank() }?.take(15)?.forEach { line ->
            val isNsfw = line.startsWith("⚠NSFW:")
            val clean = if (isNsfw) line.removePrefix("⚠NSFW:") else line
            val parts = clean.split(": ", limit = 2)
            val site = parts.firstOrNull()?.trim().orEmpty()
            val url = parts.getOrNull(1) ?: clean
            findings.add(finding(url, site.ifBlank { "Profile Scan" }, DossierConfidence.MEDIUM, site.ifBlank { "Profile" }, isLink = true, isWarning = isNsfw))
        }
        listOf("sherlock_found" to "Sherlock", "sherlock_name_found" to "Sherlock", "maigret_found" to "Maigret").forEach { (key, source) ->
            meta[key]?.lines()?.filter { it.isNotBlank() }?.take(8)?.forEach { line ->
                val parts = line.split(": ", limit = 2)
                findings.add(finding(parts.getOrNull(1) ?: line, source, DossierConfidence.MEDIUM, parts.firstOrNull() ?: "Profile", isLink = parts.getOrNull(1)?.startsWith("http") == true))
            }
        }
        meta["search_social_links"]?.lines()?.filter { it.isNotBlank() }?.take(8)?.forEach { link ->
            findings.add(finding(link, "Web Search", DossierConfidence.LOW, "Social Link", isLink = link.startsWith("http")))
        }
        meta["github_name"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "GitHub", DossierConfidence.MEDIUM, "GitHub Profile")) }
        meta["github_stats"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "GitHub", DossierConfidence.MEDIUM, "GitHub Activity")) }
        meta["holehe_services"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { svc ->
            findings.add(finding(svc, "Holehe", DossierConfidence.MEDIUM, "Email Registered"))
        }
        meta["dork_social_results"]?.split("\n---\n")?.filter { it.isNotBlank() }?.take(6)?.forEach { block ->
            findings.add(finding(block.trim(), "Auto-Dork", DossierConfidence.LOW, "Social Trace"))
        }
        if (findings.isEmpty()) {
            findings.add(finding("No digital footprint found", "SixDegrees", DossierConfidence.LOW))
        }
        return DossierSection("digital", "Digital Footprint", "◎", findings)
    }

    private fun buildVehiclesSection(meta: Map<String, String>): DossierSection {
        val findings = mutableListOf<DossierFinding>()
        meta["dork_vehicle_results"]?.split("\n---\n")?.filter { it.isNotBlank() }?.take(10)?.forEach { block ->
            findings.add(finding(block.trim(), "Auto-Dork", DossierConfidence.LOW, "Vehicle Record"))
        }
        listOf("vehicle_records", "vehicle_plates", "vehicle_makes", "vehicle_models").forEach { key ->
            meta[key]?.lines()?.filter { it.isNotBlank() }?.forEach { line ->
                findings.add(finding(line.trim(), sourceFromKey(key), confidenceFromKey(key), "Vehicle"))
            }
        }
        if (findings.isEmpty()) {
            findings.add(finding("No vehicle records found", "SixDegrees", DossierConfidence.LOW))
        }
        return DossierSection("vehicles", "Vehicles", "🚗", findings)
    }

    private fun buildDarkWebSection(meta: Map<String, String>): DossierSection {
        val findings = mutableListOf<DossierFinding>()
        meta["darkweb_search_terms"]?.takeIf { it.isNotBlank() }?.let { terms ->
            findings.add(finding(terms, "SixDegrees", DossierConfidence.MEDIUM, "Searched Terms"))
        }
        meta["darksearch_snippet"]?.lines()?.filter { it.isNotBlank() }?.take(6)?.forEach { line ->
            findings.add(finding(line.trim(), "DarkSearch", DossierConfidence.MEDIUM, "Indexed Mention", isWarning = true))
        }
        (meta["darksearch_links"] ?: meta["darksearch_dark_links"])?.lines()?.filter { it.isNotBlank() }?.take(8)?.forEach { link ->
            findings.add(finding(link, "DarkSearch", DossierConfidence.MEDIUM, ".onion / Index Link", isLink = true, isWarning = true))
        }
        meta["ahmia_count"]?.toIntOrNull()?.takeIf { it > 0 }?.let { count ->
            val viaTor = meta["ahmia_via_tor"]?.toBooleanStrictOrNull() == true
            findings.add(finding(
                "$count Ahmia hit${if (count != 1) "s" else ""}${if (viaTor) " (via Tor)" else ""}",
                "Ahmia", DossierConfidence.MEDIUM, "Tor Index", isWarning = true
            ))
        }
        meta["ahmia_titles"]?.lines()?.filter { it.isNotBlank() }?.take(6)?.zip(
            meta["ahmia_urls"]?.lines()?.filter { it.isNotBlank() }?.take(6).orEmpty()
        )?.forEach { (title, url) ->
            findings.add(finding(url.ifBlank { title }, "Ahmia", DossierConfidence.MEDIUM, title.take(80), isLink = url.isNotBlank(), isWarning = true))
        }
        meta["dork_dark_results"]?.split("\n---\n")?.filter { it.isNotBlank() }?.take(6)?.forEach { block ->
            findings.add(finding(block.trim(), "Auto-Dork", DossierConfidence.LOW, "Dark Web Intel", isWarning = true))
        }
        meta["dork_leaks_results"]?.split("\n---\n")?.filter { it.isNotBlank() }?.take(6)?.forEach { block ->
            findings.add(finding(block.trim(), "Auto-Dork", DossierConfidence.LOW, "Leaked Data", isWarning = true))
        }
        val breachCount = meta["hibp_breach_count"]?.toIntOrNull() ?: 0
        if (breachCount > 0) {
            findings.add(finding("$breachCount breach${if (breachCount != 1) "es" else ""}", "Have I Been Pwned", DossierConfidence.HIGH, "Breach Exposure", isWarning = true))
        }
        val pasteCount = meta["paste_count"]?.toIntOrNull() ?: 0
        if (pasteCount > 0) {
            findings.add(finding("$pasteCount paste dump${if (pasteCount != 1) "s" else ""}", "Paste Sites", DossierConfidence.MEDIUM, "Paste Exposure", isWarning = true))
        }
        if (findings.isEmpty()) {
            findings.add(finding("No indexed dark web or breach exposure found for this subject", "SixDegrees", DossierConfidence.LOW))
        }
        return DossierSection("darkweb", "Dark Web", "🕳", findings)
    }

    private fun buildAiSection(meta: Map<String, String>): DossierSection {
        val findings = mutableListOf<DossierFinding>()
        if (meta["ai_executive_summary"]?.isNotBlank() == true) {
            meta["ai_provider"]?.takeIf { it.isNotBlank() }
                ?.let { findings.add(finding(it.replaceFirstChar { c -> c.uppercase() }, "AI Analysis", DossierConfidence.MEDIUM, "Provider")) }
            meta["ai_executive_summary"]?.let { findings.add(finding(it.trim(), "AI Analysis", DossierConfidence.MEDIUM, "Executive Summary")) }
            meta["ai_key_findings"]?.lines()?.filter { it.isNotBlank() }?.forEach { line ->
                findings.add(finding(line.trim(), "AI Analysis", DossierConfidence.MEDIUM, "Key Finding"))
            }
            meta["ai_confidence"]?.takeIf { it.isNotBlank() }?.let { conf ->
                val rationale = meta["ai_confidence_rationale"]?.takeIf { it.isNotBlank() }
                findings.add(finding(if (rationale != null) "$conf — $rationale" else conf, "AI Analysis", DossierConfidence.MEDIUM, "AI Confidence"))
            }
            meta["ai_false_positives"]?.lines()?.filter { it.isNotBlank() }?.forEach { line ->
                findings.add(finding(line.trim(), "AI Analysis", DossierConfidence.LOW, "False Positive"))
            }
            meta["ai_next_steps"]?.lines()?.filter { it.isNotBlank() }?.forEach { line ->
                findings.add(finding(line.trim(), "AI Analysis", DossierConfidence.MEDIUM, "Next Step"))
            }
            meta["ai_suggested_searches"]?.lines()?.filter { it.isNotBlank() }?.forEach { line ->
                val colonIdx = line.indexOf(": http")
                if (colonIdx > 0) {
                    val label = line.substring(0, colonIdx)
                    val url = line.substring(colonIdx + 2)
                    findings.add(finding(url, "AI Suggested Search", DossierConfidence.MEDIUM, label, isLink = true))
                }
            }
        } else {
            meta["ai_summary"]?.takeIf { it.isNotBlank() }?.lines()?.filter { it.isNotBlank() }?.forEach { line ->
                findings.add(finding(line.trim(), "AI Analysis", DossierConfidence.MEDIUM, "Brief"))
            }
        }
        if (findings.isEmpty()) {
            findings.add(finding("No AI brief available for this report", "SixDegrees", DossierConfidence.LOW))
        } else {
            findings.add(finding("AI-generated synthesis — verify all claims independently.", "SixDegrees", DossierConfidence.LOW, "Disclaimer", isWarning = true))
        }
        return DossierSection("ai", "AI Brief", "✦", findings)
    }

    private fun extractRejectionNotes(meta: Map<String, String>, geoLabel: String): List<String> {
        val notes = mutableListOf<String>()
        meta.forEach { (key, value) ->
            if (value.isBlank()) return@forEach
            val k = key.lowercase()
            val isRejectionKey = k.contains("reject") || k.contains("skipped") || k.contains("filtered_out") || k.contains("geo_filter")
            if (isRejectionKey) {
                notes.add(formatRejectionNote(key, value, geoLabel))
            }
        }
        meta["geo_rejected"]?.lines()?.filter { it.isNotBlank() }?.forEach { line ->
            notes.add(if (line.startsWith("Skipped")) line else "Skipped — outside $geoLabel: $line")
        }
        return notes.distinct()
    }

    private fun formatRejectionNote(key: String, value: String, geoLabel: String): String {
        if (value.startsWith("Skipped", ignoreCase = true)) return value
        val source = sourceFromKey(key)
        return "Skipped — outside $geoLabel ($source): $value"
    }

    private fun finding(
        value: String,
        source: String,
        confidence: DossierConfidence,
        label: String? = null,
        isPivot: Boolean = false,
        isLink: Boolean = false,
        isWarning: Boolean = false,
        sourceUrl: String? = null
    ) = DossierFinding(
        label = label,
        value = value,
        source = source,
        confidence = confidence,
        isPivot = isPivot || value.startsWith("pivot://"),
        isLink = isLink || value.startsWith("http://") || value.startsWith("https://"),
        isWarning = isWarning || (label?.startsWith("⚠") == true),
        sourceUrl = sourceUrl
    )

    private fun sourceUrlFromMeta(meta: Map<String, String>, keyPrefix: String): String? =
        meta["${keyPrefix}_source_url"]?.takeIf { it.startsWith("http") }
            ?: meta["ddg_source_url"]?.takeIf { it.startsWith("http") }

    private fun sourceFromKey(key: String): String {
        val prefix = key.substringBefore('_').lowercase()
        return SOURCE_LABELS[prefix] ?: prefix.replaceFirstChar { it.uppercase() }
    }

    private fun confidenceFromKey(key: String): DossierConfidence {
        val k = key.lowercase()
        return when {
            HIGH_CONFIDENCE.any { k.contains(it) } -> DossierConfidence.HIGH
            MEDIUM_CONFIDENCE.any { k.contains(it) } -> DossierConfidence.MEDIUM
            k.contains("dork") || k.contains("snippet") || k.contains("search") -> DossierConfidence.LOW
            else -> DossierConfidence.MEDIUM
        }
    }

    private fun extractBestAge(meta: Map<String, String>): String? =
        meta["search_age"] ?: meta["tps_age"] ?: meta["zaba_age"] ?: meta["411_age"]
            ?: meta["voter_age"] ?: meta["radaris_age"] ?: meta["peekyou_age"]
            ?: meta["nuwber_age"] ?: meta["wp_age"] ?: meta["fps_age"]
            ?: meta["tt_ages"]?.split(", ")?.firstOrNull()?.trim()
            ?: meta["uspb_age"] ?: meta["demographics_age_estimate"]

    private fun extractPhones(meta: Map<String, String>): LinkedHashSet<String> {
        val tollfree = setOf("800", "888", "877", "866", "855", "844", "833", "822")
        val areaCodeRegex = Regex("^\\((\\d{3})\\)")
        val set = linkedSetOf<String>()
        listOf("person_phone", "pipl_phone", "pipl_phones", "pdl_phones",
            "search_phones", "tps_phones", "zaba_phones", "411_phones", "tt_phones", "uspb_phones", "fps_phones",
            "radaris_phones", "nuwber_phones", "wp_phones").forEach { key ->
            meta[key]?.split(",")?.map { it.trim() }?.filter { phone ->
                phone.isNotBlank() && (areaCodeRegex.find(phone)?.groupValues?.get(1) !in tollfree || areaCodeRegex.find(phone) == null)
            }?.forEach { set.add(it) }
        }
        return set
    }

    private fun extractEmails(meta: Map<String, String>): LinkedHashSet<String> {
        val set = linkedSetOf<String>()
        listOf("pipl_email", "pipl_emails", "pdl_emails", "clearbit_person_email", "radaris_emails", "nuwber_emails", "cse_email_hits")
            .forEach { key ->
                meta[key]?.split(",")?.map { it.trim() }?.filter { it.contains("@") }?.forEach { set.add(it) }
            }
        return set
    }

    private fun extractAddresses(meta: Map<String, String>): LinkedHashSet<String> {
        val set = linkedSetOf<String>()
        meta["person_entered_address"]?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        meta["pipl_addresses"]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { set.add(it) }
        meta["pdl_address"]?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        meta["search_addresses"]?.lines()?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { set.add(it) }
        listOf(
            "tps_locations", "zaba_locations", "411_locations", "voter_addresses", "tt_locations", "tt_addresses",
            "fps_locations", "radaris_locations", "peekyou_locations", "nuwber_locations", "wp_locations"
        ).forEach { key ->
            meta[key]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { set.add(it) }
        }
        return set
    }

    private fun extractRelatives(meta: Map<String, String>): LinkedHashSet<String> {
        val set = linkedSetOf<String>()
        listOf("search_relatives", "pipl_relatives", "pdl_associates", "tps_relatives", "411_relatives", "zaba_relatives",
            "radaris_relatives", "nuwber_relatives", "corpwiki_associates").forEach { key ->
            meta[key]?.split(",")?.map { it.trim() }?.filter { it.length > 3 && it.isNotBlank() }?.forEach { set.add(it) }
        }
        return set
    }

    private fun inferAddressSource(meta: Map<String, String>, addr: String): String {
        val checks = listOf("pipl_addresses" to "Pipl", "pdl_address" to "People Data Labs", "voter_addresses" to "Voter Records",
            "tps_locations" to "TruePeopleSearch", "search_addresses" to "Web Search")
        for ((key, source) in checks) {
            if (meta[key]?.contains(addr, ignoreCase = true) == true) return source
        }
        return "Records"
    }

    private fun inferPhoneSource(meta: Map<String, String>, phone: String): String {
        val digits = phone.filter { it.isDigit() }.takeLast(10)
        listOf("pipl_phones" to "Pipl", "pdl_phones" to "People Data Labs", "tps_phones" to "TruePeopleSearch",
            "search_phones" to "Web Search", "person_phone" to "Search Input").forEach { (key, source) ->
            if (meta[key]?.filter { it.isDigit() }?.contains(digits) == true) return source
        }
        return "Records"
    }

    private fun inferPhoneMetaKey(meta: Map<String, String>, phone: String): String? {
        val digits = phone.filter { it.isDigit() }.takeLast(10)
        listOf("pipl_phones", "pdl_phones", "tps_phones", "search_phones", "person_phone", "pipl_phone").forEach { key ->
            if (meta[key]?.filter { it.isDigit() }?.contains(digits) == true) return key
        }
        return "search_phones"
    }

    private fun inferEmailMetaKey(meta: Map<String, String>, email: String): String? {
        listOf("pipl_emails", "pdl_emails", "clearbit_person_email", "person_email").forEach { key ->
            if (meta[key]?.contains(email, ignoreCase = true) == true) return key.removeSuffix("s")
        }
        return null
    }

    private fun inferEmailSource(meta: Map<String, String>, email: String): String {
        listOf("pipl_emails" to "Pipl", "pdl_emails" to "People Data Labs", "clearbit_person_email" to "Clearbit")
            .forEach { (key, source) -> if (meta[key]?.contains(email, ignoreCase = true) == true) return source }
        return "Records"
    }

    private fun inferRelativeSource(meta: Map<String, String>, name: String): String {
        listOf("pipl_relatives" to "Pipl", "pdl_associates" to "People Data Labs", "tps_relatives" to "TruePeopleSearch")
            .forEach { (key, source) -> if (meta[key]?.contains(name, ignoreCase = true) == true) return source }
        return "Records"
    }
}
