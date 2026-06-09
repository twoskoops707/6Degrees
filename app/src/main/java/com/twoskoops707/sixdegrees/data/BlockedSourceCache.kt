package com.twoskoops707.sixdegrees.data

import android.content.Context
import android.net.Uri
import java.util.concurrent.TimeUnit

/**
 * Remembers domains that return 403/429, Cloudflare challenges, or timeouts.
 * Skipped for [TTL_MS] (24h) on future searches so users don't wait on dead sources.
 */
class BlockedSourceCache(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isBlocked(domain: String): Boolean {
        if (domain.isBlank()) return false
        val expiry = prefs.getLong(keyFor(domain), 0L)
        if (expiry == 0L) return false
        if (expiry == PERMANENT) return true
        if (System.currentTimeMillis() > expiry) {
            prefs.edit().remove(keyFor(domain)).remove(reasonKeyFor(domain)).apply()
            return false
        }
        return true
    }

    fun isUrlBlocked(url: String): Boolean = isBlocked(domainFromUrl(url))

    fun markBlocked(domain: String, reason: String = "blocked", permanent: Boolean = false) {
        if (domain.isBlank()) return
        val expiry = if (permanent) PERMANENT else System.currentTimeMillis() + TTL_MS
        prefs.edit()
            .putLong(keyFor(domain), expiry)
            .putString(reasonKeyFor(domain), reason)
            .apply()
    }

    fun markUrlBlocked(url: String, reason: String = "blocked") =
        markBlocked(domainFromUrl(url), reason)

    fun reason(domain: String): String? = prefs.getString(reasonKeyFor(domain), null)

    fun clearAll() = prefs.edit().clear().apply()

    fun clearDomain(domain: String) {
        prefs.edit().remove(keyFor(domain)).remove(reasonKeyFor(domain)).apply()
    }

    private fun keyFor(domain: String) = "blocked_$domain"
    private fun reasonKeyFor(domain: String) = "reason_$domain"

    companion object {
        const val PREFS_NAME = "blocked_sources"
        private val TTL_MS = TimeUnit.HOURS.toMillis(24)
        private const val PERMANENT = Long.MAX_VALUE

        fun domainFromUrl(url: String): String = try {
            Uri.parse(url).host?.lowercase()?.removePrefix("www.").orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}
