package com.twoskoops707.sixdegrees

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import androidx.appcompat.app.AppCompatActivity
import com.twoskoops707.sixdegrees.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val scale = when (prefs.getString("pref_font_size", "normal")) {
            "small" -> 0.85f
            "large" -> 1.2f
            else -> 1.0f
        }
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = scale
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val base = prefs.getString("pref_theme_base", "modern") ?: "modern"
        val accent = prefs.getString("pref_accent", "blue") ?: "blue"
        val themeRes = when ("${base}_${accent}") {
            "modern_cyan"     -> R.style.Theme_SixDegrees_Modern_Cyan
            "modern_green"    -> R.style.Theme_SixDegrees_Modern_Green
            "modern_purple"   -> R.style.Theme_SixDegrees_Modern_Purple
            "hacker_green"    -> R.style.Theme_SixDegrees_Hacker_Green
            "hacker_amber"    -> R.style.Theme_SixDegrees_Hacker_Amber
            "hacker_blue"     -> R.style.Theme_SixDegrees_Hacker_Blue
            "hacker_cyan"     -> R.style.Theme_SixDegrees_Hacker_Cyan
            "hacker_purple"   -> R.style.Theme_SixDegrees_Hacker_Purple
            "tactical_blue"   -> R.style.Theme_SixDegrees_Tactical_Blue
            "tactical_cyan"   -> R.style.Theme_SixDegrees_Tactical_Cyan
            "tactical_green"  -> R.style.Theme_SixDegrees_Tactical_Green
            "tactical_purple" -> R.style.Theme_SixDegrees_Tactical_Purple
            else              -> R.style.Theme_SixDegrees_Modern_Blue
        }
        setTheme(themeRes)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initTorConnection()
        checkTermuxTools()

        val navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)!!
        val navController = navHostFragment.navController

        val topLevelDestinations = setOf(R.id.nav_search, R.id.nav_osint_resources, R.id.nav_history, R.id.nav_settings)
        appBarConfiguration = AppBarConfiguration(topLevelDestinations)

        val bottomNav = binding.appBarMain.contentMain.bottomNavView
        bottomNav?.setupWithNavController(navController)
        bottomNav?.setOnItemSelectedListener { item ->
            val currentDest = navController.currentDestination?.id
            if (currentDest == item.itemId) return@setOnItemSelectedListener true
            if (item.itemId in topLevelDestinations) {
                navController.popBackStack(item.itemId, false)
                if (navController.currentDestination?.id != item.itemId) {
                    navController.navigate(item.itemId)
                }
                true
            } else false
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val menuId = when (destination.id) {
                R.id.nav_search, R.id.nav_search_progress, R.id.nav_results, R.id.nav_wizard, R.id.nav_dork_builder, R.id.nav_candidate_selection -> R.id.nav_search
                R.id.nav_osint_resources -> R.id.nav_osint_resources
                R.id.nav_history -> R.id.nav_history
                R.id.nav_settings, R.id.nav_api_settings, R.id.nav_user_profile, R.id.nav_api_signup_web -> R.id.nav_settings
                else -> null
            }
            menuId?.let { id ->
                bottomNav?.menu?.findItem(id)?.isChecked = true
            }
        }

        if (savedInstanceState == null && !prefs.getBoolean("setup_complete", false)) {
            navController.navigate(R.id.nav_wizard)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun checkTermuxTools() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val lastCheck = prefs.getLong("termux_tools_last_check", 0L)
        if (System.currentTimeMillis() - lastCheck < 24 * 60 * 60 * 1000L) return

        val statusPath = "/storage/emulated/0/.6degrees/.6d_tools_status.txt"
        val outFile = java.io.File(statusPath)
        val tools = listOf("sherlock", "maigret", "holehe", "tor", "theharvester")
        val checks = tools.joinToString(" ; ") { t ->
            val bin = "/data/data/com.termux/files/usr/bin/$t"
            "[ -f $bin ] && echo $t:ok || echo $t:missing"
        }
        val cmd = "mkdir -p /storage/emulated/0/.6degrees && { $checks ; } > $statusPath 2>&1 ; echo __DONE__ >> $statusPath"
        try {
            val intent = Intent().apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", cmd))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            startForegroundService(intent)
        } catch (_: Exception) { return }

        lifecycleScope.launch(Dispatchers.IO) {
            var waited = 0
            while (waited < 15000) {
                delay(2000); waited += 2000
                if (outFile.exists()) {
                    val content = try { outFile.readText() } catch (_: Exception) { break }
                    if (content.contains("__DONE__")) {
                        val lines = content.lines().filter { it.contains(":ok") || it.contains(":missing") }
                        val missing = lines.filter { it.contains(":missing") }.map { it.split(":").first() }
                        val ok = lines.filter { it.contains(":ok") }.map { it.split(":").first() }
                        prefs.edit().putLong("termux_tools_last_check", System.currentTimeMillis()).apply()
                        if (missing.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                if (!isFinishing && !isDestroyed) {
                                    val installCmds = missing.joinToString("\n") { tool ->
                                        when (tool) {
                                            "sherlock" -> "pip install sherlock-project"
                                            "maigret" -> "pip install maigret"
                                            "holehe" -> "pip install holehe"
                                            "tor" -> "pkg install tor"
                                            "theharvester" -> "pip install theHarvester"
                                            else -> "pkg install $tool"
                                        }
                                    }
                                    AlertDialog.Builder(this@MainActivity)
                                        .setTitle("Termux Tools Status")
                                        .setMessage(buildString {
                                            if (ok.isNotEmpty()) append("Installed: ${ok.joinToString(", ")}\n\n")
                                            append("Missing: ${missing.joinToString(", ")}\n\n")
                                            append("Run in Termux to install:\n$installCmds")
                                        })
                                        .setPositiveButton("OK", null)
                                        .show()
                                }
                            }
                        }
                        break
                    }
                }
            }
        }
    }

    private fun initTorConnection() {
        val orbotPackage = "org.torproject.android"
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val torDismissed = prefs.getBoolean("tor_dialog_dismissed", false)
        val isOrbotInstalled = try {
            packageManager.getPackageInfo(orbotPackage, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) { false }

        if (isOrbotInstalled) {
            try {
                val intent = Intent("org.torproject.android.intent.action.START")
                intent.setPackage(orbotPackage)
                intent.putExtra("org.torproject.android.intent.extra.PACKAGE_NAME", packageName)
                startService(intent)
            } catch (_: Exception) {}
        } else if (!torDismissed) {
            window?.decorView?.post {
                if (!isFinishing && !isDestroyed) {
                    AlertDialog.Builder(this)
                        .setTitle("Tor Network (Optional)")
                        .setMessage(
                            "SixDegrees can route dark web searches through Tor for anonymity and access to .onion sites.\n\n" +
                            "Install Orbot (Tor for Android) to enable this. Dark web searches still work without it via the Ahmia clearnet index.\n\n" +
                            "Recommended: Install Orbot from Google Play or Guardian Project."
                        )
                        .setPositiveButton("Install Orbot") { _, _ ->
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=org.torproject.android")))
                            } catch (_: Exception) {}
                        }
                        .setNegativeButton("Skip") { _, _ ->
                            prefs.edit().putBoolean("tor_dialog_dismissed", true).apply()
                        }
                        .show()
                }
            }
        }
    }
}
