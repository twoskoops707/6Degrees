package com.example.a6degrees.data.remote.dto.clearbit

import com.squareup.moshi.Json

data class ClearbitPersonResponse(
    @Json(name = "person") val person: ClearbitPerson?,
    @Json(name = "company") val company: ClearbitCompany?
)

data class ClearbitPerson(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: ClearbitName?,
    @Json(name = "email") val email: String?,
    @Json(name = "location") val location: String?,
    @Json(name = "bio") val bio: String?,
    @Json(name = "site") val site: String?,
    @Json(name = "avatar") val avatar: String?,
    @Json(name = "employment") val employment: ClearbitEmployment?,
    @Json(name = "facebook") val facebook: ClearbitSocialProfile?,
    @Json(name = "twitter") val twitter: ClearbitSocialProfile?,
    @Json(name = "linkedin") val linkedin: ClearbitSocialProfile?,
    @Json(name = "github") val github: ClearbitSocialProfile?,
    @Json(name = "angellist") val angellist: ClearbitSocialProfile?,
    @Json(name = "klout") val klout: ClearbitSocialProfile?,
    @Json(name = "gravatar") val gravatar: ClearbitSocialProfile?,
    @Json(name = "fuzzy") val fuzzy: Boolean?,
    @Json(name = "emailProvider") val emailProvider: Boolean?
)

data class ClearbitName(
    @Json(name = "fullName") val fullName: String?,
    @Json(name = "givenName") val givenName: String?,
    @Json(name = "familyName") val familyName: String?
)

data class ClearbitEmployment(
    @Json(name = "name") val name: String?,
    @Json(name = "title") val title: String?,
    @Json(name = "domain") val domain: String?,
    @Json(name = "role") val role: String?,
    @Json(name = "seniority") val seniority: String?
)

data class ClearbitSocialProfile(
    @Json(name = "handle") val handle: String?,
    @Json(name = "id") val id: String?,
    @Json(name = "bio") val bio: String?,
    @Json(name = "followers") val followers: Int?,
    @Json(name = "following") val following: Int?,
    @Json(name = "location") val location: String?,
    @Json(name = "site") val site: String?,
    @Json(name = "avatar") val avatar: String?,
    @Json(name = "company") val company: String?,
    @Json(name = "blog") val blog: String?
)

data class ClearbitCompany(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "legalName") val legalName: String?,
    @Json(name = "domain") val domain: String?,
    @Json(name = "domainAliases") val domainAliases: List<String>?,
    @Json(name = "site") val site: ClearbitSite?,
    @Json(name = "category") val category: ClearbitCategory?,
    @Json(name = "tags") val tags: List<String>?,
    @Json(name = "description") val description: String?,
    @Json(name = "foundedYear") val foundedYear: Int?,
    @Json(name = "location") val location: String?,
    @Json(name = "timeZone") val timeZone: String?,
    @Json(name = "utcOffset") val utcOffset: Int?,
    @Json(name = "geo") val geo: ClearbitGeo?,
    @Json(name = "logo") val logo: String?,
    @Json(name = "facebook") val facebook: ClearbitSocialMedia?,
    @Json(name = "linkedin") val linkedin: ClearbitSocialMedia?,
    @Json(name = "twitter") val twitter: ClearbitSocialMedia?,
    @Json(name = "crunchbase") val crunchbase: ClearbitSocialMedia?,
    @Json(name = "employees") val employees: Int?,
    @Json(name = "marketCap") val marketCap: Long?,
    @Json(name = "raised") val raised: Long?,
    @Json(name = "annualRevenue") val annualRevenue: Long?,
    @Json(name = "fiscalYearEnd") val fiscalYearEnd: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "ticker") val ticker: String?,
    @Json(name = "identifiers") val identifiers: ClearbitIdentifiers?,
    @Json(name = "industry") val industry: String?,
    @Json(name = "sic") val sic: String?,
    @Json(name = "naics") val naics: String?,
    @Json(name = "metrics") val metrics: ClearbitMetrics?,
    @Json(name = "tech") val tech: List<String>?,
    @Json(name = "techCategories") val techCategories: List<String>?,
    @Json(name = "parent") val parent: ClearbitParentCompany?
)

