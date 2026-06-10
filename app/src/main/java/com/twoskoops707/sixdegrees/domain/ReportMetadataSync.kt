package com.twoskoops707.sixdegrees.domain

/**
 * Resyncs scraper metadata keys with dossier/report consumers.
 * Scrapers write `{source}_name` while DossierBuilder expects `{source}_names`.
 */
object ReportMetadataSync {

    private val PERSON_SOURCE_PREFIXES = listOf(
        "tt", "fps", "tps", "uspb", "zaba", "411", "spk", "radaris", "peekyou", "nuwber", "wp", "ftn"
    )

    private val NAME_LINE_REGEX = Regex(
        """(?i)(?:owner|name|associated with|belongs to|registered to|caller)[:\s]+([A-Z][a-z'.-]+(?:\s+[A-Z][a-z'.-]+)+)"""
    )

    private val TITLE_NAME_REGEX = Regex(
        """^([A-Z][a-z'.-]+(?:\s+[A-Z][a-z'.-]+)+)\s*(?:[-–—|]|in\s+[A-Z]|,|\()"""
    )

    private val GENERIC_NAME_REGEX = Regex(
        """\b([A-Z][a-z'.-]{1,24}\s+[A-Z][a-z'.-]{1,24}(?:\s+[A-Z][a-z'.-]{1,24})?)\b"""
    )

    private val NON_NAME_WORDS = setOf(
        "phone", "number", "caller", "unknown", "wireless", "mobile", "landline", "voip",
        "united", "states", "north", "south", "east", "west", "county", "city", "state",
        "true", "false", "null", "spam", "scam", "report", "reports", "reverse", "lookup",
        "fast", "people", "search", "white", "pages", "that", "them", "notes"
    )

    fun sync(metadata: MutableMap<String, String>) {
        promoteSingularNames(metadata)
        extractNamesFromSnippets(metadata)
        aggregatePhoneOwnerNames(metadata)
        syncSecFilings(metadata)
        sync800NotesSnippet(metadata)
    }

    fun extractPersonNames(text: String, max: Int = 5): List<String> {
        if (text.isBlank()) return emptyList()
        val found = linkedSetOf<String>()
        NAME_LINE_REGEX.findAll(text).forEach { m ->
            sanitizeName(m.groupValues[1])?.let { found.add(it) }
        }
        TITLE_NAME_REGEX.find(text.trim())?.let { m ->
            sanitizeName(m.groupValues[1])?.let { found.add(it) }
        }
        GENERIC_NAME_REGEX.findAll(text).forEach { m ->
            sanitizeName(m.groupValues[1])?.let { found.add(it) }
        }
        return found.take(max).toList()
    }

    private fun promoteSingularNames(metadata: MutableMap<String, String>) {
        PERSON_SOURCE_PREFIXES.forEach { prefix ->
            val singular = metadata["${prefix}_name"]?.takeIf { it.isNotBlank() }
            if (singular != null) mergeCsv(metadata, "${prefix}_names", listOf(singular))
        }
    }

    private fun extractNamesFromSnippets(metadata: MutableMap<String, String>) {
        val snippetKeys = metadata.keys.filter { key ->
            key.endsWith("_snippet") || key == "phone_search_snippets" || key.endsWith("_title")
        }
        val extracted = linkedSetOf<String>()
        snippetKeys.forEach { key ->
            metadata[key]?.lines()?.forEach { line ->
                extractPersonNames(line).forEach { extracted.add(it) }
            }
        }
        metadata["dork_phone_results"]?.split("\n---\n")?.forEach { block ->
            extractPersonNames(block).forEach { extracted.add(it) }
        }
        if (extracted.isNotEmpty()) mergeCsv(metadata, "phone_owner_names", extracted.toList())
    }

    private fun aggregatePhoneOwnerNames(metadata: MutableMap<String, String>) {
        val owners = linkedSetOf<String>()
        PERSON_SOURCE_PREFIXES.forEach { prefix ->
            metadata["${prefix}_names"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                ?.forEach { owners.add(it) }
            metadata["${prefix}_name"]?.takeIf { it.isNotBlank() }?.let { owners.add(it.trim()) }
        }
        metadata["phone_owner_names"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
            ?.forEach { owners.add(it) }
        metadata["dork_relatives"]?.split(",")?.map { it.trim() }?.filter { it.contains(" ") }
            ?.forEach { owners.add(it) }
        if (owners.isNotEmpty()) {
            metadata["phone_owner_names"] = owners.joinToString(", ")
            if (metadata["search_names"].isNullOrBlank()) metadata["search_names"] = owners.joinToString(", ")
        }
    }

    private fun syncSecFilings(metadata: MutableMap<String, String>) {
        metadata["sec_person_entities"]?.takeIf { it.isNotBlank() }?.let { entities ->
            mergeCsv(metadata, "sec_affiliations", entities.split(",").map { it.trim() })
        }
        metadata["sec_fulltext_entities"]?.takeIf { it.isNotBlank() }?.let { entities ->
            mergeCsv(metadata, "sec_affiliations", entities.split(",").map { it.trim() })
            if (metadata["sec_person_entities"].isNullOrBlank()) metadata["sec_person_entities"] = entities
        }
        metadata["sec_fulltext_forms"]?.takeIf { it.isNotBlank() }?.let { forms ->
            if (metadata["sec_filing_types"].isNullOrBlank()) metadata["sec_filing_types"] = forms
        }
        val secHits = metadata["sec_person_hits"]?.toIntOrNull()
            ?: metadata["sec_fulltext_hits"]?.toIntOrNull()
        if (secHits != null && secHits > 0 && metadata["sec_filings_count"].isNullOrBlank()) {
            metadata["sec_filings_count"] = secHits.toString()
        }
    }

    private fun sync800NotesSnippet(metadata: MutableMap<String, String>) {
        if (!metadata["800notes_snippet"].isNullOrBlank()) return
        metadata["800notes_title"]?.takeIf { it.isNotBlank() }?.let { metadata["800notes_snippet"] = it }
    }

    private fun sanitizeName(raw: String): String? {
        val cleaned = raw.trim()
            .removeSuffix(".")
            .removeSuffix(",")
            .replace(Regex("\\s+"), " ")
        if (cleaned.length < 4 || cleaned.length > 48) return null
        if (!cleaned.contains(" ")) return null
        val words = cleaned.lowercase().split(" ")
        if (words.any { it in NON_NAME_WORDS }) return null
        if (words.any { it.length < 2 }) return null
        return cleaned
    }

    private fun mergeCsv(metadata: MutableMap<String, String>, key: String, values: List<String>) {
        val incoming = values.map { it.trim() }.filter { it.isNotBlank() }
        if (incoming.isEmpty()) return
        val existing = metadata[key]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        metadata[key] = (existing + incoming).distinct().joinToString(", ")
    }
}
