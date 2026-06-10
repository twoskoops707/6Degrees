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
    private val DOMAIN_REGEX = Regex(
        """\b(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,24}\b""",
        RegexOption.IGNORE_CASE
    )
    private val USERNAME_LABEL_REGEX = Regex(
        """(?i)\b(?:username|user|handle|profile)\b\s*[:=@-]?\s*@?([A-Za-z0-9._-]{2,32})\b"""
    )
    private val STANDALONE_USERNAME_REGEX = Regex("""^@([A-Za-z0-9._-]{2,32})$""")
    private val VIN_REGEX = Regex("""\b[A-HJ-NPR-Z0-9]{17}\b""", RegexOption.IGNORE_CASE)
    private val PLATE_REGEX = Regex(
        """\b(?:[A-Z]{1,3}[\s-]?\d{3,4}[A-Z]{0,2}|\d{1,3}[A-Z]{2,3}\d{1,4})\b""",
        RegexOption.IGNORE_CASE
    )
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
    private val COMPANY_LABEL_REGEX = Regex(
        """(?i)\b(?:company|business|employer|organization|organisation|org)\b\s*[:=-]?\s*([A-Za-z0-9][A-Za-z0-9\s&.',-]{1,60})"""
    )
    private val COMPANY_SUFFIX_REGEX = Regex(
        """\b[A-Za-z0-9][A-Za-z0-9&.'-]*(?:\s+[A-Za-z0-9&.'-]+){0,5}\s+(?:Inc|LLC|Ltd|Corp|Corporation|Company|Co|Labs|Technologies|Systems|Group|Holdings)\b""",
        RegexOption.IGNORE_CASE
    )

    private val SPECIAL_FIELD_KEYS = setOf(
        "phone", "email", "username", "domain", "company", "vin", "plate",
        "city", "state", "address", "age"
    )

    fun looksLikeFreeform(text: String): Boolean {
        val parsed = parseToFields(text)
        return parsed.keys.any { it in SPECIAL_FIELD_KEYS }
    }

    fun parseToFields(text: String): Map<String, String> {
        var remaining = text.trim()
        if (remaining.isBlank()) return emptyMap()

        val fields = linkedMapOf<String, String>()

        val phone = PHONE_REGEX.find(remaining)?.value?.also {
            remaining = remaining.replace(it, " ")
        }?.let(::normalizePhone).orEmpty()
        if (phone.isNotBlank()) fields["phone"] = phone

        val email = EMAIL_REGEX.find(remaining)?.value?.also {
            remaining = remaining.replace(it, " ")
        }.orEmpty()
        if (email.isNotBlank()) fields["email"] = email

        var city = ""
        var state = ""
        var address = STREET_ADDRESS_REGEX.find(remaining)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (address.isNotBlank()) {
            remaining = remaining.replace(address, " ")
            fields["address"] = address
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
        if (city.isNotBlank()) fields["city"] = city
        if (state.isNotBlank()) fields["state"] = state

        val company = sequenceOf(
            WORKS_AT_REGEX.find(remaining)?.groupValues?.getOrNull(1)?.trim(),
            COMPANY_LABEL_REGEX.find(remaining)?.groupValues?.getOrNull(1)?.trim(),
            COMPANY_SUFFIX_REGEX.find(remaining)?.value?.trim()
        ).mapNotNull { candidate ->
            candidate?.let(::cleanCompany)?.takeIf { it.isNotBlank() }
        }
            .firstOrNull()
            .orEmpty()
        if (company.isNotBlank()) {
            fields["company"] = company
            remaining = remaining.replace(WORKS_AT_REGEX, " ")
            remaining = remaining.replace(COMPANY_LABEL_REGEX, " ")
            remaining = remaining.replace(company, " ")
        }

        val age = AGE_REGEX.find(remaining)?.groupValues?.getOrNull(1).orEmpty()
        if (age.isNotBlank()) {
            fields["age"] = age
            remaining = remaining.replace(AGE_REGEX, " ")
        }

        val username = USERNAME_LABEL_REGEX.find(remaining)?.groupValues?.getOrNull(1)
            ?: STANDALONE_USERNAME_REGEX.matchEntire(remaining)?.groupValues?.getOrNull(1)
        username?.trim()?.takeIf { it.isNotBlank() }?.let {
            fields["username"] = it.removePrefix("@")
            remaining = remaining.replace(USERNAME_LABEL_REGEX, " ")
            remaining = remaining.replace(STANDALONE_USERNAME_REGEX, " ")
        }

        val vin = VIN_REGEX.find(remaining)?.value?.uppercase()
        if (!vin.isNullOrBlank()) {
            fields["vin"] = vin
            remaining = remaining.replace(vin, " ", ignoreCase = true)
        }

        val domain = DOMAIN_REGEX.find(remaining)?.value
            ?.takeUnless { email.endsWith("@$it", ignoreCase = true) }
            ?.lowercase()
        if (!domain.isNullOrBlank()) {
            fields["domain"] = domain
            remaining = remaining.replace(domain, " ", ignoreCase = true)
        }

        if (fields["vin"].isNullOrBlank()) {
            val plate = PLATE_REGEX.find(remaining)?.value?.uppercase()
            if (!plate.isNullOrBlank()) {
                fields["plate"] = plate
                remaining = remaining.replace(plate, " ", ignoreCase = true)
            }
        }

        remaining = remaining
            .replace(Regex("(?i)\\bphone\\b"), " ")
            .replace(Regex("(?i)\\bemail\\b"), " ")
            .replace(Regex("(?i)\\busername\\b"), " ")
            .replace(Regex("(?i)\\bhandle\\b"), " ")
            .replace(Regex("(?i)\\bdomain\\b"), " ")
            .replace(Regex("(?i)\\bwebsite\\b"), " ")
            .replace(Regex("(?i)\\bvin\\b"), " ")
            .replace(Regex("(?i)\\bplate\\b"), " ")
            .replace(Regex("(?i)\\blicense\\b"), " ")
            .replace(Regex("(?i)\\bvehicle\\b"), " ")
            .replace(Regex("(?i)\\bcompany\\b"), " ")
            .replace(Regex("(?i)\\bbusiness\\b"), " ")
            .replace(Regex("(?i)\\bemployer\\b"), " ")
            .replace(Regex("(?i)\\bin\\b"), " ")
            .replace(Regex("[,;|]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val nameParts = remaining.split("\\s+".toRegex()).filter { part ->
            part.length > 1 &&
                !part.equals("at", ignoreCase = true) &&
                !part.equals("works", ignoreCase = true) &&
                !part.equals("tx", ignoreCase = true) &&
                !part.equals("ca", ignoreCase = true) &&
                !part.equals("ny", ignoreCase = true)
        }
        val name = nameParts.joinToString(" ").trim().takeIf {
            it.isNotBlank() &&
                fields["company"].isNullOrBlank() &&
                !it.contains("@") &&
                !it.contains(".") &&
                nameParts.size in 1..4
        }.orEmpty()
        if (name.isNotBlank()) fields["name"] = name

        return fields
    }

    fun parseFreeformText(text: String): SubjectProfile {
        val fields = parseToFields(text)
        val name = fields["name"].orEmpty()
        val nameParts = name.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return SubjectProfile(
            name = name,
            firstName = nameParts.firstOrNull().orEmpty(),
            lastName = if (nameParts.size > 1) nameParts.last() else "",
            city = fields["city"].orEmpty(),
            state = fields["state"].orEmpty(),
            address = fields["address"].orEmpty(),
            phone = fields["phone"].orEmpty(),
            email = fields["email"].orEmpty(),
            username = fields["username"].orEmpty(),
            employer = fields["company"].orEmpty(),
            age = fields["age"].orEmpty(),
            intent = fields["company"]?.takeIf { it.isNotBlank() }?.let { "employer=$it" }.orEmpty()
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

    private fun normalizePhone(value: String): String {
        val digits = value.replace(Regex("[^0-9+]"), "")
        return if (digits.length == 10) {
            digits
        } else {
            digits.takeLast(10).takeIf { it.length == 10 } ?: digits
        }
    }

    private fun cleanCompany(value: String): String =
        value
            .replace(Regex("(?i)\\b(?:domain|website|site|email|phone|username|vin|plate)\\b.*$"), "")
            .trim()
}
