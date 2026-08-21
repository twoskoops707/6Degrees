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
import com.twoskoops707.sixdegrees.data.AppSettings
import com.twoskoops707.sixdegrees.data.UserProfileManager
import com.twoskoops707.sixdegrees.databinding.FragmentApiSettingsBinding
import androidx.core.os.bundleOf
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
        setupAutoApplyAll()
        populateUsageCounters()
        applyInvestigatorModeUi()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) applyInvestigatorModeUi()
    }

    private fun applyInvestigatorModeUi() {
        val investigator = AppSettings.isInvestigatorMode(requireContext())
        binding.powerUserKeysSection.visibility = if (investigator) View.VISIBLE else View.GONE
        binding.settingsToolbar.title = getString(
            if (investigator) R.string.api_settings_title else R.string.api_settings_zero_config_title
        )
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
                "hashes_org", "hashesorg" -> { apiKeyManager.hashesOrgKey = v; count++ }
                "securitytrails" -> { apiKeyManager.securityTrailsKey = v; count++ }
                "censys_id" -> { apiKeyManager.censysId = v; count++ }
                "censys_secret" -> { apiKeyManager.censysSecret = v; count++ }
                "criminalip" -> { apiKeyManager.criminalIpKey = v; count++ }
                "netlas" -> { apiKeyManager.netlasKey = v; count++ }
                "abstractapi_email" -> { apiKeyManager.abstractApiEmailKey = v; count++ }
                "abstractapi_phone" -> { apiKeyManager.abstractApiPhoneKey = v; count++ }
                "leakix" -> { apiKeyManager.leakixKey = v; count++ }
                "intelx" -> { apiKeyManager.intelxKey = v; count++ }
                "dehashed" -> { apiKeyManager.dehashed = v; count++ }
                "dehashed_user" -> { apiKeyManager.dehashedUser = v; count++ }
                "wigle" -> { apiKeyManager.wigleKey = v; count++ }
                "emailrep", "emailrep.io" -> { apiKeyManager.emailrepKey = v; count++ }
                "opensanctions" -> { apiKeyManager.opensanctionsKey = v; count++ }
                "opencorporates" -> { apiKeyManager.opencorporatesKey = v; count++ }
                "urlhaus" -> { apiKeyManager.urlhausKey = v; count++ }
                "breachdirectory" -> { apiKeyManager.breachdirectoryKey = v; count++ }
                "threatfox" -> { apiKeyManager.threatfoxKey = v; count++ }
                "openrouter", "openrouter_ai" -> { apiKeyManager.openrouterKey = v; count++ }
                "openrouter_model" -> { apiKeyManager.openrouterModel = v; count++ }
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
            "builtwith" to apiKeyManager.builtWithKey,
            "virustotal" to apiKeyManager.virusTotalKey,
            "abuseipdb" to apiKeyManager.abuseIpDbKey,
            "urlscan" to apiKeyManager.urlScanKey,
            "google_cse_key" to apiKeyManager.googleCseApiKey,
            "google_cse_id" to apiKeyManager.googleCseId,
            "bing_search" to apiKeyManager.bingSearchKey,
            "veriphone" to apiKeyManager.veriphoneKey,
            "ipqs" to apiKeyManager.ipqsKey,
            "fullcontact" to apiKeyManager.fullcontactKey,
            "hashes_org" to apiKeyManager.hashesOrgKey,
            "securitytrails" to apiKeyManager.securityTrailsKey,
            "censys_id" to apiKeyManager.censysId,
            "censys_secret" to apiKeyManager.censysSecret,
            "criminalip" to apiKeyManager.criminalIpKey,
            "netlas" to apiKeyManager.netlasKey,
            "abstractapi_email" to apiKeyManager.abstractApiEmailKey,
            "abstractapi_phone" to apiKeyManager.abstractApiPhoneKey,
            "leakix" to apiKeyManager.leakixKey,
            "intelx" to apiKeyManager.intelxKey,
            "wigle" to apiKeyManager.wigleKey,
            "emailrep" to apiKeyManager.emailrepKey,
            "opensanctions" to apiKeyManager.opensanctionsKey,
            "opencorporates" to apiKeyManager.opencorporatesKey,
            "urlhaus" to apiKeyManager.urlhausKey,
            "breachdirectory" to apiKeyManager.breachdirectoryKey,
            "threatfox" to apiKeyManager.threatfoxKey,
            "openrouter" to apiKeyManager.openrouterKey,
            "openrouter_model" to apiKeyManager.openrouterModel
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

    private val allSignupApis = listOf(
        "HaveIBeenPwned" to "https://haveibeenpwned.com/API/Key",
        "EmailRep.io" to "https://emailrep.io/",
        "BreachDirectory" to "https://breachdirectory.org/register",
        "OpenSanctions" to "https://www.opensanctions.org/docs/api/",
        "OpenCorporates" to "https://opencorporates.com/api_accounts/sign_up",
        "URLhaus" to "https://auth.abuse.ch/",
        "ThreatFox" to "https://threatfox.abuse.ch/",
        "AbuseIPDB" to "https://www.abuseipdb.com/register",
        "URLScan.io" to "https://urlscan.io/user/signup",
        "IPQualityScore" to "https://www.ipqualityscore.com/create-account",
        "Veriphone" to "https://veriphone.io/signup",
        "VirusTotal" to "https://www.virustotal.com/gui/join-us",
        "Hunter.io" to "https://hunter.io/users/sign_up",
        "People Data Labs" to "https://www.peopledatalabs.com/signup",
        "Numverify" to "https://numverify.com/product",
        "Shodan" to "https://account.shodan.io/register",
        "SecurityTrails" to "https://securitytrails.com/app/account",
        "Censys" to "https://accounts.censys.io/register",
        "CriminalIP" to "https://www.criminalip.io/user/signup",
        "Netlas" to "https://app.netlas.io/registration/",
        "AbstractAPI" to "https://app.abstractapi.com/users/signup",
        "LeakIX" to "https://leakix.net/register",
        "IntelX" to "https://intelx.io/?signup"
    )

    private fun buildQueueJson(apis: List<Pair<String, String>>): String =
        "[" + apis.joinToString(",") { (name, url) ->
            "{\"name\":${jsonString(name)},\"url\":${jsonString(url)}}"
        } + "]"

    private fun jsonString(s: String) = "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun navigateToSignup(apiName: String, signupUrl: String, queue: List<Pair<String, String>> = listOf(apiName to signupUrl), position: Int = 0) {
        if (!profileManager.hasProfile()) {
            Toast.makeText(requireContext(), "Set up your profile first — tap Edit Profile", Toast.LENGTH_LONG).show()
            return
        }
        findNavController().navigate(
            R.id.action_api_settings_to_signup_web,
            bundleOf(
                "apiName" to apiName,
                "signupUrl" to signupUrl,
                "queueJson" to buildQueueJson(queue),
                "queuePosition" to position
            )
        )
    }

    private fun setupQuickApplyButtons() {
        mapOf(
            binding.btnQuickApplyHibp to ("HaveIBeenPwned" to "https://haveibeenpwned.com/API/Key"),
            binding.btnQuickApplyHunter to ("Hunter.io" to "https://hunter.io/users/sign_up"),
            binding.btnQuickApplyPdl to ("People Data Labs" to "https://www.peopledatalabs.com/signup"),
            binding.btnQuickApplyNumverify to ("Numverify" to "https://numverify.com/product"),
            binding.btnQuickApplyShodan to ("Shodan" to "https://account.shodan.io/register"),
            binding.btnQuickApplyPipl to ("Pipl" to "https://pipl.com/api/")
        ).forEach { (button, entry) ->
            button.setOnClickListener { navigateToSignup(entry.first, entry.second) }
        }
    }

    private fun setupAutoApplyAll() {
        binding.btnAutoApplyAll.setOnClickListener {
            if (!profileManager.hasProfile()) {
                Toast.makeText(requireContext(), "Set up your profile first — tap Edit Profile", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val first = allSignupApis.first()
            navigateToSignup(first.first, first.second, allSignupApis, 0)
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
        binding.googleCseApiKeyInput.setText(apiKeyManager.getRawForDisplay("google_cse_key"))
        binding.googleCseIdInput.setText(apiKeyManager.getRawForDisplay("google_cse_id"))
        binding.bingSearchApiKeyInput.setText(apiKeyManager.bingSearchKey)
        binding.veriphoneApiKeyInput.setText(apiKeyManager.veriphoneKey)
        binding.ipqsApiKeyInput.setText(apiKeyManager.ipqsKey)
        binding.fullcontactApiKeyInput.setText(apiKeyManager.fullcontactKey)
        binding.hashesOrgApiKeyInput.setText(apiKeyManager.hashesOrgKey)
        binding.virustotalApiKeyInput.setText(apiKeyManager.getRawForDisplay("virustotal"))
        binding.abuseipdbApiKeyInput.setText(apiKeyManager.getRawForDisplay("abuseipdb"))
        binding.urlscanApiKeyInput.setText(apiKeyManager.getRawForDisplay("urlscan"))
        binding.builtwithApiKeyInput.setText(apiKeyManager.builtWithKey)
        binding.securitytrailsApiKeyInput.setText(apiKeyManager.securityTrailsKey)
        binding.censysIdInput.setText(apiKeyManager.censysId)
        binding.censysSecretInput.setText(apiKeyManager.censysSecret)
        binding.criminalipApiKeyInput.setText(apiKeyManager.criminalIpKey)
        binding.netlasApiKeyInput.setText(apiKeyManager.netlasKey)
        binding.abstractapiEmailApiKeyInput.setText(apiKeyManager.abstractApiEmailKey)
        binding.abstractapiPhoneApiKeyInput.setText(apiKeyManager.abstractApiPhoneKey)
        binding.leakixApiKeyInput.setText(apiKeyManager.leakixKey)
        binding.intelxApiKeyInput.setText(apiKeyManager.intelxKey)
        binding.emailrepApiKeyInput.setText(apiKeyManager.emailrepKey)
        binding.opensanctionsApiKeyInput.setText(apiKeyManager.opensanctionsKey)
        binding.opencorporatesApiKeyInput.setText(apiKeyManager.opencorporatesKey)
        binding.urlhausApiKeyInput.setText(apiKeyManager.urlhausKey)
        binding.breachdirectoryApiKeyInput.setText(apiKeyManager.breachdirectoryKey)
        binding.threatfoxApiKeyInput.setText(apiKeyManager.threatfoxKey)
        binding.openrouterApiKeyInput.setText(apiKeyManager.openrouterKey)
        binding.openrouterModelInput.setText(apiKeyManager.openrouterModel)
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
        apiKeyManager.hashesOrgKey = binding.hashesOrgApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.virusTotalKey = binding.virustotalApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.abuseIpDbKey = binding.abuseipdbApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.urlScanKey = binding.urlscanApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.builtWithKey = binding.builtwithApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.securityTrailsKey = binding.securitytrailsApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.censysId = binding.censysIdInput.text?.toString()?.trim() ?: ""
        apiKeyManager.censysSecret = binding.censysSecretInput.text?.toString()?.trim() ?: ""
        apiKeyManager.criminalIpKey = binding.criminalipApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.netlasKey = binding.netlasApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.abstractApiEmailKey = binding.abstractapiEmailApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.abstractApiPhoneKey = binding.abstractapiPhoneApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.leakixKey = binding.leakixApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.intelxKey = binding.intelxApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.emailrepKey = binding.emailrepApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.opensanctionsKey = binding.opensanctionsApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.opencorporatesKey = binding.opencorporatesApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.urlhausKey = binding.urlhausApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.breachdirectoryKey = binding.breachdirectoryApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.threatfoxKey = binding.threatfoxApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.openrouterKey = binding.openrouterApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.openrouterModel = binding.openrouterModelInput.text?.toString()?.trim() ?: ""
        Toast.makeText(requireContext(), "API keys saved", Toast.LENGTH_SHORT).show()
    }

    override fun onStop() {
        super.onStop()
        saveApiKeys()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
