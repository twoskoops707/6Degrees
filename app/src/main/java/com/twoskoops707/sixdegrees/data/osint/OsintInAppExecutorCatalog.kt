package com.twoskoops707.sixdegrees.data.osint

/**
 * In-app OSINT executors that write dossier metadata. Framework catalog entries must map here.
 */
object OsintInAppExecutorCatalog {

    enum class ExecutorId {
        USERNAME_SCAN,
        EMAIL_REGISTRATION,
        THATSTHEM,
        FAST_PEOPLE_SEARCH,
        US_PHONE_BOOK,
        COURT_LISTENER,
        EMAIL_REP,
        GRAVATAR,
        GITHUB,
        REDDIT,
        CRT_SH,
        AHMIA,
        EIGHT_HUNDRED_NOTES,
        CALL_TRACER,
        LIB_PHONE,
        SEC_EDGAR,
        WIKIDATA,
        PROXY_NOVA,
        WAYBACK,
        HACKER_TARGET,
        HIBP,
        OPEN_SANCTIONS,
        OPEN_CORPORATES,
        GLEIF,
        LEAK_CHECK,
        KICKBOX,
        GOOGLE_NEWS,
        IP_WHO,
        IP_API,
        IPINFO,
        BGP_VIEW,
        RDAP,
        SHODAN,
        VIRUS_TOTAL,
        URL_SCAN,
        ABUSE_IPDB,
        NUMVERIFY,
        DARK_SEARCH,
        URLHAUS,
        WIGLE,
        NAME_DEMOGRAPHICS,
        FBI_WANTED,
        NPI_REGISTRY,
        OPEN_FEC
    }

    data class ExecutorDef(
        val id: ExecutorId,
        val reportLabel: String,
        val catalogName: String,
        val catalogDescription: String,
        val metadataPrefixes: List<String>,
        val metadataExactKeys: Set<String> = emptySet(),
        val urlHosts: Set<String> = emptySet(),
        val frameworkFolders: Set<String> = emptySet(),
        val nameKeywords: Set<String> = emptySet(),
        val categories: Set<String> = setOf("tools")
    )

