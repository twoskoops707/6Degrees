package com.twoskoops707.sixdegrees.data.remote

import com.twoskoops707.sixdegrees.data.remote.dto.haveibeenpwned.HibpBreach
import com.twoskoops707.sixdegrees.data.remote.dto.haveibeenpwned.HibpDataClass
import com.twoskoops707.sixdegrees.data.remote.dto.haveibeenpwned.HibpPaste
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface HibpApiService {
    @GET("https://haveibeenpwned.com/api/v3/breachedaccount/{account}")
    suspend fun getBreachedAccount(
        @Path("account") account: String,
        @Header("hibp-api-key") apiKey: String,
        @Query("truncateResponse") truncateResponse: Boolean? = null,
        @Query("domain") domain: String? = null,
        @Query("includeUnverified") includeUnverified: Boolean? = null
    ): Response<List<HibpBreach>>

    @GET("https://haveibeenpwned.com/api/v3/breaches")
    suspend fun getAllBreaches(
        @Query("domain") domain: String? = null
    ): Response<List<HibpBreach>>

    @GET("https://haveibeenpwned.com/api/v3/breach/{name}")
    suspend fun getBreach(
        @Path("name") name: String
    ): Response<HibpBreach>

    @GET("https://haveibeenpwned.com/api/v3/dataclasses")
    suspend fun getDataClasses(): Response<List<String>>

    @GET("https://haveibeenpwned.com/api/v3/pasteaccount/{account}")
    suspend fun getPasteAccount(
        @Path("account") account: String,
        @Header("hibp-api-key") apiKey: String
    ): Response<List<HibpPaste>>

    @GET("https://api.pwnedpasswords.com/range/{hashPrefix}")
    suspend fun getPasswordRange(
        @Path("hashPrefix") hashPrefix: String
    ): Response<String>
}