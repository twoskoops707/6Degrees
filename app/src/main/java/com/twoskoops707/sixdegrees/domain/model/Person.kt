package com.twoskoops707.sixdegrees.domain.model

data class Person(
    val id: String,
    val firstName: String,
    val lastName: String,
    val fullName: String,
    val emailAddress: String?,
    val phoneNumber: String?,
    val dateOfBirth: String?,
    val addresses: List<Address>,
    val employmentHistory: List<Employment>,
    val socialProfiles: List<SocialProfile>,
    val aliases: List<String>,
    val nationalities: List<String>,
    val gender: String?,
    val profileImageUrl: String?
)

data class Address(
    val street: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String,
    val type: String // home, work, previous
)

data class Employment(
    val companyName: String,
    val jobTitle: String,
    val startDate: String?,
    val endDate: String?,
    val isCurrent: Boolean
)

data class SocialProfile(
    val platform: String, // LinkedIn, Facebook, Twitter, etc.
    val username: String,
    val url: String?,
    val followersCount: Int?
)