package com.twoskoops707.sixdegrees.ui.wizard

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.data.ApiKeyManager
import com.twoskoops707.sixdegrees.databinding.FragmentWizardBinding
import java.io.File

class WizardFragment : Fragment() {

    private var _binding: FragmentWizardBinding? = null
    private val binding get() = _binding!!

    private val termuxTools = listOf(
        "nmap" to "nmap",
        "tor" to "tor",
        "torsocks" to "torsocks",
        "sherlock" to "sherlock",
        "theHarvester" to "theharvester",
        "hashcat" to "hashcat",
        "wireshark" to "tshark",
        "recon-ng" to "recon-ng",
        "aircrack-ng" to "aircrack-ng",
        "spiderfoot" to "sfcli.py"
    )

    private val apiDefs = listOf(
        Triple("Pipl", "pipl", "pipl"),
        Triple("People Data Labs", "pdl", "pdl"),
        Triple("Hunter.io", "hunter", "hunter"),
        Triple("HaveIBeenPwned", "hibp", "hibp"),
        Triple("Clearbit", "clearbit", "clearbit"),
        Triple("BuiltWith", "builtwith", "builtwith"),
        Triple("NumVerify", "numverify", "numverify"),
        Triple("Shodan", "shodan", "shodan"),
        Triple("VirusTotal", "virustotal", "virustotal"),
        Triple("AbuseIPDB", "abuseipdb", "abuseipdb")
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWizardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val apiKeyManager = ApiKeyManager(requireContext())
        populateApiKeys(apiKeyManager)
        populateTermuxTools()

        binding.btnDone.setOnClickListener {
            requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("setup_complete", true).apply()
            findNavController().navigate(R.id.action_wizard_to_search)
        }
    }

    private fun populateApiKeys(keyManager: ApiKeyManager) {
        val inflater = LayoutInflater.from(requireContext())
        val container = binding.apiKeysContainer

        for ((label, prefKey, _) in apiDefs) {
            val prefs = requireContext().getSharedPreferences("api_keys", Context.MODE_PRIVATE)
            val isSet = (prefs.getString(prefKey, "") ?: "").isNotBlank()
            container.addView(buildStatusRow(label, isSet, "Tap Settings → API Keys to configure"))
            if (apiDefs.last().first != label) {
                container.addView(buildDivider())
            }
        }

        container.setOnClickListener {
            findNavController().navigate(R.id.action_wizard_to_api_settings)
        }
        binding.apiKeysContainer.parent.let {
            if (it is View) it.setOnClickListener {
                findNavController().navigate(R.id.action_wizard_to_api_settings)
            }
        }
    }

    private fun populateTermuxTools() {
        val container = binding.termuxToolsContainer
        for ((displayName, binName) in termuxTools) {
            val termuxBin = File("/data/data/com.termux/files/usr/bin/$binName")
            val altBin = File("/data/data/com.termux/files/usr/bin/python3").let { py ->
                if (!termuxBin.exists() && binName.endsWith(".py")) {
                    File("/data/data/com.termux/files/home/.local/bin/$binName").exists() ||
                    File("/data/data/com.termux/files/usr/local/bin/$binName").exists()
                } else false
            }
            val isInstalled = termuxBin.exists() || altBin
            val hint = if (isInstalled) "Ready" else "pkg install $binName"
            container.addView(buildStatusRow(displayName, isInstalled, hint))
            if (termuxTools.last().first != displayName) {
                container.addView(buildDivider())
            }
        }
    }

    private fun buildStatusRow(label: String, isOk: Boolean, hint: String): View {
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }

        val statusDot = TextView(ctx).apply {
            text = if (isOk) "●" else "●"
            textSize = 10f
            setTextColor(
                if (isOk) ContextCompat.getColor(ctx, R.color.success)
                else ContextCompat.getColor(ctx, R.color.error)
            )
            layoutParams = LinearLayout.LayoutParams(dp(20f), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val nameText = TextView(ctx).apply {
            text = label
            textSize = 14f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val statusLabel = TextView(ctx).apply {
            text = if (isOk) "READY" else "MISSING"
            textSize = 10f
            letterSpacing = 0.08f
            setTextColor(
                if (isOk) ContextCompat.getColor(ctx, R.color.success)
                else ContextCompat.getColor(ctx, R.color.text_secondary)
            )
        }

        row.addView(statusDot)
        row.addView(nameText)
        row.addView(statusLabel)
        return row
    }

    private fun buildDivider(): View {
        val ctx = requireContext()
        return View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1
            ).also {
                it.marginStart = (16 * ctx.resources.displayMetrics.density).toInt()
                it.marginEnd = (16 * ctx.resources.displayMetrics.density).toInt()
            }
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.border))
            alpha = 0.5f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
