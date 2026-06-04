package com.twoskoops707.sixdegrees.data.remote

import com.twoskoops707.sixdegrees.data.remote.dto.haveibeenpwned.HibpBreach
import com.twoskoops707.sixdegrees.data.remote.dto.haveibeenpwned.HibpPaste
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface HibpApiService {

    @GET("breachedaccount/{account}")
    suspend fun getBreaches(
        @Path("account") account: String,
        @Header("hibp-api-key") apiKey: String,
        @Header("User-Agent") userAgent: String = "6Degrees-OSINT",
        @Query("truncateResponse") truncate: Boolean = false
    ): List<HibpBreach>

    @GET("pasteaccount/{account}")
    suspend fun getPastes(
        @Path("account") account: String,
        @Header("hibp-api-key") apiKey: String,
        @Header("User-Agent") userAgent: String = "6Degrees-OSINT"
    ): List<HibpPaste>
}
