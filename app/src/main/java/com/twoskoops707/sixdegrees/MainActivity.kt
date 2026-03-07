package com.twoskoops707.sixdegrees

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import androidx.appcompat.app.AppCompatActivity
import com.twoskoops707.sixdegrees.databinding.ActivityMainBinding

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

        val navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)!!
        val navController = navHostFragment.navController

        val topLevelDestinations = setOf(R.id.nav_search, R.id.nav_history, R.id.nav_settings)
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
                R.id.nav_search, R.id.nav_search_progress, R.id.nav_results, R.id.nav_wizard, R.id.nav_dork_builder -> R.id.nav_search
                R.id.nav_history -> R.id.nav_history
                R.id.nav_settings, R.id.nav_api_settings, R.id.nav_user_profile -> R.id.nav_settings
                else -> null
            }
            menuId?.let { id ->
                bottomNav?.menu?.findItem(id)?.isChecked = true
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
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
