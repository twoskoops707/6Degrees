package com.twoskoops707.sixdegrees.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object RetrofitClient {

    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val fastHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val torHttpClient: OkHttpClient? by lazy {
        try {
            val probe = java.net.Socket()
            probe.connect(InetSocketAddress("127.0.0.1", 9050), 2000)
            probe.close()
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", 9050))
            OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        } catch (_: Exception) { null }
    }

    private fun retrofit(baseUrl: String, useTor: Boolean = false): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(if (useTor) torHttpClient ?: fastHttpClient else fastHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    val hibpService: HibpApiService by lazy {
        retrofit("https://haveibeenpwned.com/api/v3/").create(HibpApiService::class.java)
    }

    val hunterService: HunterIoApiService by lazy {
        retrofit("https://api.hunter.io/v2/").create(HunterIoApiService::class.java)
    }

    val piplService: PiplApiService by lazy {
        retrofit("https://api.pipl.com/").create(PiplApiService::class.java)
    }

    val pdlService: PeopleDataLabsApiService by lazy {
        retrofit("https://api.peopledatalabs.com/v5/").create(PeopleDataLabsApiService::class.java)
    }

    val builtWithService: BuiltWithApiService by lazy {
        retrofit("https://api.builtwith.com/").create(BuiltWithApiService::class.java)
    }

    val numverifyService: NumverifyApiService by lazy {
        retrofit("http://apilayer.net/api/").create(NumverifyApiService::class.java)
    }

    val ipApiService: IpApiService by lazy {
        retrofit("http://ip-api.com/").create(IpApiService::class.java)
    }
}
