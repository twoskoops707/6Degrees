package com.twoskoops707.sixdegrees.ui.results

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.twoskoops707.sixdegrees.data.AppSettings
import com.twoskoops707.sixdegrees.data.local.entity.OsintReportEntity
import com.twoskoops707.sixdegrees.data.local.entity.PersonEntity
import com.twoskoops707.sixdegrees.data.repository.OsintRepository
import com.twoskoops707.sixdegrees.domain.model.Address
import com.twoskoops707.sixdegrees.domain.model.Employment
import com.twoskoops707.sixdegrees.domain.model.SocialProfile
import com.twoskoops707.sixdegrees.domain.DorkMetadataStore
import com.twoskoops707.sixdegrees.domain.GoogleDorkLibrary
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
    val enrichedMeta: Map<String, String> = emptyMap(),
    val dossierSections: List<DossierSection> = emptyList(),
    val shadyScore: ShadyScore? = null
)

class ResultsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = OsintRepository(app)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

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
            val enriched = PersonDossierEnricher.enrich(meta, person, moshi)
            val searchType = enriched["search_type"] ?: "person"
            val investigator = AppSettings.isInvestigatorMode(getApplication())
            val sections = DossierBuilder.buildSectionsForMode(enriched, searchType, investigator)
            val shady = DossierBuilder.computeShadyScore(enriched, searchType)
            _state.value = ResultsUiState(
                isLoading = false,
                report = report,
                person = person,
                enrichedMeta = enriched,
                dossierSections = sections,
                shadyScore = shady
            )
        }
    }
}

private object PersonDossierEnricher {

    fun enrich(
        meta: MutableMap<String, String>,
        person: PersonEntity?,
        moshi: Moshi
    ): Map<String, String> {
        DossierBuilder.enrichFromPerson(meta, person)
        if (person == null) return meta

        parseAllJobs(person.employmentHistoryJson, moshi).takeIf { it.isNotEmpty() }
            ?.let { meta["pipl_employment"] = it.joinToString("\n") }
        parseAllAddresses(person.addressesJson, moshi).takeIf { it.isNotEmpty() }
            ?.let { meta["pipl_addresses"] = it.joinToString(" | ") }
        parseAllSocials(person.socialProfilesJson, moshi).takeIf { it.isNotEmpty() }
            ?.let { meta["pipl_socials"] = it.joinToString("\n") }
        parseStringList(person.aliasesJson, moshi).takeIf { it.isNotEmpty() }
            ?.let { meta["pipl_aliases"] = it.joinToString(", ") }
        parseStringList(person.nationalitiesJson, moshi).takeIf { it.isNotEmpty() }
            ?.let { meta["pipl_nationalities"] = it.joinToString(", ") }
        return meta
    }

