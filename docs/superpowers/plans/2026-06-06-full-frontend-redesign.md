# 6Degrees Full Frontend Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current patchwork UI with a cohesive CIA dossier aesthetic across all screens, ship 6 selectable themes including "The Plug", add live query counter to search, photo candidate cards, and fix the premature report completion bug.

**Architecture:** The existing theme system (pref_theme_base + pref_accent → MainActivity.setTheme()) is extended with 6 full themes. All layout files are rewritten in-place. No new fragments; behavior logic stays in existing classes.

**Tech Stack:** Android Views, XML layouts, MaterialComponents, Coil (image loading), SharedPreferences, Kotlin TextWatcher

---

## File Map

| File | Change |
|------|--------|
| `res/values/colors.xml` | Add color tokens for 5 new themes |
| `res/values/themes.xml` | Add 5 new Theme styles + update Settings card styles |
| `MainActivity.kt` | Wire new theme keys into when() block |
| `res/layout/fragment_search.xml` | Full redesign: classification bar, renamed tabs, field groups, query counter |
| `ui/search/SearchFragment.kt` | TextWatcher → live query count, tab labels |
| `data/repository/OsintRepository.kt` | Add username cross-ref query to buildPersonQueries |
| `res/layout/item_candidate_card.xml` | Dossier-style card: sharp corners, monospace, confidence badge |
| `res/layout/fragment_candidate_selection.xml` | Dossier header, "CONFIRM TARGET" button style |
| `ui/candidates/CandidateSelectionFragment.kt` | Wire dossier button labels |
| `res/layout/fragment_settings.xml` | Replace 3 theme cards with 6 dossier-styled swatches |
| `ui/settings/SettingsFragment.kt` | Handle 6 theme base values, update card selection logic |
| `ui/search/SearchProgressViewModel.kt` | Fix: don't emit Complete("",0) on exception |

---

## Task 1: Add color tokens for new themes

**Files:**
- Modify: `app/src/main/res/values/colors.xml`

- [ ] **Step 1: Add tokens to colors.xml**

Open `app/src/main/res/values/colors.xml` and add after the `<!-- ── Field Intelligence Theme ──────────────────── -->` block:

```xml
    <!-- ── Night Ops Theme ──────────────────────────── -->
    <color name="nops_bg">#000000</color>
    <color name="nops_surface">#0A0F0A</color>
    <color name="nops_border">#0D1A0D</color>
    <color name="nops_green">#00FF41</color>
    <color name="nops_green_dim">#001A0D</color>
    <color name="nops_amber">#00CC33</color>
    <color name="nops_text">#00FF41</color>
    <color name="nops_ash">#005C17</color>

    <!-- ── Redacted Theme ───────────────────────────── -->
    <color name="red_bg">#F0EDE8</color>
    <color name="red_surface">#FFFFFF</color>
    <color name="red_border">#C8C0B8</color>
    <color name="red_ink">#0D0D0D</color>
    <color name="red_ink_dim">#1A1A1A</color>
    <color name="red_accent">#BF360C</color>
    <color name="red_accent_dim">#FBE9E7</color>
    <color name="red_ash">#6B6B6B</color>
    <color name="red_success">#1B5E20</color>

    <!-- ── Cold War Theme ───────────────────────────── -->
    <color name="cw_bg">#1C1508</color>
    <color name="cw_surface">#2A1F0E</color>
    <color name="cw_border">#3D2E18</color>
    <color name="cw_rust">#C0392B</color>
    <color name="cw_rust_dim">#3B0D09</color>
    <color name="cw_amber">#D4802A</color>
    <color name="cw_parchment">#D4B896</color>
    <color name="cw_ash">#7A6040</color>
    <color name="cw_moss">#8B9E3A</color>

    <!-- ── HUMINT Theme ──────────────────────────────── -->
    <color name="hi_bg">#0A0E1A</color>
    <color name="hi_surface">#111828</color>
    <color name="hi_border">#1E2B44</color>
    <color name="hi_blue">#4FC3F7</color>
    <color name="hi_blue_dim">#041B2C</color>
    <color name="hi_cyan">#80DEEA</color>
    <color name="hi_text">#CFD8DC</color>
    <color name="hi_ash">#546E7A</color>

    <!-- ── The Plug Theme ───────────────────────────── -->
    <color name="plug_bg">#0A0F0A</color>
    <color name="plug_surface">#121A12</color>
    <color name="plug_border">#1E2B1E</color>
    <color name="plug_green">#39FF14</color>
    <color name="plug_green_dim">#061A03</color>
    <color name="plug_gold">#C8A84B</color>
    <color name="plug_gold_dim">#261E0A</color>
    <color name="plug_text">#E8E8DC</color>
    <color name="plug_ash">#7A8A7A</color>
    <color name="plug_money">#2E7D32</color>
```

- [ ] **Step 2: Commit**

```bash
cd /storage/6FFC-736C/termux-tools/6Degrees
git add app/src/main/res/values/colors.xml
git commit -m "feat: add color tokens for NightOps/Redacted/ColdWar/HUMINT/ThePlug themes"
```

---

## Task 2: Add Theme styles

**Files:**
- Modify: `app/src/main/res/values/themes.xml`

- [ ] **Step 1: Add 5 new Theme styles in themes.xml**

After the `Theme.SixDegrees.FieldIntel` style block (around line 177), add:

