# 3-Stage Search Funnel + Entity Type Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the 3-stage OSINT funnel actually differentiate between stages, and add first-class UI support for all entity types.

**Architecture:** Stage 1 = fast scrapers only → pick 1-4 people; Stage 2 = moderate APIs + username combos + social → narrow to 1-2; Stage 3 = every API + full Sherlock/Maigret + dark web + court + all heavy lookups. Each stage is a distinct Kotlin function dispatched by round number in `searchWithProgress`. New entity type tabs (Email, Phone, Username, IP, Vehicle, WiFi, Hash) added to `SearchFragment` with matching forms in XML.

**Tech Stack:** Kotlin, Coroutines (`coroutineScope` + `launch`/`async`), OkHttp, Retrofit, Room, Navigation Component, ViewBinding

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `data/repository/OsintRepository.kt` | Modify lines 286-313, 319, 4033-4141 | Dispatch by round; trim comprehensiveSearch; create deepDiveSearch |
| `ui/search/SearchFragment.kt` | Modify `setupEntityTypeSelector()` + `doSearch()` + `clearCurrentForm()` | Add 7 new entity type tabs |
| `res/layout/fragment_search.xml` | Modify | Add type cards + forms for Email/Phone/Username/IP/Vehicle/WiFi/Hash |
| `ui/search/SearchProgressFragment.kt` | Modify `estimatedTotal` block (lines 69-78) | Update estimated counts for new types |
| `ui/candidates/CandidateSelectionViewModel.kt` | Verify/update `buildRefinedQuery()` | Ensure enriched fields pass between rounds |

---

### Task 1: Trim `comprehensiveSearch` — move heavy ops to a new stub

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/repository/OsintRepository.kt:4119-4141`

Current lines 4119–4141 inside `comprehensiveSearch`:
```kotlin
val dwQuery = if (name.isNotBlank()) {
    if (location.isNotBlank()) "$name $location" else name
} else email.ifBlank { username }

if (dwQuery.isNotBlank()) {
    launch { ahmiaSearch(dwQuery, meta, sources, emit) }
    launch { pasteDumpSearch(dwQuery, meta, sources, emit) }
    launch { torchSearch(dwQuery, meta, sources, emit) }
}

val vin = fields["vin"] ?: ""
if (vin.isNotBlank()) {
    launch { nhtsaVehicleSearch(vin, meta, sources, emit) }
}

if (name.isNotBlank()) {
    launch { fbiFugitivesSearch(name, meta, sources, emit) }
    launch { interpolRedNoticesSearch(name, meta, sources, emit) }
    if (apiKeyManager.wigleKey.isNotBlank()) {
        launch { wigleWifiSearch(name, true, meta, sources, emit) }
    }
}
```

- [ ] **Step 1: Delete the dark-web + FBI/Interpol + WiGLE launches from `comprehensiveSearch`**

Replace lines 4119–4141 (the entire block from `val dwQuery` to the closing `}` of `comprehensiveSearch`) with:

```kotlin
    }
```

That is, the function ends after the `if (image.isNotBlank() ...) { launch { imageSearch(...) } }` block. The dark web / FBI / Interpol / WiGLE moves to `deepDiveSearch` in Task 2.

- [ ] **Step 2: Verify `comprehensiveSearch` now ends at line ~4118**

Check that the function closing brace is `}` right after the `if (image.isNotBlank())` block. No dark-web, no FBI, no Interpol.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/repository/OsintRepository.kt
git commit -m "refactor: strip heavy ops from comprehensiveSearch (moving to deepDiveSearch)"
```

---

