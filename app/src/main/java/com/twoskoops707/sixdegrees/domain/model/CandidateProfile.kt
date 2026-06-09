package com.twoskoops707.sixdegrees.domain.model

import java.util.UUID

data class CandidateProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val age: String,
    val location: String,
    val phones: List<String>,
    val address: String,
    val source: String,
    val confidence: Float,
    val photoUrl: String? = null,
    val photoUrls: List<String> = emptyList(),
    val socialHints: List<SocialHint> = emptyList(),
    val relatives: List<String> = emptyList(),
    val profileUrl: String? = null,
    val dob: String? = null,
    val akas: List<String> = emptyList(),
    val email: String? = null,
    val employer: String? = null,
    val linkedinUrl: String? = null,
    val facebookUrl: String? = null,
    val instagramUrl: String? = null,
    val isCompany: Boolean = false,
    val domain: String? = null,
    val industry: String? = null,
    val logoUrl: String? = null,
    val politicalAffiliation: String? = null
) {
    /** All available photos — prefers [photoUrls], falls back to singular [photoUrl]. */
    fun allPhotoUrls(): List<String> =
        photoUrls.filter { it.isNotBlank() }.ifEmpty { listOfNotNull(photoUrl?.takeIf { it.isNotBlank() }) }

    /** Platform → URL map for UI binding. */
    val socialLinks: Map<String, String>
        get() = buildMap {
            socialHints.forEach { hint ->
                if (hint.url.isNotBlank()) put(hint.platform, hint.url)
            }
            linkedinUrl?.takeIf { it.isNotBlank() }?.let { put("LinkedIn", it) }
            facebookUrl?.takeIf { it.isNotBlank() }?.let { put("Facebook", it) }
            instagramUrl?.takeIf { it.isNotBlank() }?.let { put("Instagram", it) }
        }

    fun employerSnippet(): String? =
        employer?.takeIf { it.isNotBlank() }
            ?: industry?.takeIf { it.isNotBlank() }
            ?: domain?.takeIf { it.isNotBlank() }
}
