package com.twoskoops707.sixdegrees.data

import android.content.Context
import java.util.Calendar

class ApiKeyManager(context: Context) {
    private val prefs = context.getSharedPreferences("api_keys", Context.MODE_PRIVATE)
    private val usagePrefs = context.getSharedPreferences("api_usage", Context.MODE_PRIVATE)

    var piplKey: String
        get() = prefs.getString("pipl", "") ?: ""
        set(v) { prefs.edit().putString("pipl", v).apply() }

    var clearbitKey: String
        get() = prefs.getString("clearbit", "") ?: ""
        set(v) { prefs.edit().putString("clearbit", v).apply() }

    var opensanctionsKey: String
        get() = prefs.getString("opensanctions", "") ?: ""
        set(v) { prefs.edit().putString("opensanctions", v).apply() }

    var opencorporatesKey: String
        get() = prefs.getString("opencorporates", "") ?: ""
        set(v) { prefs.edit().putString("opencorporates", v).apply() }

    var pdlKey: String
        get() = prefs.getString("pdl", "") ?: ""
        set(v) { prefs.edit().putString("pdl", v).apply() }

    var hunterKey: String
        get() = prefs.getString("hunter", "") ?: ""
        set(v) { prefs.edit().putString("hunter", v).apply() }

    var builtWithKey: String
        get() = prefs.getString("builtwith", "") ?: ""
        set(v) { prefs.edit().putString("builtwith", v).apply() }

    var hibpKey: String
        get() = prefs.getString("hibp", "") ?: ""
        set(v) { prefs.edit().putString("hibp", v).apply() }

    var numverifyKey: String
        get() = prefs.getString("numverify", "") ?: ""
        set(v) { prefs.edit().putString("numverify", v).apply() }

    var shodanKey: String
        get() = prefs.getString("shodan", "") ?: ""
        set(v) { prefs.edit().putString("shodan", v).apply() }

    var virusTotalKey: String
        get() = prefs.getString("virustotal", "") ?: ""
        set(v) { prefs.edit().putString("virustotal", v).apply() }

    var abuseIpDbKey: String
        get() = prefs.getString("abuseipdb", "") ?: ""
        set(v) { prefs.edit().putString("abuseipdb", v).apply() }

    var urlScanKey: String
        get() = prefs.getString("urlscan", "") ?: ""
        set(v) { prefs.edit().putString("urlscan", v).apply() }

    /** Free Auth-Key from https://auth.abuse.ch/ — required for URLhaus API since mid-2025. */
    var urlhausKey: String
        get() = prefs.getString("urlhaus", "") ?: ""
        set(v) { prefs.edit().putString("urlhaus", v).apply() }

    var googleCseApiKey: String
        get() = prefs.getString("google_cse_key", "") ?: ""
        set(v) { prefs.edit().putString("google_cse_key", v).apply() }

    var googleCseId: String
        get() = prefs.getString("google_cse_id", "") ?: ""
        set(v) { prefs.edit().putString("google_cse_id", v).apply() }

    var bingSearchKey: String
        get() = prefs.getString("bing_search", "") ?: ""
        set(v) { prefs.edit().putString("bing_search", v).apply() }

    var veriphoneKey: String
        get() = prefs.getString("veriphone", "") ?: ""
        set(v) { prefs.edit().putString("veriphone", v).apply() }

    var ipqsKey: String
        get() = prefs.getString("ipqs", "") ?: ""
        set(v) { prefs.edit().putString("ipqs", v).apply() }

    var fullcontactKey: String
        get() = prefs.getString("fullcontact", "") ?: ""
        set(v) { prefs.edit().putString("fullcontact", v).apply() }

    var hashesOrgKey: String
        get() = prefs.getString("hashes_org", "") ?: ""
        set(v) { prefs.edit().putString("hashes_org", v).apply() }

    var securityTrailsKey: String
        get() = prefs.getString("securitytrails", "") ?: ""
        set(v) { prefs.edit().putString("securitytrails", v).apply() }

    var censysId: String
        get() = prefs.getString("censys_id", "") ?: ""
        set(v) { prefs.edit().putString("censys_id", v).apply() }

    var censysSecret: String
        get() = prefs.getString("censys_secret", "") ?: ""
        set(v) { prefs.edit().putString("censys_secret", v).apply() }

