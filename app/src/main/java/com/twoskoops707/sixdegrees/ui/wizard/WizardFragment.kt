package com.twoskoops707.sixdegrees.ui.wizard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import android.os.Bundle
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

    private val toolInstallCmds = mapOf(
        "nmap" to "pkg install -y nmap",
        "tor" to "pkg install -y tor",
        "torsocks" to "pkg install -y torsocks",
        "sherlock" to "pkg install -y python && pip install sherlock-project",
        "theharvester" to "pkg install -y python && pip install theHarvester",
        "hashcat" to "pkg install -y hashcat",
        "tshark" to "pkg install -y tshark",
        "recon-ng" to "pkg install -y python && pip install recon-ng",
        "aircrack-ng" to "pkg install -y aircrack-ng",
        "sfcli.py" to "pkg install -y python && pip install spiderfoot"
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

    private fun populateTermuxTools() {
        val container = binding.termuxToolsContainer
        for ((displayName, binName) in termuxTools) {
            val binPath = "/data/data/com.termux/files/usr/bin/$binName"
            val installed = File(binPath).exists()
            val installCmd = toolInstallCmds[binName] ?: "pkg install -y $binName"
            val (row, _, statusLabel) = buildStatusRowDetailed(
                displayName,
                if (installed) "READY" else "Tap to install",
                pending = false,
                isOk = installed
            )
            if (!installed) {
                statusLabel.text = installCmd
                row.setOnClickListener { launchTermuxInstall(installCmd) }
            }
            container.addView(row)
            if (termuxTools.last().first != displayName) container.addView(buildDivider())
        }
    }

    private fun launchTermuxInstall(cmd: String) {
        val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("install_cmd", cmd))
        try {
            val intent = Intent().apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", cmd))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            }
            requireContext().startForegroundService(intent)
        } catch (_: Exception) {
            try {
                requireContext().startActivity(Intent().apply {
                    setClassName("com.termux", "com.termux.app.TermuxActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {}
            Toast.makeText(requireContext(), "Command copied! Paste in Termux to install.", Toast.LENGTH_LONG).show()
        }
    }

    private fun populateApiKeys(keyManager: ApiKeyManager) {
        val container = binding.apiKeysContainer
        for ((label, prefKey, _) in apiDefs) {
            val prefs = requireContext().getSharedPreferences("api_keys", Context.MODE_PRIVATE)
            val isSet = (prefs.getString(prefKey, "") ?: "").isNotBlank()
            container.addView(buildStatusRow(label, isSet, "Tap Settings → API Keys to configure"))
            if (apiDefs.last().first != label) container.addView(buildDivider())
        }
        container.setOnClickListener {
            findNavController().navigate(R.id.action_wizard_to_api_settings)
        }
    }

    private fun buildStatusRow(label: String, isOk: Boolean, hint: String): View {
        val (row, _, _) = buildStatusRowDetailed(label, if (isOk) "READY" else hint, pending = false, isOk = isOk)
        return row
    }

    private data class RowViews(val row: View, val dot: TextView, val statusLabel: TextView)

    private fun buildStatusRowDetailed(
        label: String,
        statusText: String,
        pending: Boolean = false,
        isOk: Boolean = false
    ): RowViews {
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }

        val dot = TextView(ctx).apply {
            text = "●"
            textSize = 10f
            setTextColor(
                when {
                    pending -> ContextCompat.getColor(ctx, R.color.text_secondary)
                    isOk -> ContextCompat.getColor(ctx, R.color.success)
                    else -> ContextCompat.getColor(ctx, R.color.error)
                }
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
            text = statusText
            textSize = 10f
            letterSpacing = 0.08f
            setTextColor(
                when {
                    pending -> ContextCompat.getColor(ctx, R.color.text_secondary)
                    isOk -> ContextCompat.getColor(ctx, R.color.success)
                    else -> ContextCompat.getColor(ctx, R.color.text_secondary)
                }
            )
        }

        row.addView(dot)
        row.addView(nameText)
        row.addView(statusLabel)
        return RowViews(row, dot, statusLabel)
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
