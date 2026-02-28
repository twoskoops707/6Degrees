package com.twoskoops707.sixdegrees.ui.settings

import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
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

        when (prefs.getString("pref_font_size", "normal")) {
            "small" -> binding.chipFontSmall.isChecked = true
            "large" -> binding.chipFontLarge.isChecked = true
            else -> binding.chipFontNormal.isChecked = true
        }

        when (prefs.getString("pref_accent", "blue")) {
            "cyan" -> binding.chipAccentCyan.isChecked = true
            "green" -> binding.chipAccentGreen.isChecked = true
            "purple" -> binding.chipAccentPurple.isChecked = true
            else -> binding.chipAccentBlue.isChecked = true
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
                R.id.chip_accent_cyan -> "cyan"
                R.id.chip_accent_green -> "green"
                R.id.chip_accent_purple -> "purple"
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