    private fun parseAllJobs(json: String, moshi: Moshi): List<String> = try {
        val type = Types.newParameterizedType(List::class.java, Employment::class.java)
        moshi.adapter<List<Employment>>(type).fromJson(json)
            ?.filter { it.companyName.isNotBlank() || it.jobTitle.isNotBlank() }
            ?.map { employment ->
                buildString {
                    if (employment.jobTitle.isNotBlank()) append(employment.jobTitle)
                    if (employment.companyName.isNotBlank()) {
                        if (isNotEmpty()) append(" at ") else append("Employee at ")
                        append(employment.companyName)
                    }
                    if (employment.isCurrent) append(" (Current)")
                    else if (employment.endDate != null) append(" (until ${employment.endDate})")
                }
            }.orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    private fun parseAllAddresses(json: String, moshi: Moshi): List<String> = try {
        val type = Types.newParameterizedType(List::class.java, Address::class.java)
        moshi.adapter<List<Address>>(type).fromJson(json)
            ?.filter { it.city.isNotBlank() || it.state.isNotBlank() }
            ?.map { address ->
                listOfNotNull(
                    address.street.takeIf { it.isNotBlank() },
                    address.city.takeIf { it.isNotBlank() },
                    address.state.takeIf { it.isNotBlank() },
                    address.postalCode.takeIf { it.isNotBlank() }
                ).joinToString(", ")
            }?.filter { it.isNotBlank() }.orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    private fun parseAllSocials(json: String, moshi: Moshi): List<String> = try {
        val type = Types.newParameterizedType(List::class.java, SocialProfile::class.java)
        moshi.adapter<List<SocialProfile>>(type).fromJson(json)
            ?.filter { !it.url.isNullOrBlank() }
            ?.map { social -> "${social.platform}: ${social.url}" }
            .orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    private fun parseStringList(json: String, moshi: Moshi): List<String> = try {
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        moshi.adapter<List<String>>(type).fromJson(json)?.filter { it.isNotBlank() }.orEmpty()
    } catch (_: Exception) {
        emptyList()
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
        "gitlab" to "GitLab",
        "mastodon" to "Mastodon",
        "bluesky" to "Bluesky",
        "twitter" to "X/Twitter",
        "gleif" to "GLEIF",
        "fbi" to "FBI Wanted",
        "npi" to "NPI Registry",
        "openfec" to "OpenFEC",
        "found_urls" to "Profile Scan"
    )

    private val HIGH_CONFIDENCE = setOf("pipl", "pdl", "voter", "courtlistener", "judyrecords", "opensanctions", "clearbit", "gitlab", "mastodon", "bluesky", "twitter")
    private val MEDIUM_CONFIDENCE = setOf("tps", "zaba", "411", "radaris", "nuwber", "peekyou", "sherlock", "maigret", "hibp", "corpwiki", "sec", "wikipedia", "wikidata", "holehe", "github", "gleif", "fbi", "npi", "openfec")

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
        when (searchType) {
            "phone" -> return buildPhoneSections(meta)
            "email", "breach" -> return buildEmailSections(meta)
            "vehicle", "vin" -> return listOf(buildVehiclesSection(meta), buildAiSection(meta)).filter { !it.isEmpty }
        }
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
            buildGoogleIntelligenceSection(meta, investigatorMode = true),
            buildVehiclesSection(meta),
            buildDarkWebSection(meta),
            buildAiSection(meta)
        )
    }

    fun buildSimpleSections(meta: Map<String, String>, searchType: String): List<DossierSection> {
        when (searchType) {
            "phone" -> return buildPhoneSimpleSections(meta)
            "email", "breach" -> return buildEmailSimpleSections(meta)
        }
        if (searchType !in setOf("person", "scan", "comprehensive")) {
            return listOf(buildLegacyFallback(meta))
        }
        val full = buildSections(meta, searchType)
        val byId = full.associateBy { it.id }

        fun findings(vararg ids: String): List<DossierFinding> =
            ids.flatMap { id -> byId[id]?.findings.orEmpty() }
                .filter { f -> !isEmptyPlaceholder(f) }

        val googleIntel = buildGoogleIntelligenceSection(meta, investigatorMode = false).findings
            .filter { !isEmptyPlaceholder(it) && it.label != "Summary" }
        val who = findings("identity", "employment", "contact", "family") + googleIntel
        val where = byId["locations"]?.findings.orEmpty().filter { !isEmptyPlaceholder(it) }
        val ctx = FindingUrlHelper.subjectContext(meta)
        val flags = (findings("legal", "darkweb").filter { it.isWarning || isRedFlagFinding(it) } +
            buildRiskFlagFindings(meta)).ifEmpty {
            buildLegalSection(meta).findings.filter { it.isWarning || isRedFlagFinding(it) } +
                buildDarkWebSection(meta).findings.filter { it.isWarning || isRedFlagFinding(it) } +
                buildRiskFlagFindings(meta)
        }.distinctBy { it.value + (it.label ?: "") }
        val shady = computeShadyScore(meta, searchType)
        val safe = buildSafeToMeetFindings(meta, shady)
        val verifyLinks = listOf(
            finding(
                "Search ${ctx.name.ifBlank { "subject" }} on CourtListener",
                "CourtListener", DossierConfidence.MEDIUM, "Court records",
                isLink = true, sourceUrl = FindingUrlHelper.courtUrl(ctx)
            ),
            finding(
                "Search ${ctx.name.ifBlank { "subject" }} on OpenSanctions",
                "OpenSanctions", DossierConfidence.MEDIUM, "Sanctions",
                isLink = true, sourceUrl = FindingUrlHelper.opensanctionsUrl(ctx)
            )
        )

        return listOf(
            DossierSection("who", "Who they are", "", who.ifEmpty {
                listOf(finding("We couldn't find much  -  try adding a city or photo", "SixDegrees", DossierConfidence.LOW))
            }),
            DossierSection("where", "Where they've been", "", where.ifEmpty {
                listOf(finding("No location history found in public records", "SixDegrees", DossierConfidence.LOW))
            }),
            DossierSection("flags", "Red flags", "", flags.ifEmpty {
                listOf(finding("Nothing alarming turned up in public or indexed sources", "SixDegrees", DossierConfidence.LOW))
            } + verifyLinks),
            DossierSection("safe", "Risk assessment", "", safe)
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
            shady.score < 30 -> "Minor concerns  -  use your judgment"
            shady.score < 60 -> "Some red flags  -  proceed carefully"
            else -> "Serious concerns  -  trust your instincts"
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
            "Informational only  -  not legal advice. Verify anything important yourself.",
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

        val courtCases = meta["court_case_count"]?.toIntOrNull()
            ?: meta["courtlistener_count"]?.toIntOrNull() ?: 0
        if (courtCases > 0 && arrested == 0) { score += minOf(courtCases * 12, 30); flags.add("court record${if (courtCases != 1) "s" else ""}") }

        score = minOf(score, 100)

        val (verdict, detail) = when {
            score == 0 -> "LIKELY OK" to "No red flags in public or indexed sources  -  informational only"
            score < 30 -> "LOW CONCERN" to flags.joinToString("  |  ").ifBlank { "Minor signals  -  use your judgment" }
            score < 60 -> "RED FLAGS" to flags.joinToString("  |  ")
            else -> "HIGH CONCERN" to flags.joinToString("  |  ")
        }
        return ShadyScore(
            score = score,
            verdict = verdict,
            detail = detail,
            displayValue = if (score == 0) "OK" else score.toString()
        )
    }

    private fun buildPhoneSections(meta: Map<String, String>): List<DossierSection> {
        val intel = mutableListOf<DossierFinding>()
        meta["person_phone"]?.takeIf { it.isNotBlank() }
            ?.let { intel.add(finding(it, "Search Input", DossierConfidence.HIGH, "Number")) }
        listOf(
            "libphone_valid" to "Valid",
            "libphone_carrier" to "Carrier",
            "libphone_location" to "Location",
            "libphone_line_type" to "Line Type",
            "libphone_country" to "Country",
            "numverify_carrier" to "Carrier",
            "numverify_location" to "Location",
            "numverify_line_type" to "Line Type",
            "calltracer_carrier" to "Carrier",
            "calltracer_location" to "Location",
            "calltracer_spam_score" to "Spam Score",
            "calltracer_spam_reports" to "Spam Reports"
        ).forEach { (key, label) ->
            meta[key]?.takeIf { it.isNotBlank() }?.let {
                intel.add(finding(it, sourceFromKey(key), confidenceFromKey(key), label))
            }
        }
        meta["phone_search_snippets"]?.lines()?.filter { it.isNotBlank() }?.take(6)?.forEach { line ->
            intel.add(finding(line.trim(), "Web Search", DossierConfidence.MEDIUM, "Mention"))
        }
        meta["800notes_snippet"]?.takeIf { it.isNotBlank() }
            ?.let { intel.add(finding(it, "800notes", DossierConfidence.MEDIUM, "Caller Reports")) }
        extractMatchedNames(meta).forEach { (name, source) ->
            intel.add(finding(name, source, DossierConfidence.MEDIUM, "Possible Owner"))
        }
        meta["uspb_addresses"]?.split(" | ", ",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { addr ->
            intel.add(finding(addr, "USPhoneBook", DossierConfidence.MEDIUM, "Address"))
        }
        meta["numverify_valid"]?.takeIf { it.isNotBlank() }
            ?.let { intel.add(finding(if (it == "true") "Yes" else "No", "Numverify", DossierConfidence.HIGH, "Valid")) }
        meta["numverify_country"]?.takeIf { it.isNotBlank() }
            ?.let { intel.add(finding(it, "Numverify", DossierConfidence.MEDIUM, "Country")) }
        meta["ai_executive_summary"]?.lines()?.filter { it.isNotBlank() }?.take(3)?.forEach { line ->
            intel.add(finding(line.trim(), "AI Brief", DossierConfidence.MEDIUM, "Summary"))
        }
        val contact = buildContactSection(meta)
        val legal = buildLegalSection(meta)
        val dark = buildDarkWebSection(meta)
        return listOf(
            DossierSection(
                "phone", "Phone Intel", "",
                intel.ifEmpty { listOf(finding("No phone-specific findings yet", "SixDegrees", DossierConfidence.LOW)) }
            ),
            contact,
            legal,
            dark,
            buildAiSection(meta)
        ).filter { !it.isEmpty }
    }

    private fun buildPhoneSimpleSections(meta: Map<String, String>): List<DossierSection> {
        val full = buildPhoneSections(meta)
        val intel = full.firstOrNull { it.id == "phone" }?.findings.orEmpty()
        val owners = intel.filter { it.label == "Possible Owner" || it.label == "Matched Name" }
        val carrier = intel.filter {
            it.label in setOf("Carrier", "Location", "Line Type", "Valid", "Spam Score", "Spam Reports")
        }
        val mentions = intel.filter { it.label in setOf("Mention", "Caller Reports", "Summary") }
        val flags = full.flatMap { it.findings }.filter { it.isWarning || isRedFlagFinding(it) }
        return listOf(
            DossierSection("who", "Who owns this number?", "", owners.ifEmpty {
                mentions.take(3).ifEmpty {
                    listOf(finding("No owner name found in public records", "SixDegrees", DossierConfidence.LOW))
                }
            }),
            DossierSection("carrier", "Carrier & location", "", carrier.ifEmpty {
                listOf(finding("Carrier details not available", "SixDegrees", DossierConfidence.LOW))
            }),
            DossierSection("flags", "Red flags", "", flags.ifEmpty {
                listOf(finding("Nothing alarming turned up for this number", "SixDegrees", DossierConfidence.LOW))
            }),
            DossierSection("mentions", "What people say", "", mentions.ifEmpty {
                listOf(finding("No public comments found for this number", "SixDegrees", DossierConfidence.LOW))
            })
        )
    }

    private fun buildEmailSections(meta: Map<String, String>): List<DossierSection> =
        listOf(buildContactSection(meta), buildDigitalSection(meta), buildLegalSection(meta), buildAiSection(meta))
            .filter { !it.isEmpty }

    private fun buildEmailSimpleSections(meta: Map<String, String>): List<DossierSection> {
        val contact = buildContactSection(meta)
        val digital = buildDigitalSection(meta)
        val legal = buildLegalSection(meta)
        return listOf(
            DossierSection("email", "Email profile", "", contact.findings.ifEmpty {
                listOf(finding("No email profile data found", "SixDegrees", DossierConfidence.LOW))
            }),
            DossierSection("digital", "Online footprint", "", digital.findings.ifEmpty {
                listOf(finding("No linked accounts found", "SixDegrees", DossierConfidence.LOW))
            }),
            DossierSection("flags", "Red flags", "", legal.findings.filter { it.isWarning || isRedFlagFinding(it) }.ifEmpty {
                listOf(finding("No breaches or legal hits found", "SixDegrees", DossierConfidence.LOW))
            })
        )
    }

    private fun buildLegacyFallback(meta: Map<String, String>): DossierSection {
        val findings = meta.entries
            .filter { it.value.isNotBlank() && !it.key.startsWith("_") }
            .take(40)
            .map { (k, v) -> finding(v, sourceFromKey(k), confidenceFromKey(k), label = k.replace('_', ' ')) }
        return DossierSection("data", "Report Data", "", findings)
    }

    private fun buildIdentitySection(meta: Map<String, String>): DossierSection {
        val findings = mutableListOf<DossierFinding>()
        (meta["username"] ?: meta["field_username"])?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Search Input", DossierConfidence.HIGH, "Username")) }
        extractBestAge(meta)?.let { findings.add(finding(it, "Demographics", DossierConfidence.MEDIUM, "Age")) }
        listOf("person_dob", "comp_dob", "pipl_dob").firstNotNullOfOrNull { meta[it]?.takeIf { d -> d.isNotBlank() } }
            ?.let { findings.add(finding(it, "Records", DossierConfidence.HIGH, "Date of Birth")) }
        meta["demographics_gender"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Demographics", DossierConfidence.MEDIUM, "Gender")) }
        meta["pipl_gender"]?.takeIf { meta["demographics_gender"].isNullOrBlank() && it.isNotBlank() }
            ?.let { findings.add(finding(it, "Pipl", DossierConfidence.HIGH, "Gender")) }
        meta["pipl_nationalities"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Pipl", DossierConfidence.MEDIUM, "Nationalities (verify independently)")) }
        meta["pipl_aliases"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Pipl", DossierConfidence.HIGH, "Known Aliases")) }
        meta["ftn_birth_year"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding("~$it", "FastPeopleSearch", DossierConfidence.MEDIUM, "Birth Year")) }

        extractMatchedNames(meta).forEach { (name, source) ->
            findings.add(finding(name, source, DossierConfidence.MEDIUM, "Matched Name"))
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

        // Social platform profiles from free keyless APIs
        listOf(
            "github_name" to "GitHub",
            "gitlab_name" to "GitLab",
            "mastodon_name" to "Mastodon",
            "bluesky_name" to "Bluesky",
            "twitter_name" to "X/Twitter"
        ).forEach { (key, source) ->
            meta[key]?.takeIf { it.isNotBlank() }?.let {
                findings.add(finding(it, source, DossierConfidence.MEDIUM, "Display Name"))
            }
        }
        // Social bios
        listOf(
            "mastodon_bio" to "Mastodon",
            "bluesky_bio" to "Bluesky",
            "twitter_bio" to "X/Twitter"
        ).forEach { (key, source) ->
            meta[key]?.takeIf { it.isNotBlank() }?.take(200)?.let {
                findings.add(finding(it, source, DossierConfidence.MEDIUM, "Bio"))
            }
        }
        // Social stats
        listOf(
            "github_stats" to "GitHub",
            "gitlab_stats" to "GitLab",
            "mastodon_stats" to "Mastodon",
            "bluesky_stats" to "Bluesky",
            "twitter_stats" to "X/Twitter"
        ).forEach { (key, source) ->
            meta[key]?.takeIf { it.isNotBlank() }?.let {
                findings.add(finding(it, source, DossierConfidence.LOW, "Stats"))
            }
        }
        // Social locations
        listOf(
            "github_location" to "GitHub",
            "gitlab_location" to "GitLab",
            "twitter_location" to "X/Twitter"
        ).forEach { (key, source) ->
            meta[key]?.takeIf { it.isNotBlank() }?.let {
                findings.add(finding(it, source, DossierConfidence.MEDIUM, "Location"))
            }
        }
        // Government/registry data from free keyless APIs
        meta["courtlistener_count"]?.takeIf { it.isNotBlank() && it != "0" }?.let {
            findings.add(finding("$it case(s) found", "CourtListener", DossierConfidence.HIGH, "Court Records"))
        }
        meta["sec_person_entities"]?.takeIf { it.isNotBlank() }?.let {
            findings.add(finding(it, "SEC EDGAR", DossierConfidence.HIGH, "Insider Filings"))
        }
        meta["gleif_entities"]?.takeIf { it.isNotBlank() }?.let {
            findings.add(finding(it, "GLEIF", DossierConfidence.MEDIUM, "Legal Entities"))
        }
        meta["npi_providers"]?.takeIf { it.isNotBlank() }?.let {
            findings.add(finding(it, "NPI Registry", DossierConfidence.HIGH, "Healthcare Provider"))
        }
        meta["fbi_wanted_matches"]?.takeIf { it.isNotBlank() }?.let {
            findings.add(finding(it, "FBI Wanted", DossierConfidence.HIGH, "FBI Match", isWarning = true))
        }
        meta["opensanctions_total"]?.takeIf { it.isNotBlank() && it != "0" }?.let {
            findings.add(finding("$it match(es)", "OpenSanctions", DossierConfidence.HIGH, "Sanctions", isWarning = true))
        }

        if (findings.isEmpty()) {
            findings.add(finding("No identity data found for this subject", "SixDegrees", DossierConfidence.LOW))
        }
        return DossierSection("identity", "Identity", "", findings)
    }

    private fun buildLocationsSection(meta: Map<String, String>, city: String, state: String, geoLabel: String): DossierSection {
        val allAddresses = extractAddresses(meta).toList()
        val filtered = if (city.isBlank() && state.isBlank()) allAddresses else SubjectFilter.filterByGeo(allAddresses, city, state)
        val skipped = mutableListOf<String>()
        skipped.addAll(extractRejectionNotes(meta, geoLabel))

        if (city.isNotBlank() || state.isNotBlank()) {
            val rejected = allAddresses.filter { it !in filtered.toSet() }
            rejected.forEach { addr ->
                skipped.add("Skipped  -  outside $geoLabel: $addr")
            }
        }

        val findings = mutableListOf<DossierFinding>()
        meta["person_location"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Search Input", DossierConfidence.HIGH, "Target Location")) }
        meta["person_entered_address"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Search Input", DossierConfidence.HIGH, "Entered Address")) }

        filtered.forEach { addr ->
            val source = inferAddressSource(meta, addr)
            val ctx = FindingUrlHelper.subjectContext(meta)
            findings.add(finding(
                addr, source, confidenceFromKey(source.lowercase().replace(" ", "")), "Address",
                sourceUrl = FindingUrlHelper.locationUrl(addr, ctx)
            ))
        }

        if (findings.isEmpty() && skipped.isEmpty()) {
            findings.add(finding("No location data matched your search area", "SixDegrees", DossierConfidence.LOW))
        }
        return DossierSection("locations", "Locations", "", findings, skipped.distinct())
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
        return DossierSection("contact", "Contact", "", findings)
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
        return DossierSection("family", "Family & Associates", "", findings)
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
        meta["sec_fulltext_entities"]?.takeIf { it.isNotBlank() && meta["sec_person_entities"].isNullOrBlank() }
            ?.let { findings.add(finding(it, "SEC EDGAR", DossierConfidence.HIGH, "SEC Filings")) }
        meta["sec_fulltext_forms"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "SEC EDGAR", DossierConfidence.MEDIUM, "Filing Types")) }
        meta["sec_filings_count"]?.toIntOrNull()?.takeIf { it > 0 }?.let { count ->
            findings.add(finding("$count filing${if (count != 1) "s" else ""}", "SEC EDGAR", DossierConfidence.HIGH, "Filing Count"))
        }
        if (findings.isEmpty()) {
            findings.add(finding("No employment or company records found", "SixDegrees", DossierConfidence.LOW))
        }
        return DossierSection("employment", "Employment & Companies", "", findings)
    }

    private fun buildLegalSection(meta: Map<String, String>): DossierSection {
        val findings = mutableListOf<DossierFinding>()
        val arrestCount = meta["arrest_count"]?.toIntOrNull() ?: 0
        val ctx = FindingUrlHelper.subjectContext(meta)
        if (arrestCount > 0) {
            findings.add(finding(
                "$arrestCount record${if (arrestCount != 1) "s" else ""}", "Arrest Database", DossierConfidence.HIGH,
                "Arrests on File", isWarning = true, sourceUrl = FindingUrlHelper.courtUrl(ctx)
            ))
            meta["arrest_records"]?.lines()?.filter { it.isNotBlank() }?.forEach { rec ->
                findings.add(finding(
                    rec, "Arrest Database", DossierConfidence.HIGH, "Arrest Record", isWarning = true,
                    sourceUrl = FindingUrlHelper.courtUrl(ctx, rec)
                ))
            }
        }
        val courtCount = meta["courtlistener_count"]?.toIntOrNull()
            ?: meta["court_case_count"]?.toIntOrNull() ?: 0
        if (courtCount > 0) {
            val courtLink = meta["courtlistener_link"]?.takeIf { it.startsWith("http") }
                ?: meta["court_case_urls"]?.lines()?.firstOrNull { it.startsWith("http") }
            findings.add(finding(
                "$courtCount case${if (courtCount != 1) "s" else ""}", "CourtListener", DossierConfidence.HIGH, "Court Cases",
                sourceUrl = courtLink ?: FindingUrlHelper.courtUrl(ctx)
            ))
            courtLink?.let { findings.add(finding(it, "CourtListener", DossierConfidence.HIGH, "View Cases", isLink = true, sourceUrl = it)) }
        }
        meta["judyrecords_cases"]?.lines()?.filter { it.isNotBlank() }?.forEach { case ->
            findings.add(finding(
                case, "JudyRecords", DossierConfidence.HIGH, "Court Case",
                sourceUrl = FindingUrlHelper.judyRecordsUrl(ctx)
            ))
        }
        val sanctions = meta["opensanctions_total"]?.toIntOrNull() ?: 0
        if (sanctions > 0) {
            findings.add(finding(
                "$sanctions match${if (sanctions != 1) "es" else ""}", "OpenSanctions", DossierConfidence.HIGH,
                "Sanctions / PEP", isWarning = true, sourceUrl = FindingUrlHelper.opensanctionsUrl(ctx)
            ))
            meta["opensanctions_names"]?.let {
                findings.add(finding(
                    it, "OpenSanctions", DossierConfidence.HIGH, "Matched Names", isWarning = true,
                    sourceUrl = FindingUrlHelper.opensanctionsUrl(ctx)
                ))
            }
        }
        // FBI Wanted matches
        meta["fbi_wanted_matches"]?.takeIf { it.isNotBlank() }?.let {
            findings.add(finding(it, "FBI Wanted", DossierConfidence.HIGH, "FBI Wanted Match", isWarning = true))
        }
        meta["fbi_wanted_urls"]?.lines()?.filter { it.isNotBlank() }?.take(3)?.forEach { url ->
            findings.add(finding(url, "FBI Wanted", DossierConfidence.HIGH, "FBI Profile", isLink = true, sourceUrl = url.trim()))
        }
        // SEC EDGAR insider filings
        meta["sec_person_entities"]?.takeIf { it.isNotBlank() }?.let {
            findings.add(finding(it, "SEC EDGAR", DossierConfidence.HIGH, "Insider Filings"))
        }
        // GLEIF legal entities
        meta["gleif_entities"]?.takeIf { it.isNotBlank() }?.let {
            findings.add(finding(it, "GLEIF", DossierConfidence.MEDIUM, "Legal Entity Registration"))
        }
        meta["dork_criminal_results"]?.split("\n---\n")?.filter { it.isNotBlank() }?.take(8)?.forEach { block ->
            findings.add(finding(
                block.trim(), "Auto-Dork", DossierConfidence.LOW, "Criminal Intel", isWarning = true,
                sourceUrl = FindingUrlHelper.courtUrl(ctx, block.trim())
            ))
        }
        meta["dork_court_results"]?.split("\n---\n")?.filter { it.isNotBlank() }?.take(8)?.forEach { block ->
            findings.add(
                finding(
                    block.trim(), "Auto-Dork", DossierConfidence.LOW, "Court Intel",
                    sourceUrl = FindingUrlHelper.courtUrl(FindingUrlHelper.subjectContext(meta), block.trim())
                )
            )
        }
        if (courtCount == 0) {
            findings.add(
                finding(
                    "Search ${ctx.name.ifBlank { "subject" }} on CourtListener",
                    "CourtListener", DossierConfidence.MEDIUM,
                    "Search Court Records", isLink = true,
                    sourceUrl = FindingUrlHelper.courtUrl(ctx)
                )
            )
        }
        if (sanctions == 0) {
            findings.add(
                finding(
                    "Search ${ctx.name.ifBlank { "subject" }} on OpenSanctions",
                    "OpenSanctions", DossierConfidence.MEDIUM,
                    "Search Sanctions Lists", isLink = true,
                    sourceUrl = FindingUrlHelper.opensanctionsUrl(ctx)
                )
            )
        }
        if (findings.isEmpty()) {
            findings.add(finding("No legal or court records found", "SixDegrees", DossierConfidence.LOW))
        }
        return DossierSection("legal", "Legal & Courts", "", findings)
    }

    private fun buildDigitalSection(meta: Map<String, String>): DossierSection {
        val findings = mutableListOf<DossierFinding>()
        meta["dork_needle_findings"]?.lines()?.filter { it.isNotBlank() }?.take(10)?.forEach { line ->
            val parsed = com.twoskoops707.sixdegrees.domain.DorkMetadataStore.parseNeedleLine(line)
            val value = parsed["value"] ?: line
            val confidence = parsed["confidence"]?.toIntOrNull() ?: 2
            val conf = when {
                confidence >= 4 -> DossierConfidence.HIGH
                confidence >= 2 -> DossierConfidence.MEDIUM
                else -> DossierConfidence.LOW
            }
            findings.add(finding(value, "Google Intelligence", conf, parsed["type"] ?: "Corroborated"))
        }
        com.twoskoops707.sixdegrees.domain.DorkMetadataStore.allCategories(meta).forEach { category ->
            com.twoskoops707.sixdegrees.domain.DorkMetadataStore.hitsForCategory(meta, category)
                .take(4).forEach { hit ->
                    val text = "${hit.title}: ${hit.snippet}".trim().take(200)
                    findings.add(
                        finding(
                            value = text,
                            source = "Google Intelligence",
                            confidence = DossierConfidence.MEDIUM,
                            label = category.displayName,
                            sourceUrl = hit.url.takeIf { it.startsWith("http") },
                            isLink = false
                        )
                    )
                }
        }
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
            ?.let { name ->
                val ghUrl = if (name.startsWith("http")) name else "https://github.com/${name.removePrefix("@")}"
                findings.add(finding(name, "GitHub", DossierConfidence.MEDIUM, "GitHub Profile", isLink = true, sourceUrl = ghUrl))
            }
        meta["github_stats"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "GitHub", DossierConfidence.MEDIUM, "GitHub Activity")) }
        // GitLab profile
        meta["gitlab_name"]?.takeIf { it.isNotBlank() }?.let { name ->
            findings.add(finding(name, "GitLab", DossierConfidence.MEDIUM, "GitLab Profile", isLink = true, sourceUrl = "https://gitlab.com/${meta["field_username"] ?: meta["username"] ?: ""}"))
        }
        meta["gitlab_stats"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "GitLab", DossierConfidence.MEDIUM, "GitLab Activity")) }
        // Mastodon profile
        meta["mastodon_name"]?.takeIf { it.isNotBlank() }?.let { name ->
            findings.add(finding(name, "Mastodon", DossierConfidence.MEDIUM, "Mastodon Profile", isLink = true, sourceUrl = "https://mastodon.social/@${meta["field_username"] ?: meta["username"] ?: ""}"))
        }
        meta["mastodon_stats"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Mastodon", DossierConfidence.MEDIUM, "Mastodon Activity")) }
        // Bluesky profile
        meta["bluesky_name"]?.takeIf { it.isNotBlank() }?.let { name ->
            findings.add(finding(name, "Bluesky", DossierConfidence.MEDIUM, "Bluesky Profile", isLink = true, sourceUrl = "https://bsky.app/profile/${meta["field_username"] ?: meta["username"] ?: ""}.bsky.social"))
        }
        meta["bluesky_stats"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "Bluesky", DossierConfidence.MEDIUM, "Bluesky Activity")) }
        // X/Twitter profile
        meta["twitter_name"]?.takeIf { it.isNotBlank() }?.let { name ->
            findings.add(finding(name, "X/Twitter", DossierConfidence.MEDIUM, "X/Twitter Profile", isLink = true, sourceUrl = "https://x.com/${meta["field_username"] ?: meta["username"] ?: ""}"))
        }
        meta["twitter_stats"]?.takeIf { it.isNotBlank() }
            ?.let { findings.add(finding(it, "X/Twitter", DossierConfidence.MEDIUM, "X/Twitter Activity")) }
        meta["sites_found"]?.toIntOrNull()?.takeIf { it > 0 }?.let { found ->
            val checked = meta["sites_checked"]?.toIntOrNull() ?: found
            findings.add(finding("$found profiles on $checked platforms", "Username Scan", DossierConfidence.MEDIUM, "Cross-Platform"))
            parseSocialProfilesFromMeta(meta).filter { !it.statsLabel.isNullOrBlank() }.take(10).forEach { profile ->
                val value = buildString {
                    append(profile.url ?: profile.username)
                    profile.statsLabel?.let { append("  -  $it") }
                }
                findings.add(finding(value, profile.platform, DossierConfidence.MEDIUM, profile.platform, isLink = profile.url?.startsWith("http") == true))
            }
        }
        meta["holehe_services"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { svc ->
            findings.add(finding(svc, "Holehe", DossierConfidence.MEDIUM, "Email Registered"))
        }
        meta["dork_social_results"]?.split("\n---\n")?.filter { it.isNotBlank() }?.take(6)?.forEach { block ->
            findings.add(finding(block.trim(), "Auto-Dork", DossierConfidence.LOW, "Social Trace"))
        }
        if (findings.isEmpty()) {
            findings.add(finding("No digital footprint found", "SixDegrees", DossierConfidence.LOW))
        }
        return DossierSection("digital", "Digital Footprint", "", findings)
    }

    private fun buildGoogleIntelligenceSection(
        meta: Map<String, String>,
        investigatorMode: Boolean
    ): DossierSection {
        val findings = mutableListOf<DossierFinding>()
        val categories = DorkMetadataStore.allCategories(meta).ifEmpty {
            GoogleDorkLibrary.DorkCategory.entries.filter {
                DorkMetadataStore.hitsForCategory(meta, it).isNotEmpty()
            }
        }
        for (category in categories) {
            val hits = DorkMetadataStore.hitsForCategory(meta, category)
            hits.forEach { hit ->
                val displayLabel = if (investigatorMode && hit.query.isNotBlank()) {
                    "${category.displayName}  |  ${hit.query.take(72)}"
                } else {
                    category.displayName
                }
                val summary = buildString {
                    append(hit.title)
                    if (hit.snippet.isNotBlank()) append("  -  ").append(hit.snippet.take(180))
                }.trim()
                val link = hit.url.takeIf { it.startsWith("http") }
                findings.add(
                    finding(
                        value = link ?: summary,
                        source = "Google Dork",
                        confidence = DossierConfidence.LOW,
                        label = displayLabel,
                        isLink = link != null,
                        sourceUrl = link
                    )
                )
            }
        }
        meta["dork_total_hits"]?.toIntOrNull()?.takeIf { it > 0 }?.let { total ->
            findings.add(0, finding("$total validated hit${if (total != 1) "s" else ""} across ${categories.size} categories", "Google Dork", DossierConfidence.MEDIUM, "Summary"))
        }
        if (findings.isEmpty()) {
            findings.add(finding("No Google dork hits matched this subject", "Google Dork", DossierConfidence.LOW))
        }
        return DossierSection("google_intel", "Google Intelligence", "", findings)
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
        return DossierSection("vehicles", "Vehicles", "", findings)
    }

    private fun buildDarkWebSection(meta: Map<String, String>): DossierSection {
        val findings = mutableListOf<DossierFinding>()
        val ctx = FindingUrlHelper.subjectContext(meta)

        meta["darkweb_search_terms"]?.takeIf { it.isNotBlank() }?.let { terms ->
            findings.add(finding(terms, "SixDegrees", DossierConfidence.MEDIUM, "Searched Terms"))
        }

        // DarkSearch indexed mentions and links
        meta["darksearch_snippet"]?.lines()?.filter { it.isNotBlank() }?.take(6)?.forEach { line ->
            findings.add(finding(
                line.trim(), "DarkSearch", DossierConfidence.MEDIUM, "Indexed Mention", isWarning = true,
                sourceUrl = FindingUrlHelper.darkWebUrl(ctx, line.trim())
            ))
        }
        (meta["darksearch_links"] ?: meta["darksearch_dark_links"])?.lines()?.filter { it.isNotBlank() }?.take(8)?.forEach { link ->
            findings.add(finding(link, "DarkSearch", DossierConfidence.MEDIUM, ".onion / Index Link", isLink = true, isWarning = true))
        }
        // Extract PII patterns from DarkSearch snippets
        meta["darksearch_snippet"]?.takeIf { it.isNotBlank() }?.let { snippet ->
            findings.addAll(extractPiiFromDarkSnippet(snippet, "DarkSearch"))
        }

        // Ahmia Tor index
        meta["ahmia_count"]?.toIntOrNull()?.takeIf { it > 0 }?.let { count ->
            val viaTor = meta["ahmia_via_tor"]?.toBooleanStrictOrNull() == true
            findings.add(finding(
                "$count Ahmia hit${if (count != 1) "s" else ""}${if (viaTor) " (via Tor)" else ""}",
                "Ahmia", DossierConfidence.MEDIUM, "Tor Index", isWarning = true,
                sourceUrl = FindingUrlHelper.darkWebUrl(ctx)
            ))
        }
        meta["ahmia_titles"]?.lines()?.filter { it.isNotBlank() }?.take(6)?.zip(
            meta["ahmia_urls"]?.lines()?.filter { it.isNotBlank() }?.take(6).orEmpty()
        )?.forEach { (title, url) ->
            findings.add(finding(url.ifBlank { title }, "Ahmia", DossierConfidence.MEDIUM, title.take(80), isLink = url.isNotBlank(), isWarning = true))
        }
        // Extract PII patterns from Ahmia snippets/titles
        listOf(meta["ahmia_snippet"], meta["ahmia_titles"]).forEach { raw ->
            raw?.takeIf { it.isNotBlank() }?.let { findings.addAll(extractPiiFromDarkSnippet(it, "Ahmia")) }
        }

        // Auto-dork dark/leak blocks
        meta["dork_dark_results"]?.split("\n---\n")?.filter { it.isNotBlank() }?.take(6)?.forEach { block ->
            findings.add(finding(
                block.trim(), "Auto-Dork", DossierConfidence.LOW, "Dark Web Intel", isWarning = true,
                sourceUrl = FindingUrlHelper.darkWebUrl(ctx, block.trim())
            ))
        }
        meta["dork_leaks_results"]?.split("\n---\n")?.filter { it.isNotBlank() }?.take(6)?.forEach { block ->
            findings.add(finding(
                block.trim(), "Auto-Dork", DossierConfidence.LOW, "Leaked Data", isWarning = true,
                sourceUrl = FindingUrlHelper.pasteUrl(ctx)
            ))
        }

        // HIBP breaches  -  count + individual breach names
        val breachCount = meta["hibp_breach_count"]?.toIntOrNull() ?: 0
        if (breachCount > 0) {
            findings.add(finding(
                "$breachCount breach${if (breachCount != 1) "es" else ""}", "Have I Been Pwned", DossierConfidence.HIGH,
                "Breach Exposure", isWarning = true, sourceUrl = FindingUrlHelper.hibpUrl(ctx.email)
            ))
            meta["hibp_names"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { name ->
                findings.add(finding(name, "Have I Been Pwned", DossierConfidence.HIGH, "Breach Name", isWarning = true,
                    sourceUrl = FindingUrlHelper.hibpUrl(ctx.email)))
            }
        }

        // HIBP pastes  -  count + individual paste sources
        val hibpPasteCount = meta["hibp_paste_count"]?.toIntOrNull() ?: 0
        if (hibpPasteCount > 0) {
            findings.add(finding(
                "$hibpPasteCount paste dump${if (hibpPasteCount != 1) "s" else ""}", "Have I Been Pwned", DossierConfidence.HIGH,
                "Paste Exposure", isWarning = true, sourceUrl = FindingUrlHelper.pasteUrl(ctx)
            ))
            meta["hibp_pastes"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { src ->
                findings.add(finding(src, "Have I Been Pwned", DossierConfidence.HIGH, "Paste Source", isWarning = true,
                    sourceUrl = FindingUrlHelper.pasteUrl(ctx)))
            }
        } else {
            val pasteCount = meta["paste_count"]?.toIntOrNull() ?: 0
            if (pasteCount > 0) {
                findings.add(finding(
                    "$pasteCount paste dump${if (pasteCount != 1) "s" else ""}", "Paste Sites", DossierConfidence.MEDIUM,
                    "Paste Exposure", isWarning = true, sourceUrl = FindingUrlHelper.pasteUrl(ctx)
                ))
            }
        }

        // LeakCheck breach sources
        val leakCount = meta["leakcheck_count"]?.toIntOrNull() ?: 0
        if (leakCount > 0) {
            findings.add(finding(
                "$leakCount breach source${if (leakCount != 1) "s" else ""}", "LeakCheck", DossierConfidence.HIGH,
                "Credential Leak", isWarning = true, sourceUrl = "https://leakcheck.io/"
            ))
            meta["leakcheck_sources"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { src ->
                findings.add(finding(src, "LeakCheck", DossierConfidence.HIGH, "Breach Source", isWarning = true,
                    sourceUrl = "https://leakcheck.io/"))
            }
        }

        // ProxyNova COMB credential rows
        val proxyCount = meta["proxynova_breach_count"]?.toIntOrNull() ?: 0
        if (proxyCount > 0) {
            findings.add(finding(
                "$proxyCount COMB record${if (proxyCount != 1) "s" else ""}", "ProxyNova", DossierConfidence.MEDIUM,
                "Credential Leak", isWarning = true,
                sourceUrl = "https://www.proxynova.com/tools/comb-database-search/"
            ))
            meta["proxynova_records"]?.split("|")?.map { it.trim() }?.filter { it.isNotBlank() }?.take(10)?.forEach { rec ->
                findings.add(finding(rec, "ProxyNova", DossierConfidence.MEDIUM, "COMB Record", isWarning = true,
                    sourceUrl = "https://www.proxynova.com/tools/comb-database-search/"))
            }
        }

        if (findings.isEmpty()) {
            findings.add(finding("No indexed dark web or breach exposure found for this subject", "SixDegrees", DossierConfidence.LOW))
        }
        return DossierSection("darkweb", "Dark Web & Breaches", "", findings)
    }

    private fun extractPiiFromDarkSnippet(text: String, source: String): List<DossierFinding> {
        val found = mutableListOf<DossierFinding>()
        val ssnRegex = Regex("""\b\d{3}-\d{2}-\d{4}\b""")
        val dobRegex = Regex("""\b(?:19|20)\d{2}[-/]\d{2}[-/]\d{2}\b|\b\d{2}[-/]\d{2}[-/](?:19|20)\d{2}\b""")
        val plateRegex = Regex("""\b[A-Z]{1,3}[\s-]?\d{3,4}[A-Z]{0,2}\b|\b\d{1,3}[A-Z]{2,3}\d{1,4}\b""")

        ssnRegex.findAll(text).forEach { m ->
            found.add(finding(m.value, source, DossierConfidence.HIGH, "SSN (indexed)", isWarning = true))
        }
        dobRegex.findAll(text).forEach { m ->
            found.add(finding(m.value, source, DossierConfidence.MEDIUM, "DOB (indexed)", isWarning = true))
        }
        plateRegex.findAll(text).filter { it.value.length >= 5 }.forEach { m ->
            found.add(finding(m.value, source, DossierConfidence.LOW, "License Plate (indexed)"))
        }
        return found
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
                findings.add(finding(if (rationale != null) "$conf  -  $rationale" else conf, "AI Analysis", DossierConfidence.MEDIUM, "AI Confidence"))
            }
            meta["ai_false_positives"]?.lines()?.filter { it.isNotBlank() }?.forEach { line ->
                findings.add(finding(line.trim(), "AI Analysis", DossierConfidence.LOW, "False Positive"))
            }
            meta["ai_next_steps"]?.lines()?.filter { it.isNotBlank() }?.forEach { line ->
                findings.add(finding(line.trim(), "AI Analysis", DossierConfidence.MEDIUM, "Next Step"))
            }
        } else {
            meta["ai_summary"]?.takeIf { it.isNotBlank() }?.lines()?.filter { it.isNotBlank() }?.forEach { line ->
                findings.add(finding(line.trim(), "AI Analysis", DossierConfidence.MEDIUM, "Brief"))
            }
        }
        if (findings.isEmpty()) {
            findings.add(finding("No AI brief available for this report", "SixDegrees", DossierConfidence.LOW))
        } else {
            findings.add(finding("AI-generated synthesis  -  verify all claims independently.", "SixDegrees", DossierConfidence.LOW, "Disclaimer", isWarning = true))
        }
        return DossierSection("ai", "AI Brief", "", findings)
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
            notes.add(if (line.startsWith("Skipped")) line else "Skipped  -  outside $geoLabel: $line")
        }
        return notes.distinct()
    }

    private fun formatRejectionNote(key: String, value: String, geoLabel: String): String {
        if (value.startsWith("Skipped", ignoreCase = true)) return value
        val source = sourceFromKey(key)
        return "Skipped  -  outside $geoLabel ($source): $value"
    }

    private fun parseSocialProfilesFromMeta(meta: Map<String, String>): List<SocialProfile> {
        val json = meta["social_profiles_json"]?.takeIf { it.isNotBlank() } ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                SocialProfile(
                    platform = obj.optString("platform"),
                    username = obj.optString("username"),
                    url = obj.optString("url").takeIf { it.isNotBlank() },
                    followersCount = obj.optInt("followersCount").takeIf { obj.has("followersCount") && !obj.isNull("followersCount") },
                    followingCount = obj.optInt("followingCount").takeIf { obj.has("followingCount") && !obj.isNull("followingCount") },
                    friendsCount = obj.optInt("friendsCount").takeIf { obj.has("friendsCount") && !obj.isNull("friendsCount") },
                    statsLabel = obj.optString("statsLabel").takeIf { it.isNotBlank() }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
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
        isWarning = isWarning,
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

    private fun extractMatchedNames(meta: Map<String, String>): List<Pair<String, String>> {
        val seen = linkedSetOf<String>()
        val results = mutableListOf<Pair<String, String>>()
        val keys = listOf(
            "phone_owner_names" to "Web Search",
            "search_names" to "Web Search",
            "tps_names" to "TruePeopleSearch", "tps_name" to "TruePeopleSearch",
            "fps_names" to "FastPeopleSearch", "fps_name" to "FastPeopleSearch",
            "tt_names" to "ThatsThem", "tt_name" to "ThatsThem",
            "uspb_names" to "USPhoneBook", "uspb_name" to "USPhoneBook"
        )
        keys.forEach { (key, source) ->
            meta[key]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { name ->
                val normalized = name.lowercase()
                if (seen.add(normalized)) results.add(name to source)
            }
        }
        return results
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

    private fun extractAddresses(meta: Map<String, String>): LinkedHashSet<String> =
        LinkedHashSet(FindingUrlHelper.extractFullAddresses(meta))

    private fun buildRiskFlagFindings(meta: Map<String, String>): List<DossierFinding> {
        val ctx = FindingUrlHelper.subjectContext(meta)
        val findings = mutableListOf<DossierFinding>()

        meta["hibp_breach_count"]?.toIntOrNull()?.takeIf { it > 0 }?.let { count ->
            findings.add(finding(
                "$count data breach${if (count != 1) "es" else ""} on record",
                "Have I Been Pwned", DossierConfidence.HIGH, "Breach", isWarning = true,
                sourceUrl = FindingUrlHelper.hibpUrl(ctx.email)
            ))
        }
        meta["arrest_count"]?.toIntOrNull()?.takeIf { it > 0 }?.let { count ->
            findings.add(finding(
                "$count arrest record${if (count != 1) "s" else ""}",
                "Arrest Database", DossierConfidence.HIGH, "Arrest", isWarning = true,
                sourceUrl = FindingUrlHelper.courtUrl(ctx)
            ))
        }
        meta["opensanctions_total"]?.toIntOrNull()?.takeIf { it > 0 }?.let { count ->
            findings.add(finding(
                "$count sanctions / PEP match${if (count != 1) "es" else ""}",
                "OpenSanctions", DossierConfidence.HIGH, "Sanctions", isWarning = true,
                sourceUrl = FindingUrlHelper.opensanctionsUrl(ctx)
            ))
        }
        meta["paste_count"]?.toIntOrNull()?.takeIf { it > 0 }?.let { count ->
            findings.add(finding(
                "$count paste dump${if (count != 1) "s" else ""}",
                "Paste Sites", DossierConfidence.MEDIUM, "Paste", isWarning = true,
                sourceUrl = FindingUrlHelper.pasteUrl(ctx)
            ))
        }
        meta["ahmia_count"]?.toIntOrNull()?.takeIf { it > 0 }?.let { count ->
            findings.add(finding(
                "$count dark web index hit${if (count != 1) "s" else ""}",
                "Ahmia", DossierConfidence.MEDIUM, "Dark Web", isWarning = true,
                sourceUrl = FindingUrlHelper.darkWebUrl(ctx)
            ))
        }
        meta["emailrep_suspicious"]?.toBooleanStrictOrNull()?.takeIf { it }?.let {
            findings.add(finding(
                "Email flagged as suspicious",
                "EmailRep", DossierConfidence.MEDIUM, "Email Risk", isWarning = true,
                sourceUrl = FindingUrlHelper.emailUrl(ctx.email)
            ))
        }
        val courtCases = meta["court_case_count"]?.toIntOrNull()
            ?: meta["courtlistener_count"]?.toIntOrNull() ?: 0
        if (courtCases > 0) {
            findings.add(finding(
                "$courtCases court record${if (courtCases != 1) "s" else ""}",
                "CourtListener", DossierConfidence.HIGH, "Court", isWarning = true,
                sourceUrl = meta["courtlistener_link"]?.takeIf { it.startsWith("http") }
                    ?: FindingUrlHelper.courtUrl(ctx)
            ))
        }
        return findings
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
