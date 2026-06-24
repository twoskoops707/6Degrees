package com.twoskoops707.sixdegrees.ui.results

import android.net.Uri
import com.twoskoops707.sixdegrees.data.osint.OsintToolRegistry
import java.net.URLEncoder

/**
 * Builds verification URLs for dossier findings — tap opens browser to confirm facts.
 */
object FindingUrlHelper {

    private val PHONE_REGEX = Regex("""\+?1?[\s.\-]?\(?\d{3}\)?[\s.\-]\d{3}[\s.\-]\d{4}""")
    private val EMAIL_REGEX = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")

    private val GENERIC_LABELS = setOf(
        "court", "court docs", "court intel", "criminal", "legal", "status",
        "courts & legal", "red flags", "finding", "summary", "verify", "sanctions",
        "breach", "breach exposure", "paste exposure", "dark web", "tor index",
        "search court records", "search sanctions lists", "court records",
        "search subject on courtlistener", "search subject on opensanctions",
        "courtlistener", "opensanctions", "judyrecords", "view cases", "matched names"
    )

    private val GENERIC_VALUES = setOf(
        "court docs", "sanctions", "search court records", "search sanctions lists",
        "nothing alarming turned up in public or indexed sources",
        "no legal or court records found", "no breaches or legal hits found"
    )

    data class SubjectContext(
        val name: String,
        val firstName: String,
        val lastName: String,
        val phone: String,
        val email: String,
        val username: String,
        val address: String,
        val city: String,
        val state: String,
        val zip: String
    ) {
        val location: String = listOf(city, state).filter { it.isNotBlank() }.joinToString(", ")
        val fullLocation: String = listOf(address, city, state, zip).filter { it.isNotBlank() }.joinToString(", ")
        val nameWithLocation: String = buildString {
            if (name.isNotBlank()) append("\"$name\"")
            if (location.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(location)
            }
        }.trim()
    }

    fun subjectContext(meta: Map<String, String>): SubjectContext {
        val name = meta["person_name"] ?: meta["field_name"] ?: meta["comp_name"] ?: ""
        val parts = name.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return SubjectContext(
            name = name,
            firstName = parts.firstOrNull().orEmpty(),
            lastName = if (parts.size > 1) parts.last() else "",
            phone = meta["person_phone"] ?: meta["field_phone"] ?: "",
            email = meta["person_email"] ?: meta["field_email"] ?: meta["comp_email"] ?: "",
            username = meta["field_username"] ?: meta["username"] ?: meta["comp_username"] ?: "",
            address = meta["person_entered_address"] ?: meta["field_address"] ?: "",
            city = meta["person_city"] ?: meta["field_city"] ?: meta["search_city"] ?: "",
            state = meta["person_state"] ?: meta["field_state"] ?: meta["search_state"] ?: "",
            zip = meta["person_zip"] ?: meta["field_zip"] ?: ""
        )
    }

    /** Best single-line location for report headers — prefers full street address when available. */
    fun bestDisplayLocation(meta: Map<String, String>): String {
        val ctx = subjectContext(meta)
        if (ctx.fullLocation.isNotBlank()) return ctx.fullLocation
        return extractFullAddresses(meta).firstOrNull()
            ?: meta["person_location"]?.takeIf { it.isNotBlank() }
            ?: ctx.location
    }

