package com.twoskoops707.sixdegrees.scraper

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

data class ScrapeResult(
    val source: String,
    val found: Boolean,
    val fields: Map<String, String>,
    val rawSnippets: List<String> = emptyList(),
    /** Set when the source returned a Cloudflare/bot-challenge page instead of real content. */
    val blocked: Boolean = false
)

private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"

/** Markers that indicate a Cloudflare / bot-challenge page rather than real content. */
private val CLOUDFLARE_MARKERS = listOf(
    "cf-challenge-running",
    "Just a moment",
    "Enable JavaScript",
    "challenge-platform",
    "cf-browser-verification",
    "cf-turnstile",
    "Checking your browser before accessing",
    "Attention Required! | Cloudflare",
    "cdn-cgi/challenge-platform"
)

private fun isCloudflarePage(body: String, code: Int): Boolean =
    code == 403 || code == 429 || code == 503 ||
        CLOUDFLARE_MARKERS.any { body.contains(it, ignoreCase = true) }

private fun queryTokens(query: String): List<String> =
    query.trim().lowercase().split(Regex("\\s+")).filter { it.length > 1 }

/** Require at least 2 tokens when query has 2+, else 1 — reduces name-collision false positives. */
private fun textMatchesQuery(text: String, query: String): Boolean {
    val tokens = queryTokens(query)
    if (tokens.isEmpty()) return true
    val lower = text.lowercase()
    val required = if (tokens.size >= 2) 2 else 1
    return tokens.count { lower.contains(it) } >= required
}

/**
 * Result of a raw HTTP GET — either Cloudflare-blocked, or a normal body.
 * Captures the HTTP status code so callers can react to 403/429 without re-parsing.
 */
private data class RawFetch(val code: Int, val body: String, val blocked: Boolean)

private fun get(client: OkHttpClient, url: String): RawFetch {
    val req = Request.Builder()
        .url(url)
        .header("User-Agent", UA)
        .header("Accept", ACCEPT)
        .header("Accept-Language", "en-US,en;q=0.9")
        .build()
    return try {
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            val code = resp.code
            if (isCloudflarePage(body, code)) RawFetch(code, body, blocked = true)
            else RawFetch(code, body, blocked = false)
        }
    } catch (_: Exception) {
        RawFetch(0, "", blocked = false)
    }
}

/**
 * ThatsThem is fronted by Cloudflare. If we get a challenge page, emit `blocked=true`
 * (instead of the previous `found=false, blocked=false`) so the caller can cache the
 * domain as blocked and stop retrying it on every search.
 */
suspend fun scrapeThatsThem(query: String, client: OkHttpClient): ScrapeResult {
    return try {
        val slug = query.trim().replace(Regex("\\s+"), "-")
        val fetch = get(client, "https://thatsthem.com/name/$slug")
        if (fetch.blocked) return ScrapeResult(source = "ThatsThem", found = false, fields = emptyMap(), blocked = true)
        val doc = Jsoup.parse(fetch.body)
        val records = doc.select(".ThatsThem-people-record, [class*='people-record'], [class*='result-item']")
        val names = mutableListOf<String>()
        val addresses = mutableListOf<String>()
        val phones = mutableListOf<String>()
        records.forEach { el ->
            val name = el.select("[class*='name'], h2, h3").firstOrNull()?.text()?.trim() ?: ""
            if (name.isNotEmpty() && !textMatchesQuery(name, query)) return@forEach
            val addr = el.select("[class*='address'], [class*='location']").firstOrNull()?.text()?.trim() ?: ""
            val phone = el.select("[class*='phone'], [class*='tel']").firstOrNull()?.text()?.trim() ?: ""
            if (name.isNotEmpty()) names.add(name)
            if (addr.isNotEmpty()) addresses.add(addr)
            if (phone.isNotEmpty()) phones.add(phone)
        }
        ScrapeResult(
            source = "ThatsThem",
            found = names.isNotEmpty() || addresses.isNotEmpty() || phones.isNotEmpty(),
            fields = mapOf(
                "count" to names.size.toString(),
                "name" to names.firstOrNull().orEmpty(),
                "addresses" to addresses.joinToString(" | "),
                "phones" to phones.joinToString(" | ")
            )
        )
    } catch (_: Exception) {
        ScrapeResult(source = "ThatsThem", found = false, fields = emptyMap())
    }
}

