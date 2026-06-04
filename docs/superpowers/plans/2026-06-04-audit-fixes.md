# App Audit & Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all broken/stubbed code in 6Degrees so every API service, scraper, and search flow works end-to-end.

**Architecture:** RetrofitClient becomes a singleton providing Retrofit instances for all API services. Each stubbed API service interface gets its Retrofit annotations. Dead code (Slideshow/Reflow) is removed. All 7 search type flows are verified through static code review.

**Tech Stack:** Kotlin, Retrofit 2.11, OkHttp 4.12, Moshi 1.15, Room 2.6, Coroutines 1.7

---

### Task 1: Build RetrofitClient singleton

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/remote/RetrofitClient.kt`

Currently this file contains only a package declaration. It needs to become the single source of truth for all Retrofit instances.

- [ ] **Step 1: Replace RetrofitClient.kt with full implementation**

```kotlin
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

    val clearbitService: ClearbitApiService by lazy {
        retrofit("https://company.clearbit.com/v2/").create(ClearbitApiService::class.java)
    }

    val pdlService: PeopleDataLabsApiService by lazy {
        retrofit("https://api.peopledatalabs.com/v5/").create(PeopleDataLabsApiService::class.java)
    }

    val builtWithService: BuiltWithApiService by lazy {
        retrofit("https://api.builtwith.com/").create(BuiltWithApiService::class.java)
    }
}
```

- [ ] **Step 2: Verify file compiles by checking imports exist**

Verify `retrofit2.converter.moshi.MoshiConverterFactory` is available — check `build.gradle.kts`:
```
grep -n "moshi" app/build.gradle.kts
```
Expected: `com.squareup.retrofit2:converter-moshi:2.11.0` present.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/remote/RetrofitClient.kt
git commit -m "fix: implement RetrofitClient singleton with all API service factories"
```

---

### Task 2: Implement HibpApiService

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/remote/HibpApiService.kt`

HIBP v3 API docs: breaches by account, pastes by account. Requires `hibp-api-key` header and `User-Agent` header.

- [ ] **Step 1: Replace HibpApiService.kt**

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/remote/HibpApiService.kt
git commit -m "fix: implement HibpApiService with breach and paste endpoints"
```

---

### Task 3: Implement HunterIoApiService

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/remote/HunterIoApiService.kt`

- [ ] **Step 1: Replace HunterIoApiService.kt**

```kotlin
package com.twoskoops707.sixdegrees.data.remote

