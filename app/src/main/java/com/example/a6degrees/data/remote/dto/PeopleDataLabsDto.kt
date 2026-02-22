package com.example.a6degrees.data.remote.dto.peopledatalabs

import com.squareup.moshi.Json

data class PdlPersonSearchResponse(
    @Json(name = "status") val status: Int?,
    @Json(name = "data") val data: List<PdlPerson>?,
    @Json(name = "size") val size: Int?,
    @Json(name = "total") val total: Int?
)

data class PdlPerson(
    @Json(name = "id") val id: String?,
    @Json(name = "full_name") val fullName: String?,
    @Json(name = "first_name") val firstName: String?,
    @Json(name = "last_name") val lastName: String?,
    @Json(name = "middle_name") val middleName: String?,
    @Json(name = "job_title") val jobTitle: String?,
    @Json(name = "job_level") val jobLevel: String?,
    @Json(name = "job_company_name") val jobCompanyName: String?,
    @Json(name = "job_company_website") val jobCompanyWebsite: String?,
    @Json(name = "job_company_industry") val jobCompanyIndustry: String?,
    @Json(name = "job_company_linkedin") val jobCompanyLinkedin: String?,
    @Json(name = "job_company_twitter") val jobCompanyTwitter: String?,
    @Json(name = "job_company_facebook") val jobCompanyFacebook: String?,
    @Json(name = "job_company_location") val jobCompanyLocation: String?,
    @Json(name = "job_company_size") val jobCompanySize: String?,
    @Json(name = "job_company_founded") val jobCompanyFounded: Int?,
    @Json(name = "job_last_updated") val jobLastUpdated: String?,
    @Json(name = "job_start_date") val jobStartDate: String?,
    @Json(name = "job_summary") val jobSummary: String?,
    @Json(name = "location_name") val locationName: String?,
    @Json(name = "location_country") val locationCountry: String?,
    @Json(name = "location_continent") val locationContinent: String?,
    @Json(name = "location_state") val locationState: String?,
    @Json(name = "location_city") val locationCity: String?,
    @Json(name = "location_street") val locationStreet: String?,
    @Json(name = "location_zip") val locationZip: String?,
    @Json(name = "emails") val emails: List<PdlEmail>?,
    @Json(name = "phones") val phones: List<PdlPhone>?,
    @Json(name = "profiles") val profiles: List<PdlProfile>?,
    @Json(name = "education") val education: List<PdlEducation>?,
    @Json(name = "employment") val employment: List<PdlEmployment>?,
    @Json(name = "certifications") val certifications: List<PdlCertification>?,
    @Json(name = "languages") val languages: List<PdlLanguage>?,
    @Json(name = "skills") val skills: List<String>?,
    @Json(name = "interests") val interests: List<String>?,
    @Json(name = "summary") val summary: String?
)

data class PdlEmail(
    @Json(name = "address") val address: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "first_seen") val firstSeen: String?,
    @Json(name = "last_seen") val lastSeen: String?,
    @Json(name = "num_sources") val numSources: Int?
)

data class PdlPhone(
    @Json(name = "number") val number: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "first_seen") val firstSeen: String?,
    @Json(name = "last_seen") val lastSeen: String?,
    @Json(name = "num_sources") val numSources: Int?
)

data class PdlProfile(
    @Json(name = "network") val network: String?,
    @Json(name = "username") val username: String?,
    @Json(name = "url") val url: String?,
    @Json(name = "bio") val bio: String?,
    @Json(name = "followers") val followers: Int?,
    @Json(name = "following") val following: Int?,
    @Json(name = "id") val id: String?
)

data class PdlEducation(
    @Json(name = "school_name") val schoolName: String?,
    @Json(name = "start_date") val startDate: String?,
    @Json(name = "end_date") val endDate: String?,
    @Json(name = "degree") val degree: String?,
    @Json(name = "major") val major: String?,
    @Json(name = "minor") val minor: String?
)

data class PdlEmployment(
    @Json(name = "company_name") val companyName: String?,
    @Json(name = "title") val title: String?,
    @Json(name = "start_date") val startDate: String?,
    @Json(name = "end_date") val endDate: String?,
    @Json(name = "is_primary") val isPrimary: Boolean?,
    @Json(name = "summary") val summary: String?
)

data class PdlCertification(
    @Json(name = "title") val title: String?,
    @Json(name = "issuer") val issuer: String?,
    @Json(name = "issued_date") val issuedDate: String?,
    @Json(name = "expiry_date") val expiryDate: String?,
    @Json(name = "license_number") val licenseNumber: String?
)

data class PdlLanguage(
    @Json(name = "name") val name: String?,
    @Json(name = "proficiency") val proficiency: String?
)