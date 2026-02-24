package com.example.a6degrees.data.repository

import android.content.Context
import com.example.a6degrees.data.ApiKeyManager
import com.example.a6degrees.data.local.OsintDatabase
import com.example.a6degrees.data.local.entity.OsintReportEntity
import com.example.a6degrees.data.local.entity.PersonEntity
import com.example.a6degrees.data.remote.RetrofitClient
import com.example.a6degrees.data.remote.dto.pipl.PiplPerson
import com.example.a6degrees.domain.model.Address
import com.example.a6degrees.domain.model.DataSource
import com.example.a6degrees.domain.model.Employment
import com.example.a6degrees.domain.model.SocialProfile
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Date
import java.util.UUID

private class DateAdapter {
    @ToJson fun toJson(date: Date): Long = date.time
    @FromJson fun fromJson(value: Long): Date = Date(value)
}

class OsintRepository(context: Context) {

    private val apiKeyManager = ApiKeyManager(context)
    private val db = OsintDatabase.getDatabase(context)
    private val moshi = Moshi.Builder().add(DateAdapter()).add(KotlinJsonAdapterFactory()).build()

    suspend fun searchPerson(query: String): Result<String> {
        val piplKey = apiKeyManager.piplKey
        val pdlKey = apiKeyManager.pdlKey
        val hunterKey = apiKeyManager.hunterKey

        if (!apiKeyManager.hasAnyKey()) {
            return Result.failure(Exception("No API keys configured. Please add API keys in Settings."))
        }

        return try {
            var reportId: String? = null

            if (piplKey.isNotBlank()) {
                val nameParts = query.trim().split(" ")
                val firstName = nameParts.firstOrNull()
                val lastName = if (nameParts.size > 1) nameParts.drop(1).joinToString(" ") else null

                val response = RetrofitClient.piplService.searchPerson(
                    apiKey = piplKey,
                    rawName = query,
                    firstName = firstName,
                    lastName = lastName,
                    showSources = "all"
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    val piplPerson = body?.person
                    if (piplPerson != null) {
                        val personEntity = mapPiplPersonToEntity(piplPerson)
                        db.personDao().insertPerson(personEntity)

                        val sources = body.sources?.map { source ->
                            DataSource(
                                name = source.name ?: source.domain ?: "Pipl",
                                url = source.url,
                                retrievedAt = Date(),
                                reliabilityScore = 0.8
                            )
                        } ?: listOf(DataSource("Pipl", null, Date(), 0.8))

                        reportId = saveReport(query, personEntity.id, sources)
                    }
                }
            }

            if (reportId == null && hunterKey.isNotBlank() && query.contains("@")) {
                reportId = saveReport(query, null, listOf(DataSource("Hunter.io", null, Date(), 0.7)))
            }

            if (reportId != null) {
                Result.success(reportId)
            } else {
                Result.failure(Exception("No results found for \"$query\". Try a different name, email, or verify your API keys are valid."))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Search failed: ${e.message}"))
        }
    }

    suspend fun searchCompany(query: String): Result<String> {
        if (!apiKeyManager.hasAnyKey()) {
            return Result.failure(Exception("No API keys configured. Please add API keys in Settings."))
        }
        return try {
            val reportId = saveReport(query, null, listOf(DataSource("Company Search", null, Date(), 0.5)))
            Result.success(reportId)
        } catch (e: Exception) {
            Result.failure(Exception("Search failed: ${e.message}"))
        }
    }

    suspend fun getRecentReports(limit: Int = 20): List<OsintReportEntity> =
        db.reportDao().getRecentReports(limit)

    suspend fun getReportById(id: String): OsintReportEntity? =
        db.reportDao().getReportById(id)

    suspend fun getPersonById(id: String): PersonEntity? =
        db.personDao().getPersonById(id)

    private suspend fun saveReport(
        query: String,
        personId: String?,
        sources: List<DataSource>
    ): String {
        val reportId = UUID.randomUUID().toString()
        val sourcesJson = moshi.adapter<List<DataSource>>(
            Types.newParameterizedType(List::class.java, DataSource::class.java)
        ).toJson(sources)

        val entity = OsintReportEntity(
            id = reportId,
            searchQuery = query,
            generatedAt = Date(),
            personId = personId,
            companiesJson = "[]",
            propertiesJson = "[]",
            vehicleRecordsJson = "[]",
            financialSummaryJson = null,
            confidenceScore = if (personId != null) 0.8 else 0.3,
            sourcesJson = sourcesJson
        )
        db.reportDao().insertReport(entity)
        return reportId
    }

    private fun mapPiplPersonToEntity(pipl: PiplPerson): PersonEntity {
        val id = pipl.id ?: UUID.randomUUID().toString()
        val firstName = pipl.names?.firstOrNull()?.first ?: ""
        val lastName = pipl.names?.firstOrNull()?.last ?: ""
        val fullName = pipl.names?.firstOrNull()?.display
            ?: listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

        val addresses = pipl.addresses?.map { addr ->
            Address(
                street = listOfNotNull(addr.house, addr.street).joinToString(" "),
                city = addr.city ?: "",
                state = addr.state ?: "",
                postalCode = addr.zipCode ?: "",
                country = addr.country ?: "",
                type = "unknown"
            )
        } ?: emptyList()

        val employment = pipl.jobs?.map { job ->
            Employment(
                companyName = job.organization ?: "",
                jobTitle = job.title ?: "",
                startDate = job.dateRange?.start,
                endDate = job.dateRange?.end,
                isCurrent = job.dateRange?.end == null
            )
        } ?: emptyList()

        val socialProfiles = pipl.urls?.mapNotNull { url ->
            url.url?.let { urlStr ->
                SocialProfile(
                    platform = url.category ?: url.domain ?: "Web",
                    username = url.domain ?: "",
                    url = urlStr,
                    followersCount = null
                )
            }
        } ?: emptyList()

        val addrType = Types.newParameterizedType(List::class.java, Address::class.java)
        val empType = Types.newParameterizedType(List::class.java, Employment::class.java)
        val socialType = Types.newParameterizedType(List::class.java, SocialProfile::class.java)
        val strType = Types.newParameterizedType(List::class.java, String::class.java)

        return PersonEntity(
            id = id,
            firstName = firstName,
            lastName = lastName,
            fullName = fullName,
            emailAddress = pipl.emails?.firstOrNull()?.address,
            phoneNumber = pipl.phones?.firstOrNull()?.display,
            dateOfBirth = pipl.dob,
            addressesJson = moshi.adapter<List<Address>>(addrType).toJson(addresses),
            employmentHistoryJson = moshi.adapter<List<Employment>>(empType).toJson(employment),
            socialProfilesJson = moshi.adapter<List<SocialProfile>>(socialType).toJson(socialProfiles),
            aliasesJson = moshi.adapter<List<String>>(strType).toJson(
                pipl.usernames?.mapNotNull { it.content } ?: emptyList()
            ),
            nationalitiesJson = moshi.adapter<List<String>>(strType).toJson(
                pipl.originCountries?.mapNotNull { it.content } ?: emptyList()
            ),
            gender = pipl.gender,
            profileImageUrl = pipl.images?.firstOrNull()?.url
        )
    }
}
