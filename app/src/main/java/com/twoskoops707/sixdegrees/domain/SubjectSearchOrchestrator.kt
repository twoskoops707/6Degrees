package com.twoskoops707.sixdegrees.domain

import com.twoskoops707.sixdegrees.domain.model.SubjectProfile

/**
 * Two-phase person search: round-1 candidate discovery, round-2 deep investigation after lock.
 * Deep Investigation mode targets 5–10 minutes total via exhaustive source sweeps.
 */
enum class SearchPhase {
    CANDIDATE_DISCOVERY,
    DEEP_INVESTIGATION
}

object SubjectSearchOrchestrator {

    val SAFETY_INTENTS = setOf("first_date", "meeting_new", "verify_identity", "fraud")

    fun normalizeIntent(intent: String): String = when (intent.lowercase()) {
        "identity" -> "verify_identity"
        "background" -> "first_date"
        "locate" -> "meeting_new"
        else -> intent.lowercase()
    }

    fun isSafetyIntent(intent: String): Boolean =
        intent.isBlank() || normalizeIntent(intent) in SAFETY_INTENTS

    fun extraDeepScrapersForIntent(intent: String): Set<String> = when (normalizeIntent(intent)) {
        "first_date", "meeting_new" -> setOf(
            "DarkSearch", "Ahmia", "CourtListener", "HIBP", "ProxyNova Breach",
            "ThatsThem", "FastPeopleSearch", "TruePeopleSearch", "BeenVerified"
        )
        "verify_identity" -> setOf(
            "DarkSearch", "Ahmia", "Pipl", "People Data Labs", "Holehe", "Sherlock"
        )
        "fraud" -> setOf(
            "DarkSearch", "Ahmia", "CourtListener", "OpenSanctions", "FBI Wanted",
            "IPQualityScore", "EmailRep.io"
        )
        else -> emptySet()
    }

    fun shouldRunDarkWeb(phase: SearchPhase, intent: String): Boolean =
        isDeepPhase(phase) || (intent.isNotBlank() && isSafetyIntent(intent))

    const val PARALLEL_WORKERS = 9

    /** Parallel in-app dork execution via DDG HTML / optional Google CSE. */
    const val DORK_PARALLEL_WORKERS = 8

    const val SOURCE_TIMEOUT_MS = 25_000L

    /** Phase 1 target: 2–3 minutes of discovery work. */
    const val MIN_DISCOVERY_MS = 150_000L

    /** Phase 2 target: 5–8 minutes of deep investigation work. */
    const val MIN_DEEP_MS = 300_000L

    /**
     * Simple mode still runs a real investigation — a person cannot be found in seconds.
     * Discovery runs ~2.5 minutes, deep investigation ~5 minutes, regardless of mode.
     */
    const val MIN_DISCOVERY_FAST_MS = 150_000L
    const val MIN_DEEP_FAST_MS = 300_000L

    /** Floor before secondary DDG passes kick in. */
    const val SECONDARY_PASS_THRESHOLD_MS = 240_000L
    const val SECONDARY_PASS_THRESHOLD_FAST_MS = 240_000L

    fun resolvePhase(type: String, round: Int, profile: SubjectProfile): SearchPhase {
        val effectiveType = if (type == "scan") "person" else type
        return when {
            profile.locked -> SearchPhase.DEEP_INVESTIGATION
            effectiveType == "comprehensive" -> SearchPhase.DEEP_INVESTIGATION
            isPhoneOnlySearch(effectiveType, profile) -> SearchPhase.DEEP_INVESTIGATION
            isEmailOnlySearch(effectiveType, profile) -> SearchPhase.DEEP_INVESTIGATION
            effectiveType == "person" && round >= 2 -> SearchPhase.DEEP_INVESTIGATION
            effectiveType == "person" && round == 1 -> SearchPhase.CANDIDATE_DISCOVERY
            else -> SearchPhase.DEEP_INVESTIGATION
        }
    }

    fun minimumDurationMs(phase: SearchPhase, fastMode: Boolean = false): Long = when (phase) {
        SearchPhase.CANDIDATE_DISCOVERY ->
            if (fastMode) MIN_DISCOVERY_FAST_MS else MIN_DISCOVERY_MS
        SearchPhase.DEEP_INVESTIGATION ->
            if (fastMode) MIN_DEEP_FAST_MS else MIN_DEEP_MS
    }

