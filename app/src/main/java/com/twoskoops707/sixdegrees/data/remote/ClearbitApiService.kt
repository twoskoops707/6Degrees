package com.twoskoops707.sixdegrees.data.remote

import com.twoskoops707.sixdegrees.data.remote.dto.clearbit.ClearbitCompany
import com.twoskoops707.sixdegrees.data.remote.dto.clearbit.ClearbitPersonResponse
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

interface ClearbitPersonApiService {

    @GET("people/find")
    suspend fun findPerson(
        @Query("email") email: String? = null,
        @Query("given_name") givenName: String? = null,
        @Query("family_name") familyName: String? = null,
        @Header("Authorization") bearerToken: String
    ): Response<ClearbitPersonResponse>
}
