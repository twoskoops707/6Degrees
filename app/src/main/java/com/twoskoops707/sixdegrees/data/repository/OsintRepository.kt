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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.Date
import java.util.UUID

private class DateAdapter {
    @ToJson fun toJson(date: Date): Long = date.time
    @FromJson fun fromJson(value: Long): Date = Date(value)
}

data class OsintResult(
    val reportId: String,
    val sections: Map<String, Any> = emptyMap()
)

class OsintRepository(context: Context) {

    private val apiKeyManager = ApiKeyManager(context)
    private val db = OsintDatabase.getDatabase(context)
    private val moshi = Moshi.Builder().add(DateAdapter()).add(KotlinJsonAdapterFactory()).build()
    private val httpClient = OkHttpClient.Builder().build()

    suspend fun search(query: String, type: String): Result<String> = when (type) {
        "email" -> searchEmail(query)
        "phone" -> searchPhone(query)
        "username" -> searchUsername(query)
        "ip", "domain" -> searchIpDomain(query)
        "company" -> searchCompany(query)
        "image" -> searchImage(query)
        else -> searchPerson(query)
    }

    suspend fun searchEmail(email: String): Result<String> {
        val sources = mutableListOf<DataSource>()
        val metadata = mutableMapOf<String, String>()

        try {
            val emailRep = RetrofitClient.emailRepService.getReputation(email)
            if (emailRep.isSuccessful) {
                val body = emailRep.body()
                metadata["emailrep_reputation"] = body?.reputation ?: "unknown"
                metadata["emailrep_suspicious"] = body?.suspicious?.toString() ?: "false"
                metadata["emailrep_references"] = body?.references?.toString() ?: "0"
                body?.details?.profiles?.let { profiles ->
                    metadata["emailrep_profiles"] = profiles.joinToString(", ")
                }
                metadata["emailrep_breach"] = body?.details?.dataBreach?.toString() ?: "false"
                sources.add(DataSource("EmailRep.io", null, Date(), 0.8))
            }
        } catch (_: Exception) {}

        val hibpKey = apiKeyManager.hibpKey
        if (hibpKey.isNotBlank()) {
            try {
                val resp = RetrofitClient.hibpService.getBreachedAccount(email, apiKey = hibpKey)
                if (resp.isSuccessful) {
                    val breaches = resp.body()
                    metadata["hibp_breach_count"] = breaches?.size?.toString() ?: "0"
                    metadata["hibp_breaches"] = breaches?.take(5)?.mapNotNull { it.name }?.joinToString(", ") ?: ""
                    sources.add(DataSource("HaveIBeenPwned", null, Date(), 0.95))
                }
            } catch (_: Exception) {}
        }

        try {
            val hash = md5(email.lowercase().trim())
            metadata["gravatar_url"] = "https://www.gravatar.com/avatar/$hash?d=404"
            metadata["gravatar_profile"] = "https://www.gravatar.com/$hash.json"
            sources.add(DataSource("Gravatar", "https://www.gravatar.com/$hash", Date(), 0.5))
        } catch (_: Exception) {}

        val reportId = saveReport(email, null, sources, metadata)
        return Result.success(reportId)
    }

    suspend fun searchPhone(phone: String): Result<String> {
        val sources = mutableListOf<DataSource>()
        val metadata = mutableMapOf<String, String>()
        val numverifyKey = apiKeyManager.numverifyKey

        if (numverifyKey.isNotBlank()) {
            try {
                val resp = RetrofitClient.create("https://apilayer.net/")
                    .create(retrofit2.http.GET::class.java)
                val url = "https://apilayer.net/api/validate?access_key=$numverifyKey&number=$phone"
                val req = Request.Builder().url(url).build()
                val response = httpClient.newCall(req).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    metadata["numverify_raw"] = body.take(500)
                    sources.add(DataSource("Numverify", null, Date(), 0.85))
                }
            } catch (_: Exception) {}
        }

        metadata["phone_query"] = phone
        metadata["truecaller_link"] = "https://www.truecaller.com/search/us/$phone"
        metadata["whitepages_link"] = "https://www.whitepages.com/phone/$phone"

