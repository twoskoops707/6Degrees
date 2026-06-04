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
import okhttp3.OkHttpClient
import okhttp3.Request
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

    private val torHttpClient: OkHttpClient? by lazy {
        try {
            val probe = java.net.Socket()
            probe.connect(InetSocketAddress("127.0.0.1", 9050), 2000)
            probe.close()
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", 9050))
            OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        } catch (_: Exception) { null }
    }

    private data class ScrapeOut(
        val found: Boolean,
        val blocked: Boolean,
        val fields: Map<String, String> = emptyMap()
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

    private fun duckDuckGoSearch(query: String): Pair<String, String> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://html.duckduckgo.com/html/?q=$encoded"
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()

            val doc = Jsoup.parse(body)
            val results = doc.select(".result__snippet").take(5).map { it.text() }
            val links = doc.select(".result__url").take(5).map { it.text() }
            val snippet = results.joinToString(" | ").take(800)
            val urls = links.joinToString(", ").take(400)
            Pair(snippet, urls)
        } catch (_: Exception) {
            Pair("", "")
        }
    }

    private fun scrapeFastPeopleSearch(name: String): ScrapeOut {
        return try {
            val hyphen = name.trim().replace(" ", "-")
            val url = "https://www.fastpeoplesearch.com/name/$hyphen"
            val out = tryScrapeUrl(url)
            if (!out.found) return out
            val fields = out.fields.toMutableMap()
            fields["source_type"] = "person_record"
            ScrapeOut(true, false, fields)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeThatsThem(query: String, type: String): ScrapeOut {
        return try {
            val hyphen = query.trim().replace(" ", "-")
            val url = when (type) {
                "email" -> "https://thatsthem.com/email/${URLEncoder.encode(query, "UTF-8")}"
                "phone" -> "https://thatsthem.com/phone/${query.replace(Regex("[^0-9]"), "")}"
                else -> "https://thatsthem.com/name/$hyphen"
            }
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
            val client = torHttpClient ?: fastHttpClient
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
            metadata["search_type"] = type
            fields.forEach { (k, v) -> metadata["field_$k"] = v }

            val sources = Collections.synchronizedList(mutableListOf<DataSource>())
            val semaphore = Semaphore(5)

            send(SearchProgressEvent.Checking("DuckDuckGo"))
            val (ddgSnippet, ddgUrls) = duckDuckGoSearch(primaryQuery)
            if (ddgSnippet.isNotBlank()) {
                metadata["ddg_snippet"] = ddgSnippet
                metadata["ddg_abstract"] = ddgSnippet
                metadata["ddg_web_snippets"] = ddgSnippet
                metadata["ddg_urls"] = ddgUrls
                sources.add(DataSource("DuckDuckGo", "https://html.duckduckgo.com/html/?q=${encode(primaryQuery)}", Date(), 0.7))
                send(SearchProgressEvent.Found("DuckDuckGo", ddgSnippet.take(120)))
            } else {
                send(SearchProgressEvent.NotFound("DuckDuckGo"))
            }

            val targetedScraperNames = mutableSetOf<String>()

            coroutineScope {
                when (type) {
                    "person", "comprehensive" -> {
                        targetedScraperNames += setOf("FastPeopleSearch", "ThatsThem")
                        launch {
                            send(SearchProgressEvent.Checking("FastPeopleSearch"))
                            val out = scrapeFastPeopleSearch(primaryQuery)
                            handleScrapeOut("FastPeopleSearch", "https://www.fastpeoplesearch.com/name/${primaryQuery.replace(" ", "-")}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("ThatsThem"))
                            val out = scrapeThatsThem(primaryQuery, "person")
                            handleScrapeOut("ThatsThem", "https://thatsthem.com/name/${primaryQuery.replace(" ", "-")}", out, sources, metadata, this@channelFlow)
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
                        targetedScraperNames += setOf("HackerTarget Host", "Wayback CDX")
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

            val reportId = saveReport(query, null, sources.toList(), metadata.toMap())
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
