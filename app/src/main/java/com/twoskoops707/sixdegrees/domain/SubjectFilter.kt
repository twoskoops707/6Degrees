package com.twoskoops707.sixdegrees.domain

import com.twoskoops707.sixdegrees.domain.model.CandidateProfile
import com.twoskoops707.sixdegrees.domain.model.SubjectProfile

/**
 * Validates findings and candidates against a subject profile — especially geo tokens.
 */
object SubjectFilter {

    private val COMMON_FIRST = setOf(
        "james", "john", "robert", "michael", "david", "william", "richard", "joseph",
        "thomas", "charles", "christopher", "daniel", "matthew", "anthony", "mark", "donald",
        "steven", "paul", "andrew", "joshua", "kenneth", "kevin", "brian", "george", "timothy",
        "mary", "patricia", "jennifer", "linda", "barbara", "elizabeth", "susan", "jessica",
        "sarah", "karen", "lisa", "nancy", "betty", "margaret", "sandra", "ashley", "kimberly",
        "emily", "donna", "michelle", "dorothy", "carol", "amanda", "melissa", "deborah"
    )

    private val COMMON_LAST = setOf(
        "smith", "johnson", "williams", "brown", "jones", "garcia", "miller", "davis",
        "rodriguez", "martinez", "hernandez", "lopez", "gonzalez", "wilson", "anderson",
        "thomas", "taylor", "moore", "jackson", "martin", "lee", "perez", "thompson",
        "white", "harris", "sanchez", "clark", "ramirez", "lewis", "robinson", "walker",
        "young", "allen", "king", "wright", "scott", "torres", "nguyen", "hill", "flores",
        "green", "adams", "nelson", "baker", "hall", "rivera", "campbell", "mitchell",
        "carter", "roberts", "gomez", "phillips", "evans", "turner", "diaz", "parker"
    )

    private val STATE_ABBREVS = mapOf(
        "alabama" to "AL", "alaska" to "AK", "arizona" to "AZ", "arkansas" to "AR",
        "california" to "CA", "colorado" to "CO", "connecticut" to "CT", "delaware" to "DE",
        "florida" to "FL", "georgia" to "GA", "hawaii" to "HI", "idaho" to "ID",
        "illinois" to "IL", "indiana" to "IN", "iowa" to "IA", "kansas" to "KS",
        "kentucky" to "KY", "louisiana" to "LA", "maine" to "ME", "maryland" to "MD",
        "massachusetts" to "MA", "michigan" to "MI", "minnesota" to "MN", "mississippi" to "MS",
        "missouri" to "MO", "montana" to "MT", "nebraska" to "NE", "nevada" to "NV",
        "new hampshire" to "NH", "new jersey" to "NJ", "new mexico" to "NM", "new york" to "NY",
        "north carolina" to "NC", "north dakota" to "ND", "ohio" to "OH", "oklahoma" to "OK",
        "oregon" to "OR", "pennsylvania" to "PA", "rhode island" to "RI", "south carolina" to "SC",
        "south dakota" to "SD", "tennessee" to "TN", "texas" to "TX", "utah" to "UT",
        "vermont" to "VT", "virginia" to "VA", "washington" to "WA", "west virginia" to "WV",
        "wisconsin" to "WI", "wyoming" to "WY", "district of columbia" to "DC"
    )

    fun hasGeoConstraint(city: String, state: String): Boolean =
        city.isNotBlank() || state.isNotBlank()

    fun isCommonName(name: String): Boolean {
        val parts = name.trim().lowercase().split("\\s+".toRegex()).filter { it.length > 1 }
        if (parts.size < 2) return true
        return parts.first() in COMMON_FIRST && parts.last() in COMMON_LAST
    }

    /**
     * Common names always require manual selection — never auto-pick "John Smith".
     */
    fun shouldRequireCandidateSelection(name: String, candidateCount: Int): Boolean {
        if (isCommonName(name)) return true
        return candidateCount > 1
    }

    fun matchesLocation(text: String, city: String, state: String): Boolean {
        if (city.isBlank() && state.isBlank()) return true
        val lower = text.lowercase()
        val stateTokens = geoTokens(city, state)
        if (stateTokens.isEmpty()) return true
        return stateTokens.any { token -> lower.contains(token) }
    }

    fun matchesSubject(text: String, profile: SubjectProfile): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()

        if (profile.city.isNotBlank() || profile.state.isNotBlank()) {
            if (!matchesLocation(text, profile.city, profile.state)) return false
        }

        if (profile.name.isNotBlank()) {
            val tokens = profile.name.lowercase().split("\\s+".toRegex()).filter { it.length > 1 }
            if (tokens.size >= 2) {
                val matched = tokens.count { lower.contains(it) }
                if (matched < 2) return false
            } else if (tokens.isNotEmpty() && !lower.contains(tokens.first())) {
                return false
            }
        }