```xml
    <!-- ════════════════════════════════════════════════════════════ -->
    <!-- NIGHT OPS — pure black, phosphor green, 0dp corners        -->
    <!-- ════════════════════════════════════════════════════════════ -->
    <style name="Theme.SixDegrees.NightOps" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="themeFamily">nightops</item>
        <item name="colorPrimary">@color/nops_green</item>
        <item name="colorPrimaryVariant">@color/nops_green_dim</item>
        <item name="colorOnPrimary">@color/black</item>
        <item name="colorSecondary">@color/nops_amber</item>
        <item name="colorSecondaryVariant">@color/nops_green_dim</item>
        <item name="colorOnSecondary">@color/black</item>
        <item name="android:colorBackground">@color/nops_bg</item>
        <item name="colorSurface">@color/nops_surface</item>
        <item name="colorOnSurface">@color/nops_text</item>
        <item name="colorError">@color/error</item>
        <item name="colorOnError">@color/black</item>
        <item name="android:statusBarColor">@color/nops_bg</item>
        <item name="android:navigationBarColor">@color/nops_bg</item>
        <item name="android:windowLightStatusBar" tools:targetApi="23">false</item>
        <item name="android:windowDrawsSystemBarBackgrounds">true</item>
        <item name="android:fontFamily">monospace</item>
        <item name="shapeAppearanceSmallComponent">@style/ShapeAppearance.Hacker</item>
        <item name="shapeAppearanceMediumComponent">@style/ShapeAppearance.Hacker</item>
        <item name="shapeAppearanceLargeComponent">@style/ShapeAppearance.Hacker</item>
        <item name="materialCardViewStyle">@style/Widget.SixDegrees.CardView.NightOps</item>
        <item name="bottomNavigationStyle">@style/Widget.SixDegrees.BottomNav.NightOps</item>
    </style>

    <!-- ════════════════════════════════════════════════════════════ -->
    <!-- REDACTED — white/light, black ink, government document      -->
    <!-- ════════════════════════════════════════════════════════════ -->
    <style name="Theme.SixDegrees.Redacted" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="themeFamily">redacted</item>
        <item name="colorPrimary">@color/red_ink</item>
        <item name="colorPrimaryVariant">@color/red_ink_dim</item>
        <item name="colorOnPrimary">@color/white</item>
        <item name="colorSecondary">@color/red_accent</item>
        <item name="colorSecondaryVariant">@color/red_accent_dim</item>
        <item name="colorOnSecondary">@color/white</item>
        <item name="android:colorBackground">@color/red_bg</item>
        <item name="colorSurface">@color/red_surface</item>
        <item name="colorOnSurface">@color/red_ink</item>
        <item name="colorError">@color/red_accent</item>
        <item name="colorOnError">@color/white</item>
        <item name="android:statusBarColor">@color/red_bg</item>
        <item name="android:navigationBarColor">@color/red_bg</item>
        <item name="android:windowLightStatusBar" tools:targetApi="23">true</item>
        <item name="android:windowDrawsSystemBarBackgrounds">true</item>
        <item name="shapeAppearanceSmallComponent">@style/ShapeAppearance.FieldIntel.Small</item>
        <item name="shapeAppearanceMediumComponent">@style/ShapeAppearance.FieldIntel.Medium</item>
        <item name="shapeAppearanceLargeComponent">@style/ShapeAppearance.FieldIntel.Medium</item>
        <item name="materialCardViewStyle">@style/Widget.SixDegrees.CardView.Redacted</item>
        <item name="bottomNavigationStyle">@style/Widget.SixDegrees.BottomNav.Redacted</item>
    </style>

    <!-- ════════════════════════════════════════════════════════════ -->
    <!-- COLD WAR — sepia/aged paper, rust red accent, 2dp corners  -->
    <!-- ════════════════════════════════════════════════════════════ -->
    <style name="Theme.SixDegrees.ColdWar" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="themeFamily">coldwar</item>
        <item name="colorPrimary">@color/cw_rust</item>
        <item name="colorPrimaryVariant">@color/cw_rust_dim</item>
        <item name="colorOnPrimary">@color/white</item>
        <item name="colorSecondary">@color/cw_amber</item>
        <item name="colorSecondaryVariant">@color/cw_rust_dim</item>
        <item name="colorOnSecondary">@color/cw_parchment</item>
        <item name="android:colorBackground">@color/cw_bg</item>
        <item name="colorSurface">@color/cw_surface</item>
        <item name="colorOnSurface">@color/cw_parchment</item>
        <item name="colorError">@color/cw_rust</item>
        <item name="colorOnError">@color/white</item>
        <item name="android:statusBarColor">@color/cw_bg</item>
        <item name="android:navigationBarColor">@color/cw_bg</item>
        <item name="android:windowLightStatusBar" tools:targetApi="23">false</item>
        <item name="android:windowDrawsSystemBarBackgrounds">true</item>
        <item name="android:fontFamily">monospace</item>
        <item name="shapeAppearanceSmallComponent">@style/ShapeAppearance.FieldIntel.Small</item>
        <item name="shapeAppearanceMediumComponent">@style/ShapeAppearance.FieldIntel.Medium</item>
        <item name="shapeAppearanceLargeComponent">@style/ShapeAppearance.FieldIntel.Medium</item>
        <item name="materialCardViewStyle">@style/Widget.SixDegrees.CardView.ColdWar</item>
        <item name="bottomNavigationStyle">@style/Widget.SixDegrees.BottomNav.ColdWar</item>
    </style>

    <!-- ════════════════════════════════════════════════════════════ -->
    <!-- HUMINT — dark navy, steel/ice blue, 4dp corners            -->
    <!-- ════════════════════════════════════════════════════════════ -->
    <style name="Theme.SixDegrees.Humint" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="themeFamily">humint</item>
        <item name="colorPrimary">@color/hi_blue</item>
        <item name="colorPrimaryVariant">@color/hi_blue_dim</item>
        <item name="colorOnPrimary">@color/black</item>
        <item name="colorSecondary">@color/hi_cyan</item>
        <item name="colorSecondaryVariant">@color/hi_blue_dim</item>
        <item name="colorOnSecondary">@color/black</item>
        <item name="android:colorBackground">@color/hi_bg</item>
        <item name="colorSurface">@color/hi_surface</item>
        <item name="colorOnSurface">@color/hi_text</item>
        <item name="colorError">@color/error</item>
        <item name="colorOnError">@color/white</item>
        <item name="android:statusBarColor">@color/hi_bg</item>
        <item name="android:navigationBarColor">@color/hi_bg</item>
        <item name="android:windowLightStatusBar" tools:targetApi="23">false</item>
        <item name="android:windowDrawsSystemBarBackgrounds">true</item>
        <item name="shapeAppearanceSmallComponent">@style/ShapeAppearance.Tactical.Small</item>
        <item name="shapeAppearanceMediumComponent">@style/ShapeAppearance.Tactical.Medium</item>
        <item name="shapeAppearanceLargeComponent">@style/ShapeAppearance.Tactical.Medium</item>
        <item name="materialCardViewStyle">@style/Widget.SixDegrees.CardView.Humint</item>
        <item name="bottomNavigationStyle">@style/Widget.SixDegrees.BottomNav.Humint</item>
    </style>

    <!-- ════════════════════════════════════════════════════════════ -->
    <!-- THE PLUG — black/green tint, money green, dirty gold, 0dp  -->
    <!-- ════════════════════════════════════════════════════════════ -->
    <style name="Theme.SixDegrees.ThePlug" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="themeFamily">theplug</item>
        <item name="colorPrimary">@color/plug_green</item>
        <item name="colorPrimaryVariant">@color/plug_green_dim</item>
        <item name="colorOnPrimary">@color/black</item>
        <item name="colorSecondary">@color/plug_gold</item>
        <item name="colorSecondaryVariant">@color/plug_gold_dim</item>
        <item name="colorOnSecondary">@color/black</item>
        <item name="android:colorBackground">@color/plug_bg</item>
        <item name="colorSurface">@color/plug_surface</item>
        <item name="colorOnSurface">@color/plug_text</item>
        <item name="colorError">@color/error</item>
        <item name="colorOnError">@color/black</item>
        <item name="android:statusBarColor">@color/plug_bg</item>
        <item name="android:navigationBarColor">@color/plug_bg</item>
        <item name="android:windowLightStatusBar" tools:targetApi="23">false</item>
        <item name="android:windowDrawsSystemBarBackgrounds">true</item>
        <item name="android:fontFamily">monospace</item>
        <item name="shapeAppearanceSmallComponent">@style/ShapeAppearance.Hacker</item>
        <item name="shapeAppearanceMediumComponent">@style/ShapeAppearance.Hacker</item>
        <item name="shapeAppearanceLargeComponent">@style/ShapeAppearance.Hacker</item>
        <item name="materialCardViewStyle">@style/Widget.SixDegrees.CardView.ThePlug</item>
        <item name="bottomNavigationStyle">@style/Widget.SixDegrees.BottomNav.ThePlug</item>
    </style>
```

