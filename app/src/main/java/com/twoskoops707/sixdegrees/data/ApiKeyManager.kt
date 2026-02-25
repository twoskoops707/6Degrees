package com.twoskoops707.sixdegrees.data

import android.content.Context

class ApiKeyManager(context: Context) {
    private val prefs = context.getSharedPreferences("api_keys", Context.MODE_PRIVATE)

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

    fun hasAnyKey(): Boolean = true

    fun activeKeyCount(): Int = listOf(
        hibpKey, hunterKey, pdlKey, numverifyKey, shodanKey,
        virusTotalKey, abuseIpDbKey, urlScanKey, clearbitKey, builtWithKey
    ).count { it.isNotBlank() }
}
