package com.twoskoops707.sixdegrees.domain

import com.twoskoops707.sixdegrees.domain.model.SubjectProfile

/**
 * Curated Google dork templates organized by subject type and category.
 * Placeholders: {name}, {first}, {last}, {city}, {state}, {phone}, {email}, {employer}
 */
object GoogleDorkLibrary {

    enum class DorkCategory(val key: String, val displayName: String) {
        SOCIAL("social", "Social"),
        PEOPLE("people", "People Sites"),
        COURTS("courts", "Courts & Legal"),
        CORPORATE("corporate", "Corporate"),
        PROPERTY("property", "Property"),
        NEWS("news", "News"),
        GOV("gov", "Government"),
        EMAIL("email", "Email"),
        PHONE("phone", "Phone"),
        RELATIVES("relatives", "Relatives"),
        IDENTITY("identity", "Identity"),
        DOCUMENTS("documents", "Documents"),
        LEAKS("leaks", "Leaks & Breaches")
    }

    enum class DorkSearchType { PERSON, PHONE, EMAIL, COMPANY }

    data class DorkTemplate(
        val id: String,
        val category: DorkCategory,
        val label: String,
        val queryTemplate: String,
        val minPhase: SearchPhase = SearchPhase.CANDIDATE_DISCOVERY,
        val requires: Set<String> = emptySet()
    )

    data class ResolvedDork(
        val template: DorkTemplate,
        val query: String
    )

    fun resolveEmployer(profile: SubjectProfile): String {
        if (profile.employer.isNotBlank()) return profile.employer
        val intent = profile.intent
        if (intent.startsWith("employer=", ignoreCase = true)) {
            return intent.removePrefix("employer=").trim()
        }
        return ""
    }

