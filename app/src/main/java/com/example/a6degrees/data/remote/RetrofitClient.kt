package com.example.a6degrees.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object RetrofitClient {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    fun create(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val piplService: PiplApiService by lazy {
        create("https://api.pipl.com/").create(PiplApiService::class.java)
    }

    val hunterService: HunterIoApiService by lazy {
        create("https://api.hunter.io/").create(HunterIoApiService::class.java)
    }

    val hibpService: HibpApiService by lazy {
        create("https://haveibeenpwned.com/").create(HibpApiService::class.java)
    }

    val clearbitPersonService: ClearbitApiService by lazy {
        create("https://person.clearbit.com/").create(ClearbitApiService::class.java)
    }

    val pdlService: PeopleDataLabsApiService by lazy {
        create("https://api.peopledatalabs.com/").create(PeopleDataLabsApiService::class.java)
    }
}
