package com.twoskoops707.sixdegrees.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface EmailRepService {
    @GET("{email}")
    suspend fun getReputation(
        @Path("email") email: String,
        @Header("Key") key: String = ""
    ): Response<EmailRepResponse>
}

@JsonClass(generateAdapter = false)
data class EmailRepResponse(
    val email: String? = null,
    val reputation: String? = null,
    @Json(name = "suspicious") val suspicious: Boolean? = null,
    val references: Int? = null,
    val details: EmailRepDetails? = null
)

@JsonClass(generateAdapter = false)
data class EmailRepDetails(
    @Json(name = "blacklisted") val blacklisted: Boolean? = null,
    @Json(name = "malicious_activity") val maliciousActivity: Boolean? = null,
    @Json(name = "credentials_leaked") val credentialsLeaked: Boolean? = null,
    @Json(name = "data_breach") val dataBreach: Boolean? = null,
    @Json(name = "last_seen") val lastSeen: String? = null,
    @Json(name = "spam") val spam: Boolean? = null,
    val profiles: List<String>? = null
)

interface IpApiService {
    @GET("json/{ip}")
    suspend fun lookup(
        @Path("ip") ip: String,
        @Query("fields") fields: String = "status,country,countryCode,region,regionName,city,zip,lat,lon,timezone,isp,org,as,query"
    ): Response<IpApiResponse>
}

@JsonClass(generateAdapter = false)
data class IpApiResponse(
    val status: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val region: String? = null,
    val regionName: String? = null,
    val city: String? = null,
    val zip: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val timezone: String? = null,
    val isp: String? = null,
    val org: String? = null,
    @Json(name = "as") val asn: String? = null,
    val query: String? = null
)

interface HackerTargetService {
    @GET("hostsearch/")
    suspend fun hostSearch(@Query("q") domain: String): Response<String>

    @GET("dnslookup/")
    suspend fun dnsLookup(@Query("q") domain: String): Response<String>

    @GET("reversedns/")
    suspend fun reverseDns(@Query("q") ip: String): Response<String>

    @GET("whois/")
    suspend fun whois(@Query("q") domain: String): Response<String>

    @GET("findemail/")
    suspend fun findEmail(@Query("q") email: String): Response<String>
}

interface WaybackCdxService {
    @GET("cdx/search/cdx")
    suspend fun query(
        @Query("url") url: String,
        @Query("output") output: String = "json",
        @Query("limit") limit: Int = 5,
        @Query("fl") fields: String = "timestamp,statuscode,mimetype",
        @Query("collapse") collapse: String = "timestamp:6"
    ): Response<List<List<String>>>
}

interface CrtShService {
    @GET("json")
    suspend fun query(@Query("q") domain: String): Response<List<CrtShEntry>>
}

@JsonClass(generateAdapter = false)
data class CrtShEntry(
    @Json(name = "name_value") val nameValue: String? = null,
    @Json(name = "common_name") val commonName: String? = null,
    @Json(name = "issuer_name") val issuerName: String? = null,
    @Json(name = "not_before") val notBefore: String? = null
)

interface AlienVaultOtxService {
    @GET("api/v1/indicators/IPv4/{ip}/general")
    suspend fun ipGeneral(@Path("ip") ip: String): Response<OtxResponse>

    @GET("api/v1/indicators/domain/{domain}/general")
    suspend fun domainGeneral(@Path("domain") domain: String): Response<OtxResponse>
}

@JsonClass(generateAdapter = false)
data class OtxResponse(
    @Json(name = "pulse_info") val pulseInfo: OtxPulseInfo? = null,
    val country_name: String? = null,
    val asn: String? = null,
    val reputation: Int? = null
)

@JsonClass(generateAdapter = false)
data class OtxPulseInfo(
    val count: Int? = null,
    val pulses: List<OtxPulse>? = null
)

@JsonClass(generateAdapter = false)
data class OtxPulse(
    val name: String? = null,
    val description: String? = null
)

