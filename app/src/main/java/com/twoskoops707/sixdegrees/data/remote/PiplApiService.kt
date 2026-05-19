package com.twoskoops707.sixdegrees.data.remote

import com.twoskoops707.sixdegrees.data.remote.dto.pipl.PiplSearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface PiplApiService {
    @GET("https://api.pipl.com/search/")
    suspend fun searchPerson(
        @Query("key") apiKey: String,
        @Query("first_name") firstName: String? = null,
        @Query("last_name") lastName: String? = null,
        @Query("middle_name") middleName: String? = null,
        @Query("raw_name") rawName: String? = null,
        @Query("email") email: String? = null,
        @Query("phone") phone: String? = null,
        @Query("username") username: String? = null,
        @Query("user_id") userId: String? = null,
        @Query("url") url: String? = null,
        @Query("country") country: String? = null,
        @Query("state") state: String? = null,
        @Query("city") city: String? = null,
        @Query("house") house: String? = null,
        @Query("street") street: String? = null,
        @Query("zip_code") zipCode: String? = null,
        @Query("raw_address") rawAddress: String? = null,
        @Query("from_age") fromAge: Int? = null,
        @Query("to_age") toAge: Int? = null,
        @Query("person_id") personId: String? = null,
        @Query("search_pointer") searchPointer: String? = null,
        @Query("minimum_probability") minimumProbability: Double? = null,
        @Query("minimum_match") minimumMatch: String? = null,
        @Query("show_sources") showSources: String? = null,
        @Query("live_feeds") liveFeeds: Boolean? = null,
        @Query("hide_sponsored") hideSponsored: Boolean? = null,
        @Query("infer_persons") inferPersons: Boolean? = null
    ): Response<PiplSearchResponse>
}