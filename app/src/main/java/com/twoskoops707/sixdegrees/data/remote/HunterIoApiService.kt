package com.twoskoops707.sixdegrees.data.remote

import com.twoskoops707.sixdegrees.data.remote.dto.hunterio.HunterIoDomainSearchResponse
import com.twoskoops707.sixdegrees.data.remote.dto.hunterio.HunterIoEmailVerifyResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface HunterIoApiService {

    @GET("domain-search")
    suspend fun domainSearch(
        @Query("domain") domain: String,
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 10
    ): HunterIoDomainSearchResponse

    @GET("email-verifier")
    suspend fun verifyEmail(
        @Query("email") email: String,
        @Query("api_key") apiKey: String
    ): HunterIoEmailVerifyResponse
}
