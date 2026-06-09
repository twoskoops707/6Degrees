package com.twoskoops707.sixdegrees.domain

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Writes structured dork hit metadata: dork_{category}_{n}_title|url|snippet|query
 * plus extracted PII keys (dork_{category}_phones, dork_phones) and needle rankings.
 */
object DorkMetadataStore {

    data class DorkHit(
        val title: String,
        val url: String,
        val snippet: String,
        val query: String
    )

    data class ExtractedPii(
        val phones: List<String> = emptyList(),
        val emails: List<String> = emptyList(),
        val addresses: List<String> = emptyList(),
        val relatives: List<String> = emptyList(),
        val ages: List<String> = emptyList(),
        val employers: List<String> = emptyList(),
        val socialUrls: List<String> = emptyList(),
        val profileUrls: List<String> = emptyList()
    )

    fun storeHits(
        metadata: ConcurrentHashMap<String, String>,
        category: GoogleDorkLibrary.DorkCategory,
        query: String,
        hits: List<DorkHit>
    ) {
        if (hits.isEmpty()) return
        val cat = category.key
        hits.forEachIndexed { index, hit ->
            val n = index + 1
            metadata["dork_${cat}_${n}_title"] = hit.title
            metadata["dork_${cat}_${n}_url"] = hit.url
            metadata["dork_${cat}_${n}_snippet"] = hit.snippet
            metadata["dork_${cat}_${n}_query"] = query
        }
        metadata["dork_${cat}_count"] = hits.size.toString()

        val legacyKey = GoogleDorkLibrary.legacyMetaKey(category)
        val existing = metadata[legacyKey].orEmpty()
        val block = hits.joinToString("\n---\n") { "${it.title}: ${it.snippet}".trim().take(220) }
        metadata[legacyKey] = if (existing.isBlank()) block else "$existing\n---\n$block"

        val categories = metadata["dork_categories"]?.split(",")?.filter { it.isNotBlank() }?.toMutableSet()
            ?: mutableSetOf()
        categories.add("$cat:${hits.size}")
        metadata["dork_categories"] = categories.sorted().joinToString(",")
    }

    fun storeExtractedPii(
        metadata: ConcurrentHashMap<String, String>,
        category: GoogleDorkLibrary.DorkCategory,
        pii: ExtractedPii
    ) {
        val cat = category.key
        mergeCsv(metadata, "dork_${cat}_phones", pii.phones)
        mergeCsv(metadata, "dork_${cat}_emails", pii.emails)
        mergePipe(metadata, "dork_${cat}_addresses", pii.addresses)
        mergeCsv(metadata, "dork_${cat}_relatives", pii.relatives)
        mergeCsv(metadata, "dork_${cat}_ages", pii.ages)
        mergeCsv(metadata, "dork_${cat}_employers", pii.employers)
        mergeCsv(metadata, "dork_phones", pii.phones)
        mergeCsv(metadata, "dork_emails", pii.emails)
        mergePipe(metadata, "dork_addresses", pii.addresses)
        mergeCsv(metadata, "dork_relatives", pii.relatives)
        mergeCsv(metadata, "dork_employers", pii.employers)
        if (pii.socialUrls.isNotEmpty()) {
            mergeNewline(metadata, "dork_social_urls", pii.socialUrls)
        }
        if (pii.profileUrls.isNotEmpty()) {
            mergeNewline(metadata, "dork_profile_urls", pii.profileUrls)
        }
    }

    fun recordCorroboration(
        phoneHits: ConcurrentHashMap<String, AtomicInteger>,
        emailHits: ConcurrentHashMap<String, AtomicInteger>,
        addressHits: ConcurrentHashMap<String, AtomicInteger>,
        pii: ExtractedPii
    ) {
        pii.phones.forEach { phone ->
            phoneHits.computeIfAbsent(normalizePhone(phone)) { AtomicInteger(0) }.incrementAndGet()
        }
        pii.emails.forEach { email ->
            emailHits.computeIfAbsent(email.lowercase()) { AtomicInteger(0) }.incrementAndGet()
        }
        pii.addresses.forEach { addr ->
            addressHits.computeIfAbsent(addr.lowercase()) { AtomicInteger(0) }.incrementAndGet()
        }
    }

