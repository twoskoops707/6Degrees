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
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
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
        "Cashapp" to "https://cash.app/\${u}"
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
                        meta["hibp_breaches"] = breaches.take(5).mapNotNull { it.name }.joinToString(", ")
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

        val clean = phone.replace(Regex("[^0-9+]"), "")
        meta["truecaller_link"] = "https://www.truecaller.com/search/us/${clean.replace("+", "")}"
        meta["whitepages_link"] = "https://www.whitepages.com/phone/${clean.replace("+", "")}"
        meta["spokeo_link"] = "https://www.spokeo.com/phone/${clean.replace("+", "")}"

        listOf(
            "TrueCaller" to meta["truecaller_link"]!!,
            "WhitePages" to meta["whitepages_link"]!!,
            "Spokeo" to meta["spokeo_link"]!!
        ).forEach { (name, url) ->
            emit(SearchProgressEvent.Checking(name))
            delay(80)
            sources.add(DataSource(name, url, Date(), 0.5))
            emit(SearchProgressEvent.Found(name, "Lookup link ready — tap to open"))
        }
    }

    private suspend fun usernameSearch(
        username: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        coroutineScope {
            usernameSites.entries.forEach { (site, urlTemplate) ->
                val url = urlTemplate.replace("{u}", username)
                launch {
                    emit(SearchProgressEvent.Checking(site))
                    try {
                        val req = Request.Builder()
                            .url(url)
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
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

        meta["shodan_link"] = "https://www.shodan.io/host/$query"
        meta["urlscan_link"] = "https://urlscan.io/search/#domain:$query"
        meta["virustotal_link"] = "https://www.virustotal.com/gui/domain/$query"
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

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val dashQuery = query.replace(" ", "-").lowercase()
        meta["judyrecords_link"] = "https://www.judyrecords.com/record/$dashQuery"
        meta["spokeo_link"] = "https://www.spokeo.com/$dashQuery"
        meta["beenverified_link"] = "https://www.beenverified.com/people-search/?q=$encodedQuery"
        meta["fastpeoplesearch_link"] = "https://www.fastpeoplesearch.com/name/$dashQuery"
        meta["truthfinder_link"] = "https://www.truthfinder.com/people-search/?firstName=${query.split(" ").firstOrNull() ?: ""}&lastName=${query.split(" ").drop(1).joinToString("+")}"
        meta["familytreenow_link"] = "https://www.familytreenow.com/search/people/results?first=${query.split(" ").firstOrNull() ?: ""}&last=${query.split(" ").drop(1).joinToString("+")}"
        meta["intelius_link"] = "https://www.intelius.com/people/$dashQuery"
        meta["zabasearch_link"] = "https://www.zabasearch.com/people/$dashQuery"
        meta["linkedin_person_link"] = "https://www.linkedin.com/search/results/people/?keywords=$encodedQuery"
        meta["facebook_person_link"] = "https://www.facebook.com/search/people/?q=$encodedQuery"
        meta["person_query"] = query
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

        val encodedQ = URLEncoder.encode(query, "UTF-8")
        meta["sec_link"] = "https://efts.sec.gov/LATEST/search-index?q=%22$encodedQ%22"
        meta["crunchbase_link"] = "https://www.crunchbase.com/textsearch?q=$encodedQ"
        meta["linkedin_company_link"] = "https://www.linkedin.com/search/results/companies/?keywords=$encodedQ"
        meta["opencorporates_link"] = "https://opencorporates.com/companies?q=$encodedQ"
        meta["openbb_link"] = "https://openbb.co/stock-search?query=$encodedQ"
        meta["company_query"] = query
    }

    private suspend fun imageSearch(
        imagePath: String,
        meta: ConcurrentHashMap<String, String>,
        sources: MutableList<DataSource>,
        emit: suspend (SearchProgressEvent) -> Unit
    ) {
        meta["image_path"] = imagePath
        val links = listOf(
            "TinEye" to "https://tineye.com/search",
            "Google Lens" to "https://lens.google.com/",
            "Yandex Images" to "https://yandex.com/images/",
            "FaceCheck.id" to "https://facecheck.id/",
            "Bing Visual Search" to "https://www.bing.com/visualsearch"
        )
        links.forEach { (name, url) ->
            emit(SearchProgressEvent.Checking(name))
            delay(120)
            meta["${name.lowercase().replace(" ", "_").replace(".", "_")}_link"] = url
            sources.add(DataSource(name, url, Date(), 0.5))
            emit(SearchProgressEvent.Found(name, "Open in browser — upload your image there"))
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

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