import com.twoskoops707.sixdegrees.data.remote.dto.hunterio.HunterIoDomainSearchResponse
import com.twoskoops707.sixdegrees.data.remote.dto.hunterio.HunterIoEmailVerifyResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface HunterIoApiService {

    @GET("domain-search")
    suspend fun domainSearch(
        @Query("domain") domain: String,
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 10
    ): HunterIoDomainSearchResponse

    @GET("email-verifier")
    suspend fun verifyEmail(
        @Query("email") email: String,
        @Query("api_key") apiKey: String
    ): HunterIoEmailVerifyResponse
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/remote/HunterIoApiService.kt
git commit -m "fix: implement HunterIoApiService with domain search and email verify"
```

---

### Task 4: Implement PiplApiService

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/remote/PiplApiService.kt`

Pipl v5 API uses a single `/search` endpoint with query parameters.

- [ ] **Step 1: Replace PiplApiService.kt**

```kotlin
package com.twoskoops707.sixdegrees.data.remote

import com.twoskoops707.sixdegrees.data.remote.dto.pipl.PiplSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface PiplApiService {

    @GET("search")
    suspend fun search(
        @Query("key") apiKey: String,
        @Query("first_name") firstName: String? = null,
        @Query("last_name") lastName: String? = null,
        @Query("email") email: String? = null,
        @Query("phone") phone: String? = null,
        @Query("username") username: String? = null,
        @Query("minimum_match") minimumMatch: Float = 0.7f
    ): PiplSearchResponse
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/remote/PiplApiService.kt
git commit -m "fix: implement PiplApiService search endpoint"
```

---

### Task 5: Implement remaining stub API services

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/remote/ClearbitApiService.kt`
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/remote/PeopleDataLabsApiService.kt`
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/remote/BuiltWithApiService.kt`
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/remote/FreeApiServices.kt`

- [ ] **Step 1: Implement ClearbitApiService.kt**

```kotlin
package com.twoskoops707.sixdegrees.data.remote

import com.twoskoops707.sixdegrees.data.remote.dto.ClearbitDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface ClearbitApiService {

    @GET("companies/find")
    suspend fun findCompany(
        @Query("domain") domain: String,
        @Header("Authorization") bearerToken: String
    ): ClearbitDto.ClearbitCompany
}
```

- [ ] **Step 2: Check ClearbitDto — read what fields it exposes**

```bash
cat app/src/main/java/com/twoskoops707/sixdegrees/data/remote/dto/ClearbitDto.kt
```

If `ClearbitDto.ClearbitCompany` doesn't exist, use `retrofit2.Response<okhttp3.ResponseBody>` as the return type as a safe placeholder.

- [ ] **Step 3: Implement PeopleDataLabsApiService.kt**

```kotlin
package com.twoskoops707.sixdegrees.data.remote

import com.twoskoops707.sixdegrees.data.remote.dto.PeopleDataLabsDto
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
    ): PeopleDataLabsDto.PdlPersonResponse
}
```

- [ ] **Step 4: Check PeopleDataLabsDto**

```bash
cat app/src/main/java/com/twoskoops707/sixdegrees/data/remote/dto/PeopleDataLabsDto.kt
```

If `PdlPersonResponse` doesn't exist, replace the return type with `retrofit2.Response<okhttp3.ResponseBody>`.

- [ ] **Step 5: Implement BuiltWithApiService.kt**

```kotlin
package com.twoskoops707.sixdegrees.data.remote

import com.twoskoops707.sixdegrees.data.remote.dto.BuiltWithDto
import retrofit2.http.GET
import retrofit2.http.Query

interface BuiltWithApiService {

    @GET("v21/api.json")
    suspend fun lookup(
        @Query("KEY") apiKey: String,
        @Query("LOOKUP") domain: String
    ): BuiltWithDto.BuiltWithResponse
}
```

- [ ] **Step 6: Implement FreeApiServices.kt**

```kotlin
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
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/remote/ClearbitApiService.kt \
        app/src/main/java/com/twoskoops707/sixdegrees/data/remote/PeopleDataLabsApiService.kt \
        app/src/main/java/com/twoskoops707/sixdegrees/data/remote/BuiltWithApiService.kt \
        app/src/main/java/com/twoskoops707/sixdegrees/data/remote/FreeApiServices.kt
git commit -m "fix: implement remaining stub API service interfaces"
```

---

### Task 6: Wire API calls into OsintRepository

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/repository/OsintRepository.kt`

Add API calls alongside scrapers in `searchWithProgress()`. Each API call wraps in try/catch — failure emits `Failed` event, not a crash.

- [ ] **Step 1: Add RetrofitClient import and ApiKeyManager to OsintRepository constructor**

In `OsintRepository.kt`, the class declaration is:
```kotlin
class OsintRepository(context: Context) {
```

Add ApiKeyManager field after `private val appCtx`:
```kotlin
private val apiKeys = com.twoskoops707.sixdegrees.data.ApiKeyManager(context)
```

- [ ] **Step 2: Add HIBP call for email searches**

In `searchWithProgress()`, inside the `"email", "breach"` branch (after the existing `launch { scrapeProxyNova(...) }` block), add:

```kotlin
launch {
    val key = apiKeys.getKey("hibp")
    if (key.isNullOrBlank()) {
        send(SearchProgressEvent.NotFound("HIBP (no key)"))
        return@launch
    }
    send(SearchProgressEvent.Checking("HaveIBeenPwned"))
    try {
        val breaches = RetrofitClient.hibpService.getBreaches(primaryQuery, key)
        if (breaches.isEmpty()) {
            send(SearchProgressEvent.NotFound("HaveIBeenPwned"))
        } else {
            val detail = breaches.take(3).mapNotNull { it.title }.joinToString(", ")
            sources.add(DataSource("HaveIBeenPwned", "https://haveibeenpwned.com/account/${encode(primaryQuery)}", Date(), 0.95))
            metadata["hibp_breaches"] = breaches.size.toString()
            metadata["hibp_names"] = detail
            send(SearchProgressEvent.Found("HaveIBeenPwned", "${ breaches.size } breaches: $detail"))
        }
    } catch (_: Exception) {
        send(SearchProgressEvent.Blocked("HaveIBeenPwned"))
    }
}
```

- [ ] **Step 3: Add Hunter.io call for domain searches**

In the `"domain", "ip"` branch, add after existing launches:

```kotlin
launch {
    val key = apiKeys.getKey("hunter")
    if (key.isNullOrBlank()) {
        send(SearchProgressEvent.NotFound("Hunter.io (no key)"))
        return@launch
    }
    send(SearchProgressEvent.Checking("Hunter.io"))
    try {
        val result = RetrofitClient.hunterService.domainSearch(primaryQuery, key)
        val emails = result.data?.emails?.mapNotNull { it.value } ?: emptyList()
        if (emails.isEmpty()) {
            send(SearchProgressEvent.NotFound("Hunter.io"))
        } else {
            sources.add(DataSource("Hunter.io", "https://hunter.io/domain-search/$primaryQuery", Date(), 0.9))
            metadata["hunter_emails"] = emails.take(10).joinToString(", ")
            metadata["hunter_org"] = result.data?.organization ?: ""
            send(SearchProgressEvent.Found("Hunter.io", "${emails.size} emails found"))
        }
    } catch (_: Exception) {
        send(SearchProgressEvent.Blocked("Hunter.io"))
    }
}
```

- [ ] **Step 4: Add Pipl call for person searches**

In the `"person", "comprehensive"` branch, add after existing launches:

```kotlin
launch {
    val key = apiKeys.getKey("pipl")
    if (key.isNullOrBlank()) {
        send(SearchProgressEvent.NotFound("Pipl (no key)"))
        return@launch
    }
    send(SearchProgressEvent.Checking("Pipl"))
    try {
        val nameParts = primaryQuery.trim().split("\\s+".toRegex())
        val first = nameParts.firstOrNull()
        val last = if (nameParts.size > 1) nameParts.last() else null
        val result = RetrofitClient.piplService.search(
            apiKey = key,
            firstName = first,
            lastName = last
        )
        val person = result.person
        if (person == null) {
            send(SearchProgressEvent.NotFound("Pipl"))
        } else {
            val displayName = person.names?.firstOrNull()?.display ?: primaryQuery
            sources.add(DataSource("Pipl", "https://pipl.com/", Date(), 0.95))
            metadata["pipl_name"] = displayName
            metadata["pipl_emails"] = person.emails?.mapNotNull { it.address }?.joinToString(", ") ?: ""
            metadata["pipl_phones"] = person.phones?.mapNotNull { it.display }?.joinToString(", ") ?: ""
            send(SearchProgressEvent.Found("Pipl", "Profile found: $displayName"))
        }
    } catch (_: Exception) {
        send(SearchProgressEvent.Blocked("Pipl"))
    }
}
```

- [ ] **Step 5: Add missing import to OsintRepository.kt**

Add at top of imports:
```kotlin
import com.twoskoops707.sixdegrees.data.remote.RetrofitClient
```

- [ ] **Step 6: Verify ApiKeyManager.getKey() signature**

```bash
grep -n "fun getKey\|fun get(" app/src/main/java/com/twoskoops707/sixdegrees/data/ApiKeyManager.kt | head -10
```

If the method is named differently, update the calls above to match.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/repository/OsintRepository.kt
git commit -m "feat: wire HIBP, Hunter.io, and Pipl API calls into search flow"
```

---

### Task 7: Remove dead code — SlideshowFragment and ReflowFragment

**Files:**
- Delete: `app/src/main/java/com/twoskoops707/sixdegrees/ui/slideshow/SlideshowFragment.kt`
- Delete: `app/src/main/java/com/twoskoops707/sixdegrees/ui/slideshow/SlideshowViewModel.kt`
- Delete: `app/src/main/java/com/twoskoops707/sixdegrees/ui/reflow/ReflowFragment.kt`
- Delete: `app/src/main/java/com/twoskoops707/sixdegrees/ui/reflow/ReflowViewModel.kt`
- Delete: `app/src/main/res/layout/fragment_slideshow.xml`
- Delete: `app/src/main/res/layout/fragment_reflow.xml`

Both fragments are confirmed absent from `mobile_navigation.xml`.

- [ ] **Step 1: Verify no references before deleting**

```bash
grep -r "SlideshowFragment\|SlideshowViewModel\|ReflowFragment\|ReflowViewModel\|nav_slideshow\|nav_reflow" \
    app/src/main/java app/src/main/res --include="*.kt" --include="*.xml" -l
```

Expected: no output. If any files are listed, open them and remove the references before deleting.

- [ ] **Step 2: Delete the files**

```bash
rm app/src/main/java/com/twoskoops707/sixdegrees/ui/slideshow/SlideshowFragment.kt \
   app/src/main/java/com/twoskoops707/sixdegrees/ui/slideshow/SlideshowViewModel.kt \
   app/src/main/java/com/twoskoops707/sixdegrees/ui/reflow/ReflowFragment.kt \
   app/src/main/java/com/twoskoops707/sixdegrees/ui/reflow/ReflowViewModel.kt \
   app/src/main/res/layout/fragment_slideshow.xml \
   app/src/main/res/layout/fragment_reflow.xml
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: remove unused SlideshowFragment and ReflowFragment"
```

---

### Task 8: Verify ApiKeyManager.getKey() method exists

**Files:**
- Read: `app/src/main/java/com/twoskoops707/sixdegrees/data/ApiKeyManager.kt`

- [ ] **Step 1: Read ApiKeyManager and confirm the key retrieval method**

```bash
grep -n "fun " app/src/main/java/com/twoskoops707/sixdegrees/data/ApiKeyManager.kt
```

- [ ] **Step 2: If `getKey(name)` doesn't exist, add it**

If ApiKeyManager uses a different pattern (e.g., `getHibpKey()` per-service), add a generic accessor:

```kotlin
fun getKey(service: String): String? {
    return encryptedPrefs.getString(service, null)?.takeIf { it.isNotBlank() }
}
```

Add inside the ApiKeyManager class body. The encrypted prefs field name may vary — check existing methods to find the `EncryptedSharedPreferences` field name.

- [ ] **Step 3: Commit if changed**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/ApiKeyManager.kt
git commit -m "fix: add generic getKey() accessor to ApiKeyManager"
```

---

### Task 9: Static flow verification — all 7 search types

This task is code review only — no changes unless a bug is found.

- [ ] **Step 1: Verify Person flow**

Read `SearchFragment.kt` and confirm:
- Form fields `input_first_name`, `input_last_name`, `input_city`, `input_state` exist in `fragment_search.xml`
- Tapping search navigates to `nav_search_progress` with `query` and `type="person"` args
- `SearchProgressFragment` collects the `searchWithProgress` flow and renders each `SearchProgressEvent` type

```bash
grep -n "action_search_to_progress\|nav_search_progress" \
    app/src/main/java/com/twoskoops707/sixdegrees/ui/search/SearchFragment.kt
```

- [ ] **Step 2: Verify Complete event → Results navigation**

```bash
grep -n "action_progress_to_results\|Complete" \
    app/src/main/java/com/twoskoops707/sixdegrees/ui/search/SearchProgressFragment.kt
```

Expected: `SearchProgressEvent.Complete` triggers `findNavController().navigate(R.id.action_progress_to_results, ...)`.

- [ ] **Step 3: Verify ResultsFragment loads report from Room**

```bash
grep -n "getReportById\|reportId" \
    app/src/main/java/com/twoskoops707/sixdegrees/ui/results/ResultsViewModel.kt | head -20
```

Expected: `ResultsViewModel` calls `repository.getReportById(reportId)` and exposes it as a StateFlow/LiveData.

- [ ] **Step 4: Verify History save**

```bash
grep -n "insertReport\|saveReport" \
    app/src/main/java/com/twoskoops707/sixdegrees/data/repository/OsintRepository.kt
```

Expected: `saveReport()` is called at the end of `searchWithProgress()` with all accumulated sources and metadata (line ~415 in OsintRepository.kt — confirmed present).

- [ ] **Step 5: Fix any broken navigation found**

If step 2 shows `Complete` is NOT wired to navigation, add to `SearchProgressFragment.kt` in the event collection block:

```kotlin
is SearchProgressEvent.Complete -> {
    val action = SearchProgressFragmentDirections
        .actionProgressToResults(
            searchQuery = args.query,
            searchType = args.type,
            reportId = event.reportId
        )
    findNavController().navigate(action)
}
```

- [ ] **Step 6: Commit any fixes found**

```bash
git add -A
git commit -m "fix: wire search Complete event to results navigation"
```

---

### Task 10: Trigger GitHub Actions build

- [ ] **Step 1: Push all commits**

```bash
git push origin main
```

- [ ] **Step 2: Monitor build**

```bash
gh run list --limit 5
gh run watch
```

Expected: build succeeds and APK artifact is available.
