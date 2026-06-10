package com.twoskoops707.sixdegrees.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigInteger
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * In-app email registration checks (Holehe-style) — no Termux required.
 */
class EmailRegistrationService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false)
        .build(),
    private val maxConcurrent: Int = 6,
    private val perCheckTimeoutMs: Long = 10_000L
) {
    data class RegistrationHit(val service: String, val detail: String)

    suspend fun check(
        email: String,
        onProgress: suspend (SearchProgressEvent) -> Unit = {}
    ): List<RegistrationHit> = withContext(Dispatchers.IO) {
        val normalized = email.trim().lowercase()
        if (!normalized.contains("@") || normalized.length < 6) return@withContext emptyList()

        onProgress(SearchProgressEvent.Checking("Email Registration Scan"))
        val checks = listOf(
            ::checkGravatar,
            ::checkFirefox,
            ::checkSpotify,
            ::checkPinterest,
            ::checkDuolingo,
            ::checkReplit,
            ::checkChessCom,
            ::checkFreelancer,
            ::checkCodepen,
            ::checkDockerHub,
            ::checkAtlassian,
            ::checkAdobe
        )

        val semaphore = Semaphore(maxConcurrent)
        val hits = coroutineScope {
            checks.map { checkFn ->
                async {
                    semaphore.withPermit {
                        try {
                            withTimeout(perCheckTimeoutMs) {
                                checkFn(normalized)?.let { RegistrationHit(it, "Email registered") }
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

        if (hits.isEmpty()) {
            onProgress(SearchProgressEvent.NotFound("Email Registration Scan"))
        } else {
            hits.forEach { hit ->
                onProgress(SearchProgressEvent.Found("holehe/${hit.service}", hit.detail))
            }
            onProgress(
                SearchProgressEvent.Found(
                    "Email Registration Scan",
                    "${hits.size} service(s) recognize this email"
                )
            )
        }
        hits
    }

    private fun md5Hex(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        return BigInteger(1, digest).toString(16).padStart(32, '0')
    }

    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): Pair<Int, String>? = try {
        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> builder.header(k, v) }
        val resp = httpClient.newCall(builder.build()).execute()
        val body = resp.body?.string()?.take(20_000).orEmpty()
        val code = resp.code
        resp.close()
        code to body
    } catch (_: Exception) {
        null
    }

    private fun httpPost(url: String, body: String, contentType: String, headers: Map<String, String> = emptyMap()): Pair<Int, String>? = try {
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(contentType.toMediaType()))
            .header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> builder.header(k, v) }
        val resp = httpClient.newCall(builder.build()).execute()
        val respBody = resp.body?.string()?.take(20_000).orEmpty()
        val code = resp.code
        resp.close()
        code to respBody
    } catch (_: Exception) {
        null
    }

    private fun checkGravatar(email: String): String? {
        val hash = md5Hex(email.trim().lowercase())
        val (code, _) = httpGet("https://www.gravatar.com/avatar/$hash?d=404") ?: return null
        return if (code == 200) "Gravatar" else null
    }

    private fun checkFirefox(email: String): String? {
        val body = """{"email":${jsonString(email)}}"""
        val (code, resp) = httpPost(
            "https://api.accounts.firefox.com/v1/account/status",
            body,
            "application/json",
            mapOf("Accept" to "application/json")
        ) ?: return null
        if (code !in 200..299) return null
        return when {
            resp.contains("\"exists\":true", ignoreCase = true) -> "Firefox"
            resp.contains("\"available\":false", ignoreCase = true) -> "Firefox"
            else -> null
        }
    }

    private fun checkSpotify(email: String): String? {
        val body = """{"account_attempt":{"email":${jsonString(email)}}}"""
        val (code, resp) = httpPost(
            "https://spclient.wg.spotify.com/signup/public/v1/account",
            body,
            "application/json"
        ) ?: return null
        if (code !in 200..299) return null
        return if (resp.contains("email_already_exists", ignoreCase = true) ||
            resp.contains("EMAIL_ALREADY_EXISTS", ignoreCase = true) ||
            resp.contains("account_already_exists", ignoreCase = true)
        ) {
            "Spotify"
        } else null
    }

    private fun checkPinterest(email: String): String? {
        val body = """{"options":{"email":${jsonString(email)}},"context":{}}"""
        val (code, resp) = httpPost(
            "https://www.pinterest.com/resource/EmailExistsResource/get/",
            body,
            "application/json",
            mapOf("X-Requested-With" to "XMLHttpRequest")
        ) ?: return null
        if (code !in 200..299) return null
        return if (resp.contains("\"email_exists\":true", ignoreCase = true) ||
            resp.contains("is_registered\":true", ignoreCase = true)
        ) {
            "Pinterest"
        } else null
    }

    private fun checkDuolingo(email: String): String? {
        val (code, resp) = httpGet(
            "https://www.duolingo.com/2017-06-30/users?email=${URLEncoder.encode(email, "UTF-8")}"
        ) ?: return null
        if (code !in 200..299) return null
        return if (resp.contains("\"users\":[") && !resp.contains("\"users\":[]")) "Duolingo" else null
    }

    private fun checkReplit(email: String): String? {
        val body = """{"email":${jsonString(email)}}"""
        val (code, resp) = httpPost(
            "https://replit.com/data/user/exists",
            body,
            "application/json"
        ) ?: return null
        if (code !in 200..299) return null
        return if (resp.contains("true", ignoreCase = true) && !resp.contains("false")) "Replit" else null
    }

    private fun checkChessCom(email: String): String? {
        val (code, resp) = httpGet(
            "https://www.chess.com/callback/email/available?email=${URLEncoder.encode(email, "UTF-8")}"
        ) ?: return null
        if (code !in 200..299) return null
        return if (resp.contains("\"isAvailable\":false", ignoreCase = true) ||
            resp.contains("is_available\":false", ignoreCase = true)
        ) {
            "Chess.com"
        } else null
    }

    private fun checkFreelancer(email: String): String? {
        val body = """{"email":${jsonString(email)}}"""
        val (code, resp) = httpPost(
            "https://www.freelancer.com/api/users/0.1/users/check?compact=true&new_errors=true&new_pools=true",
            body,
            "application/json"
        ) ?: return null
        if (code !in 200..299) return null
        return if (resp.contains("email_used", ignoreCase = true) ||
            resp.contains("already", ignoreCase = true)
        ) {
            "Freelancer"
        } else null
    }

    private fun checkCodepen(email: String): String? {
        val (code, resp) = httpGet(
            "https://codepen.io/pen/signup/email/available?email=${URLEncoder.encode(email, "UTF-8")}"
        ) ?: return null
        if (code !in 200..299) return null
        return if (resp.contains("false", ignoreCase = true) && !resp.contains("true")) "CodePen" else null
    }

    private fun checkDockerHub(email: String): String? {
        val body = """{"email":${jsonString(email)}}"""
        val (code, resp) = httpPost(
            "https://hub.docker.com/v2/users/signup/",
            body,
            "application/json"
        ) ?: return null
        if (code !in 200..399) return null
        return if (resp.contains("email", ignoreCase = true) &&
            (resp.contains("already", ignoreCase = true) || resp.contains("exists", ignoreCase = true))
        ) {
            "Docker Hub"
        } else null
    }

    private fun checkAtlassian(email: String): String? {
        val body = """{"email":${jsonString(email)}}"""
        val (code, resp) = httpPost(
            "https://id.atlassian.com/rest/check-username",
            body,
            "application/json"
        ) ?: return null
        if (code !in 200..299) return null
        return if (resp.contains("EXISTS", ignoreCase = true) ||
            resp.contains("already", ignoreCase = true)
        ) {
            "Atlassian"
        } else null
    }

    private fun checkAdobe(email: String): String? {
        val body = """{"username":${jsonString(email)},"usernameType":"EMAIL"}"""
        val (code, resp) = httpPost(
            "https://auth.services.adobe.com/signin/v2/users/accounts",
            body,
            "application/json",
            mapOf("Accept" to "application/json")
        ) ?: return null
        if (code !in 200..299) return null
        return if (resp.contains("\"available\":false", ignoreCase = true) ||
            resp.contains("\"exists\":true", ignoreCase = true)
        ) {
            "Adobe"
        } else null
    }

    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
