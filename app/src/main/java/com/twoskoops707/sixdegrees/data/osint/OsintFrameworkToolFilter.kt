package com.twoskoops707.sixdegrees.data.osint

import org.json.JSONObject

/**
 * Keeps only lockfale/OSINT-Framework entries that are free, live, and launchable in a browser.
 */
object OsintFrameworkToolFilter {

    fun isFreeAndWorkable(node: JSONObject): Boolean {
        val name = node.optString("name", "")
        val url = node.optString("url", "").trim()
        if (url.isBlank() || !url.startsWith("http")) return false
        if (url.contains("<br/>")) return false
        if (isDeprecated(node)) return false
        if (isInvitationOnly(node)) return false
        if (!isLive(node)) return false
        if (!isFreePricing(node, name)) return false
        if (isLocalOnly(node, name, url)) return false
        if (isManualDorkTemplate(name, url)) return false
        return true
    }

    private fun isDeprecated(node: JSONObject): Boolean =
        node.optBoolean("deprecated", false) || node.optString("deprecated") == "True"

    private fun isInvitationOnly(node: JSONObject): Boolean =
        node.optBoolean("invitationOnly", false) || node.optString("invitationOnly") == "True"

    private fun isLive(node: JSONObject): Boolean {
        val status = node.optString("status", "live").lowercase()
        return status.isBlank() || status == "live"
    }

    private fun isFreePricing(node: JSONObject, name: String): Boolean {
        if (name.contains("(R$)")) return false
        return when (node.optString("pricing", "free").lowercase()) {
            "free", "", "free/freemium" -> true
            else -> false
        }
    }

    private fun isLocalOnly(node: JSONObject, name: String, url: String): Boolean {
        if (node.optBoolean("localInstall", false) || node.optString("localInstall") == "True") return true
        if (name.endsWith("(T)") && url.contains("github.com")) return true
        return false
    }

    /** Framework (M) entries that are Google-dork recipes, not runnable services. */
    private fun isManualDorkTemplate(name: String, url: String): Boolean {
        if (!name.contains("(M)")) return false
        return url.contains("google.com/search") || url.contains("%3Cusername%3E") || url.contains("<username>")
    }
}
