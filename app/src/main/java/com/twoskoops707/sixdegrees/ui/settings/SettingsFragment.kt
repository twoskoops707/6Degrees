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
import com.google.android.material.chip.Chip
import com.twoskoops707.sixdegrees.data.AppSettings
import com.twoskoops707.sixdegrees.data.SearchPresetManager
import com.twoskoops707.sixdegrees.data.repository.TermuxToolRunner
import com.twoskoops707.sixdegrees.databinding.FragmentSettingsBinding
import com.twoskoops707.sixdegrees.tor.TorBootstrapManager

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var isInitializing = true
    private var advancedExpanded = false

    override fun onResume() {
        super.onResume()
        refreshInfrastructureStatus()
        if (_binding != null) applyInvestigatorModeUi()
    }

    private fun refreshInfrastructureStatus() {
        if (_binding == null) return
        val ctx = requireContext()
        val runner = TermuxToolRunner(ctx)
        val summary = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getString("infra_status_summary", null)
            ?: runner.infrastructureSummary()
        binding.tvInfraStatus.text = summary
        binding.tvInfraStatus.visibility = View.VISIBLE
        if (TorBootstrapManager.isPortOpen()) {
            binding.tvInfraStatus.setTextColor(ContextCompat.getColor(ctx, R.color.score_green))
        }
    }

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

        binding.switchInvestigatorMode.isChecked =
            prefs.getBoolean(AppSettings.KEY_INVESTIGATOR_MODE, false)
        binding.switchAiAgentAssist.isChecked =
            prefs.getBoolean(AppSettings.KEY_AI_AGENT_ASSIST, false)
        advancedExpanded = prefs.getBoolean(AppSettings.KEY_INVESTIGATOR_MODE, false)
        binding.advancedHeaderRow.setOnClickListener { toggleAdvancedSection() }
        binding.switchInvestigatorMode.setOnCheckedChangeListener { _, enabled ->
            if (isInitializing) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(AppSettings.KEY_INVESTIGATOR_MODE, enabled).apply()
            if (enabled) advancedExpanded = true
            applyInvestigatorModeUi()
            activity?.invalidateOptionsMenu()
        }
        binding.switchAiAgentAssist.setOnCheckedChangeListener { _, enabled ->
            if (isInitializing) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(AppSettings.KEY_AI_AGENT_ASSIST, enabled).apply()
        }
        applyInvestigatorModeUi()

        refreshInfrastructureStatus()

        binding.tvVersion.text = "Version ${BuildConfig.VERSION_NAME}"

        val currentBase = prefs.getString("pref_theme_base", "modern") ?: "modern"
        updateThemeCardSelection(currentBase)

        binding.cardThemeFieldintel.setOnClickListener { selectThemeBase("modern", prefs) }
        binding.cardThemeNightops.setOnClickListener { selectThemeBase("nightops", prefs) }
        binding.cardThemeRedacted.setOnClickListener { selectThemeBase("redacted", prefs) }
        binding.cardThemeColdwar.setOnClickListener { selectThemeBase("coldwar", prefs) }
        binding.cardThemeHumint.setOnClickListener { selectThemeBase("humint", prefs) }
        binding.cardThemeTheplug.setOnClickListener { selectThemeBase("theplug", prefs) }
        binding.cardThemePatrino.setOnClickListener { selectThemeBase("patrino", prefs) }

        binding.plugBgSection.visibility = if (currentBase == "theplug") View.VISIBLE else View.GONE

        val currentPlugBg = prefs.getString("pref_plug_bg", "bricks") ?: "bricks"
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

        setupSearchPresets(prefs)
    }

    private fun setupSearchPresets(prefs: android.content.SharedPreferences) {
        val active = SearchPresetManager.getActivePresetId(requireContext())
        when (active) {
            SearchPresetManager.PRESET_PEOPLE -> binding.chipPresetPeople.isChecked = true
            SearchPresetManager.PRESET_PHONE -> binding.chipPresetPhone.isChecked = true
            SearchPresetManager.PRESET_EMAIL -> binding.chipPresetEmail.isChecked = true
            SearchPresetManager.PRESET_DARKWEB -> binding.chipPresetDarkweb.isChecked = true
            SearchPresetManager.PRESET_COURTS_SEC -> binding.chipPresetCourts.isChecked = true
            else -> binding.chipPresetFull.isChecked = true
        }

        binding.chipGroupCustomCategories.removeAllViews()
        val customSelected = SearchPresetManager.getCustomCategories(requireContext())
        SearchPresetManager.ALL_CATEGORIES.forEach { catId ->
            val chip = Chip(requireContext()).apply {
                text = SearchPresetManager.CATEGORY_LABELS[catId] ?: catId
                isCheckable = true
                isChecked = catId in customSelected
                tag = catId
            }
            binding.chipGroupCustomCategories.addView(chip)
        }

        binding.chipGroupSearchPreset.setOnCheckedStateChangeListener { _, checkedIds ->
            if (isInitializing) return@setOnCheckedStateChangeListener
            val presetId = when (checkedIds.firstOrNull()) {
                R.id.chip_preset_people -> SearchPresetManager.PRESET_PEOPLE
                R.id.chip_preset_phone -> SearchPresetManager.PRESET_PHONE
                R.id.chip_preset_email -> SearchPresetManager.PRESET_EMAIL
                R.id.chip_preset_darkweb -> SearchPresetManager.PRESET_DARKWEB
                R.id.chip_preset_courts -> SearchPresetManager.PRESET_COURTS_SEC
                else -> SearchPresetManager.PRESET_FULL
            }
            SearchPresetManager.saveBuiltinPreset(requireContext(), presetId)
        }

        binding.btnSaveCustomPreset.setOnClickListener {
            val categories = mutableSetOf<String>()
            for (i in 0 until binding.chipGroupCustomCategories.childCount) {
                val chip = binding.chipGroupCustomCategories.getChildAt(i) as? Chip ?: continue
                if (chip.isChecked) categories.add(chip.tag as String)
            }
            if (categories.isEmpty()) return@setOnClickListener
            SearchPresetManager.saveCustomPreset(requireContext(), "Custom", categories)
            binding.chipGroupSearchPreset.clearCheck()
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
        val current = prefs.getString("pref_plug_bg", "bricks")
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

    private fun toggleAdvancedSection() {
        advancedExpanded = !advancedExpanded
        applyInvestigatorModeUi()
    }

    private fun applyInvestigatorModeUi() {
        val b = _binding ?: return
        val investigator = AppSettings.isInvestigatorMode(requireContext())
        b.advancedBody.visibility = if (advancedExpanded) View.VISIBLE else View.GONE
        b.ivAdvancedChevron.rotation = if (advancedExpanded) 180f else 270f
        val toolsVisibility = if (investigator) View.VISIBLE else View.GONE
        b.tvSearchToolsHeader.visibility = toolsVisibility
        b.searchToolsContainer.visibility = toolsVisibility
        if (investigator) refreshInfrastructureStatus()
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
        style(b.cardThemePatrino, selectedBase == "patrino")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