### Task 2: Create `deepDiveSearch` function

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/repository/OsintRepository.kt`
- Insert after line ~4141 (after `comprehensiveSearch` closing brace)

- [ ] **Step 1: Add `deepDiveSearch` function**

Insert this entire function after the closing `}` of `comprehensiveSearch`:

```kotlin
private suspend fun deepDiveSearch(
    query: String,
    meta: ConcurrentHashMap<String, String>,
    sources: MutableList<DataSource>,
    emit: suspend (SearchProgressEvent) -> Unit
) = coroutineScope {
    val fields = query.split("|").mapNotNull {
        val parts = it.split("=", limit = 2)
        if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
    }.toMap()
    val name = fields["name"] ?: ""
    val email = fields["email"] ?: ""
    val phone = fields["phone"] ?: ""
    val username = fields["username"] ?: meta["comp_derived_usernames"]?.split(", ")?.firstOrNull() ?: ""
    val city = fields["city"] ?: fields["location"] ?: ""
    val state = fields["state"] ?: ""
    val location = listOf(city, state).filter { it.isNotBlank() }.joinToString(", ")
    val vin = fields["vin"] ?: ""
    val context = fields["context"] ?: ""

    val dwQuery = if (name.isNotBlank()) {
        if (location.isNotBlank()) "$name $location" else name
    } else email.ifBlank { username }

    if (dwQuery.isNotBlank()) {
        launch { ahmiaSearch(dwQuery, meta, sources, emit) }
        launch { pasteDumpSearch(dwQuery, meta, sources, emit) }
        launch { torchSearch(dwQuery, meta, sources, emit) }
    }
    if (vin.isNotBlank()) {
        launch { nhtsaVehicleSearch(vin, meta, sources, emit) }
    }
    if (name.isNotBlank()) {
        launch { fbiFugitivesSearch(name, meta, sources, emit) }
        launch { interpolRedNoticesSearch(name, meta, sources, emit) }
    }

    val nameParts = name.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
    val derivedUsernames = mutableListOf<String>()
    if (nameParts.size >= 2) {
        val fn = nameParts.first().lowercase()
        val ln = nameParts.last().lowercase()
        derivedUsernames.addAll(listOf(
            "$fn$ln", "${fn[0]}$ln", "$fn.$ln", "$fn-$ln",
            "$fn${ln[0]}", "${fn[0]}.$ln", "$fn$ln${name.hashCode().and(0xFF) % 100}",
            "$ln$fn", "$ln.${fn[0]}", "$fn${ln.takeLast(3)}"
        ))
    }
    if (email.isNotBlank() && email.contains("@")) {
        val lp = email.substringBefore("@").lowercase().trim()
        if (lp.isNotBlank()) derivedUsernames.add(lp)
    }
    if (username.isNotBlank()) derivedUsernames.add(0, username)
    val allUsernames = derivedUsernames.distinct().take(10)

    if (allUsernames.isNotEmpty()) {
        allUsernames.forEach { u ->
            launch { usernameSearch(u, meta, sources, emit) }
        }
    }

    if (email.isNotBlank()) {
        launch {
            if (apiKeyManager.dehashed.isNotBlank()) {
                val encoded = URLEncoder.encode(email, "UTF-8")
                emit(SearchProgressEvent.Checking("Dehashed"))
                try {
                    val creds = okhttp3.Credentials.basic(apiKeyManager.dehashedUser, apiKeyManager.dehashed)
                    val req = Request.Builder()
                        .url("https://api.dehashed.com/search?query=email:$encoded")
                        .addHeader("Authorization", creds)
                        .addHeader("Accept", "application/json")
                        .build()
                    val resp = httpClient.newCall(req).execute()
                    val body = resp.body?.string() ?: ""; resp.close()
                    if (resp.isSuccessful && body.contains("entries")) {
                        val count = Regex("\"total\":(\\d+)").find(body)?.groupValues?.get(1) ?: "?"
                        meta["dehashed_hits"] = count
                        sources.add(DataSource("Dehashed", null, java.util.Date(), 0.95))
                        emit(SearchProgressEvent.Found("Dehashed", "$count breach records"))
                    } else emit(SearchProgressEvent.NotFound("Dehashed"))
                } catch (e: Exception) { emit(SearchProgressEvent.Failed("Dehashed", e.message ?: "")) }
            }
        }
        if (apiKeyManager.intelxKey.isNotBlank()) {
            launch {
                emit(SearchProgressEvent.Checking("IntelX"))
                try {
                    val encoded = URLEncoder.encode(email, "UTF-8")
                    val req = Request.Builder()
                        .url("https://2.intelx.io/phonebook/search?term=$encoded&target=1&maxresults=10&timeout=20&datefrom=&dateto=&sort=2&media=0&terminate=[]")
                        .addHeader("x-key", apiKeyManager.intelxKey)
                        .addHeader("Accept", "application/json")
                        .build()
                    val resp = httpClient.newCall(req).execute()
                    val body = resp.body?.string() ?: ""; resp.close()
                    if (resp.isSuccessful && body.contains("\"id\"")) {
                        meta["intelx_email_result"] = body.take(500)
                        sources.add(DataSource("IntelX", null, java.util.Date(), 0.9))
                        emit(SearchProgressEvent.Found("IntelX", "Phonebook search hit"))
                    } else emit(SearchProgressEvent.NotFound("IntelX"))
                } catch (e: Exception) { emit(SearchProgressEvent.Failed("IntelX", e.message ?: "")) }
            }
        }
    }

    if (apiKeyManager.pulsediveKey.isNotBlank() && (email.isNotBlank() || name.isNotBlank())) {
        launch { pulsediveSearch(email.ifBlank { name }, meta, sources, emit) }
    }

    if (apiKeyManager.leakixKey.isNotBlank() && name.isNotBlank()) {
        launch {
            emit(SearchProgressEvent.Checking("LeakIX"))
            try {
                val encoded = URLEncoder.encode(name, "UTF-8")
                val req = Request.Builder()
                    .url("https://leakix.net/search?scope=leak&q=$encoded&page=0")
                    .addHeader("api-key", apiKeyManager.leakixKey)
                    .addHeader("Accept", "application/json")
                    .build()
                val resp = httpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""; resp.close()
                if (resp.isSuccessful && body.contains("\"EventType\"")) {
                    meta["leakix_result"] = body.take(300)
                    sources.add(DataSource("LeakIX", null, java.util.Date(), 0.85))
                    emit(SearchProgressEvent.Found("LeakIX", "Leak data found"))
                } else emit(SearchProgressEvent.NotFound("LeakIX"))
            } catch (e: Exception) { emit(SearchProgressEvent.Failed("LeakIX", e.message ?: "")) }
        }
    }

    if (name.isNotBlank()) {
        launch {
            emit(SearchProgressEvent.Checking("CourtListener"))
            try {
                val encoded = URLEncoder.encode(name, "UTF-8")
                val req = Request.Builder()
                    .url("https://www.courtlistener.com/api/rest/v3/people/?name_last=${encoded.substringAfterLast('+')}&format=json")
                    .addHeader("User-Agent", "SixDegrees-OSINT/1.0")
                    .build()
                val resp = httpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""; resp.close()
                if (resp.isSuccessful && body.contains("\"count\"")) {
                    val count = Regex("\"count\":(\\d+)").find(body)?.groupValues?.get(1) ?: "0"
                    if (count != "0") {
                        meta["courtlistener_hits"] = count
                        sources.add(DataSource("CourtListener", null, java.util.Date(), 0.8))
                        emit(SearchProgressEvent.Found("CourtListener", "$count court records"))
                    } else emit(SearchProgressEvent.NotFound("CourtListener"))
                } else emit(SearchProgressEvent.NotFound("CourtListener"))
            } catch (e: Exception) { emit(SearchProgressEvent.Failed("CourtListener", e.message ?: "")) }
        }
    }

    if (apiKeyManager.wigleKey.isNotBlank() && name.isNotBlank()) {
        launch { wigleWifiSearch(name, true, meta, sources, emit) }
    }

    val sherlockOut = "/storage/emulated/0/.6degrees/sherlock_${System.currentTimeMillis()}.txt"
    val primaryU = allUsernames.firstOrNull() ?: ""
    if (primaryU.isNotBlank()) {
        val sherlockResult = runTermuxTool(
            "/data/data/com.termux/files/usr/bin/sherlock",
            listOf("--timeout", "10", "--print-found", "--output", sherlockOut, primaryU),
            sherlockOut, timeoutMs = 90000
        )
        if (!sherlockResult.isNullOrBlank()) {
            val foundLines = sherlockResult.lines().filter { it.contains("[+]") }
            if (foundLines.isNotEmpty()) {
                meta["sherlock_found"] = foundLines.joinToString("\n")
                sources.add(DataSource("Sherlock", null, java.util.Date(), 0.9))
                emit(SearchProgressEvent.Found("Sherlock", "${foundLines.size} profiles found"))
            } else emit(SearchProgressEvent.NotFound("Sherlock"))
        }
    }
}
```

- [ ] **Step 2: Build check — confirm no compile errors by checking imports are present**

`android.util.Base64`, `java.net.URLEncoder`, `okhttp3.Request`, `okhttp3.Credentials` — all already imported in the file.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/repository/OsintRepository.kt
git commit -m "feat: add deepDiveSearch (Stage 3) — dark web, full username scan, court, breach, Sherlock"
```

