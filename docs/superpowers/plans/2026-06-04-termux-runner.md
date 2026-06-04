# Termux Command Runner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create `TermuxToolRunner.kt` that fires sherlock/maigret/holehe/theHarvester/nmap via Termux RPC and streams their parsed output as `SearchProgressEvent.Found` events into the existing search results flow.

**Architecture:** Single new file `TermuxToolRunner.kt` in `data/repository/`. Each tool method fires a `startForegroundService` Intent to `com.termux.app.RunCommandService`, writes output to `/storage/emulated/0/.6degrees/<tool>_<timestamp>.txt`, then polls that file with exponential backoff (3s/6s/12s/24s/48s, max 120s). Each parsed result emits `SearchProgressEvent.Found`. `OsintRepository.searchWithProgress()` launches the appropriate tool coroutines concurrently alongside existing scrapers inside the existing `Semaphore(5)` block.

**Tech Stack:** Android Intents (Termux RPC), Kotlin Coroutines + Flow, File I/O, JSON (Moshi for maigret/nmap output), no new dependencies

---

### Task 1: Create TermuxToolRunner skeleton with Termux detection

**Files:**
- Create: `app/src/main/java/com/twoskoops707/sixdegrees/data/repository/TermuxToolRunner.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.twoskoops707.sixdegrees.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class TermuxToolRunner(private val context: Context) {

    private val outputDir = File("/storage/emulated/0/.6degrees").also { it.mkdirs() }

    fun isTermuxInstalled(): Boolean = try {
        context.packageManager.getPackageInfo("com.termux", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun isToolInstalled(name: String): Boolean =
        File("/data/data/com.termux/files/usr/bin/$name").exists()

    private fun fireCommand(cmd: String) {
        val intent = Intent().apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", cmd))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        context.startForegroundService(intent)
    }

    private suspend fun pollFile(file: File, maxMs: Long = 120_000L): String? {
        val backoffs = longArrayOf(3000, 6000, 12000, 24000, 48000)
        var waited = 0L
        var backoffIdx = 0
        while (waited < maxMs) {
            val delay = if (backoffIdx < backoffs.size) backoffs[backoffIdx++] else backoffs.last()
            delay(delay)
            waited += delay
            if (file.exists() && file.length() > 0) return file.readText()
        }
        return null
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/repository/TermuxToolRunner.kt
git commit -m "feat: add TermuxToolRunner skeleton with Termux/tool detection and RPC helper"
```

---

### Task 2: Implement sherlock runner

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/repository/TermuxToolRunner.kt`

- [ ] **Step 1: Add `runSherlock` method inside `TermuxToolRunner` class**

Add after the `pollFile` method:

```kotlin
    fun runSherlock(username: String): Flow<SearchProgressEvent> = flow {
        if (!isTermuxInstalled()) return@flow
        if (!isToolInstalled("sherlock")) {
            emit(SearchProgressEvent.NotFound("sherlock", "Install sherlock in Termux: pip install sherlock-project"))
            return@flow
        }
        emit(SearchProgressEvent.Checking("sherlock"))
        val outFile = File(outputDir, "sherlock_${System.currentTimeMillis()}.txt")
        val cmd = "sherlock ${username.trim()} --output ${outFile.absolutePath} --print-found 2>/dev/null"
        try {
            fireCommand(cmd)
        } catch (_: Exception) {
            emit(SearchProgressEvent.Blocked("sherlock", "Could not reach Termux RPC"))
            return@flow
        }
        val content = pollFile(outFile)
        outFile.delete()
        if (content == null) {
            emit(SearchProgressEvent.Blocked("sherlock", "tool timed out"))
            return@flow
        }
        val urls = content.lines()
            .filter { it.startsWith("[+]") || it.contains("http") }
            .mapNotNull { line ->
                val urlMatch = Regex("https?://[^\\s]+").find(line)?.value ?: return@mapNotNull null
                val platform = line.substringBefore(":").trimStart('[', '+', ']', ' ').trim()
                    .ifBlank { urlMatch.substringAfter("://").substringBefore("/").substringBefore(".").replaceFirstChar { it.uppercase() } }
                Pair(platform, urlMatch)
            }
        if (urls.isEmpty()) {
            emit(SearchProgressEvent.NotFound("sherlock"))
            return@flow
        }
        urls.forEach { (platform, url) ->
            emit(SearchProgressEvent.Found("sherlock/$platform", url))
        }
    }.flowOn(Dispatchers.IO)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/repository/TermuxToolRunner.kt