    var criminalIpKey: String
        get() = prefs.getString("criminalip", "") ?: ""
        set(v) { prefs.edit().putString("criminalip", v).apply() }

    var netlasKey: String
        get() = prefs.getString("netlas", "") ?: ""
        set(v) { prefs.edit().putString("netlas", v).apply() }

    var abstractApiEmailKey: String
        get() = prefs.getString("abstractapi_email", "") ?: ""
        set(v) { prefs.edit().putString("abstractapi_email", v).apply() }

    var abstractApiPhoneKey: String
        get() = prefs.getString("abstractapi_phone", "") ?: ""
        set(v) { prefs.edit().putString("abstractapi_phone", v).apply() }

    var dehashed: String
        get() = prefs.getString("dehashed", "") ?: ""
        set(v) { prefs.edit().putString("dehashed", v).apply() }

    var dehashedUser: String
        get() = prefs.getString("dehashed_user", "") ?: ""
        set(v) { prefs.edit().putString("dehashed_user", v).apply() }

    var leakixKey: String
        get() = prefs.getString("leakix", "") ?: ""
        set(v) { prefs.edit().putString("leakix", v).apply() }

    var intelxKey: String
        get() = prefs.getString("intelx", "") ?: ""
        set(v) { prefs.edit().putString("intelx", v).apply() }

    var wigleKey: String
        get() = prefs.getString("wigle", "") ?: ""
        set(v) { prefs.edit().putString("wigle", v).apply() }

    var pulsediveKey: String
        get() = prefs.getString("pulsedive", "") ?: ""
        set(v) { prefs.edit().putString("pulsedive", v).apply() }

    var fullHuntKey: String
        get() = prefs.getString("fullhunt", "") ?: ""
        set(v) { prefs.edit().putString("fullhunt", v).apply() }

    var tombaKey: String
        get() = prefs.getString("tomba", "") ?: ""
        set(v) { prefs.edit().putString("tomba", v).apply() }

    var googleSafeBrowsingKey: String
        get() = prefs.getString("google_safebrowsing", "") ?: ""
        set(v) { prefs.edit().putString("google_safebrowsing", v).apply() }

    /** OpenRouter — structured OSINT dossier synthesis (free tier models available). https://openrouter.ai/keys */
    var openrouterKey: String
        get() = prefs.getString("openrouter", "") ?: ""
        set(v) { prefs.edit().putString("openrouter", v).apply() }

    /** Optional override for OpenRouter model id (default: meta-llama/llama-3.3-70b-instruct:free). */
    var openrouterModel: String
        get() = prefs.getString("openrouter_model", "") ?: ""
        set(v) { prefs.edit().putString("openrouter_model", v).apply() }

    fun getKey(service: String): String? =
        prefs.getString(service, null)?.takeIf { it.isNotBlank() }

    fun getRawForDisplay(prefKey: String): String = prefs.getString(prefKey, "") ?: ""

    fun hasAnyKey(): Boolean = activeKeyCount() > 0

    fun activeKeyCount(): Int = listOf(
        hibpKey, hunterKey, pdlKey, numverifyKey, shodanKey,
        virusTotalKey, abuseIpDbKey, urlScanKey, clearbitKey, builtWithKey,
        securityTrailsKey, censysId, criminalIpKey, netlasKey,
        abstractApiEmailKey, abstractApiPhoneKey, leakixKey, intelxKey, wigleKey,
        pulsediveKey, fullHuntKey, tombaKey
    ).count { it.isNotBlank() }

    private fun monthKey(apiName: String): String {
        val cal = Calendar.getInstance()
        return "${apiName}_${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH)}"
    }

    fun recordUsage(apiName: String) {
        val key = monthKey(apiName)
        val current = usagePrefs.getInt(key, 0)
        usagePrefs.edit().putInt(key, current + 1).apply()
    }

    fun getMonthlyUsage(apiName: String): Int =
        usagePrefs.getInt(monthKey(apiName), 0)

    fun getRemainingForMonth(apiName: String, monthlyLimit: Int): Int =
        maxOf(0, monthlyLimit - getMonthlyUsage(apiName))