        val reportId = saveReport(phone, null, sources, metadata)
        return Result.success(reportId)
    }

    suspend fun searchUsername(username: String): Result<String> = coroutineScope {
        val sources = mutableListOf<DataSource>()
        val metadata = mutableMapOf<String, String>()

        val sites = mapOf(
            "GitHub" to "https://github.com/$username",
            "Reddit" to "https://www.reddit.com/user/$username",
            "Twitter/X" to "https://twitter.com/$username",
            "Instagram" to "https://www.instagram.com/$username",
            "TikTok" to "https://www.tiktok.com/@$username",
            "YouTube" to "https://www.youtube.com/@$username",
            "Twitch" to "https://www.twitch.tv/$username",
            "Pinterest" to "https://www.pinterest.com/$username",
            "LinkedIn" to "https://www.linkedin.com/in/$username",
            "Steam" to "https://steamcommunity.com/id/$username",
            "Flickr" to "https://www.flickr.com/people/$username",
            "Tumblr" to "https://$username.tumblr.com",
            "Medium" to "https://medium.com/@$username",
            "DeviantArt" to "https://www.deviantart.com/$username",
            "Soundcloud" to "https://soundcloud.com/$username",
            "Spotify" to "https://open.spotify.com/user/$username",
            "Gitlab" to "https://gitlab.com/$username",
            "Keybase" to "https://keybase.io/$username",
            "Replit" to "https://replit.com/@$username",
            "HackerNews" to "https://news.ycombinator.com/user?id=$username",
            "ProductHunt" to "https://www.producthunt.com/@$username",
            "Gravatar" to "https://en.gravatar.com/$username",
            "About.me" to "https://about.me/$username",
            "Wattpad" to "https://www.wattpad.com/user/$username",
            "Patreon" to "https://www.patreon.com/$username",
            "Cash.app" to "https://cash.app/\$$username",
            "Venmo" to "https://venmo.com/$username",
            "Etsy" to "https://www.etsy.com/shop/$username",
            "Xbox" to "https://account.xbox.com/en-US/profile?gamertag=$username",
            "PSN" to "https://psnprofiles.com/$username",
            "Nintendo" to "https://www.nintendo.com/en-GB/Nintendo-Switch/Online/",
            "VSCO" to "https://vsco.co/$username",
            "Behance" to "https://www.behance.net/$username",
            "Dribbble" to "https://dribbble.com/$username",
            "Last.fm" to "https://www.last.fm/user/$username",
            "Lichess" to "https://lichess.org/@/$username",
            "Chess.com" to "https://www.chess.com/member/$username",
            "Codecademy" to "https://www.codecademy.com/profiles/$username",
            "Duolingo" to "https://www.duolingo.com/profile/$username",
            "NameMC" to "https://namemc.com/profile/$username"
        )

        val hits = mutableListOf<String>()
        val checked = mutableListOf<String>()

        val checks = sites.entries.map { (site, url) ->
            async {
                try {
                    val req = Request.Builder().url(url).head()
                        .addHeader("User-Agent", "Mozilla/5.0")
                        .build()
                    val response = httpClient.newCall(req).execute()
                    val code = response.code
                    response.close()
                    if (code in 200..299 || code == 301 || code == 302) {
                        synchronized(hits) { hits.add("$site: $url") }
                        sources.add(DataSource(site, url, Date(), 0.7))
                    }
                    synchronized(checked) { checked.add(site) }
                } catch (_: Exception) {
                    synchronized(checked) { checked.add(site) }
                }
            }
        }
        checks.awaitAll()

        metadata["username"] = username
        metadata["sites_checked"] = checked.size.toString()
        metadata["sites_found"] = hits.size.toString()
        metadata["found_urls"] = hits.joinToString("\n")

        val githubKey = apiKeyManager.hibpKey
        try {
            val req = Request.Builder()
                .url("https://api.github.com/users/$username")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                metadata["github_profile"] = body.take(800)
                sources.add(DataSource("GitHub API", "https://github.com/$username", Date(), 0.9))
            }
            resp.close()
        } catch (_: Exception) {}

        val reportId = saveReport(username, null, sources, metadata)
        Result.success(reportId)
    }

    suspend fun searchIpDomain(query: String): Result<String> = coroutineScope {
        val sources = mutableListOf<DataSource>()
        val metadata = mutableMapOf<String, String>()

        val isIp = query.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))

        val ipLookup = async {
            try {
                val resp = RetrofitClient.ipApiService.lookup(query)
                if (resp.isSuccessful) {
                    val b = resp.body()
                    metadata["ip_country"] = b?.country ?: ""
                    metadata["ip_city"] = b?.city ?: ""
                    metadata["ip_isp"] = b?.isp ?: ""
                    metadata["ip_org"] = b?.org ?: ""
                    metadata["ip_asn"] = b?.asn ?: ""
                    metadata["ip_lat"] = b?.lat?.toString() ?: ""
                    metadata["ip_lon"] = b?.lon?.toString() ?: ""
                    metadata["ip_timezone"] = b?.timezone ?: ""
                    synchronized(sources) { sources.add(DataSource("IP-API.com", null, Date(), 0.9)) }
                }
            } catch (_: Exception) {}
        }

        val whoisLookup = async {
            try {
                val resp = RetrofitClient.hackerTargetService.whois(query)
                if (resp.isSuccessful) {
                    metadata["whois"] = resp.body()?.take(800) ?: ""
                    synchronized(sources) { sources.add(DataSource("HackerTarget WHOIS", null, Date(), 0.85)) }
                }
            } catch (_: Exception) {}
        }

        val dnsLookup = async {
            try {
                val resp = RetrofitClient.hackerTargetService.dnsLookup(query)
                if (resp.isSuccessful) {
                    metadata["dns"] = resp.body()?.take(500) ?: ""
                    synchronized(sources) { sources.add(DataSource("HackerTarget DNS", null, Date(), 0.85)) }
                }
            } catch (_: Exception) {}
        }

        val crtLookup = async {
            if (!isIp) {
                try {
                    val resp = RetrofitClient.crtShService.query(query)
                    if (resp.isSuccessful) {
                        val entries = resp.body() ?: emptyList()
                        val subdomains = entries.mapNotNull { it.nameValue }
                            .flatMap { it.split("\n") }
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .take(30)
                        metadata["subdomains"] = subdomains.joinToString(", ")
                        metadata["cert_count"] = entries.size.toString()
                        synchronized(sources) { sources.add(DataSource("crt.sh", "https://crt.sh/?q=$query", Date(), 0.8)) }
                    }
                } catch (_: Exception) {}
            }
        }

        val waybackLookup = async {
            try {
                val resp = RetrofitClient.waybackService.query(query)
                if (resp.isSuccessful) {
                    val data = resp.body()
                    val snapshots = data?.drop(1)
                    metadata["wayback_count"] = snapshots?.size?.toString() ?: "0"
                    metadata["wayback_first"] = snapshots?.firstOrNull()?.firstOrNull() ?: ""
                    metadata["wayback_last"] = snapshots?.lastOrNull()?.firstOrNull() ?: ""
                    synchronized(sources) { sources.add(DataSource("Wayback Machine", "https://web.archive.org/web/*/$query", Date(), 0.7)) }
                }
            } catch (_: Exception) {}
        }

        val otxLookup = async {
            try {
                val resp = if (isIp) {
                    RetrofitClient.alienVaultService.ipGeneral(query)
                } else {
                    RetrofitClient.alienVaultService.domainGeneral(query)
                }
                if (resp.isSuccessful) {
                    val b = resp.body()
                    metadata["otx_pulse_count"] = b?.pulseInfo?.count?.toString() ?: "0"
                    metadata["otx_reputation"] = b?.reputation?.toString() ?: "0"
                    synchronized(sources) { sources.add(DataSource("AlienVault OTX", null, Date(), 0.85)) }
                }
            } catch (_: Exception) {}
        }

        awaitAll(ipLookup, whoisLookup, dnsLookup, crtLookup, waybackLookup, otxLookup)

        metadata["shodan_link"] = "https://www.shodan.io/host/$query"
        metadata["urlscan_link"] = "https://urlscan.io/search/#domain:$query"
        metadata["virustotal_link"] = "https://www.virustotal.com/gui/domain/$query"

        val reportId = saveReport(query, null, sources, metadata)
        Result.success(reportId)
    }

    suspend fun searchPerson(query: String): Result<String> = coroutineScope {
        val sources = mutableListOf<DataSource>()
        val metadata = mutableMapOf<String, String>()

        val piplKey = apiKeyManager.piplKey
        var personId: String? = null

        if (piplKey.isNotBlank()) {
            try {
                val nameParts = query.trim().split(" ")
                val resp = RetrofitClient.piplService.searchPerson(
                    apiKey = piplKey,
                    rawName = query,
                    firstName = nameParts.firstOrNull(),
                    lastName = if (nameParts.size > 1) nameParts.drop(1).joinToString(" ") else null,
                    showSources = "all"
                )
                if (resp.isSuccessful) {
                    val body = resp.body()
                    val person = body?.person
                    if (person != null) {
                        val entity = mapPiplPersonToEntity(person)
                        db.personDao().insertPerson(entity)
                        personId = entity.id
                        sources.add(DataSource("Pipl", null, Date(), 0.9))
                    }
                }
            } catch (_: Exception) {}
        }

        val jailBaseSearch = async {
            try {
                val parts = query.trim().split(" ")
                val first = parts.firstOrNull() ?: ""
                val last = if (parts.size > 1) parts.last() else ""
                val resp = RetrofitClient.jailBaseService.search("$first+$last")
                if (resp.isSuccessful) {
                    val records = resp.body()?.records ?: emptyList()
                    metadata["arrest_count"] = records.size.toString()
                    metadata["arrest_records"] = records.take(5).joinToString("\n") { r ->
                        "${r.first_name} ${r.last_name} | ${r.charge} | ${r.arrest_date} | ${r.agency} | ${r.city}, ${r.state}"
                    }
                    if (records.isNotEmpty()) {
                        synchronized(sources) { sources.add(DataSource("JailBase", "https://www.jailbase.com/", Date(), 0.8)) }
                    }
                }
            } catch (_: Exception) {}
        }

        jailBaseSearch.await()

        metadata["person_query"] = query
        metadata["courtlistener_link"] = "https://www.courtlistener.com/?q=${query.replace(" ", "+")}&type=r&order_by=score+desc"
        metadata["judyrecords_link"] = "https://www.judyrecords.com/record/${query.replace(" ", "-").lowercase()}"
        metadata["spokeo_link"] = "https://www.spokeo.com/${query.replace(" ", "-").lowercase()}"
        metadata["instantcheckmate_link"] = "https://www.instantcheckmate.com/"

        val reportId = saveReport(query, personId, sources, metadata)
        Result.success(reportId)
    }

    suspend fun searchCompany(query: String): Result<String> = coroutineScope {
        val sources = mutableListOf<DataSource>()
        val metadata = mutableMapOf<String, String>()

        val companySearch = async {
            try {
                val resp = RetrofitClient.openCorporatesService.searchCompanies(query)
                if (resp.isSuccessful) {
                    val companies = resp.body()?.results?.companies ?: emptyList()
                    metadata["company_count"] = companies.size.toString()
                    metadata["companies"] = companies.take(5).joinToString("\n") { item ->
                        val c = item.company
                        "${c?.name} | ${c?.jurisdictionCode} | ${c?.currentStatus} | ${c?.incorporationDate}"
                    }
                    synchronized(sources) { sources.add(DataSource("OpenCorporates", null, Date(), 0.85)) }
                }
            } catch (_: Exception) {}
        }

        val officerSearch = async {
            try {
                val resp = RetrofitClient.openCorporatesService.searchOfficers(query)
                if (resp.isSuccessful) {
                    val officers = resp.body()?.results?.officers ?: emptyList()
                    metadata["officer_count"] = officers.size.toString()
                    metadata["officers"] = officers.take(5).joinToString("\n") { item ->
                        val o = item.officer
                        "${o?.name} | ${o?.position} | ${o?.company?.name} | ${o?.company?.jurisdictionCode}"
                    }
                }
            } catch (_: Exception) {}
        }

        val hunterKey = apiKeyManager.hunterKey
        val hunterSearch = async {
            if (hunterKey.isNotBlank()) {
                try {
                    val resp = RetrofitClient.hunterService.searchDomain(
                        domain = query.replace(" ", "").lowercase() + ".com",
                        apiKey = hunterKey
                    )
                    if (resp.isSuccessful) {
                        val data = resp.body()?.data
                        metadata["hunter_emails_count"] = data?.emails?.size?.toString() ?: "0"
                        metadata["hunter_emails"] = data?.emails?.take(5)?.mapNotNull { it.value }?.joinToString(", ") ?: ""
                        synchronized(sources) { sources.add(DataSource("Hunter.io", null, Date(), 0.8)) }
                    }
                } catch (_: Exception) {}
            }
        }

        awaitAll(companySearch, officerSearch, hunterSearch)

        metadata["company_query"] = query
        metadata["usaspending_link"] = "https://www.usaspending.gov/search/?hash=abc&filters=%7B%22keywords%22%3A%5B%22$query%22%5D%7D"
        metadata["sec_link"] = "https://efts.sec.gov/LATEST/search-index?q=%22$query%22&dateRange=custom&startdt=2000-01-01"

        val reportId = saveReport(query, null, sources, metadata)
        Result.success(reportId)
    }

    suspend fun searchImage(query: String): Result<String> {
        val sources = mutableListOf<DataSource>()
        val metadata = mutableMapOf<String, String>()

        metadata["image_path"] = query
        metadata["tineye_link"] = "https://tineye.com/search"
        metadata["google_lens_link"] = "https://lens.google.com/"
        metadata["yandex_link"] = "https://yandex.com/images/"
        metadata["facecheck_link"] = "https://facecheck.id/"

        sources.add(DataSource("Image Search", null, Date(), 0.5))
        val reportId = saveReport("image_search", null, sources, metadata)
        return Result.success(reportId)
    }

    suspend fun getRecentReports(limit: Int = 20): List<OsintReportEntity> =
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
                city = addr.city ?: "",
                state = addr.state ?: "",
                postalCode = addr.zipCode ?: "",
                country = addr.country ?: "",
                type = "unknown"
            )
        } ?: emptyList()

        val employment = pipl.jobs?.map { job ->
            Employment(
                companyName = job.organization ?: "",
                jobTitle = job.title ?: "",
                startDate = job.dateRange?.start,
                endDate = job.dateRange?.end,
                isCurrent = job.dateRange?.end == null
            )
        } ?: emptyList()

        val socialProfiles = pipl.urls?.mapNotNull { url ->
            url.url?.let { urlStr ->
                SocialProfile(
                    platform = url.category ?: url.domain ?: "Web",
                    username = url.domain ?: "",
                    url = urlStr,
                    followersCount = null
                )
            }
        } ?: emptyList()

        val addrType = Types.newParameterizedType(List::class.java, Address::class.java)
        val empType = Types.newParameterizedType(List::class.java, Employment::class.java)
        val socialType = Types.newParameterizedType(List::class.java, SocialProfile::class.java)
        val strType = Types.newParameterizedType(List::class.java, String::class.java)

        return PersonEntity(
            id = id,
            firstName = firstName,
            lastName = lastName,
            fullName = fullName,
            emailAddress = pipl.emails?.firstOrNull()?.address,
            phoneNumber = pipl.phones?.firstOrNull()?.display,
            dateOfBirth = pipl.dob,
            addressesJson = moshi.adapter<List<Address>>(addrType).toJson(addresses),
            employmentHistoryJson = moshi.adapter<List<Employment>>(empType).toJson(employment),
            socialProfilesJson = moshi.adapter<List<SocialProfile>>(socialType).toJson(socialProfiles),
            aliasesJson = moshi.adapter<List<String>>(strType).toJson(
                pipl.usernames?.mapNotNull { it.content } ?: emptyList()
            ),
            nationalitiesJson = moshi.adapter<List<String>>(strType).toJson(
                pipl.originCountries?.mapNotNull { it.content } ?: emptyList()
            ),
            gender = pipl.gender,
            profileImageUrl = pipl.images?.firstOrNull()?.url
        )
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