git commit -m "feat: add sherlock runner — username search emits Found per social profile URL"
```

---

### Task 3: Implement maigret runner

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/repository/TermuxToolRunner.kt`

- [ ] **Step 1: Add `runMaigret` method inside `TermuxToolRunner` class**

Add after `runSherlock`:

```kotlin
    fun runMaigret(username: String): Flow<SearchProgressEvent> = flow {
        if (!isTermuxInstalled()) return@flow
        if (!isToolInstalled("maigret")) {
            emit(SearchProgressEvent.NotFound("maigret", "Install maigret in Termux: pip install maigret"))
            return@flow
        }
        emit(SearchProgressEvent.Checking("maigret"))
        val outFile = File(outputDir, "maigret_${System.currentTimeMillis()}.json")
        val cmd = "maigret ${username.trim()} -J simple --no-pics -o ${outFile.absolutePath} 2>/dev/null"
        try {
            fireCommand(cmd)
        } catch (_: Exception) {
            emit(SearchProgressEvent.Blocked("maigret", "Could not reach Termux RPC"))
            return@flow
        }
        val content = pollFile(outFile)
        outFile.delete()
        if (content == null) {
            emit(SearchProgressEvent.Blocked("maigret", "tool timed out"))
            return@flow
        }
        try {
            val moshi = com.squareup.moshi.Moshi.Builder().build()
            val mapAdapter = moshi.adapter<Map<String, Any>>(
                com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            )
            val root = mapAdapter.fromJson(content) ?: return@flow
            var emitted = 0
            @Suppress("UNCHECKED_CAST")
            (root as? Map<String, Any>)?.forEach { (site, data) ->
                val entry = data as? Map<String, Any> ?: return@forEach
                val status = entry["status"] as? String ?: ""
                val url = entry["url"] as? String ?: ""
                if ((status.contains("found", ignoreCase = true) || status == "claimed") && url.isNotEmpty()) {
                    emit(SearchProgressEvent.Found("maigret/$site", url))
                    emitted++
                }
            }
            if (emitted == 0) emit(SearchProgressEvent.NotFound("maigret"))
        } catch (_: Exception) {
            emit(SearchProgressEvent.Blocked("maigret", "output parse error"))
        }
    }.flowOn(Dispatchers.IO)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/repository/TermuxToolRunner.kt
git commit -m "feat: add maigret runner — parses JSON output into Found events per claimed site"
```

---

### Task 4: Implement holehe runner

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/repository/TermuxToolRunner.kt`

- [ ] **Step 1: Add `runHolehe` method inside `TermuxToolRunner` class**

Add after `runMaigret`:

```kotlin
    fun runHolehe(email: String): Flow<SearchProgressEvent> = flow {
        if (!isTermuxInstalled()) return@flow
        if (!isToolInstalled("holehe")) {
            emit(SearchProgressEvent.NotFound("holehe", "Install holehe in Termux: pip install holehe"))
            return@flow
        }
        emit(SearchProgressEvent.Checking("holehe"))
        val outFile = File(outputDir, "holehe_${System.currentTimeMillis()}.txt")
        val cmd = "holehe ${email.trim()} > ${outFile.absolutePath} 2>&1"
        try {
            fireCommand(cmd)
        } catch (_: Exception) {
            emit(SearchProgressEvent.Blocked("holehe", "Could not reach Termux RPC"))
            return@flow
        }
        val content = pollFile(outFile)
        outFile.delete()
        if (content == null) {
            emit(SearchProgressEvent.Blocked("holehe", "tool timed out"))
            return@flow
        }
        val found = content.lines()
            .filter { line ->
                line.contains("[+]") || (line.contains("used") && !line.contains("not used", ignoreCase = true))
            }
            .mapNotNull { line ->
                val service = Regex("\\[\\+\\]\\s*(.+)").find(line)?.groupValues?.get(1)?.trim()
                    ?: line.substringBefore(":").trim().ifBlank { null }
                service?.takeIf { it.isNotBlank() && it.length < 60 }
            }
            .distinct()
        if (found.isEmpty()) {
            emit(SearchProgressEvent.NotFound("holehe"))
            return@flow
        }
        found.forEach { service ->
            emit(SearchProgressEvent.Found("holehe/$service", "Email registered on: $service"))
        }
    }.flowOn(Dispatchers.IO)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/repository/TermuxToolRunner.kt
