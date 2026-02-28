package com.twoskoops707.sixdegrees

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
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
        when (prefs.getString("pref_accent", "blue")) {
            "cyan" -> setTheme(R.style.Theme_SixDegrees_Cyan)
            "green" -> setTheme(R.style.Theme_SixDegrees_Green)
            "purple" -> setTheme(R.style.Theme_SixDegrees_Purple)
        }

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)!!
        val navController = navHostFragment.navController

        val topLevelDestinations = setOf(R.id.nav_search, R.id.nav_history, R.id.nav_settings)
        appBarConfiguration = AppBarConfiguration(topLevelDestinations)

        binding.appBarMain.contentMain.bottomNavView?.setupWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