    fun secondaryPassThresholdMs(fastMode: Boolean = false): Long =
        if (fastMode) SECONDARY_PASS_THRESHOLD_FAST_MS else SECONDARY_PASS_THRESHOLD_MS

    fun phaseLabel(phase: SearchPhase): String = when (phase) {
        SearchPhase.CANDIDATE_DISCOVERY -> "Discovery"
        SearchPhase.DEEP_INVESTIGATION -> "Deep scan"
    }

    fun phaseDurationHint(phase: SearchPhase, fastMode: Boolean = false): String = when {
        phase == SearchPhase.CANDIDATE_DISCOVERY -> "Discovery — typically 2–3 minutes"
        else -> "Deep investigation — typically 5–10 minutes"
    }

    fun isDeepPhase(phase: SearchPhase): Boolean = phase == SearchPhase.DEEP_INVESTIGATION

    fun isPhoneOnlySearch(type: String, profile: SubjectProfile): Boolean =
        type == "phone" || (type == "person" && profile.phone.isNotBlank() &&
            profile.name.isBlank() && profile.email.isBlank() && profile.username.isBlank())

    fun isEmailOnlySearch(type: String, profile: SubjectProfile): Boolean =
        (type == "email" || type == "breach") ||
            (type == "person" && profile.email.isNotBlank() &&
                profile.name.isBlank() && profile.phone.isBlank() && profile.username.isBlank())

    fun skipsCandidateSelection(type: String, profile: SubjectProfile): Boolean =
        isPhoneOnlySearch(type, profile) || isEmailOnlySearch(type, profile) || profile.locked

    /** Round-1 DDG labels — people-finder focused, no courts/family/vehicles. */
    val DISCOVERY_DDG_LABELS = setOf(
        "General", "Phone", "Address", "FPS", "Whitepages", "Spokeo", "Radaris",
        "BeenVerified", "TruePeopleSearch", "PeopleFinder", "LinkedIn", "Facebook",
        "News", "PhoneCrossRef", "EmailCrossRef", "UsernameCrossRef", "AKACrossRef", "DOB"
    )

    /** Round-2 DDG labels — courts, family, property, employment, vehicles. */
    val DEEP_DDG_LABELS = setOf(
        "Relatives", "Criminal", "Employment", "Property", "Voter"
    )

    fun ddgLabelsForPhase(phase: SearchPhase): Set<String> = when (phase) {
        SearchPhase.CANDIDATE_DISCOVERY -> DISCOVERY_DDG_LABELS
        SearchPhase.DEEP_INVESTIGATION -> DISCOVERY_DDG_LABELS + DEEP_DDG_LABELS
    }

    /** Estimated auto-dork queries per phase (from [GoogleDorkLibrary]). */
    fun estimatedDorkCount(phase: SearchPhase): Int = when (phase) {
        SearchPhase.CANDIDATE_DISCOVERY -> 26
        SearchPhase.DEEP_INVESTIGATION -> 40
    }

