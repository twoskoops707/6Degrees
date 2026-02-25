package com.twoskoops707.sixdegrees.data.remote.dto.haveibeenpwned

import com.squareup.moshi.Json

data class HibpBreach(
    @Json(name = "Name") val name: String?,
    @Json(name = "Title") val title: String?,
    @Json(name = "Domain") val domain: String?,
    @Json(name = "BreachDate") val breachDate: String?,
    @Json(name = "AddedDate") val addedDate: String?,
    @Json(name = "ModifiedDate") val modifiedDate: String?,
    @Json(name = "PwnCount") val pwnCount: Long?,
    @Json(name = "Description") val description: String?,
    @Json(name = "LogoPath") val logoPath: String?,
    @Json(name = "DataClasses") val dataClasses: List<String>?,
    @Json(name = "IsVerified") val isVerified: Boolean?,
    @Json(name = "IsFabricated") val isFabricated: Boolean?,
    @Json(name = "IsSensitive") val isSensitive: Boolean?,
    @Json(name = "IsRetired") val isRetired: Boolean?,
    @Json(name = "IsSpamList") val isSpamList: Boolean?,
    @Json(name = "IsMalware") val isMalware: Boolean?
)

data class HibpDataClass(
    @Json(name = "DataClass") val dataClass: String?
)

data class HibpPaste(
    @Json(name = "Source") val source: String?,
    @Json(name = "Id") val id: String?,
    @Json(name = "Title") val title: String?,
    @Json(name = "Date") val date: String?,
    @Json(name = "EmailCount") val emailCount: Long?
)