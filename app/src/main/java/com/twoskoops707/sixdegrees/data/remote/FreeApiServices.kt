package com.twoskoops707.sixdegrees.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Numverify / APILayer phone validation — free tier: 100 req/mo with access_key. */
interface NumverifyApiService {
    @GET("validate")
    suspend fun validate(
        @Query("access_key") accessKey: String,
        @Query("number") number: String
    ): Response<ResponseBody>
}

/** ip-api.com geolocation — free, no key (45 req/min). Already used via OkHttp in OsintRepository. */
interface IpApiService {
    @GET("json/{ip}")
    suspend fun ipGeolocate(@Path("ip") ip: String): Response<ResponseBody>
}
