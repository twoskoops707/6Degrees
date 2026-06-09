package com.twoskoops707.sixdegrees.data.remote

import com.twoskoops707.sixdegrees.data.remote.dto.peopledatalabs.PdlEnrichResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface PeopleDataLabsApiService {

    @GET("person/enrich")
    suspend fun enrichPerson(
        @Query("email") email: String? = null,
        @Query("phone") phone: String? = null,
        @Query("first_name") firstName: String? = null,
        @Query("last_name") lastName: String? = null,
        @Header("X-Api-Key") apiKey: String
    ): PdlEnrichResponse
}