- [ ] **Step 2: Add CardView and BottomNav styles for each new theme**

After the `Widget.SixDegrees.CardView.FieldIntel` style, add:

```xml
    <style name="Widget.SixDegrees.CardView.NightOps" parent="Widget.MaterialComponents.CardView">
        <item name="cardBackgroundColor">@color/nops_surface</item>
        <item name="cardCornerRadius">0dp</item>
        <item name="cardElevation">0dp</item>
        <item name="strokeColor">@color/nops_green</item>
        <item name="strokeWidth">1dp</item>
    </style>

    <style name="Widget.SixDegrees.CardView.Redacted" parent="Widget.MaterialComponents.CardView">
        <item name="cardBackgroundColor">@color/red_surface</item>
        <item name="cardCornerRadius">2dp</item>
        <item name="cardElevation">0dp</item>
        <item name="strokeColor">@color/red_border</item>
        <item name="strokeWidth">1dp</item>
    </style>

    <style name="Widget.SixDegrees.CardView.ColdWar" parent="Widget.MaterialComponents.CardView">
        <item name="cardBackgroundColor">@color/cw_surface</item>
        <item name="cardCornerRadius">2dp</item>
        <item name="cardElevation">0dp</item>
        <item name="strokeColor">@color/cw_border</item>
        <item name="strokeWidth">1dp</item>
    </style>

    <style name="Widget.SixDegrees.CardView.Humint" parent="Widget.MaterialComponents.CardView">
        <item name="cardBackgroundColor">@color/hi_surface</item>
        <item name="cardCornerRadius">4dp</item>
        <item name="cardElevation">0dp</item>
        <item name="strokeColor">@color/hi_border</item>
        <item name="strokeWidth">1dp</item>
    </style>

    <style name="Widget.SixDegrees.CardView.ThePlug" parent="Widget.MaterialComponents.CardView">
        <item name="cardBackgroundColor">@color/plug_surface</item>
        <item name="cardCornerRadius">0dp</item>
        <item name="cardElevation">0dp</item>
        <item name="strokeColor">@color/plug_border</item>
        <item name="strokeWidth">1dp</item>
    </style>

    <style name="Widget.SixDegrees.BottomNav.NightOps" parent="Widget.SixDegrees.BottomNav">
        <item name="android:background">@color/nops_bg</item>
    </style>
    <style name="Widget.SixDegrees.BottomNav.Redacted" parent="Widget.SixDegrees.BottomNav">
        <item name="android:background">@color/red_bg</item>
    </style>
    <style name="Widget.SixDegrees.BottomNav.ColdWar" parent="Widget.SixDegrees.BottomNav">
        <item name="android:background">@color/cw_bg</item>
    </style>
    <style name="Widget.SixDegrees.BottomNav.Humint" parent="Widget.SixDegrees.BottomNav">
        <item name="android:background">@color/hi_bg</item>
    </style>
    <style name="Widget.SixDegrees.BottomNav.ThePlug" parent="Widget.SixDegrees.BottomNav">
        <item name="android:background">@color/plug_bg</item>
    </style>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/themes.xml
git commit -m "feat: add NightOps/Redacted/ColdWar/HUMINT/ThePlug theme styles"
```

---

