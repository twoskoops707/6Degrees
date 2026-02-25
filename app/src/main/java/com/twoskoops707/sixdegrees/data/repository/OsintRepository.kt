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
            meta["gravatar_profile"] = "https://www.gravatar.com/$hash.json"
            if (code != 404) {
                sources.add(DataSource("Gravatar", "https://www.gravatar.com/$hash", Date(), 0.6))
                emit(SearchProgressEvent.Found("Gravatar", "Profile image exists"))
            } else {
                emit(SearchProgressEvent.NotFound("Gravatar"))
            }
        } catch (e: Exception) {
            emit(SearchProgressEvent.Failed("Gravatar", e.message ?: ""))
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

        val found = sources.filter { it.url != null }
        meta["sites_checked"] = (usernameSites.size + 1).toString()
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
                    emit(SearchProgressEvent.Failed("CourtListener", "HTTP ${resp.code()}"))
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

        meta["judyrecords_link"] = "https://www.judyrecords.com/record/${query.replace(" ", "-").lowercase()}"
        meta["spokeo_link"] = "https://www.spokeo.com/${query.replace(" ", "-").lowercase()}"
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

        meta["sec_link"] = "https://efts.sec.gov/LATEST/search-index?q=%22${URLEncoder.encode(query, "UTF-8")}%22"
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
