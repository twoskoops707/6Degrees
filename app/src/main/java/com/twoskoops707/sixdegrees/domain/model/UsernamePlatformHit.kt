package com.twoskoops707.sixdegrees.domain.model

/**
 * A username confirmed on a specific platform, optionally enriched with public stats.
 */
data class UsernamePlatformHit(
    val platform: String,
    val url: String,
    val username: String,
    val nsfw: Boolean = false,
    val followers: Int? = null,
    val following: Int? = null,
    val friends: Int? = null,
    val extraStats: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val location: String? = null
) {
    fun toSocialProfile(): SocialProfile = SocialProfile(
        platform = platform,
        username = username,
        url = url,
        followersCount = followers,
        followingCount = following,
        friendsCount = friends,
        statsLabel = formatStats()
    )

    fun formatStats(): String? {
        val parts = mutableListOf<String>()
        followers?.let { parts.add("Followers: $it") }
        following?.let { parts.add("Following: $it") }
        friends?.let { parts.add("Friends: $it") }
        extraStats?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("  ")
    }

    fun foundUrlLine(): String {
        val prefix = if (nsfw) "⚠NSFW:" else ""
        return "$prefix$platform: $url"
    }
}
