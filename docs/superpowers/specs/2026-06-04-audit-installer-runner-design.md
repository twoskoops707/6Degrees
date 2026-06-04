# 6Degrees: Audit, APK Installer & Termux Runner — Design Spec

**Date:** 2026-06-04
**Status:** Approved

---

## Overview

Three parallel workstreams to stabilize and extend the 6Degrees Android OSINT platform:

1. **App Audit & Fixes** — resolve known-broken code and verify all 7 search type flows end-to-end
2. **APK Installer** — new Tool Installer screen to download/install Termux and OSINT APKs from within the app
3. **Termux Command Runner** — extend existing Termux RPC to execute sherlock/maigret/holehe/theHarvester/nmap and merge output into search results

---

## Workstream 1: App Audit & Fixes

### Approach
Risk-ranked fixes first (known-broken), then user-flow tracing for each search type.

### Phase A — Risk-Ranked Fixes

**1. RetrofitClient.kt (Priority: Critical)**
- Current state: stub / incomplete — all paid API calls silently fail
- Fix: wire OkHttp standard client and Tor SOCKS proxy client (port 9050) to RetrofitClient; ensure all 8 service interfaces (HIBP, Pipl, Clearbit, PDL, Hunter.io, BuiltWith, Numverify, FreeApiServices) bind correctly
- Acceptance: each service can instantiate without crash; a test API call returns a structured response or a typed error

**2. WebScraper.kt (Priority: High)**
- Current state: CSS selectors for FastPeopleSearch, ThatsThem, ProxyNova, HackerTarget, Ahmia may be stale
- Fix: audit each scraper's selectors against live HTML; replace brittle selectors with more structural patterns; ensure all scrapers emit `SearchProgressEvent.Blocked` (not an exception) when content is missing or site structure has changed
- Acceptance: no scraper throws an uncaught exception; `Blocked` events surface in the progress UI

**3. OsintRepository — Cloudflare Handling (Priority: High)**
- Current state: Cloudflare challenge detection exists; handling behavior unclear
- Fix: verify detection branch stops the scrape and emits `Blocked` with a "Cloudflare protected" message; does not hang indefinitely or crash
- Acceptance: a blocked scraper resolves within the existing 30s read timeout

**4. Dead Code Removal (Priority: Low)**
- `SlideshowFragment` + `SlideshowViewModel` — verify no navigation graph references; remove if confirmed unused
- `ReflowFragment` + `ReflowViewModel` — same
- Acceptance: project compiles cleanly after removal; no navigation destination errors

### Phase B — User-Flow Tracing

For each of the 7 search types, trace the full path: **Search form → Progress screen → Results tabs → History save**

| Search Type | Form fields to verify | Expected result tabs |
|-------------|----------------------|----------------------|
| Person | First, Last, City, State, Age | Identity, Contact, Employment, Social, Breaches, Criminal, Threat |
| Email | Email address | Breaches, Social, Identity |
| Phone | Phone number | Identity, Contact |
| Username | Username | Social profiles |
| Domain/IP | Domain or IP | Domain Intel, Threat |
| Company | Company name | Identity, Employment |
| Vehicle | VIN or plate | Vehicle records |

For each flow, verify:
- Form validation fires correctly on empty/invalid input
- Progress events (`Checking`, `Found`, `NotFound`, `Blocked`, `BrowserToolsReady`, `Complete`) all fire and render in `SearchProgressFragment`
- Results fragment renders without crash when data is present and when data is absent (empty state)
- Report saves to Room database and appears in History

### Error Handling
- All network errors must surface as user-readable messages, not raw stack traces
- Missing API keys must show a prompt linking to `ApiSettingsFragment`, not a silent failure

---

## Workstream 2: APK Installer (Hybrid)

### New Screen
`ToolInstallerFragment` — accessible via Settings → "Tool Installer"

### New Files
- `app/.../ui/settings/ToolInstallerFragment.kt`
- `app/.../ui/settings/ToolInstallerViewModel.kt`
- `app/.../data/ApkDownloader.kt`
- `app/src/main/res/layout/fragment_tool_installer.xml`

### Architecture

**ApkDownloader.kt**
- Single-purpose class: download a URL to `context.cacheDir/apks/<filename>`, expose a `Flow<DownloadState>` (Downloading(percent), Done(file), Error(message))
- Uses the existing OkHttp client from `RetrofitClient`
- Saves file via `FileProvider` authority already declared in manifest

**ToolInstallerViewModel.kt**
- Holds download state per APK as `StateFlow<DownloadState>`
- `installApk(context, file)` — fires `Intent(Intent.ACTION_VIEW)` with `PackageInstaller` MIME type and FileProvider URI

**ToolInstallerFragment.kt**

Section 1 — Foundation APKs (direct download + install):

