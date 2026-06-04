package com.twoskoops707.sixdegrees.data.remote

import com.twoskoops707.sixdegrees.data.remote.dto.builtwith.BuiltWithResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface BuiltWithApiService {

    @GET("v21/api.json")
    suspend fun lookup(
        @Query("KEY") apiKey: String,
        @Query("LOOKUP") domain: String
    ): Response<BuiltWithResponse>
}
