package com.example.a6degrees.data.remote

import com.example.a6degrees.data.remote.dto.peopledatalabs.PdlPersonSearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface PeopleDataLabsApiService {
    @GET("person/search")
    suspend fun searchPerson(
        @Header("Authorization") authorization: String,
        @Query("sql") sql: String,
        @Query("size") size: Int = 10,
        @Query("from") from: Int = 0
    ): Response<PdlPersonSearchResponse>

    @GET("person/enrich")
    suspend fun enrichPerson(
        @Header("Authorization") authorization: String,
        @Query("email") email: String? = null,
        @Query("profile") profile: String? = null,
        @Query("phone") phone: String? = null,
        @Query("first_name") firstName: String? = null,
        @Query("last_name") lastName: String? = null,
        @Query("middle_name") middleName: String? = null,
        @Query("location") location: String? = null,
        @Query("street_address") streetAddress: String? = null,
        @Query("postal_code") postalCode: String? = null,
        @Query("company") company: String? = null,
        @Query("school") school: String? = null,
        @Query("job_title") jobTitle: String? = null,
        @Query("skills") skills: String? = null
    ): Response<PdlPersonSearchResponse>

    @GET("person/identify")
    suspend fun identifyPerson(
        @Header("Authorization") authorization: String,
        @Query("email") email: String? = null,
        @Query("profile") profile: String? = null,
        @Query("phone") phone: String? = null,
        @Query("first_name") firstName: String? = null,
        @Query("last_name") lastName: String? = null,
        @Query("middle_name") middleName: String? = null,
        @Query("location") location: String? = null,
        @Query("street_address") streetAddress: String? = null,
        @Query("postal_code") postalCode: String? = null,
        @Query("company") company: String? = null,
        @Query("school") school: String? = null,
        @Query("job_title") jobTitle: String? = null,
        @Query("skills") skills: String? = null
    ): Response<PdlPersonSearchResponse>
}