---

### Task 3: Wire round-based dispatch in `searchWithProgress`

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/repository/OsintRepository.kt:300-313`

Current block (lines 300-313):
```kotlin
"comprehensive" -> {
    comprehensiveSearch(cleanQuery, metadata, sources, emit)
    val fields = cleanQuery.split("|").mapNotNull {
        val p = it.split("=", limit = 2)
        if (p.size == 2) p[0].trim() to p[1].trim() else null
    }.toMap()
    cascadeDiscoveredContacts(fields, metadata, sources, emit)
}
```

- [ ] **Step 1: Replace the `"comprehensive"` branch with round-aware dispatch**

```kotlin
"comprehensive" -> {
    if (round >= 3) {
        comprehensiveSearch(cleanQuery, metadata, sources, emit)
        deepDiveSearch(cleanQuery, metadata, sources, emit)
    } else {
        comprehensiveSearch(cleanQuery, metadata, sources, emit)
    }
    val fields = cleanQuery.split("|").mapNotNull {
        val p = it.split("=", limit = 2)
        if (p.size == 2) p[0].trim() to p[1].trim() else null
    }.toMap()
    cascadeDiscoveredContacts(fields, metadata, sources, emit)
}
```

- [ ] **Step 2: Update `estimatedTotal` in `SearchProgressFragment` for comprehensive round 3**

In `SearchProgressFragment.kt` lines 69-78, `"comprehensive"` is not currently a case. Add it:

```kotlin
estimatedTotal = when (type) {
    "scan" -> 6
    "person" -> 29
    "username" -> 80
    "ip", "domain" -> 20
    "email" -> 15
    "company" -> 13
    "phone" -> 7
    "comprehensive" -> if (round >= 3) 60 else 35
    "vehicle", "vin" -> 5
    "wifi", "ssid", "mac" -> 4
    "hash" -> 3
    "cve" -> 3
    "trademark" -> 3
    else -> 10
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/repository/OsintRepository.kt
git add app/src/main/java/com/twoskoops707/sixdegrees/ui/search/SearchProgressFragment.kt
git commit -m "feat: wire 3-stage round dispatch — Stage 3 runs comprehensiveSearch + deepDiveSearch"
```

---

### Task 4: Update `CandidateSelectionFragment` round label text

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/ui/candidates/CandidateSelectionFragment.kt`

- [ ] **Step 1: Read `CandidateSelectionFragment.kt` to find the round-label and subtitle text**

Look for the text that says something like "Round 1 — Discovery" or "Select the person you're looking for". Find where it sets the subtitle/instruction text for each round.

- [ ] **Step 2: Update stage labels**

Find the round-based label logic and set:
- Round 1: `"Stage 1 of 3 — Quick Discovery\nSelect up to 4 people who might be your subject"`
- Round 2: `"Stage 2 of 3 — Comprehensive\nNarrow down to 1-2 candidates"`
- Round 3 (shouldn't reach candidates, but handle): `"Stage 3 of 3 — Deep Dive\nConfirm the final subject"`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/ui/candidates/CandidateSelectionFragment.kt
git commit -m "feat: update candidate stage labels to reflect 3-stage funnel"
```

---

### Task 5: Add entity type forms to `fragment_search.xml`

**Files:**
- Modify: `app/src/main/res/layout/fragment_search.xml`

The layout already has cards for `cardTypePerson`, `cardTypeCompany`, `cardTypeDomain`. We need to add 5 more: Email, Phone, Username, Vehicle/VIN, WiFi.

- [ ] **Step 1: Add type selector cards after the Domain card**

Find the Domain card (`cardTypeDomain`) in the HorizontalScrollView/LinearLayout that holds the three type cards. After the Domain card, insert:

```xml
<com.google.android.material.card.MaterialCardView
    android:id="@+id/cardTypeEmail"
    android:layout_width="80dp"
    android:layout_height="72dp"
    android:layout_marginStart="8dp"
    app:cardCornerRadius="12dp"
    app:strokeWidth="1dp"
    app:strokeColor="@color/border">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:gravity="center"
        android:padding="8dp">
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@" android:textSize="20sp" android:textStyle="bold"
            android:textColor="@color/text_primary" />
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="Email" android:textSize="10sp" android:textColor="@color/text_secondary"
            android:paddingTop="2dp" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>

<com.google.android.material.card.MaterialCardView
    android:id="@+id/cardTypePhone"
    android:layout_width="80dp"
    android:layout_height="72dp"
    android:layout_marginStart="8dp"
    app:cardCornerRadius="12dp"
    app:strokeWidth="1dp"
    app:strokeColor="@color/border">
    <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent"
        android:orientation="vertical" android:gravity="center" android:padding="8dp">
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="📞" android:textSize="18sp" />
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="Phone" android:textSize="10sp" android:textColor="@color/text_secondary"
            android:paddingTop="2dp" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>

<com.google.android.material.card.MaterialCardView
    android:id="@+id/cardTypeUsername"
    android:layout_width="80dp"
    android:layout_height="72dp"
    android:layout_marginStart="8dp"
    app:cardCornerRadius="12dp"
    app:strokeWidth="1dp"
    app:strokeColor="@color/border">
    <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent"
        android:orientation="vertical" android:gravity="center" android:padding="8dp">
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@U" android:textSize="16sp" android:textStyle="bold"
            android:textColor="@color/text_primary" />
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="Username" android:textSize="9sp" android:textColor="@color/text_secondary"
            android:paddingTop="2dp" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>

<com.google.android.material.card.MaterialCardView
    android:id="@+id/cardTypeVehicle"
    android:layout_width="80dp"
    android:layout_height="72dp"
    android:layout_marginStart="8dp"
    app:cardCornerRadius="12dp"
    app:strokeWidth="1dp"
    app:strokeColor="@color/border">
    <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent"
        android:orientation="vertical" android:gravity="center" android:padding="8dp">
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="🚗" android:textSize="18sp" />
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="Vehicle" android:textSize="10sp" android:textColor="@color/text_secondary"
            android:paddingTop="2dp" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>

<com.google.android.material.card.MaterialCardView
    android:id="@+id/cardTypeWifi"
    android:layout_width="80dp"
    android:layout_height="72dp"
    android:layout_marginStart="8dp"
    app:cardCornerRadius="12dp"
    app:strokeWidth="1dp"
    app:strokeColor="@color/border">
    <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent"
        android:orientation="vertical" android:gravity="center" android:padding="8dp">
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="📡" android:textSize="18sp" />
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="WiFi" android:textSize="10sp" android:textColor="@color/text_secondary"
            android:paddingTop="2dp" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 2: Add form containers after `formDomain`**

After the closing tag of `formDomain` LinearLayout, add these 5 form containers:

```xml
<!-- ── Email Form ── -->
<LinearLayout
    android:id="@+id/formEmail"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingHorizontal="16dp"
    android:visibility="gone">

    <com.google.android.material.textfield.TextInputLayout
        style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Email Address"
        android:layout_marginBottom="8dp">
        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/inputEmailValue"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="textEmailAddress"
            android:imeOptions="actionSearch" />
    </com.google.android.material.textfield.TextInputLayout>
</LinearLayout>

<!-- ── Phone Form ── -->
<LinearLayout
    android:id="@+id/formPhone"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingHorizontal="16dp"
    android:visibility="gone">

    <com.google.android.material.textfield.TextInputLayout
        style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Phone Number"
        android:layout_marginBottom="8dp">
        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/inputPhoneValue"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="phone"
            android:imeOptions="actionSearch" />
    </com.google.android.material.textfield.TextInputLayout>
</LinearLayout>

<!-- ── Username Form ── -->
<LinearLayout
    android:id="@+id/formUsername"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingHorizontal="16dp"
    android:visibility="gone">

    <com.google.android.material.textfield.TextInputLayout
        style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Username / Handle"
        android:layout_marginBottom="8dp">
        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/inputUsernameValue"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="textNoSuggestions"
            android:imeOptions="actionSearch" />
    </com.google.android.material.textfield.TextInputLayout>
</LinearLayout>

<!-- ── Vehicle/VIN Form ── -->
<LinearLayout
    android:id="@+id/formVehicle"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingHorizontal="16dp"
    android:visibility="gone">

    <com.google.android.material.textfield.TextInputLayout
        style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="VIN or Vehicle Make/Model"
        android:layout_marginBottom="8dp">
        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/inputVehicleValue"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="textCapCharacters"
            android:imeOptions="actionSearch" />
    </com.google.android.material.textfield.TextInputLayout>
</LinearLayout>

<!-- ── WiFi/SSID Form ── -->
<LinearLayout
    android:id="@+id/formWifi"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingHorizontal="16dp"
    android:visibility="gone">

    <com.google.android.material.textfield.TextInputLayout
        style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="WiFi SSID or MAC Address"
        android:layout_marginBottom="8dp">
        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/inputWifiValue"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="textNoSuggestions"
            android:imeOptions="actionSearch" />
    </com.google.android.material.textfield.TextInputLayout>
</LinearLayout>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/fragment_search.xml
git commit -m "feat: add Email/Phone/Username/Vehicle/WiFi type selector cards and forms"
```

---

### Task 6: Wire new entity tabs in `SearchFragment.kt`

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/ui/search/SearchFragment.kt`

- [ ] **Step 1: Update `setupEntityTypeSelector()` to handle 8 types**

Replace the current `setupEntityTypeSelector()` body with:

```kotlin
private fun setupEntityTypeSelector() {
    val colorPrimary = com.google.android.material.R.attr.colorPrimary
    val bgDark = "#0A0F1E"

    fun selectType(type: String) {
        currentType = type
        attachedImageUri = null
        binding.tvImageAttached.visibility = View.GONE

        val forms = mapOf(
            "person" to binding.formPerson,
            "company" to binding.formCompany,
            "domain" to binding.formDomain,
            "email" to binding.formEmail,
            "phone" to binding.formPhone,
            "username" to binding.formUsername,
            "vehicle" to binding.formVehicle,
            "wifi" to binding.formWifi
        )
        forms.forEach { (t, form) ->
            form.visibility = if (t == type) View.VISIBLE else View.GONE
        }

        val tv = android.util.TypedValue()
        requireContext().theme.resolveAttribute(colorPrimary, tv, true)
        val accentColor = tv.data
        val bgColor = android.graphics.Color.parseColor(bgDark)
        val strokeInactive = ContextCompat.getColor(requireContext(), R.color.border)

        val cards = mapOf(
            "person" to binding.cardTypePerson,
            "company" to binding.cardTypeCompany,
            "domain" to binding.cardTypeDomain,
            "email" to binding.cardTypeEmail,
            "phone" to binding.cardTypePhone,
            "username" to binding.cardTypeUsername,
            "vehicle" to binding.cardTypeVehicle,
            "wifi" to binding.cardTypeWifi
        )
        cards.forEach { (t, card) ->
            val active = t == type
            card.setCardBackgroundColor(if (active) accentColor else bgColor)
            card.strokeColor = if (active) android.graphics.Color.TRANSPARENT else strokeInactive
        }
    }

    binding.cardTypePerson.setOnClickListener { selectType("person") }
    binding.cardTypeCompany.setOnClickListener { selectType("company") }
    binding.cardTypeDomain.setOnClickListener { selectType("domain") }
    binding.cardTypeEmail.setOnClickListener { selectType("email") }
    binding.cardTypePhone.setOnClickListener { selectType("phone") }
    binding.cardTypeUsername.setOnClickListener { selectType("username") }
    binding.cardTypeVehicle.setOnClickListener { selectType("vehicle") }
    binding.cardTypeWifi.setOnClickListener { selectType("wifi") }

    selectType("person")
}
```

- [ ] **Step 2: Add new cases to `doSearch()`**

After the `"domain"` case and before the closing `}` of `when (currentType)`, add:

```kotlin
"email" -> {
    val value = binding.inputEmailValue.text?.toString()?.trim() ?: ""
    if (value.isBlank()) {
        Toast.makeText(requireContext(), "Enter an email address", Toast.LENGTH_SHORT).show()
        return
    }
    navigateToProgress(value, "email")
}

"phone" -> {
    val value = binding.inputPhoneValue.text?.toString()?.trim() ?: ""
    if (value.isBlank()) {
        Toast.makeText(requireContext(), "Enter a phone number", Toast.LENGTH_SHORT).show()
        return
    }
    navigateToProgress(value, "phone")
}

"username" -> {
    val value = binding.inputUsernameValue.text?.toString()?.trim() ?: ""
    if (value.isBlank()) {
        Toast.makeText(requireContext(), "Enter a username", Toast.LENGTH_SHORT).show()
        return
    }
    navigateToProgress(value, "username")
}

"vehicle" -> {
    val value = binding.inputVehicleValue.text?.toString()?.trim() ?: ""
    if (value.isBlank()) {
        Toast.makeText(requireContext(), "Enter a VIN or vehicle info", Toast.LENGTH_SHORT).show()
        return
    }
    val type = if (value.length == 17 && value.all { it.isLetterOrDigit() }) "vin" else "vehicle"
    navigateToProgress(value, type)
}

"wifi" -> {
    val value = binding.inputWifiValue.text?.toString()?.trim() ?: ""
    if (value.isBlank()) {
        Toast.makeText(requireContext(), "Enter an SSID or MAC address", Toast.LENGTH_SHORT).show()
        return
    }
    val type = if (value.matches(Regex("[0-9A-Fa-f:]{17}"))) "mac" else "wifi"
    navigateToProgress(value, type)
}
```

- [ ] **Step 3: Add new cases to `clearCurrentForm()`**

```kotlin
"email" -> binding.inputEmailValue.text?.clear()
"phone" -> binding.inputPhoneValue.text?.clear()
"username" -> binding.inputUsernameValue.text?.clear()
"vehicle" -> binding.inputVehicleValue.text?.clear()
"wifi" -> binding.inputWifiValue.text?.clear()
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/ui/search/SearchFragment.kt
git commit -m "feat: wire Email/Phone/Username/Vehicle/WiFi tabs in SearchFragment"
```

---

### Task 7: Progress screen — show stage labels for each round

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/ui/search/SearchProgressFragment.kt:87-91`

Current lines 87-91:
```kotlin
binding.chipSearchType.text = when {
    round > 1 -> "ROUND $round"
    type == "scan" -> "DISCOVERY"
    else -> type.uppercase()
}
```

- [ ] **Step 1: Replace chip label logic**

```kotlin
binding.chipSearchType.text = when {
    type == "scan" -> "STAGE 1 · DISCOVERY"
    type == "comprehensive" && round == 2 -> "STAGE 2 · COMPREHENSIVE"
    type == "comprehensive" && round >= 3 -> "STAGE 3 · DEEP DIVE"
    round > 1 -> "ROUND $round"
    else -> type.uppercase()
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/ui/search/SearchProgressFragment.kt
git commit -m "feat: show STAGE 1/2/3 labels in progress screen chip"
```

---

### Task 8: Self-review checklist

- [ ] **Search funnel stages are distinct:**
  - Stage 1 (scan): 4-6 scrapers, quick, candidate picker ✓
  - Stage 2 (comprehensive): 20-30 APIs, username combos, no dark web ✓ (after Task 1)
  - Stage 3 (comprehensive+deepDive): everything including dark web, FBI, Sherlock, court ✓ (after Tasks 1-3)

- [ ] **Entity type routing in `searchWithProgress` covers all new types:** vehicle/vin, wifi/ssid, mac, trademark, hash, cve (already present at lines 294-299), plus email/phone/username (already present at 287-289)

- [ ] **All new form IDs in XML match binding references in Kotlin:** `formEmail`, `formPhone`, `formUsername`, `formVehicle`, `formWifi`, `cardTypeEmail`, `cardTypePhone`, `cardTypeUsername`, `cardTypeVehicle`, `cardTypeWifi`, `inputEmailValue`, `inputPhoneValue`, `inputUsernameValue`, `inputVehicleValue`, `inputWifiValue`

- [ ] **`deepDiveSearch` doesn't duplicate what `comprehensiveSearch` already calls** (verified: personSearch/emailSearch/phoneSearch/usernameSearch stay in comprehensiveSearch; dark web / court / Sherlock / extra breach APIs are only in deepDiveSearch)

- [ ] **Build passes** — trigger GitHub Actions or verify locally that no missing binding IDs cause compile errors
