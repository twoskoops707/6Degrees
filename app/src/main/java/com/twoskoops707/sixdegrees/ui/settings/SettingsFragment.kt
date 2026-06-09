package com.twoskoops707.sixdegrees.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.twoskoops707.sixdegrees.BuildConfig
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var isInitializing = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isInitializing = true

        val prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        binding.apiKeysRow.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_api_settings)
        }
        binding.profileRow.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_profile)
        }
        binding.wizardRow.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_wizard)
        }
        binding.toolInstallerRow.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_tool_installer)
        }

        binding.tvVersion.text = "Version ${BuildConfig.VERSION_NAME}"

        val currentBase = prefs.getString("pref_theme_base", "modern") ?: "modern"
        updateThemeCardSelection(currentBase)

        binding.cardThemeFieldintel.setOnClickListener { selectThemeBase("modern", prefs) }
        binding.cardThemeNightops.setOnClickListener { selectThemeBase("nightops", prefs) }
        binding.cardThemeRedacted.setOnClickListener { selectThemeBase("redacted", prefs) }
        binding.cardThemeColdwar.setOnClickListener { selectThemeBase("coldwar", prefs) }
        binding.cardThemeHumint.setOnClickListener { selectThemeBase("humint", prefs) }
        binding.cardThemeTheplug.setOnClickListener { selectThemeBase("theplug", prefs) }

        binding.plugBgSection.visibility = if (currentBase == "theplug") View.VISIBLE else View.GONE

        val currentPlugBg = prefs.getString("pref_plug_bg", "rasta") ?: "rasta"
        updatePlugBgSelection(currentPlugBg)

        binding.cardPlugBgRasta.setOnClickListener { selectPlugBg("rasta", prefs) }
        binding.cardPlugBgLeaves.setOnClickListener { selectPlugBg("leaves", prefs) }
        binding.cardPlugBgBricks.setOnClickListener { selectPlugBg("bricks", prefs) }
        binding.cardPlugBgMedellin.setOnClickListener { selectPlugBg("medellin", prefs) }

        when (prefs.getString("pref_font_size", "normal")) {
            "small" -> binding.chipFontSmall.isChecked = true
            "large" -> binding.chipFontLarge.isChecked = true
            else -> binding.chipFontNormal.isChecked = true
        }

        when (prefs.getString("pref_accent", "blue")) {
            "cyan"   -> binding.chipAccentCyan.isChecked = true
            "green"  -> binding.chipAccentGreen.isChecked = true
            "purple" -> binding.chipAccentPurple.isChecked = true
            "amber"  -> binding.chipAccentAmber.isChecked = true
            else     -> binding.chipAccentBlue.isChecked = true
        }

        binding.switchAnimations.isChecked = prefs.getBoolean("pref_animations", true)
        binding.switchConnections.isChecked = prefs.getBoolean("pref_connections_enabled", true)

        when (prefs.getString("pref_browser", "firefox")) {
            "ddg"     -> binding.chipBrowserDdg.isChecked = true
            "chrome"  -> binding.chipBrowserChrome.isChecked = true
            "default" -> binding.chipBrowserDefault.isChecked = true
            else      -> binding.chipBrowserFirefox.isChecked = true
        }

        isInitializing = false

        binding.chipGroupBrowser.setOnCheckedStateChangeListener { _, checkedIds ->
            if (isInitializing) return@setOnCheckedStateChangeListener
            val browser = when (checkedIds.firstOrNull()) {
                R.id.chip_browser_ddg     -> "ddg"
                R.id.chip_browser_chrome  -> "chrome"
                R.id.chip_browser_default -> "default"
                else -> "firefox"
            }
            prefs.edit().putString("pref_browser", browser).apply()
        }

        binding.switchConnections.setOnCheckedChangeListener { _, enabled ->
            if (isInitializing) return@setOnCheckedChangeListener
            prefs.edit().putBoolean("pref_connections_enabled", enabled).apply()
        }

        binding.chipGroupFont.setOnCheckedStateChangeListener { _, checkedIds ->
            if (isInitializing) return@setOnCheckedStateChangeListener
            val size = when (checkedIds.firstOrNull()) {
                R.id.chip_font_small -> "small"
                R.id.chip_font_large -> "large"
                else -> "normal"
            }
            val current = prefs.getString("pref_font_size", "normal")
            if (size != current) {
                prefs.edit().putString("pref_font_size", size).apply()
                view?.post { if (_binding != null) activity?.recreate() }
            }
        }

        binding.chipGroupAccent.setOnCheckedStateChangeListener { _, checkedIds ->
            if (isInitializing) return@setOnCheckedStateChangeListener
            val accent = when (checkedIds.firstOrNull()) {
                R.id.chip_accent_cyan   -> "cyan"
                R.id.chip_accent_green  -> "green"
                R.id.chip_accent_purple -> "purple"
                R.id.chip_accent_amber  -> "amber"
                else -> "blue"
            }
            val current = prefs.getString("pref_accent", "blue")
            if (accent != current) {
                prefs.edit().putString("pref_accent", accent).apply()
                view?.post { if (_binding != null) activity?.recreate() }
            }
        }

        binding.switchAnimations.setOnCheckedChangeListener { _, enabled ->
            if (isInitializing) return@setOnCheckedChangeListener
            prefs.edit().putBoolean("pref_animations", enabled).apply()
        }
    }

    private fun selectThemeBase(base: String, prefs: android.content.SharedPreferences) {
        val current = prefs.getString("pref_theme_base", "modern")
        prefs.edit().putString("pref_theme_base", base).apply()
        if (_binding != null) {
            updateThemeCardSelection(base)
            binding.plugBgSection.visibility = if (base == "theplug") View.VISIBLE else View.GONE
        }
        if (base != current) {
            view?.post { if (_binding != null) activity?.recreate() }
        }
    }

    private fun selectPlugBg(variant: String, prefs: android.content.SharedPreferences) {
        val current = prefs.getString("pref_plug_bg", "rasta")
        prefs.edit().putString("pref_plug_bg", variant).apply()
        if (_binding != null) updatePlugBgSelection(variant)
        if (variant != current) {
            view?.post { if (_binding != null) activity?.recreate() }
        }
    }

    private fun updatePlugBgSelection(selected: String) {
        val b = _binding ?: return
        val ctx = context ?: return
        val activeStroke = ContextCompat.getColor(ctx, R.color.plug_green)
        val inactiveStroke = ContextCompat.getColor(ctx, R.color.plug_border)
        val dp = resources.displayMetrics.density
        val activeWidth = (2 * dp).toInt()
        val inactiveWidth = (1 * dp).toInt()
        fun style(card: MaterialCardView, active: Boolean) {
            card.strokeColor = if (active) activeStroke else inactiveStroke
            card.strokeWidth = if (active) activeWidth else inactiveWidth
        }
        style(b.cardPlugBgRasta, selected == "rasta")
        style(b.cardPlugBgLeaves, selected == "leaves")
        style(b.cardPlugBgBricks, selected == "bricks")
        style(b.cardPlugBgMedellin, selected == "medellin")
    }

    private fun updateThemeCardSelection(selectedBase: String) {
        val b = _binding ?: return
        val ctx = context ?: return
        val activeStroke = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary, "SettingsFragment")
        val inactiveStroke = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOutline, "SettingsFragment")
        val dp = resources.displayMetrics.density
        val activeWidth = (2 * dp).toInt()
        val inactiveWidth = (1 * dp).toInt()

        fun style(card: MaterialCardView, active: Boolean) {
            card.strokeColor = if (active) activeStroke else inactiveStroke
            card.strokeWidth = if (active) activeWidth else inactiveWidth
        }

        style(b.cardThemeFieldintel, selectedBase == "modern" || selectedBase == "fieldintel")
        style(b.cardThemeNightops, selectedBase == "nightops")
        style(b.cardThemeRedacted, selectedBase == "redacted")
        style(b.cardThemeColdwar, selectedBase == "coldwar")
        style(b.cardThemeHumint, selectedBase == "humint")
        style(b.cardThemeTheplug, selectedBase == "theplug")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
