package com.twoskoops707.sixdegrees.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.data.ApiKeyManager
import com.twoskoops707.sixdegrees.data.UserProfileManager
import com.twoskoops707.sixdegrees.databinding.FragmentApiSettingsBinding
import com.twoskoops707.sixdegrees.ui.profile.QuickApplyBottomSheet
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class ApiSettingsFragment : Fragment() {

    private var _binding: FragmentApiSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var profileManager: UserProfileManager

    private val csvImportLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val stream = requireContext().contentResolver.openInputStream(uri) ?: return@registerForActivityResult
            val reader = BufferedReader(InputStreamReader(stream))
            val keyMap = mutableMapOf<String, String>()
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEachLine
                val parts = trimmed.split(",", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim().lowercase()
                    val value = parts[1].trim().removeSurrounding("\"")
                    if (value.isNotBlank() && key != "api_name") keyMap[key] = value
                }
            }
            reader.close()
            applyImportedKeys(keyMap)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

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

        binding.btnImportCsv.setOnClickListener {
            csvImportLauncher.launch("*/*")
        }

        binding.btnExportCsv.setOnClickListener {
            exportApiKeysAsCsv()
        }

        loadSavedApiKeys()
        binding.saveApiKeysButton.setOnClickListener { saveApiKeys() }
        setupQuickApplyButtons()
        populateUsageCounters()
    }

    private fun applyImportedKeys(keyMap: Map<String, String>) {
        var count = 0
        keyMap.forEach { (k, v) ->
            when (k) {
                "hibp", "haveibeenpwned" -> { apiKeyManager.hibpKey = v; count++ }
                "hunter", "hunter.io" -> { apiKeyManager.hunterKey = v; count++ }
                "pdl", "people_data_labs" -> { apiKeyManager.pdlKey = v; count++ }
                "numverify" -> { apiKeyManager.numverifyKey = v; count++ }
                "shodan" -> { apiKeyManager.shodanKey = v; count++ }
                "pipl" -> { apiKeyManager.piplKey = v; count++ }
                "clearbit" -> { apiKeyManager.clearbitKey = v; count++ }
                "builtwith" -> { apiKeyManager.builtWithKey = v; count++ }
                "virustotal" -> { apiKeyManager.virusTotalKey = v; count++ }
                "abuseipdb" -> { apiKeyManager.abuseIpDbKey = v; count++ }
                "urlscan" -> { apiKeyManager.urlScanKey = v; count++ }
                "google_cse_key", "google_cse_api_key" -> { apiKeyManager.googleCseApiKey = v; count++ }
                "google_cse_id" -> { apiKeyManager.googleCseId = v; count++ }
                "bing_search", "bing" -> { apiKeyManager.bingSearchKey = v; count++ }
                "veriphone" -> { apiKeyManager.veriphoneKey = v; count++ }
                "ipqs", "ipqualityscore" -> { apiKeyManager.ipqsKey = v; count++ }
                "fullcontact" -> { apiKeyManager.fullcontactKey = v; count++ }
            }
        }
        if (count > 0) {
            loadSavedApiKeys()
            Toast.makeText(requireContext(), "$count API key${if (count != 1) "s" else ""} imported", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "No matching keys found in file", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportApiKeysAsCsv() {
        val sb = StringBuilder()
        sb.appendLine("api_name,api_key")
        mapOf(
            "hibp" to apiKeyManager.hibpKey,
            "hunter" to apiKeyManager.hunterKey,
            "pdl" to apiKeyManager.pdlKey,
            "numverify" to apiKeyManager.numverifyKey,
            "shodan" to apiKeyManager.shodanKey,
            "pipl" to apiKeyManager.piplKey,
            "clearbit" to apiKeyManager.clearbitKey,
            "builtwith" to apiKeyManager.builtWithKey,
            "virustotal" to apiKeyManager.virusTotalKey,
            "abuseipdb" to apiKeyManager.abuseIpDbKey,
            "urlscan" to apiKeyManager.urlScanKey,
            "google_cse_key" to apiKeyManager.googleCseApiKey,
            "google_cse_id" to apiKeyManager.googleCseId,
            "bing_search" to apiKeyManager.bingSearchKey,
            "veriphone" to apiKeyManager.veriphoneKey,
            "ipqs" to apiKeyManager.ipqsKey,
            "fullcontact" to apiKeyManager.fullcontactKey
        ).forEach { (name, key) ->
            sb.appendLine("$name,$key")
        }

        try {
            val file = File(requireContext().cacheDir, "sixdegrees_api_keys.csv")
            file.writeText(sb.toString())
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "6Degrees API Keys")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Export API Keys"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
        binding.googleCseApiKeyInput.setText(apiKeyManager.googleCseApiKey)
        binding.googleCseIdInput.setText(apiKeyManager.googleCseId)
        binding.bingSearchApiKeyInput.setText(apiKeyManager.bingSearchKey)
        binding.veriphoneApiKeyInput.setText(apiKeyManager.veriphoneKey)
        binding.ipqsApiKeyInput.setText(apiKeyManager.ipqsKey)
        binding.fullcontactApiKeyInput.setText(apiKeyManager.fullcontactKey)
    }

    private fun saveApiKeys() {
        apiKeyManager.hibpKey = binding.hibpApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.hunterKey = binding.hunterApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.pdlKey = binding.pdlApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.numverifyKey = binding.numverifyApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.shodanKey = binding.shodanApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.piplKey = binding.piplApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.googleCseApiKey = binding.googleCseApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.googleCseId = binding.googleCseIdInput.text?.toString()?.trim() ?: ""
        apiKeyManager.bingSearchKey = binding.bingSearchApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.veriphoneKey = binding.veriphoneApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.ipqsKey = binding.ipqsApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.fullcontactKey = binding.fullcontactApiKeyInput.text?.toString()?.trim() ?: ""
        Toast.makeText(requireContext(), "API keys saved", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
