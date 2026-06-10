package com.twoskoops707.sixdegrees.data.repository

import android.content.Context
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.twoskoops707.sixdegrees.data.AppSettings
import com.twoskoops707.sixdegrees.data.SearchPresetManager
import com.twoskoops707.sixdegrees.data.BlockedSourceCache
import com.twoskoops707.sixdegrees.data.ai.OpenRouterAiClient
import com.twoskoops707.sixdegrees.data.ai.OsintAiReport
import android.util.Log
import com.twoskoops707.sixdegrees.data.local.OsintDatabase
import com.twoskoops707.sixdegrees.data.local.entity.OsintReportEntity
import com.twoskoops707.sixdegrees.data.local.entity.PersonEntity
import com.twoskoops707.sixdegrees.data.osint.OsintFrameworkReportBridge
import com.twoskoops707.sixdegrees.data.osint.OsintToolRegistry
import com.twoskoops707.sixdegrees.data.remote.dto.peopledatalabs.PdlPerson
import com.twoskoops707.sixdegrees.data.remote.dto.pipl.PiplPerson
import com.twoskoops707.sixdegrees.data.remote.RetrofitClient
import com.twoskoops707.sixdegrees.domain.DorkMetadataStore
import com.twoskoops707.sixdegrees.domain.GoogleDorkLibrary
import com.twoskoops707.sixdegrees.domain.SearchPhase
import com.twoskoops707.sixdegrees.domain.SubjectFilter
import com.twoskoops707.sixdegrees.domain.SubjectIntakeParser
import com.twoskoops707.sixdegrees.domain.ReportMetadataSync
import com.twoskoops707.sixdegrees.domain.SubjectConnectionEngine
import com.twoskoops707.sixdegrees.domain.SubjectSearchOrchestrator
import com.twoskoops707.sixdegrees.domain.model.CandidateProfile
import com.twoskoops707.sixdegrees.domain.model.DataSource
import com.twoskoops707.sixdegrees.domain.model.SocialHint
import com.twoskoops707.sixdegrees.domain.model.SubjectProfile
import com.twoskoops707.sixdegrees.scraper.ScrapeResult
import com.twoskoops707.sixdegrees.scraper.scrapeFastPeopleSearch
import com.twoskoops707.sixdegrees.scraper.scrapeProxyNova as scrapeProxyNovaWeb
import com.twoskoops707.sixdegrees.scraper.scrapeThatsThem
import com.twoskoops707.sixdegrees.tor.TorBootstrapManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import retrofit2.HttpException
import android.media.ExifInterface
import android.net.Uri
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
    "ahmia" to "ahmia",
    "sec edgar" to "sec",
    "wikidata" to "wikidata",
    "urlhaus" to "urlhaus",
    "abuseipdb" to "abuseipdb",
    "virustotal" to "vt",
    "urlscan" to "urlscan",
    "numverify" to "numverify",
    "name demographics" to "demographics",
    "fbi wanted" to "fbi_wanted",
    "npi registry" to "npi",
    "zippopotam" to "zippopotam",
    "openfec" to "openfec",
    "ipinfo" to "ipinfo",
    "kickbox disposable" to "kickbox",
    "libphonenumber" to "libphone",
    "calltracer" to "calltracer"
)

    private fun isIpAddress(value: String): Boolean =
        value.trim().matches(Regex("""^(?:\d{1,3}\.){3}\d{1,3}$"""))

    private fun isLikelyDomain(value: String): Boolean {
        val v = value.trim().lowercase()
        return v.contains(".") && !isIpAddress(v) && !v.contains("@") &&
            v.matches(Regex("""^[a-z0-9][a-z0-9.-]*\.[a-z]{2,}$"""))
    }

    private fun queryTokens(query: String): List<String> =
        query.trim().lowercase().split(Regex("\\s+")).filter { it.length > 1 }

    private fun textMatchesQuery(text: String, query: String): Boolean {
        val tokens = queryTokens(query)
        if (tokens.isEmpty()) return true
        val lower = text.lowercase()
        val required = if (tokens.size >= 2) 2 else 1
        return tokens.count { lower.contains(it) } >= required
    }

    private const val SEC_USER_AGENT = "6Degrees/1.0 (Android OSINT; contact@6degrees.app)"

class OsintRepository(context: Context) {

    private val appCtx = context.applicationContext
    private val apiKeys = com.twoskoops707.sixdegrees.data.ApiKeyManager(context)
    private val blockedSourceCache = BlockedSourceCache(context)
    private val db = OsintDatabase.getDatabase(context)
    private val moshi = Moshi.Builder().add(DateAdapter()).add(KotlinJsonAdapterFactory()).build()

    private val fastHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val termuxRunner by lazy { TermuxToolRunner(appCtx) }
    private val usernameDiscovery by lazy { UsernameDiscoveryService() }
    private val inHouseRunner by lazy { InHouseOsintRunner(usernameDiscovery) }

    private suspend fun runUsernameDiscovery(
        usernames: Collection<String>,
        socialUrls: Collection<String>,
        metadata: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        channel: SendChannel<SearchProgressEvent>
    ) {
        val primary = usernames.firstOrNull()?.trim()?.removePrefix("@").orEmpty()
        if (primary.isBlank() && socialUrls.isEmpty()) return
        val user = primary.ifBlank { usernames.first() }
        inHouseRunner.runUsernameScan(user, metadata, sources, channel, socialUrls)
        maybeRunTermuxUsernameTools(user, metadata, sources, channel)
        if (metadata["profile_photo_url"].isNullOrBlank()) {
            fetchAvatarUrl(user, "github")?.let { metadata["profile_photo_url"] = it }
        }
    }

    private suspend fun maybeRunTermuxUsernameTools(
        username: String,
        metadata: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        channel: SendChannel<SearchProgressEvent>
    ) {
        if (!AppSettings.isTermuxFallbackEnabled(appCtx) || !termuxRunner.isTermuxInstalled()) return
        if (!termuxRunner.canRunCommands()) {
            channel.send(SearchProgressEvent.Blocked(
                "Termux",
                "Permission not granted — open Termux, enable Allow External Apps, then allow 6Degrees"
            ))
            return
        }
        launchTermuxUsernameCollect(username, metadata, sources, channel)
    }

    private suspend fun launchTermuxUsernameCollect(
        username: String,
        metadata: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        channel: SendChannel<SearchProgressEvent>
    ) {
        val sherlockHits = mutableListOf<String>()
        termuxRunner.runSherlock(username).collect { event ->
            channel.send(event)
            if (event is SearchProgressEvent.Found) {
                sherlockHits.add("${event.source.removePrefix("sherlock/")}: ${event.detail}")
                sources.add(DataSource(event.source, event.detail, Date(), 0.75))
            }
        }
        if (sherlockHits.isNotEmpty()) {
            appendMetadata(metadata, "sherlock_found", sherlockHits.joinToString("\n"))
        }
        val maigretHits = mutableListOf<String>()
        termuxRunner.runMaigret(username).collect { event ->
            channel.send(event)
            if (event is SearchProgressEvent.Found) {
                maigretHits.add("${event.source.removePrefix("maigret/")}: ${event.detail}")
                sources.add(DataSource(event.source, event.detail, Date(), 0.75))
            }
        }
        if (maigretHits.isNotEmpty()) {
            appendMetadata(metadata, "maigret_found", maigretHits.joinToString("\n"))
        }
    }

    private suspend fun maybeRunTermuxHolehe(
        email: String,
        metadata: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        channel: SendChannel<SearchProgressEvent>
    ) {
        if (!AppSettings.isTermuxFallbackEnabled(appCtx) || !termuxRunner.isTermuxInstalled()) return
        if (!termuxRunner.canRunCommands()) return
        val holeheHits = mutableListOf<String>()
        termuxRunner.runHolehe(email).collect { event ->
            channel.send(event)
            if (event is SearchProgressEvent.Found) {
                holeheHits.add(event.source.removePrefix("holehe/"))
                sources.add(DataSource(event.source, event.detail, Date(), 0.7))
            }
        }
        if (holeheHits.isNotEmpty()) {
            val merged = (metadata["holehe_services"]?.split(",")?.map { it.trim() }.orEmpty() + holeheHits).distinct()
            metadata["holehe_services"] = merged.joinToString(", ")
        }
    }