suspend fun scrapeFastPeopleSearch(firstName: String, lastName: String, client: OkHttpClient): ScrapeResult {
    return try {
        val slug = "${firstName.trim().lowercase()}-${lastName.trim().lowercase()}"
        val fetch = get(client, "https://www.fastpeoplesearch.com/name/$slug")
        if (fetch.blocked) return ScrapeResult(source = "FastPeopleSearch", found = false, fields = emptyMap(), blocked = true)
        val doc = Jsoup.parse(fetch.body)
        val cards = doc.select("[class*='card'], [class*='result'], [class*='person']")
        val addresses = mutableListOf<String>()
        val phones = mutableListOf<String>()
        val ages = mutableListOf<String>()
        val fullQuery = "$firstName $lastName".trim()
        cards.forEach { el ->
            val cardText = el.text()
            if (!textMatchesQuery(cardText, fullQuery)) return@forEach
            val addr = el.select("[class*='address'], [class*='location']").firstOrNull()?.text()?.trim() ?: ""
            val phone = el.select("[class*='phone'], [class*='tel']").firstOrNull()?.text()?.trim() ?: ""
            val age = el.select("[class*='age'], [class*='born']").firstOrNull()?.text()?.trim() ?: ""
            if (addr.isNotEmpty()) addresses.add(addr)
            if (phone.isNotEmpty()) phones.add(phone)
            if (age.isNotEmpty()) ages.add(age)
        }
        ScrapeResult(
            source = "FastPeopleSearch",
            found = addresses.isNotEmpty() || phones.isNotEmpty() || ages.isNotEmpty(),
            fields = mapOf(
                "count" to maxOf(addresses.size, phones.size, ages.size).toString(),
                "addresses" to addresses.joinToString(" | "),
                "phones" to phones.joinToString(" | "),
                "ages" to ages.joinToString(" | ")
            )
        )
    } catch (_: Exception) {
        ScrapeResult(source = "FastPeopleSearch", found = false, fields = emptyMap())
    }
}

/**
 * ProxyNova COMB search results are loaded via JavaScript XHR after page load — the
 * initial HTML returned to a plain OkHttp GET contains no `<table>` rows. We probe the
 * page anyway (cheap) and, if we detect a Cloudflare challenge or a JS-only shell, we
 * mark it `blocked` so the caller can cache the domain and stop retrying. The actual
 * breach data is still surfaced by the dedicated ProxyNova breach-API path in
 * `OsintRepository` when present.
 */
suspend fun scrapeProxyNova(email: String, client: OkHttpClient): ScrapeResult {
    return try {
        val encoded = URLEncoder.encode(email, "UTF-8")
        val fetch = get(client, "https://www.proxynova.com/tools/comb-database-search/?q=$encoded")
        if (fetch.blocked) return ScrapeResult(source = "ProxyNova", found = false, fields = emptyMap(), blocked = true)
        val doc = Jsoup.parse(fetch.body)
        val emailLower = email.trim().lowercase()
        val rows = doc.select("table tr").drop(1)
            .filter { row -> row.text().lowercase().contains(emailLower) }
        val records = mutableListOf<String>()
        rows.take(20).forEach { row ->
            val cells = row.select("td")
            if (cells.size >= 2) {
                records.add(cells.joinToString(":") { it.text().trim() })
            }
        }
        // If no rows were found in static HTML, mark blocked so callers don't retry —
        // the table is JS-rendered and a plain GET can never return data here.
        val blocked = records.isEmpty() && fetch.body.contains("<table", ignoreCase = true)
        ScrapeResult(
            source = "ProxyNova",
            found = records.isNotEmpty(),
            fields = mapOf(
                "breach_count" to records.size.toString(),
                "records" to records.joinToString(" | ")
            ),
            blocked = blocked
        )
    } catch (_: Exception) {
        ScrapeResult(source = "ProxyNova", found = false, fields = emptyMap())
    }
}
