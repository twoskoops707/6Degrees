package com.twoskoops707.sixdegrees.data.repository

import android.content.Context
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.twoskoops707.sixdegrees.data.local.OsintDatabase
import com.twoskoops707.sixdegrees.data.local.entity.OsintReportEntity
import com.twoskoops707.sixdegrees.data.local.entity.PersonEntity
import com.twoskoops707.sixdegrees.data.osint.OsintToolRegistry
import com.twoskoops707.sixdegrees.data.remote.RetrofitClient
import com.twoskoops707.sixdegrees.domain.model.CandidateProfile
import com.twoskoops707.sixdegrees.domain.model.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLEncoder
import java.util.Collections
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private class DateAdapter {
    @ToJson fun toJson(date: Date): Long = date.time
    @FromJson fun fromJson(value: Long): Date = Date(value)
}

private val SOURCE_ABBREVS = mapOf(
    "fastpeoplesearch" to "fps",
    "thatsthem" to "tt",
    "truepeoplesearch" to "tps",
    "zabasearch" to "zaba",
    "411.com" to "411",
    "spokeo" to "spk",
    "radaris" to "radaris",
    "peekyou" to "peekyou",
    "nuwber" to "nuwber",
    "whitepages" to "wp",
    "checkpeople" to "checkpeople",
    "beenverified" to "bv",
    "instantcheckmate" to "icm",
    "usphonebook" to "uspb",
    "familytreenow" to "ftn",
    "proxynova breach" to "proxynova",
    "hackertarget email" to "hackertarget_email",
    "hackertarget host" to "hackertarget",
    "800notes" to "800notes",
    "wayback cdx" to "wayback",
    "ahmia" to "ahmia"
)

class OsintRepository(context: Context) {

    private val appCtx = context.applicationContext
    private val apiKeys = com.twoskoops707.sixdegrees.data.ApiKeyManager(context)
    private val db = OsintDatabase.getDatabase(context)
    private val moshi = Moshi.Builder().add(DateAdapter()).add(KotlinJsonAdapterFactory()).build()

    private val fastHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val termuxRunner by lazy { TermuxToolRunner(appCtx) }

    private fun torHttpClientOrNull(): OkHttpClient? = try {
        val probe = java.net.Socket()
        probe.connect(InetSocketAddress("127.0.0.1", 9050), 500)
        probe.close()
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", 9050))
        OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    } catch (_: Exception) { null }

    private data class PersonRecord(
        val name: String,
        val age: String = "",
        val location: String = "",
        val phones: List<String> = emptyList(),
        val address: String = "",
        val relatives: List<String> = emptyList(),
        val photoUrl: String? = null,
        val profileUrl: String? = null,
        val source: String = ""
    )

    private data class ScrapeOut(
        val found: Boolean,
        val blocked: Boolean,
        val fields: Map<String, String> = emptyMap(),
        val persons: List<PersonRecord> = emptyList()
    )

    private data class DdgResult(val title: String, val snippet: String, val url: String)

    private data class ExtractedData(
        val phones: List<String> = emptyList(),
        val emails: List<String> = emptyList(),
        val addresses: List<String> = emptyList(),
        val ages: List<String> = emptyList(),
        val relatives: List<String> = emptyList(),
        val socialUrls: List<String> = emptyList(),
        val profileUrls: List<String> = emptyList(),
        val snippets: List<String> = emptyList()
    )

