package com.twoskoops707.sixdegrees.data.repository

import android.content.Context
import com.twoskoops707.sixdegrees.data.ApiKeyManager
import com.twoskoops707.sixdegrees.data.local.OsintDatabase
import com.twoskoops707.sixdegrees.data.local.entity.OsintReportEntity
import com.twoskoops707.sixdegrees.data.local.entity.PersonEntity
import com.twoskoops707.sixdegrees.data.remote.RetrofitClient
import com.twoskoops707.sixdegrees.data.remote.dto.pipl.PiplPerson
import com.twoskoops707.sixdegrees.domain.model.Address
import com.twoskoops707.sixdegrees.domain.model.DataSource
import com.twoskoops707.sixdegrees.domain.model.Employment
import com.twoskoops707.sixdegrees.domain.model.SocialProfile
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Collections
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private class DateAdapter {
    @ToJson fun toJson(date: Date): Long = date.time
    @FromJson fun fromJson(value: Long): Date = Date(value)
}

class OsintRepository(context: Context) {

    private val appCtx = context.applicationContext
    private val apiKeyManager = ApiKeyManager(context)
    private val db = OsintDatabase.getDatabase(context)
    private val moshi = Moshi.Builder().add(DateAdapter()).add(KotlinJsonAdapterFactory()).build()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val fastHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val usernameSites = linkedMapOf(
        "GitHub" to "https://github.com/{u}",
        "Reddit" to "https://www.reddit.com/user/{u}",
        "Twitter/X" to "https://twitter.com/{u}",
        "Instagram" to "https://www.instagram.com/{u}/",
        "TikTok" to "https://www.tiktok.com/@{u}",
        "YouTube" to "https://www.youtube.com/@{u}",
        "Twitch" to "https://www.twitch.tv/{u}",
        "Pinterest" to "https://www.pinterest.com/{u}/",
        "LinkedIn" to "https://www.linkedin.com/in/{u}",
        "Steam" to "https://steamcommunity.com/id/{u}",
        "Flickr" to "https://www.flickr.com/people/{u}",
        "Tumblr" to "https://{u}.tumblr.com",
        "Medium" to "https://medium.com/@{u}",
        "DeviantArt" to "https://www.deviantart.com/{u}",
        "SoundCloud" to "https://soundcloud.com/{u}",
        "Spotify" to "https://open.spotify.com/user/{u}",
        "GitLab" to "https://gitlab.com/{u}",
        "Keybase" to "https://keybase.io/{u}",
        "Replit" to "https://replit.com/@{u}",
        "HackerNews" to "https://news.ycombinator.com/user?id={u}",
        "ProductHunt" to "https://www.producthunt.com/@{u}",
        "Gravatar" to "https://en.gravatar.com/{u}",
        "About.me" to "https://about.me/{u}",
        "Wattpad" to "https://www.wattpad.com/user/{u}",
        "Patreon" to "https://www.patreon.com/{u}",
        "Venmo" to "https://venmo.com/{u}",
        "Etsy" to "https://www.etsy.com/shop/{u}",
        "Behance" to "https://www.behance.net/{u}",
        "Dribbble" to "https://dribbble.com/{u}",
        "Last.fm" to "https://www.last.fm/user/{u}",
        "Lichess" to "https://lichess.org/@/{u}",
        "Chess.com" to "https://www.chess.com/member/{u}",
        "Codecademy" to "https://www.codecademy.com/profiles/{u}",
        "Duolingo" to "https://www.duolingo.com/profile/{u}",
        "NameMC" to "https://namemc.com/profile/{u}",
        "VSCO" to "https://vsco.co/{u}",
        "Snapchat" to "https://www.snapchat.com/add/{u}",
        "Xbox Gamertag" to "https://www.xboxgamertag.com/search/{u}",
        "PSN Profiles" to "https://psnprofiles.com/{u}",
        "Cashapp" to "https://cash.app/\${u}",
        "VK" to "https://vk.com/{u}",
        "Telegram" to "https://t.me/{u}",
        "Mastodon" to "https://mastodon.social/@{u}",
        "Bluesky" to "https://bsky.app/profile/{u}.bsky.social",
        "Threads" to "https://www.threads.net/@{u}",
        "Substack" to "https://{u}.substack.com",
        "Ko-fi" to "https://ko-fi.com/{u}",
        "Linktree" to "https://linktr.ee/{u}",
        "Letterboxd" to "https://letterboxd.com/{u}/",
        "ArtStation" to "https://www.artstation.com/{u}",
        "Unsplash" to "https://unsplash.com/@{u}",
        "Mixcloud" to "https://www.mixcloud.com/{u}/",
        "Audiomack" to "https://audiomack.com/{u}",
        "Bandcamp" to "https://bandcamp.com/{u}",
        "ReverbNation" to "https://www.reverbnation.com/{u}",
        "Steemit" to "https://steemit.com/@{u}",
        "Odysee" to "https://odysee.com/@{u}",
        "Rumble" to "https://rumble.com/user/{u}",
        "Minds" to "https://www.minds.com/{u}",
        "Kaggle" to "https://www.kaggle.com/{u}",
        "Codeforces" to "https://codeforces.com/profile/{u}",
        "LeetCode" to "https://leetcode.com/{u}/",
        "CodePen" to "https://codepen.io/{u}",
        "OnlyFans" to "https://onlyfans.com/{u}",
        "Angel.co" to "https://angel.co/u/{u}",
        "GoodReads" to "https://www.goodreads.com/{u}",
        "OkCupid" to "https://www.okcupid.com/profile/{u}",
        "Xing" to "https://www.xing.com/profile/{u}",
        "Exercism" to "https://exercism.org/profiles/{u}"
    )

    suspend fun search(query: String, type: String): Result<String> {
        var reportId: String? = null
        searchWithProgress(query, type).collect { event ->
            if (event is SearchProgressEvent.Complete) reportId = event.reportId
        }
        return if (reportId != null) Result.success(reportId!!)
        else Result.failure(Exception("Search failed"))
    }

    fun searchWithProgress(query: String, type: String): Flow<SearchProgressEvent> = channelFlow {
        val prefs = appCtx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("pref_connections_enabled", true)) {
            send(SearchProgressEvent.Complete("", 0))
            return@channelFlow
        }

        val metadata = ConcurrentHashMap<String, String>()
        val sources = Collections.synchronizedList(mutableListOf<DataSource>())
        val emit: suspend (SearchProgressEvent) -> Unit = { send(it) }

        when (type) {
            "email" -> emailSearch(query, metadata, sources, emit)
            "phone" -> phoneSearch(query, metadata, sources, emit)
            "username" -> usernameSearch(query, metadata, sources, emit)
            "ip", "domain" -> ipDomainSearch(query, metadata, sources, emit)
            "company" -> companySearch(query, metadata, sources, emit)
            "image" -> imageSearch(query, metadata, sources, emit)
            "comprehensive" -> comprehensiveSearch(query, metadata, sources, emit)
            else -> personSearch(query, metadata, sources, emit)
        }

