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

    private data class ToolDef(
        val displayName: String,
        val checkPath: String,
        val installCmd: String,
        val broken: Boolean = false,
        val brokenNote: String = ""
    )

    private val termuxTools = listOf(
        ToolDef("nmap",
            "/data/data/com.termux/files/usr/bin/nmap",
            "pkg install -y nmap"),
        ToolDef("tor",
            "/data/data/com.termux/files/usr/bin/tor",
            "pkg install -y tor"),
        ToolDef("torsocks",
            "/data/data/com.termux/files/usr/bin/torsocks",
            "pkg install -y torsocks"),
        ToolDef("whois",
            "/data/data/com.termux/files/usr/bin/whois",
            "pkg install -y whois"),
        ToolDef("exiftool",
            "/data/data/com.termux/files/usr/bin/exiftool",
            "pkg install -y exiftool"),
        ToolDef("dig (dnsutils)",
            "/data/data/com.termux/files/usr/bin/dig",
            "pkg install -y dnsutils"),
        ToolDef("tshark",
            "/data/data/com.termux/files/usr/bin/tshark",
            "pkg install -y tshark"),
        ToolDef("sherlock",
            "/data/data/com.termux/files/usr/bin/sherlock",
            "pip install sherlock-project"),
        ToolDef("maigret",
            "/data/data/com.termux/files/usr/bin/maigret",
            "pip install maigret"),
        ToolDef("holehe",
            "/data/data/com.termux/files/usr/bin/holehe",
            "pip install holehe"),
        ToolDef("theHarvester",
            "/data/data/com.termux/files/home/theHarvester",
            "git clone https://github.com/laramies/theHarvester ~/theHarvester && pip install -r ~/theHarvester/requirements/base.txt"),
        ToolDef("recon-ng",
            "/data/data/com.termux/files/home/recon-ng",
            "git clone https://github.com/lanmaster53/recon-ng ~/recon-ng && pip install -r ~/recon-ng/REQUIREMENTS"),
        ToolDef("spiderfoot",
            "/data/data/com.termux/files/home/spiderfoot",
            "git clone https://github.com/smicallef/spiderfoot ~/spiderfoot && pip install -r ~/spiderfoot/requirements.txt"),
        ToolDef("sqlmap",
            "/data/data/com.termux/files/home/sqlmap",
            "git clone https://github.com/sqlmapproject/sqlmap ~/sqlmap"),
        ToolDef("nikto",
            "/data/data/com.termux/files/home/nikto",
            "pkg install -y perl && git clone https://github.com/sullo/nikto ~/nikto"),
        ToolDef("hashcat",
            "",
            "",
            broken = true,
            brokenNote = "No GPU on Android — CPU mode only via manual build"),
        ToolDef("aircrack-ng",
            "",
            "",
            broken = true,
            brokenNote = "Needs monitor mode — disabled on non-rooted Android"),
        ToolDef("maltego",
            "",
            "",
            broken = true,
            brokenNote = "GUI desktop app — use on PC only")
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
        termuxTools.forEachIndexed { index, tool ->
            if (tool.broken) {
                val (row, _, statusLabel) = buildStatusRowDetailed(
                    tool.displayName,
                    tool.brokenNote,
                    pending = false,
                    isOk = false,
                    isBroken = true
                )
                container.addView(row)
            } else {
                val installed = tool.checkPath.isNotBlank() && File(tool.checkPath).exists()
                val (row, _, statusLabel) = buildStatusRowDetailed(
                    tool.displayName,
                    if (installed) "READY" else "tap to copy install command",
                    pending = false,
                    isOk = installed
                )
                if (!installed) {
                    statusLabel.text = tool.installCmd.take(60) + if (tool.installCmd.length > 60) "…" else ""
                    row.setOnClickListener { launchTermuxInstall(tool.installCmd) }
                }
                container.addView(row)
            }
            if (index < termuxTools.lastIndex) container.addView(buildDivider())
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
            Toast.makeText(requireContext(), "Command copied to clipboard — paste in Termux", Toast.LENGTH_LONG).show()
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
        isOk: Boolean = false,
        isBroken: Boolean = false
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
                    isBroken -> ContextCompat.getColor(ctx, R.color.warning)
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
                    isBroken -> ContextCompat.getColor(ctx, R.color.warning)
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
