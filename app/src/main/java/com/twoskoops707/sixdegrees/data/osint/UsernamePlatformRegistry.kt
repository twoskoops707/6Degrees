package com.twoskoops707.sixdegrees.data.osint

/**
 * Platform definitions for in-app username existence checks (no API keys required).
 */
object UsernamePlatformRegistry {

    enum class CheckMethod {
        HTTP_STATUS,
        BODY_ABSENT_MARKER,
        BODY_PRESENT_MARKER
    }

    data class PlatformDef(
        val name: String,
        val urlTemplate: String,
        val checkUrlTemplate: String = urlTemplate,
        val method: CheckMethod = CheckMethod.HTTP_STATUS,
        val notFoundStatus: Set<Int> = setOf(404),
        val absentMarkers: List<String> = emptyList(),
        val presentMarkers: List<String> = emptyList(),
        val nsfw: Boolean = false,
        val enrichable: Boolean = false
    )

    val PLATFORMS: List<PlatformDef> = listOf(
        PlatformDef("GitHub", "https://github.com/{u}", enrichable = true),
        PlatformDef("Reddit", "https://www.reddit.com/user/{u}", enrichable = true),
        PlatformDef("Twitter/X", "https://x.com/{u}"),
        PlatformDef("Instagram", "https://www.instagram.com/{u}/",
            absentMarkers = listOf("Sorry, this page isn't available", "Page Not Found")),
        PlatformDef("TikTok", "https://www.tiktok.com/@{u}",
            absentMarkers = listOf("Couldn't find this account", "statusCode\":10202")),
        PlatformDef("YouTube", "https://www.youtube.com/@{u}",
            absentMarkers = listOf("This page isn't available", "404 Not Found")),
        PlatformDef("Twitch", "https://www.twitch.tv/{u}",
            absentMarkers = listOf("Sorry. Unless you've got a time machine")),
        PlatformDef("Pinterest", "https://www.pinterest.com/{u}/"),
        PlatformDef("LinkedIn", "https://www.linkedin.com/in/{u}",
            absentMarkers = listOf("Page not found", "This profile is not available")),
        PlatformDef("Steam", "https://steamcommunity.com/id/{u}",
            absentMarkers = listOf("The specified profile could not be found")),
        PlatformDef("Spotify", "https://open.spotify.com/user/{u}",
            absentMarkers = listOf("Page not found")),
        PlatformDef("SoundCloud", "https://soundcloud.com/{u}",
            absentMarkers = listOf("We can't find that user")),
        PlatformDef("Medium", "https://medium.com/@{u}",
            absentMarkers = listOf("404", "Out of nothing, something")),
        PlatformDef("DeviantArt", "https://www.deviantart.com/{u}"),
        PlatformDef("Tumblr", "https://{u}.tumblr.com/",
            absentMarkers = listOf("There's nothing here.", "Not Found")),
        PlatformDef("Flickr", "https://www.flickr.com/people/{u}/"),
        PlatformDef("Patreon", "https://www.patreon.com/{u}",
            absentMarkers = listOf("Couldn't find that page")),
        PlatformDef("Venmo", "https://venmo.com/{u}",
            absentMarkers = listOf("Page Not Found", "404")),
        PlatformDef("Cash App", "https://cash.app/{u}",
            absentMarkers = listOf("404", "not found")),
        PlatformDef("Linktree", "https://linktr.ee/{u}",
            absentMarkers = listOf("Page not found", "404")),
        PlatformDef("About.me", "https://about.me/{u}"),
        PlatformDef("Behance", "https://www.behance.net/{u}",
            absentMarkers = listOf("User Not Found", "404")),
        PlatformDef("Dribbble", "https://dribbble.com/{u}",
            absentMarkers = listOf("404", "doesn't exist")),
        PlatformDef("CodePen", "https://codepen.io/{u}/"),
        PlatformDef("Replit", "https://replit.com/@{u}",
            absentMarkers = listOf("404", "Page not found")),
        PlatformDef("ProductHunt", "https://www.producthunt.com/@{u}",
            absentMarkers = listOf("404", "Page not found")),
        PlatformDef("Keybase", "https://keybase.io/{u}", enrichable = true),
        PlatformDef("HackerNews", "https://news.ycombinator.com/user?id={u}", enrichable = true),
        PlatformDef("Dev.to", "https://dev.to/{u}", enrichable = true),
        PlatformDef("GitLab", "https://gitlab.com/{u}", enrichable = true),
        PlatformDef("Chess.com", "https://www.chess.com/member/{u}", enrichable = true),
        PlatformDef("Lichess", "https://lichess.org/@/{u}",
            absentMarkers = listOf("404", "Page not found")),
        PlatformDef("Kaggle", "https://www.kaggle.com/{u}",
            absentMarkers = listOf("404", "Page Not Found")),
        PlatformDef("LeetCode", "https://leetcode.com/{u}/",
            absentMarkers = listOf("404", "User not found")),
        PlatformDef("Codeforces", "https://codeforces.com/profile/{u}",
            absentMarkers = listOf("User not found")),
        PlatformDef("Letterboxd", "https://letterboxd.com/{u}/"),
        PlatformDef("Last.fm", "https://www.last.fm/user/{u}"),
        PlatformDef("ArtStation", "https://www.artstation.com/{u}",
            absentMarkers = listOf("404", "Page not found")),
        PlatformDef("Unsplash", "https://unsplash.com/@{u}",
            absentMarkers = listOf("404", "Page not found")),
        PlatformDef("Mixcloud", "https://www.mixcloud.com/{u}/"),
        PlatformDef("Bandcamp", "https://bandcamp.com/{u}",
            absentMarkers = listOf("404", "not found")),
        PlatformDef("Mastodon", "https://mastodon.social/@{u}", enrichable = true),
        PlatformDef("Bluesky", "https://bsky.app/profile/{u}.bsky.social",
            absentMarkers = listOf("Profile not found", "404")),
        PlatformDef("Threads", "https://www.threads.net/@{u}",
            absentMarkers = listOf("Sorry, this page isn't available")),
        PlatformDef("Substack", "https://{u}.substack.com/",
            absentMarkers = listOf("404", "Page not found")),
        PlatformDef("Ko-fi", "https://ko-fi.com/{u}",
            absentMarkers = listOf("404", "Page not found")),
        PlatformDef("Duolingo", "https://www.duolingo.com/profile/{u}",
            absentMarkers = listOf("404", "User not found")),
        PlatformDef("Wattpad", "https://www.wattpad.com/user/{u}",
            absentMarkers = listOf("404", "User not found")),
        PlatformDef("Etsy", "https://www.etsy.com/people/{u}",
            absentMarkers = listOf("404", "Page not found")),
        PlatformDef("GoodReads", "https://www.goodreads.com/{u}",
            absentMarkers = listOf("404", "Page not found")),
        PlatformDef("Exercism", "https://exercism.org/profiles/{u}",
            absentMarkers = listOf("404", "Profile not found")),
        PlatformDef("Gravatar", "https://en.gravatar.com/{u}",
            checkUrlTemplate = "https://en.gravatar.com/{u}.json",
            method = CheckMethod.BODY_PRESENT_MARKER,
            presentMarkers = listOf("\"entry\"")),
        PlatformDef("Angel.co", "https://angel.co/u/{u}",
            absentMarkers = listOf("404", "Page not found")),
        PlatformDef("VK", "https://vk.com/{u}",
            absentMarkers = listOf("error404", "Page not found")),
        PlatformDef("Telegram", "https://t.me/{u}",
            absentMarkers = listOf("If you have <strong>Telegram</strong>, you can contact")),
        PlatformDef("OnlyFans", "https://onlyfans.com/{u}", nsfw = true,
            absentMarkers = listOf("404", "Page Not Found")),
        PlatformDef("Tinder", "https://tinder.com/@{u}",
            absentMarkers = listOf("404", "not found"), nsfw = true)
    )

