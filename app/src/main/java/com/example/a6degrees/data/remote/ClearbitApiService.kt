package com.example.a6degrees.data.remote

import com.example.a6degrees.data.remote.dto.clearbit.ClearbitPersonResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface ClearbitApiService {
    @GET("https://person.clearbit.com/v2/people/find")
    suspend fun findPerson(
        @Header("Authorization") authorization: String,
        @Query("email") email: String? = null,
        @Query("given_name") givenName: String? = null,
        @Query("family_name") familyName: String? = null,
        @Query("company") company: String? = null
    ): Response<ClearbitPersonResponse>

    @GET("https://company.clearbit.com/v2/companies/find")
    suspend fun findCompany(
        @Header("Authorization") authorization: String,
        @Query("domain") domain: String? = null,
        @Query("name") name: String? = null,
        @Query("linkedin") linkedin: String? = null
    ): Response<ClearbitPersonResponse>

    @GET("https://prospector.clearbit.com/v1/people/search")
    suspend fun searchProspects(
        @Header("Authorization") authorization: String,
        @Query("query") query: String? = null,
        @Query("domain") domain: String? = null,
        @Query("role") role: String? = null,
        @Query("seniority") seniority: String? = null,
        @Query("titles") titles: String? = null,
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null
    ): Response<ClearbitPersonResponse>

    @GET("https://discovery.clearbit.com/v1/companies/search")
    suspend fun searchCompanies(
        @Header("Authorization") authorization: String,
        @Query("query") query: String? = null,
        @Query("tags") tags: String? = null,
        @Query("tech") tech: String? = null,
        @Query("employee_range") employeeRange: String? = null
    ): Response<ClearbitPersonResponse>
}