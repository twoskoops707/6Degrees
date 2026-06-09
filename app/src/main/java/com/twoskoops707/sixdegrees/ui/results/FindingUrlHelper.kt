package com.twoskoops707.sixdegrees.ui.results

import android.net.Uri
import java.net.URLEncoder

/**
 * Builds verification URLs for dossier findings — tap opens browser to confirm facts.
 */
object FindingUrlHelper {

    private val PHONE_REGEX = Regex("""\+?1?[\s.\-]?\(?\d{3}\)?[\s.\-]\d{3}[\s.\-]\d{4}""")
    private val EMAIL_REGEX = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")

    fun verificationUrl(finding: DossierFinding, meta: Map<String, String> = emptyMap()): String? {
        finding.sourceUrl?.takeIf { it.startsWith("http") }?.let { return it }
        if (finding.isLink && finding.value.startsWith("http")) return finding.value

        val value = finding.value.removePrefix("pivot://").substringAfter("/", finding.value).trim()
        val label = finding.label?.lowercase().orEmpty()

        return when {
            finding.isPivot -> googleSearch(value)
            label.contains("phone") || PHONE_REGEX.containsMatchIn(value) -> phoneUrl(value)
            label.contains("email") || EMAIL_REGEX.containsMatchIn(value) -> emailUrl(value)
            label.contains("company") || label.contains("employ") -> companyUrl(value, meta)
            label.contains("address") || label.contains("location") -> locationUrl(value, meta)
            label.contains("court") || label.contains("legal") || label.contains("arrest") ->
                "https://www.courtlistener.com/?q=${enc(value)}&type=r"
            label.contains("sec") || label.contains("filing") ->
                "https://www.sec.gov/edgar/search/#/q=${enc(value)}"
            label.contains("breach") || label.contains("pwned") ->
                "https://haveibeenpwned.com/account/${enc(value)}"
            label.contains("dark") ->
                "https://ahmia.fi/search/?q=${enc(value)}"
            label.contains("name") || label.contains("associate") || label.contains("relative") ->
                googleSearchPerson(value, meta)
            else -> googleSearch(value)
        }
    }

    fun phoneUrl(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length >= 10) {
            "https://www.google.com/search?q=${enc("\"$phone\" phone reverse lookup")}"
        } else "tel:$digits"
    }

    fun emailUrl(email: String): String =
        if (email.contains("@")) {
            "https://haveibeenpwned.com/account/${enc(email)}"
        } else "mailto:$email"

    fun companyUrl(company: String, meta: Map<String, String>): String {
        val domain = meta["clearbit_company_domain"] ?: meta["company_domain"]
        return if (!domain.isNullOrBlank()) {
            "https://opencorporates.com/companies?q=${enc(domain)}"
        } else {
            "https://www.google.com/search?q=${enc("\"$company\" SEC OR OpenCorporates")}"
        }
    }

    fun locationUrl(address: String, meta: Map<String, String>): String {
        val city = meta["person_city"].orEmpty()
        val state = meta["person_state"].orEmpty()
        val q = listOf(address, city, state).filter { it.isNotBlank() }.joinToString(" ")
        return googleSearch(q)
    }

    fun googleSearch(query: String): String =
        "https://www.google.com/search?q=${enc(query)}"

    fun googleSearchPerson(name: String, meta: Map<String, String>): String {
        val loc = listOf(meta["person_city"], meta["person_state"]).filter { !it.isNullOrBlank() }.joinToString(" ")
        val q = if (loc.isNotBlank()) "\"$name\" $loc" else "\"$name\""
        return googleSearch(q)
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