interface JailBaseService {
    @GET("api/search")
    suspend fun search(
        @Query("who") name: String,
        @Query("where") state: String = "",
        @Query("api_key") apiKey: String = "gautam"
    ): Response<JailBaseResponse>
}

@JsonClass(generateAdapter = false)
data class JailBaseResponse(
    val records: List<JailBaseRecord>? = null,
    val total_records: Int? = null
)

@JsonClass(generateAdapter = false)
data class JailBaseRecord(
    val first_name: String? = null,
    val last_name: String? = null,
    val city: String? = null,
    val state: String? = null,
    val charge: String? = null,
    val arrest_date: String? = null,
    val booking_date: String? = null,
    val image_url: String? = null,
    val detail_uri: String? = null,
    val agency: String? = null
)

interface OpenCorporatesService {
    @GET("companies/search")
    suspend fun searchCompanies(
        @Query("q") query: String,
        @Query("format") format: String = "json"
    ): Response<OpenCorporatesCompanyResponse>

    @GET("officers/search")
    suspend fun searchOfficers(
        @Query("q") query: String,
        @Query("format") format: String = "json"
    ): Response<OpenCorporatesOfficerResponse>
}

@JsonClass(generateAdapter = false)
data class OpenCorporatesCompanyResponse(
    val results: OpenCorporatesCompanyResults? = null
)

@JsonClass(generateAdapter = false)
data class OpenCorporatesCompanyResults(
    val companies: List<OpenCorporatesCompanyItem>? = null,
    @Json(name = "total_count") val totalCount: Int? = null
)

@JsonClass(generateAdapter = false)
data class OpenCorporatesCompanyItem(
    val company: OpenCorporatesCompany? = null
)

@JsonClass(generateAdapter = false)
data class OpenCorporatesCompany(
    val name: String? = null,
    @Json(name = "company_number") val companyNumber: String? = null,
    @Json(name = "jurisdiction_code") val jurisdictionCode: String? = null,
    @Json(name = "incorporation_date") val incorporationDate: String? = null,
    @Json(name = "company_type") val companyType: String? = null,
    @Json(name = "current_status") val currentStatus: String? = null,
    @Json(name = "opencorporates_url") val opencorporatesUrl: String? = null
)

@JsonClass(generateAdapter = false)
data class OpenCorporatesOfficerResponse(
    val results: OpenCorporatesOfficerResults? = null
)

@JsonClass(generateAdapter = false)
data class OpenCorporatesOfficerResults(
    val officers: List<OpenCorporatesOfficerItem>? = null
)

@JsonClass(generateAdapter = false)
data class OpenCorporatesOfficerItem(
    val officer: OpenCorporatesOfficer? = null
)

@JsonClass(generateAdapter = false)
data class OpenCorporatesOfficer(
    val name: String? = null,
    val position: String? = null,
    @Json(name = "start_date") val startDate: String? = null,
    @Json(name = "end_date") val endDate: String? = null,
    val company: OpenCorporatesCompany? = null
)

interface NumverifyService {
    @GET("api/validate")
    suspend fun validate(
        @Query("access_key") accessKey: String,
        @Query("number") number: String,
        @Query("country_code") countryCode: String = "",
        @Query("format") format: Int = 1
    ): Response<NumverifyResponse>
}

@JsonClass(generateAdapter = false)
data class NumverifyResponse(
    val valid: Boolean? = null,
    val number: String? = null,
    @Json(name = "local_format") val localFormat: String? = null,
    @Json(name = "international_format") val internationalFormat: String? = null,
    @Json(name = "country_prefix") val countryPrefix: String? = null,
    @Json(name = "country_code") val countryCode: String? = null,
    @Json(name = "country_name") val countryName: String? = null,
    val location: String? = null,
    val carrier: String? = null,
    @Json(name = "line_type") val lineType: String? = null
)

interface ShodanInternetDbService {
    @GET("{ip}")
    suspend fun lookup(@Path("ip") ip: String): Response<ShodanInternetDbResponse>
}

