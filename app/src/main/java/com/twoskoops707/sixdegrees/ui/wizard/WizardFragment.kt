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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import android.os.Bundle
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.data.ApiKeyManager
import com.twoskoops707.sixdegrees.data.AppSettings
import com.twoskoops707.sixdegrees.data.repository.TermuxToolRunner
import com.twoskoops707.sixdegrees.databinding.FragmentWizardBinding
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        val investigator = AppSettings.isInvestigatorMode(requireContext())
        if (investigator) {
            populateApiKeys(apiKeyManager)
            binding.wizardApiKeysHeader.visibility = View.VISIBLE
            binding.wizardApiKeysCard.visibility = View.VISIBLE
        } else {
            binding.wizardApiKeysHeader.visibility = View.GONE
            binding.wizardApiKeysCard.visibility = View.GONE
        }
        populateTermuxTools()
        populateTips()

        populateSetupSteps()

        binding.btnDone.setOnClickListener {
            requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("setup_complete", true).apply()
            findNavController().navigate(R.id.action_wizard_to_search)
        }

        binding.cardWebHub.setOnClickListener {
            findNavController().navigate(R.id.action_wizard_to_osint_resources)
        }
    }

    private fun populateSetupSteps() {
        val container = binding.termuxSetupContainer
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()

        data class StepDef(val number: String, val label: String, val actionLabel: String, val action: () -> Unit)

        val steps = listOf(
            StepDef(
                "1", getString(R.string.wizard_step1_label), getString(R.string.wizard_step1_action)
            ) {
                val cmd = "echo 'allow-external-apps = true' >> ~/.termux/termux.properties"
                val clipboard = ctx.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("setup", cmd))
                try {
                    ctx.startActivity(Intent().apply {
                        setClassName("com.termux", "com.termux.app.TermuxActivity")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (_: Exception) { }
                Toast.makeText(ctx, getString(R.string.wizard_step1_toast), Toast.LENGTH_LONG).show()
            },
            StepDef(
                "2", getString(R.string.wizard_step2_label), getString(R.string.wizard_step2_action)
            ) {
                try {
                    ctx.startActivity(Intent().apply {
                        setClassName("com.termux", "com.termux.app.TermuxActivity")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (_: Exception) {
                    Toast.makeText(ctx, getString(R.string.wizard_step2_error), Toast.LENGTH_SHORT).show()
                }
            },
            StepDef(
                "3", getString(R.string.wizard_step3_label), getString(R.string.wizard_step3_action)
            ) {
                try {
                    val runner = TermuxToolRunner(ctx)
                    val probe = runner.buildRunCommandIntent("echo termux_ready")
                    ctx.startForegroundService(probe)
                    runner.markRunCommandSuccess()
                    Toast.makeText(ctx, getString(R.string.wizard_step3_toast), Toast.LENGTH_LONG).show()
                } catch (_: SecurityException) {
                    TermuxToolRunner(ctx).markRunCommandDenied()
                    Toast.makeText(ctx, getString(R.string.wizard_step3_error), Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(ctx, getString(R.string.wizard_step3_error), Toast.LENGTH_SHORT).show()
                }
            }
        )

        steps.forEachIndexed { index, step ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
                isClickable = true
                isFocusable = true
                setOnClickListener { step.action() }
                background = android.util.TypedValue().also { v ->
                    ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, v, true)
                }.resourceId.let { ContextCompat.getDrawable(ctx, it) }
            }

            val numBadge = TextView(ctx).apply {
                text = step.number
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(ctx, R.color.background_primary))
                gravity = android.view.Gravity.CENTER
                val badgeSize = dp(22f)
                layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize).also { it.marginEnd = dp(12f) }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                }
            }

            val labelTv = TextView(ctx).apply {
                text = step.label
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val actionTv = TextView(ctx).apply {
                text = step.actionLabel
                textSize = 10f
                letterSpacing = 0.06f
                typeface = Typeface.DEFAULT_BOLD
                val tv = android.util.TypedValue()
                ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
                setTextColor(tv.data)
            }

            row.addView(numBadge)
            row.addView(labelTv)
            row.addView(actionTv)
            container.addView(row)
            if (index < steps.lastIndex) container.addView(buildDivider())
        }
    }

    private fun populateTermuxTools() {
        val container = binding.termuxToolsContainer
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()

        val statusFile = File(TermuxToolRunner.SHARED_OUTPUT_DIR, ".6d_wizard_tools.txt")
        val statusTtl = 5 * 60 * 1000L

        data class ToolRow(val statusTv: TextView, val wrapper: LinearLayout, val cmdView: TextView)
        val rowMap = mutableMapOf<String, ToolRow>()

        termuxTools.forEachIndexed { index, tool ->
            val key = tool.checkPath.substringAfterLast("/")
            val wrapper = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setOnClickListener { launchTermuxInstall(tool.installCmd) }
            }
            val (row, _, statusTv) = buildStatusRowDetailed(tool.displayName, getString(R.string.tool_status_scanning), pending = true, isOk = false)
            val cmdView = TextView(ctx).apply {
                text = tool.installCmd
                textSize = 11f
                typeface = Typeface.MONOSPACE
                isSingleLine = false
                maxLines = 4
                setPadding(dp(36f), dp(2f), dp(16f), dp(10f))
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                visibility = View.GONE
            }
            wrapper.addView(row)
            wrapper.addView(cmdView)
            container.addView(wrapper)
            if (index < termuxTools.lastIndex) container.addView(buildDivider())
            rowMap[key] = ToolRow(statusTv, wrapper, cmdView)
        }

        container.addView(buildDivider())
        val noteView = TextView(ctx).apply {
            text = getString(R.string.wizard_tools_unavailable_note)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        }
        container.addView(noteView)

        fun applyResults(content: String) {
            val results = content.lines()
                .filter { it.contains(":ok") || it.contains(":missing") }
                .associate { it.substringBefore(":") to it.contains(":ok") }
            rowMap.forEach { (key, toolRow) ->
                val isOk = results[key]
                if (isOk == null) return@forEach
                val label = if (isOk) getString(R.string.tool_status_ready) else getString(R.string.tool_status_not_installed)
                toolRow.statusTv.text = label
                toolRow.statusTv.setTextColor(ContextCompat.getColor(ctx,
                    if (isOk) R.color.score_green else R.color.score_red))
                toolRow.cmdView.visibility = if (isOk) View.GONE else View.VISIBLE
            }
        }

        val cached = if (statusFile.exists() && System.currentTimeMillis() - statusFile.lastModified() < statusTtl) {
            try { statusFile.readText() } catch (_: Exception) { null }
        } else null

        if (cached != null && cached.contains("__DONE__")) {
            applyResults(cached)
            return
        }

        val checks = termuxTools.joinToString(" ; ") { tool ->
            val key = tool.checkPath.substringAfterLast("/")
            val bin = key
            val pathCheck = "[ -e '${tool.checkPath}' ]" +
                " || command -v $bin >/dev/null 2>&1" +
                " || [ -x \"\$PREFIX/bin/$bin\" ]" +
                " || [ -x \"\$HOME/.local/bin/$bin\" ]"
            "($pathCheck) && echo $key:ok || echo $key:missing"
        }
        val cmd = "mkdir -p ${TermuxToolRunner.SHARED_OUTPUT_DIR} && { $checks ; } > ${statusFile.absolutePath} 2>&1 ; echo __DONE__ >> ${statusFile.absolutePath}"
        try {
            val intent = Intent().apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", cmd))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            ctx.startForegroundService(intent)
        } catch (_: Exception) {
            rowMap.forEach { (_, toolRow) ->
                toolRow.statusTv.text = getString(R.string.tool_status_unknown)
                toolRow.statusTv.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            }
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var waited = 0
            var done = false
            while (waited < 20000) {
                delay(1500); waited += 1500
                if (statusFile.exists()) {
                    val content = try { statusFile.readText() } catch (_: Exception) { continue }
                    if (content.contains("__DONE__")) {
                        withContext(Dispatchers.Main) { if (_binding != null) applyResults(content) }
                        done = true
                        break
                    }
                }
            }
            if (!done) {
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    rowMap.forEach { (_, toolRow) ->
                        if (toolRow.statusTv.text == getString(R.string.tool_status_scanning)) {
                            toolRow.statusTv.text = getString(R.string.tool_status_unknown)
                            toolRow.statusTv.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                        }
                    }
                }
            }
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
            Toast.makeText(requireContext(), getString(R.string.tool_install_toast), Toast.LENGTH_LONG).show()
        }
    }

    private fun populateApiKeys(keyManager: ApiKeyManager) {
        val container = binding.apiKeysContainer
        for ((label, prefKey, _) in apiDefs) {
            val prefs = requireContext().getSharedPreferences("api_keys", Context.MODE_PRIVATE)
            val isSet = (prefs.getString(prefKey, "") ?: "").isNotBlank()
            container.addView(buildStatusRow(label, isSet, getString(R.string.api_status_free)))
            if (apiDefs.last().first != label) container.addView(buildDivider())
        }
        container.setOnClickListener {
            findNavController().navigate(R.id.action_wizard_to_api_settings)
        }
    }

    private fun populateTips() {
        val tips = listOf(
            "\uD83D\uDCCD" to getString(R.string.tip_1),
            "\uD83D\uDCF1" to getString(R.string.tip_2),
            "\uD83D\uDD0D" to getString(R.string.tip_3),
            "\uD83D\uDEE1\uFE0F" to getString(R.string.tip_4),
            "\uD83D\uDC65" to getString(R.string.tip_5),
            "\uD83D\uDCC4" to getString(R.string.tip_6),
            "\uD83D\uDD13" to getString(R.string.tip_7),
            "\uD83D\uDCDE" to getString(R.string.tip_8)
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
