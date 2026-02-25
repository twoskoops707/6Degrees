package com.twoskoops707.sixdegrees.data.remote

import com.twoskoops707.sixdegrees.data.remote.dto.builtwith.BuiltWithResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface BuiltWithApiService {
    @GET("https://api.builtwith.com/v20/api.json")
    suspend fun lookupDomain(
        @Query("KEY") apiKey: String,
        @Query("LOOKUP") domain: String
    ): Response<BuiltWithResponse>

    @GET("https://api.builtwith.com/v20/detailed/json")
    suspend fun detailedLookup(
        @Query("KEY") apiKey: String,
        @Query("LOOKUP") domain: String,
        @Query("NOLIVE") noLive: Boolean? = null,
        @Query("NOHOST") noHost: Boolean? = null,
        @Query("NOMETA") noMeta: Boolean? = null
    ): Response<BuiltWithResponse>

    @GET("https://api.builtwith.com/relations/v5/api.json")
    suspend fun relationshipLookup(
        @Query("KEY") apiKey: String,
        @Query("LOOKUP") domain: String,
        @Query("NOMETA") noMeta: Boolean? = null
    ): Response<BuiltWithResponse>

    @GET("https://api.builtwith.com/trends/v8/api.json")
    suspend fun trendsLookup(
        @Query("KEY") apiKey: String,
        @Query("TECH") technology: String,
        @Query("COUNTRY") country: String? = null,
        @Query("DATE") date: String? = null
    ): Response<BuiltWithResponse>
}