| APK | Source | Notes |
|-----|--------|-------|
| Termux | F-Droid CDN | `com.termux` package |
| Termux:API | F-Droid CDN | `com.termux.api` package |

Each row: app name + description + version label + Download/Install button + ProgressBar (gone until download starts)

Button states: `DOWNLOAD` → `[progress bar]` → `INSTALL` → `INSTALLED` (detect via PackageManager)

Section 2 — OSINT Tool APKs (browser links):

| Tool | Link target |
|------|------------|
| NetHunter Rootless | GitHub releases page |
| Kali NetHunter App Store | GitHub releases page |

Each row: name + description + "VIEW ON GITHUB" outlined button — opens browser, no download logic

### Manifest Changes
- Add `REQUEST_INSTALL_PACKAGES` permission
- FileProvider already declared — no change needed

### UI Theme
Follows existing Field Intelligence theme — `@color/fi_charcoal` card background, `0dp` corner radius, `@color/fi_orange` progress tint, monospace labels

---

## Workstream 3: Termux Command Runner

### New File
`app/.../data/repository/TermuxToolRunner.kt`

### Architecture

```
OsintRepository.searchWithProgress()
    └── TermuxToolRunner.runTool(type, query)
            ├── buildCommand(tool, query)   // assemble bash command string
            ├── fireIntent()                // com.termux.app.RunCommandService
            ├── pollOutput()               // exponential backoff, max 120s
            └── parseOutput()             // tool-specific parser → domain models
                    └── emit SearchProgressEvent.Found(result)
```

### Supported Tools

| Tool | Trigger | Command pattern | Output format | Parsed into |
|------|---------|----------------|--------------|-------------|
| `sherlock` | username search | `sherlock $query --output $outfile --print-found` | Text list of URLs | Social profiles tab |
| `maigret` | username search | `maigret $query -o $outfile --no-pics` | JSON | Social profiles + metadata |
| `holehe` | email search | `holehe $query > $outfile` | Text list | Email-registered services in Breaches tab |
| `theHarvester` | domain search | `theHarvester -d $query -b all -f $outfile` | XML/JSON | Emails + subdomains in Domain tab |
| `nmap` | IP/domain search | `nmap -sV --open -oJ $outfile $query` | JSON | Open ports in Domain tab |

### Output File Convention
All tools write to `/storage/emulated/0/.6degrees/<tool>_<timestamp>.txt`
Directory is created if absent; files are deleted after parsing.

### Polling Strategy
- Initial delay: 3s
- Backoff: 3s, 6s, 12s, 24s, 48s
- Max wait: 120s
- Timeout emits `SearchProgressEvent.Blocked("tool timed out")` — no crash

### Graceful Degradation
- If `com.termux` not installed → skip all tool runners, no error surfaced (tools are additive)
- If tool binary absent (detected via `/data/data/com.termux/files/usr/bin/<tool>`) → emit `SearchProgressEvent.NotFound` with message "Install <tool> in Termux"
- If output file empty or unparseable → emit `Blocked` event, continue with other sources

### Integration Point
`OsintRepository.searchWithProgress()` — Termux tool coroutines launch concurrently alongside existing scrapers inside the existing `Semaphore(5)` block. No changes to event flow or results architecture.

### Output Parsing

**sherlock/maigret → social profiles:**
- Parse each URL line or JSON entry into `SocialProfile(platform, url, username)`
- Deduplicate against profiles already found by scrapers

**holehe → breach/service list:**
- Parse "found" lines into service names; append to breaches/services section

**theHarvester → domain intel:**
- Extract `[*] Emails found:` section → email list
- Extract `[*] Hosts found:` section → subdomain list

**nmap → port list:**
- Parse JSON `ports` array → `OpenPort(number, protocol, service, version)`

---

## Shared Constraints

- All new UI follows the Field Intelligence theme (`fi_ink`, `fi_charcoal`, `fi_orange`, `fi_parchment`, `fi_ash`, `fi_smoke`, `fi_border`, monospace fonts, `0dp` corner radius)
- No new third-party dependencies for any workstream — use existing OkHttp, Room, Coroutines, Navigation
- All new ViewModels follow existing MVVM pattern (`StateFlow` / `Flow`, no `LiveData`)
- Minimum SDK remains 24

---

## Files Changed Summary

| Workstream | New files | Modified files |
|-----------|-----------|----------------|
| Audit | 0 | RetrofitClient.kt, WebScraper.kt, OsintRepository.kt, nav_graph.xml (dead code removal) |
| APK Installer | ToolInstallerFragment.kt, ToolInstallerViewModel.kt, ApkDownloader.kt, fragment_tool_installer.xml | AndroidManifest.xml, nav_graph.xml, fragment_settings.xml |
| Termux Runner | TermuxToolRunner.kt | OsintRepository.kt, SearchProgressEvent.kt (if new event types needed) |
