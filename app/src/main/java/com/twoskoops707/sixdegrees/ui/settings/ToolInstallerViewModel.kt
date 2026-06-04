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
