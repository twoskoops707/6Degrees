package com.twoskoops707.sixdegrees.data.remote.dto.pipl

import com.squareup.moshi.Json

data class PiplSearchResponse(
    @Json(name = "@http_status_code") val httpStatusCode: Int?,
    @Json(name = "warning") val warning: String?,
    @Json(name = "query") val query: PiplQuery?,
    @Json(name = "match_requirements") val matchRequirements: String?,
    @Json(name = "visible_sources") val visibleSources: Int?,
    @Json(name = "available_sources") val availableSources: Int?,
    @Json(name = "estimated_results") val estimatedResults: Int?,
    @Json(name = "person") val person: PiplPerson?,
    @Json(name = "sources") val sources: List<PiplSource>?,
    @Json(name = "related_persons") val relatedPersons: List<PiplRelatedPerson>?,
    @Json(name = "@search_pointer") val searchPointer: String?
)

data class PiplQuery(
    @Json(name = "first_name") val firstName: String?,
    @Json(name = "last_name") val lastName: String?,
    @Json(name = "email") val email: String?,
    @Json(name = "phone") val phone: String?
)

data class PiplPerson(
    @Json(name = "id") val id: String?,
    @Json(name = "url") val url: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "names") val names: List<PiplName>?,
    @Json(name = "addresses") val addresses: List<PiplAddress>?,
    @Json(name = "phones") val phones: List<PiplPhone>?,
    @Json(name = "emails") val emails: List<PiplEmail>?,
    @Json(name = "jobs") val jobs: List<PiplJob>?,
    @Json(name = "educations") val educations: List<PiplEducation>?,
    @Json(name = "images") val images: List<PiplImage>?,
    @Json(name = "languages") val languages: List<PiplLanguage>?,
    @Json(name = "ethnicities") val ethnicities: List<PiplEthnicity>?,
    @Json(name = "origin_countries") val originCountries: List<PiplOriginCountry>?,
    @Json(name = "urls") val urls: List<PiplUrl>?,
    @Json(name = "relationships") val relationships: List<PiplRelationship>?,
    @Json(name = "usernames") val usernames: List<PiplUsername>?,
    @Json(name = "user_ids") val userIds: List<PiplUserId>?,
    @Json(name = "dob") val dob: String?,
    @Json(name = "gender") val gender: String?
)

data class PiplName(
    @Json(name = "first") val first: String?,
    @Json(name = "middle") val middle: String?,
    @Json(name = "last") val last: String?,
    @Json(name = "display") val display: String?,
    @Json(name = "prefix") val prefix: String?,
    @Json(name = "suffix") val suffix: String?
)

data class PiplAddress(
    @Json(name = "country") val country: String?,
    @Json(name = "state") val state: String?,
    @Json(name = "city") val city: String?,
    @Json(name = "street") val street: String?,
    @Json(name = "house") val house: String?,
    @Json(name = "zip_code") val zipCode: String?,
    @Json(name = "raw") val raw: String?,
    @Json(name = "display") val display: String?
)

data class PiplPhone(
    @Json(name = "number") val number: String?,
    @Json(name = "display") val display: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "country") val country: String?
)

data class PiplEmail(
    @Json(name = "address") val address: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "disposable") val disposable: Boolean?,
    @Json(name = "webmail") val webmail: Boolean?
)

data class PiplJob(
    @Json(name = "title") val title: String?,
    @Json(name = "organization") val organization: String?,
    @Json(name = "industry") val industry: String?,
    @Json(name = "date_range") val dateRange: PiplDateRange?,
    @Json(name = "display") val display: String?
)

data class PiplDateRange(
    @Json(name = "start") val start: String?,
    @Json(name = "end") val end: String?
)

data class PiplEducation(
    @Json(name = "degree") val degree: String?,
    @Json(name = "school") val school: String?,
    @Json(name = "date_range") val dateRange: PiplDateRange?,
    @Json(name = "display") val display: String?
)

data class PiplImage(
    @Json(name = "url") val url: String?,
    @Json(name = "thumbnail_token") val thumbnailToken: String?
)

data class PiplLanguage(
    @Json(name = "language") val language: String?,
    @Json(name = "region") val region: String?,
    @Json(name = "display") val display: String?
)

data class PiplEthnicity(
    @Json(name = "content") val content: String?
)

data class PiplOriginCountry(
    @Json(name = "content") val content: String?
)

data class PiplUrl(
    @Json(name = "url") val url: String?,
    @Json(name = "domain") val domain: String?,
    @Json(name = "category") val category: String?,
    @Json(name = "last_reachable") val lastReachable: String?
)

data class PiplRelationship(
    @Json(name = "type") val type: String?,
    @Json(name = "subtype") val subtype: String?,
    @Json(name = "names") val names: List<PiplName>?,
    @Json(name = "emails") val emails: List<PiplEmail>?,
    @Json(name = "phones") val phones: List<PiplPhone>?,
    @Json(name = "addresses") val addresses: List<PiplAddress>?,
    @Json(name = "dates_of_birth") val datesOfBirth: List<String>?,
    @Json(name = "usernames") val usernames: List<PiplUsername>?,
    @Json(name = "user_ids") val userIds: List<PiplUserId>?,
    @Json(name = "relationships") val relationships: List<PiplRelationship>?
)

data class PiplUsername(
    @Json(name = "content") val content: String?
)

data class PiplUserId(
    @Json(name = "content") val content: String?,
    @Json(name = "name") val name: String?
)

data class PiplSource(
    @Json(name = "@id") val id: String?,
    @Json(name = "@domain") val domain: String?,
    @Json(name = "@name") val name: String?,
    @Json(name = "@person_id") val personId: String?,
    @Json(name = "url") val url: String?,
    @Json(name = "category") val category: String?,
    @Json(name = "source_id") val sourceId: String?
)

data class PiplRelatedPerson(
    @Json(name = "person") val person: PiplPerson?,
    @Json(name = "relationship") val relationship: String?
)