    fun buildUrl(template: String, username: String): String =
        template.replace("{u}", username.trim())

    /** Extract a handle from common social profile URLs. */
    fun extractHandle(url: String): String? {
        val u = url.trim().removeSuffix("/")
        val patterns = listOf(
            Regex("""(?:github\.com)/([^/?#]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:reddit\.com)/user/([^/?#]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:twitter\.com|x\.com)/([^/?#]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:instagram\.com)/([^/?#]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:tiktok\.com)/@([^/?#]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:youtube\.com)/@([^/?#]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:twitch\.tv)/([^/?#]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:linkedin\.com)/in/([^/?#]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:pinterest\.com)/([^/?#]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:medium\.com)/@([^/?#]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:dev\.to)/([^/?#]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:keybase\.io)/([^/?#]+)""", RegexOption.IGNORE_CASE)
        )
        for (re in patterns) {
            re.find(u)?.groupValues?.getOrNull(1)?.let { handle ->
                if (handle.isNotBlank() && handle !in setOf("search", "login", "signup", "about", "help")) {
                    return handle
                }
            }
        }
        return null
    }

    /** Derive likely username candidates from an email local-part. */
    fun candidatesFromEmail(email: String): List<String> {
        val local = email.substringBefore("@").trim().lowercase()
        if (local.isBlank()) return emptyList()
        val base = local.replace(Regex("[^a-z0-9._-]"), "")
        if (base.length < 3) return emptyList()
        val out = linkedSetOf(base)
        base.split('.', '-', '_').filter { it.length >= 3 }.forEach { out.add(it) }
        return out.toList().take(3)
    }
}