@JsonClass(generateAdapter = false)
data class ShodanInternetDbResponse(
    val ip: String? = null,
    val ports: List<Int>? = null,
    val hostnames: List<String>? = null,
    val tags: List<String>? = null,
    val vulns: List<String>? = null,
    val cpes: List<String>? = null
)

interface GreyNoiseCommunityService {
    @GET("v3/community/{ip}")
    suspend fun lookup(@Path("ip") ip: String): Response<GreyNoiseCommunityResponse>
}

@JsonClass(generateAdapter = false)
data class GreyNoiseCommunityResponse(
    val ip: String? = null,
    val noise: Boolean? = null,
    val riot: Boolean? = null,
    val classification: String? = null,
    val name: String? = null,
    val link: String? = null,
    @Json(name = "last_seen") val lastSeen: String? = null,
    val message: String? = null
)

interface KeybaseLookupService {
    @GET("_/api/1.0/user/lookup.json")
    suspend fun lookup(@Query("username") username: String): Response<KeybaseResponse>
}

@JsonClass(generateAdapter = false)
data class KeybaseResponse(
    val status: KeybaseStatus? = null,
    val them: List<KeybasePerson>? = null
)

@JsonClass(generateAdapter = false)
data class KeybaseStatus(val code: Int? = null, val name: String? = null)

@JsonClass(generateAdapter = false)
data class KeybasePerson(
    val id: String? = null,
    val basics: KeybaseBasics? = null,
    val profile: KeybaseProfile? = null,
    @Json(name = "proofs_summary") val proofsSummary: KeybaseProofsSummary? = null
)

@JsonClass(generateAdapter = false)
data class KeybaseBasics(
    val username: String? = null,
    @Json(name = "full_name") val fullName: String? = null
)

@JsonClass(generateAdapter = false)
data class KeybaseProfile(
    val bio: String? = null,
    val location: String? = null,
    val twitter: String? = null
)

@JsonClass(generateAdapter = false)
data class KeybaseProofsSummary(val all: List<KeybaseProof>? = null)

@JsonClass(generateAdapter = false)
data class KeybaseProof(
    @Json(name = "proof_type") val proofType: String? = null,
    val nametag: String? = null,
    @Json(name = "service_url") val serviceUrl: String? = null
)

interface ThreatCrowdService {
    @GET("searchApi/v2/email/report/")
    suspend fun emailReport(@Query("email") email: String): Response<ThreatCrowdEmailResponse>

    @GET("searchApi/v2/domain/report/")
    suspend fun domainReport(@Query("domain") domain: String): Response<ThreatCrowdDomainResponse>

    @GET("searchApi/v2/ip/report/")
    suspend fun ipReport(@Query("ip") ip: String): Response<ThreatCrowdIpResponse>
}

@JsonClass(generateAdapter = false)
data class ThreatCrowdEmailResponse(
    @Json(name = "response_code") val responseCode: String? = null,
    val domains: List<String>? = null,
    val references: Int? = null
)

@JsonClass(generateAdapter = false)
data class ThreatCrowdDomainResponse(
    @Json(name = "response_code") val responseCode: String? = null,
    val emails: List<String>? = null,
    val subdomains: List<String>? = null,
    val resolutions: List<ThreatCrowdResolution>? = null,
    val votes: Int? = null
)

@JsonClass(generateAdapter = false)
data class ThreatCrowdIpResponse(
    @Json(name = "response_code") val responseCode: String? = null,
    val resolutions: List<ThreatCrowdIpResolution>? = null,
    val hashes: List<String>? = null,
    val votes: Int? = null
)

@JsonClass(generateAdapter = false)
data class ThreatCrowdResolution(
    @Json(name = "ip_address") val ipAddress: String? = null,
    @Json(name = "last_resolved") val lastResolved: String? = null
)

@JsonClass(generateAdapter = false)
data class ThreatCrowdIpResolution(
    @Json(name = "last_resolved") val lastResolved: String? = null,
    val domain: String? = null
)
