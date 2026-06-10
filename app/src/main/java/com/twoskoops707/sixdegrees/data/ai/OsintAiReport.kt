package com.twoskoops707.sixdegrees.data.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * Structured OSINT dossier produced by cloud AI (OpenRouter) or fallback providers.
 */
data class OsintAiReport(
    val executiveSummary: String,
    val keyFindings: List<String>,
    val confidence: String,
    val confidenceRationale: String,
    val falsePositiveNotes: List<String>,
    val nextSteps: List<String>,
    val provider: String
) {
    fun toMetadataMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        map["ai_provider"] = provider
        map["ai_executive_summary"] = executiveSummary
        map["ai_key_findings"] = keyFindings.joinToString("\n")
        map["ai_confidence"] = confidence
        if (confidenceRationale.isNotBlank()) map["ai_confidence_rationale"] = confidenceRationale
        if (falsePositiveNotes.isNotEmpty()) map["ai_false_positives"] = falsePositiveNotes.joinToString("\n")
        if (nextSteps.isNotEmpty()) map["ai_next_steps"] = nextSteps.joinToString("\n")
        map["ai_summary"] = toLegacySummary()
        return map
    }

    fun toLegacySummary(): String = buildString {
        appendLine("EXECUTIVE SUMMARY")
        appendLine(executiveSummary.trim())
        if (keyFindings.isNotEmpty()) {
            appendLine()
            appendLine("KEY FINDINGS")
            keyFindings.forEach { appendLine("• $it") }
        }
        appendLine()
        appendLine("CONFIDENCE: ${confidence.uppercase()}")
        if (confidenceRationale.isNotBlank()) appendLine(confidenceRationale.trim())
        if (falsePositiveNotes.isNotEmpty()) {
            appendLine()
            appendLine("FALSE POSITIVE NOTES")
            falsePositiveNotes.forEach { appendLine("• $it") }
        }
        if (nextSteps.isNotEmpty()) {
            appendLine()
            appendLine("RECOMMENDED NEXT STEPS")
            nextSteps.forEach { appendLine("• $it") }
        }
    }.trim()

    companion object {
        private fun jsonStringArray(key: String, obj: JSONObject): List<String> =
            obj.optJSONArray(key)?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optString(i).trim().takeIf { it.isNotBlank() } }
            } ?: obj.optString(key).lines()
                .map { it.trim().removePrefix("•").removePrefix("-").trim() }
                .filter { it.isNotBlank() }

        fun fromJson(content: String, provider: String): OsintAiReport? {
            return try {
            val trimmed = content.trim()
            val jsonStart = trimmed.indexOf('{')
            val jsonEnd = trimmed.lastIndexOf('}')
            if (jsonStart < 0 || jsonEnd <= jsonStart) return null
            val obj = JSONObject(trimmed.substring(jsonStart, jsonEnd + 1))
            val summary = obj.optString("executive_summary")
                .ifBlank { obj.optString("executiveSummary") }
                .ifBlank { obj.optString("summary") }
                .trim()
            if (summary.isBlank()) return null
            OsintAiReport(
                executiveSummary = summary,
                keyFindings = jsonStringArray("key_findings", obj)
                    .ifEmpty { jsonStringArray("keyFindings", obj) },
                confidence = obj.optString("confidence", "medium").trim().ifBlank { "medium" },
                confidenceRationale = obj.optString("confidence_rationale")
                    .ifBlank { obj.optString("confidenceRationale") }
                    .trim(),
                falsePositiveNotes = jsonStringArray("false_positive_notes", obj)
                    .ifEmpty { jsonStringArray("falsePositiveNotes", obj) },
                nextSteps = jsonStringArray("recommended_next_steps", obj)
                    .ifEmpty { jsonStringArray("next_steps", obj) }
                    .ifEmpty { jsonStringArray("nextSteps", obj) },
                provider = provider
            )
            } catch (_: Exception) {
                null
            }
        }

        fun fromPlainText(content: String, provider: String): OsintAiReport =
            OsintAiReport(
                executiveSummary = content.trim(),
                keyFindings = emptyList(),
                confidence = "unknown",
                confidenceRationale = "",
                falsePositiveNotes = emptyList(),
                nextSteps = emptyList(),
                provider = provider
            )

        /**
         * Strip unsourced claims when ai_facts_only=true — phones, emails, addresses
         * must appear in allowed metadata values.
         */
        fun enforceFactsOnly(report: OsintAiReport, allowedValues: Set<String>): OsintAiReport {
            if (allowedValues.isEmpty()) return report
            val normalizedAllowed = allowedValues.map { it.lowercase() }.toSet()
            val phoneDigitsAllowed = allowedValues.map { it.replace(Regex("[^0-9]"), "") }
                .filter { it.length >= 7 }.toSet()

            fun isAllowedToken(token: String): Boolean {
                val t = token.trim()
                if (t.length < 3) return true
                val lower = t.lowercase()
                if (lower in normalizedAllowed) return true
                if (normalizedAllowed.any { lower.contains(it) || it.contains(lower) }) return true
                val digits = t.replace(Regex("[^0-9]"), "")
                if (digits.length >= 7 && phoneDigitsAllowed.any { digits.endsWith(it.takeLast(10)) || it.endsWith(digits.takeLast(10)) }) {
                    return true
                }
                return false
            }

            fun scrubText(text: String): String {
                val phoneRegex = Regex("""\(?\d{3}\)?[.\-\s]?\d{3}[.\-\s]?\d{4}""")
                val emailRegex = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
                var scrubbed = text
                phoneRegex.findAll(text).forEach { m ->
                    if (!isAllowedToken(m.value)) scrubbed = scrubbed.replace(m.value, "[redacted phone]")
                }
                emailRegex.findAll(text).forEach { m ->
                    if (!isAllowedToken(m.value)) scrubbed = scrubbed.replace(m.value, "[redacted email]")
                }
                return scrubbed.replace(Regex("""\s{2,}"""), " ").trim()
            }

            return report.copy(
                executiveSummary = scrubText(report.executiveSummary),
                keyFindings = report.keyFindings.map { scrubText(it) }.filter { it.isNotBlank() },
                falsePositiveNotes = report.falsePositiveNotes.map { scrubText(it) },
                nextSteps = report.nextSteps.map { scrubText(it) }
            )
        }

        fun collectAllowedFactValues(metadata: Map<String, String>): Set<String> {
            val keys = listOf(
                "search_phones", "pipl_phones", "pdl_phones", "person_phone", "tps_phones",
                "search_emails", "pipl_emails", "pdl_emails", "person_email",
                "search_addresses", "pipl_addresses", "pdl_address", "person_entered_address",
                "person_name", "pipl_name", "pdl_name", "search_relatives", "pipl_relatives",
                "search_age", "person_location", "pdl_company", "clearbit_person_company",
                "tt_names", "tt_name", "fps_names", "fps_name", "tps_names", "uspb_names", "uspb_name",
                "phone_owner_names", "search_names", "sec_person_entities", "sec_fulltext_entities",
                "sec_affiliations", "wikidata_employers", "corpwiki_person_companies"
            )
            val values = mutableSetOf<String>()
            keys.forEach { k -> metadata[k]?.let { raw ->
                raw.split(",", "\n", "|").map { it.trim() }.filter { it.length > 2 }.forEach { values.add(it) }
            }}
            return values
        }
    }
}
