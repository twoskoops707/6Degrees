package com.twoskoops707.sixdegrees.ui.wizard

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    private val toolRows = mutableMapOf<String, TextView>()
    private val toolDots = mutableMapOf<String, TextView>()
    private val handler = Handler(Looper.getMainLooper())
    private val TOOLS_FILE = "sixdegrees_tools.txt"

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
        requestTermuxToolCheck()

        binding.btnDone.setOnClickListener {
            requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("setup_complete", true).apply()
            findNavController().navigate(R.id.action_wizard_to_search)
        }
    }

    private fun getToolsOutputFile(): File {
        return File(requireContext().getExternalFilesDir(null), TOOLS_FILE)
    }

    private fun requestTermuxToolCheck() {
        val outputPath = getToolsOutputFile().also { it.parentFile?.mkdirs() }.absolutePath

        val binNames = termuxTools.map { it.second }
        val checkCmds = binNames.joinToString("; ") { bin ->
            "which $bin >/dev/null 2>&1 && echo '$bin:found' || echo '$bin:missing'"
        }
        val fullCmd = "$checkCmds > '$outputPath' 2>&1"

        val intent = Intent().apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", fullCmd))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }

        try {
            requireContext().startForegroundService(intent)
        } catch (_: Exception) {
            try { requireContext().startService(intent) } catch (_: Exception) {}
        }

        handler.postDelayed({ readToolResults() }, 3500)
    }

    private fun readToolResults() {
        if (_binding == null) return
        val file = getToolsOutputFile()
        if (!file.exists() || file.length() == 0L) {
            showTermuxConnectError()
            return
        }
        val results = mutableMapOf<String, Boolean>()
        file.readLines().forEach { line ->
            val parts = line.trim().split(":")
            if (parts.size == 2) {
                results[parts[0].trim()] = parts[1].trim() == "found"
            }
        }
        termuxTools.forEach { (displayName, binName) ->
            val found = results[binName]
            if (found != null) {
                updateToolRow(displayName, binName, found)
            }
        }
    }

    private fun updateToolRow(displayName: String, binName: String, installed: Boolean) {
        val dot = toolDots[displayName] ?: return
        val label = toolRows[displayName] ?: return
        val ctx = requireContext()
        dot.setTextColor(
            if (installed) ContextCompat.getColor(ctx, R.color.success)
            else ContextCompat.getColor(ctx, R.color.error)
        )
        label.text = if (installed) "READY" else "pkg install $binName"
        label.setTextColor(
            if (installed) ContextCompat.getColor(ctx, R.color.success)
            else ContextCompat.getColor(ctx, R.color.text_secondary)
        )
    }

    private fun showTermuxConnectError() {
        termuxTools.forEach { (displayName, _) ->
            val label = toolRows[displayName] ?: return@forEach
            label.text = "Enable: Termux → Settings → Allow External Apps"
            label.setTextColor(ContextCompat.getColor(requireContext(), R.color.score_amber))
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

    private fun populateTermuxTools() {
        val container = binding.termuxToolsContainer
        for ((displayName, binName) in termuxTools) {
            val (row, dot, statusLabel) = buildStatusRowDetailed(displayName, "Checking…", pending = true)
            toolDots[displayName] = dot
            toolRows[displayName] = statusLabel
            container.addView(row)
            if (termuxTools.last().first != displayName) container.addView(buildDivider())
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
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }
}
