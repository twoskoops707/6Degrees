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

    var googleCseApiKey: String
        get() = prefs.getString("google_cse_key", "") ?: ""
        set(v) { prefs.edit().putString("google_cse_key", v).apply() }

    var googleCseId: String
        get() = prefs.getString("google_cse_id", "") ?: ""
        set(v) { prefs.edit().putString("google_cse_id", v).apply() }

    var bingSearchKey: String
        get() = prefs.getString("bing_search", "") ?: ""
        set(v) { prefs.edit().putString("bing_search", v).apply() }

    fun hasAnyKey(): Boolean = true

    fun activeKeyCount(): Int = listOf(
        hibpKey, hunterKey, pdlKey, numverifyKey, shodanKey,
        virusTotalKey, abuseIpDbKey, urlScanKey, clearbitKey, builtWithKey
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
        ApiUsageSummary("IP-API.com", getMonthlyUsage("ipapi"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("Wayback CDX", getMonthlyUsage("wayback"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("crt.sh", getMonthlyUsage("crtsh"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("CourtListener", getMonthlyUsage("courtlistener"), Int.MAX_VALUE, isUnlimited = true),
        ApiUsageSummary("OpenCorporates", getMonthlyUsage("opencorporates"), Int.MAX_VALUE, isUnlimited = true)
    )
}
