package com.twoskoops707.sixdegrees.ui.settings

import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.twoskoops707.sixdegrees.BuildConfig
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

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

        val prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        binding.apiKeysRow.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_api_settings)
        }
        binding.profileRow.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_profile)
        }

        binding.tvVersion.text = "Version ${BuildConfig.VERSION_NAME}"

        val currentBase = prefs.getString("pref_theme_base", "modern") ?: "modern"
        updateThemeCardSelection(currentBase)

        binding.cardThemeModern.setOnClickListener { selectThemeBase("modern", prefs) }
        binding.cardThemeHacker.setOnClickListener { selectThemeBase("hacker", prefs) }
        binding.cardThemeTactical.setOnClickListener { selectThemeBase("tactical", prefs) }

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

        binding.chipGroupFont.setOnCheckedStateChangeListener { _, checkedIds ->
            val size = when (checkedIds.firstOrNull()) {
                R.id.chip_font_small -> "small"
                R.id.chip_font_large -> "large"
                else -> "normal"
            }
            prefs.edit().putString("pref_font_size", size).apply()
            requireActivity().recreate()
        }

        binding.chipGroupAccent.setOnCheckedStateChangeListener { _, checkedIds ->
            val accent = when (checkedIds.firstOrNull()) {
                R.id.chip_accent_cyan   -> "cyan"
                R.id.chip_accent_green  -> "green"
                R.id.chip_accent_purple -> "purple"
                R.id.chip_accent_amber  -> "amber"
                else -> "blue"
            }
            prefs.edit().putString("pref_accent", accent).apply()
            requireActivity().recreate()
        }

        binding.switchAnimations.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean("pref_animations", enabled).apply()
            ValueAnimator.setDurationScale(if (enabled) 1f else 0f)
        }

        val animEnabled = prefs.getBoolean("pref_animations", true)
        ValueAnimator.setDurationScale(if (animEnabled) 1f else 0f)
    }

    private fun selectThemeBase(base: String, prefs: android.content.SharedPreferences) {
        prefs.edit().putString("pref_theme_base", base).apply()
        requireActivity().recreate()
    }

    private fun updateThemeCardSelection(selectedBase: String) {
        val ctx = requireContext()
        val activeStroke = ContextCompat.getColor(ctx, R.color.accent_blue)
        val inactiveStroke = ContextCompat.getColor(ctx, R.color.border)
        val dp = resources.displayMetrics.density
        val activeWidth = (2 * dp).toInt()
        val inactiveWidth = (1 * dp).toInt()

        fun style(card: MaterialCardView, active: Boolean) {
            card.strokeColor = if (active) activeStroke else inactiveStroke
            card.strokeWidth = if (active) activeWidth else inactiveWidth
        }

        style(binding.cardThemeModern, selectedBase == "modern")
        style(binding.cardThemeHacker, selectedBase == "hacker")
        style(binding.cardThemeTactical, selectedBase == "tactical")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
