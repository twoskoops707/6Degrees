package com.twoskoops707.sixdegrees.domain.model

data class CandidateProfile(
    val name: String,
    val age: String,
    val location: String,
    val phones: List<String>,
    val address: String,
    val source: String,
    val confidence: Float,
    val photoUrl: String? = null,
    val relatives: List<String> = emptyList(),
    val profileUrl: String? = null,
    // Additional fields for rich candidate display
    val dob: String? = null,
    val akas: List<String> = emptyList(),
    val email: String? = null,
    val isCompany: Boolean = false,
    val domain: String? = null,
    val industry: String? = null,
    val logoUrl: String? = null,
    val politicalAffiliation: String? = null
)
