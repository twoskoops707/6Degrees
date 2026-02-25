package com.twoskoops707.sixdegrees.data.remote.dto.builtwith

import com.squareup.moshi.Json

data class BuiltWithResponse(
    @Json(name = "Errors") val errors: List<String>?,
    @Json(name = "Results") val results: List<BuiltWithResult>?
)

data class BuiltWithResult(
    @Json(name = "Result") val result: BuiltWithDomainResult?
)

data class BuiltWithDomainResult(
    @Json(name = "Domain") val domain: String?,
    @Json(name = "Url") val url: String?,
    @Json(name = "SubDomain") val subDomain: String?,
    @Json(name = "Favicon") val favicon: String?,
    @Json(name = "Categories") val categories: List<BuiltWithCategory>?,
    @Json(name = "Technologies") val technologies: List<BuiltWithTechnology>?,
    @Json(name = "Headers") val headers: Map<String, String>?,
    @Json(name = "Meta") val meta: BuiltWithMeta?,
    @Json(name = "DNS") val dns: BuiltWithDns?,
    @Json(name = "Scripts") val scripts: List<String>?,
    @Json(name = "Redirect") val redirect: String?
)

data class BuiltWithCategory(
    @Json(name = "Name") val name: String?,
    @Json(name = "Link") val link: String?
)

data class BuiltWithTechnology(
    @Json(name = "Name") val name: String?,
    @Json(name = "Description") val description: String?,
    @Json(name = "Link") val link: String?,
    @Json(name = "Tag") val tag: String?,
    @Json(name = "FirstDetected") val firstDetected: Long?,
    @Json(name = "LastDetected") val lastDetected: Long?,
    @Json(name = "Stats") val stats: BuiltWithStats?
)

data class BuiltWithStats(
    @Json(name = "AlexaRank") val alexaRank: Int?,
    @Json(name = "SecurityScore") val securityScore: Int?
)

data class BuiltWithMeta(
    @Json(name = "Title") val title: String?,
    @Json(name = "Description") val description: String?,
    @Json(name = "Keywords") val keywords: String?
)

data class BuiltWithDns(
    @Json(name = "A") val a: List<String>?,
    @Json(name = "MX") val mx: List<String>?,
    @Json(name = "NS") val ns: List<String>?,
    @Json(name = "TXT") val txt: List<String>?,
    @Json(name = "AAAA") val aaaa: List<String>?,
    @Json(name = "CNAME") val cname: List<String>?
)