    fun extractFullAddresses(meta: Map<String, String>): List<String> {
        val set = linkedSetOf<String>()
        meta["person_entered_address"]?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        meta["pipl_addresses"]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { set.add(it) }
        meta["pdl_address"]?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        meta["pdl_location"]?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        meta["clearbit_person_location"]?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        meta["search_addresses"]?.lines()?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { set.add(it) }
        meta["ddg_addresses"]?.lines()?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { set.add(it) }
        listOf(
            "tps_full_addresses", "zaba_full_addresses", "411_full_addresses", "ftn_full_addresses",
            "tps_locations", "zaba_locations", "411_locations", "ftn_locations",
            "voter_addresses", "uspb_addresses", "tt_locations", "tt_addresses", "fps_locations", "fps_addresses",
            "radaris_locations", "peekyou_locations", "nuwber_locations", "wp_locations", "checkpeople_locations"
        ).forEach { key ->
            meta[key]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { set.add(it) }
        }
        val streetPattern = Regex(
            """\d{1,5}\s+[A-Z][A-Za-z0-9\s]{2,35}(?:St\.?|Ave\.?|Blvd\.?|Dr\.?|Rd\.?|Ln\.?|Ct\.?|Way|Pl\.?|Cir\.?|Pkwy|Hwy|Ter\.?|Trl\.?|Loop|Pass|Pt\.?|Road|Street|Avenue|Boulevard|Drive|Lane|Court)\b[^<\n]{0,40}[A-Z]{2}[\s,]+\d{5}(?:-\d{4})?"""
        )
        listOf(
            "dork_address_full_results", "dork_address_results", "dork_phone_results",
            "dork_identity_results", "dork_voter_results", "dork_property_results"
        ).forEach { key ->
            meta[key]?.let { text ->
                streetPattern.findAll(text)
                    .map { it.value.replace(Regex("\\s+"), " ").trim() }
                    .filter { it.length in 15..100 }
                    .take(8)
                    .forEach { set.add(it) }
            }
        }
        return set.toList()
    }

