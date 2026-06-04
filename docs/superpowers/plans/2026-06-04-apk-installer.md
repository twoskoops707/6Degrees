# APK Installer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Tool Installer screen to 6Degrees that lets users download and install Termux + Termux:API directly, and browse GitHub/F-Droid pages for other OSINT tool APKs.

**Architecture:** New `ToolInstallerFragment` + `ToolInstallerViewModel` + `ApkDownloader` under `ui/settings/`. Fragment has two sections: direct-download cards for Termux/Termux:API, browser-link rows for other tools. `ApkDownloader` wraps OkHttp download into a `Flow<DownloadState>`. Navigation entry added from SettingsFragment.

**Tech Stack:** OkHttp 4.12 (already in project), FileProvider (already declared), PackageInstaller intent, Kotlin Coroutines Flow, ViewBinding

---

### Task 1: Add REQUEST_INSTALL_PACKAGES permission to manifest

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add permission**

In `AndroidManifest.xml`, after the existing `<uses-permission android:name="com.termux.permission.RUN_COMMAND" />` line, add:

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: add REQUEST_INSTALL_PACKAGES permission for APK installer"
```

---

### Task 2: Create ApkDownloader

**Files:**
- Create: `app/src/main/java/com/twoskoops707/sixdegrees/data/ApkDownloader.kt`

- [ ] **Step 1: Create ApkDownloader.kt**

```kotlin
package com.twoskoops707.sixdegrees.data

import android.content.Context
import com.twoskoops707.sixdegrees.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Request
import java.io.File