## Task 3: Wire new themes in MainActivity

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/MainActivity.kt:44-58`

- [ ] **Step 1: Expand the when() block**

Replace the existing `when ("${base}_${accent}")` block with:

```kotlin
val themeRes = when (base) {
    "nightops"   -> R.style.Theme_SixDegrees_NightOps
    "redacted"   -> R.style.Theme_SixDegrees_Redacted
    "coldwar"    -> R.style.Theme_SixDegrees_ColdWar
    "humint"     -> R.style.Theme_SixDegrees_Humint
    "theplug"    -> R.style.Theme_SixDegrees_ThePlug
    "fieldintel" -> R.style.Theme_SixDegrees_FieldIntel
    "hacker"     -> when (accent) {
        "amber"  -> R.style.Theme_SixDegrees_Hacker_Amber
        "blue"   -> R.style.Theme_SixDegrees_Hacker_Blue
        "cyan"   -> R.style.Theme_SixDegrees_Hacker_Cyan
        "purple" -> R.style.Theme_SixDegrees_Hacker_Purple
        else     -> R.style.Theme_SixDegrees_Hacker_Green
    }
    "tactical"   -> when (accent) {
        "cyan"   -> R.style.Theme_SixDegrees_Tactical_Cyan
        "green"  -> R.style.Theme_SixDegrees_Tactical_Green
        "purple" -> R.style.Theme_SixDegrees_Tactical_Purple
        else     -> R.style.Theme_SixDegrees_Tactical_Blue
    }
    else         -> when ("${base}_${accent}") {
        "modern_cyan"   -> R.style.Theme_SixDegrees_Modern_Cyan
        "modern_green"  -> R.style.Theme_SixDegrees_Modern_Green
        "modern_purple" -> R.style.Theme_SixDegrees_Modern_Purple
        else            -> R.style.Theme_SixDegrees_FieldIntel
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/MainActivity.kt
git commit -m "feat: wire new theme bases into MainActivity theme resolver"
```

---

## Task 4: Fix premature completion bug

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/ui/search/SearchProgressViewModel.kt:40-41`

- [ ] **Step 1: Remove the phantom Complete emission on exception**

Replace:
```kotlin
            } catch (_: Exception) {
                _events.emit(SearchProgressEvent.Complete("", 0))
            }
```
With:
```kotlin
            } catch (_: Exception) {
                // swallow — search flow already handles its own errors
            }
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/ui/search/SearchProgressViewModel.kt
git commit -m "fix: stop emitting phantom Complete on exception in SearchProgressViewModel"
```

---

## Task 5: Add username cross-ref to buildPersonQueries

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/data/repository/OsintRepository.kt:257`

- [ ] **Step 1: Add username parameter and cross-ref query**

Change signature and body of `buildPersonQueries`:

```kotlin
private fun buildPersonQueries(name: String, city: String, state: String, phone: String = "", email: String = "", username: String = ""): List<Pair<String, String>> {
    val loc = listOf(city, state).filter { it.isNotBlank() }.joinToString(" ")
    val queries = mutableListOf(
        "General" to "\"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
        "Phone" to "\"$name\" phone number${if (loc.isNotBlank()) " $loc" else ""}",
        "Address" to "\"$name\" address${if (loc.isNotBlank()) " $loc" else ""}",
        "FPS" to "site:fastpeoplesearch.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
        "Whitepages" to "site:whitepages.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
        "Spokeo" to "site:spokeo.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
        "Radaris" to "site:radaris.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
        "BeenVerified" to "site:beenverified.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
        "TruePeopleSearch" to "site:truepeoplesearch.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
        "PeopleFinder" to "site:peoplefinder.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
        "Relatives" to "\"$name\" relatives family${if (loc.isNotBlank()) " $loc" else ""}",
        "LinkedIn" to "site:linkedin.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
        "Facebook" to "site:facebook.com \"$name\"${if (loc.isNotBlank()) " $loc" else ""}",
        "Criminal" to "\"$name\" criminal arrest court record${if (loc.isNotBlank()) " $loc" else ""}",
        "News" to "\"$name\"${if (loc.isNotBlank()) " $loc" else ""} news",
        "Employment" to "\"$name\" employer company job${if (loc.isNotBlank()) " $loc" else ""}",
        "Property" to "\"$name\" property records${if (state.isNotBlank()) " $state" else ""}",
        "Voter" to "\"$name\" voter registration${if (state.isNotBlank()) " $state" else ""}"
    )
    if (phone.isNotBlank()) queries.add("PhoneCrossRef" to "\"$phone\" \"$name\"")
    if (email.isNotBlank()) queries.add("EmailCrossRef" to "\"$email\" \"$name\"")
    if (username.isNotBlank()) queries.add("UsernameCrossRef" to "\"$username\" \"$name\"")
    return queries
}
```

- [ ] **Step 2: Pass username at call site (around line 831)**

Change:
```kotlin
val personQueries = buildPersonQueries(primaryQuery, city, state, personPhone, personEmail)
```
To:
```kotlin
val personUsername = fields["username"] ?: ""
val personQueries = buildPersonQueries(primaryQuery, city, state, personPhone, personEmail, personUsername)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/repository/OsintRepository.kt
git commit -m "feat: add username cross-reference query to buildPersonQueries"
```

---

## Task 6: Redesign fragment_search.xml

**Files:**
- Modify: `app/src/main/res/layout/fragment_search.xml`

Full replacement. The layout keeps all existing view IDs intact (the fragment's Kotlin references them by ID). Changes: add classification bar at top, rename tab labels, add `// IDENTITY` / `// LAST KNOWN LOCATION` / `// KNOWN IDENTIFIERS` section headers inside `form_person`, add query counter row.

- [ ] **Step 1: Add classification bar before the tab row**

Insert immediately after the opening `<LinearLayout` inside the root `NestedScrollView` (before the existing 56dp tab `LinearLayout`):

```xml
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="CLASSIFIED // SIGINT COLLECTION TERMINAL"
            android:textColor="@color/fi_orange"
            android:textSize="8sp"
            android:fontFamily="monospace"
            android:letterSpacing="0.3"
            android:gravity="center"
            android:paddingTop="6dp"
            android:paddingBottom="6dp"
            android:background="@color/fi_orange_dim" />
```

- [ ] **Step 2: Rename tab TextViews**

Change tab label text values:
- `android:text="PER"` → `android:text="SUBJECT"`
- `android:text="ORG"` → `android:text="ENTITY"`
- `android:text="NET"` → `android:text="DOMAIN"`
- `android:text="EML"` → `android:text="EMAIL"`
- `android:text="TEL"` → `android:text="PHONE"`
- `android:text="USR"` → `android:text="HANDLE"`
- `android:text="VEH"` → `android:text="VIN"`
- `android:text="WFI"` → `android:text="SIGNAL"`

Also reduce `android:textSize` to `8sp` on all tab TextViews to fit the longer labels, keep `android:letterSpacing="0.06"`.

- [ ] **Step 3: Replace case header bar with query counter row**

Replace the existing `CASE INTAKE — SUBJECT ACQUISITION` LinearLayout with:

```xml
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:paddingStart="20dp"
            android:paddingEnd="20dp"
            android:paddingTop="10dp"
            android:paddingBottom="10dp"
            android:gravity="center_vertical">

            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="CASE FILE"
                android:textColor="@color/fi_ash"
                android:textSize="9sp"
                android:fontFamily="monospace"
                android:letterSpacing="0.18" />

            <TextView
                android:id="@+id/tv_query_counter"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="▶ 18 QUERIES ARMED"
                android:textColor="@color/fi_amber"
                android:textSize="9sp"
                android:fontFamily="monospace"
                android:letterSpacing="0.1"
                android:paddingStart="8dp"
                android:paddingEnd="8dp"
                android:paddingTop="3dp"
                android:paddingBottom="3dp"
                android:background="@color/fi_amber_dim" />

        </LinearLayout>
```

- [ ] **Step 4: Add `// IDENTITY` section header inside form_person**

Inside `form_person` LinearLayout, before the FORENAME/MI/SURNAME row, add:

```xml
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="// IDENTITY"
                android:textColor="@color/fi_smoke"
                android:textSize="7sp"
                android:fontFamily="monospace"
                android:letterSpacing="0.3"
                android:paddingBottom="6dp"
                android:layout_marginBottom="4dp" />
```

- [ ] **Step 5: Add `// LAST KNOWN LOCATION` header before CITY/STATE row**

```xml
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="// LAST KNOWN LOCATION"
                android:textColor="@color/fi_smoke"
                android:textSize="7sp"
                android:fontFamily="monospace"
                android:letterSpacing="0.3"
                android:paddingBottom="6dp"
                android:layout_marginTop="4dp"
                android:layout_marginBottom="4dp" />
```

- [ ] **Step 6: Add `// KNOWN IDENTIFIERS` header before PHONE field row**

```xml
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="// KNOWN IDENTIFIERS"
                android:textColor="@color/fi_smoke"
                android:textSize="7sp"
                android:fontFamily="monospace"
                android:letterSpacing="0.3"
                android:paddingBottom="6dp"
                android:layout_marginTop="4dp"
                android:layout_marginBottom="4dp" />
```

- [ ] **Step 7: Change search button text**

```xml
android:text="INITIATE INVESTIGATION ▶"
```

- [ ] **Step 8: Commit**

```bash
git add app/src/main/res/layout/fragment_search.xml
git commit -m "feat: redesign search screen — classification bar, dossier tabs, field groups, query counter"
```

---

## Task 7: Wire query counter in SearchFragment.kt

**Files:**
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/ui/search/SearchFragment.kt`

- [ ] **Step 1: Add updateQueryCounter() helper**

Add after the `currentType` field declaration:

```kotlin
private fun updateQueryCounter() {
    val b = _binding ?: return
    if (currentType != "person") {
        b.tvQueryCounter.text = when (currentType) {
            "username" -> "▶ 3 QUERIES ARMED"
            "domain", "ip" -> "▶ 5 QUERIES ARMED"
            "email" -> "▶ 4 QUERIES ARMED"
            "phone" -> "▶ 3 QUERIES ARMED"
            "company" -> "▶ 6 QUERIES ARMED"
            else -> "▶ 2 QUERIES ARMED"
        }
        return
    }
    val baseCount = 18
    val hasPhone = b.inputPhone.text?.isNotBlank() == true
    val hasEmail = b.inputEmail.text?.isNotBlank() == true
    val hasUsername = b.inputUsername.text?.isNotBlank() == true
    val total = baseCount + (if (hasPhone) 1 else 0) + (if (hasEmail) 1 else 0) + (if (hasUsername) 1 else 0)
    b.tvQueryCounter.text = "▶ $total QUERIES ARMED"
}
```

- [ ] **Step 2: Add TextWatcher on identifier fields**

In `onViewCreated`, after `setupEntityTypeSelector()`, add:

```kotlin
val counterWatcher = object : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) { updateQueryCounter() }
}
binding.inputPhone.addTextChangedListener(counterWatcher)
binding.inputEmail.addTextChangedListener(counterWatcher)
binding.inputUsername.addTextChangedListener(counterWatcher)
```

- [ ] **Step 3: Call updateQueryCounter() when tab switches**

In `setupEntityTypeSelector()`, wherever `currentType` is set (each card click), call `updateQueryCounter()` at the end.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/ui/search/SearchFragment.kt
git commit -m "feat: live query counter in SearchFragment updates as identifiers are filled"
```