    fun isUsableSearchText(text: String, label: String = ""): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank() || trimmed.length < 3) return false
        if (trimmed.equals(label, ignoreCase = true)) return false
        val lower = trimmed.lowercase()
        if (lower in GENERIC_LABELS || lower in GENERIC_VALUES) return false
        if (lower.startsWith("http")) return false
        return true
    }

    /** Unified URL resolution: sourceUrl > http value > contextual verification URL. */
    fun resolveUrl(finding: DossierFinding, meta: Map<String, String> = emptyMap()): String? {
        finding.sourceUrl?.takeIf { it.startsWith("http") }?.let { return it }
        if (finding.isLink && finding.value.startsWith("http")) return finding.value
        return verificationUrl(finding, meta)
    }

    fun verificationUrl(finding: DossierFinding, meta: Map<String, String> = emptyMap()): String? {
        if (finding.isPivot) return pivotUrl(finding.value, subjectContext(meta))

        val value = finding.value.removePrefix("pivot://").substringAfter("/", finding.value).trim()
        val label = finding.label?.lowercase().orEmpty()
        val ctx = subjectContext(meta)

        return when {
            label.contains("phone") || PHONE_REGEX.containsMatchIn(value) -> phoneUrl(value.ifBlank { ctx.phone }, ctx)
            label.contains("email") || EMAIL_REGEX.containsMatchIn(value) -> emailUrl(value.ifBlank { ctx.email })
            label.contains("company") || label.contains("employ") -> companyUrl(value.ifBlank { ctx.name }, meta)
            label.contains("address") || label.contains("location") || label.contains("reg. address") ->
                locationUrl(value.ifBlank { ctx.fullLocation }, ctx)
            label.contains("court") || label.contains("legal") || label.contains("arrest") || label.contains("criminal") ->
                courtUrl(ctx, value)
            label.contains("sanction") || label.contains("pep") -> opensanctionsUrl(ctx)
            label.contains("sec") || label.contains("filing") -> secUrl(value.ifBlank { ctx.name }, ctx)
            label.contains("breach") || label.contains("pwned") || label.contains("hibp") ->
                hibpUrl(value.ifBlank { ctx.email })
            label.contains("paste") -> pasteUrl(ctx)
            label.contains("dark") || label.contains("onion") || label.contains("tor index") ->
                darkWebUrl(ctx, value)
            label.contains("voter") -> voterUrl(ctx)
            label.contains("profile") || label.contains("social") || label.contains("linkedin") ->
                socialUrl(value, ctx)
            label.contains("name") || label.contains("associate") || label.contains("relative") || label.contains("matched") ->
                peopleSearchUrl(value.ifBlank { ctx.name }, ctx)
            label.contains("vehicle") || label.contains("vin") ->
                vehicleUrl(value.ifBlank { ctx.name }, ctx)
            else -> contextualFallback(label, value, ctx)
        }
    }

    /** Row-based URL builder for legacy report tables (label + value pairs). */
    fun urlForRow(label: String, value: String, meta: Map<String, String>): String? {
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        if (value.startsWith("pivot://")) return pivotUrl(value, subjectContext(meta))
        val cleanLabel = label.removePrefix("> ").removePrefix("Verify: ").trim()
        return verificationUrl(
            DossierFinding(label = cleanLabel, value = value, source = "", confidence = DossierConfidence.LOW),
            meta
        )
    }

    private fun pivotUrl(pivotValue: String, ctx: SubjectContext): String? {
        val parts = pivotValue.removePrefix("pivot://").split("/", limit = 2)
        val type = parts.getOrNull(0).orEmpty()
        val query = parts.getOrNull(1).orEmpty()
        return when (type) {
            "phone" -> phoneUrl(query, ctx)
            "email" -> emailUrl(query)
            else -> peopleSearchUrl(query.ifBlank { ctx.name }, ctx)
        }
    }

    fun phoneUrl(phone: String, ctx: SubjectContext = SubjectContext("", "", "", "", "", "", "", "", "", "")): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length >= 10) {
            val last10 = digits.takeLast(10)
            // Reverse phone lookup — use the phone number, NOT a name search. FastPeopleSearch
            // and TruePeopleSearch both expose a /phone-number/<10-digit> path that returns
            // the owner, address, and associated names for a given number.
            "https://www.fastpeoplesearch.com/phone-number/$last10"
        } else "tel:$digits"
    }

    fun emailUrl(email: String): String =
        if (email.contains("@")) {
            OsintToolRegistry.buildUrl("https://haveibeenpwned.com/account/{q-encoded}", email)
        } else "mailto:$email"

    fun companyUrl(company: String, meta: Map<String, String>): String {
        val domain = meta["clearbit_company_domain"] ?: meta["company_domain"]
        return if (!domain.isNullOrBlank()) {
            "https://opencorporates.com/companies?q=${enc(domain)}"
        } else {
            val ctx = subjectContext(meta)
            val q = company.ifBlank { ctx.name }
            "https://www.google.com/search?q=${enc("\"$q\" SEC OR OpenCorporates ${ctx.location}")}"
        }
    }

    fun locationUrl(address: String, ctx: SubjectContext): String {
        val q = when {
            isUsableSearchText(address) -> address
            ctx.fullLocation.isNotBlank() -> ctx.fullLocation
            ctx.location.isNotBlank() -> ctx.location
            else -> return peopleSearchUrl(ctx.name, ctx)
        }
        return OsintToolRegistry.buildUrl("https://www.google.com/maps/search/{q-encoded}", q)
    }

    fun courtUrl(ctx: SubjectContext, snippet: String = ""): String {
        val q = when {
            isUsableSearchText(snippet) && snippet.length > 8 -> snippet
            ctx.nameWithLocation.isNotBlank() -> ctx.nameWithLocation
            ctx.name.isNotBlank() -> ctx.name
            ctx.phone.isNotBlank() -> ctx.phone
            ctx.email.isNotBlank() -> ctx.email
            else -> return "https://www.courtlistener.com/"
        }
        return OsintToolRegistry.buildUrl("https://www.courtlistener.com/?q={q-encoded}&type=p", q)
    }

    fun judyRecordsUrl(ctx: SubjectContext): String =
        OsintToolRegistry.buildUrl(
            "https://www.judyrecords.com/search?search={q-encoded}",
            ctx.name.ifBlank { ctx.email }.ifBlank { ctx.phone }
        )

    fun opensanctionsUrl(ctx: SubjectContext): String =
        OsintToolRegistry.buildUrl(
            "https://www.opensanctions.org/search/?q={q-encoded}",
            ctx.name.ifBlank { ctx.email }.ifBlank { ctx.phone }
        )

    fun secUrl(company: String, ctx: SubjectContext): String {
        val q = company.ifBlank { ctx.name }
        return "https://www.sec.gov/edgar/search/#/q=${enc(q)}"
    }

    fun hibpUrl(email: String): String =
        if (email.contains("@")) OsintToolRegistry.buildUrl("https://haveibeenpwned.com/account/{q-encoded}", email)
        else "https://haveibeenpwned.com/"

    fun pasteUrl(ctx: SubjectContext): String =
        OsintToolRegistry.buildUrl(
            "https://psbdmp.ws/api/search/{q-encoded}",
            ctx.name.ifBlank { ctx.email }.ifBlank { ctx.phone }
        )

    fun darkWebUrl(ctx: SubjectContext, value: String = ""): String {
        val q = when {
            value.startsWith("http") -> return value
            isUsableSearchText(value) -> value
            ctx.nameWithLocation.isNotBlank() -> ctx.nameWithLocation
            else -> ctx.name
        }
        return "https://ahmia.fi/search/?q=${enc(q)}"
    }

    fun voterUrl(ctx: SubjectContext): String =
        OsintToolRegistry.buildUrl(
            "https://www.google.com/search?q={q-encoded}",
            "\"${ctx.name}\" voter registration ${ctx.state}".trim()
        )

    fun socialUrl(value: String, ctx: SubjectContext): String {
        if (value.startsWith("http")) return value
        if (ctx.username.isNotBlank()) {
            return OsintToolRegistry.buildUrl(
                "https://www.linkedin.com/search/results/people/?keywords={q-encoded}",
                ctx.name.ifBlank { ctx.username }
            )
        }
        return peopleSearchUrl(ctx.name, ctx)
    }

    fun peopleSearchUrl(name: String, ctx: SubjectContext): String {
        val q = name.ifBlank { ctx.name }
        if (q.isBlank()) {
            return when {
                ctx.phone.isNotBlank() -> phoneUrl(ctx.phone, ctx)
                ctx.email.isNotBlank() -> emailUrl(ctx.email)
                else -> "https://www.fastpeoplesearch.com/"
            }
        }
        val hyphen = q.replace(" ", "-")
        val state = ctx.state.lowercase()
        return if (state.isNotBlank()) {
            "https://www.fastpeoplesearch.com/name/$hyphen/$state"
        } else {
            "https://www.fastpeoplesearch.com/name/$hyphen"
        }
    }

    private fun vehicleUrl(value: String, ctx: SubjectContext): String {
        val q = value.ifBlank { ctx.name }
        return "https://www.google.com/search?q=${enc("\"$q\" vehicle registration ${ctx.location}")}"
    }

    private fun contextualFallback(label: String, value: String, ctx: SubjectContext): String? {
        val labelLower = label.lowercase()
        if (labelLower in GENERIC_LABELS || value.equals(label, ignoreCase = true)) {
            return when {
                labelLower.contains("court") || labelLower.contains("criminal") || labelLower.contains("legal") ->
                    courtUrl(ctx)
                labelLower.contains("sanction") || labelLower.contains("pep") -> opensanctionsUrl(ctx)
                labelLower.contains("address") || labelLower.contains("location") -> locationUrl("", ctx)
                labelLower.contains("phone") -> phoneUrl(ctx.phone, ctx)
                labelLower.contains("email") -> emailUrl(ctx.email)
                labelLower.contains("breach") -> hibpUrl(ctx.email)
                labelLower.contains("paste") -> pasteUrl(ctx)
                labelLower.contains("dark") -> darkWebUrl(ctx)
                ctx.nameWithLocation.isNotBlank() -> peopleSearchUrl(ctx.name, ctx)
                ctx.name.isNotBlank() -> peopleSearchUrl(ctx.name, ctx)
                else -> null
            }
        }
        val q = when {
            isUsableSearchText(value, label) -> value
            ctx.nameWithLocation.isNotBlank() -> ctx.nameWithLocation
            ctx.name.isNotBlank() -> ctx.name
            else -> return null
        }
        return googleSearch(q)
    }

    fun googleSearch(query: String): String {
        if (!isUsableSearchText(query)) return peopleSearchUrl("", subjectContext(emptyMap()))
        return "https://www.google.com/search?q=${enc(query)}"
    }

    fun googleSearchPerson(name: String, meta: Map<String, String>): String =
        peopleSearchUrl(name, subjectContext(meta))

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

