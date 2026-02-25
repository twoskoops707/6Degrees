package com.twoskoops707.sixdegrees.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    fun create(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(ScalarsConverterFactory.create())
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

    val emailRepService: EmailRepService by lazy {
        create("https://emailrep.io/").create(EmailRepService::class.java)
    }

    val ipApiService: IpApiService by lazy {
        create("http://ip-api.com/").create(IpApiService::class.java)
    }

    val hackerTargetService: HackerTargetService by lazy {
        create("https://api.hackertarget.com/").create(HackerTargetService::class.java)
    }

    val waybackService: WaybackCdxService by lazy {
        create("https://web.archive.org/").create(WaybackCdxService::class.java)
    }

    val crtShService: CrtShService by lazy {
        create("https://crt.sh/").create(CrtShService::class.java)
    }

    val alienVaultService: AlienVaultOtxService by lazy {
        create("https://otx.alienvault.com/").create(AlienVaultOtxService::class.java)
    }

    val jailBaseService: JailBaseService by lazy {
        create("https://www.jailbase.com/").create(JailBaseService::class.java)
    }

    val openCorporatesService: OpenCorporatesService by lazy {
        create("https://api.opencorporates.com/v0.4/").create(OpenCorporatesService::class.java)
    }

    val numverifyService: NumverifyService by lazy {
        create("http://apilayer.net/").create(NumverifyService::class.java)
    }

    val shodanInternetDbService: ShodanInternetDbService by lazy {
        create("https://internetdb.shodan.io/").create(ShodanInternetDbService::class.java)
    }

    val greyNoiseService: GreyNoiseCommunityService by lazy {
        create("https://api.greynoise.io/").create(GreyNoiseCommunityService::class.java)
    }

    val keybaseService: KeybaseLookupService by lazy {
        create("https://keybase.io/").create(KeybaseLookupService::class.java)
    }

    val threatCrowdService: ThreatCrowdService by lazy {
        create("https://www.threatcrowd.org/").create(ThreatCrowdService::class.java)
    }
}