---

## Task 8: Dossier-style candidate card

**Files:**
- Modify: `app/src/main/res/layout/item_candidate_card.xml`

Full replacement keeping all existing view IDs. Switches from rounded modern card to sharp-cornered dossier card with monospace text, amber confidence badge instead of progress bar.

- [ ] **Step 1: Replace item_candidate_card.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="16dp"
    android:layout_marginBottom="10dp"
    android:clickable="true"
    android:focusable="true"
    app:cardBackgroundColor="@color/fi_charcoal"
    app:cardCornerRadius="2dp"
    app:cardElevation="0dp"
    app:strokeColor="@color/fi_border"
    app:strokeWidth="1dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="14dp">

        <!-- Top row: photo + name/details + confidence badge -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical">

            <!-- Dossier photo: square with slight border -->
            <FrameLayout
                android:layout_width="56dp"
                android:layout_height="72dp"
                android:layout_marginEnd="14dp">

                <com.google.android.material.imageview.ShapeableImageView
                    android:id="@+id/iv_candidate_photo"
                    android:layout_width="56dp"
                    android:layout_height="72dp"
                    android:scaleType="centerCrop"
                    android:src="@drawable/ic_person_placeholder"
                    app:shapeAppearanceOverlay="@style/ShapeAppearance.FieldIntel.Small"
                    app:strokeColor="@color/fi_border"
                    app:strokeWidth="1dp" />

                <FrameLayout
                    android:id="@+id/iv_selected_overlay"
                    android:layout_width="56dp"
                    android:layout_height="72dp"
                    android:background="#CC0E0C0A"
                    android:visibility="gone">
                    <ImageView
                        android:layout_width="24dp"
                        android:layout_height="24dp"
                        android:layout_gravity="center"
                        android:src="@drawable/ic_check"
                        android:tint="@color/fi_orange" />
                </FrameLayout>
            </FrameLayout>

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">

                <TextView
                    android:id="@+id/tv_candidate_name"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:textColor="@color/fi_parchment"
                    android:textSize="15sp"
                    android:fontFamily="monospace"
                    android:textStyle="bold"
                    android:letterSpacing="0.05"
                    android:maxLines="1"
                    android:ellipsize="end" />

                <TextView
                    android:id="@+id/tv_candidate_age_location"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:textColor="@color/fi_ash"
                    android:textSize="11sp"
                    android:fontFamily="monospace"
                    android:layout_marginTop="3dp"
                    android:maxLines="1"
                    android:ellipsize="end" />

                <TextView
                    android:id="@+id/tv_candidate_phone"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:textColor="@color/fi_amber"
                    android:textSize="11sp"
                    android:fontFamily="monospace"
                    android:layout_marginTop="2dp"
                    android:visibility="gone" />
            </LinearLayout>

            <!-- Confidence badge (replaces chip) -->
            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:gravity="center"
                android:layout_marginStart="8dp">

                <TextView
                    android:id="@+id/tv_confidence_pct"
                    android:layout_width="44dp"
                    android:layout_height="wrap_content"
                    android:gravity="center"
                    android:textColor="@color/fi_amber"
                    android:textSize="13sp"
                    android:fontFamily="monospace"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="44dp"
                    android:layout_height="wrap_content"
                    android:gravity="center"
                    android:text="MATCH"
                    android:textColor="@color/fi_smoke"
                    android:textSize="7sp"
                    android:fontFamily="monospace"
                    android:letterSpacing="0.2" />

                <!-- Stub ProgressBar kept for code compat — hidden -->
                <ProgressBar
                    android:id="@+id/progress_confidence"
                    style="?android:attr/progressBarStyleHorizontal"
                    android:layout_width="44dp"
                    android:layout_height="2dp"
                    android:max="100"
                    android:layout_marginTop="3dp"
                    android:progressTint="@color/fi_orange"
                    android:progressBackgroundTint="@color/fi_smoke" />

                <!-- Stub source chip kept for code compat — hidden via visibility in adapter -->
                <com.google.android.material.chip.Chip
                    android:id="@+id/chip_candidate_source"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:visibility="gone"
                    android:textSize="9sp" />

            </LinearLayout>
        </LinearLayout>

        <!-- Detail rows -->
        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:background="@color/fi_smoke"
            android:layout_marginTop="10dp"
            android:layout_marginBottom="6dp" />

        <TextView
            android:id="@+id/tv_candidate_address"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="@color/fi_ash"
            android:textSize="11sp"
            android:fontFamily="monospace"
            android:layout_marginTop="3dp"
            android:visibility="gone" />

        <TextView
            android:id="@+id/tv_candidate_relatives"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="@color/fi_smoke"
            android:textSize="11sp"
            android:fontFamily="monospace"
            android:layout_marginTop="3dp"
            android:visibility="gone" />

        <TextView
            android:id="@+id/tv_candidate_dob"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="@color/fi_amber"
            android:textSize="11sp"
            android:fontFamily="monospace"
            android:layout_marginTop="3dp"
            android:visibility="gone" />

        <TextView
            android:id="@+id/tv_candidate_akas"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="@color/fi_smoke"
            android:textSize="11sp"
            android:fontFamily="monospace"
            android:layout_marginTop="3dp"
            android:visibility="gone" />

        <TextView
            android:id="@+id/tv_candidate_email"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="@color/fi_orange"
            android:textSize="11sp"
            android:fontFamily="monospace"
            android:layout_marginTop="3dp"
            android:visibility="gone" />

        <TextView
            android:id="@+id/tv_candidate_political"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="@color/fi_smoke"
            android:textSize="11sp"
            android:fontFamily="monospace"
            android:layout_marginTop="3dp"
            android:visibility="gone" />

        <LinearLayout
            android:id="@+id/company_info_row"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:visibility="gone"
            android:layout_marginTop="6dp">

            <com.google.android.material.chip.Chip
                android:id="@+id/chip_company_domain"
                style="@style/Widget.Material3.Chip.Assist"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="10sp"
                app:chipBackgroundColor="@color/fi_surface"
                android:textColor="@color/fi_orange"
                app:chipMinHeight="24dp"
                app:chipIcon="@drawable/ic_link"
                app:chipIconSize="14dp"
                app:chipIconTint="@color/fi_orange"
                app:closeIconVisible="false" />

            <com.google.android.material.chip.Chip
                android:id="@+id/chip_company_industry"
                style="@style/Widget.Material3.Chip.Assist"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="6dp"
                android:textSize="10sp"
                app:chipBackgroundColor="@color/fi_surface"
                android:textColor="@color/fi_ash"
                app:chipMinHeight="24dp"
                app:chipIcon="@drawable/ic_business"
                app:chipIconSize="14dp"
                app:chipIconTint="@color/fi_ash"
                app:closeIconVisible="false" />
        </LinearLayout>

    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/layout/item_candidate_card.xml