    fun expand(template: String, profile: SubjectProfile): String {
        val employer = resolveEmployer(profile)
        val city = profile.city.trim()
        val state = profile.state.trim()
        val locQuoted = buildList {
            if (city.isNotBlank()) add("\"$city\"")
            if (state.isNotBlank()) add("\"$state\"")
        }.joinToString(" ")
        val locPlain = listOf(city, state).filter { it.isNotBlank() }.joinToString(" ")
        val locCityState = if (city.isNotBlank() && state.isNotBlank()) "\"$city $state\"" else locQuoted

        val phoneDigits = profile.phone.filter { it.isDigit() }

        return template
            .replace("{name}", profile.name.takeIf { it.isNotBlank() }?.let { "\"$it\"" } ?: "")
            .replace("{first}", profile.firstName.takeIf { it.isNotBlank() }?.let { "\"$it\"" } ?: "")
            .replace("{last}", profile.lastName.takeIf { it.isNotBlank() }?.let { "\"$it\"" } ?: "")
            .replace("{city}", city.takeIf { it.isNotBlank() }?.let { "\"$it\"" } ?: city)
            .replace("{state}", state.takeIf { it.isNotBlank() }?.let { "\"$state\"" } ?: state)
            .replace("{phone}", profile.phone.takeIf { it.isNotBlank() }?.let { "\"$it\"" } ?: "")
            .replace("{phone_digits}", phoneDigits.takeIf { it.length >= 7 }?.let { "\"$it\"" } ?: "")
            .replace("{email}", profile.email.takeIf { it.isNotBlank() }?.let { "\"$it\"" } ?: "")
            .replace("{employer}", employer.takeIf { it.isNotBlank() }?.let { "\"$it\"" } ?: "")
            .replace("{loc_quoted}", locQuoted)
            .replace("{loc_plain}", locPlain)
            .replace("{loc_city_state}", locCityState)
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private val PERSON_DORKS = listOf(
        // Social
        DorkTemplate("linkedin", DorkCategory.SOCIAL, "LinkedIn Profile",
            "{name} {loc_quoted} site:linkedin.com", requires = setOf("name")),
        DorkTemplate("facebook_ig", DorkCategory.SOCIAL, "Facebook / Instagram",
            "{name} {loc_plain} site:facebook.com OR site:instagram.com", requires = setOf("name")),
        DorkTemplate("social_broad", DorkCategory.SOCIAL, "Social Media Broad",
            "{name} site:facebook.com OR site:instagram.com OR site:twitter.com OR site:linkedin.com -login",
            requires = setOf("name")),
        DorkTemplate("social_forums", DorkCategory.SOCIAL, "Forum & Community",
            "{name} {loc_plain} site:reddit.com OR site:quora.com OR site:medium.com", requires = setOf("name")),

        // People sites
        DorkTemplate("people_tps_fps_tt", DorkCategory.PEOPLE, "People Finders (TPS/FPS/TT)",
            "{name} {loc_quoted} site:fastpeoplesearch.com OR site:truepeoplesearch.com OR site:thatsthem.com",
            requires = setOf("name")),
        DorkTemplate("people_rad_spk_wp", DorkCategory.PEOPLE, "People Finders (Radaris/Spokeo/WP)",
            "{name} {loc_plain} site:radaris.com OR site:spokeo.com OR site:whitepages.com", requires = setOf("name")),
        DorkTemplate("people_tps", DorkCategory.PEOPLE, "TruePeopleSearch",
            "site:truepeoplesearch.com {name} {loc_plain}", requires = setOf("name")),
        DorkTemplate("people_fps", DorkCategory.PEOPLE, "FastPeopleSearch",
            "site:fastpeoplesearch.com {name} {loc_plain}", requires = setOf("name")),
        DorkTemplate("people_wp", DorkCategory.PEOPLE, "Whitepages",
            "site:whitepages.com {name} {loc_plain}", requires = setOf("name")),
        DorkTemplate("people_rad", DorkCategory.PEOPLE, "Radaris",
            "site:radaris.com {name} {loc_plain}", requires = setOf("name")),
        DorkTemplate("people_spk", DorkCategory.PEOPLE, "Spokeo",
            "site:spokeo.com {name} {loc_plain}", requires = setOf("name")),
        DorkTemplate("people_bv", DorkCategory.PEOPLE, "BeenVerified",
            "site:beenverified.com {name} {loc_plain}", requires = setOf("name")),
        DorkTemplate("people_zaba", DorkCategory.PEOPLE, "ZabaSearch",
            "site:zabasearch.com {name} {loc_plain}", requires = setOf("name")),
        DorkTemplate("people_411", DorkCategory.PEOPLE, "411.com",
            "site:411.com {name} {loc_plain}", requires = setOf("name")),

        // Identity / contact
        DorkTemplate("identity_contact", DorkCategory.IDENTITY, "Profile & Contact",
            "{name} {loc_plain} profile contact", requires = setOf("name")),
        DorkTemplate("identity_phone_addr", DorkCategory.IDENTITY, "Phone or Address",
            "{name} {loc_city_state} phone OR address", requires = setOf("name")),
        DorkTemplate("identity_address", DorkCategory.IDENTITY, "Address Lookup",
            "{name} address {loc_plain}", requires = setOf("name")),
        DorkTemplate("identity_street", DorkCategory.IDENTITY, "Street Address",
            "{name} street address {loc_plain}", requires = setOf("name")),
        DorkTemplate("identity_phone", DorkCategory.IDENTITY, "Phone Number",
            "{name} phone number {loc_plain}", requires = setOf("name")),
        DorkTemplate("identity_bio", DorkCategory.IDENTITY, "Professional Bio",
            "{name} linkedin bio resume {loc_plain}", requires = setOf("name")),
        DorkTemplate("identity_intitle", DorkCategory.IDENTITY, "Exact Name in Title",
            "{name} {loc_plain} intitle:{name}", requires = setOf("name")),
        DorkTemplate("identity_email_pattern", DorkCategory.EMAIL, "Email Patterns",
            "{name} {loc_city_state} email OR @gmail OR @yahoo", requires = setOf("name")),

        // Courts & legal (deep)
        DorkTemplate("courts_arrest", DorkCategory.COURTS, "Arrest & Court Records",
            "{name} {loc_quoted} arrest OR court OR \"case number\"", minPhase = SearchPhase.DEEP_INVESTIGATION,
            requires = setOf("name")),
        DorkTemplate("courts_listener", DorkCategory.COURTS, "CourtListener / JudyRecords",
            "{name} site:courtlistener.com OR site:judyrecords.com OR site:pacer.gov",
            minPhase = SearchPhase.DEEP_INVESTIGATION, requires = setOf("name")),
        DorkTemplate("courts_criminal", DorkCategory.COURTS, "Criminal Records",
            "{name} criminal arrest record {loc_plain}", minPhase = SearchPhase.DEEP_INVESTIGATION,
            requires = setOf("name")),
        DorkTemplate("courts_mugshot", DorkCategory.COURTS, "Mugshot Sites",
            "{name} site:mugshots.com OR site:jailbase.com OR site:arrests.org",
            minPhase = SearchPhase.DEEP_INVESTIGATION, requires = setOf("name")),
        DorkTemplate("courts_voter", DorkCategory.COURTS, "Voter Registration",
            "{name} voter registration {state}", minPhase = SearchPhase.DEEP_INVESTIGATION, requires = setOf("name")),

        // Corporate / financial (deep)
        DorkTemplate("corp_sec", DorkCategory.CORPORATE, "SEC Filings",
            "{name} {loc_quoted} site:sec.gov OR \"Form 4\" OR \"10-K\"", minPhase = SearchPhase.DEEP_INVESTIGATION,
            requires = setOf("name")),
        DorkTemplate("corp_opencorp", DorkCategory.CORPORATE, "OpenCorporates Officers",
            "{name} {loc_plain} site:opencorporates.com OR officer OR director",
            minPhase = SearchPhase.DEEP_INVESTIGATION, requires = setOf("name")),
        DorkTemplate("corp_financial", DorkCategory.CORPORATE, "Financial Disclosures",
            "{name} site:sec.gov OR site:opencorporates.com officer director",
            minPhase = SearchPhase.DEEP_INVESTIGATION, requires = setOf("name")),
        DorkTemplate("corp_employer", DorkCategory.CORPORATE, "Employer Cross-Reference",
            "{name} {employer} employee OR director OR officer OR VP",
            minPhase = SearchPhase.DEEP_INVESTIGATION, requires = setOf("name", "employer")),

        // Property (deep)
        DorkTemplate("property_deed", DorkCategory.PROPERTY, "Property & Deeds",
            "{name} {loc_quoted} property OR deed OR assessor", minPhase = SearchPhase.DEEP_INVESTIGATION,
            requires = setOf("name")),
        DorkTemplate("property_records", DorkCategory.PROPERTY, "Property Records",
            "{name} property deed records {state}", minPhase = SearchPhase.DEEP_INVESTIGATION,
            requires = setOf("name")),

        // News (deep)
        DorkTemplate("news_google", DorkCategory.NEWS, "Google News",
            "{name} {loc_plain} site:news.google.com OR site:legacy.com", minPhase = SearchPhase.DEEP_INVESTIGATION,
            requires = setOf("name")),
        DorkTemplate("news_articles", DorkCategory.NEWS, "News Articles",
            "{name} news article {loc_plain}", minPhase = SearchPhase.DEEP_INVESTIGATION, requires = setOf("name")),
        DorkTemplate("news_media", DorkCategory.NEWS, "Major Media",
            "{name} inurl:news OR site:reuters.com OR site:apnews.com OR site:nytimes.com",
            minPhase = SearchPhase.DEEP_INVESTIGATION, requires = setOf("name")),

        // Government (deep)
        DorkTemplate("gov_docs", DorkCategory.GOV, "Government Documents",
            "{name} {loc_plain} site:gov OR site:court OR filetype:pdf", minPhase = SearchPhase.DEEP_INVESTIGATION,
            requires = setOf("name")),
        DorkTemplate("gov_pii", DorkCategory.GOV, "PII in Gov Docs",
            "{name} (\"date of birth\" OR \"DOB\" OR \"SSN\") site:*.gov filetype:pdf",
            minPhase = SearchPhase.DEEP_INVESTIGATION, requires = setOf("name")),
        DorkTemplate("gov_license", DorkCategory.GOV, "Professional Licenses",
            "{name} licensed OR license OR certification site:*.gov", minPhase = SearchPhase.DEEP_INVESTIGATION,
            requires = setOf("name")),

        // Relatives (deep)
        DorkTemplate("relatives_family", DorkCategory.RELATIVES, "Relatives & Associates",
            "{name} {loc_quoted} relatives OR associate OR spouse", minPhase = SearchPhase.DEEP_INVESTIGATION,
            requires = setOf("name")),
        DorkTemplate("relatives_mapping", DorkCategory.RELATIVES, "Relationship Mapping",
            "{name} (\"married to\" OR \"husband\" OR \"wife\" OR \"son of\" OR \"daughter of\")",
            minPhase = SearchPhase.DEEP_INVESTIGATION, requires = setOf("name")),

        // Documents & leaks (deep)
        DorkTemplate("docs_files", DorkCategory.DOCUMENTS, "Document Files",
            "{name} filetype:pdf OR filetype:doc OR filetype:xls", minPhase = SearchPhase.DEEP_INVESTIGATION,
            requires = setOf("name")),
        DorkTemplate("docs_paste", DorkCategory.LEAKS, "Pastebin / Dumps",
            "{name} site:pastebin.com OR site:rentry.co OR site:paste.ee",
            minPhase = SearchPhase.DEEP_INVESTIGATION, requires = setOf("name")),
        DorkTemplate("leaks_pii", DorkCategory.LEAKS, "Credential Exposure",
            "{name} (password OR passwd OR \"social security\" OR SSN) -site:whitepages.com",
            minPhase = SearchPhase.DEEP_INVESTIGATION, requires = setOf("name")),
        DorkTemplate("docs_cached", DorkCategory.DOCUMENTS, "Cached Old Profiles",
            "{name} cache:facebook.com OR cache:twitter.com OR cache:linkedin.com",
            minPhase = SearchPhase.DEEP_INVESTIGATION, requires = setOf("name"))
    )

    private val PHONE_DORKS = listOf(
        DorkTemplate("phone_exact", DorkCategory.PHONE, "Exact Phone Match",
            "\"{phone}\"", requires = setOf("phone")),
        DorkTemplate("phone_digits", DorkCategory.PHONE, "Phone Digits Only",
            "\"{phone_digits}\"", requires = setOf("phone")),
        DorkTemplate("phone_caller_id", DorkCategory.PHONE, "Caller ID Sites",
            "{phone} site:800notes.com OR site:whocallsme.com phone", requires = setOf("phone")),
        DorkTemplate("phone_owner", DorkCategory.PHONE, "Phone Owner Lookup",
            "\"{phone}\" owner OR name", requires = setOf("phone")),
        DorkTemplate("phone_reverse", DorkCategory.PHONE, "Reverse Phone Lookup",
            "\"{phone}\" site:truecaller.com OR site:whitepages.com OR site:anywho.com", requires = setOf("phone")),
        DorkTemplate("phone_records", DorkCategory.PHONE, "Phone in Public Records",
            "\"{phone}\" contact OR call OR reach -ads", requires = setOf("phone"))
    )

    private val EMAIL_DORKS = listOf(
        DorkTemplate("email_breach", DorkCategory.LEAKS, "Breach Exposure",
            "{email} site:haveibeenpwned.com OR breach OR leak", requires = setOf("email")),
        DorkTemplate("email_social", DorkCategory.EMAIL, "Email on Social/Dev",
            "{email} site:linkedin.com OR site:github.com", requires = setOf("email")),
        DorkTemplate("email_paste", DorkCategory.LEAKS, "Email in Pastes",
            "{email} (password OR hash OR dump OR leak) site:pastebin.com", requires = setOf("email")),
        DorkTemplate("email_people", DorkCategory.PEOPLE, "Email People Search",
            "{email} site:truepeoplesearch.com OR site:whitepages.com OR site:spokeo.com", requires = setOf("email")),
        DorkTemplate("email_account", DorkCategory.EMAIL, "Email Account Lookups",
            "{email} account profile breach", requires = setOf("email"))
    )

    private val COMPANY_DORKS = listOf(
        DorkTemplate("company_sec", DorkCategory.CORPORATE, "SEC / OpenCorporates",
            "\"{name}\" {loc_plain} site:sec.gov OR site:opencorporates.com", requires = setOf("name")),
        DorkTemplate("company_officers", DorkCategory.CORPORATE, "Officers & Founders",
            "\"{name}\" CEO OR founder OR officer OR director", requires = setOf("name")),
        DorkTemplate("company_filings", DorkCategory.CORPORATE, "Business Filings",
            "\"{name}\" LLC OR incorporation OR \"registered agent\" {state}", requires = setOf("name")),
        DorkTemplate("company_news", DorkCategory.NEWS, "Company News",
            "\"{name}\" news OR press release {loc_plain}", requires = setOf("name")),
        DorkTemplate("company_web", DorkCategory.IDENTITY, "Company Web Presence",
            "\"{name}\" about OR contact OR headquarters {loc_plain}", requires = setOf("name"))
    )

    fun dorksFor(
        searchType: DorkSearchType,
        profile: SubjectProfile,
        phase: SearchPhase
    ): List<ResolvedDork> {
        val templates = when (searchType) {
            DorkSearchType.PERSON -> PERSON_DORKS
            DorkSearchType.PHONE -> PHONE_DORKS
            DorkSearchType.EMAIL -> EMAIL_DORKS
            DorkSearchType.COMPANY -> COMPANY_DORKS
        }
        return templates
            .filter { phaseIncludes(it.minPhase, phase) }
            .filter { meetsRequirements(it, profile) }
            .mapNotNull { tmpl ->
                val query = expand(tmpl.queryTemplate, profile)
                if (query.isBlank() || query.length < 4) null
                else ResolvedDork(tmpl, query)
            }
            .distinctBy { it.query }
    }

    fun dorksForBuilder(profile: SubjectProfile): List<Pair<String, String>> =
        dorksFor(DorkSearchType.PERSON, profile, SearchPhase.DEEP_INVESTIGATION)
            .plus(
                if (profile.phone.isNotBlank()) dorksFor(DorkSearchType.PHONE, profile, SearchPhase.DEEP_INVESTIGATION) else emptyList()
            )
            .plus(
                if (profile.email.isNotBlank()) dorksFor(DorkSearchType.EMAIL, profile, SearchPhase.DEEP_INVESTIGATION) else emptyList()
            )
            .map { it.template.label to it.query }

    fun countFor(searchType: DorkSearchType, profile: SubjectProfile, phase: SearchPhase): Int =
        dorksFor(searchType, profile, phase).size

    fun categoriesFor(searchType: DorkSearchType, profile: SubjectProfile, phase: SearchPhase): Map<DorkCategory, Int> =
        dorksFor(searchType, profile, phase)
            .groupBy { it.template.category }
            .mapValues { (_, list) -> list.size }

    /** Legacy metadata key mapping for backward-compatible dossier sections. */
    fun legacyMetaKey(category: DorkCategory): String = when (category) {
        DorkCategory.SOCIAL -> "dork_social_results"
        DorkCategory.PEOPLE -> "dork_tps_results"
        DorkCategory.COURTS -> "dork_court_results"
        DorkCategory.CORPORATE -> "dork_financial_results"
        DorkCategory.PROPERTY -> "dork_property_results"
        DorkCategory.NEWS -> "dork_news_results"
        DorkCategory.GOV -> "dork_gov_results"
        DorkCategory.EMAIL -> "dork_email_results"
        DorkCategory.PHONE -> "dork_phone_results"
        DorkCategory.RELATIVES -> "dork_relatives_results"
        DorkCategory.IDENTITY -> "dork_identity_results"
        DorkCategory.DOCUMENTS -> "dork_files_results"
        DorkCategory.LEAKS -> "dork_leaks_results"
    }

    private fun phaseIncludes(minPhase: SearchPhase, current: SearchPhase): Boolean =
        when (minPhase) {
            SearchPhase.CANDIDATE_DISCOVERY -> true
            SearchPhase.DEEP_INVESTIGATION -> current == SearchPhase.DEEP_INVESTIGATION
        }

    private fun meetsRequirements(tmpl: DorkTemplate, profile: SubjectProfile): Boolean {
        tmpl.requires.forEach { req ->
            when (req) {
                "name" -> if (profile.name.isBlank()) return false
                "phone" -> if (profile.phone.isBlank()) return false
                "email" -> if (profile.email.isBlank()) return false
                "employer" -> if (resolveEmployer(profile).isBlank()) return false
                "city" -> if (profile.city.isBlank()) return false
                "state" -> if (profile.state.isBlank()) return false
            }
        }
        return true
    }

}
