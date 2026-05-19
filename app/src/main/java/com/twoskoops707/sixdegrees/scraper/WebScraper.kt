package com.twoskoops707.sixdegrees.scraper

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

private fun get(client: OkHttpClient, url: String): String {
    val req = Request.Builder()
        .url(url)
        .header("User-Agent", UA)
        .header("Accept", ACCEPT)
        .build()
    return client.newCall(req).execute().use { it.body?.string() ?: "" }
}

suspend fun scrapeAhmia(query: String, client: OkHttpClient): ScrapeResult {
    return try {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val html = get(client, "https://ahmia.fi/search/?q=$encoded")
        val doc = Jsoup.parse(html)
        val results = doc.select(".result")
        val titles = mutableListOf<String>()
        val onions = mutableListOf<String>()
        val snippets = mutableListOf<String>()
        results.take(10).forEach { el ->
            val title = el.select(".title").text().trim()
            val desc = el.select(".description").text().trim()
            val onion = el.select("cite, .onion").text().trim()
            if (title.isNotEmpty()) titles.add(title)
            if (onion.isNotEmpty()) onions.add(onion)
            if (desc.isNotEmpty()) snippets.add(desc)
        }
        ScrapeResult(
            source = "Ahmia",
            found = results.isNotEmpty(),
            fields = mapOf(
                "count" to results.size.toString(),
                "titles" to titles.joinToString(" | "),
                "onion_urls" to onions.joinToString(" | ")
            ),
            rawSnippets = snippets
        )
    } catch (_: Exception) {
        ScrapeResult(source = "Ahmia", found = false, fields = emptyMap())
    }
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
            val addr = el.select("[class*='address'], [class*='location']").firstOrNull()?.text()?.trim() ?: ""
            val phone = el.select("[class*='phone'], [class*='tel']").firstOrNull()?.text()?.trim() ?: ""
            if (name.isNotEmpty()) names.add(name)
            if (addr.isNotEmpty()) addresses.add(addr)
            if (phone.isNotEmpty()) phones.add(phone)
        }
        ScrapeResult(
            source = "ThatsThem",
            found = records.isNotEmpty(),
            fields = mapOf(
                "count" to records.size.toString(),
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
        cards.forEach { el ->
            val addr = el.select("[class*='address'], [class*='location']").firstOrNull()?.text()?.trim() ?: ""
            val phone = el.select("[class*='phone'], [class*='tel']").firstOrNull()?.text()?.trim() ?: ""
            val age = el.select("[class*='age'], [class*='born']").firstOrNull()?.text()?.trim() ?: ""
            if (addr.isNotEmpty()) addresses.add(addr)
            if (phone.isNotEmpty()) phones.add(phone)
            if (age.isNotEmpty()) ages.add(age)
        }
        ScrapeResult(
            source = "FastPeopleSearch",
            found = cards.isNotEmpty(),
            fields = mapOf(
                "count" to cards.size.toString(),
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
        val rows = doc.select("table tr").drop(1)
        val records = mutableListOf<String>()
        rows.take(20).forEach { row ->
            val cells = row.select("td")
            if (cells.size >= 2) {
                records.add(cells.joinToString(":") { it.text().trim() })
            }
        }
        ScrapeResult(
            source = "ProxyNova",
            found = rows.isNotEmpty(),
            fields = mapOf(
                "breach_count" to rows.size.toString(),
                "records" to records.joinToString(" | ")
            )
        )
    } catch (_: Exception) {
        ScrapeResult(source = "ProxyNova", found = false, fields = emptyMap())
    }
}

suspend fun scrapeLeakPeek(query: String, client: OkHttpClient): ScrapeResult {
    return try {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val html = get(client, "https://leakpeek.com/search?q=$encoded")
        val doc = Jsoup.parse(html)
        val snippets = doc.select("[class*='result'], [class*='leak'], [class*='breach'], [class*='record']")
        val visible = mutableListOf<String>()
        snippets.take(10).forEach { el ->
            val text = el.text().trim()
            if (text.isNotEmpty()) visible.add(text)
        }
        ScrapeResult(
            source = "LeakPeek",
            found = snippets.isNotEmpty(),
            fields = mapOf(
                "count" to snippets.size.toString(),
                "snippets" to visible.joinToString(" | ")
            ),
            rawSnippets = visible
        )
    } catch (_: Exception) {
        ScrapeResult(source = "LeakPeek", found = false, fields = emptyMap())
    }
}

suspend fun scrapeHackerTarget(query: String, type: String, client: OkHttpClient): ScrapeResult {
    return try {
        val url = when (type) {
            "ip" -> "https://api.hackertarget.com/reversedns/?q=$query"
            "email" -> "https://api.hackertarget.com/findemail/?q=$query"
            else -> "https://api.hackertarget.com/hostsearch/?q=$query"
        }
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", ACCEPT)
            .build()
        val text = client.newCall(req).execute().use { it.body?.string() ?: "" }
        val lines = text.lines().filter { it.isNotBlank() && !it.startsWith("error") }
        ScrapeResult(
            source = "HackerTarget",
            found = lines.isNotEmpty(),
            fields = mapOf(
                "count" to lines.size.toString(),
                "results" to lines.take(20).joinToString(" | ")
            ),
            rawSnippets = lines.take(20)
        )
    } catch (_: Exception) {
        ScrapeResult(source = "HackerTarget", found = false, fields = emptyMap())
    }
}

suspend fun scrapeIntelXSearch(query: String, client: OkHttpClient): ScrapeResult {
    return try {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val html = get(client, "https://intelx.io/?s=$encoded")
        val doc = Jsoup.parse(html)
        val results = doc.select("[class*='result'], [class*='item'], [class*='search-result']")
        val snippets = mutableListOf<String>()
        results.take(10).forEach { el ->
            val text = el.text().trim()
            if (text.isNotEmpty()) snippets.add(text)
        }
        ScrapeResult(
            source = "IntelX",
            found = results.isNotEmpty(),
            fields = mapOf(
                "count" to results.size.toString(),
                "snippets" to snippets.joinToString(" | ")
            ),
            rawSnippets = snippets
        )
    } catch (_: Exception) {
        ScrapeResult(source = "IntelX", found = false, fields = emptyMap())
    }
}

suspend fun scrapeWaybackUrls(query: String, client: OkHttpClient): ScrapeResult {
    return try {
        val domain = query.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        val req = Request.Builder()
            .url("https://web.archive.org/cdx/search/cdx?url=*.$domain&output=json&fl=original,timestamp&limit=20&collapse=urlkey")
            .header("User-Agent", UA)
            .header("Accept", ACCEPT)
            .build()
        val text = client.newCall(req).execute().use { it.body?.string() ?: "" }
        val lines = text.lines().filter { it.contains(domain) }
        val subdomains = lines.mapNotNull { line ->
            Regex("https?://([^/\"]+)").find(line)?.groupValues?.getOrNull(1)
        }.distinct().filter { it != domain }
        val paths = lines.mapNotNull { line ->
            Regex("https?://[^/\"]+(/[^\"]+)").find(line)?.groupValues?.getOrNull(1)
        }.distinct()
        ScrapeResult(
            source = "WaybackMachine",
            found = lines.isNotEmpty(),
            fields = mapOf(
                "count" to lines.size.toString(),
                "subdomains" to subdomains.take(10).joinToString(" | "),
                "paths" to paths.take(10).joinToString(" | ")
            ),
            rawSnippets = lines.take(10)
        )
    } catch (_: Exception) {
        ScrapeResult(source = "WaybackMachine", found = false, fields = emptyMap())
    }
}
