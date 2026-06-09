package com.twoskoops707.sixdegrees.data

import android.content.Context

/**
 * Named search presets — user picks which OSINT source categories run per investigation.
 */
object SearchPresetManager {

    const val KEY_ACTIVE_PRESET = "search_preset_active"
    const val KEY_CUSTOM_NAME = "search_preset_custom_name"
    const val KEY_CUSTOM_CATEGORIES = "search_preset_custom_categories"

    const val PRESET_FULL = "full"
    const val PRESET_PEOPLE = "people_only"
    const val PRESET_PHONE = "phone_only"
    const val PRESET_EMAIL = "email_only"
    const val PRESET_DARKWEB = "darkweb_only"
    const val PRESET_COURTS_SEC = "courts_sec"
    const val PRESET_CUSTOM = "custom"

    val ALL_CATEGORIES = listOf(
        "person", "phone", "email", "social", "breach", "darknet",
        "records", "company", "domain", "threat", "finance", "geo", "image"
    )

    val BUILTIN_PRESETS: Map<String, Pair<String, Set<String>>> = mapOf(
        PRESET_FULL to ("Full scan" to ALL_CATEGORIES.toSet()),
        PRESET_PEOPLE to ("People only" to setOf("person", "social", "image")),
        PRESET_PHONE to ("Phone only" to setOf("phone")),
        PRESET_EMAIL to ("Email & breach" to setOf("email", "breach")),
        PRESET_DARKWEB to ("Dark web only" to setOf("darknet")),
        PRESET_COURTS_SEC to ("Courts + SEC" to setOf("records", "finance", "company"))
    )

    val CATEGORY_LABELS = mapOf(
        "person" to "People",
        "phone" to "Phone",
        "email" to "Email",
        "social" to "Social",
        "breach" to "Breaches",
        "darknet" to "Dark web",
        "records" to "Public records",
        "company" to "Company",
        "domain" to "Domain / IP",
        "threat" to "Threat intel",
        "finance" to "Financial / SEC",
        "geo" to "Geolocation",
        "image" to "Image / face"
    )

    fun getActivePresetId(context: Context): String =
        context.getSharedPreferences(AppSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_PRESET, PRESET_FULL) ?: PRESET_FULL

    fun getActiveCategories(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(AppSettings.PREFS_NAME, Context.MODE_PRIVATE)
        val presetId = prefs.getString(KEY_ACTIVE_PRESET, PRESET_FULL) ?: PRESET_FULL
        if (presetId == PRESET_CUSTOM) {
            val raw = prefs.getString(KEY_CUSTOM_CATEGORIES, null)
            if (!raw.isNullOrBlank()) {
                return raw.split(",").map { it.trim() }.filter { it in ALL_CATEGORIES }.toSet()
                    .ifEmpty { ALL_CATEGORIES.toSet() }
            }
        }
        return BUILTIN_PRESETS[presetId]?.second ?: ALL_CATEGORIES.toSet()
    }

    fun getPresetDisplayName(context: Context): String {
        val prefs = context.getSharedPreferences(AppSettings.PREFS_NAME, Context.MODE_PRIVATE)
        val presetId = prefs.getString(KEY_ACTIVE_PRESET, PRESET_FULL) ?: PRESET_FULL
        if (presetId == PRESET_CUSTOM) {
            return prefs.getString(KEY_CUSTOM_NAME, "Custom") ?: "Custom"
        }
        return BUILTIN_PRESETS[presetId]?.first ?: "Full scan"
    }

    fun saveBuiltinPreset(context: Context, presetId: String) {
        context.getSharedPreferences(AppSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_PRESET, presetId)
            .apply()
    }

    fun saveCustomPreset(context: Context, name: String, categories: Set<String>) {
        context.getSharedPreferences(AppSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_PRESET, PRESET_CUSTOM)
            .putString(KEY_CUSTOM_NAME, name.trim().ifBlank { "Custom" })
            .putString(KEY_CUSTOM_CATEGORIES, categories.joinToString(","))
            .apply()
    }

    fun getCustomCategories(context: Context): Set<String> {
        val raw = context.getSharedPreferences(AppSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_CATEGORIES, null) ?: return ALL_CATEGORIES.toSet()
        return raw.split(",").map { it.trim() }.filter { it in ALL_CATEGORIES }.toSet()
            .ifEmpty { ALL_CATEGORIES.toSet() }
    }
}
