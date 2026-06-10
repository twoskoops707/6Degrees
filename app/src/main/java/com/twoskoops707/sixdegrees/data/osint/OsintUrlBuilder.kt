package com.twoskoops707.sixdegrees.data.osint

import java.net.URLEncoder

/**
 * Builds launch URLs for OSINT Framework entries and SixDegrees template overrides.
 */
object OsintUrlBuilder {

    fun buildUrl(template: String, query: String): String {
        if (template.isBlank()) return template
        val trimmed = query.trim()
        val first = urlEncode(trimmed.substringBefore(" ").trim())
        val last = urlEncode(trimmed.substringAfterLast(" ").trim())
        val email = if (trimmed.contains("@")) trimmed else trimmed
        val username = trimmed.substringBefore("@").ifBlank { trimmed }

        return template
            .replace("<email_address>", urlEncode(email))
            .replace("<username>", urlEncode(username))
            .replace("{q-encoded}", urlEncode(trimmed))
            .replace("{q-hyphen}", trimmed.replace(" ", "-"))
            .replace("{q-plus}", trimmed.replace(" ", "+"))
            .replace("{q-underscore}", trimmed.replace(" ", "_"))
            .replace("{q-digits}", trimmed.replace(Regex("[^0-9+]"), ""))
            .replace("{q-raw}", trimmed)
            .replace("{first}", first)
            .replace("{last}", last)
    }

    fun isQueryableTemplate(template: String): Boolean =
        template.contains("{q-") ||
            template.contains("{first}") ||
            template.contains("{last}") ||
            template.contains("<email_address>") ||
            template.contains("<username>")

    fun normalizeToolName(name: String): String =
        name.lowercase()
            .replace(Regex("\\s*\\([^)]*\\)\\s*"), "")
            .replace(Regex("[^a-z0-9]+"), "")
            .trim()

    private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