    /** Extra DDG passes when primary sweep finishes too quickly. */
    fun secondaryDdgPassQueries(
        name: String,
        city: String,
        state: String,
        phone: String,
        email: String,
        phase: SearchPhase,
        passIndex: Int,
        context: String = "",
        aka: String = ""
    ): List<Pair<String, String>> {
        val loc = listOf(city, state).filter { it.isNotBlank() }.joinToString(" ")
        val ctx = context.trim().takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        val akaQ = aka.trim().takeIf { it.isNotBlank() }?.let { " \"$it\"" }.orEmpty()
        val q = "\"$name\""
        val base = mutableListOf<Pair<String, String>>()
        if (akaQ.isNotBlank()) {
            base += "Secondary: AKA${passIndex}" to "\"$name\" \"$aka\"${if (loc.isNotBlank()) " $loc" else ""}"
        }
        when (passIndex) {
            0 -> {
                base += "Secondary: Mugshot" to "$q mugshot arrest${if (loc.isNotBlank()) " $loc" else ""}"
                base += "Secondary: Obituary" to "$q obituary${if (state.isNotBlank()) " $state" else ""}"
                base += "Secondary: Alumni" to "$q alumni university college${if (loc.isNotBlank()) " $loc" else ""}"
                base += "Secondary: Business" to "$q LLC owner business${if (state.isNotBlank()) " $state" else ""}"
            }
            1 -> {
                base += "Secondary: Divorce" to "$q divorce court filing${if (state.isNotBlank()) " $state" else ""}"
                base += "Secondary: License" to "$q professional license${if (state.isNotBlank()) " $state" else ""}"
                base += "Secondary: Donation" to "$q political donation FEC"
                base += "Secondary: Patent" to "$q patent inventor"
            }
            2 -> {
                base += "Secondary: Charity" to "$q nonprofit board charity"
                base += "Secondary: HOA" to "$q HOA homeowner association"
                base += "Secondary: Permits" to "$q building permit${if (city.isNotBlank()) " $city" else ""}"
                base += "Secondary: Reviews" to "$q yelp google review"
            }
            else -> {
                base += "Secondary: Pass${passIndex}: Forum" to "$q site:reddit.com OR site:quora.com"
                base += "Secondary: Pass${passIndex}: Archive" to "$q site:web.archive.org"
                base += "Secondary: Pass${passIndex}: PDF" to "$q filetype:pdf resume CV"
                base += "Secondary: Pass${passIndex}: Court2" to "$q site:pacer.gov OR site:unicourt.com"
            }
        }
        if (phone.isNotBlank()) base += "Secondary: Phone${passIndex}" to "\"$phone\" owner caller id reverse lookup"
        if (email.isNotBlank()) base += "Secondary: Email${passIndex}" to "\"$email\" account profile breach"
        if (phase == SearchPhase.DEEP_INVESTIGATION) {
            base += "Secondary: Dark${passIndex}" to "$q onion darknet mention"
            base += "Secondary: SEC${passIndex}" to "$q site:sec.gov insider filing"
        }
        // Every pass gets the context + aka terms appended so the sweep narrows toward
        // the person/thing the user actually described.
        if (ctx.isBlank() && akaQ.isBlank()) return base
        return base.map { (label, query) -> label to "$query$ctx$akaQ" }
    }

    /** Targeted scrapers that only run after the subject is locked or in deep phase. */
    val DEEP_ONLY_SCRAPERS = setOf(
        "DarkSearch", "Ahmia", "CourtListener", "GLEIF",
        "SEC EDGAR", "SEC EDGAR Form-4", "Wikidata",
        "OpenSanctions", "OpenCorporates", "OpenCorporates Officers"
    )

    fun shouldRunScraper(
        scraperName: String,
        phase: SearchPhase,
        intent: String = "",
        activeCategories: Set<String>? = null
    ): Boolean {
        if (activeCategories != null && !shouldRunForPreset(scraperName, activeCategories)) return false
        val deepOnly = scraperName in DEEP_ONLY_SCRAPERS
        val allowSafetyOverride = intent.isNotBlank() && isSafetyIntent(intent) && deepOnly
        if (deepOnly && !isDeepPhase(phase) && !allowSafetyOverride) return false
        return true
    }

