package com.twoskoops707.sixdegrees.ui.resources

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.data.osint.OsintFrameworkCategoryMapper
import com.twoskoops707.sixdegrees.data.osint.OsintFrameworkRepository
import com.twoskoops707.sixdegrees.data.osint.OsintToolRegistry
import com.twoskoops707.sixdegrees.databinding.FragmentOsintResourcesBinding

/**
 * OSINT Framework hub — only lists free tools that run in-app and feed dossier reports.
 */
class OsintResourcesFragment : Fragment() {

    private var _binding: FragmentOsintResourcesBinding? = null
    private val binding get() = _binding!!

    private var currentFilter = "all"
    private var searchQuery = ""
    private var allTools: List<OsintToolRegistry.OsintTool> = emptyList()
    private val selectedTools = mutableSetOf<String>()
    private val checkBoxMap = mutableMapOf<String, CheckBox>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOsintResourcesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val prefilledQuery = arguments?.getString("query") ?: ""
        if (prefilledQuery.isNotBlank()) {
            binding.inputHubQuery.setText(prefilledQuery)
        }

        val prefilledCategory = arguments?.getString("filterCategory") ?: ""
        if (prefilledCategory.isNotBlank() &&
            OsintFrameworkCategoryMapper.hubCategories.any { it.id == prefilledCategory }
        ) {
            currentFilter = prefilledCategory
        }

