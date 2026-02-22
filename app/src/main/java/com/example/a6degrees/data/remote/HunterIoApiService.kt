package com.example.a6degrees.data.remote

import com.example.a6degrees.data.remote.dto.hunterio.HunterIoDomainSearchResponse
import com.example.a6degrees.data.remote.dto.hunterio.HunterIoEmailVerifyResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface HunterIoApiService {
    @GET("https://api.hunter.io/v2/domain-search")
    suspend fun searchDomain(
        @Query("domain") domain: String? = null,
        @Query("company") company: String? = null,
        @Query("type") type: String? = null,
        @Query("seniority") seniority: String? = null,
        @Query("department") department: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("api_key") apiKey: String
    ): Response<HunterIoDomainSearchResponse>

    @GET("https://api.hunter.io/v2/email-finder")
    suspend fun findEmail(
        @Query("domain") domain: String,
        @Query("first_name") firstName: String? = null,
        @Query("last_name") lastName: String? = null,
        @Query("full_name") fullName: String? = null,
        @Query("company") company: String? = null,
        @Query("api_key") apiKey: String
    ): Response<HunterIoDomainSearchResponse>

    @GET("https://api.hunter.io/v2/email-verifier")
    suspend fun verifyEmail(
        @Query("email") email: String,
        @Query("api_key") apiKey: String
    ): Response<HunterIoEmailVerifyResponse>
}