    /** Maps scraper / DDG label names to SearchPresetManager category ids. */
    fun scraperCategory(scraperName: String): String {
        val n = scraperName.lowercase()
        return when {
            n.contains("darksearch") || n.contains("ahmia") || n.contains("dark") || n.contains("onion") -> "darknet"
            n.contains("court") || n.contains("judy") || n.contains("voter") || n.contains("criminal")
                || n.contains("arrest") || n.contains("mugshot") -> "records"
            n.contains("sec") || n.contains("edgar") || n.contains("fec") || n.contains("gleif")
                || n.contains("financial") -> "finance"
            n.contains("opencorporates") || n.contains("corpwiki") || n.contains("clearbit")
                && !n.contains("person") -> "company"
            n.contains("hibp") || n.contains("breach") || n.contains("leak") || n.contains("pwned")
                || n.contains("proxynova") || n.contains("dehashed") || n.contains("snusbase") -> "breach"
            n.contains("800notes") || n.contains("phone") || n.contains("libphone") || n.contains("calltracer")
                || n.contains("numverify") || n.contains("truecaller") || n.contains("usphonebook") -> "phone"
            n.contains("email") || n.contains("gravatar") || n.contains("holehe") || n.contains("kickbox")
                || n.contains("emailrep") -> "email"
            n.contains("github") || n.contains("reddit") || n.contains("linkedin") || n.contains("facebook")
                || n.contains("instagram") || n.contains("social") || n.contains("sherlock")
                || n.contains("maigret") || n.contains("username") -> "social"
            n.contains("shodan") || n.contains("virustotal") || n.contains("abuseipdb")
                || n.contains("urlhaus") || n.contains("urlscan") || n.contains("otx")
                || n.contains("fbi") || n.contains("opensanctions") || n.contains("threat") -> "threat"
            n.contains("rdap") || n.contains("crt") || n.contains("whois") || n.contains("dns")
                || n.contains("ip-") || n.contains("ipinfo") || n.contains("bgp") -> "domain"
            n.contains("wigle") || n.contains("zippopotam") || n.contains("geo") -> "geo"
            n.contains("image") || n.contains("exif") || n.contains("face") || n.contains("photo") -> "image"
            n.contains("wikipedia") || n.contains("wikidata") || n.contains("news")
                || n.contains("pipl") || n.contains("pdl") || n.contains("people")
                || n.contains("thats") || n.contains("fastpeople") || n.contains("whitepages")
                || n.contains("spokeo") || n.contains("radaris") || n.contains("beenverified")
                || n.contains("truepeople") || n.contains("peekyou") || n.contains("zaba")
                || n.contains("411") || n.contains("npi") || n.contains("demographics")
                || n.startsWith("ddg:") -> "person"
            else -> "person"
        }
    }

    fun shouldRunForPreset(scraperName: String, activeCategories: Set<String>): Boolean {
        if (activeCategories.isEmpty()) return true
        return scraperCategory(scraperName) in activeCategories
    }

    fun ddgLabelsFiltered(phase: SearchPhase, activeCategories: Set<String>?): Set<String> {
        val base = ddgLabelsForPhase(phase)
        if (activeCategories == null) return base
        return base.filter { label -> shouldRunForPreset("DDG: $label", activeCategories) }.toSet()
    }

    /** People-finder registry URLs are followed in-app during dork execution; bulk registry scrape stays off. */
    fun shouldAutoScrapeRegistry(): Boolean = false

    fun estimatedSourceCount(type: String, phase: SearchPhase, profile: SubjectProfile): Int {
        val effectiveType = if (type == "scan") "person" else type
        val base = when (effectiveType) {
            "person", "comprehensive" -> when (phase) {
                SearchPhase.CANDIDATE_DISCOVERY -> 68
                SearchPhase.DEEP_INVESTIGATION -> 118
            }
            "phone" -> 41
            "email", "breach" -> 27
            "username" -> 80
            "domain", "ip" -> 28
            "company" -> 23
            else -> 12
        }
        var extra = 0
        if (profile.phone.isNotBlank()) extra += 2
        if (profile.email.isNotBlank()) extra += 2
        if (profile.username.isNotBlank()) extra += 2
        if (profile.aka.isNotBlank()) extra += 2
        if (profile.dob.isNotBlank()) extra += 1
        if (profile.middleName.isNotBlank()) extra += 1
        if (SubjectFilter.hasGeoConstraint(profile.city, profile.state)) extra += 1
        return base + extra
    }

    fun formatConfidence(reliability: Double): String =
        "%.2f".format(reliability.coerceIn(0.0, 1.0))

    fun candidateConfidence(sourceCount: Int, index: Int, hasGeoMatch: Boolean): Float {
        val base = sourceCount * 0.10f + if (hasGeoMatch) 0.15f else 0f
        return (base - index * 0.04f).coerceIn(0.15f, 0.95f)
    }

    fun autoSelectAllowed(name: String, candidateCount: Int): Boolean {
        if (SubjectFilter.isCommonName(name)) return false
        return candidateCount == 1
    }
}