    val EXECUTORS: List<ExecutorDef> = listOf(
        ExecutorDef(
            id = ExecutorId.USERNAME_SCAN,
            reportLabel = "Username Scan",
            catalogName = "Username Scan (in-app)",
            catalogDescription = "Cross-platform username presence checks — Sherlock/Maigret equivalent, feeds profile URLs into your dossier.",
            metadataPrefixes = listOf("github_", "reddit_", "social_profiles"),
            metadataExactKeys = setOf(
                "sherlock_found", "maigret_found", "inhouse_username_found",
                "found_urls", "sites_found", "username_platform_summary"
            ),
            frameworkFolders = setOf("Username"),
            nameKeywords = setOf("whatsmyname", "namechk", "namecheck", "username search", "sylva"),
            categories = setOf("username", "social")
        ),
        ExecutorDef(
            id = ExecutorId.EMAIL_REGISTRATION,
            reportLabel = "Email Registration Scan",
            catalogName = "Email Registration Scan (in-app)",
            catalogDescription = "Holehe-style email registration checks across major services — results appear under Email Registered in reports.",
            metadataPrefixes = emptyList(),
            metadataExactKeys = setOf("holehe_services", "holehe_found"),
            frameworkFolders = setOf("Email Address"),
            nameKeywords = setOf("holehe", "email registration"),
            categories = setOf("email", "breach")
        ),
        ExecutorDef(
            id = ExecutorId.THATSTHEM,
            reportLabel = "ThatsThem",
            catalogName = "ThatsThem",
            catalogDescription = "People and phone reverse lookup scraped in-app.",
            metadataPrefixes = listOf("tt_"),
            urlHosts = setOf("thatsthem.com"),
            categories = setOf("person", "phone")
        ),
        ExecutorDef(
            id = ExecutorId.FAST_PEOPLE_SEARCH,
            reportLabel = "FastPeopleSearch",
            catalogName = "FastPeopleSearch",
            catalogDescription = "US people search scraped in-app for names, phones, and addresses.",
            metadataPrefixes = listOf("fps_"),
            urlHosts = setOf("fastpeoplesearch.com"),
            categories = setOf("person")
        ),
        ExecutorDef(
            id = ExecutorId.US_PHONE_BOOK,
            reportLabel = "USPhoneBook",
            catalogName = "USPhoneBook",
            catalogDescription = "US phone and name directory scraped in-app.",
            metadataPrefixes = listOf("uspb_"),
            urlHosts = setOf("usphonebook.com"),
            categories = setOf("person", "phone")
        ),
        ExecutorDef(
            id = ExecutorId.COURT_LISTENER,
            reportLabel = "CourtListener",
            catalogName = "CourtListener",
            catalogDescription = "Federal and state court records fetched in-app.",
            metadataPrefixes = listOf("courtlistener_", "court_"),
            metadataExactKeys = setOf("court_cases", "courtlistener_count"),
            urlHosts = setOf("courtlistener.com"),
            categories = setOf("person", "records")
        ),
        ExecutorDef(
            id = ExecutorId.EMAIL_REP,
            reportLabel = "EmailRep",
            catalogName = "EmailRep.io",
            catalogDescription = "Email reputation and risk signals pulled in-app.",
            metadataPrefixes = listOf("emailrep_"),
            urlHosts = setOf("emailrep.io"),
            categories = setOf("email")
        ),
        ExecutorDef(
            id = ExecutorId.GRAVATAR,
            reportLabel = "Gravatar",
            catalogName = "Gravatar",
            catalogDescription = "Gravatar profile and avatar metadata for an email address.",
            metadataPrefixes = listOf("gravatar_"),
            urlHosts = setOf("gravatar.com", "en.gravatar.com"),
            categories = setOf("email", "image")
        ),
        ExecutorDef(
            id = ExecutorId.GITHUB,
            reportLabel = "GitHub",
            catalogName = "GitHub Profile",
            catalogDescription = "Public GitHub profile and activity scraped in-app.",
            metadataPrefixes = listOf("github_"),
            urlHosts = setOf("github.com", "api.github.com"),
            nameKeywords = setOf("github user"),
            categories = setOf("username", "social")
        ),
        ExecutorDef(
            id = ExecutorId.REDDIT,
            reportLabel = "Reddit",
            catalogName = "Reddit Profile",
            catalogDescription = "Public Reddit user profile scraped in-app.",
            metadataPrefixes = listOf("reddit_"),
            nameKeywords = setOf("reddit user"),
            categories = setOf("username", "social")
        ),
        ExecutorDef(
            id = ExecutorId.CRT_SH,
            reportLabel = "crt.sh",
            catalogName = "crt.sh",
            catalogDescription = "Certificate transparency search for domains.",
            metadataPrefixes = listOf("crt_", "crtsh_"),
            urlHosts = setOf("crt.sh"),
            categories = setOf("domain")
        ),
        ExecutorDef(
            id = ExecutorId.AHMIA,
            reportLabel = "Ahmia",
            catalogName = "Ahmia",
            catalogDescription = "Tor hidden service index results added to dark-web report sections.",
            metadataPrefixes = listOf("ahmia_"),
            urlHosts = setOf("ahmia.fi"),
            categories = setOf("darknet")
        ),
        ExecutorDef(
            id = ExecutorId.EIGHT_HUNDRED_NOTES,
            reportLabel = "800notes",
            catalogName = "800notes",
            catalogDescription = "Phone spam reports and caller comments scraped in-app.",
            metadataPrefixes = listOf("800notes_"),
            urlHosts = setOf("800notes.com"),
            categories = setOf("phone")
        ),
        ExecutorDef(
            id = ExecutorId.CALL_TRACER,
            reportLabel = "CallTracer",
            catalogName = "CallTracer",
            catalogDescription = "Phone carrier and location lookup via in-app API scrape.",
            metadataPrefixes = listOf("calltracer_"),
            urlHosts = setOf("calltracer.io"),
            categories = setOf("phone")
        ),
        ExecutorDef(
            id = ExecutorId.LIB_PHONE,
            reportLabel = "libphonenumber",
            catalogName = "libphonenumber",
            catalogDescription = "International phone validation and carrier metadata.",
            metadataPrefixes = listOf("libphone_"),
            urlHosts = setOf("libphonenumberapi.com"),
            categories = setOf("phone")
        ),
        ExecutorDef(
            id = ExecutorId.SEC_EDGAR,
            reportLabel = "SEC EDGAR",
            catalogName = "SEC EDGAR",
            catalogDescription = "US securities filings search scraped in-app.",
            metadataPrefixes = listOf("sec_"),
            urlHosts = setOf("sec.gov", "efts.sec.gov"),
            categories = setOf("company", "finance")
        ),
        ExecutorDef(
            id = ExecutorId.WIKIDATA,
            reportLabel = "Wikidata",
            catalogName = "Wikidata",
            catalogDescription = "Structured entity data including employers and aliases.",
            metadataPrefixes = listOf("wikidata_"),
            urlHosts = setOf("wikidata.org"),
            categories = setOf("person", "company")
        ),
        ExecutorDef(
            id = ExecutorId.PROXY_NOVA,
            reportLabel = "ProxyNova",
            catalogName = "ProxyNova Breach DB",
            catalogDescription = "Breach database comb search for emails.",
            metadataPrefixes = listOf("proxynova_"),
            urlHosts = setOf("proxynova.com"),
            categories = setOf("email", "breach")
        ),
        ExecutorDef(
            id = ExecutorId.WAYBACK,
            reportLabel = "Wayback Machine",
            catalogName = "Wayback Machine",
            catalogDescription = "Historical web snapshots via CDX API.",
            metadataPrefixes = listOf("wayback_"),
            urlHosts = setOf("web.archive.org"),
            categories = setOf("domain", "tools")
        ),
        ExecutorDef(
            id = ExecutorId.HACKER_TARGET,
            reportLabel = "HackerTarget",
            catalogName = "HackerTarget",
            catalogDescription = "DNS, email, and host recon via in-app API calls.",
            metadataPrefixes = listOf("hackertarget"),
            urlHosts = setOf("hackertarget.com"),
            categories = setOf("domain", "email")
        ),
        ExecutorDef(
            id = ExecutorId.HIBP,
            reportLabel = "Have I Been Pwned",
            catalogName = "Have I Been Pwned",
            catalogDescription = "Breach exposure via HIBP API when configured.",
            metadataPrefixes = listOf("hibp_"),
            urlHosts = setOf("haveibeenpwned.com"),
            categories = setOf("email", "breach")
        ),
        ExecutorDef(
            id = ExecutorId.OPEN_SANCTIONS,
            reportLabel = "OpenSanctions",
            catalogName = "OpenSanctions",
            catalogDescription = "Sanctions and PEP screening when API key is set.",
            metadataPrefixes = listOf("opensanctions_"),
            urlHosts = setOf("opensanctions.org"),
            categories = setOf("person", "threat")
        ),
        ExecutorDef(
            id = ExecutorId.OPEN_CORPORATES,
            reportLabel = "OpenCorporates",
            catalogName = "OpenCorporates",
            catalogDescription = "Corporate officer search when API key is set.",
            metadataPrefixes = listOf("opencorporates_"),
            urlHosts = setOf("opencorporates.com"),
            categories = setOf("company")
        ),
        ExecutorDef(
            id = ExecutorId.GLEIF,
            reportLabel = "GLEIF",
            catalogName = "GLEIF",
            catalogDescription = "Legal entity identifier lookup scraped in-app.",
            metadataPrefixes = listOf("gleif_"),
            urlHosts = setOf("gleif.org", "search.gleif.org"),
            categories = setOf("company", "finance")
        ),
        ExecutorDef(
            id = ExecutorId.LEAK_CHECK,
            reportLabel = "LeakCheck",
            catalogName = "LeakCheck",
            catalogDescription = "Public leak check API for emails and usernames.",
            metadataPrefixes = listOf("leakcheck_"),
            urlHosts = setOf("leakcheck.io", "leakcheck.net"),
            categories = setOf("email", "breach")
        ),
        ExecutorDef(
            id = ExecutorId.KICKBOX,
            reportLabel = "Kickbox",
            catalogName = "Kickbox Disposable Check",
            catalogDescription = "Disposable email domain detection.",
            metadataPrefixes = listOf("kickbox_"),
            urlHosts = setOf("open.kickbox.com"),
            categories = setOf("email")
        ),
        ExecutorDef(
            id = ExecutorId.GOOGLE_NEWS,
            reportLabel = "Google News",
            catalogName = "Google News",
            catalogDescription = "News mentions scraped from Google News RSS.",
            metadataPrefixes = listOf("googlenews_", "google_news_"),
            urlHosts = setOf("news.google.com"),
            nameKeywords = setOf("google news"),
            categories = setOf("person", "company", "social")
        ),
        ExecutorDef(
            id = ExecutorId.IP_WHO,
            reportLabel = "IP WHOIS",
            catalogName = "ipwho.is",
            catalogDescription = "IP geolocation and ASN metadata.",
            metadataPrefixes = listOf("ipwho"),
            urlHosts = setOf("ipwho.is"),
            categories = setOf("domain", "geo")
        ),
        ExecutorDef(
            id = ExecutorId.IP_API,
            reportLabel = "ip-api",
            catalogName = "ip-api.com",
            catalogDescription = "IP geolocation lookup.",
            metadataPrefixes = listOf("ip_api_", "ipapi_"),
            urlHosts = setOf("ip-api.com"),
            categories = setOf("domain", "geo")
        ),
        ExecutorDef(
            id = ExecutorId.IPINFO,
            reportLabel = "IPinfo",
            catalogName = "IPinfo",
            catalogDescription = "IP address metadata when API key is configured.",
            metadataPrefixes = listOf("ipinfo_"),
            urlHosts = setOf("ipinfo.io"),
            categories = setOf("domain", "geo")
        ),
        ExecutorDef(
            id = ExecutorId.BGP_VIEW,
            reportLabel = "BGPView",
            catalogName = "BGPView",
            catalogDescription = "BGP and ASN data for IP addresses.",
            metadataPrefixes = listOf("bgpview_"),
            urlHosts = setOf("bgpview.io"),
            categories = setOf("domain")
        ),
        ExecutorDef(
            id = ExecutorId.RDAP,
            reportLabel = "RDAP",
            catalogName = "RDAP",
            catalogDescription = "Registration Data Access Protocol domain lookups.",
            metadataPrefixes = listOf("rdap_"),
            urlHosts = setOf("rdap.org", "rdap.net"),
            nameKeywords = setOf("rdap"),
            categories = setOf("domain")
        ),
        ExecutorDef(
            id = ExecutorId.SHODAN,
            reportLabel = "Shodan InternetDB",
            catalogName = "Shodan InternetDB",
            catalogDescription = "Open ports and CVE hints from Shodan InternetDB.",
            metadataPrefixes = listOf("shodan_"),
            urlHosts = setOf("shodan.io", "internetdb.shodan.io"),
            categories = setOf("domain", "threat")
        ),
        ExecutorDef(
            id = ExecutorId.VIRUS_TOTAL,
            reportLabel = "VirusTotal",
            catalogName = "VirusTotal",
            catalogDescription = "Domain and URL threat intelligence when API key is set.",
            metadataPrefixes = listOf("vt_", "virustotal_"),
            urlHosts = setOf("virustotal.com"),
            categories = setOf("domain", "threat")
        ),
        ExecutorDef(
            id = ExecutorId.URL_SCAN,
            reportLabel = "URLScan",
            catalogName = "URLScan.io",
            catalogDescription = "Website scan metadata when API key is set.",
            metadataPrefixes = listOf("urlscan_"),
            urlHosts = setOf("urlscan.io"),
            categories = setOf("domain", "threat")
        ),
        ExecutorDef(
            id = ExecutorId.ABUSE_IPDB,
            reportLabel = "AbuseIPDB",
            catalogName = "AbuseIPDB",
            catalogDescription = "IP abuse reports when API key is configured.",
            metadataPrefixes = listOf("abuseipdb_"),
            urlHosts = setOf("abuseipdb.com"),
            categories = setOf("domain", "threat")
        ),
        ExecutorDef(
            id = ExecutorId.DARK_SEARCH,
            reportLabel = "DarkSearch",
            catalogName = "DarkSearch",
            catalogDescription = "Dark web index search results in dossier dark-web sections.",
            metadataPrefixes = listOf("darksearch_"),
            urlHosts = setOf("darksearch.io"),
            categories = setOf("darknet")
        ),
        ExecutorDef(
            id = ExecutorId.URLHAUS,
            reportLabel = "URLhaus",
            catalogName = "URLhaus",
            catalogDescription = "Malicious URL host intelligence from abuse.ch.",
            metadataPrefixes = listOf("urlhaus_"),
            urlHosts = setOf("urlhaus.abuse.ch"),
            categories = setOf("threat", "domain")
        ),
        ExecutorDef(
            id = ExecutorId.WIGLE,
            reportLabel = "WiGLE",
            catalogName = "WiGLE",
            catalogDescription = "WiFi network geolocation when API credentials are set.",
            metadataPrefixes = listOf("wigle_"),
            urlHosts = setOf("wigle.net", "api.wigle.net"),
            categories = setOf("geo")
        ),
        ExecutorDef(
            id = ExecutorId.NAME_DEMOGRAPHICS,
            reportLabel = "Name Demographics",
            catalogName = "Name Demographics",
            catalogDescription = "Gender and age demographic inference from first name.",
            metadataPrefixes = listOf("demographics_"),
            urlHosts = setOf("genderize.io", "agify.io"),
            nameKeywords = setOf("demographics", "genderize", "agify"),
            categories = setOf("person")
        ),
        ExecutorDef(
            id = ExecutorId.FBI_WANTED,
            reportLabel = "FBI Wanted",
            catalogName = "FBI Wanted",
            catalogDescription = "FBI wanted list API screening.",
            metadataPrefixes = listOf("fbi_wanted_"),
            urlHosts = setOf("api.fbi.gov"),
            categories = setOf("person", "records")
        ),
        ExecutorDef(
            id = ExecutorId.NPI_REGISTRY,
            reportLabel = "NPI Registry",
            catalogName = "NPI Registry",
            catalogDescription = "US healthcare provider NPI lookup.",
            metadataPrefixes = listOf("npi_"),
            urlHosts = setOf("npiregistry.cms.hhs.gov"),
            categories = setOf("person", "records")
        ),
        ExecutorDef(
            id = ExecutorId.OPEN_FEC,
            reportLabel = "OpenFEC",
            catalogName = "OpenFEC",
            catalogDescription = "US FEC campaign finance candidate search.",
            metadataPrefixes = listOf("openfec_"),
            urlHosts = setOf("api.open.fec.gov"),
            categories = setOf("finance", "person")
        )
    )

