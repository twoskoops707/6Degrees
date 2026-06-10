package com.twoskoops707.sixdegrees

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.twoskoops707.sixdegrees.data.AppSettings
import com.twoskoops707.sixdegrees.databinding.ActivityMainBinding
import com.twoskoops707.sixdegrees.ui.settings.PlugBgFactory
import com.twoskoops707.sixdegrees.ui.theme.PatrinoJitterHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var patrinoJitter: PatrinoJitterHelper? = null

    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            showTermuxOnboardingIfNeeded()
        }

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
        val themeRes = when (base) {
            "nightops"   -> R.style.Theme_SixDegrees_NightOps
            "redacted"   -> R.style.Theme_SixDegrees_Redacted
            "coldwar"    -> R.style.Theme_SixDegrees_ColdWar
            "humint"     -> R.style.Theme_SixDegrees_Humint
            "theplug"    -> R.style.Theme_SixDegrees_ThePlug
            "patrino"    -> R.style.Theme_SixDegrees_Patrino
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
                else            -> R.style.Theme_SixDegrees_Modern
            }
        }
        setTheme(themeRes)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (base == "theplug") {
            val plugBg = prefs.getString("pref_plug_bg", "gradient") ?: "gradient"
            window.setBackgroundDrawable(PlugBgFactory.createBg(this, plugBg))
        }

        if (base == "patrino") {
            patrinoJitter = PatrinoJitterHelper(binding.root) {
                prefs.getBoolean("pref_animations", true) &&
                    !PatrinoJitterHelper.isReduceMotionEnabled(this)
            }
            patrinoJitter?.start()
        }

        com.twoskoops707.sixdegrees.tor.TorBootstrapManager.start(this)
        initTorConnection()
        checkTermuxTools()

        val navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)!!
        val navController = navHostFragment.navController

        val topLevelDestinations = setOf(R.id.nav_search, R.id.nav_osint_resources, R.id.nav_history, R.id.nav_settings)
        appBarConfiguration = AppBarConfiguration(topLevelDestinations)

        val bottomNav = binding.appBarMain.contentMain.bottomNavView
        bottomNav?.setupWithNavController(navController)
        updateBottomNavForInvestigatorMode(bottomNav)
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
        val investigationFlowDestinations = setOf(
            R.id.nav_search_progress,
            R.id.nav_candidate_selection,
            R.id.nav_results
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNav?.visibility = if (destination.id in investigationFlowDestinations) {
                View.GONE
            } else {
                View.VISIBLE
            }

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

        if (!prefs.getBoolean("perms_requested", false)) {
            requestFirstLaunchPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        updateBottomNavForInvestigatorMode(binding.appBarMain.contentMain.bottomNavView)
    }

    private fun updateBottomNavForInvestigatorMode(bottomNav: com.google.android.material.bottomnavigation.BottomNavigationView?) {
        bottomNav?.menu?.findItem(R.id.nav_osint_resources)?.isVisible =
            AppSettings.isInvestigatorMode(this)
    }

    private fun requestFirstLaunchPermissions() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        prefs.edit().putBoolean("perms_requested", true).apply()

        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.CAMERA)

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            showTermuxOnboardingIfNeeded()
        }
    }

    private fun showTermuxOnboardingIfNeeded() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        if (prefs.getBoolean("termux_onboarding_shown", false)) return
        val isTermuxInstalled = try {
            packageManager.getPackageInfo("com.termux", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) { false }
        if (!isTermuxInstalled) return

        prefs.edit().putBoolean("termux_onboarding_shown", true).apply()
        window?.decorView?.post {
            if (!isFinishing && !isDestroyed) {
                AlertDialog.Builder(this)
                    .setTitle("Connect to Termux")
                    .setMessage(
                        "6Degrees can use Termux CLI tools (sherlock, maigret, holehe, nmap) to supercharge your searches.\n\n" +
                        "To enable this, open Termux and run:\n\n" +
                        "  termux-setup-storage\n\n" +
                        "Then go to Termux → Settings → Advanced and enable:\n\n" +
                        "  \"Allow External Apps\"\n\n" +
                        "Without this, CLI tools are silently skipped and everything else works normally."
                    )
                    .setPositiveButton("Got it") { _, _ -> }
                    .setNeutralButton("Open Termux") { _, _ ->
                        try {
                            startActivity(packageManager.getLaunchIntentForPackage("com.termux"))
                        } catch (_: Exception) {}
                    }
                    .show()
            }
        }
    }

    override fun onDestroy() {
        patrinoJitter?.stop()
        patrinoJitter = null
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun checkTermuxTools() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val lastCheck = prefs.getLong("termux_tools_last_check", 0L)
        val runner = com.twoskoops707.sixdegrees.data.repository.TermuxToolRunner(this)
        val cachedStatus = runner.readToolStatus()
        if (System.currentTimeMillis() - lastCheck < 24 * 60 * 60 * 1000L && cachedStatus.isNotEmpty()) return

        if (!runner.isTermuxInstalled()) {
            prefs.edit().putString("infra_status_summary", "Termux not installed").apply()
            return
        }
        val outFile = java.io.File(runner.statusFilePath())
        try {
            runner.requestToolStatusRefresh()
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
                        prefs.edit()
                            .putLong("termux_tools_last_check", System.currentTimeMillis())
                            .putString("infra_status_summary", runner.infrastructureSummary())
                            .apply()
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
