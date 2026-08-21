package com.twoskoops707.sixdegrees.data.repository

import android.util.Log
import com.twoskoops707.sixdegrees.domain.model.CandidateProfile
import com.twoskoops707.sixdegrees.domain.model.SocialHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Attaches photo URLs and social hints to candidates during round-1 discovery.
 * Sources: DDG images, Unavatar, LinkedIn/Facebook search links, Gravatar.
 */
class CandidatePhotoEnricher(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    suspend fun enrichAll(
        candidates: List<CandidateProfile>,
        city: String,
        state: String,
        context: String = ""
    ): List<CandidateProfile> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                candidates.map { c ->
                    async { enrichOne(c, city, state, context) }
                }.awaitAll()
            }
        }

    suspend fun enrichOne(
        candidate: CandidateProfile,
        city: String,
        state: String,
        context: String = ""
    ): CandidateProfile {
        if (candidate.isCompany) return candidate
        val loc = listOf(city, state).filter { it.isNotBlank() }.joinToString(" ")
        val ctx = context.trim().takeIf { it.isNotBlank() }.orEmpty()
        val name = candidate.name
        val photos = mutableListOf<String>()
        val socials = mutableListOf<SocialHint>()

        candidate.photoUrl?.takeIf { it.isNotBlank() }?.let { photos.add(it) }
        photos.addAll(candidate.photoUrls)

        candidate.email?.takeIf { it.isNotBlank() }?.let { email ->
            gravatarUrl(email)?.let { photos.add(it) }
        }

        val encodedNameLoc = URLEncoder.encode("$name ${if (loc.isNotBlank()) loc else ""}".trim(), "UTF-8")
        val linkedinSearch = "https://www.linkedin.com/search/results/people/?keywords=$encodedNameLoc"
        val facebookSearch = "https://www.facebook.com/search/people/?q=$encodedNameLoc"
        socials.add(SocialHint("LinkedIn Search", linkedinSearch))
        socials.add(SocialHint("Facebook Search", facebookSearch))

        val quotedName = "\"$name\""
        val ddgResults = ddgSearch("$quotedName ${if (loc.isNotBlank()) loc else ""}${if (ctx.isNotBlank()) " $ctx" else ""} linkedin OR facebook OR instagram")
        for (r in ddgResults) {
            val resultText = "${r.first} ${r.second}"
            if (!matchesCandidateName(resultText, name)) continue
            val url = normalizeUrl(r.third)
            when {
                url.contains("linkedin.com/in/") -> {
                    val photo = extractOgImage(url) ?: unavatarUrl("linkedin", linkedinUsername(url))
                    socials.add(SocialHint("LinkedIn", url, photo))
                    photo?.let { photos.add(it) }
                }
                url.contains("facebook.com/") && !url.contains("/groups/") && !url.contains("/search/") -> {
                    val photo = extractOgImage(url)
                    socials.add(SocialHint("Facebook", url, photo))
                    photo?.let { photos.add(it) }
                }
                url.contains("instagram.com/") -> {
                    val photo = extractOgImage(url) ?: unavatarUrl("instagram", instagramUsername(url))
                    socials.add(SocialHint("Instagram", url, photo))
                    photo?.let { photos.add(it) }
                }
            }
        }

        val imageResults = ddgImageSearch("$quotedName ${if (loc.isNotBlank()) loc else ""}${if (ctx.isNotBlank()) " $ctx" else ""} photo portrait")
        photos.addAll(imageResults.take(4))

        val distinctPhotos = photos.filter { it.startsWith("http") }.distinct().take(8)
        val primary = distinctPhotos.firstOrNull() ?: candidate.photoUrl
        val linkedinProfile = socials.firstOrNull { it.platform == "LinkedIn" }?.url ?: linkedinSearch
        val facebookProfile = socials.firstOrNull { it.platform == "Facebook" }?.url ?: facebookSearch
        val instagramProfile = socials.firstOrNull { it.platform == "Instagram" }?.url

        return candidate.copy(
            photoUrl = primary,
            photoUrls = distinctPhotos,
            socialHints = socials.distinctBy { it.url }.take(6),
            linkedinUrl = linkedinProfile,
            facebookUrl = facebookProfile,
            instagramUrl = instagramProfile ?: candidate.instagramUrl,
            profileUrl = candidate.profileUrl ?: socials.firstOrNull { it.platform == "LinkedIn" }?.url
        )
    }

    private fun ddgSearch(query: String): List<Triple<String, String, String>> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://html.duckduckgo.com/html/?q=$encoded")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html")
                .build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank()) return emptyList()
            val doc = Jsoup.parse(body)
            doc.select(".result:not(.result--more), .result--web").take(10).mapNotNull { el ->
                val title = el.selectFirst(".result__a, .result__title a")?.text()?.trim() ?: return@mapNotNull null
                val snippet = el.selectFirst(".result__snippet, .result-snippet")?.text()?.trim() ?: ""
                val href = el.selectFirst("a.result__a, .result__title a")?.attr("href") ?: ""
                val url = extractDdgRedirect(href) ?: el.selectFirst(".result__url, .result-url")?.text()?.trim() ?: ""
                if (title.isBlank()) null else Triple(title, snippet, url)
            }
        } catch (e: Exception) {
            Log.w(TAG, "DDG web search failed for '$query': ${e.message}")
            emptyList()
        }
    }

    private fun ddgImageSearch(query: String): List<String> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://html.duckduckgo.com/html/?q=$encoded&iax=images&ia=images")
                .header("User-Agent", USER_AGENT)
                .build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank()) return emptyList()
            val doc = Jsoup.parse(body)
            val urls = mutableListOf<String>()
            doc.select("img[src]").forEach { img ->
                val src = img.attr("src")
                if (src.startsWith("http") && !src.contains("duckduckgo.com") && !src.contains("icon")) {
                    urls.add(src)
                }
            }
            doc.select("a[href]").forEach { a ->
                val href = a.attr("href")
                if (href.contains("external-content") || href.contains("iu=")) {
                    Regex("""iu=([^&]+)""").find(href)?.groupValues?.get(1)
                        ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                        ?.takeIf { it.startsWith("http") }
                        ?.let { urls.add(it) }
                }
            }
            urls.filter { !it.contains("placeholder") && !it.contains("default_avatar") }.distinct()
        } catch (e: Exception) {
            Log.w(TAG, "DDG image search failed for '$query': ${e.message}")
            emptyList()
        }
    }

    private fun extractOgImage(url: String): String? {
        return try {
            val fullUrl = if (!url.startsWith("http")) "https://$url" else url
            val req = Request.Builder().url(fullUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html")
                .build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.isBlank()) return null
            val doc = Jsoup.parse(body)
            (doc.selectFirst("meta[property=og:image]")?.attr("content")
                ?: doc.selectFirst("meta[name=twitter:image]")?.attr("content"))
                ?.takeIf { it.startsWith("http") && !it.contains("placeholder") && !it.contains("default") }
        } catch (e: Exception) {
            Log.w(TAG, "OG image fetch failed for '$url': ${e.message}")
            null
        }
    }

    private fun gravatarUrl(email: String): String? {
        val hash = MessageDigest.getInstance("MD5")
            .digest(email.trim().lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "https://www.gravatar.com/avatar/$hash?s=200&d=404"
    }

    private fun unavatarUrl(platform: String, username: String?): String? {
        if (username.isNullOrBlank()) return null
        val url = "https://unavatar.io/$platform/$username"
        return try {
            val req = Request.Builder().url(url).head().header("User-Agent", USER_AGENT).build()
            val resp = httpClient.newCall(req).execute()
            val ok = resp.code == 200 || resp.code == 302
            resp.close()
            if (ok) url else null
        } catch (e: Exception) {
            Log.w(TAG, "Unavatar HEAD failed for $platform/$username: ${e.message}")
            null
        }
    }

    private fun matchesCandidateName(text: String, candidateName: String): Boolean {
        val tokens = candidateName.trim().lowercase().split(Regex("\\s+")).filter { it.length > 1 }
        if (tokens.isEmpty()) return true
        val lower = text.lowercase()
        val required = if (tokens.size >= 2) 2 else 1
        return tokens.count { lower.contains(it) } >= required
    }

    private fun linkedinUsername(url: String): String? =
        Regex("""linkedin\.com/in/([^/?#]+)""").find(url)?.groupValues?.get(1)

    private fun instagramUsername(url: String): String? =
        Regex("""instagram\.com/([^/?#]+)""").find(url)?.groupValues?.get(1)

    private fun normalizeUrl(raw: String): String {
        if (raw.startsWith("http")) return raw
        return extractDdgRedirect(raw) ?: raw
    }

    private fun extractDdgRedirect(href: String): String? {
        if (!href.contains("uddg=")) return if (href.startsWith("http")) href else null
        return Regex("""uddg=([^&]+)""").find(href)?.groupValues?.get(1)
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    }

    companion object {
        private const val TAG = "CandidatePhotoEnricher"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