git commit -m "feat: dossier-style candidate card — square photo, monospace, amber confidence badge"
```

---

## Task 9: Update candidate selection screen

**Files:**
- Modify: `app/src/main/res/layout/fragment_candidate_selection.xml`
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/ui/candidates/CandidateSelectionFragment.kt`

- [ ] **Step 1: Redesign fragment_candidate_selection.xml header + buttons**

Replace the root file content with the dossier-themed version — keep all IDs, change styling:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/fi_ink">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="CLASSIFIED // SIGINT COLLECTION TERMINAL"
            android:textColor="@color/fi_orange"
            android:textSize="8sp"
            android:fontFamily="monospace"
            android:letterSpacing="0.3"
            android:gravity="center"
            android:paddingTop="6dp"
            android:paddingBottom="6dp"
            android:background="@color/fi_orange_dim" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingStart="16dp"
            android:paddingEnd="16dp"
            android:paddingTop="12dp"
            android:paddingBottom="12dp"
            android:background="@color/fi_charcoal">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:layout_marginBottom="6dp">

                <TextView
                    android:id="@+id/tv_round_label"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="ROUND 1 OF 3"
                    android:textColor="@color/fi_orange"
                    android:textSize="9sp"
                    android:fontFamily="monospace"
                    android:letterSpacing="0.2"
                    android:textAllCaps="true" />

                <com.google.android.material.chip.Chip
                    android:id="@+id/chip_round_indicator"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="NARROWING"
                    android:textSize="8sp"
                    android:fontFamily="monospace"
                    app:chipBackgroundColor="@color/fi_orange_dim"
                    android:textColor="@color/fi_orange" />
            </LinearLayout>

            <TextView
                android:id="@+id/tv_candidate_query"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textColor="@color/fi_parchment"
                android:textSize="15sp"
                android:fontFamily="monospace"
                android:textStyle="bold"
                android:ellipsize="end"
                android:maxLines="1"
                android:layout_marginBottom="4dp" />

            <TextView
                android:id="@+id/tv_select_instructions"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Select the 1-2 best matches to investigate further"
                android:textColor="@color/fi_ash"
                android:textSize="11sp"
                android:fontFamily="monospace" />
        </LinearLayout>

        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:background="@color/fi_orange" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rv_candidates"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:clipToPadding="false"
            android:paddingTop="8dp"
            android:paddingBottom="100dp" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:orientation="horizontal"
        android:padding="16dp"
        android:background="@color/fi_charcoal">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_skip_to_results"
            style="@style/Widget.MaterialComponents.Button.OutlinedButton"
            android:layout_width="wrap_content"
            android:layout_height="52dp"
            android:text="SKIP"
            android:textColor="@color/fi_ash"
            android:fontFamily="monospace"
            android:textSize="11sp"
            android:letterSpacing="0.1"
            android:layout_marginEnd="12dp"
            app:strokeColor="@color/fi_smoke"
            app:cornerRadius="0dp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_investigate"
            android:layout_width="0dp"
            android:layout_height="52dp"
            android:layout_weight="1"
            android:text="◉  CONFIRM TARGET"
            android:textColor="@color/fi_ink"
            android:fontFamily="monospace"
            android:textSize="12sp"
            android:letterSpacing="0.1"
            android:enabled="false"
            app:backgroundTint="@color/fi_orange"
            app:cornerRadius="0dp" />
    </LinearLayout>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 2: Update CandidateSelectionFragment.kt to use fi_ colors for card selection stroke**

