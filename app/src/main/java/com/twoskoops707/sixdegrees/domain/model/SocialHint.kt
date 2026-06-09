package com.twoskoops707.sixdegrees.domain.model

/**
 * Social platform link surfaced during candidate discovery (profile or search URL).
 */
data class SocialHint(
    val platform: String,
    val url: String,
    val photoUrl: String? = null
)