    private fun torHttpClientOrNull(): OkHttpClient? {
        if (!TorBootstrapManager.isPortOpen()) return null
        TorBootstrapManager.markReady()
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", TorBootstrapManager.TOR_SOCKS_PORT))
        return OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private suspend fun ensureDarkWebTor(channel: SendChannel<SearchProgressEvent>) {
        channel.send(SearchProgressEvent.Checking("Tor"))
        if (TorBootstrapManager.isPortOpen()) {
            TorBootstrapManager.markReady()
            channel.send(SearchProgressEvent.Found("Tor", "SOCKS proxy ready on :9050"))
            return
        }
        if (TorBootstrapManager.ensureReady(appCtx, embeddedTimeoutMs = 45_000L)) {
            channel.send(SearchProgressEvent.Found("Tor", "Embedded Tor ready on :9050"))
            return
        }
        termuxRunner.ensureTorRunning().collect { channel.send(it) }
    }

    private fun applyAhmiaMetadata(out: ScrapeOut, metadata: ConcurrentHashMap<String, String>) {
        out.fields.forEach { (k, v) -> metadata["ahmia_$k"] = v }
    }

    private fun appendMetadata(metadata: ConcurrentHashMap<String, String>, key: String, value: String) {
        if (value.isBlank()) return
        val existing = metadata[key]
        metadata[key] = if (existing.isNullOrBlank()) value else "$existing\n$value"
    }

    private fun isLikelyPhone(value: String): Boolean {
        val digits = value.replace(Regex("[^0-9]"), "")
        return digits.length in 7..15
    }

    private suspend fun runDarkWebSearches(
        subjectProfile: SubjectProfile,
        primaryQuery: String,
        sources: MutableList<DataSource>,
        metadata: ConcurrentHashMap<String, String>,
        channel: SendChannel<SearchProgressEvent>
    ) {
        val terms = subjectProfile.darkWebSearchTerms().ifEmpty { listOf(primaryQuery) }.distinct().take(4)
        metadata["darkweb_search_terms"] = terms.joinToString(", ")
        terms.forEachIndexed { index, term ->
            channel.send(SearchProgressEvent.Checking("DarkSearch"))
            val dsOut = searchDarkWeb(term)
            if (dsOut.found) {
                dsOut.fields["dark_links"]?.let { appendMetadata(metadata, "darksearch_links", it) }
                dsOut.fields["snippet"]?.let { appendMetadata(metadata, "darksearch_snippet", it) }
            }
            handleScrapeOut(
                "DarkSearch",
                "https://darksearch.io/api/search?query=${encode(term)}",
                dsOut, sources, metadata, channel, 0.55
            )
            channel.send(SearchProgressEvent.Checking("Ahmia"))
            val ahmiaOut = scrapeAhmia(term)
            if (index == 0) {
                applyAhmiaMetadata(ahmiaOut, metadata)
            } else {
                ahmiaOut.fields["urls"]?.let { appendMetadata(metadata, "ahmia_urls", it) }
                ahmiaOut.fields["titles"]?.let { appendMetadata(metadata, "ahmia_titles", it) }
                ahmiaOut.fields["snippet"]?.let { appendMetadata(metadata, "ahmia_snippet", it) }
                val prev = metadata["ahmia_count"]?.toIntOrNull() ?: 0
                val add = ahmiaOut.fields["count"]?.toIntOrNull() ?: 0
                metadata["ahmia_count"] = (prev + add).toString()
            }
            handleScrapeOut(
                "Ahmia",
                "https://ahmia.fi/search/?q=${encode(term)}",
                ahmiaOut, sources, metadata, channel, 0.55
            )
        }
    }

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

    private fun scrapeResultToOut(result: ScrapeResult, blocked: Boolean = false): ScrapeOut {
        if (blocked) return ScrapeOut(false, true)
        if (!result.found) return ScrapeOut(false, false)
        val fields = result.fields.toMutableMap()
        if (fields["snippet"].isNullOrBlank() && result.rawSnippets.isNotEmpty()) {
            fields["snippet"] = result.rawSnippets.joinToString(" | ").take(600)
        }
        if (fields["title"].isNullOrBlank()) fields["title"] = result.source
        return ScrapeOut(true, false, fields)
    }

    private suspend fun scrapeThatsThemPerson(query: String): ScrapeOut =
        scrapeResultToOut(scrapeThatsThem(query, fastHttpClient))

    private suspend fun scrapeFastPeopleSearchPerson(query: String): ScrapeOut {
        val parts = query.trim().split("\\s+".toRegex())
        val first = parts.firstOrNull().orEmpty()
        val last = if (parts.size > 1) parts.last() else ""
        if (first.isBlank() || last.isBlank()) return ScrapeOut(false, false)
        return scrapeResultToOut(scrapeFastPeopleSearch(first, last, fastHttpClient))
    }

    private suspend fun scrapeUSPhoneBook(query: String, city: String, state: String, byPhone: Boolean = false): ScrapeOut {
        return try {
            if (byPhone) {
                val digits = query.replace(Regex("[^0-9]"), "").takeLast(10)
                if (digits.length < 10) return ScrapeOut(false, false)
                return scrapePhoneReversePage(
                    "https://www.usphonebook.com/$digits",
                    query
                )
            }
            val slug = query.trim().replace(Regex("\\s+"), "-").lowercase()
            val loc = listOf(city, state).filter { it.isNotBlank() }.joinToString("-").lowercase()
            val path = if (loc.isNotBlank()) "$slug/$loc" else slug
            val url = "https://www.usphonebook.com/name/$path"
            tryScrapeUrl(
                url, query,
                SubjectProfile(name = query, city = city, state = state, phone = "")
            )
        } catch (_: Exception) {
            ScrapeOut(false, false)
        }
    }

    private fun scrapePhoneReversePage(url: String, phone: String): ScrapeOut {
        val digits = phone.replace(Regex("[^0-9]"), "").takeLast(10)
        if (digits.length < 10) return ScrapeOut(false, false)
        val profile = SubjectProfile(phone = phone)
        val out = tryScrapeUrl(url, digits, profile)
        if (!out.found) return out
        val fields = out.fields.toMutableMap()
        val text = buildString {
            append(fields["title"].orEmpty())
            append(" ")
            append(fields["snippet"].orEmpty())
        }
        val names = ReportMetadataSync.extractPersonNames(text)
        if (names.isNotEmpty()) {
            fields["name"] = names.first()
            fields["names"] = names.joinToString(", ")
        }
        Regex("""(?i)\d{1,5}\s+[A-Za-z0-9][A-Za-z0-9\s]{1,35}\s+(?:St\.?|Ave\.?|Blvd\.?|Dr\.?|Rd\.?|Ln\.?|Ct\.?|Way|Pl\.?|Pkwy|Road|Street|Avenue|Boulevard|Drive|Lane|Court)[,\s]+(?:[A-Za-z\s]{2,25}[,\s]+)?[A-Z]{2}[\s,]+\d{5}(?:-\d{4})?""")
            .find(text)?.value?.replace(Regex("\\s+"), " ")?.trim()
            ?.takeIf { it.length in 15..120 }
            ?.let { fields["addresses"] = it }
        return ScrapeOut(true, false, fields)
    }

    private fun scrapeNameDemographics(firstName: String): ScrapeOut {
        return try {
            val encoded = URLEncoder.encode(firstName.trim(), "UTF-8")
            val genderReq = Request.Builder()
                .url("https://api.genderize.io/?name=$encoded")
                .header("User-Agent", SEC_USER_AGENT)
                .build()
            val genderResp = fastHttpClient.newCall(genderReq).execute()
            val genderBody = genderResp.body?.string() ?: ""
            genderResp.close()

            val agifyReq = Request.Builder()
                .url("https://api.agify.io/?name=$encoded")
                .header("User-Agent", SEC_USER_AGENT)
                .build()
            val agifyResp = fastHttpClient.newCall(agifyReq).execute()
            val agifyBody = agifyResp.body?.string() ?: ""
            agifyResp.close()

            val fields = mutableMapOf<String, String>()
            if (genderBody.startsWith("{")) {
                val g = JSONObject(genderBody)
                g.optString("gender").takeIf { it.isNotBlank() }?.let { gender ->
                    val prob = g.optDouble("probability", 0.0)
                    fields["gender"] = "$gender (${(prob * 100).toInt()}%)"
                }
            }
            if (agifyBody.startsWith("{")) {
                val a = JSONObject(agifyBody)
                a.optInt("age", 0).takeIf { it in 1..120 }?.let { fields["estimated_age"] = it.toString() }
            }
            if (fields.isEmpty()) ScrapeOut(false, false)
            else ScrapeOut(true, false, fields + mapOf(
                "title" to "Name demographics",
                "snippet" to fields.entries.joinToString(" | ") { "${it.key}: ${it.value}" }
            ))
        } catch (_: Exception) {
            ScrapeOut(false, false)
        }
    }

    private fun scrapeIpWho(ip: String): ScrapeOut {
        return try {
            val req = Request.Builder()
                .url("https://ipwho.is/$ip")
                .header("User-Agent", SEC_USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) return ScrapeOut(false, false)
            val fields = mutableMapOf<String, String>()
            json.optString("ip").takeIf { it.isNotBlank() }?.let { fields["ip"] = it }
            json.optString("city").takeIf { it.isNotBlank() }?.let { fields["city"] = it }
            json.optString("region").takeIf { it.isNotBlank() }?.let { fields["region"] = it }
            json.optString("country").takeIf { it.isNotBlank() }?.let { fields["country"] = it }
            json.optString("isp").takeIf { it.isNotBlank() }?.let { fields["isp"] = it }
            json.optString("org").takeIf { it.isNotBlank() }?.let { fields["org"] = it }
            json.optString("timezone").takeIf { it.isNotBlank() }?.let { fields["timezone"] = it }
            json.optJSONObject("connection")?.optString("asn")?.takeIf { it.isNotBlank() }
                ?.let { fields["asn"] = it }
            ScrapeOut(true, false, fields + mapOf(
                "title" to "ipwho.is",
                "snippet" to listOfNotNull(fields["city"], fields["region"], fields["country"], fields["isp"])
                    .joinToString(", ")
            ))
        } catch (_: Exception) {
            ScrapeOut(false, false)
        }
    }

    private data class ScrapeOut(
        val found: Boolean,
        val blocked: Boolean,
        val fields: Map<String, String> = emptyMap(),
        val persons: List<PersonRecord> = emptyList(),
        val skippedBlocked: Boolean = false
    )

    private data class DdgResult(val title: String, val snippet: String, val url: String)

    private data class ExtractedData(
        val phones: List<String> = emptyList(),
        val emails: List<String> = emptyList(),
        val addresses: List<String> = emptyList(),
        val ages: List<String> = emptyList(),
        val relatives: List<String> = emptyList(),
        val names: List<String> = emptyList(),
        val socialUrls: List<String> = emptyList(),
        val profileUrls: List<String> = emptyList(),
        val snippets: List<String> = emptyList()
    )

    private fun parseContactList(raw: String): List<String> =
        raw.split(",", "|", "\n").map { it.trim() }.filter { it.isNotBlank() }

    private fun subjectProfileFromMetadata(metadata: Map<String, String>, fallbackName: String = ""): SubjectProfile =
        SubjectProfile.fromFields(
            mapOf(
                "name" to (metadata["person_name"]?.takeIf { it.isNotBlank() } ?: fallbackName),
                "city" to (metadata["person_city"] ?: ""),
                "state" to (metadata["person_state"] ?: ""),
                "phone" to (metadata["person_phone"] ?: metadata["field_phone"] ?: ""),
                "email" to (metadata["person_email"] ?: metadata["field_email"] ?: "")
            )
        )

    private fun filterScrapeContactFields(
        fields: Map<String, String>,
        profile: SubjectProfile
    ): Map<String, String> {
        val context = fields["snippet"] ?: fields["title"] ?: ""
        val out = fields.toMutableMap()
        out["phones"]?.let { raw ->
            val validated = SubjectFilter.filterPhones(parseContactList(raw), context, profile)
            if (validated.isEmpty()) out.remove("phones") else out["phones"] = validated.joinToString(", ")
        }
        out["emails"]?.let { raw ->
            val validated = SubjectFilter.filterEmails(parseContactList(raw), context, profile)
            if (validated.isEmpty()) out.remove("emails") else out["emails"] = validated.joinToString(", ")
        }
        listOf("addresses", "locations").forEach { key ->
            out[key]?.let { raw ->
                val validated = SubjectFilter.filterAddresses(parseContactList(raw), profile)
                if (validated.isEmpty()) out.remove(key) else out[key] = validated.joinToString(" | ")
            }
        }
        return out
    }

    private fun tryScrapeUrl(
        url: String,
        expectedQuery: String? = null,
        subjectProfile: SubjectProfile? = null
    ): ScrapeOut {
        val domain = BlockedSourceCache.domainFromUrl(url)
        if (blockedSourceCache.isBlocked(domain)) {
            return ScrapeOut(found = false, blocked = true, skippedBlocked = true)
        }
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
                || body.contains("Enable JavaScript")
                || body.contains("challenge-platform", ignoreCase = true)) {
                blockedSourceCache.markBlocked(domain, "http_$code")
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

            if (!expectedQuery.isNullOrBlank()) {
                val domainCheck = expectedQuery.trim().lowercase()
                val phoneDigits = expectedQuery.replace(Regex("[^0-9]"), "")
                when {
                    isLikelyDomain(domainCheck) -> {
                        if (!text.lowercase().contains(domainCheck) && !url.lowercase().contains(domainCheck)) {
                            return ScrapeOut(false, false)
                        }
                    }
                    phoneDigits.length >= 10 -> {
                        val last10 = phoneDigits.takeLast(10)
                        if (!text.replace(Regex("[^0-9]"), "").contains(last10)) {
                            return ScrapeOut(false, false)
                        }
                    }
                    !textMatchesQuery(text, expectedQuery) -> return ScrapeOut(false, false)
                }
            }

            val fields = mutableMapOf<String, String>()
            fields["title"] = doc.title().take(120)
            fields["snippet"] = text.take(600)

            val ogImage = doc.selectFirst("meta[property=og:image]")?.attr("content")
                ?: doc.selectFirst("meta[name=twitter:image]")?.attr("content")
                ?: doc.selectFirst("meta[name=twitter:image:src]")?.attr("content")
                ?: doc.selectFirst("link[rel=image_src]")?.attr("href")
            if (!ogImage.isNullOrBlank() && ogImage.startsWith("http")) fields["image_url"] = ogImage

            val profile = subjectProfile ?: SubjectProfile(
                name = expectedQuery?.takeIf { !isLikelyPhone(it) }?.trim().orEmpty(),
                city = "",
                state = "",
                phone = expectedQuery?.takeIf { isLikelyPhone(it) }?.trim().orEmpty()
            )
            val phones = Regex("""\(?(\d{3})\)?[.\-\s](\d{3})[.\-\s](\d{4})""").findAll(text)
                .map { "(${it.groupValues[1]}) ${it.groupValues[2]}-${it.groupValues[3]}" }
                .filter { p ->
                    !SubjectFilter.isTollFreeOrGeneric(p) &&
                        SubjectFilter.shouldAcceptPersonPhone(
                            p, text, profile.phone, profile.name, profile.city, profile.state, "", url, false
                        )
                }
                .distinct().take(5).toList()
            val emails = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""").findAll(text)
                .map { it.value.lowercase() }
                .filter { !it.contains("example") && !it.contains("domain") }
                .filter { SubjectFilter.validateEmail(it, text, profile) }
                .distinct().take(3).toList()

            if (phones.isNotEmpty()) fields["phones"] = phones.joinToString(", ")
            if (emails.isNotEmpty()) fields["emails"] = emails.joinToString(", ")

            ScrapeOut(true, false, fields)
        } catch (e: Exception) {
            val isTimeout = e is java.net.SocketTimeoutException
                || e is java.io.InterruptedIOException
                || e.message?.contains("timeout", ignoreCase = true) == true
            if (isTimeout && domain.isNotBlank()) {
                blockedSourceCache.markBlocked(domain, "timeout")
                ScrapeOut(false, true)
            } else {
                ScrapeOut(false, false)
            }
        }
    }

    private fun extractDdgRedirect(href: String): String? {
        if (href.isBlank()) return null
        if (!href.contains("uddg=")) return href.takeIf { it.startsWith("http") }
        return Regex("""uddg=([^&]+)""").find(href)?.groupValues?.get(1)
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
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
                val linkEl = el.selectFirst(".result__a, .result__title a")
                val title = linkEl?.text()?.trim() ?: return@mapNotNull null
                val snippet = el.selectFirst(".result__snippet, .result-snippet")?.text()?.trim() ?: ""
                val href = linkEl.attr("href")
                val url = extractDdgRedirect(href)
                    ?: el.selectFirst(".result__url, .result-url")?.text()?.trim().orEmpty()
                if (title.isBlank()) null else DdgResult(title, snippet, url)
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun extractDataFromDdgResults(
        results: List<DdgResult>,
        subject: SubjectProfile = SubjectProfile()
    ): ExtractedData {
        val phoneRegex = Regex("""\(?(\d{3})\)?[.\-\s](\d{3})[.\-\s](\d{4})""")
        val emailRegex = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
        val ageRegex = Regex("""(?i)\bage[:\s]+(\d{2,3})\b|\b(\d{2,3})\s*years?\s*old\b|\baged?\s+(\d{2,3})\b""")
        val tollfree = setOf("800", "888", "877", "866", "855", "844", "833")
        val socialDomains = setOf("linkedin.com", "facebook.com", "twitter.com", "instagram.com", "tiktok.com", "youtube.com", "pinterest.com", "reddit.com")
        val peopleDomains = setOf("fastpeoplesearch.com", "whitepages.com", "spokeo.com", "peoplefinder.com", "beenverified.com", "truepeoplesearch.com", "radaris.com", "thatsthem.com", "zabasearch.com", "411.com", "intelius.com", "truthfinder.com", "familytreenow.com", "usphonebook.com", "addresses.com")
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        val ages = mutableListOf<String>()
        val relatives = mutableListOf<String>()
        val names = mutableListOf<String>()
        val socialUrls = mutableListOf<String>()
        val profileUrls = mutableListOf<String>()
        val snippets = mutableListOf<String>()
        val addresses = mutableListOf<String>()
        for (r in results) {
            val text = "${r.title} ${r.snippet}"
            if (!SubjectFilter.matchesSubject(text, subject) && subject.phone.isBlank()) continue
            if (subject.phone.isNotBlank() || peopleDomains.any { r.url.lowercase().contains(it) }) {
                ReportMetadataSync.extractPersonNames(r.title).forEach { names.add(it) }
            }
            phoneRegex.findAll(text).forEach { m ->
                val area = m.groupValues[1]
                val formatted = "(${area}) ${m.groupValues[2]}-${m.groupValues[3]}"
                if (area !in tollfree && SubjectFilter.shouldAcceptPersonPhone(
                        formatted, text, subject.phone, subject.name, subject.city, subject.state, "ddg", r.url, false
                    )
                ) phones.add(formatted)
            }
            emailRegex.findAll(text).map { it.value.lowercase() }
                .filter { !it.contains("example") && !it.endsWith(".png") && !it.endsWith(".jpg") }
                .filter { SubjectFilter.validateEmail(it, text, subject) }
                .forEach { emails.add(it) }
            Regex("""(?i)\d{1,5}\s+[A-Za-z0-9][A-Za-z0-9\s]{1,35}\s+(?:St\.?|Ave\.?|Blvd\.?|Dr\.?|Rd\.?|Ln\.?|Ct\.?|Way|Pl\.?|Pkwy|Road|Street|Avenue|Boulevard|Drive|Lane|Court)[,\s]+(?:[A-Za-z\s]{2,25}[,\s]+)?[A-Z]{2}[\s,]+\d{5}(?:-\d{4})?""")
                .find(text)?.value?.replace(Regex("\\s+"), " ")?.trim()
                ?.takeIf { it.length in 15..120 && SubjectFilter.validateAddress(it, subject) }
                ?.let { addresses.add(it) }
            Regex("""(?i)(?:relatives?|associates?|related\s+to|family)[:\s]+([^.\n]{5,80})""").find(text)?.groupValues?.get(1)?.split(",")?.forEach { rel ->
                val name = rel.trim().take(40)
                if (name.length > 3 && name.contains(" ")) relatives.add(name)
            }
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
        }
        return ExtractedData(
            phones = phones.distinct().take(10),
            emails = emails.distinct().take(5),
            addresses = addresses.distinct().take(5),
            ages = ages.distinct().take(3),
            relatives = relatives.distinct().take(10),
            names = names.distinct().take(8),
            socialUrls = socialUrls.distinct().take(8),
            profileUrls = profileUrls.distinct().take(8),
            snippets = snippets.take(15)
        )
    }

    private fun buildPersonQueries(
        name: String,
        city: String,
        state: String,
        phone: String = "",
        email: String = "",
        username: String = "",
        phase: SearchPhase = SearchPhase.DEEP_INVESTIGATION,
        activeCategories: Set<String>? = null
    ): List<Pair<String, String>> {
        val loc = listOf(city, state).filter { it.isNotBlank() }.joinToString(" ")
        val allowedLabels = SubjectSearchOrchestrator.ddgLabelsFiltered(phase, activeCategories)
        if (name.isBlank() && phone.isNotBlank()) {
            val phoneQueries = mutableListOf(
                "Phone" to "\"$phone\" owner name reverse lookup",
                "General" to "\"$phone\" who called caller id",
                "Whitepages" to "site:whitepages.com \"$phone\"",
                "TruePeopleSearch" to "site:truepeoplesearch.com \"$phone\"",
                "BeenVerified" to "site:beenverified.com \"$phone\"",
                "Spokeo" to "site:spokeo.com \"$phone\"",
                "News" to "\"$phone\" scam fraud report"
            )
            if (email.isNotBlank()) phoneQueries.add("EmailCrossRef" to "\"$email\" \"$phone\"")
            if (username.isNotBlank()) phoneQueries.add("UsernameCrossRef" to "\"$username\" \"$phone\"")
            return phoneQueries.filter { (label, _) -> label in allowedLabels }
        }
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
        return queries.filter { (label, _) -> label in allowedLabels }
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
                    val titleMatches = title.isBlank() || textMatchesQuery("$title $extract", name)
                    if (titleMatches) {
                        result["wikipedia_extract"] = extract.take(1200)
                        result["wikipedia_title"] = title
                        if (pageUrl.isNotBlank()) result["wikipedia_url"] = pageUrl
                    }
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

    private fun scrapeGoogleNews(query: String, matchQuery: String? = null): ScrapeOut {
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
                if (!matchQuery.isNullOrBlank() && !textMatchesQuery(title, matchQuery)) return@mapNotNull null
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

    private suspend fun scrapeProxyNova(email: String): ScrapeOut =
        scrapeResultToOut(scrapeProxyNovaWeb(email, fastHttpClient))

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
            val viaTor = torHttpClientOrNull() != null
            val client = torHttpClientOrNull() ?: fastHttpClient
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = if (viaTor) {
                "http://juhanurmihxlp77nkq76iba5ek5w6frgijb5lvlxm3qmqyestw57k5ad.onion/search/?q=$encoded"
            } else {
                "https://ahmia.fi/search/?q=$encoded"
            }
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank()) {
                return ScrapeOut(false, false, mapOf("count" to "0", "via_tor" to viaTor.toString()))
            }
            val doc = Jsoup.parse(body)
            val items = doc.select("li.result, ol.results li, #results li, .result").take(10)
            val titles = mutableListOf<String>()
            val urls = mutableListOf<String>()
            val descs = mutableListOf<String>()
            for (item in items) {
                val linkEl = item.selectFirst("h4 a[href], a[href*=.onion], a[href]")
                val title = linkEl?.text()?.trim().orEmpty()
                var href = linkEl?.attr("href")?.trim().orEmpty()
                if (href.startsWith("/")) href = "https://ahmia.fi$href"
                val desc = item.selectFirst("p, .description, span")?.text()?.trim().orEmpty()
                if (title.isBlank() && href.isBlank()) continue
                titles.add(title.ifBlank { href })
                urls.add(href)
                descs.add(desc.take(300))
            }
            val count = titles.size
            val fields = mutableMapOf(
                "count" to count.toString(),
                "via_tor" to viaTor.toString(),
                "title" to "Ahmia: $count indexed hit${if (count != 1) "s" else ""}"
            )
            if (count > 0) {
                fields["snippet"] = titles.zip(descs) { t, d ->
                    if (d.isNotBlank()) "$t — $d" else t
                }.joinToString("\n").take(600)
                fields["titles"] = titles.joinToString("\n")
                fields["urls"] = urls.joinToString("\n")
                fields["descs"] = descs.joinToString("\n---\n")
                return ScrapeOut(true, false, fields)
            }
            ScrapeOut(false, false, fields)
        } catch (_: Exception) {
            ScrapeOut(false, false, mapOf("count" to "0", "via_tor" to TorBootstrapManager.isPortOpen().toString()))
        }
    }

    private fun scrapeWaybackCdx(domain: String): ScrapeOut {
        return try {
            val encoded = URLEncoder.encode(domain, "UTF-8")
            val url = "https://web.archive.org/cdx/search/cdx?url=$encoded/*&output=text&limit=20&fl=original,timestamp&collapse=urlkey"
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

    private fun scrapeLibPhoneNumber(phone: String): ScrapeOut {
        return try {
            val digits = phone.replace(Regex("[^0-9+]"), "")
            if (digits.replace("+", "").length < 7) return ScrapeOut(false, false)
            val e164 = if (digits.startsWith("+")) digits else "+$digits"
            val encoded = URLEncoder.encode(e164, "UTF-8")
            val req = Request.Builder()
                .url("https://libphonenumberapi.com/api/phone-numbers/$encoded")
                .header("User-Agent", "6Degrees OSINT/1.0")
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            val isValid = json.optBoolean("is_valid", false)
            val isPossible = json.optBoolean("is_possible", false)
            if (!isValid && !isPossible) return ScrapeOut(false, false)
            val fields = mutableMapOf<String, String>()
            fields["title"] = "libphonenumber: $phone"
            fields["valid"] = isValid.toString()
            fields["possible"] = isPossible.toString()
            json.optString("country", "").takeIf { it.isNotBlank() }?.let { fields["country"] = it }
            json.optString("type", "").takeIf { it.isNotBlank() }?.let { fields["line_type"] = it }
            json.optString("carrier", "").takeIf { it.isNotBlank() }?.let { fields["carrier"] = it }
            json.optString("geo_name", "").takeIf { it.isNotBlank() }?.let { fields["location"] = it }
            json.optString("timezone", "").takeIf { it.isNotBlank() }?.let { fields["timezone"] = it }
            json.optJSONObject("formats")?.optString("international", "")?.takeIf { it.isNotBlank() }
                ?.let { fields["intl"] = it }
            fields["snippet"] = buildString {
                append(if (isValid) "Valid" else "Possible")
                fields["line_type"]?.let { append(" $it") }
                fields["country"]?.let { append(" · $it") }
                fields["location"]?.let { append(" · $it") }
                fields["carrier"]?.let { append(" · $it") }
            }.trim()
            ScrapeOut(true, false, fields)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeCallTracer(phone: String): ScrapeOut {
        return try {
            val digits = phone.replace(Regex("[^0-9]"), "")
            if (digits.length < 7) return ScrapeOut(false, false)
            val req = Request.Builder()
                .url("https://calltracer.io/api/lookup/$digits")
                .header("User-Agent", "6Degrees OSINT/1.0")
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            if (!json.optBoolean("is_valid", false)) return ScrapeOut(false, false)
            val fields = mutableMapOf<String, String>()
            fields["title"] = "CallTracer: $phone"
            json.optString("country", "").takeIf { it.isNotBlank() }?.let { fields["country"] = it }
            json.optString("number_type", "").takeIf { it.isNotBlank() }?.let { fields["line_type"] = it }
            json.optString("carrier", "").takeIf { it.isNotBlank() }?.let { fields["carrier"] = it }
            json.optString("location", "").takeIf { it.isNotBlank() }?.let { fields["location"] = it }
            json.optString("international", "").takeIf { it.isNotBlank() }?.let { fields["intl"] = it }
            json.optJSONObject("reports")?.let { reports ->
                reports.optInt("total", 0).takeIf { it > 0 }?.let { fields["spam_reports"] = it.toString() }
                reports.optInt("spam_score", 0).takeIf { it > 0 }?.let { fields["spam_score"] = it.toString() }
            }
            fields["snippet"] = buildString {
                append("Valid")
                fields["line_type"]?.let { append(" $it") }
                fields["location"]?.let { append(" · $it") }
                fields["spam_score"]?.let { append(" · spam score $it") }
            }.trim()
            ScrapeOut(true, false, fields)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrape800Notes(phone: String): ScrapeOut {
        return try {
            val digits = phone.replace(Regex("[^0-9]"), "")
            if (digits.length < 10) return ScrapeOut(false, false)
            val formatted = "${digits.substring(0,3)}-${digits.substring(3,6)}-${digits.substring(6,10)}"
            val url = "https://800notes.com/Phone.aspx/$formatted"
            tryScrapeUrl(url, digits.takeLast(10))
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

    private fun scrapeKickboxDisposable(email: String): ScrapeOut {
        return try {
            val encoded = URLEncoder.encode(email.trim().lowercase(), "UTF-8")
            val req = Request.Builder()
                .url("https://open.kickbox.com/v1/disposable/$encoded")
                .header("User-Agent", "6Degrees OSINT/1.0")
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            val disposable = json.optBoolean("disposable", false)
            val didYouMean = json.optString("did_you_mean", "").takeIf { it.isNotBlank() }
            ScrapeOut(true, false, buildMap {
                put("title", "Kickbox: $email")
                put("snippet", if (disposable) "Disposable/temporary email provider" else "Not a known disposable provider")
                put("disposable", disposable.toString())
                didYouMean?.let { put("did_you_mean", it) }
            })
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

    private fun scrapeNumverify(phone: String, apiKey: String): ScrapeOut {
        return try {
            val digits = phone.replace(Regex("[^0-9+]"), "")
            if (digits.length < 7) return ScrapeOut(false, false)
            val req = Request.Builder()
                .url("http://apilayer.net/api/validate?access_key=${encode(apiKey)}&number=${encode(digits)}")
                .header("User-Agent", SEC_USER_AGENT)
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val code = resp.code
            resp.close()
            if (code == 429) return ScrapeOut(false, true)
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            if (json.has("error")) return ScrapeOut(false, false)
            val valid = json.optBoolean("valid", false)
            val country = json.optString("country_name", "")
            val carrier = json.optString("carrier", "")
            val lineType = json.optString("line_type", "")
            val location = json.optString("location", "")
            val intl = json.optString("international_format", "")
            val fields = mutableMapOf(
                "title" to "Numverify: ${if (valid) "Valid" else "Invalid"}",
                "snippet" to listOf(country, carrier, lineType).filter { it.isNotBlank() }.joinToString(" · "),
                "valid" to valid.toString(),
                "country" to country,
                "carrier" to carrier,
                "line_type" to lineType,
                "location" to location,
                "intl" to intl
            )
            apiKeys.recordUsage("numverify")
            ScrapeOut(true, false, fields)
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeAbuseIpDb(ip: String, apiKey: String): ScrapeOut {
        return try {
            val req = Request.Builder()
                .url("https://api.abuseipdb.com/api/v2/check?ipAddress=${encode(ip.trim())}&maxAgeInDays=90&verbose")
                .header("Key", apiKey)
                .header("Accept", "application/json")
                .header("User-Agent", SEC_USER_AGENT)
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val code = resp.code
            resp.close()
            if (code == 429) return ScrapeOut(false, true)
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val data = JSONObject(body).optJSONObject("data") ?: return ScrapeOut(false, false)
            val score = data.optInt("abuseConfidenceScore", 0)
            val reports = data.optInt("totalReports", 0)
            val country = data.optString("countryCode", "")
            ScrapeOut(true, false, mapOf(
                "title" to "AbuseIPDB: $ip",
                "snippet" to "Abuse score: $score% · Reports: $reports${if (country.isNotBlank()) " · $country" else ""}",
                "score" to score.toString(),
                "reports" to reports.toString(),
                "country" to country
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeVirusTotal(target: String, apiKey: String): ScrapeOut {
        return try {
            val isIp = isIpAddress(target)
            val path = if (isIp) "ip_addresses/${target.trim()}" else "domains/${target.trim().lowercase()}"
            val req = Request.Builder()
                .url("https://www.virustotal.com/api/v3/$path")
                .header("x-apikey", apiKey)
                .header("Accept", "application/json")
                .header("User-Agent", SEC_USER_AGENT)
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val code = resp.code
            resp.close()
            if (code == 429) return ScrapeOut(false, true)
            if (code == 404 || body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val attrs = JSONObject(body).optJSONObject("data")?.optJSONObject("attributes") ?: return ScrapeOut(false, false)
            val stats = attrs.optJSONObject("last_analysis_stats")
            val malicious = stats?.optInt("malicious", 0) ?: 0
            val harmless = stats?.optInt("harmless", 0) ?: 0
            val suspicious = stats?.optInt("suspicious", 0) ?: 0
            val reputation = attrs.optInt("reputation", 0)
            val country = attrs.optString("country", "")
            val asOwner = attrs.optString("as_owner", "")
            ScrapeOut(true, false, mapOf(
                "title" to "VirusTotal: $target",
                "snippet" to "Malicious: $malicious · Harmless: $harmless · Suspicious: $suspicious",
                "malicious" to malicious.toString(),
                "harmless" to harmless.toString(),
                "suspicious" to suspicious.toString(),
                "reputation" to reputation.toString(),
                "country" to country,
                "as_owner" to asOwner
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeUrlScan(domain: String, apiKey: String): ScrapeOut {
        return try {
            val req = Request.Builder()
                .url("https://urlscan.io/api/v1/search/?q=domain:${encode(domain.trim().lowercase())}&size=100")
                .header("API-Key", apiKey)
                .header("Accept", "application/json")
                .header("User-Agent", SEC_USER_AGENT)
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val code = resp.code
            resp.close()
            if (code == 429) return ScrapeOut(false, true)
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val results = JSONObject(body).optJSONArray("results") ?: return ScrapeOut(false, false)
            if (results.length() == 0) return ScrapeOut(false, false)
            var malicious = 0
            val ips = mutableSetOf<String>()
            for (i in 0 until results.length()) {
                val page = results.optJSONObject(i)?.optJSONObject("page") ?: continue
                if (page.optBoolean("malicious", false)) malicious++
                page.optString("ip").takeIf { it.isNotBlank() }?.let { ips.add(it) }
            }
            ScrapeOut(true, false, mapOf(
                "title" to "URLScan: $domain",
                "snippet" to "${results.length()} scans · $malicious flagged",
                "total_scans" to results.length().toString(),
                "malicious_scans" to malicious.toString(),
                "ips" to ips.take(5).joinToString(", ")
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeSecEdgar(query: String, forms: String? = null): ScrapeOut {
        return try {
            val phrase = URLEncoder.encode("\"${query.trim()}\"", "UTF-8")
            val urlBuilder = StringBuilder("https://efts.sec.gov/LATEST/search-index?q=$phrase&from=0&size=10")
            if (!forms.isNullOrBlank()) urlBuilder.append("&forms=${encode(forms)}")
            val req = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", SEC_USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val code = resp.code
            resp.close()
            if (code == 429) return ScrapeOut(false, true)
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val hitsObj = JSONObject(body).optJSONObject("hits") ?: return ScrapeOut(false, false)
            val total = hitsObj.optJSONObject("total")?.optInt("value", 0) ?: 0
            if (total == 0) return ScrapeOut(false, false)
            val hits = hitsObj.optJSONArray("hits") ?: return ScrapeOut(false, false)
            val entities = mutableListOf<String>()
            val formTypes = mutableSetOf<String>()
            for (i in 0 until minOf(10, hits.length())) {
                val source = hits.optJSONObject(i)?.optJSONObject("_source") ?: continue
                val name = source.optJSONArray("display_names")?.optString(0)
                    ?: source.optString("entity_name", "")
                if (name.isNotBlank()) entities.add(name)
                source.optString("file_type").takeIf { it.isNotBlank() }?.let { formTypes.add(it) }
            }
            apiKeys.recordUsage("sec_edgar")
            ScrapeOut(true, false, mapOf(
                "title" to "SEC EDGAR: $total filing(s)",
                "snippet" to entities.take(3).joinToString("; "),
                "total_hits" to total.toString(),
                "entities" to entities.distinct().take(5).joinToString(", "),
                "form_types" to formTypes.joinToString(", ")
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeWikidata(query: String): ScrapeOut {
        return try {
            val req = Request.Builder()
                .url("https://www.wikidata.org/w/api.php?action=wbsearchentities&search=${encode(query.trim())}&language=en&format=json&limit=3")
                .header("User-Agent", SEC_USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val search = JSONObject(body).optJSONArray("search") ?: return ScrapeOut(false, false)
            if (search.length() == 0) return ScrapeOut(false, false)
            val labels = mutableListOf<String>()
            val descriptions = mutableListOf<String>()
            val ids = mutableListOf<String>()
            for (i in 0 until search.length()) {
                val item = search.optJSONObject(i) ?: continue
                val label = item.optString("label")
                if (label.isNotBlank() && !textMatchesQuery(label, query)) continue
                label.takeIf { it.isNotBlank() }?.let { labels.add(it) }
                item.optString("description").takeIf { it.isNotBlank() }?.let { descriptions.add(it) }
                item.optString("id").takeIf { it.isNotBlank() }?.let { ids.add(it) }
            }
            if (descriptions.isEmpty() && labels.isEmpty()) return ScrapeOut(false, false)
            val link = ids.firstOrNull()?.let { "https://www.wikidata.org/wiki/$it" } ?: ""
            val snippet: String = descriptions.firstOrNull() ?: labels.firstOrNull() ?: ""
            ScrapeOut(true, false, mapOf(
                "title" to "Wikidata: ${labels.firstOrNull() ?: query}",
                "snippet" to snippet,
                "descriptions" to descriptions.joinToString(" | "),
                "labels" to labels.joinToString(", "),
                "link" to link
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun normalizeScraperFieldAliases(sourceKey: String, metadata: ConcurrentHashMap<String, String>) {
        val prefix = "${sourceKey}_"
        metadata["${prefix}name"]?.takeIf { it.isNotBlank() }?.let { name ->
            val existing = metadata["${prefix}names"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
            if (name !in existing) {
                metadata["${prefix}names"] = (existing + name).joinToString(", ")
            }
        }
        metadata["${prefix}addresses"]?.takeIf { it.isNotBlank() }?.let { addrs ->
            if (metadata["${prefix}locations"].isNullOrBlank()) metadata["${prefix}locations"] = addrs
        }
        if (sourceKey == "demographics") {
            metadata["${prefix}estimated_age"]?.takeIf { it.isNotBlank() }?.let {
                if (metadata["demographics_age_estimate"].isNullOrBlank()) metadata["demographics_age_estimate"] = it
            }
            metadata["${prefix}gender"]?.takeIf { it.isNotBlank() }?.let {
                if (metadata["demographics_gender"].isNullOrBlank()) metadata["demographics_gender"] = it
            }
            // Nationality from name-only APIs is unreliable — do not promote to report metadata.
        }
    }

    private fun appendPersonRecordFromScrape(
        sourceKey: String,
        primaryQuery: String,
        metadata: Map<String, String>,
        records: MutableList<PersonRecord>
    ) {
        val prefix = "${sourceKey}_"
        val addrs = metadata["${prefix}locations"] ?: metadata["${prefix}addresses"] ?: ""
        val phones = metadata["${prefix}phones"] ?: ""
        val ages = metadata["${prefix}ages"] ?: metadata["${prefix}age"] ?: ""
        val relatives = metadata["${prefix}relatives"] ?: ""
        if (addrs.isBlank() && phones.isBlank() && ages.isBlank()) return
        records.add(
            PersonRecord(
                name = metadata["${prefix}name"]?.takeIf { it.isNotBlank() } ?: primaryQuery,
                age = ages.split(" | ", ",").firstOrNull()?.trim().orEmpty(),
                location = addrs.split(" | ", "\n").firstOrNull()?.trim().orEmpty(),
                phones = phones.split(" | ", ",").map { it.trim() }.filter { it.isNotBlank() },
                address = addrs.split(" | ", "\n").firstOrNull()?.trim().orEmpty(),
                relatives = relatives.split(",").map { it.trim() }.filter { it.length > 3 },
                source = sourceKey.uppercase()
            )
        )
    }

    private fun applyPiplPersonToMetadata(
        person: PiplPerson,
        metadata: ConcurrentHashMap<String, String>,
        profile: SubjectProfile
    ) {
        val displayName = person.names?.firstOrNull()?.display ?: return
        if (profile.name.isNotBlank() && !SubjectFilter.textMatchesQuery(displayName, profile.name)) return
        metadata["pipl_found"] = "true"
        metadata["pipl_name"] = displayName
        val context = buildString {
            append(displayName)
            person.addresses?.forEach { append(" "); append(it.display ?: "") }
            person.jobs?.forEach { append(" "); append(it.display ?: "") }
        }
        person.emails?.mapNotNull { it.address }
            ?.filter { SubjectFilter.validateEmail(it, context, profile) }
            ?.take(3)?.joinToString(", ")
            ?.let { metadata["pipl_emails"] = it }
        person.phones?.mapNotNull { it.display ?: it.number }
            ?.filter {
                SubjectFilter.shouldAcceptPersonPhone(
                    it, context, profile.phone, profile.name, profile.city, profile.state, "pipl", "", true
                )
            }
            ?.take(3)?.joinToString(", ")
            ?.let { metadata["pipl_phones"] = it }
        person.jobs?.mapNotNull { job ->
            job.display ?: listOfNotNull(job.title, job.organization).joinToString(" at ").takeIf { it.isNotBlank() }
        }?.take(5)?.joinToString("\n")?.let { metadata["pipl_employment"] = it }
        person.addresses?.mapNotNull { addr ->
            addr.display ?: listOfNotNull(addr.street, addr.city, addr.state, addr.zipCode)
                .filter { !it.isNullOrBlank() }.joinToString(", ").takeIf { it.isNotBlank() }
        }?.take(4)?.joinToString(" | ")?.let { metadata["pipl_addresses"] = it }
        person.relationships?.mapNotNull { rel -> rel.names?.firstOrNull()?.display }
            ?.take(10)?.joinToString(",")?.let { metadata["pipl_relatives"] = it }
        person.dob?.takeIf { it.isNotBlank() }?.let { metadata["pipl_dob"] = it }
        person.gender?.takeIf { it.isNotBlank() }?.let { metadata["pipl_gender"] = it }
        person.urls?.mapNotNull { it.url }?.take(6)?.joinToString("\n")?.let { metadata["pipl_socials"] = it }
    }

    private fun applyPdlPersonToMetadata(
        person: PdlPerson,
        metadata: ConcurrentHashMap<String, String>,
        profile: SubjectProfile
    ) {
        val fullName = person.fullName ?: return
        if (profile.name.isNotBlank() && !SubjectFilter.textMatchesQuery(fullName, profile.name)) return
        metadata["pdl_found"] = "true"
        metadata["pdl_name"] = fullName
        val context = buildString {
            append(fullName)
            append(" "); append(person.locationName ?: "")
            append(" "); append(person.jobCompanyName ?: "")
        }
        person.emails?.mapNotNull { it.address }
            ?.filter { SubjectFilter.validateEmail(it, context, profile) }
            ?.take(3)?.joinToString(", ")
            ?.let { metadata["pdl_emails"] = it }
        person.phones?.mapNotNull { it.number }
            ?.filter {
                SubjectFilter.shouldAcceptPersonPhone(
                    it, context, profile.phone, profile.name, profile.city, profile.state, "pdl", "", true
                )
            }
            ?.take(3)?.joinToString(", ")
            ?.let { metadata["pdl_phones"] = it }
        person.jobTitle?.let { metadata["pdl_job_title"] = it }
        person.jobCompanyName?.let { metadata["pdl_company"] = it }
        person.locationName?.takeIf { it.isNotBlank() }?.let { metadata["pdl_location"] = it }
        listOfNotNull(person.locationStreet, person.locationCity, person.locationState, person.locationZip)
            .filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() }
            ?.let { metadata["pdl_address"] = it }
        person.employment?.mapNotNull { emp ->
            listOfNotNull(emp.title, emp.companyName).joinToString(" at ").takeIf { it.isNotBlank() }
        }?.take(6)?.joinToString("\n")?.let { metadata["pdl_employment"] = it }
        person.profiles?.mapNotNull { p ->
            p.url?.let { url -> "${p.network ?: "profile"}: $url" }
        }?.take(6)?.joinToString("\n")?.let { metadata["pdl_profiles"] = it }
        person.education?.mapNotNull { ed ->
            listOfNotNull(ed.degree, ed.schoolName).joinToString(" — ").takeIf { it.isNotBlank() }
        }?.take(3)?.joinToString("\n")?.let { metadata["pdl_education"] = it }
    }

    private fun scrapeOpenSanctions(name: String, apiKey: String): ScrapeOut {
        return try {
            val encoded = URLEncoder.encode(name.trim(), "UTF-8")
            val req = Request.Builder()
                .url("https://api.opensanctions.org/search/default?q=$encoded&schema=Person&limit=5")
                .header("Authorization", "ApiKey $apiKey")
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val code = resp.code
            resp.close()
            if (code == 401 || code == 403) return ScrapeOut(false, true)
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return ScrapeOut(false, false)
            if (results.length() == 0) return ScrapeOut(false, false)
            val names = mutableListOf<String>()
            val datasets = mutableSetOf<String>()
            val countries = mutableSetOf<String>()
            for (i in 0 until minOf(5, results.length())) {
                val item = results.optJSONObject(i) ?: continue
                item.optString("caption").takeIf { it.isNotBlank() }?.let { names.add(it) }
                item.optJSONArray("datasets")?.let { ds ->
                    for (j in 0 until ds.length()) ds.optString(j).takeIf { it.isNotBlank() }?.let { datasets.add(it) }
                }
                item.optJSONObject("properties")?.optJSONArray("country")?.let { cs ->
                    for (j in 0 until cs.length()) cs.optString(j).takeIf { it.isNotBlank() }?.let { countries.add(it) }
                }
            }
            if (names.isEmpty()) return ScrapeOut(false, false)
            apiKeys.recordUsage("opensanctions")
            ScrapeOut(true, false, mapOf(
                "title" to "OpenSanctions: ${names.size} match(es)",
                "snippet" to names.take(3).joinToString("; "),
                "total" to names.size.toString(),
                "names" to names.joinToString(", "),
                "datasets" to datasets.take(5).joinToString(", "),
                "countries" to countries.take(5).joinToString(", "),
                "link" to "https://www.opensanctions.org/search/?q=$encoded"
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeOpenCorporatesOfficers(name: String, apiKey: String): ScrapeOut {
        return try {
            val encoded = URLEncoder.encode(name.trim(), "UTF-8")
            val req = Request.Builder()
                .url("https://api.opencorporates.com/v0.4/officers/search?q=$encoded&api_token=$apiKey&per_page=10")
                .header("User-Agent", SEC_USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val code = resp.code
            resp.close()
            if (code == 401 || code == 403) return ScrapeOut(false, true)
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val officers = JSONObject(body).optJSONObject("results")?.optJSONArray("officers")
                ?: return ScrapeOut(false, false)
            if (officers.length() == 0) return ScrapeOut(false, false)
            val companies = mutableListOf<String>()
            val positions = mutableListOf<String>()
            val jurisdictions = mutableSetOf<String>()
            for (i in 0 until minOf(10, officers.length())) {
                val wrapper = officers.optJSONObject(i) ?: continue
                val officer = wrapper.optJSONObject("officer") ?: continue
                val officerName = officer.optString("name", "")
                if (officerName.isNotBlank() && !textMatchesQuery(officerName, name)) continue
                val company = wrapper.optJSONObject("company")?.optString("name").orEmpty()
                val position = officer.optString("position", "")
                val jurisdiction = officer.optString("jurisdiction_code", "")
                if (company.isNotBlank()) companies.add(company)
                if (position.isNotBlank()) positions.add(position)
                if (jurisdiction.isNotBlank()) jurisdictions.add(jurisdiction.uppercase())
            }
            if (companies.isEmpty()) return ScrapeOut(false, false)
            apiKeys.recordUsage("opencorporates")
            ScrapeOut(true, false, mapOf(
                "title" to "OpenCorporates: ${companies.size} officer record(s)",
                "snippet" to companies.take(3).joinToString("; "),
                "person_companies" to companies.distinct().take(8).joinToString("\n"),
                "positions" to positions.take(8).joinToString(", "),
                "person_states" to jurisdictions.take(5).joinToString(", "),
                "officer_matches" to companies.size.toString()
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeWikidataEnriched(query: String): ScrapeOut {
        val base = scrapeWikidata(query)
        if (!base.found) return base
        val link = base.fields["link"] ?: return base
        val entityId = link.substringAfterLast("/").takeIf { it.startsWith("Q") } ?: return base
        return try {
            val req = Request.Builder()
                .url("https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$entityId&props=claims&format=json")
                .header("User-Agent", SEC_USER_AGENT)
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return base
            val claims = JSONObject(body).optJSONObject("entities")?.optJSONObject(entityId)
                ?.optJSONObject("claims") ?: return base
            val employers = mutableListOf<String>()
            val orgs = mutableListOf<String>()
            claims.optJSONArray("P108")?.let { arr ->
                for (i in 0 until minOf(3, arr.length())) {
                    arr.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")
                        ?.optJSONObject("value")?.optString("id")?.let { id ->
                            resolveWikidataLabel(id)?.let { employers.add(it) }
                        }
                }
            }
            claims.optJSONArray("P463")?.let { arr ->
                for (i in 0 until minOf(3, arr.length())) {
                    arr.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")
                        ?.optJSONObject("value")?.optString("id")?.let { id ->
                            resolveWikidataLabel(id)?.let { orgs.add(it) }
                        }
                }
            }
            val fields = base.fields.toMutableMap()
            if (employers.isNotEmpty()) fields["employers"] = employers.joinToString(", ")
            if (orgs.isNotEmpty()) fields["organizations"] = orgs.joinToString(", ")
            base.copy(fields = fields)
        } catch (_: Exception) { base }
    }

    private fun resolveWikidataLabel(entityId: String): String? {
        return try {
            val req = Request.Builder()
                .url("https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$entityId&props=labels&languages=en&format=json")
                .header("User-Agent", SEC_USER_AGENT)
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            JSONObject(body).optJSONObject("entities")?.optJSONObject(entityId)
                ?.optJSONObject("labels")?.optJSONObject("en")?.optString("value")
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    private fun formatDdgDorkResults(results: List<DdgResult>, nameTokens: List<String>): String {
        val required = if (nameTokens.size >= 2) 2 else 1
        return results.filter { r ->
            val text = "${r.title} ${r.snippet}".lowercase()
            nameTokens.isEmpty() || nameTokens.count { text.contains(it.lowercase()) } >= required
        }.take(8).joinToString("\n---\n") { "${it.title}: ${it.snippet}".trim().take(220) }
    }

    private fun googleCseSearch(query: String, maxResults: Int = 3): List<DdgResult> {
        val key = apiKeys.googleCseApiKey
        val cx = apiKeys.googleCseId
        if (key.isBlank() || cx.isBlank()) return emptyList()
        return try {
            val url = "https://www.googleapis.com/customsearch/v1?key=${encode(key)}&cx=${encode(cx)}&q=${encode(query)}&num=${maxResults.coerceIn(1, 10)}"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", SEC_USER_AGENT)
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val code = resp.code
            resp.close()
            if (code != 200 || body.isBlank()) return emptyList()
            val json = JSONObject(body)
            val items = json.optJSONArray("items") ?: return emptyList()
            (0 until items.length()).mapNotNull { i ->
                val item = items.optJSONObject(i) ?: return@mapNotNull null
                val title = item.optString("title", "").trim()
                val snippet = item.optString("snippet", "").trim()
                val link = item.optString("link", "").trim()
                if (title.isBlank()) null else DdgResult(title, snippet, link)
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun dorkSearch(query: String, maxResults: Int = 8): Pair<List<DdgResult>, String> {
        val cse = googleCseSearch(query, maxResults)
        if (cse.isNotEmpty()) return cse to "Google CSE"
        return ddgHtmlSearch(query).take(maxResults) to "DuckDuckGo"
    }

    private fun filterDorkHits(
        results: List<DdgResult>,
        profile: SubjectProfile,
        maxHits: Int = 8
    ): List<DorkMetadataStore.DorkHit> {
        return results
            .filter { r ->
                val text = "${r.title} ${r.snippet} ${r.url}"
                when {
                    profile.phone.isNotBlank() && profile.name.isBlank() -> {
                        val digits = SubjectFilter.phoneDigits(profile.phone)
                        digits.length >= 7 && text.filter { it.isDigit() }.contains(digits)
                    }
                    profile.email.isNotBlank() && profile.name.isBlank() ->
                        text.contains(profile.email, ignoreCase = true)
                    else -> SubjectFilter.matchesSubject(text, profile)
                }
            }
            .take(maxHits)
            .map { r ->
                DorkMetadataStore.DorkHit(
                    title = r.title,
                    url = r.url.ifBlank { "https://html.duckduckgo.com/html/?q=${encode(r.title)}" },
                    snippet = r.snippet,
                    query = ""
                )
            }
    }

    private fun extractPiiFromDdgResults(
        results: List<DdgResult>,
        profile: SubjectProfile
    ): DorkMetadataStore.ExtractedPii {
        val data = extractDataFromDdgResults(results, profile)
        val employers = mutableListOf<String>()
        val employerRegex = Regex(
            """(?i)(?:works?\s+at|employed\s+(?:by|at)|CEO\s+of|founder\s+of|VP\s+at|director\s+at)\s+([A-Z][A-Za-z0-9&\s.'-]{2,45})"""
        )
        for (r in results) {
            employerRegex.find("${r.title} ${r.snippet}")?.groupValues?.get(1)?.trim()
                ?.takeIf { it.length in 3..45 }
                ?.let { employers.add(it) }
        }
        return DorkMetadataStore.ExtractedPii(
            phones = data.phones,
            emails = data.emails,
            addresses = data.addresses,
            relatives = data.relatives,
            ages = data.ages,
            employers = employers.distinct().take(5),
            socialUrls = data.socialUrls,
            profileUrls = data.profileUrls
        )
    }

    private val DORK_FOLLOW_DOMAINS = setOf(
        "linkedin.com", "fastpeoplesearch.com", "truepeoplesearch.com", "whitepages.com",
        "spokeo.com", "radaris.com", "beenverified.com", "thatsthem.com", "zabasearch.com",
        "411.com", "intelius.com", "familytreenow.com", "usphonebook.com"
    )

    private fun isFollowableDorkUrl(url: String, profile: SubjectProfile): Boolean {
        if (!url.startsWith("http")) return false
        val lower = url.lowercase()
        if (!DORK_FOLLOW_DOMAINS.any { lower.contains(it) }) return false
        if (blockedSourceCache.isUrlBlocked(url)) return false
        if (lower.contains("linkedin.com") && !lower.contains("/in/")) return false
        return SubjectFilter.matchesSubject(url, profile) || profile.name.isBlank()
    }

    private fun followDorkResultUrl(
        hit: DorkMetadataStore.DorkHit,
        profile: SubjectProfile,
        metadata: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>
    ) {
        if (!isFollowableDorkUrl(hit.url, profile)) return
        val out = tryScrapeUrl(hit.url, profile.name, profile)
        if (out.skippedBlocked || out.blocked || !out.found) return
        val domain = BlockedSourceCache.domainFromUrl(hit.url)
        sources.add(DataSource("DorkFollow:$domain", hit.url, Date(), 0.72))
        out.fields["phones"]?.let { phones ->
            val validated = filterScrapeContactFields(
                mapOf("phones" to phones, "snippet" to out.fields["snippet"].orEmpty()), profile
            )
            validated["phones"]?.let { metadata.merge("dork_follow_phones", it) { old, new -> "$old, $new" } }
        }
        out.fields["emails"]?.let { emails ->
            val validated = filterScrapeContactFields(
                mapOf("emails" to emails, "snippet" to out.fields["snippet"].orEmpty()), profile
            )
            validated["emails"]?.let { metadata.merge("dork_follow_emails", it) { old, new -> "$old, $new" } }
        }
        out.fields["snippet"]?.takeIf { it.isNotBlank() }?.let { snippet ->
            metadata.merge("dork_follow_snippets", "${hit.title}: ${snippet.take(180)}") { old, new -> "$old\n---\n$new" }
        }
    }

    private data class DorkAggregateBuffers(
        val phones: MutableList<String>? = null,
        val emails: MutableList<String>? = null,
        val addresses: MutableList<String>? = null,
        val relatives: MutableList<String>? = null,
        val ages: MutableList<String>? = null,
        val social: MutableList<String>? = null,
        val profiles: MutableList<String>? = null,
        val snippets: MutableList<String>? = null
    )

    private suspend fun runAutoDorks(
        profile: SubjectProfile,
        searchType: GoogleDorkLibrary.DorkSearchType,
        metadata: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        channel: SendChannel<SearchProgressEvent>,
        @Suppress("UNUSED_PARAMETER") semaphore: Semaphore,
        phase: SearchPhase,
        aggregate: DorkAggregateBuffers? = null
    ) {
        val dorks = GoogleDorkLibrary.dorksFor(searchType, profile, phase)
        if (dorks.isEmpty()) return
        metadata["dork_query_count"] = dorks.size.toString()
        metadata["dork_phase"] = phase.name
        val dorkSemaphore = Semaphore(SubjectSearchOrchestrator.DORK_PARALLEL_WORKERS)
        val phoneCorroboration = ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
        val emailCorroboration = ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
        val addressCorroboration = ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
        coroutineScope {
            for (resolved in dorks) {
                launch {
                    dorkSemaphore.withPermit {
                        val label = "Dork: ${resolved.template.label}"
                        channel.send(SearchProgressEvent.Checking(label))
                        try {
                            withTimeout(SubjectSearchOrchestrator.SOURCE_TIMEOUT_MS) {
                                val (results, engine) = dorkSearch(resolved.query, 8)
                                val hits = filterDorkHits(results, profile, 8)
                                    .map { it.copy(query = resolved.query) }
                                val pii = extractPiiFromDdgResults(results, profile)
                                if (hits.isNotEmpty() || pii.phones.isNotEmpty() || pii.emails.isNotEmpty()) {
                                    if (hits.isNotEmpty()) {
                                        DorkMetadataStore.storeHits(metadata, resolved.template.category, resolved.query, hits)
                                    }
                                    DorkMetadataStore.storeExtractedPii(metadata, resolved.template.category, pii)
                                    DorkMetadataStore.recordCorroboration(
                                        phoneCorroboration, emailCorroboration, addressCorroboration, pii
                                    )
                                    aggregate?.phones?.addAll(pii.phones)
                                    aggregate?.emails?.addAll(pii.emails)
                                    aggregate?.addresses?.addAll(pii.addresses)
                                    aggregate?.relatives?.addAll(pii.relatives)
                                    aggregate?.ages?.addAll(pii.ages)
                                    aggregate?.social?.addAll(pii.socialUrls)
                                    aggregate?.profiles?.addAll(pii.profileUrls)
                                    aggregate?.snippets?.addAll(
                                        hits.map { "${it.title}: ${it.snippet}".take(200) }
                                    )
                                    hits.firstOrNull { isFollowableDorkUrl(it.url, profile) }?.let { topHit ->
                                        followDorkResultUrl(topHit, profile, metadata, sources)
                                    }
                                    val sourceUrl = if (engine == "Google CSE") {
                                        "https://www.googleapis.com/customsearch/v1?q=${encode(resolved.query)}"
                                    } else {
                                        "https://html.duckduckgo.com/html/?q=${encode(resolved.query)}"
                                    }
                                    sources.add(DataSource("$engine:${resolved.template.id}", sourceUrl, Date(), 0.58))
                                    val preview = hits.firstOrNull()?.title?.take(100)
                                        ?: pii.phones.firstOrNull()
                                        ?: pii.emails.firstOrNull()
                                        ?: ""
                                    channel.send(SearchProgressEvent.Found(label, preview))
                                } else {
                                    channel.send(SearchProgressEvent.NotFound(label))
                                }
                            }
                        } catch (_: Exception) {
                            channel.send(SearchProgressEvent.Failed(label, "timeout"))
                        }
                    }
                }
            }
        }
        DorkMetadataStore.finalizeNeedleFindings(
            metadata, phoneCorroboration, emailCorroboration, addressCorroboration, profile.phone
        )
        metadata["dork_total_hits"] = DorkMetadataStore.totalHitCount(metadata).toString()
        metadata["dork_execution"] = "in_app"
    }

    private suspend fun runSecondaryPassesUntilMinimum(
        startMs: Long,
        minMs: Long,
        name: String,
        city: String,
        state: String,
        phone: String,
        email: String,
        phase: SearchPhase,
        nameTokens: List<String>,
        metadata: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        channel: SendChannel<SearchProgressEvent>,
        semaphore: Semaphore
    ) {
        var passIndex = 0
        while (System.currentTimeMillis() - startMs < minMs && passIndex < 10) {
            channel.send(SearchProgressEvent.PhaseUpdate("Secondary sweep", "Pass ${passIndex + 1}"))
            val passes = SubjectSearchOrchestrator.secondaryDdgPassQueries(
                name, city, state, phone, email, phase, passIndex
            )
            coroutineScope {
                for ((label, q) in passes) {
                    launch {
                        semaphore.withPermit {
                            channel.send(SearchProgressEvent.Checking(label))
                            try {
                                withTimeout(SubjectSearchOrchestrator.SOURCE_TIMEOUT_MS) {
                                    val results = ddgHtmlSearch(q)
                                    val formatted = formatDdgDorkResults(results, nameTokens)
                                    if (formatted.isNotBlank()) {
                                        val key = "secondary_p${passIndex}_${label.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()}"
                                        metadata[key] = formatted
                                        sources.add(DataSource(label, "https://html.duckduckgo.com/html/?q=${encode(q)}", Date(), 0.5))
                                        channel.send(SearchProgressEvent.Found(label, formatted.lines().firstOrNull()?.take(100) ?: ""))
                                    } else {
                                        channel.send(SearchProgressEvent.NotFound(label))
                                    }
                                }
                            } catch (_: Exception) {
                                channel.send(SearchProgressEvent.Failed(label, "timeout"))
                            }
                        }
                    }
                }
            }
            passIndex++
            if (System.currentTimeMillis() - startMs >= minMs) break
            delay(2_000)
        }
    }

    private fun subjectProfileToFields(profile: SubjectProfile): Map<String, String> {
        val m = mutableMapOf<String, String>()
        if (profile.name.isNotBlank()) m["name"] = profile.name
        if (profile.city.isNotBlank()) m["city"] = profile.city
        if (profile.state.isNotBlank()) m["state"] = profile.state
        if (profile.phone.isNotBlank()) m["phone"] = profile.phone
        if (profile.email.isNotBlank()) m["email"] = profile.email
        if (profile.username.isNotBlank()) m["username"] = profile.username
        if (profile.employer.isNotBlank()) m["employer"] = profile.employer
        if (profile.address.isNotBlank()) m["address"] = profile.address
        if (profile.age.isNotBlank()) m["age"] = profile.age
        if (profile.dob.isNotBlank()) m["dob"] = profile.dob
        if (profile.photoUri.isNotBlank()) m["image"] = profile.photoUri
        if (profile.intent.isNotBlank()) m["intent"] = profile.intent
        if (profile.locked) m["locked"] = "true"
        profile.candidateId?.let { m["candidateId"] = it }
        return m
    }

    private fun scrapeUrlhaus(host: String, authKey: String): ScrapeOut {
        return try {
            if (authKey.isBlank()) return ScrapeOut(false, false)
            val cleanHost = host.trim().lowercase().removePrefix("http://").removePrefix("https://").substringBefore("/")
            val req = Request.Builder()
                .url("https://urlhaus-api.abuse.ch/v1/host/${encode(cleanHost)}/")
                .header("Auth-Key", authKey)
                .header("User-Agent", SEC_USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val code = resp.code
            resp.close()
            if (code == 401 || code == 403) return ScrapeOut(false, true)
            if (code == 429) return ScrapeOut(false, true)
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            if (body.contains("\"Unauthorized\"") || body.contains("unknown_auth_key")) return ScrapeOut(false, true)
            val json = JSONObject(body)
            if (json.optString("query_status") == "no_results") return ScrapeOut(false, false)
            val urlCount = json.optInt("url_count", 0)
            val blacklists = json.optJSONArray("blacklists")
            val status = if (blacklists != null && blacklists.length() > 0) "blacklisted" else json.optString("urlhaus_status", "online")
            ScrapeOut(true, false, mapOf(
                "title" to "URLhaus: $cleanHost",
                "snippet" to "$urlCount malicious URL(s) · Status: $status",
                "status" to status,
                "urls_count" to urlCount.toString()
            ))
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
            val normKey = listOf(
                rec.name.trim().lowercase().replace(Regex("\\s+"), " "),
                rec.address.trim().lowercase().take(50),
                rec.age.trim(),
                rec.location.trim().lowercase().take(40)
            ).joinToString("|")
            val existing = seen.indexOfFirst { existing ->
                listOf(
                    existing.name.trim().lowercase().replace(Regex("\\s+"), " "),
                    existing.address.trim().lowercase().take(50),
                    existing.age.trim(),
                    existing.location.trim().lowercase().take(40)
                ).joinToString("|") == normKey
            }
            if (existing == -1) {
                seen.add(rec)
            } else {
                val e = seen[existing]
                seen[existing] = e.copy(
                    phones = (e.phones + rec.phones).distinct().take(6),
                    relatives = (e.relatives + rec.relatives).distinct().take(10),
                    photoUrl = e.photoUrl ?: rec.photoUrl,
                    profileUrl = e.profileUrl ?: rec.profileUrl
                )
            }
        }
        return seen
    }

    private fun buildCandidatesFromDiscovery(
        records: List<PersonRecord>,
        addresses: List<String>,
        ages: List<String>,
        phones: List<String>,
        relatives: List<String>,
        socialUrls: List<String>,
        name: String,
        city: String,
        state: String,
        locationStr: String,
        sourceCount: Int
    ): List<CandidateProfile> {
        val hasGeo = SubjectFilter.hasGeoConstraint(city, state)
        val geoFilteredRecords = records.filter { rec ->
            val text = "${rec.location} ${rec.address}"
            SubjectFilter.matchesLocation(text, city, state)
        }
        val geoAddresses = SubjectFilter.filterByGeo(addresses, city, state)
        val baseRecords = if (hasGeo) geoFilteredRecords else records

        val candidates = mutableListOf<CandidateProfile>()
        if (baseRecords.isNotEmpty()) {
            baseRecords.forEachIndexed { i, rec ->
                val geoMatch = SubjectFilter.matchesLocation("${rec.location} ${rec.address}", city, state)
                val socialHints = socialUrls.take(3).mapNotNull { url ->
                    when {
                        url.contains("linkedin.com") -> SocialHint("LinkedIn", url)
                        url.contains("facebook.com") -> SocialHint("Facebook", url)
                        url.contains("instagram.com") -> SocialHint("Instagram", url)
                        else -> null
                    }
                }
                candidates.add(
                    CandidateProfile(
                        name = rec.name.ifBlank { name },
                        age = rec.age,
                        location = rec.location.ifBlank { locationStr },
                        phones = rec.phones,
                        address = rec.address.ifBlank { rec.location },
                        source = rec.source,
                        confidence = SubjectSearchOrchestrator.candidateConfidence(sourceCount, i, geoMatch),
                        relatives = rec.relatives,
                        photoUrl = rec.photoUrl,
                        profileUrl = rec.profileUrl,
                        socialHints = socialHints,
                        linkedinUrl = socialHints.firstOrNull { it.platform == "LinkedIn" }?.url,
                        facebookUrl = socialHints.firstOrNull { it.platform == "Facebook" }?.url,
                        instagramUrl = socialHints.firstOrNull { it.platform == "Instagram" }?.url
                    )
                )
            }
        }

        if (geoAddresses.size >= 2) {
            geoAddresses.take(8).forEachIndexed { i, addr ->
                if (candidates.any { it.address.equals(addr, ignoreCase = true) }) return@forEachIndexed
                candidates.add(
                    CandidateProfile(
                        name = name,
                        age = ages.getOrNull(i % ages.size.coerceAtLeast(1)).orEmpty(),
                        location = locationStr,
                        phones = if (i == 0) phones.take(2) else emptyList(),
                        address = addr,
                        source = "Public Records",
                        confidence = SubjectSearchOrchestrator.candidateConfidence(3, i, hasGeo),
                        relatives = if (i == 0) relatives.take(3) else emptyList()
                    )
                )
            }
        }

        val geoCandidates = SubjectFilter.filterCandidatesByGeo(candidates, city, state)
        return geoCandidates.take(12)
    }

    private suspend fun enrichCandidatesWithPhotos(
        candidates: List<CandidateProfile>,
        city: String,
        state: String,
        channel: SendChannel<SearchProgressEvent>
    ): List<CandidateProfile> {
        if (candidates.isEmpty()) return candidates
        channel.send(SearchProgressEvent.Checking("Candidate Photos"))
        return try {
            val enricher = CandidatePhotoEnricher(fastHttpClient)
            val enriched = enricher.enrichAll(candidates, city, state)
            val withPhotos = enriched.count { it.allPhotoUrls().isNotEmpty() }
            if (withPhotos > 0) {
                channel.send(SearchProgressEvent.Found("Candidate Photos", "$withPhotos of ${enriched.size} with photos"))
            } else {
                channel.send(SearchProgressEvent.NotFound("Candidate Photos"))
            }
            enriched
        } catch (e: Exception) {
            Log.w("CandidatePhotoEnricher", "Photo enrichment failed: ${e.message}", e)
            channel.send(SearchProgressEvent.NotFound("Candidate Photos"))
            candidates
        }
    }

    private fun buildOsintAiContext(
        query: String,
        type: String,
        metadata: Map<String, String>,
        sources: List<DataSource>
    ): String = buildString {
        appendLine("Synthesize an OSINT dossier from the collected data below.")
        appendLine("ai_facts_only=true — cite ONLY the metadata keys/values below. Never invent contact info.")
        appendLine()
        appendLine("SUBJECT: $query")
        appendLine("SEARCH TYPE: $type")
        appendLine("SOURCES CHECKED (${sources.size}): ${sources.take(20).joinToString(", ") { it.name }}")
        appendLine()
        metadata["person_name"]?.let { appendLine("Name: $it") }
        metadata["field_age"]?.takeIf { it.isNotBlank() }?.let { appendLine("Age: $it") }
        metadata["person_location"]?.takeIf { it.isNotBlank() }?.let { appendLine("Location: $it") }
        listOfNotNull(metadata["search_phones"], metadata["pipl_phones"], metadata["pdl_phones"], metadata["person_phone"])
            .firstOrNull { it.isNotBlank() }?.let { appendLine("Phone(s): $it") }
        listOfNotNull(metadata["search_emails"], metadata["pipl_emails"], metadata["pdl_emails"])
            .firstOrNull { it.isNotBlank() }?.let { appendLine("Email(s): $it") }
        listOfNotNull(metadata["search_relatives"], metadata["pipl_relatives"])
            .firstOrNull { it.isNotBlank() }?.let { appendLine("Associates: $it") }
        metadata["search_addresses"]?.takeIf { it.isNotBlank() }?.let { appendLine("Addresses:\n${it.take(300)}") }
        metadata["pdl_address"]?.takeIf { metadata["search_addresses"].isNullOrBlank() }?.let { appendLine("Address: $it") }
        metadata["pipl_addresses"]?.takeIf { it.isNotBlank() }?.let { appendLine("Addresses (Pipl):\n${it.take(200)}") }
        metadata["search_social_links"]?.takeIf { it.isNotBlank() }?.let { appendLine("Social:\n${it.take(300)}") }
        metadata["search_profile_links"]?.takeIf { it.isNotBlank() }?.let { appendLine("Profiles:\n${it.take(300)}") }
        metadata["pdl_profiles"]?.takeIf { it.isNotBlank() }?.let { appendLine("Profiles (PDL):\n${it.take(200)}") }
        metadata["github_name"]?.takeIf { it.isNotBlank() }?.let { appendLine("GitHub Name: $it") }
        metadata["github_stats"]?.takeIf { it.isNotBlank() }?.let { appendLine("GitHub: $it") }
        metadata["username_platform_summary"]?.takeIf { it.isNotBlank() }?.let { appendLine("Username platforms:\n${it.take(400)}") }
        metadata["sites_found"]?.toIntOrNull()?.takeIf { it > 0 }?.let { found ->
            appendLine("Username scan: $found profiles on ${metadata["sites_checked"] ?: "?"} platforms")
        }
        metadata["reddit_url"]?.takeIf { it.isNotBlank() }?.let { appendLine("Reddit: $it") }
        listOfNotNull(metadata["pipl_employment"], metadata["pdl_employment"])
            .firstOrNull { it.isNotBlank() }?.let { appendLine("Employment:\n${it.take(300)}") }
        metadata["career_transition"]?.takeIf { it.isNotBlank() }
            ?.let { appendLine("Career inference: $it") }
        metadata["connection_summary"]?.takeIf { it.isNotBlank() }
            ?.let { appendLine("Connections & pivots:\n${it.take(500)}") }
        metadata["subject_timeline"]?.takeIf { it.isNotBlank() }
            ?.let { appendLine("Timeline:\n${it.take(400)}") }
        metadata["pdl_company"]?.takeIf { it.isNotBlank() }?.let { appendLine("Company (PDL): $it") }
        metadata["clearbit_person_company"]?.takeIf { it.isNotBlank() }?.let { appendLine("Company (Clearbit): $it") }
        metadata["corpwiki_person_companies"]?.takeIf { it.isNotBlank() }?.let { appendLine("Companies (OpenCorporates):\n${it.take(200)}") }
        metadata["sec_person_entities"]?.takeIf { it.isNotBlank() }?.let { appendLine("SEC Affiliations: $it") }
        metadata["npi_providers"]?.takeIf { it.isNotBlank() }?.let { appendLine("Healthcare Providers (NPI):\n${it.take(300)}") }
        metadata["fbi_wanted_matches"]?.takeIf { it.isNotBlank() }?.let { appendLine("FBI Wanted:\n${it.take(300)}") }
        metadata["openfec_candidates"]?.takeIf { it.isNotBlank() }?.let { appendLine("Political Candidates (FEC):\n${it.take(200)}") }
        metadata["zippopotam_zips"]?.takeIf { it.isNotBlank() }?.let { appendLine("Area ZIP codes: $it") }
        metadata["wikidata_employers"]?.takeIf { it.isNotBlank() }?.let { appendLine("Employers (Wikidata): $it") }
        metadata["wikipedia_extract"]?.takeIf { it.isNotBlank() }?.let { appendLine("Wikipedia:\n${it.take(500)}") }
        metadata["ddg_abstract"]?.takeIf { it.isNotBlank() }?.let { appendLine("Web Summary:\n${it.take(400)}") }
        metadata.entries.firstOrNull { it.key.contains("news") && it.value.isNotBlank() }
            ?.value?.let { appendLine("News:\n${it.take(400)}") }
        metadata["darksearch_links"]?.takeIf { it.isNotBlank() }?.let { appendLine("Dark Web Mentions:\n${it.take(200)}") }
        metadata["search_snippets"]?.takeIf { it.isNotBlank() }?.let { appendLine("Web Intelligence:\n${it.take(800)}") }
        metadata.entries.filter { (k, v) ->
            v.isNotBlank() && (k.contains("breach") || k.contains("hibp") || k.contains("risk"))
        }.take(5).forEach { (k, v) -> appendLine("${k.replace('_', ' ')}: ${v.take(200)}") }
    }

    private fun buildAiSummaryPrompt(query: String, type: String, metadata: Map<String, String>): String =
        buildOsintAiContext(query, type, metadata, emptyList()) + "\nWrite a concise OSINT intelligence brief (max 350 words). Be factual and analytical."

    private fun generateOsintAiReport(
        query: String,
        type: String,
        metadata: Map<String, String>,
        sources: List<DataSource>
    ): OsintAiReport? {
        val context = buildOsintAiContext(query, type, metadata, sources)
        val allowedFacts = OsintAiReport.collectAllowedFactValues(metadata)
        val openrouterKey = apiKeys.openrouterKey.trim()
        val report = if (openrouterKey.isNotBlank()) {
            val model = apiKeys.openrouterModel.trim()
                .ifBlank { "meta-llama/llama-3.3-70b-instruct:free" }
            OpenRouterAiClient.generateReport(openrouterKey, context, model)?.also {
                apiKeys.recordUsage("openrouter")
            }
        } else {
            val fallbackPrompt = buildAiSummaryPrompt(query, type, metadata) +
                "\n\nai_facts_only=true — only state facts from the data above."
            queryPollinationsAI(fallbackPrompt)?.let { OsintAiReport.fromPlainText(it, "pollinations") }
        } ?: return null
        return OsintAiReport.enforceFactsOnly(report, allowedFacts)
    }

    private fun generateAiSuggestedSearchLinks(
        query: String,
        type: String,
        metadata: Map<String, String>,
        sources: List<DataSource>
    ): String? {
        if (!AppSettings.isAiAgentAssist(appCtx)) return null
        val openrouterKey = apiKeys.openrouterKey.trim()
        if (openrouterKey.isBlank()) return null
        val context = buildOsintAiContext(query, type, metadata, sources)
        val model = apiKeys.openrouterModel.trim()
            .ifBlank { "meta-llama/llama-3.3-70b-instruct:free" }
        val searches = OpenRouterAiClient.generateSuggestedSearches(openrouterKey, context, model)
            ?: return null
        apiKeys.recordUsage("openrouter")
        return searches.joinToString("\n") { search ->
            "${search.label}: https://html.duckduckgo.com/html/?q=${encode(search.query)}"
        }
    }

    private fun queryPollinationsAI(prompt: String): String? = try {
        val body = JSONObject().apply {
            put("model", "openai-large")
            put("private", true)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are an OSINT intelligence analyst. ai_facts_only=true: only cite data explicitly provided. Never invent phone, address, email, or name. Be objective and analytical.")
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

    private fun finalizeMetadata(metadata: ConcurrentHashMap<String, String>) {
        ReportMetadataSync.sync(metadata)
        OsintFrameworkReportBridge.apply(metadata, OsintToolRegistry.allTools)
    }

    private fun buildStructuredPersonFields(metadata: Map<String, String>): Triple<String, String, String> {
        val employmentJson = metadata["structured_employment_json"]
            ?: SubjectConnectionEngine.employmentToJson(SubjectConnectionEngine.parseEmployment(metadata))
        val addressesJson = buildAddressesJson(metadata)
        val socialJson = metadata["social_profiles_json"] ?: "[]"
        return Triple(employmentJson, addressesJson, socialJson)
    }

    private fun buildAddressesJson(metadata: Map<String, String>): String {
        val addresses = linkedSetOf<String>()
        listOf("search_addresses", "pipl_addresses", "pdl_address", "voter_addresses")
            .forEach { key ->
                metadata[key]?.split(" | ", ",")?.map { it.trim() }?.filter { it.length > 8 }
                    ?.forEach { addresses.add(it) }
            }
        if (addresses.isEmpty()) return "[]"
        val arr = org.json.JSONArray()
        addresses.take(12).forEach { full ->
            val parts = full.split(",").map { it.trim() }
            val obj = org.json.JSONObject()
            when {
                parts.size >= 3 -> {
                    obj.put("street", parts[0])
                    obj.put("city", parts[1])
                    obj.put("state", parts.getOrNull(2)?.take(2) ?: "")
                    obj.put("postalCode", parts.getOrNull(3) ?: "")
                }
                else -> obj.put("city", full)
            }
            obj.put("country", "US")
            obj.put("type", "previous")
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun CoroutineScope.launchPhoneIntelScrapers(
        phone: String,
        city: String,
        state: String,
        metadata: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        channel: SendChannel<SearchProgressEvent>,
        semaphore: Semaphore
    ) {
        val phoneDigits = phone.replace(Regex("[^0-9]"), "").takeLast(10)
        if (phoneDigits.length < 10) return
        val phoneFmt = "${phoneDigits.substring(0, 3)}-${phoneDigits.substring(3, 6)}-${phoneDigits.substring(6, 10)}"
        metadata["person_phone"] = phoneFmt

        launch {
            channel.send(SearchProgressEvent.Checking("800notes"))
            val out = scrape800Notes(phone)
            handleScrapeOut("800notes", "https://800notes.com/Phone.aspx/$phoneFmt", out, sources, metadata, channel)
        }
        launch {
            channel.send(SearchProgressEvent.Checking("ThatsThem"))
            val out = scrapePhoneReversePage("https://thatsthem.com/phone/$phoneFmt", phoneFmt)
            handleScrapeOut("ThatsThem", "https://thatsthem.com/phone/$phoneFmt", out, sources, metadata, channel)
        }
        launch {
            channel.send(SearchProgressEvent.Checking("FastPeopleSearch"))
            val out = scrapePhoneReversePage("https://www.fastpeoplesearch.com/$phoneDigits", phoneFmt)
            handleScrapeOut("FastPeopleSearch", "https://www.fastpeoplesearch.com/$phoneDigits", out, sources, metadata, channel)
        }
        launch {
            channel.send(SearchProgressEvent.Checking("USPhoneBook"))
            val out = scrapeUSPhoneBook(phoneFmt, city, state, byPhone = true)
            handleScrapeOut("USPhoneBook", "https://www.usphonebook.com/$phoneDigits", out, sources, metadata, channel)
        }
        launch {
            semaphore.withPermit {
                val key = apiKeys.numverifyKey
                if (key.isBlank()) return@withPermit
                channel.send(SearchProgressEvent.Checking("Numverify"))
                val out = scrapeNumverify(phone, key)
                if (out.blocked) {
                    channel.send(SearchProgressEvent.Blocked("Numverify"))
                } else if (out.found) {
                    out.fields["valid"]?.let { metadata["numverify_valid"] = it }
                    out.fields["country"]?.let { metadata["numverify_country"] = it }
                    out.fields["carrier"]?.let { metadata["numverify_carrier"] = it }
                    out.fields["line_type"]?.let { metadata["numverify_line_type"] = it }
                    out.fields["location"]?.let { metadata["numverify_location"] = it }
                    out.fields["intl"]?.let { metadata["numverify_intl"] = it }
                    sources.add(DataSource("Numverify", "https://numverify.com/", Date(), 0.85))
                    channel.send(SearchProgressEvent.Found("Numverify", out.fields["snippet"] ?: ""))
                } else {
                    channel.send(SearchProgressEvent.NotFound("Numverify"))
                }
            }
        }
        launch {
            channel.send(SearchProgressEvent.Checking("libphonenumber"))
            val out = scrapeLibPhoneNumber(phone)
            if (out.found) {
                out.fields["valid"]?.let { metadata["libphone_valid"] = it }
                out.fields["country"]?.let { metadata["libphone_country"] = it }
                out.fields["carrier"]?.let { metadata["libphone_carrier"] = it }
                out.fields["line_type"]?.let { metadata["libphone_line_type"] = it }
                out.fields["location"]?.let { metadata["libphone_location"] = it }
                out.fields["timezone"]?.let { metadata["libphone_timezone"] = it }
                out.fields["intl"]?.let { metadata["libphone_intl"] = it }
            }
            handleScrapeOut("libphonenumber", "https://libphonenumberapi.com/api/phone-numbers/${encode(phone)}", out, sources, metadata, channel)
        }
        launch {
            channel.send(SearchProgressEvent.Checking("CallTracer"))
            val out = scrapeCallTracer(phone)
            if (out.found) {
                out.fields["country"]?.let { metadata["calltracer_country"] = it }
                out.fields["line_type"]?.let { metadata["calltracer_line_type"] = it }
                out.fields["carrier"]?.let { metadata["calltracer_carrier"] = it }
                out.fields["location"]?.let { metadata["calltracer_location"] = it }
                out.fields["spam_score"]?.let { metadata["calltracer_spam_score"] = it }
                out.fields["spam_reports"]?.let { metadata["calltracer_spam_reports"] = it }
            }
            handleScrapeOut("CallTracer", "https://calltracer.io/api/lookup/$phoneDigits", out, sources, metadata, channel)
        }
    }

    suspend fun saveReport(
        query: String,
        personId: String?,
        sources: List<DataSource>,
        metadata: Map<String, String>,
        reportId: String? = null
    ): String {
        val id = reportId ?: UUID.randomUUID().toString()
        val sourcesType = Types.newParameterizedType(List::class.java, DataSource::class.java)
        val sourcesAdapter = moshi.adapter<List<DataSource>>(sourcesType)
        val metaType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
        val metaAdapter = moshi.adapter<Map<String, String>>(metaType)

        val entity = OsintReportEntity(
            id = id,
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
        return id
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
            val searchStartMs = System.currentTimeMillis()
            val fields = parseFields(query)
            val subjectProfile = SubjectProfile.fromFields(fields)
            val primaryQuery = resolvePrimaryQuery(type, fields, query)

            val metadata = ConcurrentHashMap<String, String>()
            OsintToolRegistry.ensureLoaded(appCtx)
            val effectiveType = if (type == "scan") "person" else type
            val searchPhase = SubjectSearchOrchestrator.resolvePhase(type, round, subjectProfile)
            val fastMode = !AppSettings.isInvestigatorMode(appCtx)
            val minPhaseMs = SubjectSearchOrchestrator.minimumDurationMs(searchPhase, fastMode)
            send(SearchProgressEvent.PhaseUpdate(
                SubjectSearchOrchestrator.phaseLabel(searchPhase),
                SubjectSearchOrchestrator.phaseDurationHint(searchPhase, fastMode)
            ))
            metadata["search_type"] = effectiveType
            metadata["search_phase"] = searchPhase.name
            metadata["investigation_mode"] = "deep"
            metadata["subject_locked"] = subjectProfile.locked.toString()
            val activeCategories = SearchPresetManager.getActiveCategories(appCtx)
            metadata["search_preset"] = SearchPresetManager.getPresetDisplayName(appCtx)
            metadata["search_preset_categories"] = activeCategories.joinToString(",")
            fun allowSource(name: String): Boolean =
                SubjectSearchOrchestrator.shouldRunForPreset(name, activeCategories)
            val subjectIntent = SubjectSearchOrchestrator.normalizeIntent(subjectProfile.intent)
            if (subjectProfile.intent.isNotBlank()) metadata["subject_intent"] = subjectIntent
            if (termuxRunner.isTermuxInstalled()) {
                termuxRunner.requestToolStatusRefresh()
            }
            val isPhoneOnly = fields["phone"]?.isNotBlank() == true &&
                fields["name"].isNullOrBlank() && fields["email"].isNullOrBlank() && fields["username"].isNullOrBlank()
            if (isPhoneOnly) metadata["phone_only_search"] = "true"
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
            val semaphore = Semaphore(SubjectSearchOrchestrator.PARALLEL_WORKERS)
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
                        val deepPhase = SubjectSearchOrchestrator.isDeepPhase(searchPhase)
                        targetedScraperNames += setOf(
                            "Wikipedia", "Google News", "ThatsThem", "FastPeopleSearch",
                            "USPhoneBook", "Name Demographics", "Pipl", "Clearbit Person"
                        )
                        if (deepPhase) {
                            targetedScraperNames += setOf(
                                "DarkSearch", "Ahmia", "CourtListener", "GLEIF", "SEC EDGAR",
                                "Wikidata", "OpenSanctions", "OpenCorporates", "FBI Wanted",
                                "NPI Registry", "OpenFEC"
                            ) + SubjectSearchOrchestrator.extraDeepScrapersForIntent(subjectIntent)
                        }
                        if (SubjectSearchOrchestrator.shouldRunDarkWeb(searchPhase, subjectIntent) &&
                            SubjectSearchOrchestrator.shouldRunScraper("DarkSearch", searchPhase, subjectIntent, activeCategories)
                        ) {
                            launch { ensureDarkWebTor(this@channelFlow) }
                        }
                        val personPhone = fields["phone"] ?: ""
                        val personEmail = fields["email"] ?: ""
                        val personUsername = fields["username"] ?: ""
                        val personQueries = buildPersonQueries(
                            primaryQuery, city, state, personPhone, personEmail, personUsername, searchPhase, activeCategories
                        )
                        val nameTokens = primaryQuery.lowercase().split(" ").filter { it.length > 1 }
                        for ((label, q) in personQueries) {
                            launch {
                                semaphore.withPermit {
                                    send(SearchProgressEvent.Checking("DDG: $label"))
                                    try {
                                        withTimeout(SubjectSearchOrchestrator.SOURCE_TIMEOUT_MS) {
                                            val results = ddgHtmlSearch(q)
                                            val extracted = extractDataFromDdgResults(results, subjectProfile)
                                            if (results.isNotEmpty()) {
                                                sources.add(DataSource("DDG:$label", "https://html.duckduckgo.com/html/?q=${encode(q)}", Date(), 0.6))
                                                send(SearchProgressEvent.Found("DDG: $label", (extracted.snippets.firstOrNull() ?: results.firstOrNull()?.snippet ?: "").take(120)))
                                            } else {
                                                send(SearchProgressEvent.NotFound("DDG: $label"))
                                            }
                                            val hasGeo = SubjectFilter.hasGeoConstraint(city, state)
                                            ddgPhones.addAll(extracted.phones)
                                            ddgEmails.addAll(extracted.emails)
                                            if (!hasGeo || SubjectSearchOrchestrator.isDeepPhase(searchPhase)) {
                                                ddgRelatives.addAll(extracted.relatives)
                                            }
                                            ddgAges.addAll(extracted.ages)
                                            ddgAddresses.addAll(
                                                if (hasGeo) SubjectFilter.filterByGeo(extracted.addresses, city, state)
                                                else extracted.addresses
                                            )
                                            ddgSocial.addAll(extracted.socialUrls)
                                            ddgProfiles.addAll(extracted.profileUrls)
                                            ddgSnippets.addAll(
                                                extracted.snippets.filter { snippet ->
                                                    !hasGeo || SubjectFilter.matchesSubject(snippet, subjectProfile)
                                                }
                                            )
                                        }
                                    } catch (_: Exception) {
                                        send(SearchProgressEvent.Failed("DDG: $label", "timeout"))
                                    }
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
                            val out = scrapeGoogleNews(newsQuery, matchQuery = primaryQuery)
                            handleScrapeOut("Google News", "https://news.google.com/rss/search?q=${encode(newsQuery)}", out, sources, metadata, this@channelFlow)
                        }
                        fields["phone"]?.takeIf { it.isNotBlank() }?.let { personPhone ->
                            launchPhoneIntelScrapers(
                                personPhone, city, state, metadata, sources, this@channelFlow, semaphore
                            )
                        }
                        if (SubjectSearchOrchestrator.shouldRunScraper(
                                "DarkSearch", searchPhase, subjectIntent, activeCategories
                            )
                        ) {
                            launch {
                                runDarkWebSearches(subjectProfile, primaryQuery, sources, metadata, this@channelFlow)
                            }
                        }
                        if (deepPhase) {
                            launch {
                                send(SearchProgressEvent.Checking("CourtListener"))
                                val out = scrapeCourtListener(primaryQuery)
                                if (out.found) {
                                    out.fields["case_count"]?.let {
                                        metadata["courtlistener_count"] = it
                                        metadata["court_case_count"] = it
                                    }
                                    out.fields["snippet"]?.let { metadata["court_cases"] = it }
                                    out.fields["case_urls"]?.let { urls ->
                                        metadata["court_case_urls"] = urls
                                        urls.lines().firstOrNull { it.startsWith("http") }
                                            ?.let { metadata["courtlistener_link"] = it }
                                    }
                                }
                                handleScrapeOut("CourtListener", "https://www.courtlistener.com/?q=${encode(primaryQuery)}&type=r", out, sources, metadata, this@channelFlow, 0.75)
                            }
                            launch {
                                send(SearchProgressEvent.Checking("GLEIF"))
                                val out = scrapeGleif(primaryQuery)
                                if (out.found) {
                                    out.fields["legal_entities"]?.let { metadata["gleif_entities"] = it }
                                }
                                handleScrapeOut("GLEIF", "https://search.gleif.org/#/record/${encode(primaryQuery)}", out, sources, metadata, this@channelFlow, 0.7)
                            }
                            launch {
                                send(SearchProgressEvent.Checking("SEC EDGAR Form-4"))
                                val out = scrapeSecEdgar(primaryQuery, forms = "4")
                                if (out.found) {
                                    out.fields["total_hits"]?.let { metadata["sec_person_hits"] = it }
                                    out.fields["entities"]?.let { metadata["sec_person_entities"] = it }
                                }
                                handleScrapeOut("SEC EDGAR Form-4", "https://www.sec.gov/edgar/search/#/q=${encode(primaryQuery)}&forms=4", out, sources, metadata, this@channelFlow, 0.8)
                            }
                            launch {
                                send(SearchProgressEvent.Checking("SEC EDGAR"))
                                val out = scrapeSecEdgar(primaryQuery)
                                if (out.found) {
                                    out.fields["total_hits"]?.let { metadata["sec_fulltext_hits"] = it }
                                    out.fields["form_types"]?.let { metadata["sec_fulltext_forms"] = it }
                                    out.fields["entities"]?.let { metadata["sec_fulltext_entities"] = it }
                                }
                                handleScrapeOut("SEC EDGAR", "https://www.sec.gov/edgar/search/#/q=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow, 0.8)
                            }
                            launch {
                                send(SearchProgressEvent.Checking("Wikidata"))
                                val out = scrapeWikidataEnriched(primaryQuery)
                                if (out.found) {
                                    out.fields["descriptions"]?.let { metadata["wikidata_descriptions"] = it }
                                    out.fields["link"]?.let { metadata["wikidata_link"] = it }
                                    out.fields["employers"]?.let { metadata["wikidata_employers"] = it }
                                    out.fields["organizations"]?.let { metadata["wikidata_organizations"] = it }
                                    metadata["wikipedia_hits"] = "1"
                                }
                                handleScrapeOut("Wikidata", "https://www.wikidata.org/w/index.php?search=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow, 0.75)
                            }
                        }
                        launch {
                            send(SearchProgressEvent.Checking("ThatsThem"))
                            val out = scrapeThatsThemPerson(primaryQuery)
                            handleScrapeOut("ThatsThem", "https://thatsthem.com/name/${encode(primaryQuery.replace(" ", "-"))}", out, sources, metadata, this@channelFlow)
                            appendPersonRecordFromScrape("tt", primaryQuery, metadata, scrapedPersonRecords)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("FastPeopleSearch"))
                            val out = scrapeFastPeopleSearchPerson(primaryQuery)
                            handleScrapeOut("FastPeopleSearch", "https://www.fastpeoplesearch.com/name/${encode(primaryQuery.replace(" ", "-"))}", out, sources, metadata, this@channelFlow)
                            appendPersonRecordFromScrape("fps", primaryQuery, metadata, scrapedPersonRecords)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("USPhoneBook"))
                            val out = scrapeUSPhoneBook(primaryQuery, city, state)
                            handleScrapeOut("USPhoneBook", "https://www.usphonebook.com/name/${encode(primaryQuery.replace(" ", "-"))}", out, sources, metadata, this@channelFlow)
                            appendPersonRecordFromScrape("uspb", primaryQuery, metadata, scrapedPersonRecords)
                        }
                        if (SubjectFilter.hasGeoConstraint(city, state)) {
                            launch {
                                send(SearchProgressEvent.Checking("Zippopotam"))
                                val out = scrapeZippopotam(city, state)
                                if (out.found) {
                                    out.fields["zips"]?.let { metadata["zippopotam_zips"] = it }
                                    out.fields["latitude"]?.let { metadata["zippopotam_lat"] = it }
                                    out.fields["longitude"]?.let { metadata["zippopotam_lon"] = it }
                                }
                                handleScrapeOut(
                                    "Zippopotam",
                                    "http://api.zippopotam.us/us/${SubjectFilter.toStateAbbrev(state).lowercase()}/${encode(city.lowercase())}",
                                    out, sources, metadata, this@channelFlow, 0.65
                                )
                                apiKeys.recordUsage("zippopotam")
                            }
                        }
                        launch {
                            val firstName = primaryQuery.trim().split("\\s+".toRegex()).firstOrNull().orEmpty()
                            if (firstName.isNotBlank()) {
                                send(SearchProgressEvent.Checking("Name Demographics"))
                                val out = scrapeNameDemographics(firstName)
                                handleScrapeOut("Name Demographics", "https://genderize.io", out, sources, metadata, this@channelFlow)
                            }
                        }
                        launch {
                            semaphore.withPermit {
                                val key = apiKeys.getKey("pipl")
                                if (key.isNullOrBlank()) return@withPermit
                                    send(SearchProgressEvent.Checking("Pipl"))
                                    try {
                                        val nameParts = primaryQuery.trim().split("\\s+".toRegex())
                                        val first = nameParts.firstOrNull()
                                        val last = if (nameParts.size > 1) nameParts.last() else null
                                        val result = RetrofitClient.piplService.search(
                                            apiKey = key,
                                            firstName = first,
                                            lastName = last,
                                            email = fields["email"],
                                            phone = fields["phone"],
                                            username = fields["username"]
                                        )
                                        val person = result.person
                                        if (person == null) {
                                            send(SearchProgressEvent.NotFound("Pipl"))
                                        } else {
                                            applyPiplPersonToMetadata(person, metadata, subjectProfile)
                                            sources.add(DataSource("Pipl", "https://pipl.com/search/?q=${encode(primaryQuery)}", Date(), 0.9))
                                            send(SearchProgressEvent.Found("Pipl", person.names?.firstOrNull()?.display ?: "Person profile found"))
                                            apiKeys.recordUsage("pipl")
                                        }
                                    } catch (_: Exception) {
                                        send(SearchProgressEvent.Blocked("Pipl"))
                                    }

                            }
                        }
                        launch {
                            semaphore.withPermit {
                                val key = apiKeys.getKey("pdl")
                                if (key.isNullOrBlank()) return@withPermit
                                    send(SearchProgressEvent.Checking("People Data Labs"))
                                    try {
                                        val nameParts = primaryQuery.trim().split("\\s+".toRegex())
                                        val result = RetrofitClient.pdlService.enrichPerson(
                                            email = fields["email"],
                                            phone = fields["phone"],
                                            firstName = nameParts.firstOrNull(),
                                            lastName = if (nameParts.size > 1) nameParts.last() else null,
                                            apiKey = key
                                        )
                                        val person = result.data
                                        if (person == null) {
                                            send(SearchProgressEvent.NotFound("People Data Labs"))
                                        } else {
                                            applyPdlPersonToMetadata(person, metadata, subjectProfile)
                                            sources.add(DataSource("People Data Labs", "https://peopledatalabs.com/", Date(), 0.9))
                                            send(SearchProgressEvent.Found("People Data Labs", person.fullName ?: "Profile enriched"))
                                            apiKeys.recordUsage("pdl")
                                        }
                                    } catch (_: Exception) {
                                        send(SearchProgressEvent.Blocked("People Data Labs"))
                                    }

                            }
                        }
                        launch {
                            semaphore.withPermit {
                                val key = apiKeys.clearbitKey.ifBlank { apiKeys.getKey("clearbit") ?: "" }
                                if (key.isNullOrBlank()) return@withPermit
                                    send(SearchProgressEvent.Checking("Clearbit Person"))
                                    try {
                                        val nameParts = primaryQuery.trim().split("\\s+".toRegex())
                                        val resp = RetrofitClient.clearbitPersonService.findPerson(
                                            email = fields["email"],
                                            givenName = nameParts.firstOrNull(),
                                            familyName = if (nameParts.size > 1) nameParts.last() else null,
                                            bearerToken = "Bearer $key"
                                        )
                                        val cbPerson = resp.body()?.person
                                        if (!resp.isSuccessful || cbPerson == null) {
                                            send(SearchProgressEvent.NotFound("Clearbit Person"))
                                        } else {
                                            metadata["clearbit_person_found"] = "true"
                                            cbPerson.name?.fullName?.let { metadata["clearbit_person_name"] = it }
                                            cbPerson.email?.let { metadata["clearbit_person_email"] = it }
                                            cbPerson.location?.let { metadata["clearbit_person_location"] = it }
                                            cbPerson.employment?.let { emp ->
                                                emp.name?.let { metadata["clearbit_person_company"] = it }
                                                emp.title?.let { metadata["clearbit_person_title"] = it }
                                            }
                                            cbPerson.linkedin?.handle?.let { handle ->
                                                appendMetadata(metadata, "found_urls", "LinkedIn: https://linkedin.com/in/$handle")
                                            }
                                            sources.add(DataSource("Clearbit Person", "https://clearbit.com/", Date(), 0.88))
                                            send(SearchProgressEvent.Found("Clearbit Person", cbPerson.name?.fullName ?: "Profile found"))
                                            apiKeys.recordUsage("clearbit")
                                        }
                                    } catch (_: Exception) {
                                        send(SearchProgressEvent.Blocked("Clearbit Person"))
                                    }

                            }
                        }
                        if (deepPhase) {
                            launch {
                                val key = apiKeys.opensanctionsKey.ifBlank { apiKeys.getKey("opensanctions") ?: "" }
                                if (!key.isBlank()) {
                                    send(SearchProgressEvent.Checking("OpenSanctions"))
                                    val out = scrapeOpenSanctions(primaryQuery, key)
                                    if (out.found) {
                                        out.fields["total"]?.let { metadata["opensanctions_total"] = it }
                                        out.fields["names"]?.let { metadata["opensanctions_names"] = it }
                                        out.fields["datasets"]?.let { metadata["opensanctions_datasets"] = it }
                                        out.fields["countries"]?.let { metadata["opensanctions_countries"] = it }
                                        out.fields["link"]?.let { metadata["opensanctions_link"] = it }
                                    }
                                    handleScrapeOut("OpenSanctions", "https://api.opensanctions.org/search/default?q=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow, 0.85)
                                }
                            }
                            launch {
                                val key = apiKeys.opencorporatesKey.ifBlank { apiKeys.getKey("opencorporates") ?: "" }
                                if (!key.isBlank()) {
                                    send(SearchProgressEvent.Checking("OpenCorporates Officers"))
                                    val out = scrapeOpenCorporatesOfficers(primaryQuery, key)
                                    if (out.found) {
                                        out.fields["person_companies"]?.let { metadata["corpwiki_person_companies"] = it }
                                        out.fields["positions"]?.let { metadata["opencorp_positions"] = it }
                                        out.fields["person_states"]?.let { metadata["corpwiki_person_states"] = it }
                                        out.fields["officer_matches"]?.let { metadata["officer_matches"] = it }
                                    }
                                    handleScrapeOut("OpenCorporates", "https://api.opencorporates.com/v0.4/officers/search?q=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow, 0.85)
                                }
                            }
                            launch {
                                send(SearchProgressEvent.Checking("FBI Wanted"))
                                val out = scrapeFbiWanted(primaryQuery, state)
                                if (out.found) {
                                    out.fields["matches"]?.let { metadata["fbi_wanted_matches"] = it }
                                    out.fields["urls"]?.let { metadata["fbi_wanted_urls"] = it }
                                    out.fields["match_count"]?.let { metadata["fbi_wanted_count"] = it }
                                }
                                handleScrapeOut("FBI Wanted", "https://api.fbi.gov/wanted/v1/list?title=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow, 0.9)
                            }
                            launch {
                                send(SearchProgressEvent.Checking("NPI Registry"))
                                val out = scrapeNpiRegistry(primaryQuery, state, city)
                                if (out.found) {
                                    out.fields["providers"]?.let { metadata["npi_providers"] = it }
                                    out.fields["provider_count"]?.let { metadata["npi_provider_count"] = it }
                                }
                                handleScrapeOut("NPI Registry", "https://npiregistry.cms.hhs.gov/", out, sources, metadata, this@channelFlow, 0.8)
                            }
                            if (state.isNotBlank()) {
                                launch {
                                    send(SearchProgressEvent.Checking("OpenFEC"))
                                    val fecKey = apiKeys.openFecKey.ifBlank { apiKeys.getKey("openfec") ?: "DEMO_KEY" }
                                    val out = scrapeOpenFec(primaryQuery, state, fecKey)
                                    if (out.found) {
                                        out.fields["candidates"]?.let { metadata["openfec_candidates"] = it }
                                        out.fields["candidate_count"]?.let { metadata["openfec_count"] = it }
                                    }
                                    handleScrapeOut("OpenFEC", "https://api.open.fec.gov/v1/candidates/search/?q=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow, 0.75)
                                }
                            }
                        }
                        if (personUsername.isNotBlank()) {
                            if (SubjectSearchOrchestrator.shouldRunScraper("sherlock", searchPhase, subjectIntent, activeCategories) ||
                                SubjectSearchOrchestrator.shouldRunScraper("maigret", searchPhase, subjectIntent, activeCategories)
                            ) {
                                launch {
                                    semaphore.withPermit {
                                        inHouseRunner.runUsernameScan(personUsername, metadata, sources, this@channelFlow)
                                        maybeRunTermuxUsernameTools(personUsername, metadata, sources, this@channelFlow)
                                    }
                                }
                            }
                        }
                        if (personEmail.isNotBlank() &&
                            SubjectSearchOrchestrator.shouldRunScraper("holehe", searchPhase, subjectIntent, activeCategories)
                        ) {
                            launch {
                                semaphore.withPermit {
                                    inHouseRunner.runEmailRegistrationScan(personEmail, metadata, sources, this@channelFlow)
                                    maybeRunTermuxHolehe(personEmail, metadata, sources, this@channelFlow)
                                }
                            }
                        }
                        if (AppSettings.isTermuxFallbackEnabled(appCtx) && termuxRunner.canRunCommands() &&
                            SubjectSearchOrchestrator.shouldRunScraper("theHarvester", searchPhase, subjectIntent, activeCategories)
                        ) {
                            launch {
                                semaphore.withPermit {
                                    termuxRunner.runTheHarvester(primaryQuery).collect { send(it) }
                                }
                            }
                        }
                        launch {
                            runAutoDorks(
                                subjectProfile,
                                GoogleDorkLibrary.DorkSearchType.PERSON,
                                metadata,
                                sources,
                                this@channelFlow,
                                semaphore,
                                searchPhase,
                                aggregate = DorkAggregateBuffers(
                                    phones = ddgPhones,
                                    emails = ddgEmails,
                                    addresses = ddgAddresses,
                                    relatives = ddgRelatives,
                                    ages = ddgAges,
                                    social = ddgSocial,
                                    profiles = ddgProfiles,
                                    snippets = ddgSnippets
                                )
                            )
                        }
                    }
                    "email", "breach" -> {
                        targetedScraperNames += setOf("ProxyNova", "HackerTarget", "LeakCheck", "EmailRep", "Kickbox Disposable", "Gravatar")
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
                            send(SearchProgressEvent.Checking("Kickbox Disposable"))
                            val out = scrapeKickboxDisposable(primaryQuery)
                            if (out.found) {
                                out.fields["disposable"]?.let { metadata["kickbox_disposable"] = it }
                                out.fields["did_you_mean"]?.let { metadata["kickbox_did_you_mean"] = it }
                            }
                            handleScrapeOut("Kickbox Disposable", "https://open.kickbox.com/v1/disposable/${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
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
                            runAutoDorks(
                                subjectProfile.copy(email = primaryQuery),
                                GoogleDorkLibrary.DorkSearchType.EMAIL,
                                metadata, sources, this@channelFlow, semaphore, searchPhase
                            )
                        }
                        launch {
                            semaphore.withPermit {
                                val key = apiKeys.getKey("hibp")
                                if (key.isNullOrBlank()) return@withPermit
                                    send(SearchProgressEvent.Checking("HaveIBeenPwned"))
                                    try {
                                        val breaches = RetrofitClient.hibpService.getBreaches(primaryQuery, key)
                                        if (breaches.isEmpty()) {
                                            send(SearchProgressEvent.NotFound("HaveIBeenPwned"))
                                        } else {
                                            metadata["hibp_found"] = "true"
                                            metadata["hibp_count"] = breaches.size.toString()
                                            metadata["hibp_breach_count"] = breaches.size.toString()
                                            metadata["hibp_names"] = breaches.mapNotNull { it.title }.joinToString(", ")
                                            sources.add(DataSource("HaveIBeenPwned", "https://haveibeenpwned.com/account/${encode(primaryQuery)}", java.util.Date(), 0.9))
                                            send(SearchProgressEvent.Found("HaveIBeenPwned", "${breaches.size} breach(es) found"))
                                            apiKeys.recordUsage("hibp")
                                        }
                                        try {
                                            val pastes = RetrofitClient.hibpService.getPastes(primaryQuery, key)
                                            if (pastes.isNotEmpty()) {
                                                metadata["hibp_paste_count"] = pastes.size.toString()
                                                metadata["paste_count"] = pastes.size.toString()
                                                metadata["hibp_pastes"] = pastes.mapNotNull { it.source }.joinToString(", ")
                                                send(SearchProgressEvent.Found("HaveIBeenPwned Pastes", "${pastes.size} paste(s) found"))
                                            }
                                        } catch (_: Exception) { /* pastes optional */ }
                                    } catch (e: HttpException) {
                                        if (e.code() == 404) {
                                            send(SearchProgressEvent.NotFound("HaveIBeenPwned"))
                                        } else {
                                            send(SearchProgressEvent.Blocked("HaveIBeenPwned"))
                                        }
                                    } catch (_: Exception) {
                                        send(SearchProgressEvent.Blocked("HaveIBeenPwned"))
                                    }

                            }
                        }
                        launch {
                            semaphore.withPermit {
                                val key = apiKeys.getKey("hunter")
                                if (key.isNullOrBlank()) return@withPermit
                                    send(SearchProgressEvent.Checking("Hunter.io Verify"))
                                    try {
                                        val result = RetrofitClient.hunterService.verifyEmail(primaryQuery, key)
                                        val data = result.data
                                        if (data == null) {
                                            send(SearchProgressEvent.NotFound("Hunter.io Verify"))
                                        } else {
                                            metadata["hunter_verify_score"] = data.score?.toString() ?: ""
                                            metadata["hunter_verify_status"] = data.deliverability ?: ""
                                            metadata["hunter_verify_result"] = data.result ?: ""
                                            sources.add(DataSource("Hunter.io Verify", "https://hunter.io/email-verifier", Date(), 0.85))
                                            send(SearchProgressEvent.Found("Hunter.io Verify", data.result ?: data.deliverability ?: "Verified"))
                                            apiKeys.recordUsage("hunter")
                                        }
                                    } catch (_: Exception) {
                                        send(SearchProgressEvent.Blocked("Hunter.io Verify"))
                                    }

                            }
                        }
                        launch {
                            semaphore.withPermit {
                                inHouseRunner.runEmailRegistrationScan(primaryQuery, metadata, sources, this@channelFlow)
                                maybeRunTermuxHolehe(primaryQuery, metadata, sources, this@channelFlow)
                            }
                        }
                    }
                    "domain", "ip" -> {
                        targetedScraperNames += setOf("HackerTarget Host", "Wayback CDX", "crt.sh", "DarkSearch", "Ahmia", "Google News", "ip-api", "ipwho.is", "ipinfo", "BGPView", "Shodan InternetDB", "RDAP", "AbuseIPDB", "VirusTotal", "URLScan", "URLhaus")
                        launch { ensureDarkWebTor(this@channelFlow) }
                        launch {
                            send(SearchProgressEvent.Checking("HackerTarget Host"))
                            val out = scrapeHackerTarget(primaryQuery, "host")
                            handleScrapeOut("HackerTarget Host", "https://api.hackertarget.com/hostsearch/?q=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("Wayback CDX"))
                            val out = scrapeWaybackCdx(primaryQuery)
                            handleScrapeOut("Wayback CDX", "https://web.archive.org/cdx/search/cdx?url=${encode(primaryQuery)}/*", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            semaphore.withPermit {
                                val key = apiKeys.getKey("hunter")
                                if (key.isNullOrBlank()) return@withPermit
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
                                            apiKeys.recordUsage("hunter")
                                        }
                                    } catch (_: Exception) {
                                        send(SearchProgressEvent.Blocked("Hunter.io"))
                                    }

                            }
                        }
                        if (AppSettings.isTermuxFallbackEnabled(appCtx) && termuxRunner.canRunCommands()) {
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
                            ensureDarkWebTor(this@channelFlow)
                            runDarkWebSearches(subjectProfile, primaryQuery, sources, metadata, this@channelFlow)
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
                            send(SearchProgressEvent.Checking("ipwho.is"))
                            val out = scrapeIpWho(primaryQuery)
                            handleScrapeOut("ipwho.is", "https://ipwho.is/$primaryQuery", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("ipinfo"))
                            val out = scrapeIpInfo(primaryQuery)
                            if (out.found) {
                                out.fields["city"]?.let { metadata["ipinfo_city"] = it }
                                out.fields["region"]?.let { metadata["ipinfo_region"] = it }
                                out.fields["org"]?.let { metadata["ipinfo_org"] = it }
                                out.fields["coords"]?.let { metadata["ipinfo_coords"] = it }
                            }
                            handleScrapeOut("ipinfo", "https://ipinfo.io/$primaryQuery", out, sources, metadata, this@channelFlow)
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
                        launch {
                            val ipTarget = if (isIpAddress(primaryQuery)) primaryQuery.trim() else null
                            val domainTarget = fields["domain"]?.takeIf { isLikelyDomain(it) }
                                ?: primaryQuery.takeIf { isLikelyDomain(it) }
                            if (ipTarget != null) {
                                val key = apiKeys.abuseIpDbKey
                                if (key.isNotBlank()) {
                                    send(SearchProgressEvent.Checking("AbuseIPDB"))
                                    val out = scrapeAbuseIpDb(ipTarget, key)
                                    if (out.found) {
                                        out.fields["score"]?.let { metadata["abuseipdb_score"] = it }
                                        out.fields["reports"]?.let { metadata["abuseipdb_reports"] = it }
                                    }
                                    handleScrapeOut("AbuseIPDB", "https://www.abuseipdb.com/check/$ipTarget", out, sources, metadata, this@channelFlow)
                                }
                            }
                            val vtTarget = ipTarget ?: domainTarget
                            if (vtTarget != null) {
                                val key = apiKeys.virusTotalKey
                                if (key.isNotBlank()) {
                                    send(SearchProgressEvent.Checking("VirusTotal"))
                                    val out = scrapeVirusTotal(vtTarget, key)
                                    if (out.found) {
                                        out.fields["malicious"]?.let { metadata["vt_malicious"] = it }
                                        out.fields["harmless"]?.let { metadata["vt_harmless"] = it }
                                        out.fields["suspicious"]?.let { metadata["vt_suspicious"] = it }
                                        out.fields["reputation"]?.let { metadata["vt_reputation"] = it }
                                        out.fields["country"]?.let { metadata["vt_country"] = it }
                                        out.fields["as_owner"]?.let { metadata["vt_as_owner"] = it }
                                    }
                                    handleScrapeOut("VirusTotal", "https://www.virustotal.com/gui/search/$vtTarget", out, sources, metadata, this@channelFlow)
                                }
                            }
                            if (domainTarget != null) {
                                val urlScanKey = apiKeys.urlScanKey
                                if (urlScanKey.isNotBlank()) {
                                    send(SearchProgressEvent.Checking("URLScan"))
                                    val out = scrapeUrlScan(domainTarget, urlScanKey)
                                    if (out.found) {
                                        out.fields["total_scans"]?.let { metadata["urlscan_total_scans"] = it }
                                        out.fields["malicious_scans"]?.let { metadata["urlscan_malicious_scans"] = it }
                                        out.fields["ips"]?.let { metadata["urlscan_ips"] = it }
                                    }
                                    handleScrapeOut("URLScan", "https://urlscan.io/search/#domain:$domainTarget", out, sources, metadata, this@channelFlow)
                                }
                                val urlhausKey = apiKeys.urlhausKey
                                if (urlhausKey.isNotBlank()) {
                                    send(SearchProgressEvent.Checking("URLhaus"))
                                    val out = scrapeUrlhaus(domainTarget, urlhausKey)
                                    if (out.found) {
                                        out.fields["status"]?.let { metadata["urlhaus_status"] = it }
                                        out.fields["urls_count"]?.let { metadata["urlhaus_urls_count"] = it }
                                    }
                                    handleScrapeOut("URLhaus", "https://urlhaus.abuse.ch/browse/host/$domainTarget/", out, sources, metadata, this@channelFlow)
                                }
                            }
                        }
                    }
                    "phone" -> {
                        val phoneDigits = primaryQuery.replace(Regex("[^0-9]"), "").takeLast(10)
                        val phoneFmt = if (phoneDigits.length >= 10) {
                            "${phoneDigits.substring(0, 3)}-${phoneDigits.substring(3, 6)}-${phoneDigits.substring(6, 10)}"
                        } else primaryQuery
                        targetedScraperNames += setOf(
                            "800notes", "DDG Phone", "Numverify", "libphonenumber", "CallTracer",
                            "DarkSearch", "Ahmia", "ThatsThem", "FastPeopleSearch", "USPhoneBook"
                        )
                        launch { ensureDarkWebTor(this@channelFlow) }
                        val phoneDdgQueries = listOf(
                            "Phone Owner" to "\"$primaryQuery\" who called owner reverse lookup",
                            "Phone FPS" to "site:fastpeoplesearch.com \"$phoneFmt\"",
                            "Phone TPS" to "site:truepeoplesearch.com \"$phoneFmt\"",
                            "Phone Spokeo" to "site:spokeo.com \"$phoneFmt\"",
                            "Phone Whitepages" to "site:whitepages.com \"$phoneFmt\"",
                            "Phone Social" to "\"$primaryQuery\" facebook linkedin profile",
                            "Phone Address" to "\"$primaryQuery\" address owner",
                            "Phone Criminal" to "\"$primaryQuery\" criminal arrest record",
                            "Phone Relatives" to "\"$primaryQuery\" relatives family"
                        )
                        for ((label, q) in phoneDdgQueries) {
                            launch {
                                semaphore.withPermit {
                                    send(SearchProgressEvent.Checking("DDG: $label"))
                                    try {
                                        withTimeout(SubjectSearchOrchestrator.SOURCE_TIMEOUT_MS) {
                                            val results = ddgHtmlSearch(q)
                                            if (results.isNotEmpty()) {
                                                sources.add(DataSource("DDG:$label", "https://html.duckduckgo.com/html/?q=${encode(q)}", Date(), 0.6))
                                                appendMetadata(metadata, "phone_search_snippets", results.take(3).joinToString("\n") { "${it.title}: ${it.snippet}".take(120) })
                                                val phoneSubject = subjectProfile.copy(phone = phoneFmt)
                                                val extracted = extractDataFromDdgResults(results, phoneSubject)
                                                if (extracted.names.isNotEmpty()) {
                                                    appendMetadata(metadata, "phone_owner_names", extracted.names.joinToString(", "))
                                                }
                                                if (extracted.addresses.isNotEmpty()) {
                                                    appendMetadata(metadata, "search_addresses", extracted.addresses.joinToString(" | "))
                                                }
                                                send(SearchProgressEvent.Found("DDG: $label", results.firstOrNull()?.snippet?.take(100) ?: ""))
                                            } else {
                                                send(SearchProgressEvent.NotFound("DDG: $label"))
                                            }
                                        }
                                    } catch (_: Exception) {
                                        send(SearchProgressEvent.Failed("DDG: $label", "timeout"))
                                    }
                                }
                            }
                        }
                        launchPhoneIntelScrapers(
                            primaryQuery, city, state, metadata, sources, this@channelFlow, semaphore
                        )
                        launch {
                            send(SearchProgressEvent.Checking("DarkSearch"))
                            val out = searchDarkWeb(primaryQuery)
                            if (out.found) out.fields["dark_links"]?.let { metadata["darksearch_links"] = it }
                            handleScrapeOut("DarkSearch", "https://darksearch.io/api/search?query=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow, 0.55)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("Ahmia"))
                            val out = scrapeAhmia(primaryQuery)
                            applyAhmiaMetadata(out, metadata)
                            handleScrapeOut("Ahmia", "https://ahmia.fi/search/?q=${encode(primaryQuery)}", out, sources, metadata, this@channelFlow, 0.55)
                        }
                        launch {
                            runAutoDorks(
                                subjectProfile.copy(phone = phoneFmt, name = ""),
                                GoogleDorkLibrary.DorkSearchType.PHONE,
                                metadata, sources, this@channelFlow, semaphore, searchPhase
                            )
                        }
                    }
                    "company" -> {
                        val companyName = fields["company"] ?: fields["name"] ?: primaryQuery
                        val companyDomain = fields["domain"]?.takeIf { isLikelyDomain(it) }
                        metadata["company_name"] = companyName
                        companyDomain?.let { metadata["company_domain"] = it }
                        targetedScraperNames += setOf("SEC EDGAR", "GLEIF", "Google News", "Wikidata", "RDAP")
                        launch {
                            send(SearchProgressEvent.Checking("SEC EDGAR"))
                            val out = scrapeSecEdgar(companyName)
                            if (out.found) {
                                out.fields["total_hits"]?.let { metadata["sec_filings_count"] = it }
                                out.fields["form_types"]?.let { metadata["sec_filing_types"] = it }
                            }
                            handleScrapeOut("SEC EDGAR", "https://www.sec.gov/edgar/search/#/q=${encode(companyName)}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("GLEIF"))
                            val out = scrapeGleif(companyName)
                            if (out.found) {
                                out.fields["legal_entities"]?.let { metadata["gleif_company_entities"] = it }
                            }
                            handleScrapeOut("GLEIF", "https://search.gleif.org/#/record/${encode(companyName)}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("Google News"))
                            val out = scrapeGoogleNews(companyName)
                            handleScrapeOut("Google News", "https://news.google.com/rss/search?q=${encode(companyName)}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("Wikidata"))
                            val out = scrapeWikidata(companyName)
                            if (out.found) {
                                out.fields["descriptions"]?.let { metadata["wikidata_company_descriptions"] = it }
                                out.fields["link"]?.let { metadata["wikidata_link"] = it }
                            }
                            handleScrapeOut("Wikidata", "https://www.wikidata.org/w/index.php?search=${encode(companyName)}", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            runAutoDorks(
                                SubjectProfile(name = companyName, city = city, state = state),
                                GoogleDorkLibrary.DorkSearchType.COMPANY,
                                metadata, sources, this@channelFlow, semaphore, searchPhase
                            )
                        }
                        if (companyDomain != null) {
                            targetedScraperNames += setOf("VirusTotal", "URLScan", "URLhaus", "RDAP")
                            launch {
                                send(SearchProgressEvent.Checking("RDAP"))
                                val out = scrapeRdap(companyDomain)
                                if (out.found) {
                                    out.fields["registrant"]?.let { metadata["rdap_registrant"] = it }
                                    out.fields["registrant_org"]?.let { metadata["rdap_org"] = it }
                                    out.fields["registered"]?.let { metadata["rdap_registered"] = it }
                                    out.fields["expires"]?.let { metadata["rdap_expires"] = it }
                                    out.fields["nameservers"]?.let { metadata["rdap_nameservers"] = it }
                                }
                                handleScrapeOut("RDAP", "https://rdap.org/domain/$companyDomain", out, sources, metadata, this@channelFlow)
                            }
                            launch {
                                val key = apiKeys.virusTotalKey
                                if (key.isNotBlank()) {
                                    send(SearchProgressEvent.Checking("VirusTotal"))
                                    val out = scrapeVirusTotal(companyDomain, key)
                                    if (out.found) {
                                        out.fields["malicious"]?.let { metadata["vt_malicious"] = it }
                                        out.fields["harmless"]?.let { metadata["vt_harmless"] = it }
                                        out.fields["suspicious"]?.let { metadata["vt_suspicious"] = it }
                                        out.fields["reputation"]?.let { metadata["vt_reputation"] = it }
                                    }
                                    handleScrapeOut("VirusTotal", "https://www.virustotal.com/gui/domain/$companyDomain", out, sources, metadata, this@channelFlow)
                                }
                            }
                            launch {
                                val key = apiKeys.urlScanKey
                                if (key.isNotBlank()) {
                                    send(SearchProgressEvent.Checking("URLScan"))
                                    val out = scrapeUrlScan(companyDomain, key)
                                    if (out.found) {
                                        out.fields["total_scans"]?.let { metadata["urlscan_total_scans"] = it }
                                        out.fields["malicious_scans"]?.let { metadata["urlscan_malicious_scans"] = it }
                                        out.fields["ips"]?.let { metadata["urlscan_ips"] = it }
                                    }
                                    handleScrapeOut("URLScan", "https://urlscan.io/search/#domain:$companyDomain", out, sources, metadata, this@channelFlow)
                                }
                            }
                            launch {
                                val urlhausKey = apiKeys.urlhausKey
                                if (urlhausKey.isNotBlank()) {
                                    send(SearchProgressEvent.Checking("URLhaus"))
                                    val out = scrapeUrlhaus(companyDomain, urlhausKey)
                                    if (out.found) {
                                        out.fields["status"]?.let { metadata["urlhaus_status"] = it }
                                        out.fields["urls_count"]?.let { metadata["urlhaus_urls_count"] = it }
                                    }
                                    handleScrapeOut("URLhaus", "https://urlhaus.abuse.ch/browse/host/$companyDomain/", out, sources, metadata, this@channelFlow)
                                }
                            }
                            launch {
                                semaphore.withPermit {
                                    val key = apiKeys.getKey("clearbit")
                                    if (key.isNullOrBlank()) return@withPermit
                                        send(SearchProgressEvent.Checking("Clearbit"))
                                        try {
                                            val token = if (key.startsWith("Bearer ", ignoreCase = true)) key else "Bearer $key"
                                            val resp = RetrofitClient.clearbitService.findCompany(companyDomain, token)
                                            val company = resp.body()
                                            if (resp.isSuccessful && company != null) {
                                                metadata["clearbit_found"] = "true"
                                                company.name?.let { metadata["clearbit_name"] = it }
                                                company.description?.let { metadata["clearbit_description"] = it }
                                                company.industry?.let { metadata["clearbit_industry"] = it }
                                                company.location?.let { metadata["clearbit_location"] = it }
                                                company.logo?.let { metadata["company_logo_url"] = it }
                                                company.employees?.let { metadata["clearbit_employees"] = it.toString() }
                                                sources.add(DataSource("Clearbit", "https://clearbit.com/", Date(), 0.9))
                                                send(SearchProgressEvent.Found("Clearbit", company.name ?: companyDomain))
                                                apiKeys.recordUsage("clearbit")
                                            } else {
                                                send(SearchProgressEvent.NotFound("Clearbit"))
                                            }
                                        } catch (_: Exception) {
                                            send(SearchProgressEvent.Blocked("Clearbit"))
                                        }
                                }
                            }
                            launch {
                                semaphore.withPermit {
                                    val key = apiKeys.getKey("builtwith")
                                    if (key.isNullOrBlank()) return@withPermit
                                        send(SearchProgressEvent.Checking("BuiltWith"))
                                        try {
                                            val resp = RetrofitClient.builtWithService.lookup(key, companyDomain)
                                            if (resp.isSuccessful && resp.body() != null) {
                                                metadata["builtwith_found"] = "true"
                                                sources.add(DataSource("BuiltWith", "https://builtwith.com/$companyDomain", Date(), 0.85))
                                                send(SearchProgressEvent.Found("BuiltWith", "Tech stack lookup complete"))
                                                apiKeys.recordUsage("builtwith")
                                            } else {
                                                send(SearchProgressEvent.NotFound("BuiltWith"))
                                            }
                                        } catch (_: Exception) {
                                            send(SearchProgressEvent.Blocked("BuiltWith"))
                                        }
                                }
                            }
                            launch {
                                semaphore.withPermit {
                                    val key = apiKeys.getKey("hunter")
                                    if (key.isNullOrBlank()) return@withPermit
                                        send(SearchProgressEvent.Checking("Hunter.io"))
                                        try {
                                            val result = RetrofitClient.hunterService.domainSearch(companyDomain, key)
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
                                                sources.add(DataSource("Hunter.io", "https://hunter.io/domain-search/${encode(companyDomain)}", Date(), 0.85))
                                                send(SearchProgressEvent.Found("Hunter.io", org ?: "${emailList?.size ?: 0} email(s)"))
                                                apiKeys.recordUsage("hunter")
                                            }
                                        } catch (_: Exception) {
                                            send(SearchProgressEvent.Blocked("Hunter.io"))
                                        }
                                }
                            }
                        }
                    }
                    "vehicle", "vin" -> {
                        val vin = fields["vin"]?.trim()?.uppercase()?.filter { it.isLetterOrDigit() }
                            ?: primaryQuery.trim().uppercase().filter { it.isLetterOrDigit() }
                        val plate = fields["plate"]?.trim()?.uppercase()?.replace(Regex("\\s+"), " ")
                        if (!vin.isNullOrBlank()) metadata["vehicle_vin"] = vin
                        if (!plate.isNullOrBlank()) {
                            metadata["vehicle_plate"] = plate
                            metadata["vehicle_plates"] = plate
                        }
                        if (vin.length == 17) {
                            targetedScraperNames += setOf("NHTSA VIN")
                            launch {
                                send(SearchProgressEvent.Checking("NHTSA VIN"))
                                val out = scrapeNhtsaVin(vin)
                                if (out.found) {
                                    out.fields["make"]?.let {
                                        metadata["vehicle_make"] = it
                                        metadata["vehicle_makes"] = it
                                    }
                                    out.fields["model"]?.let {
                                        metadata["vehicle_model"] = it
                                        metadata["vehicle_models"] = it
                                    }
                                    out.fields["year"]?.let { metadata["vehicle_year"] = it }
                                    out.fields["body_class"]?.let { metadata["vehicle_body"] = it }
                                    out.fields["fuel_type"]?.let { metadata["vehicle_fuel"] = it }
                                    out.fields["manufacturer"]?.let { metadata["vehicle_manufacturer"] = it }
                                    metadata["vehicle_records"] = listOfNotNull(
                                        out.fields["year"],
                                        out.fields["make"],
                                        out.fields["model"]
                                    ).joinToString(" ").trim()
                                }
                                handleScrapeOut(
                                    "NHTSA VIN",
                                    "https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVin/$vin?format=json",
                                    out, sources, metadata, this@channelFlow
                                )
                                apiKeys.recordUsage("nhtsa")
                            }
                        } else if (!plate.isNullOrBlank()) {
                            metadata["vehicle_search_note"] = "License plate detected. Browser tools are available, but no in-app plate decoder is configured."
                            send(SearchProgressEvent.NotFound("NHTSA VIN"))
                        }
                    }
                    "wifi", "mac", "ssid" -> {
                        targetedScraperNames += setOf("WiGLE", "DDG WiFi")
                        launch {
                            val key = apiKeys.wigleKey
                            if (key.isBlank()) return@launch
                            send(SearchProgressEvent.Checking("WiGLE"))
                                val out = scrapeWigle(primaryQuery, effectiveType, key)
                                if (out.found) {
                                    out.fields["ssid"]?.let { metadata["wifi_ssid"] = it }
                                    out.fields["bssid"]?.let { metadata["wifi_bssid"] = it }
                                    out.fields["location"]?.let { metadata["wifi_location"] = it }
                                    out.fields["channel"]?.let { metadata["wifi_channel"] = it }
                                    out.fields["encryption"]?.let { metadata["wifi_encryption"] = it }
                                }
                                handleScrapeOut("WiGLE", "https://api.wigle.net/api/v2/network/search", out, sources, metadata, this@channelFlow)
                                apiKeys.recordUsage("wigle")
                        }
                        launch {
                            semaphore.withPermit {
                                send(SearchProgressEvent.Checking("DDG WiFi"))
                                val q = when (effectiveType) {
                                    "mac" -> "\"$primaryQuery\" wifi bssid location"
                                    "ssid" -> "\"$primaryQuery\" wifi network location"
                                    else -> "\"$primaryQuery\" wifi network geolocation"
                                }
                                val results = ddgHtmlSearch(q)
                                if (results.isNotEmpty()) {
                                    metadata["wifi_ddg_snippets"] = results.take(5).joinToString("\n") { "${it.title}: ${it.snippet}".take(150) }
                                    sources.add(DataSource("DDG WiFi", "https://html.duckduckgo.com/html/?q=${encode(q)}", Date(), 0.5))
                                    send(SearchProgressEvent.Found("DDG WiFi", results.first().snippet.take(100)))
                                } else {
                                    send(SearchProgressEvent.NotFound("DDG WiFi"))
                                }
                            }
                        }
                    }
                    "image" -> {
                        targetedScraperNames += "Image EXIF"
                        launch {
                            send(SearchProgressEvent.Checking("Image EXIF"))
                            val out = scrapeImageExif(primaryQuery)
                            if (out.found) {
                                out.fields.forEach { (k, v) -> metadata["image_$k"] = v }
                                if (out.fields["gps"]?.isNotBlank() == true) {
                                    metadata["image_search_note"] = "GPS coordinates extracted from photo metadata"
                                }
                            }
                            handleScrapeOut("Image EXIF", primaryQuery, out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("DDG Reverse Image"))
                            val results = ddgHtmlSearch("reverse image search metadata photo")
                            if (results.isNotEmpty()) {
                                metadata["image_ddg_snippets"] = results.take(3).joinToString("\n") { it.snippet.take(120) }
                                sources.add(DataSource("DDG Reverse Image", "https://html.duckduckgo.com/html/", Date(), 0.5))
                                send(SearchProgressEvent.Found("DDG Reverse Image", results.first().snippet.take(100)))
                            } else {
                                send(SearchProgressEvent.NotFound("DDG Reverse Image"))
                            }
                        }
                    }
                    "darknet" -> {
                        targetedScraperNames += setOf("Ahmia", "DarkSearch")
                        launch {
                            ensureDarkWebTor(this@channelFlow)
                            runDarkWebSearches(subjectProfile, primaryQuery, sources, metadata, this@channelFlow)
                        }
                    }
                    "username" -> {
                        targetedScraperNames += setOf("GitHub", "Reddit")
                        launch {
                            runUsernameDiscovery(
                                usernames = listOf(primaryQuery),
                                socialUrls = emptyList(),
                                metadata = metadata,
                                sources = sources,
                                channel = this@channelFlow
                            )
                        }
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
                                val ghUrl = out.fields["profile_url"] ?: "https://github.com/$primaryQuery"
                                metadata["github_url"] = ghUrl
                                if (!metadata["found_urls"].orEmpty().contains("GitHub:")) {
                                    appendMetadata(metadata, "found_urls", "GitHub: $ghUrl")
                                }
                            }
                            handleScrapeOut("GitHub", "https://github.com/$primaryQuery", out, sources, metadata, this@channelFlow)
                        }
                        launch {
                            send(SearchProgressEvent.Checking("Reddit"))
                            val out = scrapeReddit(primaryQuery)
                            if (out.found) {
                                if (metadata["profile_photo_url"].isNullOrBlank()) out.fields["image_url"]?.let { metadata["profile_photo_url"] = it }
                                val rdUrl = out.fields["profile_url"] ?: "https://www.reddit.com/user/$primaryQuery"
                                metadata["reddit_url"] = rdUrl
                                appendMetadata(metadata, "found_urls", "Reddit: $rdUrl")
                            }
                            handleScrapeOut("Reddit", "https://www.reddit.com/user/$primaryQuery", out, sources, metadata, this@channelFlow)
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

                // OSINT Framework tools run in-app via scrapers / InHouseOsintRunner — not external browser tabs.
            }

            val nameTokens = primaryQuery.lowercase().split(" ").filter { it.length > 1 }
            if (System.currentTimeMillis() - searchStartMs < SubjectSearchOrchestrator.secondaryPassThresholdMs(fastMode) ||
                System.currentTimeMillis() - searchStartMs < minPhaseMs
            ) {
                runSecondaryPassesUntilMinimum(
                    startMs = searchStartMs,
                    minMs = minPhaseMs,
                    name = primaryQuery,
                    city = city,
                    state = state,
                    phone = fields["phone"] ?: "",
                    email = fields["email"] ?: "",
                    phase = searchPhase,
                    nameTokens = nameTokens,
                    metadata = metadata,
                    sources = sources,
                    channel = this@channelFlow,
                    semaphore = semaphore
                )
            }

            if (SubjectSearchOrchestrator.shouldRunDarkWeb(searchPhase, subjectIntent)) {
                send(SearchProgressEvent.PhaseUpdate("Dark web", "Tor + onion index sweep"))
            }

            if (effectiveType == "person" || effectiveType == "comprehensive") {
                val queryPhone = fields["phone"] ?: ""
                val distinctPhones = ddgPhones.distinct().filter { phone ->
                    SubjectFilter.shouldAcceptPersonPhone(
                        phone, ddgSnippets.joinToString(" "), queryPhone, primaryQuery, city, state
                    )
                }.take(10)
                val distinctEmails = ddgEmails.distinct().take(5)
                val distinctRelatives = ddgRelatives.distinct().take(15)
                val distinctAges = ddgAges.distinct()
                val allAddresses = ddgAddresses.distinct()
                val stateFilteredAddresses = SubjectFilter.filterByGeo(allAddresses, city, state).take(8)
                val distinctSocial = ddgSocial.distinct().take(10)
                val distinctProfiles = ddgProfiles.distinct().take(10)
                if (distinctPhones.isNotEmpty()) {
                    metadata["search_phones"] = distinctPhones.joinToString(", ")
                    metadata["search_phones_source_url"] = "https://html.duckduckgo.com/html/?q=${encode(primaryQuery)}"
                    metadata["search_phones_verified"] = "true"
                }
                if (distinctEmails.isNotEmpty()) metadata["search_emails"] = distinctEmails.joinToString(", ")
                if (distinctRelatives.isNotEmpty()) metadata["search_relatives"] = distinctRelatives.joinToString(", ")
                if (distinctAges.isNotEmpty()) metadata["search_age"] = distinctAges.first()
                if (stateFilteredAddresses.isNotEmpty()) {
                    metadata["search_addresses"] = stateFilteredAddresses.joinToString("\n")
                    metadata["ddg_addresses"] = stateFilteredAddresses.joinToString("\n")
                }
                if (distinctSocial.isNotEmpty()) metadata["search_social_links"] = distinctSocial.joinToString("\n")
                if (distinctProfiles.isNotEmpty()) metadata["search_profile_links"] = distinctProfiles.joinToString("\n")
                if (metadata["sites_checked"].isNullOrBlank()) {
                    val usernameCandidates = buildList {
                        fields["username"]?.takeIf { it.isNotBlank() }?.let { add(it) }
                        fields["email"]?.takeIf { it.isNotBlank() }?.let { email ->
                            addAll(com.twoskoops707.sixdegrees.data.osint.UsernamePlatformRegistry.candidatesFromEmail(email))
                        }
                    }.distinct()
                    if (usernameCandidates.isNotEmpty() || distinctSocial.isNotEmpty()) {
                        runUsernameDiscovery(
                            usernames = usernameCandidates,
                            socialUrls = distinctSocial,
                            metadata = metadata,
                            sources = sources,
                            channel = this@channelFlow
                        )
                    }
                }
                if (ddgSnippets.isNotEmpty()) metadata["search_snippets"] = ddgSnippets.distinct().take(20).joinToString("\n").take(3000)
                if (distinctPhones.isNotEmpty() || distinctAges.isNotEmpty() || stateFilteredAddresses.isNotEmpty() || distinctRelatives.isNotEmpty()) {
                    val rec = PersonRecord(
                        name = primaryQuery,
                        age = distinctAges.firstOrNull() ?: "",
                        location = stateFilteredAddresses.firstOrNull()?.take(80) ?: locationStr,
                        phones = distinctPhones,
                        address = stateFilteredAddresses.firstOrNull() ?: locationStr,
                        relatives = distinctRelatives,
                        source = "DDG Search"
                    )
                    scrapedPersonRecords.add(rec)
                }

                val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
                val street = fields["address"]?.trim().orEmpty()
                val fullLoc = listOf(street, city, state).filter { it.isNotBlank() }.joinToString(", ")
                val locEncoded = if (fullLoc.isNotBlank()) "+${enc(fullLoc)}" else if (locationStr.isNotBlank()) "+${enc(locationStr)}" else ""
                val nameEnc = enc(primaryQuery)
                val hyphenName = enc(primaryQuery.replace(" ", "-"))
                val verifyLinks = listOf(
                    "DDG: Name + Location" to "https://html.duckduckgo.com/html/?q=%22$nameEnc%22$locEncoded",
                    "DDG: Phone Lookup" to "https://html.duckduckgo.com/html/?q=%22$nameEnc%22+phone+number$locEncoded",
                    "FastPeopleSearch" to "https://www.fastpeoplesearch.com/name/$hyphenName${if (state.isNotBlank()) "/${enc(state.lowercase())}" else ""}",
                    "TruePeopleSearch" to "https://www.truepeoplesearch.com/results?name=$nameEnc&citystatezip=${enc(fullLoc.ifBlank { locationStr })}",
                    "CourtListener" to "https://www.courtlistener.com/?q=$nameEnc$locEncoded&type=p&order_by=score+desc",
                    "JudyRecords" to "https://www.judyrecords.com/search?search=$nameEnc"
                ).joinToString("\n") { (label, url) -> "$label: $url" }
                metadata["dork_browser_verify_links"] = verifyLinks
            }

            finalizeMetadata(metadata)

            val partialPersonId = if (effectiveType == "person" || effectiveType == "comprehensive") {
                buildPersonIdFromMetadata(metadata.toMap(), primaryQuery)
            } else null
            metadata["report_status"] = "partial"
            val partialReportId = try {
                saveReport(query, partialPersonId, sources.toList(), metadata.toMap())
            } catch (_: Exception) { null }
            if (partialReportId != null) {
                send(SearchProgressEvent.PartialResultsReady(partialReportId, sources.size))
            }

            send(SearchProgressEvent.PhaseUpdate("AI brief", "Synthesizing dossier"))
            send(SearchProgressEvent.Checking("AI Brief"))
            val aiReport = withContext(Dispatchers.IO) {
                generateOsintAiReport(primaryQuery, effectiveType, metadata.toMap(), sources.toList())
            }
            if (aiReport != null) {
                aiReport.toMetadataMap().forEach { (k, v) -> metadata[k] = v }
                metadata["ai_facts_only"] = "true"
                val preview = aiReport.executiveSummary.take(100)
                val providerLabel = when (aiReport.provider) {
                    "openrouter" -> "OpenRouter"
                    else -> "Pollinations"
                }
                send(SearchProgressEvent.Found("AI Brief ($providerLabel)", preview))
            } else {
                send(SearchProgressEvent.NotFound("AI Brief"))
            }

            if (AppSettings.isAiAgentAssist(appCtx)) {
                send(SearchProgressEvent.Checking("AI Search Suggestions"))
                val suggestedLinks = withContext(Dispatchers.IO) {
                    generateAiSuggestedSearchLinks(
                        primaryQuery, effectiveType, metadata.toMap(), sources.toList()
                    )
                }
                if (!suggestedLinks.isNullOrBlank()) {
                    metadata["ai_suggested_searches"] = suggestedLinks
                    val count = suggestedLinks.lines().count { it.isNotBlank() }
                    send(SearchProgressEvent.Found("AI Search Suggestions", "$count follow-up searches ready"))
                } else {
                    send(SearchProgressEvent.NotFound("AI Search Suggestions"))
                }
            }

            val personId = if (effectiveType == "person" || effectiveType == "comprehensive") {
                buildPersonIdFromMetadata(metadata.toMap(), primaryQuery)
            } else null
            metadata["report_status"] = "complete"
            val reportId = try {
                saveReport(query, personId, sources.toList(), metadata.toMap())
            } catch (_: Exception) {
                send(SearchProgressEvent.Failed("Search", "Could not save report to database"))
                return@withContext
            }

            if (effectiveType == "person" && round == 1 &&
                !SubjectSearchOrchestrator.skipsCandidateSelection(type, subjectProfile)
            ) {
                val allPersonRecords = scrapedPersonRecords.toList()
                val deduped = deduplicatePersonRecords(allPersonRecords)
                val distinctPhones = (metadata["search_phones"] ?: "").split(",")
                    .map { it.trim() }.filter { it.isNotBlank() }
                val distinctAges = (metadata["search_age"] ?: "").split(",")
                    .map { it.trim() }.filter { it.isNotBlank() }
                val distinctRelatives = (metadata["search_relatives"] ?: "").split(",")
                    .map { it.trim() }.filter { it.length > 3 }
                val geoAddresses = (metadata["search_addresses"] ?: "").lines()
                    .map { it.trim() }.filter { it.isNotBlank() }
                val socialUrls = (metadata["search_social_links"] ?: "").lines()
                    .map { it.trim() }.filter { it.isNotBlank() }

                val rawCandidates = if (deduped.isNotEmpty() || geoAddresses.isNotEmpty()) {
                    buildCandidatesFromDiscovery(
                        records = deduped,
                        addresses = geoAddresses,
                        ages = distinctAges,
                        phones = distinctPhones,
                        relatives = distinctRelatives,
                        socialUrls = socialUrls,
                        name = primaryQuery,
                        city = city,
                        state = state,
                        locationStr = locationStr,
                        sourceCount = sources.size
                    )
                } else emptyList()

                if (rawCandidates.isNotEmpty()) {
                    val candidates = enrichCandidatesWithPhotos(rawCandidates, city, state, this@channelFlow)
                    candidates.forEachIndexed { i, c ->
                        metadata["candidate_${i}_id"] = c.id
                        metadata["candidate_${i}_confidence"] = "%.2f".format(c.confidence)
                        if (c.allPhotoUrls().isNotEmpty()) {
                            metadata["candidate_${i}_photo_urls"] = c.allPhotoUrls().joinToString("|")
                        }
                        if (c.socialLinks.isNotEmpty()) {
                            metadata["candidate_${i}_social_links"] = c.socialLinks.entries.joinToString("|") { "${it.key}=${it.value}" }
                        }
                    }
                    metadata["candidate_count"] = candidates.size.toString()
                    candidates.first().allPhotoUrls().firstOrNull()?.let { metadata["profile_photo_url"] = it }
                    saveReport(query, personId, sources.toList(), metadata.toMap(), reportId)
                    val autoSelect = SubjectSearchOrchestrator.autoSelectAllowed(primaryQuery, candidates.size)
                    val primary = candidates.first()
                    val lockedProfile = SubjectProfile.fromCandidate(primary, subjectProfile)
                    val refinedQuery = lockedProfile.toQueryString()
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
                    val fallbackLoc = geoAddresses.firstOrNull() ?: locationStr
                    val fallbackPhones = distinctPhones
                    val fallbackCandidate = CandidateProfile(
                        name = fallbackName, age = fallbackAge, location = fallbackLoc,
                        phones = fallbackPhones, address = fallbackLoc,
                        source = sources.firstOrNull()?.name ?: "Web",
                        confidence = SubjectSearchOrchestrator.candidateConfidence(sources.size, 0, SubjectFilter.hasGeoConstraint(city, state))
                    )
                    val enriched = enrichCandidatesWithPhotos(listOf(fallbackCandidate), city, state, this@channelFlow)
                    enriched.forEachIndexed { i, c ->
                        metadata["candidate_${i}_id"] = c.id
                        if (c.allPhotoUrls().isNotEmpty()) {
                            metadata["candidate_${i}_photo_urls"] = c.allPhotoUrls().joinToString("|")
                        }
                    }
                    metadata["candidate_count"] = enriched.size.toString()
                    enriched.first().allPhotoUrls().firstOrNull()?.let { metadata["profile_photo_url"] = it }
                    saveReport(query, personId, sources.toList(), metadata.toMap(), reportId)
                    val autoSelect = SubjectSearchOrchestrator.autoSelectAllowed(fallbackName, enriched.size)
                    val lockedProfile = SubjectProfile.fromCandidate(enriched.first(), subjectProfile)
                    send(SearchProgressEvent.CandidatesReady(
                        candidates = enriched, reportId = reportId, round = round,
                        autoSelect = autoSelect,
                        refinedQuery = if (autoSelect) lockedProfile.toQueryString() else ""
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
        channel: kotlinx.coroutines.channels.SendChannel<SearchProgressEvent>,
        reliability: Double = 0.6
    ) {
        when {
            out.skippedBlocked -> channel.send(SearchProgressEvent.Skipped(name, "blocked"))
            out.blocked -> {
                if (!blockedSourceCache.isUrlBlocked(url)) {
                    blockedSourceCache.markUrlBlocked(url)
                }
                channel.send(SearchProgressEvent.Blocked(name))
            }
            out.found -> {
                val detail = out.fields["snippet"]?.take(120) ?: out.fields["title"] ?: ""
                channel.send(SearchProgressEvent.Found(name, detail))
                sources.add(DataSource(name, url, Date(), reliability))
                val key = SOURCE_ABBREVS[name.lowercase()] ?: name.lowercase().replace(" ", "_")
                val confidence = SubjectSearchOrchestrator.formatConfidence(reliability)
                val structuredApi = SubjectFilter.isStructuredApiSource(key)
                val companySource = SubjectFilter.isCompanySource(key, url)
                metadata["${key}_confidence"] = confidence
                metadata["${key}_source_url"] = url
                out.fields.forEach { (k, v) ->
                    if (v.isBlank()) return@forEach
                    val fieldKey = "${key}_$k"
                    when {
                        k == "phones" && companySource -> {
                            metadata["company_phone"] = v
                            metadata["company_phone_source_url"] = url
                            metadata["company_phone_verified"] = structuredApi.toString()
                        }
                        k == "phones" -> {
                            val queryPhone = metadata["person_phone"] ?: metadata["field_phone"] ?: ""
                            val subjectName = metadata["person_name"] ?: ""
                            val city = metadata["person_city"] ?: ""
                            val state = metadata["person_state"] ?: ""
                            val context = out.fields["snippet"] ?: out.fields["title"] ?: v
                            val accepted = v.split(",").map { it.trim() }.filter { phone ->
                                SubjectFilter.shouldAcceptPersonPhone(
                                    phone, context, queryPhone, subjectName, city, state, key, url, structuredApi
                                )
                            }
                            if (accepted.isNotEmpty()) {
                                metadata[fieldKey] = accepted.joinToString(", ")
                                metadata["${fieldKey}_source_url"] = url
                                metadata["${fieldKey}_verified"] = structuredApi.toString()
                            }
                        }
                        else -> {
                            metadata[fieldKey] = v
                            metadata["${fieldKey}_source_url"] = url
                            metadata["${fieldKey}_verified"] = structuredApi.toString()
                        }
                    }
                    metadata["${fieldKey}_confidence"] = confidence
                }
                normalizeScraperFieldAliases(key, metadata)
            }
            else -> channel.send(SearchProgressEvent.NotFound(name))
        }
    }

    private fun parseFields(query: String): Map<String, String> {
        val trimmed = query.trim()
        if (trimmed.contains("|") || trimmed.contains("=")) {
            val result = mutableMapOf<String, String>()
            trimmed.split("|").forEach { part ->
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
        val parsed = SubjectIntakeParser.parseToFields(trimmed)
        if (parsed.isNotEmpty()) {
            return parsed
        }
        return mapOf("name" to trimmed)
    }

    private fun resolvePrimaryQuery(type: String, fields: Map<String, String>, rawQuery: String): String {
        val effectiveType = if (type == "scan") "person" else type
        val trimmed = rawQuery.trim()
        return when (effectiveType) {
            "image" -> trimmed
            "phone" -> fields["phone"] ?: trimmed
            "email", "breach" -> fields["email"] ?: trimmed
            "username" -> fields["username"] ?: trimmed.removePrefix("@")
            "domain" -> fields["domain"]
                ?: trimmed.removePrefix("http://").removePrefix("https://").substringBefore("/")
            "ip" -> fields["ip"] ?: trimmed
            "company" -> fields["company"] ?: fields["name"] ?: fields["domain"] ?: trimmed
            "vehicle", "vin" -> fields["vin"] ?: fields["plate"] ?: trimmed
            else -> fields["name"] ?: fields["email"] ?: fields["phone"]
                ?: fields["username"] ?: fields["domain"] ?: fields["ip"] ?: trimmed
        }
    }

    private suspend fun buildPersonIdFromMetadata(metadata: Map<String, String>, primaryQuery: String): String? {
        val name = metadata["pipl_name"]?.takeIf { it.isNotBlank() }
            ?: metadata["pdl_name"]?.takeIf { it.isNotBlank() }
            ?: metadata["person_name"]?.takeIf { it.isNotBlank() }
            ?: primaryQuery.takeIf { it.isNotBlank() && !it.startsWith("content://") }
            ?: return null
        val parts = name.trim().split("\\s+".toRegex())
        val first = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val last = if (parts.size > 1) parts.last() else ""
        val id = UUID.randomUUID().toString()
        val (employmentJson, addressesJson, socialJson) = buildStructuredPersonFields(metadata)
        val aliases = listOfNotNull(
            metadata["pipl_aliases"]?.split(",")?.map { it.trim() },
            metadata["search_aliases"]?.split(",")?.map { it.trim() }
        ).flatten().filter { it.isNotBlank() }.distinct()
        val person = PersonEntity(
            id = id,
            firstName = first,
            lastName = last,
            fullName = name,
            emailAddress = metadata["pipl_emails"]?.split(",")?.firstOrNull()?.trim()
                ?: metadata["pdl_emails"]?.split(",")?.firstOrNull()?.trim()
                ?: metadata["person_email"],
            phoneNumber = metadata["pipl_phones"]?.split(",")?.firstOrNull()?.trim()
                ?: metadata["pdl_phones"]?.split(",")?.firstOrNull()?.trim()
                ?: metadata["person_phone"],
            dateOfBirth = metadata["comp_dob"] ?: metadata["pipl_dob"] ?: metadata["person_dob"],
            addressesJson = addressesJson,
            employmentHistoryJson = employmentJson.ifBlank { "[]" },
            socialProfilesJson = socialJson,
            aliasesJson = if (aliases.isEmpty()) "[]" else org.json.JSONArray(aliases).toString(),
            nationalitiesJson = metadata["pipl_nationalities"]?.let { nat ->
                val list = nat.split(",").map { it.trim() }.filter { it.isNotBlank() }
                if (list.isEmpty()) "[]" else org.json.JSONArray(list).toString()
            } ?: "[]",
            gender = metadata["pipl_gender"] ?: metadata["demographics_gender"],
            profileImageUrl = metadata["profile_photo_url"]
        )
        db.personDao().insertPerson(person)
        return id
    }

    /** Zippopotam.us — free geo/ZIP lookup, no key (public-apis Geography). */
    private fun scrapeZippopotam(city: String, state: String): ScrapeOut {
        return try {
            val c = city.trim().lowercase().replace(" ", "-")
            val s = SubjectFilter.toStateAbbrev(state).lowercase()
            if (c.isBlank() || s.isBlank()) return ScrapeOut(false, false)
            val req = Request.Builder()
                .url("http://api.zippopotam.us/us/$s/$c")
                .header("User-Agent", SEC_USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            val places = json.optJSONArray("places") ?: return ScrapeOut(false, false)
            if (places.length() == 0) return ScrapeOut(false, false)
            val zips = (0 until minOf(12, places.length())).mapNotNull { i ->
                places.optJSONObject(i)?.optString("post code")?.takeIf { it.isNotBlank() }
            }
            val first = places.optJSONObject(0)
            val lat = first?.optString("latitude") ?: ""
            val lon = first?.optString("longitude") ?: ""
            val placeName = json.optString("place name", city)
            val stateName = json.optString("state", state)
            ScrapeOut(true, false, mapOf(
                "title" to "Zippopotam: $placeName, $stateName",
                "snippet" to "${zips.size} ZIP code(s) · ${zips.take(5).joinToString(", ")}",
                "zips" to zips.joinToString(", "),
                "latitude" to lat,
                "longitude" to lon,
                "place" to placeName,
                "state" to stateName
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    /** FBI Wanted API — free, no key (public-apis Government). */
    private fun scrapeFbiWanted(name: String, state: String = ""): ScrapeOut {
        return try {
            val parts = name.trim().split("\\s+".toRegex()).filter { it.length > 1 }
            if (parts.isEmpty()) return ScrapeOut(false, false)
            val searchTerm = parts.first()
            val req = Request.Builder()
                .url("https://api.fbi.gov/wanted/v1/list?page=1&pageSize=20&title=${encode(searchTerm)}")
                .header("User-Agent", SEC_USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val items = JSONObject(body).optJSONArray("items") ?: return ScrapeOut(false, false)
            val matches = mutableListOf<String>()
            val urls = mutableListOf<String>()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val title = item.optString("title", "")
                val description = item.optString("description", "")
                val detail = item.optString("details", "")
                val text = "$title $description $detail".lowercase()
                val nameMatch = parts.count { text.contains(it.lowercase()) } >= minOf(2, parts.size)
                if (!nameMatch) continue
                if (state.isNotBlank() && !SubjectFilter.matchesLocation(text, "", state)) {
                    val states = item.optJSONArray("possible_states")
                    val stateHit = (0 until (states?.length() ?: 0)).any { j ->
                        states?.optString(j)?.contains(state.take(2).uppercase(), ignoreCase = true) == true
                    }
                    if (!stateHit && !SubjectFilter.matchesLocation(text, "", state)) continue
                }
                val url = item.optString("url", "")
                val subjects = item.optJSONArray("subjects")?.let { arr ->
                    (0 until arr.length()).joinToString(", ") { arr.optString(it) }
                } ?: ""
                matches.add("$title${if (subjects.isNotBlank()) " [$subjects]" else ""}")
                if (url.isNotBlank()) urls.add(url)
            }
            if (matches.isEmpty()) return ScrapeOut(false, false)
            apiKeys.recordUsage("fbi_wanted")
            ScrapeOut(true, false, mapOf(
                "title" to "FBI Wanted: ${matches.size} possible match(es)",
                "snippet" to matches.take(5).joinToString("\n").take(500),
                "matches" to matches.joinToString("\n"),
                "urls" to urls.joinToString("\n"),
                "match_count" to matches.size.toString()
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    /** CMS NPI Registry — free healthcare provider lookup, no key. */
    private fun scrapeNpiRegistry(name: String, state: String, city: String = ""): ScrapeOut {
        return try {
            val parts = name.trim().split("\\s+".toRegex()).filter { it.length > 1 }
            if (parts.size < 2) return ScrapeOut(false, false)
            val first = encode(parts.first())
            val last = encode(parts.last())
            val stateParam = state.trim().take(2).uppercase()
            val url = buildString {
                append("https://npiregistry.cms.hhs.gov/api/?version=2.1")
                append("&first_name=$first&last_name=$last&limit=10")
                if (stateParam.length == 2) append("&state=$stateParam")
            }
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", SEC_USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val results = JSONObject(body).optJSONArray("results") ?: return ScrapeOut(false, false)
            val providers = mutableListOf<String>()
            for (i in 0 until results.length()) {
                val r = results.optJSONObject(i) ?: continue
                val basic = r.optJSONObject("basic") ?: continue
                val fullName = listOfNotNull(
                    basic.optString("name_prefix").takeIf { it.isNotBlank() },
                    basic.optString("first_name"),
                    basic.optString("middle_name").takeIf { it.isNotBlank() },
                    basic.optString("last_name"),
                    basic.optString("name_suffix").takeIf { it.isNotBlank() }
                ).joinToString(" ")
                val addresses = r.optJSONArray("addresses") ?: org.json.JSONArray()
                val addrText = (0 until addresses.length()).mapNotNull { j ->
                    addresses.optJSONObject(j)?.let { a ->
                        listOfNotNull(
                            a.optString("address_1").takeIf { it.isNotBlank() },
                            a.optString("city").takeIf { it.isNotBlank() },
                            a.optString("state").takeIf { it.isNotBlank() },
                            a.optString("postal_code").takeIf { it.isNotBlank() }
                        ).joinToString(", ")
                    }
                }.joinToString(" | ")
                if (city.isNotBlank() && !SubjectFilter.matchesLocation(addrText, city, state)) continue
                val taxonomy = r.optJSONArray("taxonomies")?.optJSONObject(0)?.optString("desc") ?: ""
                val npi = r.optString("number", "")
                providers.add("$fullName · NPI $npi${if (taxonomy.isNotBlank()) " · $taxonomy" else ""} · $addrText")
            }
            if (providers.isEmpty()) return ScrapeOut(false, false)
            apiKeys.recordUsage("npi")
            ScrapeOut(true, false, mapOf(
                "title" to "NPI Registry: ${providers.size} provider(s)",
                "snippet" to providers.take(5).joinToString("\n").take(500),
                "providers" to providers.joinToString("\n"),
                "provider_count" to providers.size.toString()
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    /** OpenFEC candidate search — free DEMO_KEY tier (public-apis Government). */
    private fun scrapeOpenFec(name: String, state: String, apiKey: String = "DEMO_KEY"): ScrapeOut {
        return try {
            val encoded = encode(name.trim())
            val stateParam = state.trim().take(2).uppercase()
            val url = buildString {
                append("https://api.open.fec.gov/v1/candidates/search/?q=$encoded&api_key=$apiKey")
                if (stateParam.length == 2) append("&state=$stateParam")
            }
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", SEC_USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val results = JSONObject(body).optJSONArray("results") ?: return ScrapeOut(false, false)
            if (results.length() == 0) return ScrapeOut(false, false)
            val candidates = (0 until minOf(5, results.length())).mapNotNull { i ->
                results.optJSONObject(i)?.let { c ->
                    val cName = c.optString("name", "")
                    val office = c.optString("office_full", c.optString("office", ""))
                    val party = c.optString("party_full", c.optString("party", ""))
                    val st = c.optString("state", "")
                    val district = c.optString("district", "")
                    listOfNotNull(cName.takeIf { it.isNotBlank() }, office.takeIf { it.isNotBlank() },
                        party.takeIf { it.isNotBlank() }, st.takeIf { it.isNotBlank() },
                        district.takeIf { it.isNotBlank() }?.let { "District $it" }
                    ).joinToString(" · ")
                }
            }
            if (candidates.isEmpty()) return ScrapeOut(false, false)
            apiKeys.recordUsage("openfec")
            ScrapeOut(true, false, mapOf(
                "title" to "OpenFEC: ${candidates.size} candidate(s)",
                "snippet" to candidates.joinToString("\n").take(500),
                "candidates" to candidates.joinToString("\n"),
                "candidate_count" to candidates.size.toString()
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    /** ipinfo.io — free tier, no key (50k/mo; public-apis Geolocation). */
    private fun scrapeIpInfo(ip: String): ScrapeOut {
        return try {
            if (!isIpAddress(ip)) return ScrapeOut(false, false)
            val req = Request.Builder()
                .url("https://ipinfo.io/${encode(ip.trim())}/json")
                .header("User-Agent", SEC_USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            val fields = mutableMapOf<String, String>()
            json.optString("ip").takeIf { it.isNotBlank() }?.let { fields["ip"] = it }
            json.optString("hostname").takeIf { it.isNotBlank() }?.let { fields["hostname"] = it }
            json.optString("city").takeIf { it.isNotBlank() }?.let { fields["city"] = it }
            json.optString("region").takeIf { it.isNotBlank() }?.let { fields["region"] = it }
            json.optString("country").takeIf { it.isNotBlank() }?.let { fields["country"] = it }
            json.optString("org").takeIf { it.isNotBlank() }?.let { fields["org"] = it }
            json.optString("postal").takeIf { it.isNotBlank() }?.let { fields["postal"] = it }
            json.optString("loc").takeIf { it.isNotBlank() }?.let { fields["coords"] = it }
            json.optString("timezone").takeIf { it.isNotBlank() }?.let { fields["timezone"] = it }
            if (fields.isEmpty()) return ScrapeOut(false, false)
            apiKeys.recordUsage("ipinfo")
            ScrapeOut(true, false, fields + mapOf(
                "title" to "ipinfo.io",
                "snippet" to listOfNotNull(fields["city"], fields["region"], fields["country"], fields["org"])
                    .joinToString(", ")
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeNhtsaVin(vin: String): ScrapeOut {
        return try {
            if (vin.length != 17) return ScrapeOut(false, false)
            val req = Request.Builder()
                .url("https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVin/${encode(vin)}?format=json")
                .header("User-Agent", SEC_USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val results = JSONObject(body).optJSONArray("Results") ?: return ScrapeOut(false, false)
            val vars = mutableMapOf<String, String>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val variable = item.optString("Variable")
                val value = item.optString("Value")
                if (variable.isNotBlank() && value.isNotBlank() && value != "Not Applicable") {
                    vars[variable] = value
                }
            }
            val make = vars["Make"] ?: vars["Manufacturer Name"]
            val model = vars["Model"]
            if (make.isNullOrBlank() && model.isNullOrBlank()) return ScrapeOut(false, false)
            val snippet = listOfNotNull(make, model, vars["Model Year"]).joinToString(" ")
            ScrapeOut(true, false, mapOf(
                "title" to "NHTSA: $vin",
                "snippet" to snippet,
                "make" to (make ?: ""),
                "model" to (model ?: ""),
                "year" to (vars["Model Year"] ?: ""),
                "body_class" to (vars["Body Class"] ?: ""),
                "fuel_type" to (vars["Fuel Type - Primary"] ?: ""),
                "manufacturer" to (vars["Manufacturer Name"] ?: make ?: "")
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeWigle(query: String, searchType: String, apiKey: String): ScrapeOut {
        return try {
            val parts = apiKey.split(":", limit = 2)
            val auth = if (parts.size == 2) {
                android.util.Base64.encodeToString("${parts[0]}:${parts[1]}".toByteArray(), android.util.Base64.NO_WRAP)
            } else {
                android.util.Base64.encodeToString(":$apiKey".toByteArray(), android.util.Base64.NO_WRAP)
            }
            val param = when (searchType) {
                "mac" -> "netid=${encode(query.trim().lowercase())}"
                "ssid" -> "ssid=${encode(query.trim())}"
                else -> {
                    val mac = query.trim().lowercase()
                    if (mac.matches(Regex("[0-9a-f:]{17}"))) "netid=${encode(mac)}" else "ssid=${encode(query.trim())}"
                }
            }
            val req = Request.Builder()
                .url("https://api.wigle.net/api/v2/network/search?$param&resultsPerPage=5")
                .header("Authorization", "Basic $auth")
                .header("Accept", "application/json")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val code = resp.code
            resp.close()
            if (code == 401 || code == 403) return ScrapeOut(false, true)
            if (body.isBlank() || !body.startsWith("{")) return ScrapeOut(false, false)
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return ScrapeOut(false, false)
            if (results.length() == 0) return ScrapeOut(false, false)
            val first = results.optJSONObject(0) ?: return ScrapeOut(false, false)
            val ssid = first.optString("ssid")
            val netid = first.optString("netid")
            val lat = first.optDouble("trilat", 0.0)
            val lon = first.optDouble("trilong", 0.0)
            val location = if (lat != 0.0 || lon != 0.0) "$lat, $lon" else ""
            ScrapeOut(true, false, mapOf(
                "title" to "WiGLE: ${ssid.ifBlank { netid }}",
                "snippet" to listOfNotNull(ssid.takeIf { it.isNotBlank() }, netid.takeIf { it.isNotBlank() }, location.takeIf { it.isNotBlank() }).joinToString(" · "),
                "ssid" to ssid,
                "bssid" to netid,
                "location" to location,
                "channel" to first.optString("channel"),
                "encryption" to first.optString("encryption")
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }

    private fun scrapeImageExif(imageUri: String): ScrapeOut {
        return try {
            val uri = Uri.parse(imageUri)
            val fields = mutableMapOf<String, String>()
            appCtx.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttribute(ExifInterface.TAG_DATETIME)?.let { fields["datetime"] = it }
                exif.getAttribute(ExifInterface.TAG_MAKE)?.let { fields["make"] = it }
                exif.getAttribute(ExifInterface.TAG_MODEL)?.let { fields["model"] = it }
                exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)?.let { fields["width"] = it }
                exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)?.let { fields["height"] = it }
                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                    fields["gps"] = "${latLong[0]}, ${latLong[1]}"
                }
            } ?: return ScrapeOut(false, false)
            if (fields.isEmpty()) return ScrapeOut(false, false)
            ScrapeOut(true, false, fields + mapOf(
                "title" to "Image EXIF",
                "snippet" to fields.entries.joinToString(" | ") { "${it.key}=${it.value}" }.take(600)
            ))
        } catch (_: Exception) { ScrapeOut(false, false) }
    }
}