    private val hostIndex: Map<String, ExecutorDef> = buildMap {
        EXECUTORS.forEach { def ->
            def.urlHosts.forEach { host -> put(host, def) }
        }
    }

    fun resolveExecutor(name: String, url: String, frameworkPath: List<String>): ExecutorDef? {
        val host = urlHost(url)
        hostIndex[host]?.let { return it }
        hostIndex.entries.firstOrNull { host.endsWith(it.key) }?.value?.let { return it }

        val normalizedName = OsintUrlBuilder.normalizeToolName(name)
        val nameLower = name.lowercase()
        val topFolder = frameworkPath.firstOrNull().orEmpty()

        if (host == "github.com" || host == "api.github.com") {
            if (nameLower.contains("user") || url.contains("/users/")) {
                return EXECUTORS.first { it.id == ExecutorId.GITHUB }
            }
            return null
        }
        if (host == "reddit.com" && nameLower.contains("/r/")) return null
        if (host == "reddit.com" && nameLower.contains("user")) {
            return EXECUTORS.first { it.id == ExecutorId.REDDIT }
        }

        return EXECUTORS.firstOrNull { def ->
            val folderMatch = def.frameworkFolders.any { it.equals(topFolder, ignoreCase = true) }
            val keywordMatch = def.nameKeywords.any { kw ->
                nameLower.contains(kw) || normalizedName.contains(kw.replace(" ", ""))
            }
            when {
                folderMatch && keywordMatch -> true
                folderMatch && def.id == ExecutorId.USERNAME_SCAN -> isUsernameSearchTool(name)
                folderMatch && def.id == ExecutorId.EMAIL_REGISTRATION -> isEmailRegistrationTool(name)
                keywordMatch && def.urlHosts.isEmpty() -> true
                else -> false
            }
        }
    }

    fun executorById(id: ExecutorId): ExecutorDef = EXECUTORS.first { it.id == id }

    fun executorProducedReportData(executor: ExecutorDef, metadata: Map<String, String>): Boolean {
        if (executor.metadataExactKeys.any { metadata[it]?.isNotBlank() == true }) return true
        return metadata.keys.any { key ->
            executor.metadataPrefixes.any { prefix -> key.startsWith(prefix) && metadata[key]?.isNotBlank() == true }
        }
    }

    fun executorsWithReportData(metadata: Map<String, String>): List<ExecutorDef> =
        EXECUTORS.filter { executorProducedReportData(it, metadata) }

    private fun isUsernameSearchTool(name: String): Boolean {
        val lower = name.lowercase()
        if (lower.contains("github") && lower.contains("user")) return false
        return lower.contains("search") || lower.contains("check") || lower.contains("name") ||
            lower.contains("whatsmyname") || lower.contains("sylva") || lower.contains("namechk")
    }

    private fun isEmailRegistrationTool(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("holehe") || lower.contains("registration")
    }

    private fun urlHost(url: String): String =
        url.removePrefix("https://").removePrefix("http://")
            .substringBefore("/")
            .removePrefix("www.")
            .lowercase()
}