        if (profile.phone.isNotBlank()) {
            val digits = profile.phone.filter { it.isDigit() }.takeLast(10)
            if (digits.length >= 7 && !text.filter { it.isDigit() }.contains(digits)) {
                val otherPhone = Regex("""\d{3}[-.\s]?\d{3}[-.\s]?\d{4}""").findAll(text)
                    .map { it.value.filter { c -> c.isDigit() } }.filter { it.length >= 10 }.toList()
                if (otherPhone.isNotEmpty() && otherPhone.none { it.endsWith(digits) }) return false
            }
        }
        return true
    }

    /** Reject texts outside geo when city+state are provided. */
    fun filterByGeo(texts: List<String>, city: String, state: String): List<String> {
        if (!hasGeoConstraint(city, state)) return texts
        return texts.filter { matchesLocation(it, city, state) }
    }

    fun filterCandidatesByGeo(
        candidates: List<CandidateProfile>,
        city: String,
        state: String
    ): List<CandidateProfile> {
        if (!hasGeoConstraint(city, state)) return candidates
        return candidates.filter { c ->
            matchesLocation("${c.location} ${c.address}", city, state)
        }
    }

    /** Two-letter state code for geo APIs (e.g. Zippopotam). */
    fun toStateAbbrev(state: String): String {
        val s = state.trim()
        if (s.length == 2) return s.uppercase()
        return STATE_ABBREVS[s.lowercase()] ?: s.take(2).uppercase()
    }

    private fun geoTokens(city: String, state: String): List<String> {
        val tokens = mutableListOf<String>()
        if (city.isNotBlank()) tokens.add(city.trim().lowercase())
        if (state.isNotBlank()) {
            val s = state.trim()
            tokens.add(s.lowercase())
            if (s.length == 2) tokens.add(s.uppercase())
            STATE_ABBREVS[s.lowercase()]?.let { tokens.add(it.lowercase()) }
        }
        return tokens.distinct()
    }

    fun normalizePhoneDigits(phone: String): String =
        phone.filter { it.isDigit() }.takeLast(10)

    private val TOLLFREE_AREA_CODES = setOf("800", "888", "877", "866", "855", "844", "833", "822")

    fun isTollFreeOrGeneric(phone: String): Boolean {
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 10) return true
        val area = digits.takeLast(10).take(3)
        return area in TOLLFREE_AREA_CODES
    }

    /** Company/registry/WHOIS sources — phones belong in company_phone, not search_phones. */
    fun isCompanySource(sourceKey: String, url: String = ""): Boolean {
        val key = sourceKey.lowercase()
        val u = url.lowercase()
        if (key.contains("clearbit") && !key.contains("person")) return true
        if (key.contains("corpwiki") || key.contains("opencorporates") || key.contains("gleif")) return true
        if (key.contains("rdap") || key.contains("whois") || key.contains("sec_edgar")) return true
        if (u.contains("clearbit.com/c") || u.contains("opencorporates.com/companies")) return true
        if (u.contains("rdap.org") || u.contains("/whois")) return true
        return false
    }

    private val STRUCTURED_API_SOURCES = setOf(
        "pipl", "pdl", "people_data_labs", "numverify", "libphonenumber", "libphone", "calltracer"
    )

    fun isStructuredApiSource(sourceKey: String): Boolean =
        sourceKey.lowercase() in STRUCTURED_API_SOURCES ||
            STRUCTURED_API_SOURCES.any { sourceKey.lowercase().contains(it) }

    /**
     * Phone isolation: only accept as subject contact when query-matched, API-keyed, or
     * explicitly tied to subject name + geo in source context.
     */
    fun shouldAcceptPersonPhone(
        phone: String,
        contextText: String,
        queryPhone: String,
        subjectName: String,
        city: String,
        state: String,
        sourceKey: String = "",
        sourceUrl: String = "",
        isStructuredApi: Boolean = false
    ): Boolean {
        if (phone.isBlank() || isTollFreeOrGeneric(phone)) return false
        if (isCompanySource(sourceKey, sourceUrl)) return false

        val normalized = normalizePhoneDigits(phone)
        val queryDigits = normalizePhoneDigits(queryPhone)
        if (queryDigits.length >= 7 && normalized == queryDigits) return true
        if (isStructuredApi || isStructuredApiSource(sourceKey)) return true

        if (subjectName.isBlank()) return false
        if (!textMatchesQuery(contextText, subjectName)) return false
        if (hasGeoConstraint(city, state) && !matchesLocation(contextText, city, state)) return false
        return true
    }

    fun textMatchesQuery(text: String, query: String): Boolean {
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.length > 1 }
        if (tokens.isEmpty()) return true
        val lower = text.lowercase()
        val required = if (tokens.size >= 2) 2 else 1
        return tokens.count { lower.contains(it) } >= required
    }

    fun rejectPersonField(
        value: String,
        subjectName: String,
        city: String,
        state: String,
        fieldKind: String = "generic"
    ): Boolean {
        if (value.isBlank()) return true
        if (fieldKind == "phone") return false
        if (subjectName.isNotBlank() && !textMatchesQuery(value, subjectName)) {
            if (fieldKind in setOf("name", "relative", "associate")) return true
        }
        if (hasGeoConstraint(city, state) && fieldKind in setOf("address", "location", "relative")) {
            if (!matchesLocation(value, city, state)) return true
        }
        return false
    }
}
