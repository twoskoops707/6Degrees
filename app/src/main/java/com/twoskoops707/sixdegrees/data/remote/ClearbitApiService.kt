package com.twoskoops707.sixdegrees.data.remote

import com.twoskoops707.sixdegrees.data.remote.dto.clearbit.ClearbitCompany
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface ClearbitApiService {

    @GET("companies/find")
    suspend fun findCompany(
        @Query("domain") domain: String,
        @Header("Authorization") bearerToken: String
    ): Response<ClearbitCompany>
}
