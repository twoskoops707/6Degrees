package com.twoskoops707.sixdegrees.scraper

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

data class ScrapeResult(
    val source: String,
    val found: Boolean,
    val fields: Map<String, String>,
    val rawSnippets: List<String> = emptyList()
)

private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"

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

private fun get(client: OkHttpClient, url: String): String {
    val req = Request.Builder()
        .url(url)
        .header("User-Agent", UA)
        .header("Accept", ACCEPT)
        .build()
    return client.newCall(req).execute().use { it.body?.string() ?: "" }
}

suspend fun scrapeThatsThem(query: String, client: OkHttpClient): ScrapeResult {
    return try {
        val slug = query.trim().replace(Regex("\\s+"), "-")
        val html = get(client, "https://thatsthem.com/name/$slug")
        val doc = Jsoup.parse(html)
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
        val html = get(client, "https://www.fastpeoplesearch.com/name/$slug")
        val doc = Jsoup.parse(html)
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

suspend fun scrapeProxyNova(email: String, client: OkHttpClient): ScrapeResult {
    return try {
        val encoded = URLEncoder.encode(email, "UTF-8")
        val html = get(client, "https://www.proxynova.com/tools/comb-breach/?q=$encoded")
        val doc = Jsoup.parse(html)
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
        ScrapeResult(
            source = "ProxyNova",
            found = records.isNotEmpty(),
            fields = mapOf(
                "breach_count" to records.size.toString(),
                "records" to records.joinToString(" | ")
            )
        )
    } catch (_: Exception) {
        ScrapeResult(source = "ProxyNova", found = false, fields = emptyMap())
    }
}