    data class ApiUsageSummary(
        val name: String,
        val used: Int,
        val limit: Int,
        val isUnlimited: Boolean = false
    ) {
        val remaining: Int get() = maxOf(0, limit - used)
        val label: String get() = if (isUnlimited) "Unlimited" else "$remaining / $limit left this month"
        val fractionUsed: Float get() = if (isUnlimited || limit == 0) 0f else used.toFloat() / limit
    }

    fun getUsageSummaries(): List<ApiUsageSummary> = listOf(
        ApiUsageSummary("HIBP", getMonthlyUsage("hibp"), Int.MAX_VALUE, isUnlimited = hibpKey.isBlank()),
        ApiUsageSummary("Hunter.io", getMonthlyUsage("hunter"), 25),
        ApiUsageSummary("People Data Labs", getMonthlyUsage("pdl"), 100),
        ApiUsageSummary("Numverify", getMonthlyUsage("numverify"), 100),
        ApiUsageSummary("EmailRep.io", getMonthlyUsage("emailrep"), 250, isUnlimited = false),
        ApiUsageSummary("HackerTarget", getMonthlyUsage("hackertarget"), 50, isUnlimited = false),
        ApiUsageSummary("AlienVault OTX", getMonthlyUsage("otx"), 1000, isUnlimited = false),
        ApiUsageSummary("SecurityTrails", getMonthlyUsage("securitytrails"), 50, isUnlimited = securityTrailsKey.isBlank()),
        ApiUsageSummary("Censys", getMonthlyUsage("censys"), 250, isUnlimited = censysId.isBlank()),
        ApiUsageSummary("CriminalIP", getMonthlyUsage("criminalip"), 100, isUnlimited = criminalIpKey.isBlank()),
        ApiUsageSummary("AbstractAPI Email", getMonthlyUsage("abstractapi_email"), 100, isUnlimited = abstractApiEmailKey.isBlank()),
        ApiUsageSummary("AbstractAPI Phone", getMonthlyUsage("abstractapi_phone"), 500, isUnlimited = abstractApiPhoneKey.isBlank()),
        ApiUsageSummary("LeakIX", getMonthlyUsage("leakix"), 1000, isUnlimited = leakixKey.isBlank()),
        ApiUsageSummary("IntelX", getMonthlyUsage("intelx"), 500, isUnlimited = intelxKey.isBlank()),
        ApiUsageSummary("IP-API.com", getMonthlyUsage("ipapi"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("Wayback CDX", getMonthlyUsage("wayback"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("crt.sh", getMonthlyUsage("crtsh"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("BGPView", getMonthlyUsage("bgpview"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("IPinfo", getMonthlyUsage("ipinfo"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("ThreatFox", getMonthlyUsage("threatfox"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("MalwareBazaar", getMonthlyUsage("malwarebazaar"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("CourtListener", getMonthlyUsage("courtlistener"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("OpenCorporates", getMonthlyUsage("opencorporates"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("FBI Wanted", getMonthlyUsage("fbi_wanted"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("Interpol", getMonthlyUsage("interpol"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("NVD CVE", getMonthlyUsage("nvd_cve"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("PhishStats", getMonthlyUsage("phishstats"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("Dehash.lt", getMonthlyUsage("dehash"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("GeoJS", getMonthlyUsage("geojs"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("NHTSA", getMonthlyUsage("nhtsa"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("WiGLE", getMonthlyUsage("wigle"), 100, isUnlimited = wigleKey.isBlank()),
        ApiUsageSummary("Pulsedive", getMonthlyUsage("pulsedive"), 30, isUnlimited = pulsediveKey.isBlank()),
        ApiUsageSummary("FullHunt", getMonthlyUsage("fullhunt"), 100, isUnlimited = fullHuntKey.isBlank()),
        ApiUsageSummary("Tomba", getMonthlyUsage("tomba"), 25, isUnlimited = tombaKey.isBlank()),
        ApiUsageSummary("MarkerAPI", getMonthlyUsage("markerapi"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("Google SafeBrowsing", getMonthlyUsage("safebrowsing"), Int.MAX_VALUE, isUnlimited = false),
        ApiUsageSummary("OpenRouter AI", getMonthlyUsage("openrouter"), 50, isUnlimited = openrouterKey.isBlank())
    )
}
