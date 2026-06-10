package com.twoskoops707.sixdegrees.domain

import com.twoskoops707.sixdegrees.domain.model.SubjectProfile

/**
 * Parses freeform subject dumps into structured [SubjectProfile] fields.
 * Example: "John Smith, Austin TX, works at Dell, phone 512-555-0100"
 */
object SubjectIntakeParser {

    private val PHONE_REGEX = Regex(
        """(?:\+?1[-.\s]?)?(?:\(?\d{3}\)?[-.\s]?)\d{3}[-.\s]?\d{4}"""
    )
    private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val CITY_STATE_REGEX = Regex(
        """\b([A-Za-z][A-Za-z\s.'-]{1,30}),\s*([A-Z]{2})\b"""
    )
    private val CITY_STATE_SPACED_REGEX = Regex(
        """\b([A-Za-z][A-Za-z\s.'-]{1,30})\s+([A-Z]{2})\b"""
    )
    private val WORKS_AT_REGEX = Regex(
        """(?i)(?:works?\s+at|employed\s+(?:at|by)|job\s+at)\s+([A-Za-z0-9][A-Za-z0-9\s&.'-]{1,40})"""
    )
    private val AGE_REGEX = Regex("""\b(?:age\s+)?(\d{2})\s*(?:years?\s*old|yo)?\b""", RegexOption.IGNORE_CASE)
    private val STREET_ADDRESS_REGEX = Regex(
        """\b(\d{1,5}\s+[A-Za-z0-9][A-Za-z0-9\s.'#-]{2,60}?(?:St\.?|Street|Ave\.?|Avenue|Blvd\.?|Boulevard|Dr\.?|Drive|Rd\.?|Road|Ln\.?|Lane|Ct\.?|Court|Way|Pl\.?|Place|Cir\.?|Circle|Pkwy|Hwy|Ter\.?|Trail)\b)""",
        RegexOption.IGNORE_CASE
    )

    fun looksLikeFreeform(text: String): Boolean {
        val t = text.trim()
        if (t.length < 20) return false
        return t.contains(',') ||
            WORKS_AT_REGEX.containsMatchIn(t) ||
            (PHONE_REGEX.containsMatchIn(t) && t.split("\\s+".toRegex()).size >= 4)
    }

    fun parseFreeformText(text: String): SubjectProfile {
        var remaining = text.trim()
        val phone = PHONE_REGEX.find(remaining)?.value?.also {
            remaining = remaining.replace(it, " ")
        }?.replace(Regex("[^0-9+]"), "")?.let { digits ->
            if (digits.length == 10) digits else digits.takeLast(10).takeIf { it.length == 10 } ?: digits
        }.orEmpty()

        val email = EMAIL_REGEX.find(remaining)?.value?.also {
            remaining = remaining.replace(it, " ")
        }.orEmpty()

        var city = ""
        var state = ""
        var address = STREET_ADDRESS_REGEX.find(remaining)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (address.isNotBlank()) {
            remaining = remaining.replace(address, " ")
        }
        CITY_STATE_REGEX.find(remaining)?.let { match ->
            city = match.groupValues[1].trim()
            state = match.groupValues[2].trim()
            remaining = remaining.replace(match.value, " ")
        } ?: CITY_STATE_SPACED_REGEX.find(remaining)?.let { match ->
            val candidateCity = match.groupValues[1].trim()
            if (!candidateCity.contains(' ') || candidateCity.split(' ').size <= 3) {
                city = candidateCity
                state = match.groupValues[2].trim()
                remaining = remaining.replace(match.value, " ")
            }
        }

        val employer = WORKS_AT_REGEX.find(remaining)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (employer.isNotBlank()) {
            remaining = remaining.replace(WORKS_AT_REGEX, " ")
        }

        val age = AGE_REGEX.find(remaining)?.groupValues?.getOrNull(1).orEmpty()
        if (age.isNotBlank()) remaining = remaining.replace(AGE_REGEX, " ")

        remaining = remaining
            .replace(Regex("(?i)\\bphone\\b"), " ")
            .replace(Regex("(?i)\\bemail\\b"), " ")
            .replace(Regex("(?i)\\bin\\b"), " ")
            .replace(Regex("[,;|]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val nameParts = remaining.split("\\s+".toRegex()).filter { part ->
            part.length > 1 && !part.equals("at", ignoreCase = true) &&
                !part.equals("works", ignoreCase = true) && !part.equals("tx", ignoreCase = true)
        }
        val name = nameParts.joinToString(" ").trim()

        return SubjectProfile(
            name = name,
            firstName = nameParts.firstOrNull().orEmpty(),
            lastName = if (nameParts.size > 1) nameParts.last() else "",
            city = city,
            state = state,
            address = address,
            phone = phone,
            email = email,
            age = age,
            intent = if (employer.isNotBlank()) "employer=$employer" else ""
        )
    }

    fun mergeWithForm(profile: SubjectProfile, form: Map<String, String>): SubjectProfile =
        profile.copy(
            name = form["name"]?.takeIf { it.isNotBlank() } ?: profile.name,
            firstName = form["firstName"]?.takeIf { it.isNotBlank() } ?: profile.firstName,
            lastName = form["lastName"]?.takeIf { it.isNotBlank() } ?: profile.lastName,
            city = form["city"]?.takeIf { it.isNotBlank() } ?: profile.city,
            state = form["state"]?.takeIf { it.isNotBlank() } ?: profile.state,
            address = form["address"]?.takeIf { it.isNotBlank() } ?: profile.address,
            phone = form["phone"]?.takeIf { it.isNotBlank() } ?: profile.phone,
            email = form["email"]?.takeIf { it.isNotBlank() } ?: profile.email,
            username = form["username"]?.takeIf { it.isNotBlank() } ?: profile.username,
            photoUri = form["image"]?.takeIf { it.isNotBlank() } ?: profile.photoUri,
            intent = form["intent"]?.takeIf { it.isNotBlank() } ?: profile.intent
        )
}
