package com.twoskoops707.sixdegrees.data.remote.dto.hunterio

import com.squareup.moshi.Json

data class HunterIoDomainSearchResponse(
    @Json(name = "data") val data: HunterIoDomainData?,
    @Json(name = "meta") val meta: HunterIoMeta?
)

data class HunterIoDomainData(
    @Json(name = "domain") val domain: String?,
    @Json(name = "disposable") val disposable: Boolean?,
    @Json(name = "webmail") val webmail: Boolean?,
    @Json(name = "accept_all") val acceptAll: Boolean?,
    @Json(name = "pattern") val pattern: String?,
    @Json(name = "organization") val organization: String?,
    @Json(name = "country") val country: String?,
    @Json(name = "emails") val emails: List<HunterIoEmail>?,
    @Json(name = "linked_domains") val linkedDomains: List<String>?
)

data class HunterIoEmail(
    @Json(name = "value") val value: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "confidence") val confidence: Int?,
    @Json(name = "sources") val sources: List<HunterIoSource>?,
    @Json(name = "first_name") val firstName: String?,
    @Json(name = "last_name") val lastName: String?,
    @Json(name = "position") val position: String?,
    @Json(name = "seniority") val seniority: String?,
    @Json(name = "department") val department: String?,
    @Json(name = "verified") val verified: Boolean?,
    @Json(name = "unreachable_direct") val unreachableDirect: Boolean?
)

data class HunterIoSource(
    @Json(name = "domain") val domain: String?,
    @Json(name = "uri") val uri: String?,
    @Json(name = "extracted_on") val extractedOn: String?,
    @Json(name = "last_seen_on") val lastSeenOn: String?,
    @Json(name = "still_on_page") val stillOnPage: Boolean?
)

data class HunterIoMeta(
    @Json(name = "results") val results: Int?,
    @Json(name = "limit") val limit: Int?,
    @Json(name = "offset") val offset: Int?,
    @Json(name = "search") val search: String?
)

data class HunterIoEmailVerifyResponse(
    @Json(name = "data") val data: HunterIoEmailVerificationData?,
    @Json(name = "meta") val meta: HunterIoVerificationMeta?
)

data class HunterIoEmailVerificationData(
    @Json(name = "email") val email: String?,
    @Json(name = "autocorrect") val autocorrect: String?,
    @Json(name = "deliverability") val deliverability: String?,
    @Json(name = "quality_score") val qualityScore: Int?,
    @Json(name = "is_valid_format") val isValidFormat: Boolean?,
    @Json(name = "is_mx_found") val isMxFound: Boolean?,
    @Json(name = "is_smtp_valid") val isSmtpValid: Boolean?,
    @Json(name = "is_catch_all") val isCatchAll: Boolean?,
    @Json(name = "is_role_account") val isRoleAccount: Boolean?,
    @Json(name = "is_disposable") val isDisposable: Boolean?,
    @Json(name = "is_free") val isFree: Boolean?,
    @Json(name = "result") val result: String?,
    @Json(name = "score") val score: Int?,
    @Json(name = "regexp") val regexp: Boolean?,
    @Json(name = "gibberish") val gibberish: Boolean?,
    @Json(name = "mailbox_full") val mailboxFull: Boolean?,
    @Json(name = "disposable") val disposable: Boolean?,
    @Json(name = "webmail") val webmail: Boolean?,
    @Json(name = "suspect") val suspect: Boolean?,
    @Json(name = "recent_domain") val recentDomain: Boolean?,
    @Json(name = "valid_role") val validRole: Boolean?,
    @Json(name = "sources") val sources: List<HunterIoSource>?
)

data class HunterIoVerificationMeta(
    @Json(name = "params") val params: HunterIoParams?
)

data class HunterIoParams(
    @Json(name = "email") val email: String?
)