    private fun tryScrapeUrl(url: String): ScrapeOut {
        return try {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val code = resp.code
            val body = resp.body?.string() ?: ""
            resp.close()

            if (code == 403 || code == 429
                || body.contains("cf-challenge-running")
                || body.contains("Just a moment", ignoreCase = true)
                || body.contains("Enable JavaScript")) {
                return ScrapeOut(false, true)
            }

            if (code == 404 || code >= 500 || body.isBlank()) return ScrapeOut(false, false)

            val doc = Jsoup.parse(body)
            doc.select("script, style, nav, footer, header, noscript, iframe").remove()
            val text = doc.body()?.text() ?: ""

            val notFoundPhrases = listOf(
                "no results", "no records found", "0 results found",
                "couldn't find", "not found", "does not exist", "no matches"
            )
            if (text.length < 150 || notFoundPhrases.any { text.lowercase().contains(it) }) {
                return ScrapeOut(false, false)
            }

            val fields = mutableMapOf<String, String>()
            fields["title"] = doc.title().take(120)
            fields["snippet"] = text.take(600)

            val ogImage = doc.selectFirst("meta[property=og:image]")?.attr("content")
                ?: doc.selectFirst("meta[name=twitter:image]")?.attr("content")
                ?: doc.selectFirst("meta[name=twitter:image:src]")?.attr("content")
                ?: doc.selectFirst("link[rel=image_src]")?.attr("href")
            if (!ogImage.isNullOrBlank() && ogImage.startsWith("http")) fields["image_url"] = ogImage

            val phones = Regex("""\(?(\d{3})\)?[.\-\s](\d{3})[.\-\s](\d{4})""").findAll(text)
                .map { "(${it.groupValues[1]}) ${it.groupValues[2]}-${it.groupValues[3]}" }
                .distinct().take(5).toList()
            val emails = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""").findAll(text)
                .map { it.value.lowercase() }
                .filter { !it.contains("example") && !it.contains("domain") }
                .distinct().take(3).toList()

            if (phones.isNotEmpty()) fields["phones"] = phones.joinToString(", ")
            if (emails.isNotEmpty()) fields["emails"] = emails.joinToString(", ")

            ScrapeOut(true, false, fields)
        } catch (_: Exception) {
            ScrapeOut(false, false)
        }
    }

    private fun ddgHtmlSearch(query: String): List<DdgResult> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://html.duckduckgo.com/html/?q=$encoded")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://duckduckgo.com/")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank()) return emptyList()
            val doc = Jsoup.parse(body)
            doc.select(".result:not(.result--more), .result--web").take(12).mapNotNull { el ->
                val title = el.selectFirst(".result__a, .result__title a")?.text()?.trim() ?: return@mapNotNull null
                val snippet = el.selectFirst(".result__snippet, .result-snippet")?.text()?.trim() ?: ""
                val url = el.selectFirst(".result__url, .result-url")?.text()?.trim() ?: ""
                if (title.isBlank()) null else DdgResult(title, snippet, url)
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun extractDataFromDdgResults(results: List<DdgResult>): ExtractedData {
        val phoneRegex = Regex("""\(?(\d{3})\)?[.\-\s](\d{3})[.\-\s](\d{4})""")
        val emailRegex = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
        val ageRegex = Regex("""(?i)\bage[:\s]+(\d{2,3})\b|\b(\d{2,3})\s*years?\s*old\b|\baged?\s+(\d{2,3})\b""")
        val socialDomains = setOf("linkedin.com", "facebook.com", "twitter.com", "instagram.com", "tiktok.com", "youtube.com", "pinterest.com", "reddit.com")
        val peopleDomains = setOf("fastpeoplesearch.com", "whitepages.com", "spokeo.com", "peoplefinder.com", "beenverified.com", "truepeoplesearch.com", "radaris.com", "thatsthem.com", "zabasearch.com", "411.com", "intelius.com", "truthfinder.com", "familytreenow.com", "usphonebook.com", "addresses.com")
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        val ages = mutableListOf<String>()
        val relatives = mutableListOf<String>()
        val socialUrls = mutableListOf<String>()
        val profileUrls = mutableListOf<String>()
        val snippets = mutableListOf<String>()
        val addresses = mutableListOf<String>()
        for (r in results) {
            val text = "${r.title} ${r.snippet}"
            phoneRegex.findAll(text).map { "(${it.groupValues[1]}) ${it.groupValues[2]}-${it.groupValues[3]}" }.forEach { phones.add(it) }
            emailRegex.findAll(text).map { it.value.lowercase() }
                .filter { !it.contains("example") && !it.endsWith(".png") && !it.endsWith(".jpg") }
                .forEach { emails.add(it) }
            ageRegex.find(text)?.let { m ->
                val age = m.groupValues.drop(1).firstOrNull { it.isNotBlank() }
                if (age != null && (age.toIntOrNull() ?: 0) in 18..120) ages.add(age)
            }
            if (r.snippet.isNotBlank()) snippets.add("${r.title}: ${r.snippet}".take(200))
            val urlLower = r.url.lowercase()
            when {
                socialDomains.any { urlLower.contains(it) } -> socialUrls.add(r.url)
                peopleDomains.any { urlLower.contains(it) } -> profileUrls.add(r.url)
            }
            Regex("""(?i)\d+\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*\s+(?:St|Ave|Blvd|Dr|Rd|Ln|Ct|Way|Pl|Pkwy)\b[.,]?""").find(text)?.value?.trim()?.let { addresses.add(it) }
            Regex("""(?i)(?:relatives?|associates?|related\s+to|family)[:\s]+([^.\n]+)""").find(text)?.groupValues?.get(1)?.split(",")?.forEach { rel ->
                val name = rel.trim().take(40)
                if (name.length > 3 && name.contains(" ")) relatives.add(name)
            }
        }
        return ExtractedData(
            phones = phones.distinct().take(10),
            emails = emails.distinct().take(5),
            addresses = addresses.distinct().take(5),
            ages = ages.distinct().take(3),
            relatives = relatives.distinct().take(10),
            socialUrls = socialUrls.distinct().take(8),
            profileUrls = profileUrls.distinct().take(8),
            snippets = snippets.take(15)
        )
    }

    private fun buildPersonQueries(name: String, city: String, state: String, phone: String = "", email: String = "", username: String = ""): List<Pair<String, String>> {
        val loc = listOf(city, state).filter { it.isNotBlank() }.joinToString(" ")
        val queries = mutableListOf(
            "General" to "\"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
            "Phone" to "\"$name\" phone number${if (loc.isNotBlank()) " $loc" else ""}",
            "Address" to "\"$name\" address${if (loc.isNotBlank()) " $loc" else ""}",
            "FPS" to "site:fastpeoplesearch.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
            "Whitepages" to "site:whitepages.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
            "Spokeo" to "site:spokeo.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
            "Radaris" to "site:radaris.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
            "BeenVerified" to "site:beenverified.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
            "TruePeopleSearch" to "site:truepeoplesearch.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
            "PeopleFinder" to "site:peoplefinder.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
            "Relatives" to "\"$name\" relatives family${if (loc.isNotBlank()) " $loc" else ""}",
            "LinkedIn" to "site:linkedin.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
            "Facebook" to "site:facebook.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
            "Criminal" to "\"$name\" criminal arrest court record${if (loc.isNotBlank()) " $loc" else ""}",
            "News" to "\"$name\"${if (loc.isNotBlank()) " $loc" else ""} news",
            "Employment" to "\"$name\" employer company job${if (loc.isNotBlank()) " $loc" else ""}",
            "Property" to "\"$name\" property records${if (state.isNotBlank()) " $state" else ""}",
            "Voter" to "\"$name\" voter registration${if (state.isNotBlank()) " $state" else ""}"
        )
        if (phone.isNotBlank()) queries.add("PhoneCrossRef" to "\"$phone\" \"$name\"")
        if (email.isNotBlank()) queries.add("EmailCrossRef" to "\"$email\" \"$name\"")
        if (username.isNotBlank()) queries.add("UsernameCrossRef" to "\"$username\" \"$name\"")
        return queries
    }

    private fun duckDuckGoSearch(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val jsonUrl = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val req = Request.Builder().url(jsonUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isNotBlank() && body.startsWith("{")) {
                try {
                    val json = JSONObject(body)
                    val abstract = json.optString("AbstractText", "")
                    val answer = json.optString("Answer", "")
                    val source = json.optString("AbstractSource", "")
                    val sourceUrl = json.optString("AbstractURL", "")
                    val definition = json.optString("Definition", "")
                    if (abstract.isNotBlank()) result["ddg_abstract"] = abstract.take(800)
                    else if (definition.isNotBlank()) result["ddg_abstract"] = definition.take(800)
                    if (answer.isNotBlank()) result["ddg_answer"] = answer.take(400)
                    if (source.isNotBlank()) result["ddg_source"] = source
                    if (sourceUrl.isNotBlank()) result["ddg_source_url"] = sourceUrl
                    val relatedTopics = json.optJSONArray("RelatedTopics")
                    if (relatedTopics != null) {
                        val snippets = mutableListOf<String>()
                        val urls = mutableListOf<String>()
                        for (i in 0 until minOf(8, relatedTopics.length())) {
                            val topic = relatedTopics.optJSONObject(i) ?: continue
                            val text = topic.optString("Text", "")
                            val url = topic.optString("FirstURL", "")
                            if (text.isNotBlank()) snippets.add(text.take(200))
                            if (url.isNotBlank()) urls.add(url)
                        }
                        if (snippets.isNotEmpty()) {
                            result["ddg_web_snippets"] = snippets.joinToString("\n").take(1200)
                            if (result["ddg_abstract"].isNullOrBlank()) result["ddg_abstract"] = snippets.take(3).joinToString(" ").take(800)
                        }
                        if (urls.isNotEmpty()) result["ddg_urls"] = urls.joinToString(", ").take(600)
                    }
                    val infobox = json.optJSONObject("Infobox")
                    if (infobox != null) {
                        val content = infobox.optJSONArray("content")
                        if (content != null) {
                            val infoRows = mutableListOf<String>()
                            for (i in 0 until minOf(12, content.length())) {
                                val item = content.optJSONObject(i) ?: continue
                                val label = item.optString("label", "")
                                val value = item.optString("value", "")
                                if (label.isNotBlank() && value.isNotBlank()) infoRows.add("$label: $value")
                            }
                            if (infoRows.isNotEmpty()) result["ddg_infobox"] = infoRows.joinToString("\n")
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        if (result["ddg_abstract"].isNullOrBlank() && result["ddg_web_snippets"].isNullOrBlank()) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val htmlUrl = "https://html.duckduckgo.com/html/?q=$encoded"
                val req = Request.Builder().url(htmlUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .build()
                val resp = fastHttpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                resp.close()
                val doc = Jsoup.parse(body)
                val snippets = doc.select(".result__snippet, .result-snippet").take(6).map { it.text() }.filter { it.isNotBlank() }
                val links = doc.select(".result__url, .result-url").take(6).map { it.text() }.filter { it.isNotBlank() }
                if (snippets.isNotEmpty()) {
                    result["ddg_web_snippets"] = snippets.joinToString("\n").take(1200)
                    result["ddg_abstract"] = snippets.take(3).joinToString(" ").take(800)
                }
                if (links.isNotEmpty()) result["ddg_urls"] = links.joinToString(", ").take(400)
            } catch (_: Exception) {}
        }
        result["ddg_snippet"] = (result["ddg_abstract"] ?: result["ddg_web_snippets"] ?: "").take(800)
        return result
    }

    private fun searchWikipedia(name: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val encoded = URLEncoder.encode(name, "UTF-8")
            val searchUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$encoded"
            val req = Request.Builder().url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (compatible; OsintApp/1.0)")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isNotBlank() && body.startsWith("{")) {
                val json = JSONObject(body)
                val extract = json.optString("extract", "")
                val title = json.optString("title", "")
                val pageUrl = json.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page", "") ?: ""
                if (extract.isNotBlank()) {
                    result["wikipedia_extract"] = extract.take(1200)
                    result["wikipedia_title"] = title
                    if (pageUrl.isNotBlank()) result["wikipedia_url"] = pageUrl
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun scrapeCrtSh(domain: String): ScrapeOut {
        return try {
            val req = Request.Builder().url("https://crt.sh/?q=${URLEncoder.encode(domain, "UTF-8")}&output=json")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || body == "[]") return ScrapeOut(false, false)
            val arr = org.json.JSONArray(body)
            val domains = mutableSetOf<String>()
            val issuers = mutableSetOf<String>()
            for (i in 0 until minOf(50, arr.length())) {
                val obj = arr.optJSONObject(i) ?: continue
                obj.optString("name_value", "").split("\n").forEach { n -> if (n.isNotBlank()) domains.add(n.trim().lowercase()) }
                obj.optString("issuer_ca_id", "").takeIf { it.isNotBlank() }?.let {}
                obj.optString("issuer_name", "").takeIf { it.isNotBlank() }?.let { issuers.add(it.take(60)) }
            }
            if (domains.isEmpty()) return ScrapeOut(false, false)
            val fields = mapOf(
                "title" to "crt.sh: ${arr.length()} certificates",
                "snippet" to "Subdomains: ${domains.take(20).joinToString(", ")}",
                "subdomains" to domains.filter { it.startsWith("*.").not() }.take(30).joinToString("\n"),
                "wildcard_domains" to domains.filter { it.startsWith("*") }.take(10).joinToString("\n"),
                "issuers" to issuers.take(5).joinToString(", ")
            )
            ScrapeOut(true, false, fields)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun searchDarkWeb(query: String): ScrapeOut {
        return try {
            val client = torHttpClientOrNull() ?: fastHttpClient
            val encoded = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder().url("https://darksearch.io/api/search?query=$encoded&page=1")
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || body == "{}") return ScrapeOut(false, false)
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return ScrapeOut(false, false)
            if (data.length() == 0) return ScrapeOut(false, false)
            val results = mutableListOf<String>()
            val links = mutableListOf<String>()
            for (i in 0 until minOf(10, data.length())) {
                val item = data.optJSONObject(i) ?: continue
                val title = item.optString("title", "")
                val link = item.optString("link", "")
                val desc = item.optString("description", "").take(200)
                if (title.isNotBlank()) results.add("$title — $desc".trim())
                if (link.isNotBlank()) links.add(link)
            }
            if (results.isEmpty()) return ScrapeOut(false, false)
            ScrapeOut(true, false, mapOf(
                "title" to "DarkSearch: ${data.length()} results",
                "snippet" to results.take(5).joinToString("\n").take(600),
                "dark_links" to links.take(10).joinToString("\n"),
                "source_type" to "dark_web"
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeGoogleNews(query: String): ScrapeOut {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://news.google.com/rss/search?q=$encoded&hl=en-US&gl=US&ceid=US:en"
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank()) return ScrapeOut(false, false)
            val doc = Jsoup.parse(body, "", org.jsoup.parser.Parser.xmlParser())
            val items = doc.select("item").take(10)
            if (items.isEmpty()) return ScrapeOut(false, false)
            val articles = items.mapNotNull { item ->
                val title = item.selectFirst("title")?.text()?.trim() ?: return@mapNotNull null
                val pubDate = item.selectFirst("pubDate")?.text()?.trim() ?: ""
                val source = item.selectFirst("source")?.text()?.trim() ?: ""
                val link = item.selectFirst("link")?.text()?.trim() ?: ""
                buildString {
                    append(title)
                    if (source.isNotBlank()) append(" [$source]")
                    if (pubDate.isNotBlank()) append(" ($pubDate)")
                }.trim()
            }
            if (articles.isEmpty()) return ScrapeOut(false, false)
            ScrapeOut(true, false, mapOf(
                "title" to "Google News: ${articles.size} articles",
                "snippet" to articles.joinToString("\n").take(800),
                "news_count" to articles.size.toString()
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeGitHub(username: String): ScrapeOut {
        return try {
            val req = Request.Builder().url("https://api.github.com/users/${URLEncoder.encode(username, "UTF-8")}")
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || resp.code == 404) return ScrapeOut(false, false)
            val json = JSONObject(body)
            if (json.optString("message") == "Not Found") return ScrapeOut(false, false)
            val fields = mutableMapOf<String, String>()
            fields["title"] = "GitHub: ${json.optString("login", username)}"
            val name = json.optString("name", "")
            val bio = json.optString("bio", "")
            val location = json.optString("location", "")
            val blog = json.optString("blog", "")
            val company = json.optString("company", "")
            val followers = json.optInt("followers", 0)
            val following = json.optInt("following", 0)
            val repos = json.optInt("public_repos", 0)
            val avatarUrl = json.optString("avatar_url", "")
            val profileUrl = json.optString("html_url", "")
            val email = json.optString("email", "")
            if (name.isNotBlank()) fields["name"] = name
            if (bio.isNotBlank()) fields["snippet"] = bio.take(300)
            if (location.isNotBlank()) fields["location"] = location
            if (blog.isNotBlank()) fields["website"] = blog
            if (company.isNotBlank()) fields["company"] = company
            fields["stats"] = "Followers: $followers  Following: $following  Repos: $repos"
            if (avatarUrl.isNotBlank()) fields["image_url"] = avatarUrl
            if (profileUrl.isNotBlank()) fields["profile_url"] = profileUrl
            if (email.isNotBlank()) fields["email"] = email
            ScrapeOut(true, false, fields)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeReddit(username: String): ScrapeOut {
        return try {
            val req = Request.Builder().url("https://www.reddit.com/user/${URLEncoder.encode(username, "UTF-8")}/about.json")
                .header("User-Agent", "Mozilla/5.0 (compatible; OSINT/1.0)")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || resp.code == 404) return ScrapeOut(false, false)
            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: return ScrapeOut(false, false)
            val fields = mutableMapOf<String, String>()
            val name = data.optString("name", "")
            val karma = data.optInt("link_karma", 0) + data.optInt("comment_karma", 0)
            val created = data.optLong("created_utc", 0L)
            val iconImg = data.optString("icon_img", "").substringBefore("?")
            val snoovatarImg = data.optString("snoovatar_img", "").substringBefore("?")
            val verified = data.optBoolean("verified", false)
            val subreddit = data.optJSONObject("subreddit")
            val description = subreddit?.optString("public_description", "") ?: ""
            fields["title"] = "Reddit: u/$name"
            fields["snippet"] = buildString {
                append("Reddit user u/$name  |  Karma: $karma")
                if (verified) append("  |  Email Verified")
                if (description.isNotBlank()) append("\n$description")
            }.take(400)
            val avatarUrl = snoovatarImg.takeIf { it.startsWith("http") } ?: iconImg.takeIf { it.startsWith("http") }
            if (avatarUrl != null) fields["image_url"] = avatarUrl
            fields["profile_url"] = "https://www.reddit.com/user/$name"
            if (created > 0) fields["created_utc"] = created.toString()
            ScrapeOut(true, false, fields)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeProxyNova(email: String): ScrapeOut {
        return try {
            val encoded = URLEncoder.encode(email, "UTF-8")
            val url = "https://www.proxynova.com/tools/comb-database-search/?q=$encoded"
            tryScrapeUrl(url)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeHackerTarget(query: String, type: String): ScrapeOut {
        return try {
            val url = when (type) {
                "email" -> "https://api.hackertarget.com/findemail/?q=${URLEncoder.encode(query, "UTF-8")}"
                else -> "https://api.hackertarget.com/hostsearch/?q=${URLEncoder.encode(query, "UTF-8")}"
            }
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || body.startsWith("error") || body.length < 10) return ScrapeOut(false, false)
            val lines = body.lines().filter { it.isNotBlank() }
            ScrapeOut(true, false, mapOf("snippet" to lines.take(20).joinToString("\n").take(600), "title" to "HackerTarget"))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeAhmia(query: String): ScrapeOut {
        return try {
            val client = torHttpClientOrNull() ?: fastHttpClient
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://ahmia.fi/search/?q=$encoded"
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            val doc = Jsoup.parse(body)
            val results = doc.select(".result").take(5).map { it.text() }
            if (results.isEmpty()) return ScrapeOut(false, false)
            ScrapeOut(true, false, mapOf("snippet" to results.joinToString(" | ").take(600), "title" to "Ahmia Dark Web"))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeWaybackCdx(domain: String): ScrapeOut {
        return try {
            val encoded = URLEncoder.encode(domain, "UTF-8")
            val url = "http://web.archive.org/cdx/search/cdx?url=$encoded/*&output=text&limit=20&fl=original,timestamp&collapse=urlkey"
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || body.length < 10) return ScrapeOut(false, false)
            val lines = body.lines().filter { it.isNotBlank() }
            ScrapeOut(true, false, mapOf("snippet" to lines.take(20).joinToString("\n").take(600), "title" to "Wayback Machine CDX"))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrape800Notes(phone: String): ScrapeOut {
        return try {
            val digits = phone.replace(Regex("[^0-9]"), "")
            if (digits.length < 10) return ScrapeOut(false, false)
            val formatted = "${digits.substring(0,3)}-${digits.substring(3,6)}-${digits.substring(6,10)}"
            val url = "https://800notes.com/Phone.aspx/$formatted"
            tryScrapeUrl(url)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeLeakCheck(query: String): ScrapeOut {
        return try {
            val encoded = URLEncoder.encode(query.trim().lowercase(), "UTF-8")
            val req = Request.Builder()
                .url("https://leakcheck.io/api/public?check=$encoded")
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            val found = json.optBoolean("found", false)
            val sources = json.optJSONArray("sources")
            if (!found || sources == null || sources.length() == 0) return ScrapeOut(false, false)
            val sourceList = (0 until sources.length()).mapNotNull { sources.optJSONObject(it)?.optString("name") }.joinToString(", ")
            ScrapeOut(true, false, mapOf(
                "title" to "LeakCheck: ${sources.length()} breach(es)",
                "snippet" to "Found in: $sourceList",
                "breach_sources" to sourceList,
                "breach_count" to sources.length().toString()
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeEmailRep(email: String): ScrapeOut {
        return try {
            val encoded = URLEncoder.encode(email.trim().lowercase(), "UTF-8")
            val req = Request.Builder()
                .url("https://emailrep.io/$encoded")
                .header("User-Agent", "6Degrees OSINT/1.0")
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            val reputation = json.optString("reputation", "")
            val suspicious = json.optBoolean("suspicious", false)
            val refs = json.optInt("references", 0)
            if (refs == 0 && reputation.isBlank()) return ScrapeOut(false, false)
            val details = json.optJSONObject("details")
            val fields = mutableMapOf<String, String>()
            fields["title"] = "EmailRep: $email"
            fields["snippet"] = "Reputation: $reputation | Suspicious: $suspicious | References: $refs"
            if (details != null) {
                details.optString("days_since_domain_creation", "").takeIf { it.isNotBlank() }
                    ?.let { fields["domain_age_days"] = it }
                details.optBoolean("spam", false).let { if (it) fields["spam_flag"] = "true" }
                details.optJSONArray("profiles")?.let { arr ->
                    val profiles = (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                    if (profiles.isNotEmpty()) fields["linked_profiles"] = profiles.joinToString(", ")
                }
                details.optString("first_seen", "").takeIf { it.isNotBlank() }?.let { fields["first_seen"] = it }
                details.optString("last_seen", "").takeIf { it.isNotBlank() }?.let { fields["last_seen"] = it }
            }
            ScrapeOut(true, false, fields)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeGravatar(email: String): ScrapeOut {
        return try {
            val hash = java.security.MessageDigest.getInstance("MD5")
                .digest(email.trim().lowercase().toByteArray())
                .joinToString("") { "%02x".format(it) }
            val req = Request.Builder()
                .url("https://www.gravatar.com/$hash.json")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val code = resp.code
            resp.close()
            if (code == 404 || body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            val entry = json.optJSONArray("entry")?.optJSONObject(0) ?: return ScrapeOut(false, false)
            val fields = mutableMapOf<String, String>()
            val displayName = entry.optString("displayName", "")
            val profileUrl = entry.optString("profileUrl", "")
            val thumbnailUrl = entry.optString("thumbnailUrl", "")
            val aboutMe = entry.optString("aboutMe", "")
            val name = entry.optJSONObject("name")
            val formattedName = name?.optString("formatted", "") ?: displayName
            if (formattedName.isNotBlank()) fields["name"] = formattedName
            if (displayName.isNotBlank()) fields["title"] = "Gravatar: $displayName"
            else fields["title"] = "Gravatar profile found"
            if (profileUrl.isNotBlank()) fields["profile_url"] = profileUrl
            if (thumbnailUrl.isNotBlank()) {
                fields["image_url"] = thumbnailUrl
                fields["snippet"] = "Gravatar profile with photo: $formattedName"
            } else {
                fields["snippet"] = "Gravatar profile: $formattedName"
            }
            if (aboutMe.isNotBlank()) fields["about"] = aboutMe.take(200)
            entry.optJSONArray("accounts")?.let { accounts ->
                val linked = (0 until accounts.length()).mapNotNull { i ->
                    val acc = accounts.optJSONObject(i) ?: return@mapNotNull null
                    "${acc.optString("shortname", "")} (${acc.optString("display", "")})"
                        .takeIf { it.length > 3 }
                }.joinToString(", ")
                if (linked.isNotBlank()) fields["linked_accounts"] = linked
            }
            ScrapeOut(true, false, fields)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeCourtListener(query: String): ScrapeOut {
        return try {
            val encoded = URLEncoder.encode("\"${query.trim()}\"", "UTF-8")
            val req = Request.Builder()
                .url("https://www.courtlistener.com/api/rest/v4/search/?q=$encoded&type=r&order_by=score+desc&page_size=5")
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            val count = json.optInt("count", 0)
            if (count == 0) return ScrapeOut(false, false)
            val results = json.optJSONArray("results") ?: return ScrapeOut(false, false)
            val cases = mutableListOf<String>()
            val urls = mutableListOf<String>()
            for (i in 0 until minOf(5, results.length())) {
                val r = results.optJSONObject(i) ?: continue
                val caseName = r.optString("caseName", "")
                val court = r.optString("court_citation_string", r.optString("court", ""))
                val date = r.optString("dateFiled", "")
                val absoluteUrl = r.optString("absolute_url", "")
                if (caseName.isNotBlank()) {
                    cases.add("$caseName${if (court.isNotBlank()) " ($court)" else ""}${if (date.isNotBlank()) " $date" else ""}")
                }
                if (absoluteUrl.isNotBlank()) urls.add("https://www.courtlistener.com$absoluteUrl")
            }
            if (cases.isEmpty()) return ScrapeOut(false, false)
            ScrapeOut(true, false, mapOf(
                "title" to "CourtListener: $count case(s) found",
                "snippet" to cases.joinToString("\n").take(500),
                "case_urls" to urls.joinToString("\n"),
                "case_count" to count.toString()
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeGleif(name: String): ScrapeOut {
        return try {
            val encoded = URLEncoder.encode(name.trim(), "UTF-8")
            val req = Request.Builder()
                .url("https://api.gleif.org/api/v1/fuzzycompletions?q=$encoded&field=entity.legalName")
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return ScrapeOut(false, false)
            if (data.length() == 0) return ScrapeOut(false, false)
            val entities = mutableListOf<String>()
            for (i in 0 until minOf(5, data.length())) {
                val item = data.optJSONObject(i) ?: continue
                val attrs = item.optJSONObject("attributes") ?: continue
                val legalName = attrs.optString("value", "")
                if (legalName.isNotBlank()) entities.add(legalName)
            }
            if (entities.isEmpty()) return ScrapeOut(false, false)
            ScrapeOut(true, false, mapOf(
                "title" to "GLEIF: ${entities.size} legal entit(ies)",
                "snippet" to entities.joinToString("\n"),
                "legal_entities" to entities.joinToString("\n")
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeIpApi(ip: String): ScrapeOut {
        return try {
            val req = Request.Builder()
                .url("http://ip-api.com/json/${URLEncoder.encode(ip.trim(), "UTF-8")}?fields=status,country,regionName,city,zip,lat,lon,isp,org,as,query,proxy,hosting")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            if (json.optString("status") != "success") return ScrapeOut(false, false)
            val city = json.optString("city", "")
            val region = json.optString("regionName", "")
            val country = json.optString("country", "")
            val isp = json.optString("isp", "")
            val org = json.optString("org", "")
            val asn = json.optString("as", "")
            val proxy = json.optBoolean("proxy", false)
            val hosting = json.optBoolean("hosting", false)
            val lat = json.optDouble("lat", 0.0)
            val lon = json.optDouble("lon", 0.0)
            val fields = mutableMapOf<String, String>()
            fields["title"] = "ip-api: $ip"
            fields["location"] = listOf(city, region, country).filter { it.isNotBlank() }.joinToString(", ")
            fields["isp"] = isp
            if (org.isNotBlank()) fields["org"] = org
            if (asn.isNotBlank()) fields["asn"] = asn
            if (lat != 0.0) fields["coords"] = "$lat, $lon"
            fields["snippet"] = "${fields["location"]} | ISP: $isp${if (proxy) " | PROXY" else ""}${if (hosting) " | HOSTING" else ""}"
            if (proxy) fields["proxy"] = "true"
            if (hosting) fields["hosting"] = "true"
            ScrapeOut(true, false, fields)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeBgpView(ip: String): ScrapeOut {
        return try {
            val req = Request.Builder()
                .url("https://api.bgpview.io/ip/${URLEncoder.encode(ip.trim(), "UTF-8")}")
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            if (json.optString("status") != "ok") return ScrapeOut(false, false)
            val data = json.optJSONObject("data") ?: return ScrapeOut(false, false)
            val prefixes = data.optJSONArray("prefixes") ?: return ScrapeOut(false, false)
            if (prefixes.length() == 0) return ScrapeOut(false, false)
            val prefix = prefixes.optJSONObject(0) ?: return ScrapeOut(false, false)
            val asn = prefix.optJSONObject("asn")
            val asnNum = asn?.optInt("asn", 0) ?: 0
            val asnName = asn?.optString("name", "") ?: ""
            val asnDesc = asn?.optString("description", "") ?: ""
            val cidr = prefix.optString("prefix", "")
            val country = prefix.optString("country_code", "")
            ScrapeOut(true, false, mapOf(
                "title" to "BGPView: AS$asnNum $asnName",
                "snippet" to "ASN: AS$asnNum | Prefix: $cidr | Country: $country | Org: $asnDesc",
                "asn" to "AS$asnNum",
                "asn_name" to asnName,
                "prefix" to cidr,
                "country" to country
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeShodanInternetDB(ip: String): ScrapeOut {
        return try {
            val req = Request.Builder()
                .url("https://internetdb.shodan.io/${URLEncoder.encode(ip.trim(), "UTF-8")}")
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val code = resp.code
            resp.close()
            if (code == 404 || body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            if (json.has("detail")) return ScrapeOut(false, false)
            val ports = json.optJSONArray("ports")
            val cves = json.optJSONArray("cpes")
            val hostnames = json.optJSONArray("hostnames")
            val tags = json.optJSONArray("tags")
            val fields = mutableMapOf<String, String>()
            fields["title"] = "Shodan InternetDB: $ip"
            if (ports != null && ports.length() > 0) {
                val portList = (0 until ports.length()).map { ports.optInt(it) }.joinToString(", ")
                fields["open_ports"] = portList
            }
            if (cves != null && cves.length() > 0) {
                val cveList = (0 until cves.length()).map { cves.optString(it) }.joinToString(", ")
                fields["cves"] = cveList
            }
            if (hostnames != null && hostnames.length() > 0) {
                val hnList = (0 until hostnames.length()).map { hostnames.optString(it) }.joinToString(", ")
                fields["hostnames"] = hnList
            }
            if (tags != null && tags.length() > 0) {
                val tagList = (0 until tags.length()).map { tags.optString(it) }.joinToString(", ")
                fields["tags"] = tagList
            }
            fields["snippet"] = buildString {
                fields["open_ports"]?.let { append("Ports: $it ") }
                fields["hostnames"]?.let { append("| Hosts: $it ") }
                fields["cves"]?.let { append("| CVEs: $it") }
            }.trim().ifBlank { "No open ports or vulns" }
            ScrapeOut(true, false, fields)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeRdap(domain: String): ScrapeOut {
        return try {
            val req = Request.Builder()
                .url("https://rdap.org/domain/${URLEncoder.encode(domain.trim().lowercase(), "UTF-8")}")
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            val fields = mutableMapOf<String, String>()
            val domainName = json.optString("ldhName", domain)
            fields["title"] = "RDAP: $domainName"
            json.optJSONArray("events")?.let { events ->
                for (i in 0 until events.length()) {
                    val ev = events.optJSONObject(i) ?: continue
                    val action = ev.optString("eventAction", "")
                    val date = ev.optString("eventDate", "").take(10)
                    when (action) {
                        "registration" -> fields["registered"] = date
                        "expiration" -> fields["expires"] = date
                        "last changed" -> fields["updated"] = date
                    }
                }
            }
            json.optJSONArray("nameservers")?.let { ns ->
                val nameservers = (0 until ns.length()).mapNotNull { ns.optJSONObject(it)?.optString("ldhName") }.joinToString(", ")
                if (nameservers.isNotBlank()) fields["nameservers"] = nameservers
            }
            json.optJSONArray("entities")?.let { entities ->
                for (i in 0 until entities.length()) {
                    val entity = entities.optJSONObject(i) ?: continue
                    val roles = entity.optJSONArray("roles")
                    val role = (0 until (roles?.length() ?: 0)).map { roles?.optString(it) }.joinToString(",")
                    if (role.contains("registrant") || role.contains("registrar")) {
                        entity.optJSONArray("vcardArray")?.optJSONArray(1)?.let { vcard ->
                            for (j in 0 until vcard.length()) {
                                val entry = vcard.optJSONArray(j) ?: continue
                                if (entry.optString(0) == "fn") fields["registrant"] = entry.optString(3, "")
                                if (entry.optString(0) == "org") fields["registrant_org"] = entry.optString(3, "")
                            }
                        }
                    }
                }
            }
            fields["snippet"] = buildString {
                fields["registrant"]?.takeIf { it.isNotBlank() }?.let { append("Registrant: $it | ") }
                fields["registered"]?.let { append("Reg: $it | ") }
                fields["expires"]?.let { append("Exp: $it | ") }
                fields["nameservers"]?.let { append("NS: $it") }
            }.trimEnd(' ', '|').ifBlank { "RDAP data for $domainName" }
            ScrapeOut(true, false, fields)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun extractOgImage(url: String): String? {
        return try {
            val fullUrl = if (!url.startsWith("http")) "https://$url" else url
            val req = Request.Builder().url(fullUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            val client = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank()) return null
            val doc = Jsoup.parse(body)
            (doc.selectFirst("meta[property=og:image]")?.attr("content")
                ?: doc.selectFirst("meta[name=twitter:image]")?.attr("content")
                ?: doc.selectFirst("meta[name=twitter:image:src]")?.attr("content"))
                ?.takeIf { it.startsWith("http") && !it.contains("placeholder") && !it.contains("default") }
        } catch (_: Exception) { null }
    }

    private fun fetchAvatarUrl(username: String, platform: String? = null): String? {
        return try {
            val url = if (platform != null) "https://unavatar.io/$platform/$username" else "https://unavatar.io/$username"
            val req = Request.Builder().url(url).head()
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val ok = resp.code == 200 || resp.code == 302
            resp.close()
            if (ok) url else null
        } catch (_: Exception) { null }
    }

    private fun deduplicatePersonRecords(records: List<PersonRecord>): List<PersonRecord> {
        val seen = mutableListOf<PersonRecord>()
        for (rec in records) {
            val normName = rec.name.trim().lowercase().replace(Regex("\\s+"), " ")
            val existing = seen.indexOfFirst { it.name.trim().lowercase().replace(Regex("\\s+"), " ") == normName }
            if (existing == -1) {
                seen.add(rec)
            } else {
                val e = seen[existing]
                seen[existing] = e.copy(
                    age = e.age.ifBlank { rec.age },
                    location = e.location.ifBlank { rec.location },
                    phones = (e.phones + rec.phones).distinct().take(6),
                    relatives = (e.relatives + rec.relatives).distinct().take(10),
                    photoUrl = e.photoUrl ?: rec.photoUrl,
                    profileUrl = e.profileUrl ?: rec.profileUrl
                )
            }
        }
        return seen
    }

    private fun buildAiSummaryPrompt(query: String, type: String, metadata: Map<String, String>): String = buildString {
        appendLine("Write a concise OSINT intelligence brief (max 350 words) for the subject below. Be factual and analytical. Highlight key findings, risks, and unknowns.")
        appendLine()
        appendLine("SUBJECT: $query")
        appendLine("SEARCH TYPE: $type")
        appendLine()
        metadata["person_name"]?.let { appendLine("Name: $it") }
        metadata["field_age"]?.takeIf { it.isNotBlank() }?.let { appendLine("Age: $it") }
        metadata["person_location"]?.takeIf { it.isNotBlank() }?.let { appendLine("Location: $it") }
        listOfNotNull(metadata["search_phones"], metadata["pipl_phones"], metadata["person_phone"])
            .firstOrNull { it.isNotBlank() }?.let { appendLine("Phone(s): $it") }
        listOfNotNull(metadata["search_relatives"])
            .firstOrNull { it.isNotBlank() }?.let { appendLine("Associates: $it") }
        metadata["search_addresses"]?.takeIf { it.isNotBlank() }?.let { appendLine("Addresses: ${it.take(200)}") }
        metadata["search_social_links"]?.takeIf { it.isNotBlank() }?.let { appendLine("Social: ${it.take(200)}") }
        metadata["github_name"]?.takeIf { it.isNotBlank() }?.let { appendLine("GitHub Name: $it") }
        metadata["github_stats"]?.takeIf { it.isNotBlank() }?.let { appendLine("GitHub: $it") }
        metadata["reddit_url"]?.takeIf { it.isNotBlank() }?.let { appendLine("Reddit: $it") }
        metadata["wikipedia_extract"]?.takeIf { it.isNotBlank() }?.let { appendLine("\nWikipedia:\n${it.take(500)}") }
        metadata["ddg_abstract"]?.takeIf { it.isNotBlank() }?.let { appendLine("\nWeb Summary:\n${it.take(400)}") }
        metadata.entries.firstOrNull { it.key.contains("news") && it.value.isNotBlank() }
            ?.value?.let { appendLine("\nNews:\n${it.take(400)}") }
        metadata["darksearch_links"]?.takeIf { it.isNotBlank() }?.let { appendLine("\nDark Web Mentions:\n${it.take(200)}") }
        metadata["search_snippets"]?.takeIf { it.isNotBlank() }?.let { appendLine("\nWeb Intelligence:\n${it.take(600)}") }
        appendLine("\nINTELLIGENCE BRIEF:")
    }

    private fun queryPollinationsAI(prompt: String): String? = try {
        val body = JSONObject().apply {
            put("model", "openai-large")
            put("private", true)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are an OSINT intelligence analyst. Write concise, factual intelligence briefs from collected data. Be objective and analytical.")
                })
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
            put("max_tokens", 600)
        }.toString()
        val reqBody = body.toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url("https://text.pollinations.ai/openai")
            .post(reqBody)
            .header("User-Agent", "Mozilla/5.0")
            .build()
        val aiClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
        val resp = aiClient.newCall(req).execute()
        val respBody = resp.body?.string() ?: ""
        resp.close()
        if (respBody.isBlank()) null
        else JSONObject(respBody).optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content")?.trim()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun saveReport(
        query: String,
        personId: String?,
        sources: List<DataSource>,
        metadata: Map<String, String>
    ): String {
        val reportId = UUID.randomUUID().toString()
        val sourcesType = Types.newParameterizedType(List::class.java, DataSource::class.java)
        val sourcesAdapter = moshi.adapter<List<DataSource>>(sourcesType)
        val metaType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
        val metaAdapter = moshi.adapter<Map<String, String>>(metaType)

        val entity = OsintReportEntity(
            id = reportId,
            searchQuery = query,
            generatedAt = Date(),
            personId = personId,
            companiesJson = metaAdapter.toJson(metadata),
            propertiesJson = "[]",
            vehicleRecordsJson = "[]",
            financialSummaryJson = null,
            confidenceScore = if (sources.isNotEmpty()) minOf(1.0, sources.size * 0.1) else 0.0,
            sourcesJson = sourcesAdapter.toJson(sources)
        )
        db.reportDao().insertReport(entity)
        return reportId
    }

    suspend fun search(query: String, type: String): String {
        var reportId = ""
        searchWithProgress(query, type).collect { event ->
            if (event is SearchProgressEvent.Complete) reportId = event.reportId
        }
        return reportId
    }

    suspend fun getReportById(reportId: String): OsintReportEntity? =
        db.reportDao().getReportById(reportId)

    suspend fun getPersonById(personId: String): PersonEntity? =
        db.personDao().getPersonById(personId)

    suspend fun getRecentReports(limit: Int): List<OsintReportEntity> =
        db.reportDao().getRecentReports(limit)

    fun searchWithProgress(query: String, type: String, round: Int = 1): Flow<SearchProgressEvent> = channelFlow {
        withContext(Dispatchers.IO) {
            val fields = parseFields(query)
            val primaryQuery = fields["name"] ?: fields["email"] ?: fields["phone"]
                ?: fields["username"] ?: fields["domain"] ?: fields["ip"] ?: query.trim()

            val metadata = ConcurrentHashMap<String, String>()
            val effectiveType = if (type == "scan") "person" else type
            metadata["search_type"] = effectiveType
            fields.forEach { (k, v) -> metadata["field_$k"] = v }
            val city = fields["city"] ?: ""
            val state = fields["state"] ?: ""
            val locationStr = listOf(city, state).filter { it.isNotBlank() }.joinToString(", ")
            if (city.isNotBlank()) metadata["person_city"] = city
            if (state.isNotBlank()) metadata["person_state"] = state
            if (locationStr.isNotBlank()) metadata["person_location"] = locationStr
            fields["name"]?.takeIf { it.isNotBlank() }?.let { metadata["person_name"] = it }
            fields["dob"]?.takeIf { it.isNotBlank() }?.let { metadata["comp_dob"] = it }
            fields["address"]?.takeIf { it.isNotBlank() }?.let { metadata["person_entered_address"] = it }
            fields["phone"]?.takeIf { it.isNotBlank() }?.let { metadata["person_phone"] = it }
            fields["email"]?.takeIf { it.isNotBlank() }?.let { metadata["person_email"] = it }
            fields["username"]?.takeIf { it.isNotBlank() }?.let { uname ->
                val avatarUrl = fetchAvatarUrl(uname)
                if (avatarUrl != null) metadata["profile_photo_url"] = avatarUrl
            }

            val sources = Collections.synchronizedList(mutableListOf<DataSource>())
            val scrapedPersonRecords = Collections.synchronizedList(mutableListOf<PersonRecord>())
            val semaphore = Semaphore(5)
            val ddgPhones = Collections.synchronizedList(mutableListOf<String>())
            val ddgEmails = Collections.synchronizedList(mutableListOf<String>())
            val ddgRelatives = Collections.synchronizedList(mutableListOf<String>())
            val ddgAges = Collections.synchronizedList(mutableListOf<String>())
            val ddgAddresses = Collections.synchronizedList(mutableListOf<String>())
            val ddgSocial = Collections.synchronizedList(mutableListOf<String>())
            val ddgProfiles = Collections.synchronizedList(mutableListOf<String>())
            val ddgSnippets = Collections.synchronizedList(mutableListOf<String>())

            send(SearchProgressEvent.Checking("DuckDuckGo"))
            val ddgData = duckDuckGoSearch(primaryQuery)
            if (ddgData.isNotEmpty() && (ddgData["ddg_abstract"]?.isNotBlank() == true || ddgData["ddg_web_snippets"]?.isNotBlank() == true)) {
                ddgData.forEach { (k, v) -> metadata[k] = v }
                sources.add(DataSource("DuckDuckGo", "https://api.duckduckgo.com/?q=${encode(primaryQuery)}&format=json", Date(), 0.7))
                send(SearchProgressEvent.Found("DuckDuckGo", (ddgData["ddg_abstract"] ?: ddgData["ddg_web_snippets"] ?: "").take(120)))
            } else {
                send(SearchProgressEvent.NotFound("DuckDuckGo"))
            }

            val targetedScraperNames = mutableSetOf<String>()

            coroutineScope {
                when (effectiveType) {
                    "person", "comprehensive" -> {
                        targetedScraperNames += setOf("Wikipedia", "Google News", "DarkSearch", "Pipl", "CourtListener", "GLEIF")
                        launch { termuxRunner.ensureTorRunning().collect { send(it) } }
                        val personPhone = fields["phone"] ?: ""
                        val personEmail = fields["email"] ?: ""
                        val personUsername = fields["username"] ?: ""
                        val personQueries = buildPersonQueries(primaryQuery, city, state, personPhone, personEmail, personUsername)
                        for ((label, q) in personQueries) {
                            launch {
                                semaphore.withPermit {
                                    send(SearchProgressEvent.Checking("DDG: $label"))
                                    val results = ddgHtmlSearch(q)
                                    val extracted = extractDataFromDdgResults(results)
                                    if (results.isNotEmpty()) {
                                        sources.add(DataSource("DDG:$label", "https://html.duckduckgo.com/html/?q=${encode(q)}", Date(), 0.6))
                                        send(SearchProgressEvent.Found("DDG: $label", (extracted.snippets.firstOrNull() ?: results.firstOrNull()?.snippet ?: "").take(120)))
                                    } else {
                                        send(SearchProgressEvent.NotFound("DDG: $label"))
                                    }
                                    ddgPhones.addAll(extracted.phones)
                                    ddgEmails.addAll(extracted.emails)
                                    ddgRelatives.addAll(extracted.relatives)
                                    ddgAges.addAll(extracted.ages)
                                    ddgAddresses.addAll(extracted.addresses)
                                    ddgSocial.addAll(extracted.socialUrls)
                                    ddgProfiles.addAll(extracted.profileUrls)
                                    ddgSnippets.addAll(extracted.snippets)
                                }
                            }
                        }
                        launch {
                            send(SearchProgressEvent.Checking("Wikipedia"))
                            val wikiData = searchWikipedia(primaryQuery)
                            if (wikiData.isNotEmpty()) {
                                wikiData.forEach { (k, v) -> metadata[k] = v }
                                sources.add(DataSource("Wikipedia", "https://en.wikipedia.org/wiki/${encode(primaryQuery)}", Date(), 0.8))
                                send(SearchProgressEvent.Found("Wikipedia", wikiData["wikipedia_extract"]?.take(120) ?: ""))
                            } else {
                                send(SearchProgressEvent.NotFound("Wikipedia"))
                            }
                        }
                        launch {
                            send(SearchProgressEvent.Checking("Google News"))
                            val newsQuery = if (city.isNotBlank()) "$primaryQuery $city" else primaryQuery
                            val out = scrapeGoogleNews(newsQuery)
                            handleScrapeOut("Google News", "https://news.google.com/rss/search?q=${encode(newsQuery)}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("DarkSearch"))
                            val out = searchDarkWeb(primaryQuery)
                            if (out.found) out.fields["dark_links"]?.let { metadata["darksearch_links"] = it }
                            handleScrapeOut("DarkSearch", "https://darksearch.io/api/search?query=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("CourtListener"))
                            val out = scrapeCourtListener(primaryQuery)
                            if (out.found) {
                                out.fields["case_count"]?.let { metadata["court_case_count"] = it }
                                out.fields["snippet"]?.let { metadata["court_cases"] = it }
                                out.fields["case_urls"]?.let { metadata["court_case_urls"] = it }
                            }
                            handleScrapeOut("CourtListener", "https://www.courtlistener.com/?q=${encode(primaryQuery)}&type=r", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("GLEIF"))
                            val out = scrapeGleif(primaryQuery)
                            if (out.found) {
                                out.fields["legal_entities"]?.let { metadata["gleif_entities"] = it }
                            }
                            handleScrapeOut("GLEIF", "https://search.gleif.org/#/record/${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            semaphore.withPermit {
                                val key = apiKeys.getKey("pipl")
                                if (key.isNullOrBlank()) {
                                    send(SearchProgressEvent.NotFound("Pipl (no key)"))
                                } else {
                                    send(SearchProgressEvent.Checking("Pipl"))
                                    try {
                                        val nameParts = primaryQuery.trim().split("\\s+".toRegex())
                                        val first = nameParts.firstOrNull()
                                        val last = if (nameParts.size > 1) nameParts.last() else null
                                        val result = RetrofitClient.piplService.search(apiKey = key, firstName = first, lastName = last)
                                        val person = result.person
                                        if (person == null) {
                                            send(SearchProgressEvent.NotFound("Pipl"))
                                        } else {
                                            metadata["pipl_found"] = "true"
                                            val displayName = person.names?.firstOrNull()?.display
                                            if (!displayName.isNullOrBlank()) metadata["pipl_name"] = displayName
                                            val piplEmails = person.emails?.mapNotNull { it.address }
                                            if (!piplEmails.isNullOrEmpty()) metadata["pipl_emails"] = piplEmails.take(3).joinToString(", ")
                                            val piplPhones = person.phones?.mapNotNull { it.display ?: it.number }
                                            if (!piplPhones.isNullOrEmpty()) metadata["pipl_phones"] = piplPhones.take(3).joinToString(", ")
                                            sources.add(DataSource("Pipl", "https://pipl.com/search/?q=${encode(primaryQuery)}", Date(), 0.9))
                                            send(SearchProgressEvent.Found("Pipl", displayName ?: "Person profile found"))
                                        }
                                    } catch (_: Exception) {
                                        send(SearchProgressEvent.Blocked("Pipl"))
                                    }
                                }
                            }
                        }
                    }
                    "email", "breach" -> {
                        targetedScraperNames += setOf("ProxyNova", "HackerTarget", "LeakCheck", "EmailRep", "Gravatar")
                        launch {
                            send(SearchProgressEvent.Checking("ProxyNova Breach"))
                            val out = scrapeProxyNova(primaryQuery)
                            handleScrapeOut("ProxyNova Breach", "https://www.proxynova.com/tools/comb-database-search/?q=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("HackerTarget Email"))
                            val out = scrapeHackerTarget(primaryQuery, "email")
                            handleScrapeOut("HackerTarget Email", "https://api.hackertarget.com/findemail/?q=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("LeakCheck"))
                            val out = scrapeLeakCheck(primaryQuery)
                            if (out.found) {
                                out.fields["breach_sources"]?.let { metadata["leakcheck_sources"] = it }
                                out.fields["breach_count"]?.let { metadata["leakcheck_count"] = it }
                            }
                            handleScrapeOut("LeakCheck", "https://leakcheck.io/api/public?check=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("EmailRep"))
                            val out = scrapeEmailRep(primaryQuery)
                            if (out.found) {
                                out.fields["linked_profiles"]?.let { metadata["emailrep_profiles"] = it }
                                out.fields["spam_flag"]?.let { metadata["emailrep_spam"] = it }
                                out.fields["first_seen"]?.let { metadata["emailrep_first_seen"] = it }
                            }
                            handleScrapeOut("EmailRep", "https://emailrep.io/${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("Gravatar"))
                            val out = scrapeGravatar(primaryQuery)
                            if (out.found) {
                                out.fields["image_url"]?.let { if (metadata["profile_photo_url"].isNullOrBlank()) metadata["profile_photo_url"] = it }
                                out.fields["name"]?.let { metadata["gravatar_name"] = it }
                                out.fields["linked_accounts"]?.let { metadata["gravatar_accounts"] = it }
                                out.fields["about"]?.let { metadata["gravatar_about"] = it }
                            }
                            handleScrapeOut("Gravatar", "https://gravatar.com/profile/avatars", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            semaphore.withPermit {
                                val key = apiKeys.getKey("hibp")
                                if (key.isNullOrBlank()) {
                                    send(SearchProgressEvent.NotFound("HaveIBeenPwned (no key)"))
                                } else {
                                    send(SearchProgressEvent.Checking("HaveIBeenPwned"))
                                    try {
                                        val breaches = RetrofitClient.hibpService.getBreaches(primaryQuery, key)
                                        if (breaches.isEmpty()) {
                                            send(SearchProgressEvent.NotFound("HaveIBeenPwned"))
                                        } else {
                                            metadata["hibp_found"] = "true"
                                            metadata["hibp_count"] = breaches.size.toString()
                                            sources.add(DataSource("HaveIBeenPwned", "https://haveibeenpwned.com/account/${encode(primaryQuery)}", java.util.Date(), 0.9))
                                            send(SearchProgressEvent.Found("HaveIBeenPwned", "${breaches.size} breach(es) found"))
                                        }
                                    } catch (_: Exception) {
                                        send(SearchProgressEvent.Blocked("HaveIBeenPwned"))
                                    }
                                }
                            }
                        }
                        launch {
                            semaphore.withPermit {
                                val holeheHits = mutableListOf<String>()
                                termuxRunner.runHolehe(primaryQuery).collect { event ->
                                    send(event)
                                    if (event is SearchProgressEvent.Found) {
                                        holeheHits.add(event.source.removePrefix("holehe/"))
                                        sources.add(DataSource(event.source, event.detail, Date(), 0.7))
                                    }
                                }
                                if (holeheHits.isNotEmpty()) metadata["holehe_services"] = holeheHits.joinToString(", ")
                            }
                        }
                    }
                    "domain", "ip" -> {
                        targetedScraperNames += setOf("HackerTarget Host", "Wayback CDX", "crt.sh", "DarkSearch", "Google News", "ip-api", "BGPView", "Shodan InternetDB", "RDAP")
                        launch { termuxRunner.ensureTorRunning().collect { send(it) } }
                        launch {
                            send(SearchProgressEvent.Checking("HackerTarget Host"))
                            val out = scrapeHackerTarget(primaryQuery, "host")
                            handleScrapeOut("HackerTarget Host", "https://api.hackertarget.com/hostsearch/?q=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("Wayback CDX"))
                            val out = scrapeWaybackCdx(primaryQuery)
                            handleScrapeOut("Wayback CDX", "http://web.archive.org/cdx/search/cdx?url=${encode(primaryQuery)}/*", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            semaphore.withPermit {
                                val key = apiKeys.getKey("hunter")
                                if (key.isNullOrBlank()) {
                                    send(SearchProgressEvent.NotFound("Hunter.io (no key)"))
                                } else {
                                    send(SearchProgressEvent.Checking("Hunter.io"))
                                    try {
                                        val result = RetrofitClient.hunterService.domainSearch(primaryQuery, key)
                                        val emailList = result.data?.emails
                                        val org = result.data?.organization
                                        if (emailList.isNullOrEmpty() && org.isNullOrBlank()) {
                                            send(SearchProgressEvent.NotFound("Hunter.io"))
                                        } else {
                                            metadata["hunter_found"] = "true"
                                            if (!org.isNullOrBlank()) metadata["hunter_org"] = org
                                            if (!emailList.isNullOrEmpty()) {
                                                metadata["hunter_email_count"] = emailList.size.toString()
                                                metadata["hunter_emails"] = emailList.mapNotNull { it.value }.take(5).joinToString(", ")
                                            }
                                            sources.add(DataSource("Hunter.io", "https://hunter.io/domain-search/${encode(primaryQuery)}", java.util.Date(), 0.85))
                                            val detail = buildString {
                                                if (!org.isNullOrBlank()) append(org)
                                                if (!emailList.isNullOrEmpty()) append(" — ${emailList.size} email(s)")
                                            }
                                            send(SearchProgressEvent.Found("Hunter.io", detail))
                                        }
                                    } catch (_: Exception) {
                                        send(SearchProgressEvent.Blocked("Hunter.io"))
                                    }
                                }
                            }
                        }
                        launch {
                            semaphore.withPermit {
                                termuxRunner.runTheHarvester(primaryQuery).collect { send(it) }
                            }
                        }
                        launch {
                            semaphore.withPermit {
                                termuxRunner.runNmap(primaryQuery).collect { send(it) }
                            }
                        }
                        launch {
                            send(SearchProgressEvent.Checking("crt.sh"))
                            val out = scrapeCrtSh(primaryQuery)
                            if (out.found) {
                                out.fields["subdomains"]?.let { metadata["crtsh_subdomains"] = it }
                                out.fields["wildcard_domains"]?.let { metadata["crtsh_wildcards"] = it }
                            }
                            handleScrapeOut("crt.sh", "https://crt.sh/?q=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("DarkSearch"))
                            val out = searchDarkWeb(primaryQuery)
                            if (out.found) out.fields["dark_links"]?.let { metadata["darksearch_links"] = it }
                            handleScrapeOut("DarkSearch", "https://darksearch.io/api/search?query=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("Google News"))
                            val out = scrapeGoogleNews(primaryQuery)
                            handleScrapeOut("Google News", "https://news.google.com/rss/search?q=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("ip-api"))
                            val ipQuery = primaryQuery
                            val out = scrapeIpApi(ipQuery)
                            if (out.found) {
                                out.fields["location"]?.let { metadata["ip_location"] = it }
                                out.fields["isp"]?.let { metadata["ip_isp"] = it }
                                out.fields["asn"]?.let { metadata["ip_asn"] = it }
                                out.fields["coords"]?.let { metadata["ip_coords"] = it }
                                out.fields["proxy"]?.let { metadata["ip_proxy"] = it }
                            }
                            handleScrapeOut("ip-api", "http://ip-api.com/json/$ipQuery", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("BGPView"))
                            val out = scrapeBgpView(primaryQuery)
                            if (out.found) {
                                out.fields["asn"]?.let { metadata["bgp_asn"] = it }
                                out.fields["asn_name"]?.let { metadata["bgp_asn_name"] = it }
                                out.fields["prefix"]?.let { metadata["bgp_prefix"] = it }
                            }
                            handleScrapeOut("BGPView", "https://bgpview.io/ip/$primaryQuery", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("Shodan InternetDB"))
                            val out = scrapeShodanInternetDB(primaryQuery)
                            if (out.found) {
                                out.fields["open_ports"]?.let { metadata["shodan_ports"] = it }
                                out.fields["cves"]?.let { metadata["shodan_cves"] = it }
                                out.fields["hostnames"]?.let { metadata["shodan_hostnames"] = it }
                            }
                            handleScrapeOut("Shodan InternetDB", "https://internetdb.shodan.io/$primaryQuery", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("RDAP"))
                            val out = scrapeRdap(primaryQuery)
                            if (out.found) {
                                out.fields["registrant"]?.let { metadata["rdap_registrant"] = it }
                                out.fields["registrant_org"]?.let { metadata["rdap_org"] = it }
                                out.fields["registered"]?.let { metadata["rdap_registered"] = it }
                                out.fields["expires"]?.let { metadata["rdap_expires"] = it }
                                out.fields["nameservers"]?.let { metadata["rdap_nameservers"] = it }
                            }
                            handleScrapeOut("RDAP", "https://rdap.org/domain/$primaryQuery", out, sources, metadata, this@channelFlow)
                        }
                    }
                    "phone" -> {
                        targetedScraperNames += setOf("800notes", "DDG Phone")
                        launch {
                            send(SearchProgressEvent.Checking("800notes"))
                            val out = scrape800Notes(primaryQuery)
                            val digits = primaryQuery.replace(Regex("[^0-9]"), "")
                            val fmt = if (digits.length >= 10) "${digits.substring(0,3)}-${digits.substring(3,6)}-${digits.substring(6,10)}" else primaryQuery
                            handleScrapeOut("800notes", "https://800notes.com/Phone.aspx/$fmt", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            semaphore.withPermit {
                                send(SearchProgressEvent.Checking("DDG Phone"))
                                val results = ddgHtmlSearch("\"$primaryQuery\" who called owner")
                                val extracted = extractDataFromDdgResults(results)
                                if (results.isNotEmpty()) {
                                    sources.add(DataSource("DDG Phone", "https://html.duckduckgo.com/html/?q=${encode(primaryQuery)}", Date(), 0.6))
                                    metadata["phone_search_snippets"] = results.take(5).joinToString("\n") { "${it.title}: ${it.snippet}".take(150) }
                                    if (extracted.phones.isNotEmpty()) metadata["phone_owner_name"] = extracted.snippets.firstOrNull() ?: ""
                                    send(SearchProgressEvent.Found("DDG Phone", results.firstOrNull()?.snippet?.take(100) ?: ""))
                                } else {
                                    send(SearchProgressEvent.NotFound("DDG Phone"))
                                }
                            }
                        }
                    }
                    "darknet" -> {
                        targetedScraperNames += "Ahmia"
                        launch {
                            send(SearchProgressEvent.Checking("Ahmia Dark Web"))
                            val out = scrapeAhmia(primaryQuery)
                            handleScrapeOut("Ahmia Dark Web", "https://ahmia.fi/search/?q=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
                        }
                    }
                    "username" -> {
                        targetedScraperNames += setOf("GitHub", "Reddit")
                        launch {
                            send(SearchProgressEvent.Checking("GitHub"))
                            val out = scrapeGitHub(primaryQuery)
                            if (out.found) {
                                out.fields["image_url"]?.let { metadata["profile_photo_url"] = it }
                                out.fields["name"]?.let { metadata["github_name"] = it }
                                out.fields["location"]?.let { metadata["github_location"] = it }
                                out.fields["email"]?.let { metadata["github_email"] = it }
                                out.fields["company"]?.let { metadata["github_company"] = it }
                                metadata["github_stats"] = out.fields["stats"] ?: ""
                                metadata["github_url"] = out.fields["profile_url"] ?: ""
                            }
                            handleScrapeOut("GitHub", "https://github.com/$primaryQuery", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("Reddit"))
                            val out = scrapeReddit(primaryQuery)
                            if (out.found) {
                                if (metadata["profile_photo_url"].isNullOrBlank()) out.fields["image_url"]?.let { metadata["profile_photo_url"] = it }
                                metadata["reddit_url"] = out.fields["profile_url"] ?: ""
                            }
                            handleScrapeOut("Reddit", "https://www.reddit.com/user/$primaryQuery", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            semaphore.withPermit {
                                val sherlockHits = mutableListOf<String>()
                                termuxRunner.runSherlock(primaryQuery).collect { event ->
                                    send(event)
                                    if (event is SearchProgressEvent.Found) {
                                        sherlockHits.add("${event.source.removePrefix("sherlock/")}: ${event.detail}")
                                        sources.add(DataSource(event.source, event.detail, Date(), 0.75))
                                    }
                                }
                                if (sherlockHits.isNotEmpty()) metadata["sherlock_found"] = sherlockHits.joinToString("\n")
                            }
                        }
                        launch {
                            semaphore.withPermit {
                                val maigretHits = mutableListOf<String>()
                                termuxRunner.runMaigret(primaryQuery).collect { event ->
                                    send(event)
                                    if (event is SearchProgressEvent.Found) {
                                        maigretHits.add("${event.source.removePrefix("maigret/")}: ${event.detail}")
                                        sources.add(DataSource(event.source, event.detail, Date(), 0.75))
                                    }
                                }
                                if (maigretHits.isNotEmpty()) metadata["maigret_found"] = maigretHits.joinToString("\n")
                            }
                        }
                        launch {
                            val platformsToTry = listOf(
                                "github" to "github",
                                "twitter" to "twitter",
                                "instagram" to "instagram",
                                "tiktok" to "tiktok",
                                null to null
                            )
                            for ((platform, _) in platformsToTry) {
                                val avatarUrl = if (platform != null) fetchAvatarUrl(primaryQuery, platform) else fetchAvatarUrl(primaryQuery)
                                if (avatarUrl != null) {
                                    metadata["profile_photo_url"] = avatarUrl
                                    break
                                }
                            }
                        }
                    }
                }

                val relevantTools = OsintToolRegistry.relevantTools(type)
                val allTools = relevantTools.values.flatten()
                val browserCategories = LinkedHashMap<String, List<Pair<String, String>>>()

                for ((cat, tools) in relevantTools) {
                    browserCategories[cat] = tools.map { tool ->
                        Pair(tool.name, OsintToolRegistry.buildUrl(tool.urlTemplate, primaryQuery))
                    }
                }

                for (tool in allTools) {
                    if (tool.name in targetedScraperNames) continue
                    launch {
                        semaphore.withPermit {
                            val url = OsintToolRegistry.buildUrl(tool.urlTemplate, primaryQuery)
                            send(SearchProgressEvent.Checking(tool.name))
                            val out = tryScrapeUrl(url)
                            handleScrapeOut(tool.name, url, out, sources, metadata, this@channelFlow)
                        }
                    }
                }

                send(SearchProgressEvent.BrowserToolsReady(browserCategories))
            }

            if (effectiveType == "person" || effectiveType == "comprehensive") {
                val distinctPhones = ddgPhones.distinct().take(10)
                val distinctEmails = ddgEmails.distinct().take(5)
                val distinctRelatives = ddgRelatives.distinct().take(15)
                val distinctAges = ddgAges.distinct()
                val distinctAddresses = ddgAddresses.distinct().take(8)
                val distinctSocial = ddgSocial.distinct().take(10)
                val distinctProfiles = ddgProfiles.distinct().take(10)
                if (distinctPhones.isNotEmpty()) metadata["search_phones"] = distinctPhones.joinToString(", ")
                if (distinctEmails.isNotEmpty()) metadata["search_emails"] = distinctEmails.joinToString(", ")
                if (distinctRelatives.isNotEmpty()) metadata["search_relatives"] = distinctRelatives.joinToString(", ")
                if (distinctAges.isNotEmpty()) metadata["search_age"] = distinctAges.first()
                if (distinctAddresses.isNotEmpty()) metadata["search_addresses"] = distinctAddresses.joinToString("\n")
                if (distinctSocial.isNotEmpty()) metadata["search_social_links"] = distinctSocial.joinToString("\n")
                if (distinctProfiles.isNotEmpty()) metadata["search_profile_links"] = distinctProfiles.joinToString("\n")
                if (ddgSnippets.isNotEmpty()) metadata["search_snippets"] = ddgSnippets.distinct().take(20).joinToString("\n").take(3000)
                if (distinctPhones.isNotEmpty() || distinctAges.isNotEmpty()) {
                    val rec = PersonRecord(
                        name = primaryQuery,
                        age = distinctAges.firstOrNull() ?: "",
                        location = distinctAddresses.firstOrNull()?.take(80) ?: locationStr,
                        phones = distinctPhones,
                        address = distinctAddresses.firstOrNull() ?: locationStr,
                        relatives = distinctRelatives,
                        source = "DDG Search"
                    )
                    scrapedPersonRecords.add(rec)
                }
            }

            send(SearchProgressEvent.Checking("AI Brief"))
            val aiPrompt = buildAiSummaryPrompt(primaryQuery, effectiveType, metadata.toMap())
            val aiSummary = withContext(Dispatchers.IO) { queryPollinationsAI(aiPrompt) }
            if (!aiSummary.isNullOrBlank()) {
                metadata["ai_summary"] = aiSummary
                send(SearchProgressEvent.Found("AI Brief", aiSummary.take(100)))
            } else {
                send(SearchProgressEvent.NotFound("AI Brief"))
            }

            val reportId = try {
                saveReport(query, null, sources.toList(), metadata.toMap())
            } catch (_: Exception) {
                UUID.randomUUID().toString()
            }

            if (effectiveType == "person" && round == 1) {
                val allPersonRecords = scrapedPersonRecords.toList()
                val deduped = deduplicatePersonRecords(allPersonRecords)
                if (deduped.isNotEmpty()) {
                    val candidates = deduped.take(6).mapIndexed { i, rec ->
                        CandidateProfile(
                            name = rec.name,
                            age = rec.age,
                            location = rec.location.ifBlank { locationStr },
                            phones = rec.phones,
                            address = rec.address.ifBlank { rec.location },
                            source = rec.source,
                            confidence = minOf(1f, (sources.size * 0.12f) - (i * 0.05f)).coerceAtLeast(0.1f),
                            relatives = rec.relatives,
                            photoUrl = rec.photoUrl,
                            profileUrl = rec.profileUrl
                        )
                    }
                    val autoSelect = deduped.size == 1
                    val primary = candidates.first()
                    val refinedParts = mutableListOf("name=${primary.name}")
                    if (primary.age.isNotBlank()) refinedParts.add("age=${primary.age}")
                    if (city.isNotBlank()) refinedParts.add("city=$city")
                    if (state.isNotBlank()) refinedParts.add("state=$state")
                    else if (primary.location.isNotBlank() && city.isBlank()) refinedParts.add("location=${primary.location}")
                    primary.phones.firstOrNull()?.let { refinedParts.add("phone=$it") }
                    val refinedQuery = refinedParts.joinToString("|")
                    send(SearchProgressEvent.CandidatesReady(
                        candidates = candidates,
                        reportId = reportId,
                        round = round,
                        autoSelect = autoSelect,
                        refinedQuery = if (autoSelect) refinedQuery else ""
                    ))
                    return@withContext
                } else if (sources.size > 1 || metadata["ddg_abstract"]?.isNotBlank() == true) {
                    val fallbackName = primaryQuery
                    val fallbackAge = metadata["search_age"] ?: ""
                    val fallbackLoc = metadata["search_addresses"]?.lines()?.firstOrNull()?.trim() ?: locationStr
                    val fallbackPhones = (metadata["search_phones"] ?: "").split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val candidate = CandidateProfile(
                        name = fallbackName, age = fallbackAge, location = fallbackLoc,
                        phones = fallbackPhones, address = fallbackLoc,
                        source = sources.firstOrNull()?.name ?: "Web",
                        confidence = minOf(1f, sources.size * 0.15f)
                    )
                    val refinedParts = mutableListOf("name=$fallbackName")
                    if (fallbackAge.isNotBlank()) refinedParts.add("age=$fallbackAge")
                    if (city.isNotBlank()) refinedParts.add("city=$city")
                    if (state.isNotBlank()) refinedParts.add("state=$state")
                    send(SearchProgressEvent.CandidatesReady(
                        candidates = listOf(candidate), reportId = reportId, round = round,
                        autoSelect = true, refinedQuery = refinedParts.joinToString("|")
                    ))
                    return@withContext
                }
            }

            send(SearchProgressEvent.Complete(reportId, sources.size))
        }
    }

    private suspend fun handleScrapeOut(
        name: String,
        url: String,
        out: ScrapeOut,
        sources: MutableList<DataSource>,
        metadata: ConcurrentHashMap<String, String>,
        channel: kotlinx.coroutines.channels.SendChannel<SearchProgressEvent>
    ) {
        when {
            out.blocked -> channel.send(SearchProgressEvent.Blocked(name))
            out.found -> {
                val detail = out.fields["snippet"]?.take(120) ?: out.fields["title"] ?: ""
                channel.send(SearchProgressEvent.Found(name, detail))
                sources.add(DataSource(name, url, Date(), 0.6))
                val key = SOURCE_ABBREVS[name.lowercase()] ?: name.lowercase().replace(" ", "_")
                out.fields.forEach { (k, v) -> metadata["${key}_$k"] = v }
            }
            else -> channel.send(SearchProgressEvent.NotFound(name))
        }
    }

    private fun parseFields(query: String): Map<String, String> {
        if (!query.contains("|") && !query.contains("=")) return mapOf("name" to query.trim())
        val result = mutableMapOf<String, String>()
        query.split("|").forEach { part ->
            val eq = part.indexOf("=")
            if (eq > 0) {
                val k = part.substring(0, eq).trim()
                val v = part.substring(eq + 1).trim()
                if (v.isNotBlank()) result[k] = v
            } else if (part.isNotBlank() && result.isEmpty()) {
                result["name"] = part.trim()
            }
        }
        return result
    }
}
