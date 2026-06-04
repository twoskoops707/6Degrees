package com.twoskoops707.sixdegrees.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface FreeApiServices {

    @GET("json/{ip}")
    suspend fun ipGeolocate(@Path("ip") ip: String): Response<ResponseBody>

    @GET("api/v1/info")
    suspend fun phoneInfo(
        @Query("number") number: String,
        @Query("access_key") key: String
    ): Response<ResponseBody>
}