sealed class DownloadState {
    data class Downloading(val percent: Int) : DownloadState()
    data class Done(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class ApkDownloader(private val context: Context) {

    fun download(url: String, fileName: String): Flow<DownloadState> = flow {
        val cacheDir = File(context.cacheDir, "apks").also { it.mkdirs() }
        val outFile = File(cacheDir, fileName)

        try {
            val request = Request.Builder().url(url).build()
            val response = RetrofitClient.fastHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(DownloadState.Error("HTTP ${response.code}"))
                return@flow
            }

            val body = response.body ?: run {
                emit(DownloadState.Error("Empty response"))
                return@flow
            }

            val contentLength = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = outFile.outputStream()
            val buffer = ByteArray(8192)
            var bytesRead = 0L
            var read: Int

            outputStream.use { out ->
                while (inputStream.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                    bytesRead += read
                    if (contentLength > 0) {
                        emit(DownloadState.Downloading(((bytesRead * 100) / contentLength).toInt()))
                    }
                }
            }

            emit(DownloadState.Done(outFile))
        } catch (e: Exception) {
            outFile.delete()
            emit(DownloadState.Error(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/data/ApkDownloader.kt
git commit -m "feat: add ApkDownloader with Flow-based progress"
```

---

### Task 3: Create ToolInstallerViewModel

**Files:**
- Create: `app/src/main/java/com/twoskoops707/sixdegrees/ui/settings/ToolInstallerViewModel.kt`

- [ ] **Step 1: Create ToolInstallerViewModel.kt**

```kotlin
package com.twoskoops707.sixdegrees.ui.settings

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twoskoops707.sixdegrees.data.ApkDownloader
import com.twoskoops707.sixdegrees.data.DownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ApkEntry(
    val id: String,
    val displayName: String,
    val packageName: String,
    val description: String,
    val downloadUrl: String,
    val fileName: String
)

sealed class InstallState {
    object Idle : InstallState()
    data class Downloading(val percent: Int) : InstallState()
    data class ReadyToInstall(val file: File) : InstallState()
    data class Error(val message: String) : InstallState()
    object Installed : InstallState()
}

class ToolInstallerViewModel(app: Application) : AndroidViewModel(app) {

    private val downloader = ApkDownloader(app)

    val foundationApks = listOf(
        ApkEntry(
            id = "termux",
            displayName = "Termux",
            packageName = "com.termux",
            description = "Terminal emulator and Linux environment",
            downloadUrl = "https://f-droid.org/repo/com.termux_1020.apk",
            fileName = "termux.apk"
        ),
        ApkEntry(
            id = "termux_api",
            displayName = "Termux:API",
            packageName = "com.termux.api",
            description = "Access Android APIs from Termux scripts",
            downloadUrl = "https://f-droid.org/repo/com.termux.api_51.apk",
            fileName = "termux_api.apk"
        )
    )

    val browserApks = listOf(
        ApkEntry(
            id = "nethunter",
            displayName = "NetHunter Rootless",
            packageName = "com.offsec.nethunter",
            description = "Kali Linux tools without root",
            downloadUrl = "https://github.com/offensive-security/kali-nethunter-app/releases",
            fileName = ""
        ),
        ApkEntry(
            id = "nethunter_store",
            displayName = "NetHunter App Store",
            packageName = "com.offsec.nethunter.store",
            description = "Browse and install security tool APKs",
            downloadUrl = "https://store.nethunter.com/",
            fileName = ""
        )
    )

    private val _installStates = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val installStates: StateFlow<Map<String, InstallState>> = _installStates

    fun isInstalled(packageName: String): Boolean {
        return try {
            getApplication<Application>().packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) { false }
    }

    fun download(apk: ApkEntry) {
        viewModelScope.launch {
            downloader.download(apk.downloadUrl, apk.fileName).collect { state ->
                val newState: InstallState = when (state) {
                    is DownloadState.Downloading -> InstallState.Downloading(state.percent)
                    is DownloadState.Done -> InstallState.ReadyToInstall(state.file)
                    is DownloadState.Error -> InstallState.Error(state.message)
                }
                _installStates.value = _installStates.value + (apk.id to newState)
            }
        }
    }

    fun installApk(apk: ApkEntry, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            getApplication(),
            "${getApplication<Application>().packageName}.provider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/ui/settings/ToolInstallerViewModel.kt
git commit -m "feat: add ToolInstallerViewModel with download state and install intent"
```

---

### Task 4: Create fragment_tool_installer.xml layout

**Files:**
- Create: `app/src/main/res/layout/fragment_tool_installer.xml`

- [ ] **Step 1: Create layout**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/fi_ink">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/toolbar"
        android:layout_width="match_parent"
        android:layout_height="52dp"
        android:background="@color/fi_charcoal"
        app:title="TOOL INSTALLER"
        app:titleTextColor="@color/fi_parchment"
        app:navigationIcon="@drawable/ic_arrow_back"
        app:navigationIconTint="@color/fi_ash" />

    <View
        android:layout_width="match_parent"
        android:layout_height="2dp"
        android:background="@color/fi_orange" />

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingStart="20dp"
            android:paddingEnd="20dp"
            android:paddingTop="16dp"
            android:paddingBottom="24dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="FOUNDATION"
                android:textColor="@color/fi_ash"
                android:textSize="9sp"
                android:fontFamily="monospace"
                android:letterSpacing="0.18"
                android:textAllCaps="true"
                android:layout_marginBottom="10dp" />

            <LinearLayout
                android:id="@+id/container_foundation"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="OSINT TOOLS"
                android:textColor="@color/fi_ash"
                android:textSize="9sp"
                android:fontFamily="monospace"
                android:letterSpacing="0.18"
                android:textAllCaps="true"
                android:layout_marginTop="24dp"
                android:layout_marginBottom="10dp" />

            <LinearLayout
                android:id="@+id/container_browser"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical" />

        </LinearLayout>

    </androidx.core.widget.NestedScrollView>

</LinearLayout>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/layout/fragment_tool_installer.xml
git commit -m "feat: add fragment_tool_installer layout"
```

---

### Task 5: Create ToolInstallerFragment

**Files:**
- Create: `app/src/main/java/com/twoskoops707/sixdegrees/ui/settings/ToolInstallerFragment.kt`

- [ ] **Step 1: Create ToolInstallerFragment.kt**

```kotlin
package com.twoskoops707.sixdegrees.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.databinding.FragmentToolInstallerBinding
import kotlinx.coroutines.launch

class ToolInstallerFragment : Fragment() {

    private var _binding: FragmentToolInstallerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ToolInstallerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolInstallerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        buildFoundationSection()
        buildBrowserSection()
        observeStates()
    }

    private fun buildFoundationSection() {
        val ctx = requireContext()
        val d = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * d).toInt()

        viewModel.foundationApks.forEach { apk ->
            val card = com.google.android.material.card.MaterialCardView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(10f) }
                setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.fi_charcoal))
                radius = 0f
                cardElevation = 0f
                strokeColor = ContextCompat.getColor(ctx, R.color.fi_border)
                strokeWidth = dp(1f)
            }

            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            }

            val nameTv = TextView(ctx).apply {
                text = apk.displayName
                textSize = 15f
                setTextColor(ContextCompat.getColor(ctx, R.color.fi_parchment))
                typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
                letterSpacing = 0.04f
            }

            val descTv = TextView(ctx).apply {
                text = apk.description
                textSize = 11f
                setTextColor(ContextCompat.getColor(ctx, R.color.fi_ash))
                typeface = android.graphics.Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(2f); it.bottomMargin = dp(10f) }
            }

            val progress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(4f)
                ).also { it.bottomMargin = dp(8f) }
                progressTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.fi_orange)
                )
                max = 100
                visibility = View.GONE
            }

            val btn = MaterialButton(ctx).apply {
                tag = apk.id
                text = if (viewModel.isInstalled(apk.packageName)) "INSTALLED" else "DOWNLOAD"
                isEnabled = !viewModel.isInstalled(apk.packageName)
                setBackgroundColor(ContextCompat.getColor(ctx,
                    if (viewModel.isInstalled(apk.packageName)) R.color.fi_charcoal else R.color.fi_orange))
                setTextColor(ContextCompat.getColor(ctx,
                    if (viewModel.isInstalled(apk.packageName)) R.color.fi_ash else R.color.fi_ink))
                textSize = 11f
                letterSpacing = 0.1f
                cornerRadius = 0
                setOnClickListener {
                    viewModel.download(apk)
                }
            }

            inner.addView(nameTv)
            inner.addView(descTv)
            inner.addView(progress)
            inner.addView(btn)
            card.addView(inner)
            binding.containerFoundation.addView(card)

            // Store views for state updates
            card.setTag(R.id.btn_web_hub, progress)
            card.setTag(R.id.btn_export, btn)
        }
    }

    private fun buildBrowserSection() {
        val ctx = requireContext()
        val d = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * d).toInt()

        viewModel.browserApks.forEach { apk ->
            val card = com.google.android.material.card.MaterialCardView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(10f) }
                setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.fi_charcoal))
                radius = 0f
                cardElevation = 0f
                strokeColor = ContextCompat.getColor(ctx, R.color.fi_border)
                strokeWidth = dp(1f)
            }

            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            }

            val textCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameTv = TextView(ctx).apply {
                text = apk.displayName
                textSize = 14f
                setTextColor(ContextCompat.getColor(ctx, R.color.fi_parchment))
                typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
            }

            val descTv = TextView(ctx).apply {
                text = apk.description
                textSize = 11f
                setTextColor(ContextCompat.getColor(ctx, R.color.fi_ash))
                typeface = android.graphics.Typeface.MONOSPACE
            }

            val btn = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "VIEW"
                textSize = 10f
                letterSpacing = 0.1f
                cornerRadius = 0
                strokeColor = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.fi_orange)
                )
                setTextColor(ContextCompat.getColor(ctx, R.color.fi_orange))
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apk.downloadUrl)))
                }
            }

            textCol.addView(nameTv)
            textCol.addView(descTv)
            inner.addView(textCol)
            inner.addView(btn)
            card.addView(inner)
            binding.containerBrowser.addView(card)
        }
    }

    private fun observeStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.installStates.collect { states ->
                states.forEach { (apkId, state) ->
                    val apk = viewModel.foundationApks.find { it.id == apkId } ?: return@forEach
                    val cardIndex = viewModel.foundationApks.indexOf(apk)
                    val card = binding.containerFoundation.getChildAt(cardIndex)
                        as? com.google.android.material.card.MaterialCardView ?: return@forEach
                    val progress = card.getTag(R.id.btn_web_hub) as? ProgressBar ?: return@forEach
                    val btn = card.getTag(R.id.btn_export) as? MaterialButton ?: return@forEach

                    when (state) {
                        is InstallState.Downloading -> {
                            progress.visibility = View.VISIBLE
                            progress.progress = state.percent
                            btn.text = "${state.percent}%"
                            btn.isEnabled = false
                        }
                        is InstallState.ReadyToInstall -> {
                            progress.visibility = View.GONE
                            btn.text = "INSTALL"
                            btn.isEnabled = true
                            btn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.fi_orange))
                            btn.setOnClickListener {
                                startActivity(viewModel.installApk(apk, state.file))
                            }
                        }
                        is InstallState.Error -> {
                            progress.visibility = View.GONE
                            btn.text = "RETRY"
                            btn.isEnabled = true
                            btn.setOnClickListener { viewModel.download(apk) }
                        }
                        InstallState.Idle, InstallState.Installed -> {}
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/twoskoops707/sixdegrees/ui/settings/ToolInstallerFragment.kt
git commit -m "feat: add ToolInstallerFragment with download+install and browser-link sections"
```

---

### Task 6: Wire navigation and Settings entry

**Files:**
- Modify: `app/src/main/res/navigation/mobile_navigation.xml`
- Modify: `app/src/main/java/com/twoskoops707/sixdegrees/ui/settings/SettingsFragment.kt`
- Modify: `app/src/main/res/layout/fragment_settings.xml`

- [ ] **Step 1: Add nav destination to mobile_navigation.xml**

Inside the `<navigation>` element, add before the closing tag:

```xml
<fragment
    android:id="@+id/nav_tool_installer"
    android:name="com.twoskoops707.sixdegrees.ui.settings.ToolInstallerFragment"
    android:label="Tool Installer"
    tools:layout="@layout/fragment_tool_installer" />
```

Add action to `nav_settings` fragment block (after `action_settings_to_wizard`):

```xml
<action
    android:id="@+id/action_settings_to_tool_installer"
    app:destination="@id/nav_tool_installer" />
```

- [ ] **Step 2: Add "Tool Installer" row to fragment_settings.xml**

Find the existing `wizardRow` in `fragment_settings.xml`. Add a similar row after it:

```xml
<LinearLayout
    android:id="@+id/toolInstallerRow"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="16dp"
    android:paddingEnd="16dp"
    android:paddingTop="14dp"
    android:paddingBottom="14dp"
    android:clickable="true"
    android:focusable="true"
    android:background="?attr/selectableItemBackground">

    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="Tool Installer"
        android:textSize="14sp"
        android:textColor="@color/fi_parchment"
        android:fontFamily="sans-serif-condensed" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="APK download ›"
        android:textSize="11sp"
        android:textColor="@color/fi_orange"
        android:fontFamily="monospace" />

</LinearLayout>
```

- [ ] **Step 3: Wire click in SettingsFragment.kt**

In `SettingsFragment.onViewCreated()`, after the existing `binding.wizardRow.setOnClickListener` block, add:

```kotlin
binding.toolInstallerRow.setOnClickListener {
    findNavController().navigate(R.id.action_settings_to_tool_installer)
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/navigation/mobile_navigation.xml \
        app/src/main/java/com/twoskoops707/sixdegrees/ui/settings/SettingsFragment.kt \
        app/src/main/res/layout/fragment_settings.xml
git commit -m "feat: add Tool Installer navigation and Settings entry"
```

---

### Task 7: Push and build

- [ ] **Step 1: Push all commits**

```bash
git push origin main
```

- [ ] **Step 2: Monitor GitHub Actions build**

```bash
gh run list --limit 3
gh run watch
```