In `onBindViewHolder`, replace the selected card stroke logic:

```kotlin
if (isSelected) {
    card?.strokeColor = ContextCompat.getColor(requireContext(), R.color.fi_orange)
    card?.strokeWidth = 3
} else {
    card?.strokeColor = ContextCompat.getColor(requireContext(), R.color.fi_border)
    card?.strokeWidth = 1
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/fragment_candidate_selection.xml app/src/main/java/com/twoskoops707/sixdegrees/ui/candidates/CandidateSelectionFragment.kt
git commit -m "feat: dossier-style candidate selection screen — classification bar, CONFIRM TARGET button"
```

---

## Task 10: Redesign Settings theme picker

**Files:**
- Modify: `app/src/main/res/layout/fragment_settings.xml`
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/ui/settings/SettingsFragment.kt`

- [ ] **Step 1: Replace the 3-card theme section in fragment_settings.xml**

Find the APPEARANCE section (look for `cardThemeModern`, `cardThemeHacker`, `cardThemeTactical`) and replace the entire theme card row with 6 swatches in a 2-column grid:

```xml
        <!-- ── APPEARANCE ── -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="TERMINAL SKIN"
            android:textColor="?attr/colorOnSurface"
            android:textSize="9sp"
            android:fontFamily="monospace"
            android:letterSpacing="0.2"
            android:textAllCaps="true"
            android:layout_marginBottom="8dp"
            android:layout_marginTop="8dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginBottom="6dp">

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/card_theme_fieldintel"
                android:layout_width="0dp"
                android:layout_height="72dp"
                android:layout_weight="1"
                android:layout_marginEnd="4dp"
                app:cardBackgroundColor="#0E0C0A"
                app:cardCornerRadius="2dp"
                app:cardElevation="0dp"
                app:strokeColor="#3A3228"
                app:strokeWidth="1dp"
                android:clickable="true"
                android:focusable="true">
                <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent"
                    android:orientation="vertical" android:gravity="center" android:padding="6dp">
                    <View android:layout_width="24dp" android:layout_height="4dp" android:background="#E8530A" android:layout_marginBottom="4dp" />
                    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                        android:text="FIELD\nINTEL" android:textColor="#E8DCC8" android:textSize="9sp"
                        android:fontFamily="monospace" android:gravity="center" android:letterSpacing="0.1" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/card_theme_nightops"
                android:layout_width="0dp"
                android:layout_height="72dp"
                android:layout_weight="1"
                android:layout_marginEnd="4dp"
                app:cardBackgroundColor="#000000"
                app:cardCornerRadius="0dp"
                app:cardElevation="0dp"
                app:strokeColor="#0D1A0D"
                app:strokeWidth="1dp"
                android:clickable="true"
                android:focusable="true">
                <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent"
                    android:orientation="vertical" android:gravity="center" android:padding="6dp">
                    <View android:layout_width="24dp" android:layout_height="4dp" android:background="#00FF41" android:layout_marginBottom="4dp" />
                    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                        android:text="NIGHT\nOPS" android:textColor="#00FF41" android:textSize="9sp"
                        android:fontFamily="monospace" android:gravity="center" android:letterSpacing="0.1" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/card_theme_redacted"
                android:layout_width="0dp"
                android:layout_height="72dp"
                android:layout_weight="1"
                app:cardBackgroundColor="#F0EDE8"
                app:cardCornerRadius="2dp"
                app:cardElevation="0dp"
                app:strokeColor="#C8C0B8"
                app:strokeWidth="1dp"
                android:clickable="true"
                android:focusable="true">
                <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent"
                    android:orientation="vertical" android:gravity="center" android:padding="6dp">
                    <View android:layout_width="24dp" android:layout_height="4dp" android:background="#0D0D0D" android:layout_marginBottom="4dp" />
                    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                        android:text="REDACT-\nED" android:textColor="#0D0D0D" android:textSize="9sp"
                        android:fontFamily="monospace" android:gravity="center" android:letterSpacing="0.1" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginBottom="16dp">

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/card_theme_coldwar"
                android:layout_width="0dp"
                android:layout_height="72dp"
                android:layout_weight="1"
                android:layout_marginEnd="4dp"
                app:cardBackgroundColor="#1C1508"
                app:cardCornerRadius="2dp"
                app:cardElevation="0dp"
                app:strokeColor="#3D2E18"
                app:strokeWidth="1dp"
                android:clickable="true"
                android:focusable="true">
                <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent"
                    android:orientation="vertical" android:gravity="center" android:padding="6dp">
                    <View android:layout_width="24dp" android:layout_height="4dp" android:background="#C0392B" android:layout_marginBottom="4dp" />
                    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                        android:text="COLD\nWAR" android:textColor="#D4B896" android:textSize="9sp"
                        android:fontFamily="monospace" android:gravity="center" android:letterSpacing="0.1" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/card_theme_humint"
                android:layout_width="0dp"
                android:layout_height="72dp"
                android:layout_weight="1"
                android:layout_marginEnd="4dp"
                app:cardBackgroundColor="#0A0E1A"
                app:cardCornerRadius="4dp"
                app:cardElevation="0dp"
                app:strokeColor="#1E2B44"
                app:strokeWidth="1dp"
                android:clickable="true"
                android:focusable="true">
                <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent"
                    android:orientation="vertical" android:gravity="center" android:padding="6dp">
                    <View android:layout_width="24dp" android:layout_height="4dp" android:background="#4FC3F7" android:layout_marginBottom="4dp" />
                    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                        android:text="HUM-\nINT" android:textColor="#CFD8DC" android:textSize="9sp"
                        android:fontFamily="monospace" android:gravity="center" android:letterSpacing="0.1" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/card_theme_theplug"
                android:layout_width="0dp"
                android:layout_height="72dp"
                android:layout_weight="1"
                app:cardBackgroundColor="#0A0F0A"
                app:cardCornerRadius="0dp"
                app:cardElevation="0dp"
                app:strokeColor="#1E2B1E"
                app:strokeWidth="1dp"
                android:clickable="true"
                android:focusable="true">
                <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent"
                    android:orientation="vertical" android:gravity="center" android:padding="6dp">
                    <View android:layout_width="24dp" android:layout_height="4dp" android:background="#39FF14" android:layout_marginBottom="4dp" />
                    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                        android:text="THE\nPLUG" android:textColor="#E8E8DC" android:textSize="9sp"
                        android:fontFamily="monospace" android:gravity="center" android:letterSpacing="0.1" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

        </LinearLayout>
