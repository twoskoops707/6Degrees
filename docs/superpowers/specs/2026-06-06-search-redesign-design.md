# 6Degrees Search Screen Redesign — Design Spec
**Date:** 2026-06-06  
**Status:** Approved

---

## Overview

Replace the current tab-based search input screen with a CIA dossier aesthetic. Keep the 8-tab structure (it's the right UX for this type of intelligence gathering), but overhaul the visual language, field groupings, tab labels, and add a live query counter that shows how many searches will fire based on what the user has entered.

---

## Aesthetic Direction

**Theme:** Classified intelligence dossier. Serious, professional, not gamey.  
**Not:** Neon cyberpunk, video game HUD, or cartoon spy app.  
**Reference colors (Field Intelligence theme already in codebase):**
- `fi_ink` (#0E0C0A) — background
- `fi_orange` (#E8530A) — active/accent
- `fi_parchment` (#E8DCC8) — primary text
- Amber (#E8A020) — query counter / armed status
- Green (#4CAF76) — hit confirmed / tab has data dot
- Muted tan (#8A8070) — labels, secondary text

**Typography:** Monospace throughout (`Courier New` / system mono). Uppercase labels with wide letter-spacing (2–4px).

**Classification bar:** Appears at very top of search screen (above tabs):
```
CLASSIFIED // SIGINT COLLECTION TERMINAL
```
Small, 8sp, orange border, subtle orange background wash. Sets tone immediately.

---

## Tab Structure

Keep all 8 tabs. Rename to match the dossier language:

| Index | Old Name | New Name | Icon |
|-------|----------|----------|------|
| 0 | Person | SUBJECT | 👤 |
| 1 | Company | ENTITY | 🏢 |
| 2 | Domain | DOMAIN | 🌐 |
| 3 | Email | EMAIL | ✉ |
| 4 | Phone | PHONE | 📞 |
| 5 | Username | HANDLE | @ |
| 6 | Vehicle/VIN | VIN | 🚗 |
| 7 | WiFi/Network | SIGNAL | 📡 |

Tab styling:
- Inactive: `fi_ink` bg, dark border, muted tan text
- Active: subtle ink bg, orange border + bottom accent bar, orange text
- Has data: green border, green text, small green dot indicator (4dp circle) in corner
- Font size: 7–8sp, letter-spacing 1px

---

## SUBJECT Tab Field Groups

Replace the flat field list with semantic groups separated by section headers styled as:
```
// IDENTITY
```
(7sp, muted, dark border-bottom separator)

### Group 1: `// IDENTITY`
- FIRST NAME
- LAST NAME
- AGE / DOB (optional)

### Group 2: `// LAST KNOWN LOCATION`
- CITY
- STATE

### Group 3: `// KNOWN IDENTIFIERS` (optional, expands query count)
- EMAIL ADDRESS
- PHONE NUMBER
- USERNAME / HANDLE

When any identifier field has content, the query counter increments to reflect cross-reference queries being armed.

---

## Live Query Counter

Shown in the case header row (right side, opposite "CASE FILE"):
```
▶ 21 QUERIES ARMED
```
- Amber color (#E8A020), amber border, subtle amber background wash
- Updates in real-time as user types into fields via `TextWatcher`
- Computation: `buildPersonQueries()` already exists in `OsintRepository.kt` — expose a pure function or duplicate the count logic in the fragment

**Count breakdown (SUBJECT tab):**
- Base (name + location): 18 queries
- +Phone cross-ref: +1 if phone field filled
- +Email cross-ref: +1 if email field filled
- +Username cross-ref: +1 if username field filled (add this query to `buildPersonQueries`)
- Other tabs have fixed counts shown as static text or computed similarly

---

## Cross-Reference Preview Panel (optional, collapsible)

Below the query counter, a collapsible panel labeled:
```
// CROSS-REFERENCE QUERIES ARMED
```
Shows 2–3 example queries that will fire, formatted as:
```
"John Smith" Walnut Creek CA site:fastpeoplesearch.com
"John Smith" Walnut Creek CA phone criminal
```
Collapsed by default. User can tap to expand. Uses the same amber styling.

---

## Initiate Search Button

Replace generic button text with:
```
INITIATE INVESTIGATION ▶
```
Full-width, orange background, monospace, wide letter-spacing. Existing behavior unchanged.

---

## Candidate Selection (Photo Cards)

When multiple subjects match (e.g., 10 John Smiths), the current list-only candidate selection must show photo cards.

**Layout:** Horizontal scroll strip of cards, OR a 2-column grid if > 4 candidates.

**Each card:**
- Photo thumbnail (from `CandidateProfile.photoUrl` — already in model)
- Name in monospace
- Age if available
- Confidence % badge (e.g., `87% MATCH`) in amber/green depending on score
- Location snippet if available

**Selection behavior:** Tap a card to select. Selected card gets orange border. "CONFIRM TARGET" button activates.

If no photo available: show a silhouette placeholder with the person's initials, styled as a redacted dossier photo.

---

## Completion Guarantee

**Current bug:** The "View Full Report" / dossier button appears before all searches complete (either via timeout or partial completion).

**Fix:** The `Complete` event in `SearchProgressEvent` must only emit after the `coroutineScope { }` block in `searchWithProgress` has fully returned — meaning ALL child coroutines have joined. No timeout-based shortcuts or early exits should trigger `Complete`.

Specifically:
- The `coroutineScope { }` in the person branch already suspends until all launched coroutines finish — verify nothing calls `channel.send(SearchProgressEvent.Complete(...))` inside or before that scope closes prematurely
- The `fabViewReport` in `SearchProgressFragment.kt` must remain `GONE` until `SearchProgressEvent.Complete` or `SearchProgressEvent.CandidatesReady` is received
- Remove any existing timer-based fallback that shows the button after N seconds

---

## Files to Change

| File | Change |
|------|--------|
| `res/layout/fragment_search.xml` | Full redesign — classification bar, new tab styling, field groups |
| `res/layout/item_tab_search.xml` | New tab item layout with dot indicator |
| `ui/search/SearchFragment.kt` | Tab labels, TextWatcher for query counter, field group logic |
| `ui/search/SearchProgressFragment.kt` | Remove any timer-based early completion |
| `ui/candidates/CandidateSelectionFragment.kt` | Photo card grid/strip layout |
| `res/layout/fragment_candidate_selection.xml` | Photo grid layout |
| `res/layout/item_candidate_card.xml` | New photo card item |
| `data/repository/OsintRepository.kt` | Add username cross-ref query to `buildPersonQueries` |

---

## Themes

The app ships with 6 selectable themes. All share the same layout; only colors and accent values change. Theme is stored in `SharedPreferences` and applied via a `ThemeManager` that swaps color resource aliases at startup.

---

### A. Field Intelligence *(default)*
Ink background, orange accent, parchment text. CIA dossier — the base design this spec is built on.

| Role | Hex |
|------|-----|
| Background | `#0E0C0A` |
| Surface | `#1A1712` |
| Accent | `#E8530A` |
| Text primary | `#E8DCC8` |
| Text secondary | `#8A8070` |
| Hit/success | `#4CAF76` |
| Counter/armed | `#E8A020` |

---

### B. Night Ops
Pure black, green phosphor terminal. Classic operator/hacker. CRT glow on active elements.

| Role | Hex |
|------|-----|
| Background | `#000000` |
| Surface | `#0A0F0A` |
| Accent | `#00FF41` |
| Text primary | `#00FF41` |
| Text secondary | `#005C17` |
| Hit/success | `#00FF41` |
| Counter/armed | `#00CC33` |

---

### C. Redacted
White/light gray background, black ink text. Government document. Clinical, high contrast — looks like a printed dossier with black bars.

| Role | Hex |
|------|-----|
| Background | `#F0EDE8` |
| Surface | `#FFFFFF` |
| Accent | `#1A1A1A` |
| Text primary | `#0D0D0D` |
| Text secondary | `#6B6B6B` |
| Hit/success | `#1B5E20` |
| Counter/armed | `#BF360C` |

---

### D. Cold War
Aged paper sepia tones, typewriter brown text, rust red accent. Worn field document — like something pulled from a dead drop.

| Role | Hex |
|------|-----|
| Background | `#1C1508` |
| Surface | `#2A1F0E` |
| Accent | `#C0392B` |
| Text primary | `#D4B896` |
| Text secondary | `#7A6040` |
| Hit/success | `#8B9E3A` |
| Counter/armed | `#D4802A` |

---

### E. HUMINT
Dark navy background, steel silver text, ice blue accent. Modern intelligence agency — NSA/DIA aesthetic.

| Role | Hex |
|------|-----|
| Background | `#0A0E1A` |
| Surface | `#111828` |
| Accent | `#4FC3F7` |
| Text primary | `#CFD8DC` |
| Text secondary | `#546E7A` |
| Hit/success | `#26C6DA` |
| Counter/armed | `#80DEEA` |

---

### F. The Plug
Near-black with green tint background, money green accents, dirty gold secondary. Street-level, transactional, minimal. Burner phone energy — no fluff, no gradients. Inspired by *The Wire* palette, Griselda Records artwork, trap house low-light.

| Role | Hex |
|------|-----|
| Background | `#0A0F0A` |
| Surface | `#121A12` |
| Accent | `#39FF14` |
| Text primary | `#E8E8DC` |
| Text secondary | `#7A8A7A` |
| Hit/success | `#2E7D32` |
| Counter/armed | `#C8A84B` |

Active elements get a subtle LED glow: `0 0 6px #39FF14`. Sharp corners only — no rounded cards. Monospace condensed type.

---

### Implementation

- `res/values/colors.xml` — define all 6 × 7 color tokens with `theme_` prefix (e.g., `theme_bg`, `theme_surface`, `theme_accent`)
- `res/values/themes.xml` — one `Style` per theme overriding the 7 color attrs
- `ThemeManager.kt` — reads `SharedPreferences("theme")`, calls `setTheme()` before `setContentView` in `MainActivity`
- Settings screen gets a theme picker row showing color swatches

---

## Out of Scope

- Dark web integration changes
- New search types beyond the existing 8
- Backend API changes
- Anything not visible on the search input or candidate selection screens
