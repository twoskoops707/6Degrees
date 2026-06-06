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

    private fun scrapeFastPeopleSearch(name: String, city: String = "", state: String = ""): ScrapeOut {
        return try {
            val hyphen = name.trim().replace(" ", "-").lowercase()
            val locationSlug = buildString {
                if (city.isNotBlank()) append("_${city.trim().replace(" ", "-").lowercase()}")
                if (state.isNotBlank() && city.isNotBlank()) append("-${state.trim().replace(" ", "-").lowercase()}")
                else if (state.isNotBlank()) append("_${state.trim().replace(" ", "-").lowercase()}")
            }
            val url = "https://www.fastpeoplesearch.com/name/$hyphen$locationSlug"
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://www.fastpeoplesearch.com/")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val code = resp.code
            val body = resp.body?.string() ?: ""
            resp.close()
            if (code == 403 || code == 429 || body.contains("cf-challenge-running") ||
                body.contains("Just a moment", ignoreCase = true) || body.contains("Enable JavaScript")) {
                return ScrapeOut(false, true)
            }
            if (code == 404 || code >= 500 || body.isBlank()) return ScrapeOut(false, false)
            val doc = Jsoup.parse(body)
            val fields = mutableMapOf<String, String>()
            fields["title"] = doc.title().take(120)
            val cards = doc.select("div.card-block, div.person, div[class*=result], article")
            val allNames = mutableListOf<String>()
            val allAges = mutableListOf<String>()
            val allLocations = mutableListOf<String>()
            val allPhones = mutableListOf<String>()
            val allRelatives = mutableListOf<String>()
            val personRecords = mutableListOf<PersonRecord>()
            val phoneRegex = Regex("""\(?(\d{3})\)?[.\-\s](\d{3})[.\-\s](\d{4})""")
            for (card in cards.take(6)) {
                val cardName = card.selectFirst("h2, h3, .name, [itemprop=name], .card-title")?.text()?.trim()?.takeIf { it.length > 3 } ?: continue
                val cardAge = (card.selectFirst(".age, .age-value, [data-age]")?.text()?.replace(Regex("[^0-9]"), "")?.takeIf { it.isNotBlank() }
                    ?: Regex("""(?i)\bage[:\s]+(\d{2,3})\b""").find(card.text())?.groupValues?.get(1)) ?: ""
                val cardLoc = card.selectFirst("address, .address, .location, [itemprop=address], .city-state")?.text()?.trim() ?: ""
                val cardPhones = phoneRegex.findAll(card.text()).map { "(${it.groupValues[1]}) ${it.groupValues[2]}-${it.groupValues[3]}" }.distinct().toList()
                val cardRelatives = card.select(".relatives a, .relative a, .associates a, li a[href*=name]").mapNotNull { a -> a.text().takeIf { it.isNotBlank() && it.length > 3 } }
                val cardProfileUrl = card.selectFirst("a[href*=/name/]")?.attr("abs:href")
                personRecords.add(PersonRecord(name = cardName, age = cardAge, location = cardLoc, phones = cardPhones, address = cardLoc, relatives = cardRelatives, profileUrl = cardProfileUrl, source = "FastPeopleSearch"))
                allNames.add(cardName)
                if (cardAge.isNotBlank()) allAges.add(cardAge)
                if (cardLoc.isNotBlank()) allLocations.add(cardLoc)
                allPhones.addAll(cardPhones)
                allRelatives.addAll(cardRelatives)
            }
            val fullText = doc.body()?.text() ?: ""
            if (fullText.length < 100) return ScrapeOut(false, false)
            phoneRegex.findAll(fullText).map { "(${it.groupValues[1]}) ${it.groupValues[2]}-${it.groupValues[3]}" }.forEach { allPhones.add(it) }
            fields["snippet"] = fullText.take(600)
            fields["source_type"] = "person_record"
            if (allNames.isNotEmpty()) fields["names"] = allNames.distinct().take(5).joinToString(", ")
            if (allAges.isNotEmpty()) fields["age"] = allAges.first()
            if (allLocations.isNotEmpty()) fields["locations"] = allLocations.distinct().take(6).joinToString(" | ")
            val distinctPhones = allPhones.distinct().take(8)
            if (distinctPhones.isNotEmpty()) fields["phones"] = distinctPhones.joinToString(", ")
            if (allRelatives.isNotEmpty()) fields["relatives"] = allRelatives.distinct().take(10).joinToString(", ")
            if (fields.size <= 2) return ScrapeOut(false, false)
            ScrapeOut(true, false, fields, personRecords)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeThatsThem(query: String, type: String, city: String = "", state: String = ""): ScrapeOut {
        return try {
            val hyphen = query.trim().replace(" ", "-").lowercase()
            val url = when (type) {
                "email" -> "https://thatsthem.com/email/${URLEncoder.encode(query, "UTF-8")}"
                "phone" -> "https://thatsthem.com/phone/${query.replace(Regex("[^0-9]"), "")}"
                else -> {
                    val locSlug = listOf(city, state).filter { it.isNotBlank() }.joinToString("-") { it.trim().replace(" ", "-").lowercase() }
                    if (locSlug.isNotBlank()) "https://thatsthem.com/name/$hyphen?city=${URLEncoder.encode(city, "UTF-8")}&state=${URLEncoder.encode(state, "UTF-8")}"
                    else "https://thatsthem.com/name/$hyphen"
                }
            }
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val code = resp.code
            val body = resp.body?.string() ?: ""
            resp.close()
            if (code == 403 || code == 429 || body.contains("cf-challenge-running") ||
                body.contains("Just a moment", ignoreCase = true) || body.contains("Enable JavaScript")) {
                return ScrapeOut(false, true)
            }
            if (code == 404 || code >= 500 || body.isBlank()) return ScrapeOut(false, false)
            val doc = Jsoup.parse(body)
            val fields = mutableMapOf<String, String>()
            fields["title"] = doc.title().take(120)
            val cards = doc.select(".ThatsThem-person, .person-block, .result-person, div[class*=person], div[class*=result]")
            val allNames = mutableListOf<String>()
            val allAges = mutableListOf<String>()
            val allLocations = mutableListOf<String>()
            val allPhones = mutableListOf<String>()
            val allRelatives = mutableListOf<String>()
            val personRecords = mutableListOf<PersonRecord>()
            val phoneRegex = Regex("""\(?(\d{3})\)?[.\-\s](\d{3})[.\-\s](\d{4})""")
            for (card in cards.take(5)) {
                val cardName = card.selectFirst(".name, h2, h3, [itemprop=name]")?.text()?.trim()?.takeIf { it.length > 3 } ?: continue
                val cardAge = card.selectFirst(".age, [class*=age]")?.text()?.replace(Regex("[^0-9]"), "")?.takeIf { it.isNotBlank() } ?: ""
                val cardLoc = card.selectFirst(".location, .city, address, [itemprop=address]")?.text()?.trim() ?: ""
                val cardPhones = phoneRegex.findAll(card.text()).map { "(${it.groupValues[1]}) ${it.groupValues[2]}-${it.groupValues[3]}" }.distinct().toList()
                val cardProfileUrl = card.selectFirst("a[href*=/name/], a[href*=/person/]")?.attr("abs:href")
                personRecords.add(PersonRecord(name = cardName, age = cardAge, location = cardLoc, phones = cardPhones, address = cardLoc, source = "ThatsThem", profileUrl = cardProfileUrl))
                allNames.add(cardName)
                if (cardAge.isNotBlank()) allAges.add(cardAge)
                if (cardLoc.isNotBlank()) allLocations.add(cardLoc)
                allPhones.addAll(cardPhones)
            }
            val fullText = doc.body()?.text() ?: ""
            if (fullText.length < 100) return ScrapeOut(false, false)
            phoneRegex.findAll(fullText).map { "(${it.groupValues[1]}) ${it.groupValues[2]}-${it.groupValues[3]}" }.forEach { allPhones.add(it) }
            fields["snippet"] = fullText.take(600)
            if (allNames.isNotEmpty()) fields["names"] = allNames.distinct().take(5).joinToString(", ")
            if (allAges.isNotEmpty()) fields["ages"] = allAges.distinct().take(3).joinToString(", ")
            if (allLocations.isNotEmpty()) fields["locations"] = allLocations.distinct().take(6).joinToString(" | ")
            val distinctPhones = allPhones.distinct().take(8)
            if (distinctPhones.isNotEmpty()) fields["phones"] = distinctPhones.joinToString(", ")
            if (allRelatives.isNotEmpty()) fields["relatives"] = allRelatives.distinct().take(10).joinToString(", ")
            if (fields.size <= 2) return ScrapeOut(false, false)
            ScrapeOut(true, false, fields, personRecords)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeTruePeopleSearch(name: String, city: String = "", state: String = ""): ScrapeOut {
        return try {
            val encoded = URLEncoder.encode(name, "UTF-8")
            val url = buildString {
                append("https://www.truepeoplesearch.com/results?name=$encoded")
                if (city.isNotBlank()) append("&citystatezip=${URLEncoder.encode("$city ${state}".trim(), "UTF-8")}")
            }
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val code = resp.code
            val body = resp.body?.string() ?: ""
            resp.close()
            if (code == 403 || code == 429 || body.contains("cf-challenge-running") ||
                body.contains("Just a moment", ignoreCase = true) || body.contains("Enable JavaScript")) {
                return ScrapeOut(false, true)
            }
            if (code == 404 || code >= 500 || body.isBlank()) return ScrapeOut(false, false)
            val doc = Jsoup.parse(body)
            val fields = mutableMapOf<String, String>()
            fields["title"] = doc.title().take(120)
            val cards = doc.select("div[data-lunr-doc-id], div.card, div[class*=result]")
            val allNames = mutableListOf<String>()
            val allAges = mutableListOf<String>()
            val allLocations = mutableListOf<String>()
            val allPhones = mutableListOf<String>()
            val allRelatives = mutableListOf<String>()
            val personRecords = mutableListOf<PersonRecord>()
            val phoneRegex = Regex("""\(?(\d{3})\)?[.\-\s](\d{3})[.\-\s](\d{4})""")
            for (card in cards.take(5)) {
                val cardName = card.selectFirst("div.h4, .full-name, span[itemprop=name], h2")?.text()?.trim()?.takeIf { it.isNotBlank() } ?: continue
                val cardAge = card.selectFirst("[class*=age], span:contains(Age)")?.text()?.replace(Regex("[^0-9]"), "")?.takeIf { it.isNotBlank() } ?: ""
                val cardLoc = card.selectFirst("div[class*=location], [itemprop=addressLocality]")?.text()?.trim() ?: ""
                val cardPhones = phoneRegex.findAll(card.text()).map { "(${it.groupValues[1]}) ${it.groupValues[2]}-${it.groupValues[3]}" }.distinct().toList()
                val cardProfileUrl = card.selectFirst("a[href*=/find/]")?.attr("abs:href")
                personRecords.add(PersonRecord(name = cardName, age = cardAge, location = cardLoc, phones = cardPhones, address = cardLoc, source = "TruePeopleSearch", profileUrl = cardProfileUrl))
                allNames.add(cardName)
                if (cardAge.isNotBlank()) allAges.add(cardAge)
                if (cardLoc.isNotBlank()) allLocations.add(cardLoc)
                allPhones.addAll(cardPhones)
            }
            val fullText = doc.body()?.text() ?: ""
            if (fullText.length < 100) return ScrapeOut(false, false)
            phoneRegex.findAll(fullText).map { "(${it.groupValues[1]}) ${it.groupValues[2]}-${it.groupValues[3]}" }.forEach { allPhones.add(it) }
            fields["snippet"] = fullText.take(600)
            if (allNames.isNotEmpty()) fields["names"] = allNames.distinct().take(5).joinToString(", ")
            if (allAges.isNotEmpty()) fields["age"] = allAges.first()
            if (allLocations.isNotEmpty()) fields["locations"] = allLocations.distinct().take(6).joinToString(" | ")
            val distinctPhones = allPhones.distinct().take(8)
            if (distinctPhones.isNotEmpty()) fields["phones"] = distinctPhones.joinToString(", ")
            if (allRelatives.isNotEmpty()) fields["relatives"] = allRelatives.distinct().take(10).joinToString(", ")
            if (fields.size <= 2) return ScrapeOut(false, false)
            ScrapeOut(true, false, fields, personRecords)
        } catch (_: Exception) { ScrapeOut(false, false) }
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

    private fun scrapeZabaSearch(name: String, city: String = "", state: String = ""): ScrapeOut {
        return try {
            val parts = name.trim().split(" ")
            val first = parts.firstOrNull()?.lowercase() ?: return ScrapeOut(false, false)
            val last = parts.drop(1).joinToString("-").lowercase().ifBlank { return ScrapeOut(false, false) }
            val stateLower = state.trim().lowercase().replace(" ", "-")
            val url = if (stateLower.isNotBlank()) "https://www.zabasearch.com/people/$first+$last/$stateLower/"
                      else "https://www.zabasearch.com/people/$first+$last/"
            tryScrapeUrl(url)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeFamilyTreeNow(name: String, city: String = "", state: String = ""): ScrapeOut {
        return try {
            val encoded = URLEncoder.encode(name, "UTF-8")
            val stateEnc = URLEncoder.encode(state, "UTF-8")
            val url = if (state.isNotBlank()) "https://www.familytreenow.com/search/genealogy/results/?firstname=${encoded.substringBefore("+")}&lastname=${encoded.substringAfter("+")}&state=$stateEnc"
                      else "https://www.familytreenow.com/search/genealogy/results/?q=$encoded"
            tryScrapeUrl(url)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeUSPhoneBook(name: String, city: String = "", state: String = ""): ScrapeOut {
        return try {
            val parts = name.trim().split(" ")
            val first = URLEncoder.encode(parts.firstOrNull() ?: "", "UTF-8")
            val last = URLEncoder.encode(parts.drop(1).joinToString(" "), "UTF-8")
            val stateCode = state.trim().uppercase().take(2)
            val url = if (stateCode.isNotBlank()) "https://www.usphonebook.com/$first-$last/$stateCode"
                      else "https://www.usphonebook.com/$first-$last"
            tryScrapeUrl(url)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrape411(name: String, city: String = "", state: String = ""): ScrapeOut {
        return try {
            val nameParts = name.trim().split(" ")
            val first = URLEncoder.encode(nameParts.firstOrNull() ?: "", "UTF-8")
            val last = URLEncoder.encode(nameParts.drop(1).joinToString(" "), "UTF-8")
            val url = if (city.isNotBlank() && state.isNotBlank())
                "https://www.411.com/name/$first-$last/${URLEncoder.encode(city, "UTF-8")}-${state.trim().uppercase()}"
            else "https://www.411.com/name/$first-$last"
            tryScrapeUrl(url)
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
        listOfNotNull(metadata["fps_phones"], metadata["tps_phones"], metadata["tt_phones"], metadata["person_phone"])
            .firstOrNull { it.isNotBlank() }?.let { appendLine("Phone(s): $it") }
        listOfNotNull(metadata["fps_relatives"], metadata["tps_relatives"], metadata["tt_relatives"])
            .firstOrNull { it.isNotBlank() }?.let { appendLine("Associates: $it") }
        metadata["github_name"]?.takeIf { it.isNotBlank() }?.let { appendLine("GitHub Name: $it") }
        metadata["github_stats"]?.takeIf { it.isNotBlank() }?.let { appendLine("GitHub: $it") }
        metadata["reddit_url"]?.takeIf { it.isNotBlank() }?.let { appendLine("Reddit: $it") }
        metadata["wikipedia_extract"]?.takeIf { it.isNotBlank() }?.let { appendLine("\nWikipedia:\n${it.take(500)}") }
        metadata["ddg_abstract"]?.takeIf { it.isNotBlank() }?.let { appendLine("\nWeb Summary:\n${it.take(400)}") }
        metadata.entries.firstOrNull { it.key.contains("news") && it.value.isNotBlank() }
            ?.value?.let { appendLine("\nNews:\n${it.take(400)}") }
        metadata["darksearch_links"]?.takeIf { it.isNotBlank() }?.let { appendLine("\nDark Web Mentions:\n${it.take(200)}") }
        listOfNotNull(metadata["fps_snippet"], metadata["tps_snippet"], metadata["tt_snippet"])
            .firstOrNull { it.isNotBlank() }?.let { appendLine("\nPublic Records:\n${it.take(300)}") }
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
                        targetedScraperNames += setOf("FastPeopleSearch", "ThatsThem", "TruePeopleSearch", "Wikipedia", "Google News", "DarkSearch", "ZabaSearch", "FamilyTreeNow", "USPhoneBook", "411.com")
                        launch { termuxRunner.ensureTorRunning().collect { send(it) } }
                        launch {
                            send(SearchProgressEvent.Checking("FastPeopleSearch"))
                            val out = scrapeFastPeopleSearch(primaryQuery, city, state)
                            scrapedPersonRecords.addAll(out.persons)
                            val nameSlug = primaryQuery.replace(" ", "-").lowercase()
                            val locSlug = if (city.isNotBlank()) "_${city.replace(" ", "-").lowercase()}${if (state.isNotBlank()) "-${state.replace(" ", "-").lowercase()}" else ""}" else ""
                            handleScrapeOut("FastPeopleSearch", "https://www.fastpeoplesearch.com/name/$nameSlug$locSlug", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("ThatsThem"))
                            val out = scrapeThatsThem(primaryQuery, "person", city, state)
                            scrapedPersonRecords.addAll(out.persons)
                            handleScrapeOut("ThatsThem", "https://thatsthem.com/name/${primaryQuery.replace(" ", "-").lowercase()}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("TruePeopleSearch"))
                            val out = scrapeTruePeopleSearch(primaryQuery, city, state)
                            scrapedPersonRecords.addAll(out.persons)
                            handleScrapeOut("TruePeopleSearch", "https://www.truepeoplesearch.com/results?name=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
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
                                        val result = RetrofitClient.piplService.search(
                                            apiKey = key,
                                            firstName = first,
                                            lastName = last
                                        )
                                        val person = result.person
                                        if (person == null) {
                                            send(SearchProgressEvent.NotFound("Pipl"))
                                        } else {
                                            metadata["pipl_found"] = "true"
                                            val displayName = person.names?.firstOrNull()?.display
                                            if (!displayName.isNullOrBlank()) metadata["pipl_name"] = displayName
                                            val emailAddresses = person.emails?.mapNotNull { it.address }
                                            if (!emailAddresses.isNullOrEmpty()) metadata["pipl_emails"] = emailAddresses.take(3).joinToString(", ")
                                            val phones = person.phones?.mapNotNull { it.display ?: it.number }
                                            if (!phones.isNullOrEmpty()) metadata["pipl_phones"] = phones.take(3).joinToString(", ")
                                            sources.add(DataSource("Pipl", "https://pipl.com/search/?q=${encode(primaryQuery)}", java.util.Date(), 0.9))
                                            val detail = displayName ?: "Person profile found"
                                            send(SearchProgressEvent.Found("Pipl", detail))
                                        }
                                    } catch (_: Exception) {
                                        send(SearchProgressEvent.Blocked("Pipl"))
                                    }
                                }
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
                            semaphore.withPermit {
                                send(SearchProgressEvent.Checking("ZabaSearch"))
                                val out = scrapeZabaSearch(primaryQuery, city, state)
                                handleScrapeOut("ZabaSearch", "https://www.zabasearch.com/people/${encode(primaryQuery)}/", out, sources, metadata, this@channelFlow)
                            }
                        }
                        launch {
                            semaphore.withPermit {
                                send(SearchProgressEvent.Checking("FamilyTreeNow"))
                                val out = scrapeFamilyTreeNow(primaryQuery, city, state)
                                handleScrapeOut("FamilyTreeNow", "https://www.familytreenow.com/search/genealogy/results/?q=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
                            }
                        }
                        launch {
                            semaphore.withPermit {
                                send(SearchProgressEvent.Checking("USPhoneBook"))
                                val out = scrapeUSPhoneBook(primaryQuery, city, state)
                                handleScrapeOut("USPhoneBook", "https://www.usphonebook.com/${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
                            }
                        }
                        launch {
                            semaphore.withPermit {
                                send(SearchProgressEvent.Checking("411.com"))
                                val out = scrape411(primaryQuery, city, state)
                                handleScrapeOut("411.com", "https://www.411.com/name/${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
                            }
                        }
                    }
                    "email", "breach" -> {
                        targetedScraperNames += setOf("ProxyNova", "HackerTarget")
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
                            send(SearchProgressEvent.Checking("ThatsThem"))
                            val out = scrapeThatsThem(primaryQuery, "email")
                            handleScrapeOut("ThatsThem", "https://thatsthem.com/email/${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
                            targetedScraperNames += "ThatsThem"
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
                                termuxRunner.runHolehe(primaryQuery).collect { send(it) }
                            }
                        }
                    }
                    "domain", "ip" -> {
                        targetedScraperNames += setOf("HackerTarget Host", "Wayback CDX", "crt.sh", "DarkSearch", "Google News")
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
                    }
                    "phone" -> {
                        targetedScraperNames += setOf("800notes", "ThatsThem")
                        launch {
                            send(SearchProgressEvent.Checking("800notes"))
                            val out = scrape800Notes(primaryQuery)
                            val digits = primaryQuery.replace(Regex("[^0-9]"), "")
                            val fmt = if (digits.length >= 10) "${digits.substring(0,3)}-${digits.substring(3,6)}-${digits.substring(6,10)}" else primaryQuery
                            handleScrapeOut("800notes", "https://800notes.com/Phone.aspx/$fmt", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("ThatsThem"))
                            val out = scrapeThatsThem(primaryQuery, "phone")
                            val digits = primaryQuery.replace(Regex("[^0-9]"), "")
                            handleScrapeOut("ThatsThem", "https://thatsthem.com/phone/$digits", out, sources, metadata, this@channelFlow)
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
                                termuxRunner.runSherlock(primaryQuery).collect { send(it) }
                            }
                        }
                        launch {
                            semaphore.withPermit {
                                termuxRunner.runMaigret(primaryQuery).collect { send(it) }
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

            send(SearchProgressEvent.Checking("AI Brief"))
            val aiPrompt = buildAiSummaryPrompt(primaryQuery, effectiveType, metadata.toMap())
            val aiSummary = withContext(Dispatchers.IO) { queryPollinationsAI(aiPrompt) }
            if (!aiSummary.isNullOrBlank()) {
                metadata["ai_summary"] = aiSummary
                send(SearchProgressEvent.Found("AI Brief", aiSummary.take(100)))
            } else {
                send(SearchProgressEvent.NotFound("AI Brief"))
            }

            val reportId = saveReport(query, null, sources.toList(), metadata.toMap())

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
                    val fallbackAge = metadata["fps_age"] ?: metadata["tps_age"] ?: ""
                    val fallbackLoc = metadata["fps_locations"]?.split("|")?.firstOrNull()?.trim() ?: locationStr
                    val fallbackPhones = (metadata["fps_phones"] ?: metadata["tps_phones"] ?: "").split(",").map { it.trim() }.filter { it.isNotBlank() }
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
