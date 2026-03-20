package com.twoskoops707.sixdegrees.ui.wizard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
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
            "pkg install -y perl && git clone https://github.com/sullo/nikto ~/nikto")
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
        populateTips()

        binding.btnDone.setOnClickListener {
            requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("setup_complete", true).apply()
            findNavController().navigate(R.id.action_wizard_to_search)
        }
    }

    private fun populateTermuxTools() {
        val container = binding.termuxToolsContainer
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()

        termuxTools.forEachIndexed { index, tool ->
            val installed = tool.checkPath.isNotBlank() && File(tool.checkPath).exists()
            if (installed) {
                val (row, _, _) = buildStatusRowDetailed(tool.displayName, "READY", pending = false, isOk = true)
                container.addView(row)
            } else {
                val wrapper = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setOnClickListener { launchTermuxInstall(tool.installCmd) }
                }
                val (row1, _, _) = buildStatusRowDetailed(tool.displayName, "NOT INSTALLED", pending = false, isOk = false)
                val cmdView = TextView(ctx).apply {
                    text = tool.installCmd
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    isSingleLine = false
                    maxLines = 4
                    setPadding(dp(36f), dp(2f), dp(16f), dp(10f))
                    setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                }
                wrapper.addView(row1)
                wrapper.addView(cmdView)
                container.addView(wrapper)
            }
            if (index < termuxTools.lastIndex) container.addView(buildDivider())
        }

        container.addView(buildDivider())
        val noteView = TextView(ctx).apply {
            text = "Not available on this device: theHarvester (needs Playwright browser), hashcat (no GPU), aircrack-ng (needs monitor mode), maltego (desktop GUI). Use on PC."
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        }
        container.addView(noteView)
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

    private fun populateTips() {
        val tips = listOf(
            "\uD83D\uDCA1" to "Enter full name + city/state for best results and fewer blank reports",
            "\uD83D\uDCF1" to "Enable Termux external app permission before tapping install commands (see step above)",
            "\uD83D\uDD0D" to "Sherlock and Maigret run in Termux background — check Termux app for username results",
            "\uD83D\uDD11" to "Add a Google Custom Search Engine API key for richer web search results",
            "\uD83D\uDEE1\uFE0F" to "Shodan and HIBP keys unlock breach and IP data sections in reports",
            "\uD83D\uDCC4" to "If a report seems empty: try adding employer, age, or school to the search query",
            "\uD83D\uDC65" to "Round 1 shows candidate cards — pick the closest match for a deep-dive report",
            "\uD83D\uDCDE" to "Tap phone numbers and email addresses in reports to dial or open mail"
        )
        val container = binding.tipsContainer
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()

        tips.forEachIndexed { index, (icon, text) ->
            val row = TextView(ctx).apply {
                this.text = "$icon  $text"
                textSize = 13f
                isSingleLine = false
                setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            }
            container.addView(row)
            if (index < tips.lastIndex) container.addView(buildDivider())
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
            isSingleLine = false
            maxLines = 3
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
