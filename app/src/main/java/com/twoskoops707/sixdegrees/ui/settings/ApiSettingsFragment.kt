package com.twoskoops707.sixdegrees.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.data.ApiKeyManager
import com.twoskoops707.sixdegrees.data.UserProfileManager
import com.twoskoops707.sixdegrees.databinding.FragmentApiSettingsBinding
import com.twoskoops707.sixdegrees.ui.profile.QuickApplyBottomSheet

class ApiSettingsFragment : Fragment() {

    private var _binding: FragmentApiSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var profileManager: UserProfileManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentApiSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        apiKeyManager = ApiKeyManager(requireContext())
        profileManager = UserProfileManager(requireContext())

        binding.settingsToolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_api_settings_to_profile)
        }

        loadSavedApiKeys()
        binding.saveApiKeysButton.setOnClickListener { saveApiKeys() }
        setupQuickApplyButtons()
        populateUsageCounters()
    }

    private fun setupQuickApplyButtons() {
        data class ApiEntry(val apiName: String, val signupUrl: String)

        val entries = mapOf(
            binding.btnQuickApplyHibp to ApiEntry("HaveIBeenPwned", "https://haveibeenpwned.com/API/Key"),
            binding.btnQuickApplyHunter to ApiEntry("Hunter.io", "https://hunter.io/users/sign_up"),
            binding.btnQuickApplyPdl to ApiEntry("People Data Labs", "https://www.peopledatalabs.com/signup"),
            binding.btnQuickApplyNumverify to ApiEntry("Numverify", "https://numverify.com/product"),
            binding.btnQuickApplyShodan to ApiEntry("Shodan", "https://account.shodan.io/register"),
            binding.btnQuickApplyPipl to ApiEntry("Pipl", "https://pipl.com/api/")
        )

        entries.forEach { (button, entry) ->
            button.setOnClickListener {
                if (!profileManager.hasProfile()) {
                    Toast.makeText(requireContext(), "Set up your profile first — tap Edit Profile", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                QuickApplyBottomSheet.show(this, entry.apiName, entry.signupUrl, profileManager.load())
            }
        }
    }

    private fun populateUsageCounters() {
        val container = binding.usageContainer
        container.removeAllViews()
        val ctx = requireContext()

        apiKeyManager.getUsageSummaries().forEach { summary ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = (12 * resources.displayMetrics.density).toInt() }
            }

            val labelRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val nameView = TextView(ctx).apply {
                text = summary.name
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val countView = TextView(ctx).apply {
                text = summary.label
                textSize = 12f
                val color = when {
                    summary.isUnlimited -> R.color.success
                    summary.fractionUsed > 0.8f -> R.color.error
                    summary.fractionUsed > 0.5f -> R.color.warning
                    else -> R.color.text_secondary
                }
                setTextColor(ContextCompat.getColor(ctx, color))
            }

            labelRow.addView(nameView)
            labelRow.addView(countView)
            row.addView(labelRow)

            if (!summary.isUnlimited) {
                val bar = LinearProgressIndicator(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.topMargin = (4 * resources.displayMetrics.density).toInt() }
                    max = 100
                    progress = (summary.fractionUsed * 100).toInt().coerceIn(0, 100)
                    val trackColor = when {
                        summary.fractionUsed > 0.8f -> ContextCompat.getColor(ctx, R.color.error)
                        summary.fractionUsed > 0.5f -> ContextCompat.getColor(ctx, R.color.warning)
                        else -> ContextCompat.getColor(ctx, R.color.accent_blue)
                    }
                    setIndicatorColor(trackColor)
                    trackColor.let { setTrackColor(ContextCompat.getColor(ctx, R.color.border)) }
                }
                row.addView(bar)
            }

            container.addView(row)
        }
    }

    private fun loadSavedApiKeys() {
        binding.hibpApiKeyInput.setText(apiKeyManager.hibpKey)
        binding.hunterApiKeyInput.setText(apiKeyManager.hunterKey)
        binding.pdlApiKeyInput.setText(apiKeyManager.pdlKey)
        binding.numverifyApiKeyInput.setText(apiKeyManager.numverifyKey)
        binding.shodanApiKeyInput.setText(apiKeyManager.shodanKey)
        binding.piplApiKeyInput.setText(apiKeyManager.piplKey)
    }

    private fun saveApiKeys() {
        apiKeyManager.hibpKey = binding.hibpApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.hunterKey = binding.hunterApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.pdlKey = binding.pdlApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.numverifyKey = binding.numverifyApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.shodanKey = binding.shodanApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.piplKey = binding.piplApiKeyInput.text?.toString()?.trim() ?: ""
        Toast.makeText(requireContext(), "API keys saved", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