        allTools = OsintToolRegistry.ensureLoaded(requireContext())
        binding.inputToolSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                rebuildCards()
            }
        })

        buildCategoryChips()
        rebuildCards()

        binding.btnSelectAll.setOnClickListener {
            visibleTools().forEach { selectedTools.add(it.id) }
            checkBoxMap.values.forEach { it.isChecked = true }
        }

        binding.btnClearSelection.setOnClickListener {
            selectedTools.clear()
            checkBoxMap.values.forEach { it.isChecked = false }
        }

        binding.btnLaunchSelected.setOnClickListener {
            val query = binding.inputHubQuery.text?.toString()?.trim() ?: ""
            if (selectedTools.isEmpty()) {
                Toast.makeText(requireContext(), "Select at least one tool first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (query.isBlank()) {
                Toast.makeText(requireContext(), "Enter a target query above", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            launchSelectedTools(query)
        }

        binding.inputHubQuery.setOnEditorActionListener { _, _, _ ->
            val query = binding.inputHubQuery.text?.toString()?.trim() ?: ""
            if (selectedTools.isNotEmpty() && query.isNotBlank()) launchSelectedTools(query)
            true
        }
    }

    private fun visibleTools(): List<OsintToolRegistry.OsintTool> =
        OsintToolRegistry.searchTools(requireContext(), searchQuery, currentFilter)

    private fun buildCategoryChips() {
        val ctx = requireContext()
        val chipGroup = binding.chipContainer
        chipGroup.isSingleSelection = true
        chipGroup.removeAllViews()

        val allChip = Chip(ctx).apply {
            text = "All (${allTools.size})"
            tag = "all"
            isCheckable = true
            isChecked = currentFilter == "all"
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    currentFilter = "all"
                    rebuildCards()
                }
            }
        }
        chipGroup.addView(allChip)

        OsintFrameworkCategoryMapper.hubCategories.forEach { category ->
            val count = allTools.count { category.id in it.categories }
            if (count == 0) return@forEach
            val chip = Chip(ctx).apply {
                text = "${category.label} ($count)"
                tag = category.id
                isCheckable = true
                isChecked = currentFilter == category.id
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        currentFilter = category.id
                        rebuildCards()
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun rebuildCards() {
        selectedTools.clear()
        checkBoxMap.clear()
        binding.resourcesContainer.removeAllViews()
        buildResourceCards()
    }

    private fun buildResourceCards() {
        val container = binding.resourcesContainer
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()

        val tools = visibleTools()
        if (tools.isEmpty()) {
            container.addView(TextView(ctx).apply {
                text = "No in-app OSINT Framework tools match this filter."
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                setPadding(dp(4f), dp(8f), dp(4f), dp(8f))
            })
            return
        }

        val grouped = LinkedHashMap<String, MutableList<OsintToolRegistry.OsintTool>>()
        tools.forEach { tool ->
            val cat = tool.categories.firstOrNull() ?: "tools"
            grouped.getOrPut(cat) { mutableListOf() }.add(tool)
        }

        grouped.forEach { (categoryId, categoryTools) ->
            val label = OsintFrameworkCategoryMapper.hubCategories
                .firstOrNull { it.id == categoryId }?.label ?: categoryId

            val sectionLabel = TextView(ctx).apply {
                text = label.uppercase()
                textSize = 10f
                letterSpacing = 0.14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dp(16f)
                lp.bottomMargin = dp(6f)
                layoutParams = lp
            }
            container.addView(sectionLabel)

            val card = com.google.android.material.card.MaterialCardView(ctx).apply {
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = dp(4f)
                layoutParams = lp
                setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface))
                radius = dp(12f).toFloat()
                strokeColor = ContextCompat.getColor(ctx, R.color.border)
                strokeWidth = ctx.resources.displayMetrics.density.toInt()
                cardElevation = 0f
            }

            val cardContent = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
            }

            categoryTools.sortedBy { it.name.lowercase() }.forEachIndexed { index, tool ->
                cardContent.addView(buildToolRow(tool, dp(16f)))
                if (index < categoryTools.lastIndex) {
                    cardContent.addView(buildDivider())
                }
            }

            card.addView(cardContent)
            container.addView(card)
        }

        val attribution = TextView(ctx).apply {
            text = OsintFrameworkRepository.FRAMEWORK_ATTRIBUTION
            textSize = 10f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_dim))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(20f)
            layoutParams = lp
        }
        container.addView(attribution)
    }

    private fun buildToolRow(tool: OsintToolRegistry.OsintTool, paddingPx: Int): View {
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(paddingPx, dp(12f), paddingPx, dp(12f))
            isClickable = true
            isFocusable = true
            background = android.util.TypedValue().also { v ->
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, v, true)
            }.resourceId.let { ContextCompat.getDrawable(ctx, it) }
        }

        val cb = CheckBox(ctx).apply {
            isChecked = tool.id in selectedTools
            buttonTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(ctx, R.color.accent_cyan)
            )
            val lp = LinearLayout.LayoutParams(dp(24f), dp(24f))
            lp.marginEnd = dp(12f)
            layoutParams = lp
            setOnCheckedChangeListener { _, checked ->
                if (checked) selectedTools.add(tool.id) else selectedTools.remove(tool.id)
            }
        }
        checkBoxMap[tool.id] = cb

        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        textCol.addView(TextView(ctx).apply {
            text = tool.name
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        })

        textCol.addView(TextView(ctx).apply {
            text = tool.description
            textSize = 11f
            maxLines = 3
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        })

        val badge = TextView(ctx).apply {
            text = if (tool.isQueryable) "IN-APP" else "IN-APP"
            textSize = 9f
            setTextColor(ContextCompat.getColor(ctx, R.color.accent_cyan))
            setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
        }

        row.addView(cb)
        row.addView(textCol)
        row.addView(badge)

        row.setOnClickListener {
            if (tool.urlTemplate.isBlank()) {
                Toast.makeText(
                    ctx,
                    "Run a SixDegrees search to collect ${tool.name} data in your dossier.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            val query = binding.inputHubQuery.text?.toString()?.trim() ?: ""
            openUrl(OsintToolRegistry.buildUrl(tool.urlTemplate, query.ifBlank { "example" }))
        }

        return row
    }

    private fun launchSelectedTools(query: String) {
        var launched = 0
        allTools.filter { it.id in selectedTools }.forEach { tool ->
            if (tool.urlTemplate.isBlank()) return@forEach
            try {
                openUrl(OsintToolRegistry.buildUrl(tool.urlTemplate, query))
                launched++
            } catch (_: Exception) { }
        }
        Toast.makeText(
            requireContext(),
            "Opened $launched reference page${if (launched != 1) "s" else ""}. " +
                "Run a search in SixDegrees to collect in-app report data.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "No browser found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildDivider(): View {
        val ctx = requireContext()
        return View(ctx).apply {
            val density = ctx.resources.displayMetrics.density
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1
            ).also {
                it.marginStart = (16 * density).toInt()
                it.marginEnd = (16 * density).toInt()
            }
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.border))
            alpha = 0.5f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(query: String) = OsintResourcesFragment().apply {
            arguments = Bundle().also { it.putString("query", query) }
        }
    }
}