    fun finalizeNeedleFindings(
        metadata: ConcurrentHashMap<String, String>,
        phoneHits: ConcurrentHashMap<String, AtomicInteger>,
        emailHits: ConcurrentHashMap<String, AtomicInteger>,
        addressHits: ConcurrentHashMap<String, AtomicInteger>,
        queryPhone: String
    ) {
        val ranked = mutableListOf<Pair<Int, String>>()

        phoneHits.forEach { (digits, count) ->
            val hits = count.get()
            val queryMatch = queryPhone.isNotBlank() && digits == normalizePhone(queryPhone)
            val confidence = hits + if (queryMatch) 2 else 0
            if (hits >= 2 || confidence >= 3) {
                val display = formatPhone(digits)
                val suffix = if (queryMatch) "|query_match=true" else ""
                ranked += confidence to "PHONE|$display|hits=$hits|confidence=$confidence$suffix"
            }
        }
        emailHits.forEach { (email, count) ->
            val hits = count.get()
            if (hits >= 2) {
                ranked += hits to "EMAIL|$email|hits=$hits|confidence=$hits"
            }
        }
        addressHits.forEach { (addr, count) ->
            val hits = count.get()
            if (hits >= 2) {
                ranked += hits to "ADDRESS|${addr.take(120)}|hits=$hits|confidence=$hits"
            }
        }

        if (ranked.isNotEmpty()) {
            metadata["dork_needle_findings"] = ranked
                .sortedByDescending { it.first }
                .map { it.second }
                .joinToString("\n")
        }

        val highPhones = phoneHits.entries
            .filter { (digits, count) ->
                val c = count.get() + if (queryPhone.isNotBlank() && digits == normalizePhone(queryPhone)) 2 else 0
                count.get() >= 2 || c >= 3
            }
            .sortedByDescending { it.value.get() }
            .map { formatPhone(it.key) }
        if (highPhones.isNotEmpty()) {
            metadata["dork_corroborated_phones"] = highPhones.joinToString(", ")
        }
        val highEmails = emailHits.entries.filter { it.value.get() >= 2 }.sortedByDescending { it.value.get() }.map { it.key }
        if (highEmails.isNotEmpty()) {
            metadata["dork_corroborated_emails"] = highEmails.joinToString(", ")
        }
    }

    fun totalHitCount(metadata: Map<String, String>): Int =
        metadata.filterKeys { it.matches(Regex("dork_[a-z]+_\\d+_title")) }.size

    fun hitsForCategory(metadata: Map<String, String>, category: GoogleDorkLibrary.DorkCategory): List<DorkHit> {
        val cat = category.key
        val count = metadata["dork_${cat}_count"]?.toIntOrNull()
            ?: metadata.keys.count { it.startsWith("dork_${cat}_") && it.endsWith("_title") }
        return (1..count).mapNotNull { n ->
            val title = metadata["dork_${cat}_${n}_title"] ?: return@mapNotNull null
            DorkHit(
                title = title,
                url = metadata["dork_${cat}_${n}_url"].orEmpty(),
                snippet = metadata["dork_${cat}_${n}_snippet"].orEmpty(),
                query = metadata["dork_${cat}_${n}_query"].orEmpty()
            )
        }
    }

    fun allCategories(metadata: Map<String, String>): List<GoogleDorkLibrary.DorkCategory> =
        GoogleDorkLibrary.DorkCategory.entries.filter { hitsForCategory(metadata, it).isNotEmpty() }

    fun parseNeedleLine(line: String): Map<String, String> {
        val parts = line.split("|")
        if (parts.isEmpty()) return emptyMap()
        val map = mutableMapOf("type" to parts[0])
        if (parts.size > 1) map["value"] = parts[1]
        parts.drop(2).forEach { token ->
            val eq = token.indexOf('=')
            if (eq > 0) map[token.substring(0, eq)] = token.substring(eq + 1)
        }
        return map
    }

    private fun mergeCsv(metadata: ConcurrentHashMap<String, String>, key: String, values: List<String>) {
        if (values.isEmpty()) return
        val existing = metadata[key]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toMutableSet()
            ?: mutableSetOf()
        existing.addAll(values)
        metadata[key] = existing.joinToString(", ")
    }

    private fun mergePipe(metadata: ConcurrentHashMap<String, String>, key: String, values: List<String>) {
        if (values.isEmpty()) return
        val existing = metadata[key]?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() }?.toMutableSet()
            ?: mutableSetOf()
        existing.addAll(values)
        metadata[key] = existing.joinToString(" | ")
    }

    private fun mergeNewline(metadata: ConcurrentHashMap<String, String>, key: String, values: List<String>) {
        if (values.isEmpty()) return
        val existing = metadata[key]?.lines()?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
        existing.addAll(values)
        metadata[key] = existing.joinToString("\n")
    }

    private fun normalizePhone(phone: String): String =
        phone.filter { it.isDigit() }.takeLast(10)

    private fun formatPhone(digits: String): String {
        val d = digits.filter { it.isDigit() }.takeLast(10)
        return if (d.length == 10) "(${d.substring(0, 3)}) ${d.substring(3, 6)}-${d.substring(6)}" else digits
    }
}