        metadata["search_type"] = type
        val reportId = saveReport(query, null, sources, metadata.toMap())
        send(SearchProgressEvent.Complete(reportId, sources.size))
    }

    private suspend fun emailSearch(
        email: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        emit(SearchProgressEvent.Checking("EmailRep.io"))
        try {
            val resp = RetrofitClient.emailRepService.getReputation(email)
            if (resp.isSuccessful && resp.body() != null) {
                apiKeyManager.recordUsage("emailrep")
                val b = resp.body()!!
                meta["emailrep_reputation"] = b.reputation ?: "unknown"
                meta["emailrep_suspicious"] = b.suspicious?.toString() ?: "false"
                meta["emailrep_references"] = b.references?.toString() ?: "0"
                b.details?.profiles?.let { meta["emailrep_profiles"] = it.joinToString(", ") }
                meta["emailrep_breach"] = b.details?.dataBreach?.toString() ?: "false"
                sources.add(DataSource("EmailRep.io", null, Date(), 0.8))
                emit(SearchProgressEvent.Found("EmailRep.io",
                    "Reputation: ${b.reputation ?: "unknown"} · ${b.references ?: 0} references · data breach: ${b.details?.dataBreach ?: false}"))
            } else {
                emit(SearchProgressEvent.NotFound("EmailRep.io"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("EmailRep.io", e.message ?: "Network error"))
        }

        val hibpKey = apiKeyManager.hibpKey
        if (hibpKey.isNotBlank()) {
            emit(SearchProgressEvent.Checking("HaveIBeenPwned"))
            try {
                val resp = RetrofitClient.hibpService.getBreachedAccount(email, apiKey = hibpKey)
                when {
                    resp.isSuccessful && resp.body() != null -> {
                        apiKeyManager.recordUsage("hibp")
                        val breaches = resp.body()!!
                        meta["hibp_breach_count"] = breaches.size.toString()
                        meta["hibp_breaches"] = breaches.mapNotNull { it.name }.joinToString(", ")
                        val breachDetails = breaches.joinToString("\n") { b ->
                            val pwnFmt = b.pwnCount?.let { c ->
                                when {
                                    c >= 1_000_000_000 -> "${c / 1_000_000_000}B records"
                                    c >= 1_000_000 -> "${c / 1_000_000}M records"
                                    c >= 1_000 -> "${c / 1_000}K records"
                                    else -> "$c records"
                                }
                            } ?: ""
                            val dataTypes = b.dataClasses?.joinToString(", ") ?: ""
                            val verified = if (b.isVerified == true) "" else " [UNVERIFIED]"
                            val sensitive = if (b.isSensitive == true) " [SENSITIVE]" else ""
                            buildString {
                                append("${b.title ?: b.name}${verified}${sensitive}")
                                if (b.domain?.isNotBlank() == true) append(" (${b.domain})")
                                if (b.breachDate?.isNotBlank() == true) append(" — ${b.breachDate}")
                                if (pwnFmt.isNotBlank()) append(" — $pwnFmt")
                                if (dataTypes.isNotBlank()) append(" — EXPOSED: $dataTypes")
                            }
                        }
                        meta["hibp_breach_details"] = breachDetails
                        sources.add(DataSource("HaveIBeenPwned", null, Date(), 0.95))
                        val names = breaches.take(3).mapNotNull { it.name }.joinToString(", ")
                        emit(SearchProgressEvent.Found("HaveIBeenPwned",
                            "${breaches.size} breach${if (breaches.size != 1) "es" else ""}: $names"))
                    }
                    resp.code() == 404 -> {
                        meta["hibp_breach_count"] = "0"
                        emit(SearchProgressEvent.NotFound("HaveIBeenPwned"))
                    }
                    else -> emit(SearchProgressEvent.Failed("HaveIBeenPwned", "HTTP ${resp.code()}"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("HaveIBeenPwned", e.message ?: "Network error"))
            }
        }

        emit(SearchProgressEvent.Checking("Gravatar"))
        try {
            val hash = md5(email.lowercase().trim())
            val req = Request.Builder()
                .url("https://www.gravatar.com/avatar/$hash?d=404")
                .head()
                .addHeader("User-Agent", "Mozilla/5.0")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val code = resp.code
            resp.close()
            meta["gravatar_url"] = "https://www.gravatar.com/avatar/$hash"
            if (code != 404) {
                sources.add(DataSource("Gravatar", "https://www.gravatar.com/$hash", Date(), 0.6))
                try {
                    val jsonReq = Request.Builder()
                        .url("https://en.gravatar.com/$hash.json")
                        .addHeader("User-Agent", "SixDegrees-OSINT")
                        .build()
                    val jsonResp = fastHttpClient.newCall(jsonReq).execute()
                    val jsonBody = jsonResp.body?.string() ?: ""
                    jsonResp.close()
                    val displayName = Regex("\"displayName\":\\s*\"([^\"]+)\"").find(jsonBody)?.groupValues?.get(1) ?: ""
                    val aboutMe = Regex("\"aboutMe\":\\s*\"([^\"]+)\"").find(jsonBody)?.groupValues?.get(1) ?: ""
                    val location = Regex("\"currentLocation\":\\s*\"([^\"]+)\"").find(jsonBody)?.groupValues?.get(1) ?: ""
                    if (displayName.isNotBlank()) meta["gravatar_name"] = displayName
                    if (aboutMe.isNotBlank()) meta["gravatar_bio"] = aboutMe.take(300)
                    if (location.isNotBlank()) meta["gravatar_location"] = location
                    val accountMatches = Regex("\"domain\":\\s*\"([^\"]+)\",\\s*\"username\":\\s*\"([^\"]+)\"").findAll(jsonBody)
                    val accounts = accountMatches.take(8).map { "${it.groupValues[1]}/${it.groupValues[2]}" }.toList()
                    if (accounts.isNotEmpty()) meta["gravatar_accounts"] = accounts.joinToString(", ")
                    val detail = buildString {
                        if (displayName.isNotBlank()) append(displayName)
                        if (location.isNotBlank()) { if (isNotEmpty()) append(" · "); append(location) }
                        if (accounts.isNotEmpty()) { if (isNotEmpty()) append(" · "); append("${accounts.size} linked accounts") }
                    }.ifBlank { "Profile image exists" }
                    emit(SearchProgressEvent.Found("Gravatar", detail))
                } catch (_: Exception) {
                    emit(SearchProgressEvent.Found("Gravatar", "Profile image exists"))
                }
            } else {
                emit(SearchProgressEvent.NotFound("Gravatar"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("Gravatar", e.message ?: ""))
        }

        emit(SearchProgressEvent.Checking("LeakCheck.io"))
        try {
            val encodedEmail = URLEncoder.encode(email, "UTF-8")
            val req = Request.Builder()
                .url("https://leakcheck.io/api/public?check=$encodedEmail")
                .addHeader("User-Agent", "SixDegrees-OSINT")
                .build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            val success = Regex("\"success\":\\s*true").containsMatchIn(body)
            val found = Regex("\"found\":\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            if (success && found > 0) {
                meta["leakcheck_found"] = found.toString()
                val sourcesList = Regex("\"sources\":\\s*\\[([^\\]]+)\\]").find(body)
                    ?.groupValues?.get(1)?.split(",")
                    ?.map { it.trim().trim('"') }?.filter { it.isNotBlank() }
                    ?: emptyList()
                meta["leakcheck_sources"] = sourcesList.joinToString(", ")
                sources.add(DataSource("LeakCheck.io", null, Date(), 0.85))
                emit(SearchProgressEvent.Found("LeakCheck.io",
                    "$found breach hit${if (found != 1) "s" else ""}: ${sourcesList.take(3).joinToString(", ")}"))
            } else {
                emit(SearchProgressEvent.NotFound("LeakCheck.io"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("LeakCheck.io", e.message ?: ""))
        }

        emit(SearchProgressEvent.Checking("ThreatCrowd"))
        try {
            val resp = RetrofitClient.threatCrowdService.emailReport(email)
            if (resp.isSuccessful && resp.body()?.responseCode == "1") {
                val b = resp.body()!!
                val domains = b.domains ?: emptyList()
                val refs = b.references ?: 0
                meta["threatcrowd_email_domains"] = domains.take(10).joinToString(", ")
                meta["threatcrowd_email_refs"] = refs.toString()
                if (domains.isNotEmpty() || refs > 0) {
                    sources.add(DataSource("ThreatCrowd", null, Date(), 0.7))
                    emit(SearchProgressEvent.Found("ThreatCrowd",
                        buildString {
                            if (domains.isNotEmpty()) append("${domains.size} domain${if (domains.size != 1) "s" else ""} linked")
                            if (refs > 0) { if (isNotEmpty()) append(" · "); append("$refs ref${if (refs != 1) "s" else ""}") }
                        }
                    ))
                } else {
                    emit(SearchProgressEvent.NotFound("ThreatCrowd"))
                }
            } else {
                emit(SearchProgressEvent.NotFound("ThreatCrowd"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("ThreatCrowd", e.message ?: ""))
        }

        emit(SearchProgressEvent.Checking("HackerTarget Email Search"))
        try {
            val resp = RetrofitClient.hackerTargetService.findEmail(email)
            if (resp.isSuccessful) {
                val body = resp.body() ?: ""
                if (!body.startsWith("error") && body.isNotBlank()) {
                    apiKeyManager.recordUsage("hackertarget")
                    val lines = body.lines().filter { it.isNotBlank() }
                    meta["hackertarget_email_hosts"] = lines.take(10).joinToString(", ")
                    sources.add(DataSource("HackerTarget Email", null, Date(), 0.75))
                    emit(SearchProgressEvent.Found("HackerTarget Email Search",
                        "${lines.size} hostname${if (lines.size != 1) "s" else ""} associated with this email"))
                } else {
                    emit(SearchProgressEvent.NotFound("HackerTarget Email Search"))
                }
            } else {
                emit(SearchProgressEvent.Failed("HackerTarget Email Search", "HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("HackerTarget Email Search", e.message ?: ""))
        }

        emit(SearchProgressEvent.Checking("ProxyNova Breach"))
        try {
            val encoded = URLEncoder.encode(email, "UTF-8")
            val req = Request.Builder()
                .url("https://api.proxynova.com/comb?query=$encoded&limit=100")
                .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                .build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            val count = Regex("\"count\":(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val total = Regex("\"total\":(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val found = (total.takeIf { it > 0 } ?: count)
            if (found > 0) {
                meta["proxynova_breach_count"] = found.toString()
                val lines = Regex("\"line\":\"([^\"]+)\"").findAll(body)
                    .map { it.groupValues[1].replace("\\n", "").replace("\\r", "").trim() }
                    .filter { it.isNotBlank() }
                    .take(25)
                    .toList()
                if (lines.isNotEmpty()) meta["proxynova_samples"] = lines.joinToString("\n")
                sources.add(DataSource("ProxyNova COMB", null, Date(), 0.9))
                emit(SearchProgressEvent.Found("ProxyNova Breach",
                    "$found breach record${if (found != 1) "s" else ""} in COMB dataset"))
            } else {
                emit(SearchProgressEvent.NotFound("ProxyNova Breach"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("ProxyNova Breach", e.message ?: ""))
        }

        emit(SearchProgressEvent.Checking("EVA Email Validator"))
        try {
            val encoded = URLEncoder.encode(email, "UTF-8")
            val req = Request.Builder()
                .url("https://eva.pingutil.com/email?email=$encoded")
                .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            val deliverable = Regex("\"deliverable\":(true|false)").find(body)?.groupValues?.get(1)
            val disposable = Regex("\"disposable\":(true|false)").find(body)?.groupValues?.get(1)?.toBooleanStrictOrNull()
            val spamTrap = Regex("\"spam_trap\":(true|false)").find(body)?.groupValues?.get(1)?.toBooleanStrictOrNull()
            val mxRecord = Regex("\"mx_record\":(true|false)").find(body)?.groupValues?.get(1)
            if (deliverable != null) {
                meta["eva_deliverable"] = deliverable
                if (disposable != null) meta["eva_disposable"] = disposable.toString()
                if (spamTrap != null) meta["eva_spam_trap"] = spamTrap.toString()
                if (mxRecord != null) meta["eva_mx_record"] = mxRecord
                sources.add(DataSource("EVA Email Validator", null, Date(), 0.8))
                emit(SearchProgressEvent.Found("EVA Email Validator",
                    buildString {
                        append("Deliverable: $deliverable")
                        if (disposable == true) append(" · Disposable address")
                        if (spamTrap == true) append(" · SPAM TRAP")
                        if (mxRecord != null) append(" · MX: $mxRecord")
                    }
                ))
            } else {
                emit(SearchProgressEvent.NotFound("EVA Email Validator"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("EVA Email Validator", e.message ?: ""))
        }

        val ipqsEmailKey = apiKeyManager.ipqsKey
        if (ipqsEmailKey.isNotBlank()) {
            emit(SearchProgressEvent.Checking("IPQS Email"))
            try {
                val encoded = URLEncoder.encode(email, "UTF-8")
                val req = Request.Builder()
                    .url("https://ipqualityscore.com/api/json/email/$ipqsEmailKey/$encoded?strictness=1&abuse_strictness=1")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .build()
                val resp = httpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""; resp.close()
                val valid = Regex("\"valid\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)
                if (valid != null) {
                    apiKeyManager.recordUsage("ipqs")
                    val fraudScore = Regex("\"fraud_score\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val disposable = Regex("\"disposable\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false
                    val leaked = Regex("\"leaked\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false
                    val suspect = Regex("\"suspect\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false
                    val smtp = Regex("\"smtp_score\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                    val domain = Regex("\"domain\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                    meta["ipqs_email_fraud_score"] = fraudScore.toString()
                    if (disposable) meta["ipqs_email_disposable"] = "true"
                    if (leaked) meta["ipqs_email_leaked"] = "true"
                    if (suspect) meta["ipqs_email_suspect"] = "true"
                    if (smtp >= 0) meta["ipqs_email_smtp"] = smtp.toString()
                    if (domain.isNotBlank()) meta["ipqs_email_domain"] = domain
                    sources.add(DataSource("IPQS Email", null, Date(), 0.88))
                    emit(SearchProgressEvent.Found("IPQS Email",
                        buildString {
                            append("Fraud score: $fraudScore/100")
                            if (leaked) append(" · ⚠ LEAKED")
                            if (suspect) append(" · ⚠ Suspect")
                            if (disposable) append(" · Disposable")
                        }
                    ))
                } else {
                    emit(SearchProgressEvent.NotFound("IPQS Email"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("IPQS Email", e.message ?: ""))
            }
        }

        val fcKey = apiKeyManager.fullcontactKey
        if (fcKey.isNotBlank()) {
            emit(SearchProgressEvent.Checking("FullContact"))
            try {
                val reqBody = "{\"email\":\"$email\"}".toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("https://api.fullcontact.com/v3/person.enrich")
                    .post(reqBody)
                    .addHeader("Authorization", "Bearer $fcKey")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .build()
                val resp = httpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                val code = resp.code; resp.close()
                if (code in 200..299 && body.isNotBlank() && !body.contains("\"status\":404")) {
                    apiKeyManager.recordUsage("fullcontact")
                    val fullName = Regex("\"fullName\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                    val location = Regex("\"location\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                    val title = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").findAll(body).firstOrNull()?.groupValues?.get(1) ?: ""
                    val org = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").findAll(body).drop(1).firstOrNull()?.groupValues?.get(1) ?: ""
                    val twitter = Regex("\"twitter\\.com/([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                    val linkedin = Regex("\"linkedin\\.com/in/([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                    val ageRange = Regex("\"ageRange\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                    val gender = Regex("\"gender\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                    if (fullName.isNotBlank() || location.isNotBlank()) {
                        if (fullName.isNotBlank()) meta["fullcontact_name"] = fullName
                        if (location.isNotBlank()) meta["fullcontact_location"] = location
                        if (title.isNotBlank()) meta["fullcontact_title"] = title
                        if (org.isNotBlank()) meta["fullcontact_org"] = org
                        if (twitter.isNotBlank()) meta["fullcontact_twitter"] = twitter
                        if (linkedin.isNotBlank()) meta["fullcontact_linkedin"] = linkedin
                        if (ageRange.isNotBlank()) meta["fullcontact_age_range"] = ageRange
                        if (gender.isNotBlank()) meta["fullcontact_gender"] = gender
                        sources.add(DataSource("FullContact", null, Date(), 0.85))
                        emit(SearchProgressEvent.Found("FullContact",
                            buildString {
                                if (fullName.isNotBlank()) append(fullName)
                                if (location.isNotBlank()) { if (isNotEmpty()) append(" · "); append(location) }
                                if (title.isNotBlank()) { if (isNotEmpty()) append(" · "); append(title) }
                            }.ifBlank { "Identity enrichment found" }
                        ))
                    } else {
                        emit(SearchProgressEvent.NotFound("FullContact"))
                    }
                } else {
                    emit(SearchProgressEvent.NotFound("FullContact"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("FullContact", e.message ?: ""))
            }
        }
    }

    private suspend fun phoneSearch(
        phone: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        val numverifyKey = apiKeyManager.numverifyKey
        if (numverifyKey.isNotBlank()) {
            emit(SearchProgressEvent.Checking("Numverify"))
            try {
                val resp = RetrofitClient.numverifyService.validate(numverifyKey, phone)
                if (resp.isSuccessful && resp.body() != null) {
                    apiKeyManager.recordUsage("numverify")
                    val b = resp.body()!!
                    meta["numverify_valid"] = b.valid?.toString() ?: "false"
                    meta["numverify_country"] = b.countryName ?: ""
                    meta["numverify_carrier"] = b.carrier ?: ""
                    meta["numverify_line_type"] = b.lineType ?: ""
                    meta["numverify_location"] = b.location ?: ""
                    meta["numverify_intl"] = b.internationalFormat ?: phone
                    sources.add(DataSource("Numverify", null, Date(), 0.85))
                    emit(SearchProgressEvent.Found("Numverify",
                        "${b.countryName ?: "Unknown"} · ${b.carrier ?: "Unknown carrier"} · ${b.lineType ?: "unknown"}"))
                } else {
                    emit(SearchProgressEvent.Failed("Numverify", "HTTP ${resp.code()}"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("Numverify", e.message ?: ""))
            }
        }

        val veriphoneKey = apiKeyManager.veriphoneKey
        if (veriphoneKey.isNotBlank()) {
            emit(SearchProgressEvent.Checking("Veriphone"))
            try {
                val encoded = URLEncoder.encode(phone, "UTF-8")
                val req = Request.Builder()
                    .url("https://api.veriphone.io/v2/verify?phone=$encoded&key=$veriphoneKey")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .build()
                val resp = httpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""; resp.close()
                val valid = Regex("\"phone_valid\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)
                val country = Regex("\"country\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                val carrier = Regex("\"carrier\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                val lineType = Regex("\"phone_type\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                val intl = Regex("\"international_number\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: phone
                if (valid != null) {
                    apiKeyManager.recordUsage("veriphone")
                    meta["veriphone_valid"] = valid
                    meta["veriphone_country"] = country
                    meta["veriphone_carrier"] = carrier
                    meta["veriphone_line_type"] = lineType
                    meta["veriphone_international"] = intl
                    sources.add(DataSource("Veriphone", null, Date(), 0.85))
                    emit(SearchProgressEvent.Found("Veriphone",
                        buildString {
                            if (country.isNotBlank()) append(country)
                            if (carrier.isNotBlank()) { if (isNotEmpty()) append(" · "); append(carrier) }
                            if (lineType.isNotBlank()) { if (isNotEmpty()) append(" · "); append(lineType) }
                        }.ifBlank { "Valid: $valid" }
                    ))
                } else {
                    emit(SearchProgressEvent.NotFound("Veriphone"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("Veriphone", e.message ?: ""))
            }
        }

        val ipqsKey = apiKeyManager.ipqsKey
        if (ipqsKey.isNotBlank()) {
            emit(SearchProgressEvent.Checking("IPQS Phone"))
            try {
                val encoded = URLEncoder.encode(phone, "UTF-8")
                val req = Request.Builder()
                    .url("https://ipqualityscore.com/api/json/phone/$ipqsKey/$encoded?strictness=1")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .build()
                val resp = httpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""; resp.close()
                val valid = Regex("\"valid\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)
                if (valid != null) {
                    apiKeyManager.recordUsage("ipqs")
                    val fraudScore = Regex("\"fraud_score\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val lineType = Regex("\"line_type\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                    val carrier = Regex("\"carrier\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                    val country = Regex("\"country\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                    val isVoip = Regex("\"VOIP\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false
                    val isRisky = Regex("\"risky\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false
                    val isSpam = Regex("\"spammer\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false
                    meta["ipqs_phone_fraud_score"] = fraudScore.toString()
                    meta["ipqs_phone_line_type"] = lineType
                    meta["ipqs_phone_carrier"] = carrier
                    meta["ipqs_phone_country"] = country
                    if (isVoip) meta["ipqs_phone_voip"] = "true"
                    if (isRisky) meta["ipqs_phone_risky"] = "true"
                    if (isSpam) meta["ipqs_phone_spam"] = "true"
                    sources.add(DataSource("IPQS Phone", null, Date(), 0.88))
                    emit(SearchProgressEvent.Found("IPQS Phone",
                        buildString {
                            append("Fraud score: $fraudScore/100")
                            if (isRisky) append(" · ⚠ RISKY")
                            if (isSpam) append(" · ⚠ SPAMMER")
                            if (isVoip) append(" · VoIP")
                            if (carrier.isNotBlank()) { append(" · "); append(carrier) }
                        }
                    ))
                } else {
                    emit(SearchProgressEvent.NotFound("IPQS Phone"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("IPQS Phone", e.message ?: ""))
            }
        }

    }

    private suspend fun usernameSearch(
        username: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        val semaphore = Semaphore(10)
        coroutineScope {
            usernameSites.entries.forEach { (site, urlTemplate) ->
                val url = urlTemplate.replace("{u}", username)
                launch {
                    semaphore.withPermit {
                        emit(SearchProgressEvent.Checking(site))
                        try {
                            val req = Request.Builder()
                                .url(url)
                                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0")
                                .build()
                            val resp = fastHttpClient.newCall(req).execute()
                            val code = resp.code
                            resp.close()
                            if (code in 200..299) {
                                sources.add(DataSource(site, url, Date(), 0.7))
                                emit(SearchProgressEvent.Found(site, url))
                            } else {
                                emit(SearchProgressEvent.NotFound(site))
                            }
                        } catch (e: Exception) {
                            emit(SearchProgressEvent.NotFound(site))
                        }
                    }
                }
            }
        }

        emit(SearchProgressEvent.Checking("GitHub API"))
        try {
            val req = Request.Builder()
                .url("https://api.github.com/users/$username")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", "SixDegrees-OSINT")
                .build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                resp.close()
                meta["github_profile"] = body.take(800)
                sources.add(DataSource("GitHub API", "https://github.com/$username", Date(), 0.9))
                val name = Regex("\"name\":\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                val followers = Regex("\"followers\":\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
                val repos = Regex("\"public_repos\":\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
                emit(SearchProgressEvent.Found("GitHub API",
                    buildString {
                        if (name.isNotBlank()) append(name)
                        if (followers != null) append(" · $followers followers")
                        if (repos != null) append(" · $repos repos")
                    }.ifBlank { "Profile exists" }
                ))
            } else {
                resp.close()
                emit(SearchProgressEvent.NotFound("GitHub API"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("GitHub API", e.message ?: ""))
        }

        emit(SearchProgressEvent.Checking("Keybase"))
        try {
            val resp = RetrofitClient.keybaseService.lookup(username)
            if (resp.isSuccessful && resp.body()?.them?.isNotEmpty() == true) {
                val person = resp.body()!!.them!!.first()
                val basics = person.basics
                val profile = person.profile
                val proofs = person.proofsSummary?.all ?: emptyList()
                if (basics?.username != null) meta["keybase_username"] = basics.username
                if (!basics?.fullName.isNullOrBlank()) meta["keybase_name"] = basics?.fullName ?: ""
                if (!profile?.bio.isNullOrBlank()) meta["keybase_bio"] = (profile?.bio ?: "").take(300)
                if (!profile?.location.isNullOrBlank()) meta["keybase_location"] = profile?.location ?: ""
                if (proofs.isNotEmpty()) {
                    meta["keybase_proofs"] = proofs.take(10).mapNotNull {
                        if (!it.proofType.isNullOrBlank() && !it.nametag.isNullOrBlank()) "${it.proofType}: ${it.nametag}" else null
                    }.joinToString(", ")
                    meta["keybase_proofs_count"] = proofs.size.toString()
                }
                sources.add(DataSource("Keybase", "https://keybase.io/$username", Date(), 0.9))
                emit(SearchProgressEvent.Found("Keybase",
                    buildString {
                        val name = basics?.fullName ?: ""
                        if (name.isNotBlank()) append(name)
                        if (proofs.isNotEmpty()) { if (isNotEmpty()) append(" · "); append("${proofs.size} social proof${if (proofs.size != 1) "s" else ""}") }
                        val bio = profile?.bio ?: ""
                        if (bio.isNotBlank()) { if (isNotEmpty()) append(" · "); append(bio.take(60)) }
                    }.ifBlank { "Profile found" }
                ))
            } else {
                emit(SearchProgressEvent.NotFound("Keybase"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("Keybase", e.message ?: ""))
        }

        emit(SearchProgressEvent.Checking("HackerNews"))
        try {
            val req = Request.Builder()
                .url("https://hacker-news.firebaseio.com/v0/user/$username.json")
                .addHeader("User-Agent", "SixDegrees-OSINT")
                .build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body != "null" && body.isNotBlank() && body.startsWith("{")) {
                val karma = Regex("\"karma\":\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val about = Regex("\"about\":\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                meta["hackernews_karma"] = karma.toString()
                if (about.isNotBlank()) meta["hackernews_about"] = about.take(200)
                sources.add(DataSource("HackerNews", "https://news.ycombinator.com/user?id=$username", Date(), 0.8))
                emit(SearchProgressEvent.Found("HackerNews",
                    "Karma: $karma${if (about.isNotBlank()) " · ${about.take(60)}" else ""}"))
            } else {
                emit(SearchProgressEvent.NotFound("HackerNews"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("HackerNews", e.message ?: ""))
        }

        emit(SearchProgressEvent.Checking("Dev.to"))
        try {
            val req = Request.Builder()
                .url("https://dev.to/api/users/by_username?url=$username")
                .addHeader("User-Agent", "SixDegrees-OSINT")
                .build()
            val resp = httpClient.newCall(req).execute()
            val code = resp.code
            val body = resp.body?.string() ?: ""
            resp.close()
            if (code in 200..299 && body.startsWith("{")) {
                val name = Regex("\"name\":\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                val location = Regex("\"location\":\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                val summary = Regex("\"summary\":\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                val joinedAt = Regex("\"joined_at\":\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                if (name.isNotBlank()) meta["devto_name"] = name
                if (location.isNotBlank()) meta["devto_location"] = location
                if (summary.isNotBlank()) meta["devto_summary"] = summary.take(200)
                if (joinedAt.isNotBlank()) meta["devto_joined"] = joinedAt
                sources.add(DataSource("Dev.to", "https://dev.to/$username", Date(), 0.8))
                emit(SearchProgressEvent.Found("Dev.to",
                    buildString {
                        if (name.isNotBlank()) append(name)
                        if (location.isNotBlank()) { if (isNotEmpty()) append(" · "); append(location) }
                        if (joinedAt.isNotBlank()) { if (isNotEmpty()) append(" · "); append("Joined $joinedAt") }
                    }.ifBlank { "Profile found" }
                ))
            } else {
                emit(SearchProgressEvent.NotFound("Dev.to"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("Dev.to", e.message ?: ""))
        }

        val found = sources.filter { it.url != null }
        meta["sites_checked"] = (usernameSites.size + 4).toString()
        meta["sites_found"] = found.size.toString()
        meta["found_urls"] = found.joinToString("\n") { "${it.name}: ${it.url}" }
    }

    private suspend fun ipDomainSearch(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) = coroutineScope {
        val isIp = query.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))

        launch {
            emit(SearchProgressEvent.Checking("IP-API.com"))
            try {
                val resp = RetrofitClient.ipApiService.lookup(query)
                if (resp.isSuccessful && resp.body() != null) {
                    apiKeyManager.recordUsage("ipapi")
                    val b = resp.body()!!
                    meta["ip_country"] = b.country ?: ""
                    meta["ip_city"] = b.city ?: ""
                    meta["ip_isp"] = b.isp ?: ""
                    meta["ip_org"] = b.org ?: ""
                    meta["ip_asn"] = b.asn ?: ""
                    meta["ip_timezone"] = b.timezone ?: ""
                    sources.add(DataSource("IP-API.com", null, Date(), 0.9))
                    emit(SearchProgressEvent.Found("IP-API.com",
                        listOfNotNull(b.city, b.country, b.isp).filter { it.isNotBlank() }.joinToString(" · ")))
                } else {
                    emit(SearchProgressEvent.NotFound("IP-API.com"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("IP-API.com", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("HackerTarget DNS"))
            try {
                val resp = RetrofitClient.hackerTargetService.dnsLookup(query)
                if (resp.isSuccessful) {
                    val body = resp.body() ?: ""
                    if (!body.startsWith("error") && body.isNotBlank()) {
                        apiKeyManager.recordUsage("hackertarget")
                        meta["dns"] = body.take(500)
                        val lines = body.lines().filter { it.isNotBlank() }.size
                        sources.add(DataSource("HackerTarget DNS", null, Date(), 0.85))
                        emit(SearchProgressEvent.Found("HackerTarget DNS", "$lines DNS records"))
                    } else {
                        emit(SearchProgressEvent.NotFound("HackerTarget DNS"))
                    }
                } else {
                    emit(SearchProgressEvent.Failed("HackerTarget DNS", "HTTP ${resp.code()}"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("HackerTarget DNS", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("HackerTarget WHOIS"))
            try {
                val resp = RetrofitClient.hackerTargetService.whois(query)
                if (resp.isSuccessful) {
                    val body = resp.body() ?: ""
                    if (!body.startsWith("error") && body.isNotBlank()) {
                        meta["whois"] = body.take(800)
                        val registrar = body.lines().firstOrNull { it.lowercase().contains("registrar:") }
                            ?.substringAfter(":")?.trim()
                        sources.add(DataSource("HackerTarget WHOIS", null, Date(), 0.85))
                        emit(SearchProgressEvent.Found("HackerTarget WHOIS",
                            if (!registrar.isNullOrBlank()) "Registrar: $registrar" else "WHOIS data found"))
                    } else {
                        emit(SearchProgressEvent.NotFound("HackerTarget WHOIS"))
                    }
                } else {
                    emit(SearchProgressEvent.Failed("HackerTarget WHOIS", "HTTP ${resp.code()}"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("HackerTarget WHOIS", e.message ?: ""))
            }
        }

        if (!isIp) {
            launch {
                emit(SearchProgressEvent.Checking("crt.sh"))
                try {
                    val resp = RetrofitClient.crtShService.query(query)
                    if (resp.isSuccessful && resp.body() != null) {
                        val entries = resp.body()!!
                        val subs = entries.mapNotNull { it.nameValue }
                            .flatMap { it.split("\n") }
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .take(30)
                        meta["subdomains"] = subs.joinToString(", ")
                        meta["cert_count"] = entries.size.toString()
                        sources.add(DataSource("crt.sh", "https://crt.sh/?q=$query", Date(), 0.8))
                        emit(SearchProgressEvent.Found("crt.sh", "${entries.size} SSL certs · ${subs.size} subdomains"))
                    } else {
                        emit(SearchProgressEvent.NotFound("crt.sh"))
                    }
                } catch (e: Exception) {
                    emit(SearchProgressEvent.Failed("crt.sh", e.message ?: ""))
                }
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("Wayback Machine"))
            try {
                val resp = RetrofitClient.waybackService.query(query)
                if (resp.isSuccessful && resp.body() != null) {
                    val snapshots = resp.body()!!.drop(1)
                    meta["wayback_count"] = snapshots.size.toString()
                    if (snapshots.isNotEmpty()) {
                        meta["wayback_first"] = snapshots.firstOrNull()?.firstOrNull() ?: ""
                        meta["wayback_last"] = snapshots.lastOrNull()?.firstOrNull() ?: ""
                        sources.add(DataSource("Wayback Machine", "https://web.archive.org/web/*/$query", Date(), 0.7))
                        emit(SearchProgressEvent.Found("Wayback Machine", "${snapshots.size} snapshots archived"))
                    } else {
                        emit(SearchProgressEvent.NotFound("Wayback Machine"))
                    }
                } else {
                    emit(SearchProgressEvent.NotFound("Wayback Machine"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("Wayback Machine", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("AlienVault OTX"))
            try {
                val resp = if (isIp) RetrofitClient.alienVaultService.ipGeneral(query)
                           else RetrofitClient.alienVaultService.domainGeneral(query)
                if (resp.isSuccessful && resp.body() != null) {
                    apiKeyManager.recordUsage("otx")
                    val b = resp.body()!!
                    val pulseCount = b.pulseInfo?.count ?: 0
                    meta["otx_pulse_count"] = pulseCount.toString()
                    meta["otx_reputation"] = b.reputation?.toString() ?: "0"
                    sources.add(DataSource("AlienVault OTX", null, Date(), 0.85))
                    emit(SearchProgressEvent.Found("AlienVault OTX",
                        if (pulseCount > 0) "$pulseCount threat pulse${if (pulseCount != 1) "s" else ""}"
                        else "No threats — clean"))
                } else {
                    emit(SearchProgressEvent.NotFound("AlienVault OTX"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("AlienVault OTX", e.message ?: ""))
            }
        }

        if (isIp) {
            launch {
                emit(SearchProgressEvent.Checking("Shodan InternetDB"))
                try {
                    val resp = RetrofitClient.shodanInternetDbService.lookup(query)
                    if (resp.isSuccessful && resp.body() != null) {
                        val b = resp.body()!!
                        val ports = b.ports ?: emptyList()
                        val vulns = b.vulns ?: emptyList()
                        val hostnames = b.hostnames ?: emptyList()
                        meta["shodan_ports"] = ports.joinToString(", ")
                        meta["shodan_vulns"] = vulns.joinToString(", ")
                        meta["shodan_hostnames"] = hostnames.take(5).joinToString(", ")
                        meta["shodan_tags"] = (b.tags ?: emptyList()).joinToString(", ")
                        sources.add(DataSource("Shodan InternetDB", "https://www.shodan.io/host/$query", Date(), 0.9))
                        emit(SearchProgressEvent.Found("Shodan InternetDB",
                            buildString {
                                if (ports.isNotEmpty()) append("${ports.size} open port${if (ports.size != 1) "s" else ""}: ${ports.take(6).joinToString(", ")}")
                                if (vulns.isNotEmpty()) { if (isNotEmpty()) append(" · "); append("${vulns.size} CVE${if (vulns.size != 1) "s" else ""}") }
                            }.ifBlank { "No open ports" }
                        ))
                    } else if (resp.code() == 404) {
                        emit(SearchProgressEvent.NotFound("Shodan InternetDB"))
                    } else {
                        emit(SearchProgressEvent.Failed("Shodan InternetDB", "HTTP ${resp.code()}"))
                    }
                } catch (e: Exception) {
                    emit(SearchProgressEvent.Failed("Shodan InternetDB", e.message ?: ""))
                }
            }

            launch {
                emit(SearchProgressEvent.Checking("GreyNoise"))
                try {
                    val resp = RetrofitClient.greyNoiseService.lookup(query)
                    if (resp.isSuccessful && resp.body() != null) {
                        val b = resp.body()!!
                        meta["greynoise_noise"] = b.noise?.toString() ?: "false"
                        meta["greynoise_riot"] = b.riot?.toString() ?: "false"
                        meta["greynoise_classification"] = b.classification ?: ""
                        meta["greynoise_name"] = b.name ?: ""
                        meta["greynoise_last_seen"] = b.lastSeen ?: ""
                        sources.add(DataSource("GreyNoise", b.link, Date(), 0.85))
                        emit(SearchProgressEvent.Found("GreyNoise",
                            buildString {
                                if (b.noise == true) append("Internet scanner")
                                if (b.riot == true) { if (isNotEmpty()) append(" · "); append("Common service (RIOT)") }
                                if (!b.classification.isNullOrBlank()) { if (isNotEmpty()) append(" · "); append(b.classification) }
                                if (!b.name.isNullOrBlank()) { if (isNotEmpty()) append(" · "); append(b.name) }
                            }.ifBlank { "Not a known scanner" }
                        ))
                    } else if (resp.code() == 404) {
                        emit(SearchProgressEvent.NotFound("GreyNoise"))
                    } else {
                        emit(SearchProgressEvent.Failed("GreyNoise", "HTTP ${resp.code()}"))
                    }
                } catch (e: Exception) {
                    emit(SearchProgressEvent.Failed("GreyNoise", e.message ?: ""))
                }
            }

            launch {
                emit(SearchProgressEvent.Checking("Robtex"))
                try {
                    val req = Request.Builder()
                        .url("https://freeapi.robtex.com/ipquery/$query")
                        .addHeader("User-Agent", "SixDegrees-OSINT")
                        .build()
                    val resp = httpClient.newCall(req).execute()
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    val status = Regex("\"status\":\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                    val asName = Regex("\"asname\":\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                    val bgpRoute = Regex("\"bgproute\":\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                    val pasMatches = Regex("\"o\":\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(10).toList()
                    meta["robtex_as_name"] = asName
                    meta["robtex_bgp_route"] = bgpRoute
                    meta["robtex_passive_dns"] = pasMatches.joinToString(", ")
                    if (status == "ok") {
                        sources.add(DataSource("Robtex", null, Date(), 0.8))
                        emit(SearchProgressEvent.Found("Robtex",
                            buildString {
                                if (asName.isNotBlank()) append("AS: $asName")
                                if (bgpRoute.isNotBlank()) { if (isNotEmpty()) append(" · "); append(bgpRoute) }
                                if (pasMatches.isNotEmpty()) { if (isNotEmpty()) append(" · "); append("${pasMatches.size} passive DNS") }
                            }.ifBlank { "Data found" }
                        ))
                    } else {
                        emit(SearchProgressEvent.NotFound("Robtex"))
                    }
                } catch (e: Exception) {
                    emit(SearchProgressEvent.Failed("Robtex", e.message ?: ""))
                }
            }

            val abuseIpDbKey = apiKeyManager.abuseIpDbKey
            if (abuseIpDbKey.isNotBlank()) {
                launch {
                    emit(SearchProgressEvent.Checking("AbuseIPDB"))
                    try {
                        val req = Request.Builder()
                            .url("https://api.abuseipdb.com/api/v2/check?ipAddress=$query&maxAgeInDays=90")
                            .addHeader("Key", abuseIpDbKey)
                            .addHeader("Accept", "application/json")
                            .addHeader("User-Agent", "SixDegrees-OSINT")
                            .build()
                        val resp = httpClient.newCall(req).execute()
                        val body = resp.body?.string() ?: ""
                        resp.close()
                        val score = Regex("\"abuseConfidenceScore\":\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        val reports = Regex("\"totalReports\":\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        val domain = Regex("\"domain\":\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                        val isp = Regex("\"isp\":\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                        meta["abuseipdb_score"] = score.toString()
                        meta["abuseipdb_reports"] = reports.toString()
                        if (domain.isNotBlank()) meta["abuseipdb_domain"] = domain
                        if (isp.isNotBlank()) meta["abuseipdb_isp"] = isp
                        sources.add(DataSource("AbuseIPDB", null, Date(), 0.9))
                        emit(SearchProgressEvent.Found("AbuseIPDB",
                            "Confidence: $score% · $reports report${if (reports != 1) "s" else ""}${if (domain.isNotBlank()) " · $domain" else ""}"))
                    } catch (e: Exception) {
                        emit(SearchProgressEvent.Failed("AbuseIPDB", e.message ?: ""))
                    }
                }
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("URLhaus"))
            try {
                val reqBody = FormBody.Builder().add("host", query).build()
                val req = Request.Builder()
                    .url("https://urlhaus-api.abuse.ch/v1/host/")
                    .post(reqBody)
                    .addHeader("User-Agent", "SixDegrees-OSINT")
                    .build()
                val resp = httpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                resp.close()
                val queryStatus = Regex("\"query_status\":\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                val urlsCount = Regex("\"urls_count\":\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                meta["urlhaus_status"] = queryStatus
                meta["urlhaus_urls_count"] = urlsCount.toString()
                if (queryStatus == "is_host" || urlsCount > 0) {
                    sources.add(DataSource("URLhaus", null, Date(), 0.95))
                    emit(SearchProgressEvent.Found("URLhaus",
                        "MALWARE HOST — $urlsCount malicious URL${if (urlsCount != 1) "s" else ""} tracked"))
                } else {
                    emit(SearchProgressEvent.NotFound("URLhaus"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("URLhaus", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("ThreatCrowd"))
            try {
                if (isIp) {
                    val resp = RetrofitClient.threatCrowdService.ipReport(query)
                    if (resp.isSuccessful && resp.body()?.responseCode == "1") {
                        val b = resp.body()!!
                        val resolutions = b.resolutions ?: emptyList()
                        val domains = resolutions.mapNotNull { it.domain }.take(15)
                        meta["threatcrowd_domains"] = domains.joinToString(", ")
                        meta["threatcrowd_hashes"] = (b.hashes ?: emptyList()).take(5).joinToString(", ")
                        sources.add(DataSource("ThreatCrowd", null, Date(), 0.75))
                        emit(SearchProgressEvent.Found("ThreatCrowd",
                            "${resolutions.size} historic domain${if (resolutions.size != 1) "s" else ""}: ${domains.take(3).joinToString(", ")}"))
                    } else {
                        emit(SearchProgressEvent.NotFound("ThreatCrowd"))
                    }
                } else {
                    val resp = RetrofitClient.threatCrowdService.domainReport(query)
                    if (resp.isSuccessful && resp.body()?.responseCode == "1") {
                        val b = resp.body()!!
                        val subs = (b.subdomains ?: emptyList()).take(15)
                        val emails = (b.emails ?: emptyList()).take(10)
                        val resolutions = (b.resolutions ?: emptyList()).take(10)
                        meta["threatcrowd_subdomains"] = subs.joinToString(", ")
                        meta["threatcrowd_emails"] = emails.joinToString(", ")
                        meta["threatcrowd_resolutions"] = resolutions.mapNotNull {
                            "${it.ipAddress} (${it.lastResolved})"
                        }.joinToString(", ")
                        if (subs.isNotEmpty() || emails.isNotEmpty()) {
                            sources.add(DataSource("ThreatCrowd", null, Date(), 0.75))
                            emit(SearchProgressEvent.Found("ThreatCrowd",
                                buildString {
                                    if (subs.isNotEmpty()) append("${subs.size} subdomain${if (subs.size != 1) "s" else ""}")
                                    if (emails.isNotEmpty()) { if (isNotEmpty()) append(" · "); append("${emails.size} email${if (emails.size != 1) "s" else ""}") }
                                }
                            ))
                        } else {
                            emit(SearchProgressEvent.NotFound("ThreatCrowd"))
                        }
                    } else {
                        emit(SearchProgressEvent.NotFound("ThreatCrowd"))
                    }
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("ThreatCrowd", e.message ?: ""))
            }
        }

        if (!isIp) {
            launch {
                emit(SearchProgressEvent.Checking("HackerTarget Host Search"))
                try {
                    val resp = RetrofitClient.hackerTargetService.hostSearch(query)
                    if (resp.isSuccessful) {
                        val body = resp.body() ?: ""
                        if (!body.startsWith("error") && body.isNotBlank()) {
                            apiKeyManager.recordUsage("hackertarget")
                            val lines = body.lines().filter { it.isNotBlank() }
                            meta["hackertarget_hostsearch"] = lines.take(20).joinToString("\n")
                            sources.add(DataSource("HackerTarget Hosts", null, Date(), 0.8))
                            emit(SearchProgressEvent.Found("HackerTarget Host Search",
                                "${lines.size} host${if (lines.size != 1) "s" else ""} found"))
                        } else {
                            emit(SearchProgressEvent.NotFound("HackerTarget Host Search"))
                        }
                    } else {
                        emit(SearchProgressEvent.Failed("HackerTarget Host Search", "HTTP ${resp.code()}"))
                    }
                } catch (e: Exception) {
                    emit(SearchProgressEvent.Failed("HackerTarget Host Search", e.message ?: ""))
                }
            }
        }

        if (isIp) {
            launch {
                emit(SearchProgressEvent.Checking("ipwho.is"))
                try {
                    val req = Request.Builder()
                        .url("https://ipwho.is/$query")
                        .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                        .build()
                    val resp = httpClient.newCall(req).execute()
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    val success = Regex("\"success\":(true|false)").find(body)?.groupValues?.get(1)
                    if (success == "true") {
                        val org = Regex("\"org\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                        val region = Regex("\"region\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                        val city = Regex("\"city\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                        val country = Regex("\"country\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                        val tz = Regex("\"id\":\"([^\"]+/[^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                        if (org.isNotBlank()) meta["ipwhois_org"] = org
                        if (region.isNotBlank()) meta["ipwhois_region"] = region
                        if (city.isNotBlank()) meta["ipwhois_city"] = city
                        if (country.isNotBlank()) meta["ipwhois_country"] = country
                        if (tz.isNotBlank()) meta["ipwhois_timezone"] = tz
                        sources.add(DataSource("ipwho.is", null, Date(), 0.8))
                        emit(SearchProgressEvent.Found("ipwho.is",
                            buildString {
                                if (city.isNotBlank()) append(city)
                                if (region.isNotBlank()) { if (isNotEmpty()) append(", "); append(region) }
                                if (country.isNotBlank()) { if (isNotEmpty()) append(", "); append(country) }
                                if (org.isNotBlank()) { if (isNotEmpty()) append(" · "); append(org) }
                            }.ifBlank { "Location data found" }
                        ))
                    } else {
                        emit(SearchProgressEvent.NotFound("ipwho.is"))
                    }
                } catch (e: Exception) {
                    emit(SearchProgressEvent.Failed("ipwho.is", e.message ?: ""))
                }
            }

            launch {
                emit(SearchProgressEvent.Checking("ipinfo.io"))
                try {
                    val req = Request.Builder()
                        .url("https://ipinfo.io/$query/json")
                        .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                        .build()
                    val resp = httpClient.newCall(req).execute()
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    val bogon = body.contains("\"bogon\":true")
                    val hostname = Regex("\"hostname\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                    val org = Regex("\"org\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                    val postal = Regex("\"postal\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                    if (bogon) {
                        meta["ipinfo_bogon"] = "true"
                        emit(SearchProgressEvent.Found("ipinfo.io", "Bogon/private address"))
                    } else if (org.isNotBlank()) {
                        if (hostname.isNotBlank()) meta["ipinfo_hostname"] = hostname
                        meta["ipinfo_org"] = org
                        if (postal.isNotBlank()) meta["ipinfo_postal"] = postal
                        sources.add(DataSource("ipinfo.io", "https://ipinfo.io/$query", Date(), 0.85))
                        emit(SearchProgressEvent.Found("ipinfo.io",
                            buildString {
                                append(org)
                                if (hostname.isNotBlank()) { append(" · "); append(hostname) }
                                if (postal.isNotBlank()) { append(" · "); append(postal) }
                            }
                        ))
                    } else {
                        emit(SearchProgressEvent.NotFound("ipinfo.io"))
                    }
                } catch (e: Exception) {
                    emit(SearchProgressEvent.Failed("ipinfo.io", e.message ?: ""))
                }
            }

            launch {
                emit(SearchProgressEvent.Checking("IPQuery.io"))
                try {
                    val req = Request.Builder()
                        .url("https://api.ipquery.io/$query")
                        .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                        .build()
                    val resp = fastHttpClient.newCall(req).execute()
                    val body = resp.body?.string() ?: ""; resp.close()
                    val riskScore = Regex("\"risk_score\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                    if (riskScore >= 0) {
                        val isVpn = Regex("\"is_vpn\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false
                        val isProxy = Regex("\"is_proxy\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false
                        val isTor = Regex("\"is_tor\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false
                        val isDataCenter = Regex("\"is_datacenter\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false
                        val isp = Regex("\"isp\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                        val asn = Regex("\"asn\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                        val city = Regex("\"city\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                        val country = Regex("\"country\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                        meta["ipquery_risk_score"] = riskScore.toString()
                        if (isVpn) meta["ipquery_vpn"] = "true"
                        if (isProxy) meta["ipquery_proxy"] = "true"
                        if (isTor) meta["ipquery_tor"] = "true"
                        if (isDataCenter) meta["ipquery_datacenter"] = "true"
                        if (isp.isNotBlank()) meta["ipquery_isp"] = isp
                        if (asn.isNotBlank()) meta["ipquery_asn"] = asn
                        if (city.isNotBlank()) meta["ipquery_city"] = city
                        if (country.isNotBlank()) meta["ipquery_country"] = country
                        sources.add(DataSource("IPQuery.io", null, Date(), 0.85))
                        emit(SearchProgressEvent.Found("IPQuery.io",
                            buildString {
                                append("Risk: $riskScore/100")
                                if (isVpn) append(" · VPN")
                                if (isProxy) append(" · Proxy")
                                if (isTor) append(" · Tor")
                                if (isDataCenter) append(" · Datacenter")
                                if (isp.isNotBlank()) { append(" · "); append(isp) }
                            }
                        ))
                    } else {
                        emit(SearchProgressEvent.NotFound("IPQuery.io"))
                    }
                } catch (e: Exception) {
                    emit(SearchProgressEvent.Failed("IPQuery.io", e.message ?: ""))
                }
            }

            val ipqsIpKey = apiKeyManager.ipqsKey
            if (ipqsIpKey.isNotBlank()) {
                launch {
                    emit(SearchProgressEvent.Checking("IPQS IP Reputation"))
                    try {
                        val req = Request.Builder()
                            .url("https://ipqualityscore.com/api/json/ip/$ipqsIpKey/$query?strictness=1&allow_public_access_points=true")
                            .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                            .build()
                        val resp = httpClient.newCall(req).execute()
                        val body = resp.body?.string() ?: ""; resp.close()
                        val success = Regex("\"success\"\\s*:\\s*true").containsMatchIn(body)
                        if (success) {
                            apiKeyManager.recordUsage("ipqs")
                            val fraudScore = Regex("\"fraud_score\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                            val isVpn = Regex("\"vpn\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false
                            val isProxy = Regex("\"proxy\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false
                            val isTor = Regex("\"tor\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false
                            val isBot = Regex("\"bot_status\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false
                            val isp = Regex("\"ISP\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                            val country = Regex("\"country_code\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                            meta["ipqs_ip_fraud_score"] = fraudScore.toString()
                            if (isVpn) meta["ipqs_ip_vpn"] = "true"
                            if (isProxy) meta["ipqs_ip_proxy"] = "true"
                            if (isTor) meta["ipqs_ip_tor"] = "true"
                            if (isBot) meta["ipqs_ip_bot"] = "true"
                            if (isp.isNotBlank()) meta["ipqs_ip_isp"] = isp
                            if (country.isNotBlank()) meta["ipqs_ip_country"] = country
                            sources.add(DataSource("IPQS IP", null, Date(), 0.88))
                            emit(SearchProgressEvent.Found("IPQS IP Reputation",
                                buildString {
                                    append("Fraud: $fraudScore/100")
                                    if (isVpn) append(" · VPN")
                                    if (isProxy) append(" · Proxy")
                                    if (isTor) append(" · Tor")
                                    if (isBot) append(" · Bot")
                                }
                            ))
                        } else {
                            emit(SearchProgressEvent.NotFound("IPQS IP Reputation"))
                        }
                    } catch (e: Exception) {
                        emit(SearchProgressEvent.Failed("IPQS IP Reputation", e.message ?: ""))
                    }
                }
            }

            launch {
                emit(SearchProgressEvent.Checking("Maltiverse"))
                try {
                    val req = Request.Builder()
                        .url("https://api.maltiverse.com/ip/$query")
                        .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                        .build()
                    val resp = httpClient.newCall(req).execute()
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    if (resp.isSuccessful && body.isNotBlank() && !body.contains("\"error\"") && !body.contains("Not found")) {
                        val classification = Regex("\"classification\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                        val asName = Regex("\"as_name\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                        val country = Regex("\"country_code\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                        val blacklists = Regex("\"blacklist\":\\s*\\[([^\\]]+)\\]").find(body)?.groupValues?.get(1)
                            ?.let { Regex("\"([^\"]+)\"").findAll(it).map { m -> m.groupValues[1] }.take(5).toList() }
                            ?: emptyList()
                        if (classification.isNotBlank() || asName.isNotBlank()) {
                            if (classification.isNotBlank()) meta["maltiverse_classification"] = classification
                            if (asName.isNotBlank()) meta["maltiverse_as_name"] = asName
                            if (country.isNotBlank()) meta["maltiverse_country"] = country
                            if (blacklists.isNotEmpty()) meta["maltiverse_blacklists"] = blacklists.joinToString(", ")
                            sources.add(DataSource("Maltiverse", null, Date(), 0.8))
                            emit(SearchProgressEvent.Found("Maltiverse",
                                buildString {
                                    if (classification == "malicious") append("MALICIOUS")
                                    else if (classification.isNotBlank()) append(classification.replaceFirstChar { it.uppercase() })
                                    if (asName.isNotBlank()) { if (isNotEmpty()) append(" · "); append(asName) }
                                    if (blacklists.isNotEmpty()) { if (isNotEmpty()) append(" · "); append("${blacklists.size} blacklist${if (blacklists.size != 1) "s" else ""}") }
                                }.ifBlank { "Threat data found" }
                            ))
                        } else {
                            emit(SearchProgressEvent.NotFound("Maltiverse"))
                        }
                    } else {
                        emit(SearchProgressEvent.NotFound("Maltiverse"))
                    }
                } catch (e: Exception) {
                    emit(SearchProgressEvent.Failed("Maltiverse", e.message ?: ""))
                }
            }
        }

        if (!isIp) {
            launch {
                emit(SearchProgressEvent.Checking("DomainsDB"))
                try {
                    val domainPart = query.substringBefore(".")
                    val tldPart = if (query.contains(".")) query.substringAfter(".") else "com"
                    val req = Request.Builder()
                        .url("https://api.domainsdb.info/v1/domains/search?domain=$domainPart&zone=$tldPart&limit=10")
                        .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                        .build()
                    val resp = fastHttpClient.newCall(req).execute()
                    val body = resp.body?.string() ?: ""; resp.close()
                    val total = Regex("\"total\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val domains = Regex("\"domain\"\\s*:\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(10).toList()
                    val createDates = Regex("\"create_date\"\\s*:\\s*\"([^\"T]+)").findAll(body).map { it.groupValues[1].trim() }.take(10).toList()
                    if (total > 0 && domains.isNotEmpty()) {
                        meta["domainsdb_total"] = total.toString()
                        meta["domainsdb_domains"] = domains.zip(createDates.ifEmpty { List(domains.size) { "" } })
                            .take(8).joinToString("\n") { (d, c) -> if (c.isNotBlank()) "$d (created $c)" else d }
                        sources.add(DataSource("DomainsDB", null, Date(), 0.7))
                        emit(SearchProgressEvent.Found("DomainsDB",
                            "$total related domain${if (total != 1) "s" else ""} registered: ${domains.take(3).joinToString(", ")}"))
                    } else {
                        emit(SearchProgressEvent.NotFound("DomainsDB"))
                    }
                } catch (e: Exception) {
                    emit(SearchProgressEvent.Failed("DomainsDB", e.message ?: ""))
                }
            }

            launch {
                emit(SearchProgressEvent.Checking("RDAP"))
                try {
                    val req = Request.Builder()
                        .url("https://rdap.org/domain/$query")
                        .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                        .addHeader("Accept", "application/rdap+json")
                        .build()
                    val resp = httpClient.newCall(req).execute()
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    if (resp.isSuccessful && body.isNotBlank()) {
                        val registrar = Regex("\"registrarName\":\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
                        val registered = Regex("\"registration\":\\s*\"([^\"T]+)").find(body)?.groupValues?.get(1) ?: ""
                        val expiry = Regex("\"expiration\":\\s*\"([^\"T]+)").find(body)?.groupValues?.get(1) ?: ""
                        val nameservers = Regex("\"ldhName\":\\s*\"([^\"]+)\"").findAll(body)
                            .map { it.groupValues[1].lowercase() }
                            .filter { it.contains(".") }
                            .distinct().take(4).toList()
                        val status = Regex("\"clientTransferProhibited\"|\"active\"").containsMatchIn(body)
                        if (registrar.isNotBlank() || nameservers.isNotEmpty()) {
                            if (registrar.isNotBlank()) meta["rdap_registrar"] = registrar
                            if (registered.isNotBlank()) meta["rdap_registered"] = registered.trim()
                            if (expiry.isNotBlank()) meta["rdap_expiry"] = expiry.trim()
                            if (nameservers.isNotEmpty()) meta["rdap_nameservers"] = nameservers.joinToString(", ")
                            sources.add(DataSource("RDAP", "https://rdap.org/domain/$query", Date(), 0.9))
                            emit(SearchProgressEvent.Found("RDAP",
                                buildString {
                                    if (registrar.isNotBlank()) append("Registrar: $registrar")
                                    if (registered.isNotBlank()) { if (isNotEmpty()) append(" · "); append("Reg: ${registered.trim().take(10)}") }
                                    if (expiry.isNotBlank()) { if (isNotEmpty()) append(" · "); append("Exp: ${expiry.trim().take(10)}") }
                                    if (nameservers.isNotEmpty()) { if (isNotEmpty()) append(" · "); append("NS: ${nameservers.first()}") }
                                }.ifBlank { "Registration data found" }
                            ))
                        } else {
                            emit(SearchProgressEvent.NotFound("RDAP"))
                        }
                    } else {
                        emit(SearchProgressEvent.NotFound("RDAP"))
                    }
                } catch (e: Exception) {
                    emit(SearchProgressEvent.Failed("RDAP", e.message ?: ""))
                }
            }
        }

    }

    private suspend fun personSearch(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) = coroutineScope {
        val piplKey = apiKeyManager.piplKey
        if (piplKey.isNotBlank()) {
            emit(SearchProgressEvent.Checking("Pipl"))
            try {
                val nameParts = query.trim().split(" ")
                val resp = RetrofitClient.piplService.searchPerson(
                    apiKey = piplKey,
                    rawName = query,
                    firstName = nameParts.firstOrNull(),
                    lastName = if (nameParts.size > 1) nameParts.drop(1).joinToString(" ") else null,
                    showSources = "all"
                )
                if (resp.isSuccessful && resp.body()?.person != null) {
                    val entity = mapPiplPersonToEntity(resp.body()!!.person!!)
                    db.personDao().insertPerson(entity)
                    sources.add(DataSource("Pipl", null, Date(), 0.9))
                    emit(SearchProgressEvent.Found("Pipl", "Full profile found"))
                } else {
                    emit(SearchProgressEvent.NotFound("Pipl"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("Pipl", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("CourtListener"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://www.courtlistener.com/api/rest/v3/search/?q=$encoded&type=r&format=json")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .build()
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    val count = Regex("\"count\":\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    meta["courtlistener_count"] = count.toString()
                    meta["courtlistener_link"] = "https://www.courtlistener.com/?q=$encoded&type=r&order_by=score+desc"
                    if (count > 0) {
                        sources.add(DataSource("CourtListener", meta["courtlistener_link"], Date(), 0.8))
                        emit(SearchProgressEvent.Found("CourtListener",
                            "$count court record${if (count != 1) "s" else ""} found"))
                    } else {
                        emit(SearchProgressEvent.NotFound("CourtListener"))
                    }
                } else {
                    resp.close()
                    emit(SearchProgressEvent.Failed("CourtListener", "HTTP ${resp.code}"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("CourtListener", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("OpenCorporates Officers"))
            try {
                val resp = RetrofitClient.openCorporatesService.searchOfficers(query)
                if (resp.isSuccessful) {
                    val officers = resp.body()?.results?.officers ?: emptyList()
                    if (officers.isNotEmpty()) {
                        meta["officer_matches"] = officers.size.toString()
                        meta["officer_details"] = officers.take(3).joinToString("\n") { item ->
                            "${item.officer?.name} | ${item.officer?.position} | ${item.officer?.company?.name}"
                        }
                        sources.add(DataSource("OpenCorporates Officers", null, Date(), 0.7))
                        emit(SearchProgressEvent.Found("OpenCorporates Officers",
                            "${officers.size} officer record${if (officers.size != 1) "s" else ""}"))
                    } else {
                        emit(SearchProgressEvent.NotFound("OpenCorporates Officers"))
                    }
                } else {
                    emit(SearchProgressEvent.Failed("OpenCorporates Officers", "HTTP ${resp.code()}"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("OpenCorporates Officers", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("JailBase"))
            try {
                val resp = RetrofitClient.jailBaseService.search(name = query)
                if (resp.isSuccessful && resp.body() != null) {
                    val records = resp.body()!!.records ?: emptyList()
                    val total = resp.body()!!.total_records ?: records.size
                    meta["arrest_count"] = total.toString()
                    if (records.isNotEmpty()) {
                        meta["arrest_records"] = records.take(5).joinToString("\n") { r ->
                            "${r.first_name ?: ""} ${r.last_name ?: ""} | ${r.charge ?: "Unknown charge"} | ${r.arrest_date ?: "Unknown date"} | ${r.agency ?: "Unknown agency"}"
                        }
                        sources.add(DataSource("JailBase", null, Date(), 0.85))
                        emit(SearchProgressEvent.Found("JailBase",
                            "$total arrest record${if (total != 1) "s" else ""} found"))
                    } else {
                        emit(SearchProgressEvent.NotFound("JailBase"))
                    }
                } else {
                    emit(SearchProgressEvent.Failed("JailBase", "HTTP ${resp.code()}"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("JailBase", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("Wikipedia"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&srlimit=3&format=json")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .build()
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    val hits = Regex("\"totalhits\":(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val titles = Regex("\"title\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(3).toList()
                    if (hits > 0 && titles.isNotEmpty()) {
                        meta["wikipedia_hits"] = hits.toString()
                        meta["wikipedia_titles"] = titles.joinToString(" | ")
                        meta["wikipedia_link"] = "https://en.wikipedia.org/w/index.php?search=$encoded"
                        sources.add(DataSource("Wikipedia", meta["wikipedia_link"], Date(), 0.7))
                        emit(SearchProgressEvent.Found("Wikipedia", "$hits article${if (hits != 1) "s" else ""}: ${titles.firstOrNull() ?: ""}"))
                    } else {
                        emit(SearchProgressEvent.NotFound("Wikipedia"))
                    }
                } else {
                    resp.close()
                    emit(SearchProgressEvent.Failed("Wikipedia", "HTTP ${resp.code}"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("Wikipedia", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("WikiData"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://www.wikidata.org/w/api.php?action=wbsearchentities&search=$encoded&language=en&limit=3&format=json&type=item")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .build()
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    val labels = Regex("\"label\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(3).toList()
                    val descs = Regex("\"description\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(3).toList()
                    val ids = Regex("\"id\":\"(Q\\d+)\"").findAll(body).map { it.groupValues[1] }.take(3).toList()
                    if (labels.isNotEmpty()) {
                        meta["wikidata_labels"] = labels.joinToString(" | ")
                        meta["wikidata_descriptions"] = descs.zip(labels).joinToString("\n") { (d, l) -> "$l: $d" }
                        if (ids.isNotEmpty()) meta["wikidata_link"] = "https://www.wikidata.org/wiki/${ids.first()}"
                        sources.add(DataSource("WikiData", meta["wikidata_link"], Date(), 0.65))
                        emit(SearchProgressEvent.Found("WikiData", labels.firstOrNull() ?: "${labels.size} entities"))
                    } else {
                        emit(SearchProgressEvent.NotFound("WikiData"))
                    }
                } else {
                    resp.close()
                    emit(SearchProgressEvent.Failed("WikiData", "HTTP ${resp.code}"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("WikiData", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("FEC Campaign Finance"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://api.open.fec.gov/v1/candidates/?q=$encoded&api_key=DEMO_KEY&per_page=3")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .build()
                val resp = fastHttpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    val total = Regex("\"total_count\":(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val names = Regex("\"name\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(3).toList()
                    val offices = Regex("\"office_full\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(3).toList()
                    if (total > 0 && names.isNotEmpty()) {
                        meta["fec_candidate_count"] = total.toString()
                        meta["fec_candidates"] = names.zip(offices).take(3).joinToString("\n") { (n, o) -> "$n — $o" }
                        meta["fec_link"] = "https://www.fec.gov/data/candidates/?q=$encoded"
                        sources.add(DataSource("FEC Campaign Finance", meta["fec_link"], Date(), 0.75))
                        emit(SearchProgressEvent.Found("FEC Campaign Finance", "$total candidate record${if (total != 1) "s" else ""}"))
                    } else {
                        emit(SearchProgressEvent.NotFound("FEC Campaign Finance"))
                    }
                } else {
                    resp.close()
                    emit(SearchProgressEvent.NotFound("FEC Campaign Finance"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("FEC Campaign Finance", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("SEC EDGAR"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://efts.sec.gov/LATEST/search-index?q=%22$encoded%22&dateRange=custom&startdt=2000-01-01&forms=4")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .build()
                val resp = fastHttpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    val hits = Regex("\"total\":\\s*\\{[^}]*\"value\":(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val entities = Regex("\"entity_name\":\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.distinct().take(5).toList()
                    if (hits > 0) {
                        meta["sec_person_hits"] = hits.toString()
                        if (entities.isNotEmpty()) meta["sec_person_entities"] = entities.joinToString(", ")
                        meta["sec_person_link"] = "https://efts.sec.gov/LATEST/search-index?q=%22$encoded%22&forms=4"
                        sources.add(DataSource("SEC EDGAR", meta["sec_person_link"], Date(), 0.7))
                        emit(SearchProgressEvent.Found("SEC EDGAR", "$hits Form-4 filing${if (hits != 1) "s" else ""} found"))
                    } else {
                        emit(SearchProgressEvent.NotFound("SEC EDGAR"))
                    }
                } else {
                    resp.close()
                    emit(SearchProgressEvent.NotFound("SEC EDGAR"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("SEC EDGAR", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("GLEIF Entity Search"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://api.gleif.org/api/v1/fuzzycompletions?field=entity.legalName&q=$encoded&page%5Bsize%5D=5")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .addHeader("Accept", "application/vnd.api+json")
                    .build()
                val resp = fastHttpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""; resp.close()
                val names = Regex("\"value\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(5).toList()
                if (names.isNotEmpty()) {
                    meta["gleif_entities"] = names.joinToString("\n")
                    meta["gleif_link"] = "https://search.gleif.org/#/search?q=$encoded"
                    sources.add(DataSource("GLEIF", meta["gleif_link"], Date(), 0.75))
                    emit(SearchProgressEvent.Found("GLEIF Entity Search", "${names.size} legal entit${if (names.size != 1) "ies" else "y"}: ${names.firstOrNull() ?: ""}"))
                } else {
                    emit(SearchProgressEvent.NotFound("GLEIF Entity Search"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("GLEIF Entity Search", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("SEC EDGAR Full-Text"))
            try {
                val encoded = URLEncoder.encode("\"$query\"", "UTF-8")
                val req = Request.Builder()
                    .url("https://efts.sec.gov/LATEST/search-index?q=$encoded&dateRange=custom&startdt=2000-01-01")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .build()
                val resp = fastHttpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""; resp.close()
                val hits = Regex("\"total\":\\s*\\{[^}]*\"value\":(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val formTypes = Regex("\"form_type\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.distinct().take(5).toList()
                val entities = Regex("\"entity_name\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.distinct().take(5).toList()
                if (hits > 0) {
                    meta["sec_fulltext_hits"] = hits.toString()
                    if (formTypes.isNotEmpty()) meta["sec_fulltext_forms"] = formTypes.joinToString(", ")
                    if (entities.isNotEmpty()) meta["sec_fulltext_entities"] = entities.joinToString(", ")
                    meta["sec_fulltext_link"] = "https://efts.sec.gov/LATEST/search-index?q=$encoded"
                    sources.add(DataSource("SEC EDGAR All Forms", meta["sec_fulltext_link"], Date(), 0.7))
                    emit(SearchProgressEvent.Found("SEC EDGAR Full-Text", "$hits filing${if (hits != 1) "s" else ""} across ${formTypes.size} form type${if (formTypes.size != 1) "s" else ""}"))
                } else {
                    emit(SearchProgressEvent.NotFound("SEC EDGAR Full-Text"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("SEC EDGAR Full-Text", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("Florida SunBiz Officers"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://services.sunbiz.org/Filings/OfficerSearch/ByOfficerName?searchTerm=$encoded")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0")
                    .addHeader("Accept", "application/json, text/javascript, */*")
                    .addHeader("Referer", "https://search.sunbiz.org/")
                    .build()
                val resp = fastHttpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""; resp.close()
                val filingNumbers = Regex("\"documentNumber\"\\s*:\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(5).toList()
                val entityNames = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.filter { it.length > 2 }.distinct().take(5).toList()
                val statuses = Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(5).toList()
                if (filingNumbers.isNotEmpty() || entityNames.isNotEmpty()) {
                    val combined = entityNames.zip(statuses.ifEmpty { List(entityNames.size) { "Unknown" } }).take(5)
                    meta["sunbiz_officer_count"] = filingNumbers.size.coerceAtLeast(entityNames.size).toString()
                    meta["sunbiz_officer_companies"] = combined.joinToString("\n") { (n, s) -> "$n ($s)" }.ifBlank { entityNames.joinToString("\n") }
                    meta["sunbiz_officer_link"] = "https://search.sunbiz.org/Inquiry/CorporationSearch/ByOfficerRegisteredAgentName?SearchType=OfficerName&searchTerm=$encoded"
                    sources.add(DataSource("FL SunBiz Officers", meta["sunbiz_officer_link"], Date(), 0.85))
                    emit(SearchProgressEvent.Found("Florida SunBiz Officers", "${filingNumbers.size} FL corporate filing${if (filingNumbers.size != 1) "s" else ""}: ${entityNames.firstOrNull() ?: ""}"))
                } else {
                    emit(SearchProgressEvent.NotFound("Florida SunBiz Officers"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("Florida SunBiz Officers", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("SAM.gov Entity Registry"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://api.sam.gov/entity-information/v3/entities?api_key=DEMO_KEY&legalBusinessName=$encoded&includeSections=entityRegistration&registrationStatus=A&resultCount=5")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .addHeader("Accept", "application/json")
                    .build()
                val resp = fastHttpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""; resp.close()
                val totalRecords = Regex("\"totalRecords\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val entityNames = Regex("\"legalBusinessName\"\\s*:\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.distinct().take(5).toList()
                val ueiCodes = Regex("\"ueiSAM\"\\s*:\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(5).toList()
                val states = Regex("\"stateOrProvinceOfIncorporation\"\\s*:\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(5).toList()
                if (totalRecords > 0 && entityNames.isNotEmpty()) {
                    meta["sam_entity_count"] = totalRecords.toString()
                    meta["sam_entities"] = entityNames.zip(states.ifEmpty { List(entityNames.size) { "" } }).take(5)
                        .joinToString("\n") { (n, s) -> if (s.isNotBlank()) "$n ($s)" else n }
                    if (ueiCodes.isNotEmpty()) meta["sam_uei_codes"] = ueiCodes.joinToString(", ")
                    meta["sam_link"] = "https://sam.gov/search/?keywords=$encoded&index=ei&sort=relevance"
                    sources.add(DataSource("SAM.gov", meta["sam_link"], Date(), 0.8))
                    emit(SearchProgressEvent.Found("SAM.gov Entity Registry", "$totalRecords federal entit${if (totalRecords != 1) "ies" else "y"}: ${entityNames.firstOrNull() ?: ""}"))
                } else {
                    emit(SearchProgressEvent.NotFound("SAM.gov Entity Registry"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("SAM.gov Entity Registry", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("Corporations Wiki"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://www.corporationswiki.com/l/search?q=$encoded")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("Accept-Language", "en-US,en;q=0.5")
                    .addHeader("Referer", "https://www.corporationswiki.com/")
                    .build()
                val resp = fastHttpClient.newCall(req).execute()
                val html = resp.body?.string() ?: ""; resp.close()
                val companyNames = Regex("<a[^>]+href=\"/p/[^\"]+\"[^>]*>([^<]{3,80})</a>").findAll(html)
                    .map { it.groupValues[1].trim() }
                    .filter { it.isNotBlank() && !it.contains("\\s{3,}".toRegex()) }
                    .distinct().take(8).toList()
                val states = Regex("(?:incorporated|formed|registered)[^<]*<[^>]*>([A-Z]{2})<", RegexOption.IGNORE_CASE).findAll(html)
                    .map { it.groupValues[1] }.take(8).toList()
                val officers = Regex("class=\"[^\"]*officer[^\"]*\"[^>]*>[^<]*<[^>]+>([A-Z][a-z]+ [A-Z][a-z]+)").findAll(html)
                    .map { it.groupValues[1] }.distinct().take(5).toList()
                if (companyNames.isNotEmpty()) {
                    meta["corpwiki_person_companies"] = companyNames.joinToString("\n")
                    meta["corpwiki_person_link"] = "https://www.corporationswiki.com/l/search?q=$encoded"
                    if (states.isNotEmpty()) meta["corpwiki_person_states"] = states.distinct().take(5).joinToString(", ")
                    if (officers.isNotEmpty()) meta["corpwiki_associates"] = officers.joinToString(", ")
                    sources.add(DataSource("Corporations Wiki", meta["corpwiki_person_link"], Date(), 0.75))
                    emit(SearchProgressEvent.Found("Corporations Wiki", "${companyNames.size} corporate record${if (companyNames.size != 1) "s" else ""}: ${companyNames.firstOrNull() ?: ""}"))
                } else {
                    emit(SearchProgressEvent.NotFound("Corporations Wiki"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("Corporations Wiki", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("California SOS Officers"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://businesssearch.sos.ca.gov/CBS/SearchResults?filing_type=ALL&status=ACTIVE&SearchType=O&SearchValue=$encoded")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("Referer", "https://businesssearch.sos.ca.gov/")
                    .build()
                val resp = fastHttpClient.newCall(req).execute()
                val html = resp.body?.string() ?: ""; resp.close()
                val entityNames = Regex("<td[^>]*>\\s*<a[^>]+href=\"[^\"]*BusinessDetail[^\"]+\"[^>]*>([^<]+)</a>\\s*</td>").findAll(html)
                    .map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.distinct().take(8).toList()
                val statuses = Regex("<td[^>]*class=\"[^\"]*status[^\"]*\"[^>]*>([^<]+)</td>", RegexOption.IGNORE_CASE).findAll(html)
                    .map { it.groupValues[1].trim() }.take(8).toList()
                val entityTypes = Regex("<td[^>]*class=\"[^\"]*type[^\"]*\"[^>]*>([^<]+)</td>", RegexOption.IGNORE_CASE).findAll(html)
                    .map { it.groupValues[1].trim() }.take(8).toList()
                if (entityNames.isNotEmpty()) {
                    meta["ca_sos_officer_count"] = entityNames.size.toString()
                    meta["ca_sos_officer_entities"] = entityNames.indices.take(8).joinToString("\n") { i ->
                        val n = entityNames.getOrNull(i) ?: ""
                        val s = statuses.getOrNull(i) ?: ""
                        val t = entityTypes.getOrNull(i) ?: ""
                        listOf(n, t, s).filter { it.isNotBlank() }.joinToString(" | ")
                    }
                    meta["ca_sos_officer_link"] = "https://businesssearch.sos.ca.gov/CBS/SearchResults?filing_type=ALL&status=ACTIVE&SearchType=O&SearchValue=$encoded"
                    sources.add(DataSource("CA SOS Officers", meta["ca_sos_officer_link"], Date(), 0.85))
                    emit(SearchProgressEvent.Found("California SOS Officers", "${entityNames.size} CA entit${if (entityNames.size != 1) "ies" else "y"}: ${entityNames.firstOrNull() ?: ""}"))
                } else {
                    emit(SearchProgressEvent.NotFound("California SOS Officers"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("California SOS Officers", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("Google News RSS"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://news.google.com/rss/search?q=$encoded&hl=en-US&gl=US&ceid=US:en")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .build()
                val resp = fastHttpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    val titles = Regex("<title><!\\[CDATA\\[([^\\]]+)\\]\\]></title>").findAll(body)
                        .map { it.groupValues[1] }.drop(1).take(5).toList()
                    val sources_found = Regex("<source[^>]*>([^<]+)</source>").findAll(body).map { it.groupValues[1] }.take(3).toList()
                    if (titles.isNotEmpty()) {
                        meta["news_article_count"] = titles.size.toString()
                        meta["news_titles"] = titles.joinToString("\n")
                        meta["news_sources_list"] = sources_found.joinToString(", ")
                        meta["news_link"] = "https://news.google.com/search?q=$encoded"
                        sources.add(DataSource("Google News", meta["news_link"], Date(), 0.6))
                        emit(SearchProgressEvent.Found("Google News RSS", "${titles.size} recent article${if (titles.size != 1) "s" else ""}"))
                    } else {
                        emit(SearchProgressEvent.NotFound("Google News RSS"))
                    }
                } else {
                    resp.close()
                    emit(SearchProgressEvent.NotFound("Google News RSS"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("Google News RSS", e.message ?: ""))
            }
        }

        meta["person_query"] = query

        launch {
            val firstName = query.trim().split(" ").firstOrNull() ?: query
            emit(SearchProgressEvent.Checking("Name Demographics"))
            try {
                val encoded = URLEncoder.encode(firstName, "UTF-8")
                val genderReq = Request.Builder()
                    .url("https://api.genderize.io?name=$encoded")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0").build()
                val ageReq = Request.Builder()
                    .url("https://api.agify.io?name=$encoded")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0").build()
                val natReq = Request.Builder()
                    .url("https://api.nationalize.io?name=$encoded")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0").build()
                val genderResp = fastHttpClient.newCall(genderReq).execute()
                val genderBody = genderResp.body?.string() ?: ""; genderResp.close()
                val ageResp = fastHttpClient.newCall(ageReq).execute()
                val ageBody = ageResp.body?.string() ?: ""; ageResp.close()
                val natResp = fastHttpClient.newCall(natReq).execute()
                val natBody = natResp.body?.string() ?: ""; natResp.close()
                val gender = Regex("\"gender\":\"([^\"]+)\"").find(genderBody)?.groupValues?.get(1) ?: ""
                val genderProb = Regex("\"probability\":([\\d.]+)").find(genderBody)?.groupValues?.get(1)?.toFloatOrNull()
                val ageEstimate = Regex("\"age\":(\\d+)").find(ageBody)?.groupValues?.get(1) ?: ""
                val topCountry = Regex("\"country_id\":\"([^\"]+)\"").find(natBody)?.groupValues?.get(1) ?: ""
                val countryProb = Regex("\"probability\":([\\d.]+)").find(natBody)?.groupValues?.get(1)?.toFloatOrNull()
                if (gender.isNotBlank() || ageEstimate.isNotBlank() || topCountry.isNotBlank()) {
                    if (gender.isNotBlank()) meta["demographics_gender"] = "$gender (${genderProb?.times(100)?.toInt() ?: "?"}%)"
                    if (ageEstimate.isNotBlank()) meta["demographics_age_estimate"] = ageEstimate
                    if (topCountry.isNotBlank()) meta["demographics_nationality"] = "$topCountry (${countryProb?.times(100)?.toInt() ?: "?"}%)"
                    sources.add(DataSource("Name Demographics", null, Date(), 0.4))
                    emit(SearchProgressEvent.Found("Name Demographics",
                        buildString {
                            if (gender.isNotBlank()) append("${gender.replaceFirstChar { it.uppercase() }}${genderProb?.let { " (${(it * 100).toInt()}%)" } ?: ""}")
                            if (ageEstimate.isNotBlank()) { if (isNotEmpty()) append(" · "); append("Avg age: $ageEstimate") }
                            if (topCountry.isNotBlank()) { if (isNotEmpty()) append(" · "); append("Origin: $topCountry") }
                        }
                    ))
                } else {
                    emit(SearchProgressEvent.NotFound("Name Demographics"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("Name Demographics", e.message ?: ""))
            }
        }

        launch { thatsThenScrape(query, meta, sources, emit) }
        launch { usPhonebookScrape(query, meta, sources, emit) }
        launch { fastPeopleSearchScrape(query, meta, sources, emit) }
        launch { judyRecordsScrape(query, meta, sources, emit) }
        launch { familyTreeNowScrape(query, meta, sources, emit) }
        launch { generateAndRunDorks(query, meta, sources, emit) }
        launch { voterRecordsScrape(query, meta, sources, emit) }
        launch { ahmiaSearch(query, meta, sources, emit) }
        launch { zoAiSynthesis(query, meta, sources, emit) }
        launch { truePeopleSearchScrape(query, meta, sources, emit) }
        launch { openSanctionsScrape(query, meta, sources, emit) }
        launch { duckDuckGoPersonSearch(query, meta, sources, emit) }
        launch { zabaSearchScrape(query, meta, sources, emit) }
        launch { fourOneOneScrape(query, meta, sources, emit) }
        launch { gNewsFetch(query, meta, sources, emit) }

        val gKey = apiKeyManager.googleCseApiKey
        val gCx = apiKeyManager.googleCseId
        if (gKey.isNotBlank() && gCx.isNotBlank()) {
            launch { googleCsePersonSearch(query, gKey, gCx, meta, sources, emit) }
        }
        val bKey = apiKeyManager.bingSearchKey
        if (bKey.isNotBlank()) {
            launch { bingPersonSearch(query, bKey, meta, sources, emit) }
        }
    }

    private suspend fun voterRecordsScrape(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        emit(SearchProgressEvent.Checking("VoterRecords.com"))
        try {
            val parts = query.trim().split(" ")
            val firstName = parts.firstOrNull() ?: return
            val lastName = parts.drop(1).joinToString("-").ifBlank { return }
            val slug = "${firstName.lowercase()}-${lastName.lowercase()}"
            val req = Request.Builder()
                .url("https://voterrecords.com/voters/$slug/1")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.5")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: ""; resp.close()
            if (resp.code == 403 || html.isBlank()) {
                emit(SearchProgressEvent.NotFound("VoterRecords.com"))
                return
            }
            val names = Regex("<h5[^>]*class=\"[^\"]*card-title[^\"]*\"[^>]*>([^<]{5,60})</h5>", RegexOption.IGNORE_CASE)
                .findAll(html).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.distinct().take(5).toList()
            val addresses = Regex("(?:address|location)[^<]*<[^>]+>([A-Z][^<]{10,80})</", RegexOption.IGNORE_CASE)
                .findAll(html).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.distinct().take(5).toList()
            val parties = Regex("(?:party|affiliation)[^<]*<[^>]+>(Democratic|Republican|Independent|Green|Libertarian|NPA|No Party Affiliation)[^<]*<", RegexOption.IGNORE_CASE)
                .findAll(html).map { it.groupValues[1].trim() }.distinct().take(3).toList()
            val ageMatch = Regex("(?:age|born)[^<]*<[^>]+>(\\d{1,3})[^<]*<", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            if (names.isNotEmpty() || addresses.isNotEmpty()) {
                if (names.isNotEmpty()) meta["voter_names"] = names.joinToString(", ")
                if (addresses.isNotEmpty()) meta["voter_addresses"] = addresses.joinToString(" | ")
                if (parties.isNotEmpty()) meta["voter_party"] = parties.joinToString(", ")
                if (ageMatch != null) meta["voter_age"] = ageMatch
                meta["voter_link"] = "https://voterrecords.com/voters/$slug/1"
                sources.add(DataSource("VoterRecords.com", meta["voter_link"], Date(), 0.8))
                emit(SearchProgressEvent.Found("VoterRecords.com",
                    "${names.size} voter record${if (names.size != 1) "s" else ""}${if (addresses.isNotEmpty()) " · ${addresses.first().take(40)}" else ""}"))
            } else {
                emit(SearchProgressEvent.NotFound("VoterRecords.com"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("VoterRecords.com", e.message ?: ""))
        }
    }

    private suspend fun ahmiaSearch(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        emit(SearchProgressEvent.Checking("Ahmia Dark Web"))
        try {
            val encoded = URLEncoder.encode("\"$query\"", "UTF-8")
            val req = Request.Builder()
                .url("https://ahmia.fi/search/?q=$encoded")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: ""; resp.close()
            val titles = Regex("<h4>([^<]{5,120})</h4>").findAll(html)
                .map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.take(5).toList()
            val onionUrls = Regex("href=\"(/search/redirect\\?[^\"]+)\"").findAll(html)
                .map { "https://ahmia.fi${it.groupValues[1]}" }.take(5).toList()
            val countMatch = Regex("About\\s+(\\d[\\d,]+)\\s+result").find(html)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
            if (titles.isNotEmpty() || (countMatch != null && countMatch > 0)) {
                val count = countMatch ?: titles.size
                meta["ahmia_count"] = count.toString()
                meta["ahmia_titles"] = titles.joinToString("\n")
                if (onionUrls.isNotEmpty()) meta["ahmia_urls"] = onionUrls.joinToString("\n")
                meta["ahmia_link"] = "https://ahmia.fi/search/?q=$encoded"
                sources.add(DataSource("Ahmia (Dark Web Index)", meta["ahmia_link"], Date(), 0.6))
                emit(SearchProgressEvent.Found("Ahmia Dark Web", "$count dark web mention${if (count != 1) "s" else ""} indexed"))
            } else {
                emit(SearchProgressEvent.NotFound("Ahmia Dark Web"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("Ahmia Dark Web", e.message ?: ""))
        }
    }

    private suspend fun zoAiSynthesis(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        emit(SearchProgressEvent.Checking("Zo AI Background Check"))
        try {
            val prompt = "You are an OSINT analyst. Provide a concise factual background summary for the person named \"$query\" using only publicly available information. Include: known profession or public role, any notable activities, public controversies or news mentions, professional history, and any notable public associations. Be factual and brief (3-5 sentences). If this is a private individual with no public profile, say so."
            val reqBody = """{"prompt":"${prompt.replace("\"","\\\"").replace("\n"," ")}","model":"claude-3-haiku"}""".toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("https://api.zo.computer/zo/ask")
                .post(reqBody)
                .addHeader("Authorization", "Bearer zo_sk_pmDTqQAkltmtTXI0Uowg0ozph9iXe0reyHsBM53Ij7M")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                .build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""; resp.close()
            val answer = Regex("\"answer\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(body)?.groupValues?.get(1)
                ?.replace("\\n", "\n")?.replace("\\\"", "\"")?.trim()
                ?: Regex("\"response\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(body)?.groupValues?.get(1)
                    ?.replace("\\n", "\n")?.replace("\\\"", "\"")?.trim()
                ?: Regex("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(body)?.groupValues?.get(1)
                    ?.replace("\\n", "\n")?.replace("\\\"", "\"")?.trim()
            if (!answer.isNullOrBlank() && answer.length > 20) {
                meta["ai_summary"] = answer.take(1000)
                sources.add(DataSource("Zo AI Synthesis", null, Date(), 0.5))
                emit(SearchProgressEvent.Found("Zo AI Background Check", "AI background analysis complete"))
            } else {
                emit(SearchProgressEvent.NotFound("Zo AI Background Check"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("Zo AI Background Check", e.message ?: ""))
        }
    }

    private suspend fun comprehensiveSearch(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) = coroutineScope {
        val fields = query.split("|").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }.toMap()
        val name = fields["name"] ?: ""
        val phone = fields["phone"] ?: ""
        val email = fields["email"] ?: ""
        val ip = fields["ip"] ?: ""
        val location = fields["location"] ?: ""
        meta["comprehensive_query"] = query
        meta["comp_name"] = name
        meta["comp_phone"] = phone
        meta["comp_email"] = email
        meta["comp_ip"] = ip
        meta["comp_location"] = location

        if (name.isNotBlank()) {
            launch { personSearch(name, meta, sources, emit) }
        }
        if (email.isNotBlank()) {
            launch { emailSearch(email, meta, sources, emit) }
        }
        if (phone.isNotBlank()) {
            launch { phoneSearch(phone, meta, sources, emit) }
        }
        if (ip.isNotBlank()) {
            launch { ipDomainSearch(ip, meta, sources, emit) }
        }
    }

    private suspend fun truePeopleSearchScrape(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        emit(SearchProgressEvent.Checking("TruePeopleSearch"))
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://www.truepeoplesearch.com/results?name=$encoded&citystatezip=&rid=0")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .addHeader("Referer", "https://www.truepeoplesearch.com/")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: ""; resp.close()
            if (resp.code == 403 || resp.code == 429 || html.isBlank()) {
                emit(SearchProgressEvent.NotFound("TruePeopleSearch")); return
            }
            val names = Regex("<div[^>]*class=\"[^\"]*card-title[^\"]*\"[^>]*>([^<]{5,60})</div>", RegexOption.IGNORE_CASE)
                .findAll(html).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.distinct().take(5).toList()
            val ages = Regex("(?:Age|age)[:\\s]+(\\d{2,3})").findAll(html).map { it.groupValues[1] }.take(5).distinct().toList()
            val phones = Regex("\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}").findAll(html)
                .map { it.value.trim() }.filter { it.length >= 10 }.take(10).distinct().toList()
            val cities = Regex("<span[^>]*>([A-Z][a-zA-Z ]+,\\s*[A-Z]{2})</span>").findAll(html)
                .map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.take(10).distinct().toList()
            val relatives = Regex("class=\"[^\"]*related[^\"]*\"[^>]*>[^<]*<[^>]+>([A-Z][a-z]+ [A-Z][a-z]+)").findAll(html)
                .map { it.groupValues[1] }.take(10).distinct().toList()
            val hasData = names.isNotEmpty() || ages.isNotEmpty() || phones.isNotEmpty() || cities.isNotEmpty()
            if (hasData) {
                if (names.isNotEmpty()) meta["tps_names"] = names.joinToString(", ")
                if (ages.isNotEmpty()) meta["tps_age"] = ages.first()
                if (phones.isNotEmpty()) meta["tps_phones"] = phones.joinToString(", ")
                if (cities.isNotEmpty()) meta["tps_locations"] = cities.joinToString(" | ")
                if (relatives.isNotEmpty()) meta["tps_relatives"] = relatives.joinToString(", ")
                meta["tps_link"] = "https://www.truepeoplesearch.com/results?name=$encoded"
                sources.add(DataSource("TruePeopleSearch", meta["tps_link"], Date(), 0.75))
                emit(SearchProgressEvent.Found("TruePeopleSearch", buildString {
                    if (names.isNotEmpty()) append(names.first())
                    if (ages.isNotEmpty()) { if (isNotEmpty()) append(" · "); append("Age: ${ages.first()}") }
                    if (cities.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(cities.first()) }
                }))
            } else {
                emit(SearchProgressEvent.NotFound("TruePeopleSearch"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("TruePeopleSearch", e.message ?: ""))
        }
    }

    private suspend fun openSanctionsScrape(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        emit(SearchProgressEvent.Checking("OpenSanctions"))
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://api.opensanctions.org/search/default?q=$encoded&schema=Person&limit=5")
                .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                .addHeader("Accept", "application/json")
                .build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""; resp.close()
            val total = Regex("\"total\":\\s*\\{[^}]*\"value\":\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val names = Regex("\"caption\":\\s*\"([^\"]{3,80})\"").findAll(body).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.distinct().take(5).toList()
            val datasets = Regex("\"dataset\":\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.distinct().take(5).toList()
            val countries = Regex("\"country\":\\s*\"([^\"]{2,50})\"").findAll(body).map { it.groupValues[1] }.distinct().take(3).toList()
            if (total > 0 || names.isNotEmpty()) {
                meta["opensanctions_total"] = total.toString()
                if (names.isNotEmpty()) meta["opensanctions_names"] = names.joinToString(", ")
                if (datasets.isNotEmpty()) meta["opensanctions_datasets"] = datasets.joinToString(", ")
                if (countries.isNotEmpty()) meta["opensanctions_countries"] = countries.joinToString(", ")
                meta["opensanctions_link"] = "https://www.opensanctions.org/search/?q=$encoded"
                sources.add(DataSource("OpenSanctions", meta["opensanctions_link"], Date(), 0.9))
                emit(SearchProgressEvent.Found("OpenSanctions", "⚠ $total sanctions/PEP match${if (total != 1) "es" else ""}: ${datasets.take(2).joinToString(", ")}"))
            } else {
                emit(SearchProgressEvent.NotFound("OpenSanctions"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("OpenSanctions", e.message ?: ""))
        }
    }

    private suspend fun duckDuckGoPersonSearch(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        emit(SearchProgressEvent.Checking("DuckDuckGo Instant"))
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""; resp.close()
            val abstract_ = Regex("\"Abstract\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(body)?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\\"", "\"")?.trim()
            val abstractSource = Regex("\"AbstractSource\":\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)?.trim()
            val abstractUrl = Regex("\"AbstractURL\":\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)?.trim()
            val answer = Regex("\"Answer\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(body)?.groupValues?.get(1)?.trim()
            val infobox = Regex("\"content\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(body)
                .map { it.groupValues[1].replace("\\\"", "\"").trim() }.filter { it.isNotBlank() && it.length > 5 }.take(6).toList()
            if (!abstract_.isNullOrBlank() && abstract_.length > 20) {
                meta["ddg_abstract"] = abstract_.take(500)
                if (!abstractSource.isNullOrBlank()) meta["ddg_source"] = abstractSource
                if (!abstractUrl.isNullOrBlank()) meta["ddg_url"] = abstractUrl
                if (infobox.isNotEmpty()) meta["ddg_infobox"] = infobox.joinToString("\n")
                sources.add(DataSource("DuckDuckGo Instant", abstractUrl, Date(), 0.7))
                emit(SearchProgressEvent.Found("DuckDuckGo Instant", "${abstract_.take(80)}…"))
            } else if (!answer.isNullOrBlank()) {
                meta["ddg_answer"] = answer.take(200)
                sources.add(DataSource("DuckDuckGo Instant", null, Date(), 0.6))
                emit(SearchProgressEvent.Found("DuckDuckGo Instant", answer.take(80)))
            } else {
                emit(SearchProgressEvent.NotFound("DuckDuckGo Instant"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("DuckDuckGo Instant", e.message ?: ""))
        }
    }

    private suspend fun thatsThenScrape(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        emit(SearchProgressEvent.Checking("ThatsThem"))
        try {
            val dashName = query.lowercase().replace(" ", "-")
            val req = Request.Builder()
                .url("https://thatsthem.com/name/$dashName")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.5")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: ""
            resp.close()
            if (html.isBlank() || resp.code == 403) {
                emit(SearchProgressEvent.NotFound("ThatsThem"))
                return
            }
            val ages = Regex("Age\\s+(\\d{2,3})").findAll(html).map { it.groupValues[1] }.take(5).distinct().toList()
            val cities = Regex("<span[^>]*class=\"[^\"]*city[^\"]*\"[^>]*>([^<]+)</span>").findAll(html)
                .map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.take(10).distinct().toList()
            val states = Regex("<span[^>]*class=\"[^\"]*state[^\"]*\"[^>]*>([^<]+)</span>").findAll(html)
                .map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.take(10).distinct().toList()
            val phones = Regex("\\(\\d{3}\\)\\s*\\d{3}-\\d{4}").findAll(html).map { it.value.trim() }.take(10).distinct().toList()
            val relatives = Regex("(?:Relative|Associated|Related)[^<]*<[^>]+>([A-Z][a-z]+ [A-Z][a-z]+)").findAll(html)
                .map { it.groupValues[1] }.take(15).distinct().toList()
            val imgUrls = Regex("src=\"(https://[^\"]+(?:photo|profile|avatar|thumb)[^\"]+)\"").findAll(html)
                .map { it.groupValues[1] }.take(3).distinct().toList()

            val hasData = ages.isNotEmpty() || cities.isNotEmpty() || phones.isNotEmpty() || relatives.isNotEmpty()
            if (hasData) {
                if (ages.isNotEmpty()) meta["tt_ages"] = ages.joinToString(", ")
                if (cities.isNotEmpty() && states.isNotEmpty()) {
                    meta["tt_locations"] = cities.zip(states).take(10).joinToString(" | ") { (c, s) -> "$c, $s" }
                } else if (cities.isNotEmpty()) {
                    meta["tt_locations"] = cities.take(10).joinToString(" | ")
                }
                if (phones.isNotEmpty()) meta["tt_phones"] = phones.joinToString(", ")
                if (relatives.isNotEmpty()) meta["tt_relatives"] = relatives.joinToString(", ")
                if (imgUrls.isNotEmpty()) meta["tt_image_url"] = imgUrls.first()
                meta["thatsthem_link"] = "https://thatsthem.com/name/$dashName"
                sources.add(DataSource("ThatsThem", meta["thatsthem_link"], Date(), 0.75))
                val summary = buildString {
                    if (ages.isNotEmpty()) append("Age: ${ages.first()}")
                    if (cities.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(cities.first()) }
                    if (phones.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(phones.first()) }
                    if (relatives.isNotEmpty()) { if (isNotEmpty()) append(" · "); append("${relatives.size} relative${if (relatives.size != 1) "s" else ""} found") }
                }
                emit(SearchProgressEvent.Found("ThatsThem", summary.ifBlank { "Profile found" }))
            } else {
                emit(SearchProgressEvent.NotFound("ThatsThem"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("ThatsThem", e.message ?: ""))
        }
    }

    private suspend fun usPhonebookScrape(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        emit(SearchProgressEvent.Checking("USPhoneBook"))
        try {
            val encodedName = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://www.usphonebook.com/$encodedName")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0")
                .addHeader("Accept", "text/html")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: ""
            resp.close()
            if (html.isBlank() || resp.code == 403) {
                emit(SearchProgressEvent.NotFound("USPhoneBook"))
                return
            }
            val addresses = Regex("(?:address|street)[^<]{0,50}<[^>]+>([^<]+[A-Z]{2}\\s+\\d{5}[^<]*)").findAll(html)
                .map { it.groupValues[1].trim() }.filter { it.length > 5 }.take(10).distinct().toList()
            val phones = Regex("\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}").findAll(html)
                .map { it.value.trim() }.filter { it.length >= 10 }.take(10).distinct().toList()
            val ages = Regex("Age[:\\s]+(\\d{2,3})").findAll(html).map { it.groupValues[1] }.take(5).distinct().toList()

            val hasData = addresses.isNotEmpty() || phones.isNotEmpty()
            if (hasData) {
                if (addresses.isNotEmpty()) meta["uspb_addresses"] = addresses.joinToString(" | ")
                if (phones.isNotEmpty()) meta["uspb_phones"] = phones.joinToString(", ")
                if (ages.isNotEmpty()) meta["uspb_age"] = ages.first()
                meta["usphonebook_link"] = "https://www.usphonebook.com/$encodedName"
                sources.add(DataSource("USPhoneBook", meta["usphonebook_link"], Date(), 0.7))
                emit(SearchProgressEvent.Found("USPhoneBook",
                    buildString {
                        if (addresses.isNotEmpty()) append(addresses.first().take(60))
                        if (phones.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(phones.first()) }
                    }
                ))
            } else {
                emit(SearchProgressEvent.NotFound("USPhoneBook"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("USPhoneBook", e.message ?: ""))
        }
    }

    private suspend fun generateAndRunDorks(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        emit(SearchProgressEvent.Checking("ShadowDork Engine"))
        val enc = URLEncoder.encode("\"$query\"", "UTF-8")
        val nameEnc = URLEncoder.encode(query, "UTF-8")
        val dorks = linkedMapOf(
            "identity_confirm" to "\"$query\" (\"date of birth\" OR \"born in\" OR age) -obituary",
            "address_records" to "\"$query\" (\"lives at\" OR \"address\" OR \"moved to\" OR \"resides\") -jobs -hire",
            "relatives_map" to "\"$query\" (\"related to\" OR \"son of\" OR \"daughter of\" OR \"married to\" OR relatives)",
            "criminal_records" to "\"$query\" (arrest OR convicted OR \"sentenced to\" OR mugshot OR \"guilty\") -fiction",
            "property_records" to "\"$query\" (\"property owner\" OR deed OR mortgage OR \"square feet\") site:zillow.com OR site:redfin.com OR site:realtor.com",
            "financial_exposure" to "\"$query\" (bankruptcy OR lien OR garnishment OR foreclosure OR judgment)",
            "leaked_data" to "\"$query\" site:pastebin.com OR site:ghostbin.co OR site:hastebin.com OR site:rentry.co",
            "social_discovery" to "\"$query\" (instagram OR twitter OR facebook OR tiktok) -buy -sell -news",
            "email_patterns" to "\"${query.split(" ").joinToString(".")}\" OR \"${query.split(" ").take(1).firstOrNull() ?: ""}${query.split(" ").drop(1).firstOrNull() ?: ""}\" email OR contact",
            "vehicle_trace" to "\"$query\" (vehicle OR \"license plate\" OR registration OR VIN OR \"car owned\") -dealer",
            "business_ties" to "\"$query\" (CEO OR founder OR director OR owner OR LLC OR \"Inc.\") site:bloomberg.com OR site:linkedin.com",
            "court_deep" to "\"$query\" site:courtlistener.com OR site:judyrecords.com OR site:unicourt.com",
            "voter_records" to "\"$query\" (\"registered voter\" OR \"voter registration\" OR precinct OR \"party affiliation\")",
            "obituary_cross" to "\"$query\" obituary (survived by OR relatives OR children OR spouse)",
            "dark_mentions" to "\"$query\" (\"ssn\" OR \"social security\" OR \"date of birth\" OR dob) -form -request",
            "files_dump" to "\"$query\" (filetype:pdf OR filetype:doc OR filetype:docx OR filetype:xls OR filetype:xlsx OR filetype:csv OR filetype:txt OR filetype:ppt OR filetype:pptx) (name OR address OR phone OR email OR \"date of birth\" OR resume OR cv OR application OR record)"
        )

        val gBase = "https://www.google.com/search?q="
        val bBase = "https://www.bing.com/search?q="

        val dorkLinks = dorks.entries.mapIndexed { i, (key, dork) ->
            val dorkEnc = URLEncoder.encode(dork, "UTF-8")
            "$key::G:${gBase}$dorkEnc::B:${bBase}$dorkEnc"
        }
        meta["shadowdork_count"] = dorks.size.toString()
        meta["shadowdork_links"] = dorkLinks.joinToString("\n")

        val identityDork = URLEncoder.encode("\"$query\" (\"date of birth\" OR age OR city OR state) -obituary", "UTF-8")
        meta["dork_identity"] = "${gBase}$identityDork"
        val relativesDork = URLEncoder.encode("\"$query\" (relatives OR \"related to\" OR \"married to\" OR children OR spouse)", "UTF-8")
        meta["dork_relatives"] = "${gBase}$relativesDork"
        val addressDork = URLEncoder.encode("\"$query\" (address OR \"lives in\" OR \"moved to\") site:whitepages.com OR site:411.com OR site:addresses.com", "UTF-8")
        meta["dork_address"] = "${gBase}$addressDork"
        val criminalDork = URLEncoder.encode("\"$query\" (arrest OR mugshot OR convicted OR sentenced OR guilty) -news -jobs", "UTF-8")
        meta["dork_criminal"] = "${gBase}$criminalDork"
        val leakDork = URLEncoder.encode("\"$query\" site:pastebin.com OR site:ghostbin.co OR site:hastebin.com", "UTF-8")
        meta["dork_leaks"] = "${gBase}$leakDork"
        val propertyDork = URLEncoder.encode("\"$query\" property owner OR deed OR title -for sale", "UTF-8")
        meta["dork_property"] = "${gBase}$propertyDork"
        val vehicleDork = URLEncoder.encode("\"$query\" vehicle OR registration OR \"license plate\" -buy -sell", "UTF-8")
        meta["dork_vehicle"] = "${gBase}$vehicleDork"
        val socialDork = URLEncoder.encode("\"$query\" facebook OR instagram OR twitter OR tiktok OR snapchat -news -buy", "UTF-8")
        meta["dork_social"] = "${gBase}$socialDork"
        val filesDork = URLEncoder.encode("\"$query\" (filetype:pdf OR filetype:doc OR filetype:docx OR filetype:xls OR filetype:xlsx OR filetype:csv OR filetype:txt OR filetype:ppt OR filetype:pptx) (name OR address OR phone OR email OR \"date of birth\" OR resume OR cv OR application OR record)", "UTF-8")
        meta["dork_files"] = "${gBase}$filesDork"

        sources.add(DataSource("ShadowDork Engine", null, Date(), 0.5))
        emit(SearchProgressEvent.Found("ShadowDork Engine", "${dorks.size} specialized search dorks generated"))
    }

    private suspend fun googleCsePersonSearch(
        query: String,
        apiKey: String,
        cseId: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        val topDorks = listOf(
            "\"$query\" (address OR city OR state OR age)",
            "\"$query\" (relatives OR \"married to\" OR children OR spouse)",
            "\"$query\" (arrest OR criminal OR court OR lawsuit)"
        )
        val allSnippets = mutableListOf<String>()
        val allLinks = mutableListOf<String>()
        for (dork in topDorks) {
            emit(SearchProgressEvent.Checking("Google CSE: ${dork.take(40)}…"))
            try {
                val resp = RetrofitClient.googleCseService.search(apiKey, cseId, dork)
                if (resp.isSuccessful && resp.body()?.items != null) {
                    apiKeyManager.recordUsage("google_cse")
                    val items = resp.body()!!.items!!
                    items.take(3).forEach { item ->
                        item.snippet?.let { allSnippets.add(it.take(200)) }
                        item.link?.let { allLinks.add("${item.displayLink ?: item.link}: $it") }
                    }
                }
            } catch (_: Exception) {}
            delay(300)
        }
        if (allSnippets.isNotEmpty()) {
            meta["cse_snippets"] = allSnippets.take(6).joinToString("\n---\n")
            meta["cse_links"] = allLinks.take(6).joinToString("\n")
            meta["cse_result_count"] = allSnippets.size.toString()
            sources.add(DataSource("Google CSE", null, Date(), 0.85))
            emit(SearchProgressEvent.Found("Google CSE", "${allSnippets.size} results from targeted dork searches"))
        } else {
            emit(SearchProgressEvent.NotFound("Google CSE"))
        }
    }

    private suspend fun bingPersonSearch(
        query: String,
        apiKey: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        emit(SearchProgressEvent.Checking("Bing Web Search"))
        try {
            val dork = "\"$query\" (address OR relatives OR age OR arrest OR property)"
            val resp = RetrofitClient.bingSearchService.search(apiKey, dork)
            if (resp.isSuccessful && resp.body()?.webPages?.value != null) {
                apiKeyManager.recordUsage("bing_search")
                val results = resp.body()!!.webPages!!.value!!
                val snippets = results.take(5).mapNotNull { it.snippet }.joinToString("\n---\n")
                val links = results.take(5).mapNotNull { r -> r.url?.let { "${r.displayUrl ?: r.name}: $it" } }.joinToString("\n")
                if (snippets.isNotBlank()) {
                    meta["bing_snippets"] = snippets
                    meta["bing_links"] = links
                    meta["bing_total"] = resp.body()!!.webPages!!.totalEstimatedMatches?.toString() ?: "0"
                    sources.add(DataSource("Bing Search", null, Date(), 0.8))
                    emit(SearchProgressEvent.Found("Bing Search", "${results.size} results found"))
                } else {
                    emit(SearchProgressEvent.NotFound("Bing Search"))
                }
            } else {
                emit(SearchProgressEvent.NotFound("Bing Search"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("Bing Search", e.message ?: ""))
        }
    }

    private suspend fun companySearch(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) = coroutineScope {
        launch {
            emit(SearchProgressEvent.Checking("OpenCorporates"))
            try {
                val resp = RetrofitClient.openCorporatesService.searchCompanies(query)
                if (resp.isSuccessful) {
                    val companies = resp.body()?.results?.companies ?: emptyList()
                    meta["company_count"] = companies.size.toString()
                    meta["companies"] = companies.take(5).joinToString("\n") { item ->
                        "${item.company?.name} | ${item.company?.jurisdictionCode} | ${item.company?.currentStatus}"
                    }
                    if (companies.isNotEmpty()) {
                        sources.add(DataSource("OpenCorporates", null, Date(), 0.85))
                        emit(SearchProgressEvent.Found("OpenCorporates",
                            "${companies.size} compan${if (companies.size != 1) "ies" else "y"} found"))
                    } else {
                        emit(SearchProgressEvent.NotFound("OpenCorporates"))
                    }
                } else {
                    emit(SearchProgressEvent.Failed("OpenCorporates", "HTTP ${resp.code()}"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("OpenCorporates", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("OpenCorporates Officers"))
            try {
                val resp = RetrofitClient.openCorporatesService.searchOfficers(query)
                if (resp.isSuccessful) {
                    val officers = resp.body()?.results?.officers ?: emptyList()
                    meta["officer_count"] = officers.size.toString()
                    meta["officers"] = officers.take(5).joinToString("\n") { item ->
                        "${item.officer?.name} | ${item.officer?.position} | ${item.officer?.company?.name}"
                    }
                    if (officers.isNotEmpty()) {
                        sources.add(DataSource("OC Officers", null, Date(), 0.8))
                        emit(SearchProgressEvent.Found("OpenCorporates Officers",
                            "${officers.size} officer${if (officers.size != 1) "s" else ""} found"))
                    } else {
                        emit(SearchProgressEvent.NotFound("OpenCorporates Officers"))
                    }
                } else {
                    emit(SearchProgressEvent.Failed("OpenCorporates Officers", "HTTP ${resp.code()}"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("OpenCorporates Officers", e.message ?: ""))
            }
        }

        val hunterKey = apiKeyManager.hunterKey
        if (hunterKey.isNotBlank()) {
            launch {
                emit(SearchProgressEvent.Checking("Hunter.io"))
                try {
                    val domain = query.replace(" ", "").lowercase() + ".com"
                    val resp = RetrofitClient.hunterService.searchDomain(domain = domain, apiKey = hunterKey)
                    if (resp.isSuccessful) {
                        val emails = resp.body()?.data?.emails ?: emptyList()
                        meta["hunter_emails_count"] = emails.size.toString()
                        meta["hunter_emails"] = emails.take(5).mapNotNull { it.value }.joinToString(", ")
                        if (emails.isNotEmpty()) {
                            sources.add(DataSource("Hunter.io", null, Date(), 0.8))
                            emit(SearchProgressEvent.Found("Hunter.io", "${emails.size} email${if (emails.size != 1) "s" else ""} found"))
                        } else {
                            emit(SearchProgressEvent.NotFound("Hunter.io"))
                        }
                    } else {
                        emit(SearchProgressEvent.Failed("Hunter.io", "HTTP ${resp.code()}"))
                    }
                } catch (e: Exception) {
                    emit(SearchProgressEvent.Failed("Hunter.io", e.message ?: ""))
                }
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("WikiData Companies"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://www.wikidata.org/w/api.php?action=wbsearchentities&search=$encoded&language=en&limit=3&format=json&type=item")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .build()
                val resp = fastHttpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    val labels = Regex("\"label\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(3).toList()
                    val descs = Regex("\"description\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(3).toList()
                    val ids = Regex("\"id\":\"(Q\\d+)\"").findAll(body).map { it.groupValues[1] }.take(1).toList()
                    if (labels.isNotEmpty()) {
                        meta["wikidata_company_labels"] = labels.joinToString(" | ")
                        meta["wikidata_company_descriptions"] = descs.zip(labels).take(3).joinToString("\n") { (d, l) -> "$l: $d" }
                        if (ids.isNotEmpty()) meta["wikidata_company_link"] = "https://www.wikidata.org/wiki/${ids.first()}"
                        sources.add(DataSource("WikiData", meta["wikidata_company_link"], Date(), 0.65))
                        emit(SearchProgressEvent.Found("WikiData Companies", labels.firstOrNull() ?: "${labels.size} entities"))
                    } else {
                        emit(SearchProgressEvent.NotFound("WikiData Companies"))
                    }
                } else {
                    resp.close()
                    emit(SearchProgressEvent.Failed("WikiData Companies", "HTTP ${resp.code}"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("WikiData Companies", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("SEC EDGAR Companies"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://efts.sec.gov/LATEST/search-index?q=%22$encoded%22&dateRange=custom&startdt=2000-01-01")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .build()
                val resp = fastHttpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    val hits = Regex("\"total\":\\s*\\{[^}]*\"value\":(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val filingTypes = Regex("\"form_type\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.distinct().take(5).toList()
                    if (hits > 0) {
                        meta["sec_filings_count"] = hits.toString()
                        if (filingTypes.isNotEmpty()) meta["sec_filing_types"] = filingTypes.joinToString(", ")
                        sources.add(DataSource("SEC EDGAR", "https://efts.sec.gov/LATEST/search-index?q=%22$encoded%22", Date(), 0.8))
                        emit(SearchProgressEvent.Found("SEC EDGAR Companies", "$hits SEC filing${if (hits != 1) "s" else ""}"))
                    } else {
                        emit(SearchProgressEvent.NotFound("SEC EDGAR Companies"))
                    }
                } else {
                    resp.close()
                    emit(SearchProgressEvent.NotFound("SEC EDGAR Companies"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("SEC EDGAR Companies", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("Florida SunBiz"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://search.sunbiz.org/Inquiry/CorporationSearch/GetListCorporations?SearchType=N&SearchTerm=$encoded&ListStartIndex=0")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0")
                    .addHeader("Accept", "application/json, text/javascript, */*")
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .addHeader("Referer", "https://search.sunbiz.org/")
                    .build()
                val resp = fastHttpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""; resp.close()
                val entityNames = Regex("\"strCorporationName\"\\s*:\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.distinct().take(5).toList()
                val docNums = Regex("\"strDocumentNumber\"\\s*:\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(5).toList()
                val statuses = Regex("\"strStatus\"\\s*:\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(5).toList()
                val dates = Regex("\"strFileDate\"\\s*:\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(5).toList()
                if (entityNames.isNotEmpty()) {
                    meta["sunbiz_count"] = entityNames.size.toString()
                    meta["sunbiz_entities"] = entityNames.indices.take(5).joinToString("\n") { i ->
                        val n = entityNames.getOrNull(i) ?: ""; val s = statuses.getOrNull(i) ?: ""; val d = dates.getOrNull(i) ?: ""
                        listOf(n, s, d).filter { it.isNotBlank() }.joinToString(" | ")
                    }
                    if (docNums.isNotEmpty()) meta["sunbiz_doc_numbers"] = docNums.joinToString(", ")
                    meta["sunbiz_link"] = "https://search.sunbiz.org/Inquiry/CorporationSearch/ByName?SearchTerm=$encoded"
                    sources.add(DataSource("FL SunBiz", meta["sunbiz_link"], Date(), 0.85))
                    emit(SearchProgressEvent.Found("Florida SunBiz", "${entityNames.size} FL entit${if (entityNames.size != 1) "ies" else "y"}: ${entityNames.firstOrNull() ?: ""}"))
                } else {
                    emit(SearchProgressEvent.NotFound("Florida SunBiz"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("Florida SunBiz", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("SAM.gov Entity Registry"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://api.sam.gov/entity-information/v3/entities?api_key=DEMO_KEY&legalBusinessName=$encoded&includeSections=entityRegistration&registrationStatus=A&resultCount=5")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .addHeader("Accept", "application/json")
                    .build()
                val resp = fastHttpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""; resp.close()
                val totalRecords = Regex("\"totalRecords\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val entityNames = Regex("\"legalBusinessName\"\\s*:\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.distinct().take(5).toList()
                val ueiCodes = Regex("\"ueiSAM\"\\s*:\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(5).toList()
                val states = Regex("\"stateOrProvinceOfIncorporation\"\\s*:\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(5).toList()
                val cageNums = Regex("\"cageCode\"\\s*:\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(5).toList()
                if (totalRecords > 0 && entityNames.isNotEmpty()) {
                    meta["sam_entity_count"] = totalRecords.toString()
                    meta["sam_entities"] = entityNames.zip(states.ifEmpty { List(entityNames.size) { "" } }).take(5)
                        .joinToString("\n") { (n, s) -> if (s.isNotBlank()) "$n ($s)" else n }
                    if (ueiCodes.isNotEmpty()) meta["sam_uei_codes"] = ueiCodes.joinToString(", ")
                    if (cageNums.isNotEmpty()) meta["sam_cage_codes"] = cageNums.joinToString(", ")
                    meta["sam_link"] = "https://sam.gov/search/?keywords=$encoded&index=ei&sort=relevance"
                    sources.add(DataSource("SAM.gov", meta["sam_link"], Date(), 0.8))
                    emit(SearchProgressEvent.Found("SAM.gov Entity Registry", "$totalRecords federal entit${if (totalRecords != 1) "ies" else "y"}: ${entityNames.firstOrNull() ?: ""}"))
                } else {
                    emit(SearchProgressEvent.NotFound("SAM.gov Entity Registry"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("SAM.gov Entity Registry", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("GLEIF Entity Search"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://api.gleif.org/api/v1/fuzzycompletions?field=entity.legalName&q=$encoded&page%5Bsize%5D=5")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .addHeader("Accept", "application/vnd.api+json")
                    .build()
                val resp = fastHttpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""; resp.close()
                val names = Regex("\"value\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.take(5).toList()
                if (names.isNotEmpty()) {
                    meta["gleif_company_entities"] = names.joinToString("\n")
                    meta["gleif_company_link"] = "https://search.gleif.org/#/search?q=$encoded"
                    sources.add(DataSource("GLEIF", meta["gleif_company_link"], Date(), 0.75))
                    emit(SearchProgressEvent.Found("GLEIF Entity Search", "${names.size} legal entit${if (names.size != 1) "ies" else "y"}: ${names.firstOrNull() ?: ""}"))
                } else {
                    emit(SearchProgressEvent.NotFound("GLEIF Entity Search"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("GLEIF Entity Search", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("Corporations Wiki"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://www.corporationswiki.com/l/search?q=$encoded")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("Accept-Language", "en-US,en;q=0.5")
                    .addHeader("Referer", "https://www.corporationswiki.com/")
                    .build()
                val resp = fastHttpClient.newCall(req).execute()
                val html = resp.body?.string() ?: ""; resp.close()
                val companyNames = Regex("<a[^>]+href=\"/p/[^\"]+\"[^>]*>([^<]{3,80})</a>").findAll(html)
                    .map { it.groupValues[1].trim() }
                    .filter { it.isNotBlank() && !it.contains("\\s{3,}".toRegex()) }
                    .distinct().take(8).toList()
                val states = Regex("(?:incorporated|formed|registered)[^<]*<[^>]*>([A-Z]{2})<", RegexOption.IGNORE_CASE).findAll(html)
                    .map { it.groupValues[1] }.take(8).toList()
                val officers = Regex("class=\"[^\"]*officer[^\"]*\"[^>]*>[^<]*<[^>]+>([A-Z][a-z]+ [A-Z][a-z]+)").findAll(html)
                    .map { it.groupValues[1] }.distinct().take(8).toList()
                if (companyNames.isNotEmpty()) {
                    meta["corpwiki_companies"] = companyNames.joinToString("\n")
                    meta["corpwiki_company_link"] = "https://www.corporationswiki.com/l/search?q=$encoded"
                    if (states.isNotEmpty()) meta["corpwiki_states"] = states.distinct().take(5).joinToString(", ")
                    if (officers.isNotEmpty()) meta["corpwiki_officers"] = officers.joinToString(", ")
                    sources.add(DataSource("Corporations Wiki", meta["corpwiki_company_link"], Date(), 0.75))
                    emit(SearchProgressEvent.Found("Corporations Wiki", "${companyNames.size} corporate record${if (companyNames.size != 1) "s" else ""}: ${companyNames.firstOrNull() ?: ""}"))
                } else {
                    emit(SearchProgressEvent.NotFound("Corporations Wiki"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("Corporations Wiki", e.message ?: ""))
            }
        }

        launch {
            emit(SearchProgressEvent.Checking("California SOS"))
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val req = Request.Builder()
                    .url("https://businesssearch.sos.ca.gov/CBS/SearchResults?filing_type=ALL&status=ACTIVE&SearchType=B&SearchValue=$encoded")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("Referer", "https://businesssearch.sos.ca.gov/")
                    .build()
                val resp = fastHttpClient.newCall(req).execute()
                val html = resp.body?.string() ?: ""; resp.close()
                val entityNames = Regex("<td[^>]*>\\s*<a[^>]+href=\"[^\"]*BusinessDetail[^\"]+\"[^>]*>([^<]+)</a>\\s*</td>").findAll(html)
                    .map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.distinct().take(10).toList()
                val statuses = Regex("(?:ACTIVE|DISSOLVED|SUSPENDED|FORFEITED|CANCELLED)").findAll(html)
                    .map { it.value }.take(10).toList()
                val entityNumbers = Regex("C\\d{7}").findAll(html).map { it.value }.distinct().take(10).toList()
                val entityTypes = Regex("(?:CORP|LLC|LP|LLP|GP|PA)(?:\\s+[A-Z]{1,4})?").findAll(html)
                    .map { it.value.trim() }.filter { it.length >= 2 }.distinct().take(5).toList()
                if (entityNames.isNotEmpty()) {
                    meta["ca_sos_count"] = entityNames.size.toString()
                    meta["ca_sos_entities"] = entityNames.indices.take(10).joinToString("\n") { i ->
                        val n = entityNames.getOrNull(i) ?: ""
                        val s = statuses.getOrNull(i) ?: ""
                        val num = entityNumbers.getOrNull(i) ?: ""
                        listOf(n, s, num).filter { it.isNotBlank() }.joinToString(" | ")
                    }
                    if (entityTypes.isNotEmpty()) meta["ca_sos_types"] = entityTypes.joinToString(", ")
                    meta["ca_sos_link"] = "https://businesssearch.sos.ca.gov/CBS/SearchResults?filing_type=ALL&status=ACTIVE&SearchType=B&SearchValue=$encoded"
                    sources.add(DataSource("CA SOS", meta["ca_sos_link"], Date(), 0.9))
                    emit(SearchProgressEvent.Found("California SOS", "${entityNames.size} CA entit${if (entityNames.size != 1) "ies" else "y"}: ${entityNames.firstOrNull() ?: ""}"))
                } else {
                    emit(SearchProgressEvent.NotFound("California SOS"))
                }
            } catch (e: Exception) {
                emit(SearchProgressEvent.Failed("California SOS", e.message ?: ""))
            }
        }

        meta["company_query"] = query
    }

    private suspend fun imageSearch(
        imagePath: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        meta["image_path"] = imagePath
        val faceLinks = listOf(
            "FaceCheck.id" to "https://facecheck.id/",
            "PimEyes" to "https://pimeyes.com/en",
            "Search4Faces" to "https://search4faces.com/",
            "Lenso.ai" to "https://lenso.ai/en"
        )
        val reverseLinks = listOf(
            "Google Lens" to "https://lens.google.com/",
            "Yandex Images" to "https://yandex.com/images/",
            "TinEye" to "https://tineye.com/search",
            "Bing Visual Search" to "https://www.bing.com/visualsearch",
            "KarmaDecay" to "https://karmadecay.com/"
        )
        meta["image_has_path"] = if (imagePath.startsWith("content://") || imagePath.startsWith("file://")) "true" else "false"
        faceLinks.forEach { (name, url) ->
            emit(SearchProgressEvent.Checking(name))
            delay(80)
            meta["face_${name.lowercase().replace(" ", "_").replace(".", "_").replace("/","")}_link"] = url
            sources.add(DataSource(name, url, Date(), 0.6))
            emit(SearchProgressEvent.Found(name, "Upload photo → instant face match across the web"))
        }
        reverseLinks.forEach { (name, url) ->
            emit(SearchProgressEvent.Checking(name))
            delay(80)
            meta["rev_${name.lowercase().replace(" ", "_").replace(".", "_").replace("/","")}_link"] = url
            sources.add(DataSource(name, url, Date(), 0.5))
            emit(SearchProgressEvent.Found(name, "Open → upload image for reverse search"))
        }
    }

    suspend fun getRecentReports(limit: Int = 50): List<OsintReportEntity> =
        db.reportDao().getRecentReports(limit)

    suspend fun getReportById(id: String): OsintReportEntity? =
        db.reportDao().getReportById(id)

    suspend fun getPersonById(id: String): PersonEntity? =
        db.personDao().getPersonById(id)

    private suspend fun saveReport(
        query: String,
        personId: String?,
        sources: List<DataSource>,
        metadata: Map<String, String> = emptyMap()
    ): String {
        val reportId = UUID.randomUUID().toString()
        val sourcesJson = moshi.adapter<List<DataSource>>(
            Types.newParameterizedType(List::class.java, DataSource::class.java)
        ).toJson(sources)
        val metaJson = moshi.adapter<Map<String, String>>(
            Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
        ).toJson(metadata)
        val entity = OsintReportEntity(
            id = reportId,
            searchQuery = query,
            generatedAt = Date(),
            personId = personId,
            companiesJson = metaJson,
            propertiesJson = "[]",
            vehicleRecordsJson = "[]",
            financialSummaryJson = null,
            confidenceScore = if (sources.size > 2) 0.8 else 0.4,
            sourcesJson = sourcesJson
        )
        db.reportDao().insertReport(entity)
        return reportId
    }

    private fun mapPiplPersonToEntity(pipl: PiplPerson): PersonEntity {
        val id = pipl.id ?: UUID.randomUUID().toString()
        val firstName = pipl.names?.firstOrNull()?.first ?: ""
        val lastName = pipl.names?.firstOrNull()?.last ?: ""
        val fullName = pipl.names?.firstOrNull()?.display
            ?: listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
        val addresses = pipl.addresses?.map { addr ->
            Address(
                street = listOfNotNull(addr.house, addr.street).joinToString(" "),
                city = addr.city ?: "", state = addr.state ?: "",
                postalCode = addr.zipCode ?: "", country = addr.country ?: "", type = "unknown"
            )
        } ?: emptyList()
        val employment = pipl.jobs?.map { job ->
            Employment(companyName = job.organization ?: "", jobTitle = job.title ?: "",
                startDate = job.dateRange?.start, endDate = job.dateRange?.end,
                isCurrent = job.dateRange?.end == null)
        } ?: emptyList()
        val socialProfiles = pipl.urls?.mapNotNull { url ->
            url.url?.let { urlStr ->
                SocialProfile(platform = url.category ?: url.domain ?: "Web",
                    username = url.domain ?: "", url = urlStr, followersCount = null)
            }
        } ?: emptyList()
        val addrType = Types.newParameterizedType(List::class.java, Address::class.java)
        val empType = Types.newParameterizedType(List::class.java, Employment::class.java)
        val socialType = Types.newParameterizedType(List::class.java, SocialProfile::class.java)
        val strType = Types.newParameterizedType(List::class.java, String::class.java)
        return PersonEntity(
            id = id, firstName = firstName, lastName = lastName, fullName = fullName,
            emailAddress = pipl.emails?.firstOrNull()?.address,
            phoneNumber = pipl.phones?.firstOrNull()?.display,
            dateOfBirth = pipl.dob,
            addressesJson = moshi.adapter<List<Address>>(addrType).toJson(addresses),
            employmentHistoryJson = moshi.adapter<List<Employment>>(empType).toJson(employment),
            socialProfilesJson = moshi.adapter<List<SocialProfile>>(socialType).toJson(socialProfiles),
            aliasesJson = moshi.adapter<List<String>>(strType).toJson(pipl.usernames?.mapNotNull { it.content } ?: emptyList()),
            nationalitiesJson = moshi.adapter<List<String>>(strType).toJson(pipl.originCountries?.mapNotNull { it.content } ?: emptyList()),
            gender = pipl.gender,
            profileImageUrl = pipl.images?.firstOrNull()?.url
        )
    }

    private suspend fun fastPeopleSearchScrape(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        emit(SearchProgressEvent.Checking("FastPeopleSearch"))
        try {
            val dashName = query.lowercase().replace(" ", "-")
            val req = Request.Builder()
                .url("https://www.fastpeoplesearch.com/name/$dashName")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.5")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: ""
            resp.close()
            if (html.isBlank() || resp.code == 403) { emit(SearchProgressEvent.NotFound("FastPeopleSearch")); return }
            val ages = Regex("Age\\s+(\\d{2,3})").findAll(html).map { it.groupValues[1] }.take(5).distinct().toList()
            val cities = Regex("<span[^>]*itemprop=\"addressLocality\"[^>]*>([^<]+)</span>").findAll(html)
                .map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.take(10).distinct().toList()
            val states = Regex("<span[^>]*itemprop=\"addressRegion\"[^>]*>([^<]+)</span>").findAll(html)
                .map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.take(10).distinct().toList()
            val phones = Regex("\\(\\d{3}\\)\\s*\\d{3}-\\d{4}").findAll(html).map { it.value.trim() }.take(10).distinct().toList()
            val relatives = Regex("class=\"[^\"]*relative[^\"]*\"[^>]*>[^<]*<[^>]+>([A-Z][a-z]+ [A-Z][a-z]+)").findAll(html)
                .map { it.groupValues[1] }.take(15).distinct().toList()
            val hasData = ages.isNotEmpty() || cities.isNotEmpty() || phones.isNotEmpty()
            if (hasData) {
                if (ages.isNotEmpty()) meta["fps_age"] = ages.first()
                if (cities.isNotEmpty() && states.isNotEmpty()) {
                    meta["fps_locations"] = cities.zip(states).take(10).joinToString(" | ") { (c, s) -> "$c, $s" }
                } else if (cities.isNotEmpty()) {
                    meta["fps_locations"] = cities.take(10).joinToString(" | ")
                }
                if (phones.isNotEmpty()) meta["fps_phones"] = phones.joinToString(", ")
                if (relatives.isNotEmpty()) meta["fps_relatives"] = relatives.joinToString(", ")
                sources.add(DataSource("FastPeopleSearch", null, Date(), 0.7))
                emit(SearchProgressEvent.Found("FastPeopleSearch",
                    buildString {
                        if (ages.isNotEmpty()) append("Age: ${ages.first()}")
                        if (cities.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(cities.first()) }
                        if (phones.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(phones.first()) }
                    }
                ))
            } else emit(SearchProgressEvent.NotFound("FastPeopleSearch"))
        } catch (e: Exception) { emit(SearchProgressEvent.Failed("FastPeopleSearch", e.message ?: "")) }
    }

    private suspend fun judyRecordsScrape(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        emit(SearchProgressEvent.Checking("JudyRecords"))
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://www.judyrecords.com/search/?q=$encoded")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0")
                .addHeader("Accept", "text/html,application/xhtml+xml")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: ""
            resp.close()
            if (html.isBlank() || resp.code == 403) { emit(SearchProgressEvent.NotFound("JudyRecords")); return }
            val caseCount = Regex("(\\d+)\\s+result").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val caseNames = Regex("<div[^>]*class=\"[^\"]*case-name[^\"]*\"[^>]*>([^<]+)</div>").findAll(html)
                .map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.take(8).distinct().toList()
            val courts = Regex("<div[^>]*class=\"[^\"]*court[^\"]*\"[^>]*>([^<]+)</div>").findAll(html)
                .map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.take(5).distinct().toList()
            val hasData = caseCount > 0 || caseNames.isNotEmpty()
            if (hasData) {
                if (caseCount > 0) meta["judyrecords_count"] = caseCount.toString()
                if (caseNames.isNotEmpty()) meta["judyrecords_cases"] = caseNames.joinToString("\n")
                if (courts.isNotEmpty()) meta["judyrecords_courts"] = courts.joinToString(", ")
                sources.add(DataSource("JudyRecords", null, Date(), 0.75))
                emit(SearchProgressEvent.Found("JudyRecords", "${if (caseCount > 0) "$caseCount cases found" else "${caseNames.size} records found"}"))
            } else emit(SearchProgressEvent.NotFound("JudyRecords"))
        } catch (e: Exception) { emit(SearchProgressEvent.Failed("JudyRecords", e.message ?: "")) }
    }

    private suspend fun familyTreeNowScrape(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        emit(SearchProgressEvent.Checking("FamilyTreeNow"))
        try {
            val parts = query.trim().split(" ")
            val first = URLEncoder.encode(parts.firstOrNull() ?: "", "UTF-8")
            val last = URLEncoder.encode(parts.drop(1).joinToString("+"), "UTF-8")
            val req = Request.Builder()
                .url("https://www.familytreenow.com/search/people/results?first=$first&last=$last")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0")
                .addHeader("Accept", "text/html,application/xhtml+xml")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: ""
            resp.close()
            if (html.isBlank() || resp.code == 403) { emit(SearchProgressEvent.NotFound("FamilyTreeNow")); return }
            val birthYears = Regex("(?:Born|Birth)[^0-9]{0,20}(\\d{4})").findAll(html)
                .map { it.groupValues[1] }.filter { it.toIntOrNull()?.let { y -> y in 1900..2010 } == true }.take(3).distinct().toList()
            val cities = Regex("<span[^>]*class=\"[^\"]*city[^\"]*\"[^>]*>([^<]+)</span>").findAll(html)
                .map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.take(5).distinct().toList()
            val relatives = Regex("(?:relative|family member)[^<]{0,100}<[^>]+>([A-Z][a-z]+ [A-Z][a-z]+)").findAll(html)
                .map { it.groupValues[1] }.take(8).distinct().toList()
            val hasData = birthYears.isNotEmpty() || cities.isNotEmpty() || relatives.isNotEmpty()
            if (hasData) {
                if (birthYears.isNotEmpty()) meta["ftn_birth_year"] = birthYears.first()
                if (cities.isNotEmpty()) meta["ftn_locations"] = cities.take(10).joinToString(" | ")
                if (relatives.isNotEmpty()) meta["ftn_relatives"] = relatives.joinToString(", ")
                sources.add(DataSource("FamilyTreeNow", null, Date(), 0.65))
                emit(SearchProgressEvent.Found("FamilyTreeNow",
                    buildString {
                        if (birthYears.isNotEmpty()) append("Born ~${birthYears.first()}")
                        if (relatives.isNotEmpty()) { if (isNotEmpty()) append(" · "); append("${relatives.size} relative${if (relatives.size != 1) "s" else ""}") }
                    }.ifBlank { "Record found" }
                ))
            } else emit(SearchProgressEvent.NotFound("FamilyTreeNow"))
        } catch (e: Exception) { emit(SearchProgressEvent.Failed("FamilyTreeNow", e.message ?: "")) }
    }

    private suspend fun zabaSearchScrape(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        emit(SearchProgressEvent.Checking("ZabaSearch"))
        try {
            val slug = query.trim().replace(" ", "+")
            val req = Request.Builder()
                .url("https://www.zabasearch.com/people/$slug/")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .addHeader("Referer", "https://www.zabasearch.com/")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: ""; resp.close()
            if (resp.code == 403 || resp.code == 429 || html.isBlank()) {
                emit(SearchProgressEvent.NotFound("ZabaSearch")); return
            }
            val ages = Regex("(?:Age|age)\\s*:?\\s*(\\d{2,3})").findAll(html).map { it.groupValues[1] }.take(5).distinct().toList()
            val phones = Regex("\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}").findAll(html)
                .map { it.value.replace(Regex("[^\\d]"), "").let { d -> if (d.length == 10) "(${d.substring(0,3)}) ${d.substring(3,6)}-${d.substring(6)}" else it.value } }
                .filter { it.length >= 10 }.take(10).distinct().toList()
            val addresses = Regex("(?:address|street|avenue|boulevard|drive|road|lane|way|court|place)[^<]{0,5}<[^>]+>([^<]{10,80}(?:AL|AK|AZ|AR|CA|CO|CT|DE|FL|GA|HI|ID|IL|IN|IA|KS|KY|LA|ME|MD|MA|MI|MN|MS|MO|MT|NE|NV|NH|NJ|NM|NY|NC|ND|OH|OK|OR|PA|RI|SC|SD|TN|TX|UT|VT|VA|WA|WV|WI|WY)[^<]{0,10})</", RegexOption.IGNORE_CASE)
                .findAll(html).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.take(10).distinct().toList()
            val cityState = Regex("([A-Z][a-zA-Z ]+,\\s*(?:AL|AK|AZ|AR|CA|CO|CT|DE|FL|GA|HI|ID|IL|IN|IA|KS|KY|LA|ME|MD|MA|MI|MN|MS|MO|MT|NE|NV|NH|NJ|NM|NY|NC|ND|OH|OK|OR|PA|RI|SC|SD|TN|TX|UT|VT|VA|WA|WV|WI|WY))").findAll(html)
                .map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.distinct().take(10).toList()
            val hasData = ages.isNotEmpty() || phones.isNotEmpty() || addresses.isNotEmpty() || cityState.isNotEmpty()
            if (hasData) {
                if (ages.isNotEmpty()) meta["zaba_age"] = ages.first()
                if (phones.isNotEmpty()) meta["zaba_phones"] = phones.joinToString(", ")
                if (addresses.isNotEmpty()) meta["zaba_addresses"] = addresses.joinToString(" | ")
                if (cityState.isNotEmpty()) meta["zaba_locations"] = cityState.joinToString(" | ")
                sources.add(DataSource("ZabaSearch", "https://www.zabasearch.com/people/$slug/", Date(), 0.7))
                emit(SearchProgressEvent.Found("ZabaSearch", buildString {
                    if (ages.isNotEmpty()) append("Age: ${ages.first()}")
                    if (cityState.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(cityState.first()) }
                    if (phones.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(phones.first()) }
                }))
            } else {
                emit(SearchProgressEvent.NotFound("ZabaSearch"))
            }
        } catch (e: Exception) { emit(SearchProgressEvent.Failed("ZabaSearch", e.message ?: "")) }
    }

    private suspend fun fourOneOneScrape(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        emit(SearchProgressEvent.Checking("411.com"))
        try {
            val parts = query.trim().split(" ")
            val first = URLEncoder.encode(parts.firstOrNull() ?: "", "UTF-8")
            val last = URLEncoder.encode(parts.drop(1).joinToString(" "), "UTF-8")
            val req = Request.Builder()
                .url("https://www.411.com/name/$first-$last/")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .addHeader("Referer", "https://www.411.com/")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: ""; resp.close()
            if (resp.code == 403 || resp.code == 429 || html.isBlank()) {
                emit(SearchProgressEvent.NotFound("411.com")); return
            }
            val phones = Regex("\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}").findAll(html)
                .map { it.value.trim() }.filter { it.length >= 10 }.take(10).distinct().toList()
            val ages = Regex("(?:Age|age)[:\\s]+(\\d{2,3})").findAll(html).map { it.groupValues[1] }.take(5).distinct().toList()
            val cityState = Regex("([A-Z][a-zA-Z ]+,\\s*(?:AL|AK|AZ|AR|CA|CO|CT|DE|FL|GA|HI|ID|IL|IN|IA|KS|KY|LA|ME|MD|MA|MI|MN|MS|MO|MT|NE|NV|NH|NJ|NM|NY|NC|ND|OH|OK|OR|PA|RI|SC|SD|TN|TX|UT|VT|VA|WA|WV|WI|WY))").findAll(html)
                .map { it.groupValues[1].trim() }.distinct().take(10).toList()
            val relatives = Regex("(?:relative|associate|related|alias)[^<]{0,80}<[^>]+>([A-Z][a-z]+ [A-Z][a-z]+)").findAll(html)
                .map { it.groupValues[1] }.filter { it.isNotBlank() }.distinct().take(10).toList()
            val hasData = phones.isNotEmpty() || ages.isNotEmpty() || cityState.isNotEmpty()
            if (hasData) {
                if (phones.isNotEmpty()) meta["411_phones"] = phones.joinToString(", ")
                if (ages.isNotEmpty()) meta["411_age"] = ages.first()
                if (cityState.isNotEmpty()) meta["411_locations"] = cityState.joinToString(" | ")
                if (relatives.isNotEmpty()) meta["411_relatives"] = relatives.joinToString(", ")
                sources.add(DataSource("411.com", null, Date(), 0.7))
                emit(SearchProgressEvent.Found("411.com", buildString {
                    if (ages.isNotEmpty()) append("Age: ${ages.first()}")
                    if (cityState.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(cityState.first()) }
                    if (phones.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(phones.first()) }
                }))
            } else {
                emit(SearchProgressEvent.NotFound("411.com"))
            }
        } catch (e: Exception) { emit(SearchProgressEvent.Failed("411.com", e.message ?: "")) }
    }

    private suspend fun gNewsFetch(
        query: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        emit(SearchProgressEvent.Checking("GNews"))
        try {
            val encoded = URLEncoder.encode("\"$query\"", "UTF-8")
            val req = Request.Builder()
                .url("https://gnews.io/api/v4/search?q=$encoded&lang=en&max=5&token=free")
                .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                .build()
            val resp = fastHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""; resp.close()
            val titles = Regex("\"title\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(body)
                .map { it.groupValues[1].replace("\\\"", "\"").trim() }.filter { it.isNotBlank() }.take(5).toList()
            val descriptions = Regex("\"description\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(body)
                .map { it.groupValues[1].replace("\\\"", "\"").replace("\\n", " ").trim() }.filter { it.isNotBlank() }.take(5).toList()
            val sources2 = Regex("\"name\"\\s*:\\s*\"([^\"]{2,60})\"").findAll(body)
                .map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.distinct().take(5).toList()
            val publishedDates = Regex("\"publishedAt\"\\s*:\\s*\"([^\"]+)\"").findAll(body)
                .map { it.groupValues[1].take(10) }.take(5).toList()
            if (titles.isNotEmpty()) {
                meta["gnews_count"] = titles.size.toString()
                val combined = titles.zip(descriptions.ifEmpty { List(titles.size) { "" } })
                    .zip(publishedDates.ifEmpty { List(titles.size) { "" } })
                    .mapIndexed { i, (td, date) ->
                        val src = sources2.getOrNull(i) ?: ""
                        "${td.first}${if (date.isNotBlank()) " [$date]" else ""}${if (src.isNotBlank()) " — $src" else ""}${if (td.second.isNotBlank()) "\n${td.second}" else ""}"
                    }
                meta["gnews_articles"] = combined.joinToString("\n---\n")
                sources.add(DataSource("GNews", null, Date(), 0.75))
                emit(SearchProgressEvent.Found("GNews", "${titles.size} news article${if (titles.size != 1) "s" else ""} found"))
            } else {
                val totalHits = Regex("\"totalArticles\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (totalHits == 0) emit(SearchProgressEvent.NotFound("GNews"))
                else emit(SearchProgressEvent.NotFound("GNews"))
            }
        } catch (e: Exception) { emit(SearchProgressEvent.Failed("GNews", e.message ?: "")) }
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
