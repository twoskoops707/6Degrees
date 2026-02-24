package com.example.a6degrees.data

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

    fun hasAnyKey(): Boolean =
        listOf(piplKey, clearbitKey, pdlKey, hunterKey, builtWithKey, hibpKey).any { it.isNotBlank() }
}
