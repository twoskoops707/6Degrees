package com.twoskoops707.sixdegrees.data.repository

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.twoskoops707.sixdegrees.data.osint.UsernamePlatformRegistry
import com.twoskoops707.sixdegrees.data.osint.UsernamePlatformRegistry.CheckMethod
import com.twoskoops707.sixdegrees.domain.model.UsernamePlatformHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Checks username presence across social/gaming platforms and enriches confirmed profiles
 * with public follower/friend stats where APIs allow.
 */
class UsernameDiscoveryService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val maxConcurrent: Int = 8,
    private val perPlatformTimeoutMs: Long = 8_000L
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    suspend fun discoverAndEnrich(
        username: String,
        onProgress: suspend (SearchProgressEvent) -> Unit = {}
    ): List<UsernamePlatformHit> = withContext(Dispatchers.IO) {
        val clean = username.trim().removePrefix("@")
        if (clean.length < 2 || clean.contains(' ')) return@withContext emptyList()

        onProgress(SearchProgressEvent.Checking("Username Scan (${UsernamePlatformRegistry.PLATFORMS.size} platforms)"))
        val semaphore = Semaphore(maxConcurrent)
        val found = ConcurrentHashMap<String, UsernamePlatformHit>()

        coroutineScope {
            UsernamePlatformRegistry.PLATFORMS.map { platform ->
                async {
                    semaphore.withPermit {
                        try {
                            withTimeout(perPlatformTimeoutMs) {
                                val hit = checkPlatform(clean, platform)
                                if (hit != null) {
                                    found[platform.name] = enrichHit(hit)
                                    onProgress(SearchProgressEvent.Found("Username/${platform.name}", hit.url))
                                }
                            }
                        } catch (_: Exception) {
                            // Platform timeout or network error — skip silently
                        }
                    }
                }
            }.awaitAll()
        }

        if (found.isEmpty()) {
            onProgress(SearchProgressEvent.NotFound("Username Scan"))
        } else {
            onProgress(SearchProgressEvent.Found("Username Scan", "${found.size} profiles on ${UsernamePlatformRegistry.PLATFORMS.size} platforms"))
        }
        found.values.sortedBy { it.platform }
    }

    suspend fun discoverFromHints(
        usernames: Collection<String>,
        socialUrls: Collection<String> = emptyList(),
        onProgress: suspend (SearchProgressEvent) -> Unit = {}
    ): List<UsernamePlatformHit> {
        val candidates = linkedSetOf<String>()
        usernames.map { it.trim().removePrefix("@") }.filter { it.length >= 2 && !it.contains(' ') }.forEach { candidates.add(it) }
        socialUrls.forEach { url ->
            UsernamePlatformRegistry.extractHandle(url)?.let { candidates.add(it) }
        }
        if (candidates.isEmpty()) return emptyList()
        val primary = candidates.first()
        val hits = discoverAndEnrich(primary, onProgress).toMutableList()
        val seen = hits.map { it.platform }.toMutableSet()
        for (extra in candidates.drop(1).take(1)) {
            if (extra.equals(primary, ignoreCase = true)) continue
            discoverAndEnrich(extra, onProgress)
                .filter { it.platform !in seen }
                .forEach {
                    hits.add(it)
                    seen.add(it.platform)
                }
        }
        return hits.distinctBy { "${it.platform}:${it.url}" }
    }

    fun applyToMetadata(
        hits: List<UsernamePlatformHit>,
        metadata: ConcurrentHashMap<String, String>,
        username: String
    ) {
        if (hits.isEmpty()) return
        metadata["username"] = username
        metadata["sites_checked"] = UsernamePlatformRegistry.PLATFORMS.size.toString()
        metadata["sites_found"] = hits.size.toString()
        hits.forEach { hit ->
            appendLine(metadata, "found_urls", hit.foundUrlLine())
            applyPlatformMetadata(hit, metadata)
        }
        val profiles = hits.map { it.toSocialProfile() }
        val adapter = moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.SocialProfile>>(
            Types.newParameterizedType(
                List::class.java,
                com.twoskoops707.sixdegrees.domain.model.SocialProfile::class.java
            )
        )
        metadata["social_profiles_json"] = adapter.toJson(profiles)
        metadata["username_platform_summary"] = hits.joinToString("\n") { hit ->
            buildString {
                append(hit.platform)
                append("|")
                append(hit.url)
                hit.formatStats()?.let { append("|").append(it) }
            }
        }
    }

    private fun appendLine(map: ConcurrentHashMap<String, String>, key: String, value: String) {
        if (value.isBlank()) return
        val existing = map[key]
        map[key] = if (existing.isNullOrBlank()) value else "$existing\n$value"
    }

    private fun applyPlatformMetadata(hit: UsernamePlatformHit, metadata: ConcurrentHashMap<String, String>) {
        when (hit.platform) {
            "GitHub" -> {
                hit.displayName?.let { metadata["github_name"] = it }
                hit.location?.let { metadata["github_location"] = it }
                hit.bio?.let { metadata["github_bio"] = it }
                hit.formatStats()?.let { metadata["github_stats"] = it }
                metadata["github_url"] = hit.url
            }
            "Reddit" -> metadata["reddit_url"] = hit.url
            "Keybase" -> {
                hit.displayName?.let { metadata["keybase_name"] = it }
                hit.location?.let { metadata["keybase_location"] = it }
                hit.bio?.let { metadata["keybase_bio"] = it }
                hit.extraStats?.let { metadata["keybase_proofs"] = it }
            }
            "HackerNews" -> {
                hit.extraStats?.substringAfter("Karma: ")?.substringBefore(" ")?.let { metadata["hackernews_karma"] = it }
                hit.bio?.let { metadata["hackernews_about"] = it }
            }
            "Dev.to" -> {
                hit.displayName?.let { metadata["devto_name"] = it }
                hit.location?.let { metadata["devto_location"] = it }
                hit.bio?.let { metadata["devto_summary"] = it }
                hit.extraStats?.let { metadata["devto_joined"] = it }
            }
            else -> {
                val key = hit.platform.lowercase().replace(Regex("[^a-z0-9]"), "_")
                hit.formatStats()?.let { metadata["${key}_stats"] = it }
                metadata["${key}_url"] = hit.url
            }
        }
    }

    private fun checkPlatform(username: String, platform: UsernamePlatformRegistry.PlatformDef): UsernamePlatformHit? {
        val encoded = URLEncoder.encode(username, "UTF-8")
        val checkUrl = UsernamePlatformRegistry.buildUrl(platform.checkUrlTemplate, encoded)
        val profileUrl = UsernamePlatformRegistry.buildUrl(platform.urlTemplate, encoded)

        return when (platform.name) {
            "GitHub" -> checkGitHubApi(username, profileUrl, platform)
            "Reddit" -> checkRedditApi(username, profileUrl, platform)
            "Keybase" -> checkKeybaseApi(username, profileUrl, platform)
            "HackerNews" -> checkHackerNewsApi(username, profileUrl, platform)
            "Dev.to" -> checkDevToApi(username, profileUrl, platform)
            "GitLab" -> checkGitLabApi(username, profileUrl, platform)
            "Chess.com" -> checkChessComApi(username, profileUrl, platform)
            "Mastodon" -> checkMastodonApi(username, profileUrl, platform)
            else -> checkHttp(platform, checkUrl, profileUrl, username)
        }
    }

    private fun checkHttp(
        platform: UsernamePlatformRegistry.PlatformDef,
        checkUrl: String,
        profileUrl: String,
        username: String
    ): UsernamePlatformHit? {
        val (code, body) = httpGet(checkUrl) ?: return null
        if (code in platform.notFoundStatus) return null
        if (code !in 200..399) return null

        return when (platform.method) {
            CheckMethod.BODY_ABSENT_MARKER -> {
                if (platform.absentMarkers.any { body.contains(it, ignoreCase = true) }) return null
                baseHit(platform, profileUrl, username)
            }
            CheckMethod.BODY_PRESENT_MARKER -> {
                if (platform.presentMarkers.none { body.contains(it, ignoreCase = true) }) return null
                baseHit(platform, profileUrl, username)
            }
            CheckMethod.HTTP_STATUS -> {
                if (platform.absentMarkers.any { body.contains(it, ignoreCase = true) }) return null
                if (body.length < 200 && body.contains("404")) return null
                baseHit(platform, profileUrl, username)
            }
        }
    }

    private fun checkGitHubApi(username: String, profileUrl: String, platform: UsernamePlatformRegistry.PlatformDef): UsernamePlatformHit? {
        val (code, body) = httpGet("https://api.github.com/users/${URLEncoder.encode(username, "UTF-8")}",
            mapOf("Accept" to "application/vnd.github.v3+json")) ?: return null
        if (code == 404 || !body.startsWith("{")) return null
        val json = JSONObject(body)
        if (json.optString("message") == "Not Found") return null
        return baseHit(platform, profileUrl, username).copy(
            displayName = json.optString("name").takeIf { it.isNotBlank() },
            bio = json.optString("bio").takeIf { it.isNotBlank() },
            location = json.optString("location").takeIf { it.isNotBlank() },
            followers = json.optInt("followers").takeIf { it >= 0 },
            following = json.optInt("following").takeIf { it >= 0 },
            extraStats = "Repos: ${json.optInt("public_repos")}"
        )
    }

    private fun checkRedditApi(username: String, profileUrl: String, platform: UsernamePlatformRegistry.PlatformDef): UsernamePlatformHit? {
        val (code, body) = httpGet("https://www.reddit.com/user/${URLEncoder.encode(username, "UTF-8")}/about.json") ?: return null
        if (code == 404 || !body.startsWith("{")) return null
        val data = JSONObject(body).optJSONObject("data") ?: return null
        val karma = data.optInt("link_karma", 0) + data.optInt("comment_karma", 0)
        return baseHit(platform, profileUrl, username).copy(
            extraStats = "Karma: $karma"
        )
    }

    private fun checkKeybaseApi(username: String, profileUrl: String, platform: UsernamePlatformRegistry.PlatformDef): UsernamePlatformHit? {
        val (code, body) = httpGet("https://keybase.io/_/api/1.0/user/lookup.json?usernames=${URLEncoder.encode(username, "UTF-8")}") ?: return null
        if (code != 200 || !body.startsWith("{")) return null
        val them = JSONObject(body).optJSONObject("them") ?: return null
        val basics = them.optJSONObject("basics") ?: return null
        if (basics.optString("username").isBlank()) return null
        val profile = them.optJSONObject("profile") ?: JSONObject()
        val proofs = them.optJSONArray("proofs_summary")?.length() ?: 0
        return baseHit(platform, profileUrl, username).copy(
            displayName = profile.optString("full_name").takeIf { it.isNotBlank() },
            bio = profile.optString("bio").takeIf { it.isNotBlank() },
            location = profile.optString("location").takeIf { it.isNotBlank() },
            followers = them.optInt("followers").takeIf { it > 0 },
            following = them.optInt("followees").takeIf { it > 0 },
            extraStats = if (proofs > 0) "$proofs verified proofs" else null
        )
    }

    private fun checkHackerNewsApi(username: String, profileUrl: String, platform: UsernamePlatformRegistry.PlatformDef): UsernamePlatformHit? {
        val (code, body) = httpGet("https://hacker-news.firebaseio.com/v0/user/${URLEncoder.encode(username, "UTF-8")}.json") ?: return null
        if (code != 200 || body == "null" || body.isBlank()) return null
        val json = JSONObject(body)
        val karma = json.optInt("karma", 0)
        return baseHit(platform, profileUrl, username).copy(
            bio = json.optString("about").takeIf { it.isNotBlank() },
            extraStats = "Karma: $karma"
        )
    }

    private fun checkDevToApi(username: String, profileUrl: String, platform: UsernamePlatformRegistry.PlatformDef): UsernamePlatformHit? {
        val (code, body) = httpGet("https://dev.to/api/users/by_username?url=${URLEncoder.encode(username, "UTF-8")}") ?: return null
        if (code == 404 || !body.startsWith("{")) return null
        val json = JSONObject(body)
        if (json.optString("username").isBlank()) return null
        return baseHit(platform, profileUrl, username).copy(
            displayName = json.optString("name").takeIf { it.isNotBlank() },
            bio = json.optString("summary").takeIf { it.isNotBlank() },
            location = json.optString("location").takeIf { it.isNotBlank() },
            extraStats = json.optString("joined_at").takeIf { it.isNotBlank() }
        )
    }

    private fun checkGitLabApi(username: String, profileUrl: String, platform: UsernamePlatformRegistry.PlatformDef): UsernamePlatformHit? {
        val (code, body) = httpGet("https://gitlab.com/api/v4/users?username=${URLEncoder.encode(username, "UTF-8")}") ?: return null
        if (code != 200 || !body.startsWith("[")) return null
        val arr = JSONArray(body)
        if (arr.length() == 0) return null
        val user = arr.optJSONObject(0) ?: return null
        return baseHit(platform, user.optString("web_url", profileUrl), username).copy(
            displayName = user.optString("name").takeIf { it.isNotBlank() },
            followers = null,
            following = null
        )
    }

    private fun checkChessComApi(username: String, profileUrl: String, platform: UsernamePlatformRegistry.PlatformDef): UsernamePlatformHit? {
        val (code, body) = httpGet("https://api.chess.com/pub/player/${URLEncoder.encode(username, "UTF-8")}") ?: return null
        if (code == 404 || !body.startsWith("{")) return null
        val json = JSONObject(body)
        if (json.optString("username").isBlank()) return null
        return baseHit(platform, profileUrl, username).copy(
            displayName = json.optString("name").takeIf { it.isNotBlank() },
            location = json.optString("country").takeIf { it.isNotBlank() },
            followers = json.optInt("followers").takeIf { it > 0 }
        )
    }

    private fun checkMastodonApi(username: String, profileUrl: String, platform: UsernamePlatformRegistry.PlatformDef): UsernamePlatformHit? {
        val (code, body) = httpGet("https://mastodon.social/api/v1/accounts/lookup?acct=${URLEncoder.encode(username, "UTF-8")}") ?: return null
        if (code == 404 || !body.startsWith("{")) return null
        val json = JSONObject(body)
        if (json.optString("username").isBlank()) return null
        return baseHit(platform, json.optString("url", profileUrl), username).copy(
            displayName = json.optString("display_name").takeIf { it.isNotBlank() },
            bio = json.optString("note").replace(Regex("<[^>]+>"), "").takeIf { it.isNotBlank() },
            followers = json.optInt("followers_count").takeIf { it >= 0 },
            following = json.optInt("following_count").takeIf { it >= 0 }
        )
    }

    private fun enrichHit(hit: UsernamePlatformHit): UsernamePlatformHit {
        if (hit.followers != null || hit.following != null || hit.extraStats != null) return hit
        return hit
    }

    private fun baseHit(
        platform: UsernamePlatformRegistry.PlatformDef,
        url: String,
        username: String
    ) = UsernamePlatformHit(
        platform = platform.name,
        url = url,
        username = username,
        nsfw = platform.nsfw
    )

    private fun httpGet(url: String, extraHeaders: Map<String, String> = emptyMap()): Pair<Int, String>? {
        return try {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/json,*/*")
            extraHeaders.forEach { (k, v) -> builder.header(k, v) }
            val resp = httpClient.newCall(builder.build()).execute()
            val body = resp.body?.string()?.take(50_000) ?: ""
            val code = resp.code
            resp.close()
            code to body
        } catch (e: Exception) {
            Log.d(TAG, "HTTP GET failed for $url: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "UsernameDiscovery"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