git commit -m "feat: add holehe runner — email registration check emits Found per service"
```

---

### Task 5: Implement theHarvester runner

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/repository/TermuxToolRunner.kt`

- [ ] **Step 1: Add `runTheHarvester` method inside `TermuxToolRunner` class**

Add after `runHolehe`:

```kotlin
    fun runTheHarvester(domain: String): Flow<SearchProgressEvent> = flow {
        if (!isTermuxInstalled()) return@flow
        if (!isToolInstalled("theHarvester")) {
            emit(SearchProgressEvent.NotFound("theHarvester", "Install theHarvester in Termux: pip install theHarvester"))
            return@flow
        }
        emit(SearchProgressEvent.Checking("theHarvester"))
        val outFile = File(outputDir, "harvester_${System.currentTimeMillis()}.txt")
        val cmd = "theHarvester -d ${domain.trim()} -b duckduckgo,bing,google -f ${outFile.absolutePath} 2>/dev/null"
        try {
            fireCommand(cmd)
        } catch (_: Exception) {
            emit(SearchProgressEvent.Blocked("theHarvester", "Could not reach Termux RPC"))
            return@flow
        }
        val content = pollFile(outFile, maxMs = 120_000L)
        outFile.delete()
        if (content == null) {
            emit(SearchProgressEvent.Blocked("theHarvester", "tool timed out"))
            return@flow
        }
        var emitted = 0
        val emailSection = Regex("""\[\*\] Emails found:(.*?)(?:\[\*\]|$)""", RegexOption.DOT_MATCHES_ALL).find(content)
        emailSection?.groupValues?.get(1)?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() && it.contains("@") }
            ?.distinct()
            ?.forEach { email ->
                emit(SearchProgressEvent.Found("theHarvester/email", email))
                emitted++
            }
        val hostSection = Regex("""\[\*\] Hosts found:(.*?)(?:\[\*\]|$)""", RegexOption.DOT_MATCHES_ALL).find(content)
        hostSection?.groupValues?.get(1)?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() && it.contains(".") }
            ?.distinct()
            ?.forEach { host ->
                emit(SearchProgressEvent.Found("theHarvester/host", host))
                emitted++
            }
        if (emitted == 0) emit(SearchProgressEvent.NotFound("theHarvester"))
    }.flowOn(Dispatchers.IO)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/repository/TermuxToolRunner.kt
git commit -m "feat: add theHarvester runner — domain OSINT emits Found per email and host"
```

---