data class ClearbitSite(
    @Json(name = "url") val url: String?,
    @Json(name = "title") val title: String?,
    @Json(name = "h1") val h1: String?,
    @Json(name = "metaDescription") val metaDescription: String?,
    @Json(name = "metaAuthor") val metaAuthor: String?,
    @Json(name = "phoneNumbers") val phoneNumbers: List<String>?,
    @Json(name = "emailAddresses") val emailAddresses: List<String>?
)

data class ClearbitCategory(
    @Json(name = "sector") val sector: String?,
    @Json(name = "industryGroup") val industryGroup: String?,
    @Json(name = "industry") val industry: String?,
    @Json(name = "subIndustry") val subIndustry: String?
)

data class ClearbitGeo(
    @Json(name = "streetNumber") val streetNumber: String?,
    @Json(name = "streetName") val streetName: String?,
    @Json(name = "subPremise") val subPremise: String?,
    @Json(name = "streetAddress") val streetAddress: String?,
    @Json(name = "city") val city: String?,
    @Json(name = "state") val state: String?,
    @Json(name = "stateCode") val stateCode: String?,
    @Json(name = "postalCode") val postalCode: String?,
    @Json(name = "country") val country: String?,
    @Json(name = "countryCode") val countryCode: String?,
    @Json(name = "lat") val lat: Double?,
    @Json(name = "lng") val lng: Double?
)

data class ClearbitSocialMedia(
    @Json(name = "handle") val handle: String?,
    @Json(name = "url") val url: String?,
    @Json(name = "followers") val followers: Int?,
    @Json(name = "following") val following: Int?,
    @Json(name = "posts") val posts: Int?
)

data class ClearbitIdentifiers(
    @Json(name = "usSicV4") val usSicV4: String?,
    @Json(name = "naicsCode") val naicsCode: String?,
    @Json(name = "isOpenCorporates") val isOpenCorporates: Boolean?,
    @Json(name = "isDnB") val isDnB: Boolean?
)

data class ClearbitMetrics(
    @Json(name = "alexaUsRank") val alexaUsRank: Int?,
    @Json(name = "alexaGlobalRank") val alexaGlobalRank: Int?,
    @Json(name = "trafficRank") val trafficRank: ClearbitTrafficRank?,
    @Json(name = "employees") val employees: Int?,
    @Json(name = "employeesRange") val employeesRange: String?,
    @Json(name = "marketCap") val marketCap: Long?,
    @Json(name = "raised") val raised: Long?,
    @Json(name = "annualRevenue") val annualRevenue: Long?,
    @Json(name = "estimatedAnnualRevenue") val estimatedAnnualRevenue: String?,
    @Json(name = "fiscalYearEnd") val fiscalYearEnd: String?,
    @Json(name = "latestFunding") val latestFunding: ClearbitFunding?
)

data class ClearbitTrafficRank(
    @Json(name = "rank") val rank: Int?,
    @Json(name = "country") val country: String?,
    @Json(name = "countryCode") val countryCode: String?
)

data class ClearbitFunding(
    @Json(name = "amount") val amount: Long?,
    @Json(name = "round") val round: String?,
    @Json(name = "year") val year: Int?,
    @Json(name = "date") val date: String?,
    @Json(name = "investors") val investors: List<ClearbitInvestor>?
)

data class ClearbitInvestor(
    @Json(name = "name") val name: String?,
    @Json(name = "type") val type: String?
)

data class ClearbitParentCompany(
    @Json(name = "name") val name: String?,
    @Json(name = "domain") val domain: String?
)