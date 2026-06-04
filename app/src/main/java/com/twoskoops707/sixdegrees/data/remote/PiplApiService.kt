package com.twoskoops707.sixdegrees.data.remote

import com.twoskoops707.sixdegrees.data.remote.dto.pipl.PiplSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface PiplApiService {

    @GET("search")
    suspend fun search(
        @Query("key") apiKey: String,
        @Query("first_name") firstName: String? = null,
        @Query("last_name") lastName: String? = null,
        @Query("email") email: String? = null,
        @Query("phone") phone: String? = null,
        @Query("username") username: String? = null,
        @Query("minimum_match") minimumMatch: Float = 0.7f
    ): PiplSearchResponse
}