### Task 6: Implement nmap runner

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/repository/TermuxToolRunner.kt`

- [ ] **Step 1: Add `runNmap` method inside `TermuxToolRunner` class**

Add after `runTheHarvester`:

```kotlin
    fun runNmap(target: String): Flow<SearchProgressEvent> = flow {
        if (!isTermuxInstalled()) return@flow
        if (!isToolInstalled("nmap")) {
            emit(SearchProgressEvent.NotFound("nmap", "Install nmap in Termux: pkg install nmap"))
            return@flow
        }
        emit(SearchProgressEvent.Checking("nmap"))
        val outFile = File(outputDir, "nmap_${System.currentTimeMillis()}.txt")
        val cmd = "nmap -sV --open -oN ${outFile.absolutePath} ${target.trim()} 2>/dev/null"
        try {
            fireCommand(cmd)
        } catch (_: Exception) {
            emit(SearchProgressEvent.Blocked("nmap", "Could not reach Termux RPC"))
            return@flow
        }
        val content = pollFile(outFile, maxMs = 120_000L)
        outFile.delete()
        if (content == null) {
            emit(SearchProgressEvent.Blocked("nmap", "tool timed out"))
            return@flow
        }
        val portLines = content.lines()
            .filter { line -> line.matches(Regex("""^\d+/(tcp|udp)\s+open.*""")) }
        if (portLines.isEmpty()) {
            emit(SearchProgressEvent.NotFound("nmap"))
            return@flow
        }
        portLines.forEach { line ->
            val parts = line.split(Regex("\\s+"))
            val portProto = parts.getOrNull(0) ?: return@forEach
            val service = parts.getOrNull(2) ?: ""
            val version = parts.drop(3).joinToString(" ").take(80)
            val detail = if (version.isNotBlank()) "$portProto $service $version" else "$portProto $service"
            emit(SearchProgressEvent.Found("nmap/port", detail.trim()))
        }
    }.flowOn(Dispatchers.IO)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/repository/TermuxToolRunner.kt
git commit -m "feat: add nmap runner — open port scan emits Found per port/service"
```

---

### Task 7: Integrate TermuxToolRunner into OsintRepository

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/repository/OsintRepository.kt`

The `searchWithProgress()` method at line 291 uses a `coroutineScope { }` block inside `withContext(Dispatchers.IO)` with `Semaphore(5)`. Tool runners launch concurrently inside that same scope.

- [ ] **Step 1: Add TermuxToolRunner field to OsintRepository**

In `OsintRepository.kt`, after the existing `torHttpClient` lazy property (around line 65), add:

```kotlin
    private val termuxRunner by lazy { TermuxToolRunner(appCtx) }
```

- [ ] **Step 2: Add username search integration**

In `searchWithProgress()`, locate the `"username"` branch inside the `when (type)` block. It currently launches scraper coroutines. At the end of that branch, inside the same `coroutineScope { }`, add:

```kotlin
                launch {
                    semaphore.withPermit {
                        termuxRunner.runSherlock(primaryQuery).collect { send(it) }
                    }
                }
                launch {
                    semaphore.withPermit {
                        termuxRunner.runMaigret(primaryQuery).collect { send(it) }
                    }
                }
```

- [ ] **Step 3: Add email search integration**

In the `"email"` branch, add after existing scraper launches:

```kotlin
                launch {
                    semaphore.withPermit {
                        termuxRunner.runHolehe(primaryQuery).collect { send(it) }
                    }
                }
```

- [ ] **Step 4: Add domain/IP search integration**

In the `"domain"` branch (or `"ip"` branch if separate), add after existing scraper launches:

```kotlin
                launch {
                    semaphore.withPermit {
                        termuxRunner.runTheHarvester(primaryQuery).collect { send(it) }
                    }
                }
                launch {
                    semaphore.withPermit {
                        termuxRunner.runNmap(primaryQuery).collect { send(it) }
                    }
                }
```

- [ ] **Step 5: Verify the file compiles (check imports are present)**

`OsintRepository.kt` already imports `kotlinx.coroutines.sync.Semaphore` and `kotlinx.coroutines.sync.withPermit`. No new imports needed for the additions in steps 2-4 since `TermuxToolRunner` is in the same package.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/repository/OsintRepository.kt
git commit -m "feat: integrate TermuxToolRunner into OsintRepository — sherlock/maigret for username, holehe for email, theHarvester/nmap for domain"
```

---

### Task 8: Ensure output directory is accessible

The output directory `/storage/emulated/0/.6degrees/` requires `WRITE_EXTERNAL_STORAGE` on SDK < 29 and uses scoped storage on SDK 29+. Since minSdk=24 we need the permission for older devices, but the directory write will succeed on SDK 29+ without it.

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add WRITE_EXTERNAL_STORAGE with maxSdkVersion guard**

In `AndroidManifest.xml`, after the `READ_EXTERNAL_STORAGE` permission line, add:

```xml
<uses-permission
    android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: add WRITE_EXTERNAL_STORAGE (API ≤28) for Termux tool output directory"
```