```

- [ ] **Step 2: Update SettingsFragment.kt for 6 themes**

Replace `setupEntityTypeSelector` / theme card logic. Change `updateThemeCardSelection` to handle all 6 bases and add click listeners for the 3 new cards. Full replacement of the relevant section:

```kotlin
val currentBase = prefs.getString("pref_theme_base", "fieldintel") ?: "fieldintel"
updateThemeCardSelection(currentBase)

binding.cardThemeFieldintel.setOnClickListener { selectThemeBase("fieldintel", prefs) }
binding.cardThemeNightops.setOnClickListener { selectThemeBase("nightops", prefs) }
binding.cardThemeRedacted.setOnClickListener { selectThemeBase("redacted", prefs) }
binding.cardThemeColdwar.setOnClickListener { selectThemeBase("coldwar", prefs) }
binding.cardThemeHumint.setOnClickListener { selectThemeBase("humint", prefs) }
binding.cardThemeTheplug.setOnClickListener { selectThemeBase("theplug", prefs) }
```

Replace `updateThemeCardSelection`:

```kotlin
private fun updateThemeCardSelection(selectedBase: String) {
    val b = _binding ?: return
    val ctx = context ?: return
    val activeStroke = ContextCompat.getColor(ctx, R.color.fi_orange)
    val inactiveColor = ContextCompat.getColor(ctx, R.color.border)
    val dp = resources.displayMetrics.density
    val activeWidth = (2 * dp).toInt()
    val inactiveWidth = (1 * dp).toInt()

    fun style(card: MaterialCardView, key: String) {
        val active = selectedBase == key
        card.strokeColor = if (active) activeStroke else inactiveColor
        card.strokeWidth = if (active) activeWidth else inactiveWidth
    }

    style(b.cardThemeFieldintel, "fieldintel")
    style(b.cardThemeNightops, "nightops")
    style(b.cardThemeRedacted, "redacted")
    style(b.cardThemeColdwar, "coldwar")
    style(b.cardThemeHumint, "humint")
    style(b.cardThemeTheplug, "theplug")
}
```

- [ ] **Step 3: Remove old cardThemeModern / cardThemeHacker / cardThemeTactical references from SettingsFragment.kt**

Delete or comment out the 3 old `binding.cardThemeModern.setOnClickListener`, `binding.cardThemeHacker.setOnClickListener`, `binding.cardThemeTactical.setOnClickListener` lines.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/fragment_settings.xml app/src/main/java/com/twoskoops707/sixdegrees/ui/settings/SettingsFragment.kt
git commit -m "feat: redesign settings theme picker — 6 dossier-themed swatches"
```
