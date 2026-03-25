package com.twoskoops707.sixdegrees.ui.results

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.twoskoops707.sixdegrees.R

class ReportPagerAdapter(
    private val fragment: Fragment,
    private val tabs: List<Pair<String, List<Pair<String, String>>>>,
    private val meta: Map<String, String>
) : RecyclerView.Adapter<ReportPagerAdapter.TabPageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabPageViewHolder {
        val scroll = android.widget.ScrollView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(16, 16, 16, 80)
        }
        scroll.addView(container)
        return TabPageViewHolder(scroll)
    }

    override fun onBindViewHolder(holder: TabPageViewHolder, position: Int) {
        val (_, rows) = tabs[position]
        val container = holder.scrollView.getChildAt(0) as LinearLayout
        container.removeAllViews()
        val inflater = LayoutInflater.from(fragment.requireContext())
        val ctx = fragment.requireContext()

        if (rows.isEmpty() || (rows.size == 1 && rows[0].second.isBlank())) {
            addEmptyState(container, ctx)
            return
        }

        val sections = groupIntoSections(rows)
        for ((sectionTitle, sectionRows) in sections) {
            val card = buildSectionCard(ctx, inflater, sectionTitle, sectionRows)
            container.addView(card)
        }
    }

    override fun getItemCount() = tabs.size

    class TabPageViewHolder(val scrollView: android.widget.ScrollView) : RecyclerView.ViewHolder(scrollView)

    private fun addEmptyState(container: LinearLayout, ctx: android.content.Context) {
        val tv = TextView(ctx).apply {
            text = "No data available"
            setTextColor(ContextCompat.getColor(ctx, R.color.accent_cyan))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
        }
        container.addView(tv)
    }

    private fun groupIntoSections(rows: List<Pair<String, String>>): List<Pair<String, List<Pair<String, String>>>> {
        val sections = mutableListOf<Pair<String, MutableList<Pair<String, String>>>>()
        var currentTitle = ""
        var currentRows = mutableListOf<Pair<String, String>>()
        for ((label, value) in rows) {
            if (value.isEmpty()) {
                if (currentRows.isNotEmpty() || currentTitle.isNotEmpty()) {
                    sections.add(currentTitle to currentRows)
                    currentRows = mutableListOf()
                }
                currentTitle = label.trimStart().removePrefix("◈ ").removePrefix("> ").removePrefix("══ ").removeSuffix(" ══").trim()
            } else {
                currentRows.add(label to value)
            }
        }
        if (currentRows.isNotEmpty() || currentTitle.isNotEmpty()) {
            sections.add(currentTitle to currentRows)
        }
        return sections.map { it.first to it.second.toList() }
    }

    private fun getSectionAccentColor(ctx: android.content.Context, title: String): Int {
        val t = title.uppercase()
        return when {
            t.startsWith("⚠") || "CRIMINAL" in t || "LEGAL" in t || "ARREST" in t
                || "COURT" in t || "SANCTIONS" in t || "LEAKED" in t || "BREACH" in t
                || "DARK WEB" in t || "PASTE" in t || "HIBP" in t || "COMB" in t
                || "LEAKCHECK" in t || "IPQS" in t ->
                ContextCompat.getColor(ctx, R.color.score_red)
            "PHONE" in t || "CONTACT" in t || "ADDRESS" in t || "VOTER" in t
                || "EMAIL" in t || "HOLEHE" in t || "NUMBER VALID" in t ->
                ContextCompat.getColor(ctx, R.color.accent_cyan)
            "DIGITAL" in t || "SOCIAL" in t || "SHERLOCK" in t || "MAIGRET" in t
                || "GITHUB" in t || "KEYBASE" in t || "DEV.TO" in t || "PROFILE" in t
                || "HACKER NEWS" in t || "FOUND" in t ->
                ContextCompat.getColor(ctx, R.color.accent_green)
            "NEWS" in t || "INTEL" in t || "DORK" in t || "PROPERTY" in t
                || "FINANCIAL" in t || "VEHICLE" in t || "EDUCATION" in t
                || "ACADEMIC" in t || "HISTORICAL" in t || "LIBRARY" in t
                || "OBITUARY" in t || "AWARDS" in t || "SEARCH ENGINE" in t
                || "INVESTIGATIVE" in t || "PEOPLE-SEARCH" in t ->
                ContextCompat.getColor(ctx, R.color.accent_amber)
            else -> {
                val tv = TypedValue()
                ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
                tv.data
            }
        }
    }

    private fun buildSectionCard(ctx: android.content.Context, inflater: LayoutInflater, title: String, rows: List<Pair<String, String>>): View {
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()
        val isWarning = title.startsWith("⚠")
        val accentColor = getSectionAccentColor(ctx, title)
        val cardBg = if (isWarning) ContextCompat.getColor(ctx, R.color.error_dim) else ContextCompat.getColor(ctx, R.color.surface)
        val borderColor = if (isWarning) ContextCompat.getColor(ctx, R.color.score_red) else ContextCompat.getColor(ctx, R.color.border)

        val card = MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8f); it.bottomMargin = dp(4f) }
            radius = dp(10f).toFloat()
            strokeWidth = dp(1f)
            strokeColor = borderColor
            cardElevation = 0f
            setCardBackgroundColor(cardBg)
        }

        val inner = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        if (title.isNotBlank()) {
            val r = Color.red(accentColor); val g = Color.green(accentColor); val b = Color.blue(accentColor)
            val header = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14f), dp(10f), dp(16f), dp(10f)); setBackgroundColor(Color.argb(30, r, g, b))
            }
            header.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(3f), dp(16f)).also { it.marginEnd = dp(10f) }; setBackgroundColor(accentColor)
            })
            val sectionIcon = when {
                "PHONE" in title.uppercase() || "NUMBER VALID" in title.uppercase() -> "☎ "
                "EMAIL" in title.uppercase() || "BREACH" in title.uppercase() || "HIBP" in title.uppercase() -> "✉ "
                "ADDRESS" in title.uppercase() || "VOTER" in title.uppercase() -> "⌂ "
                title.startsWith("⚠") || "CRIMINAL" in title.uppercase() || "ARREST" in title.uppercase() -> "⚠ "
                "SOCIAL" in title.uppercase() || "DIGITAL" in title.uppercase() || "PROFILE" in title.uppercase() -> "◎ "
                "NEWS" in title.uppercase() -> "◉ "
                "IDENTITY" in title.uppercase() || "SUBJECT" in title.uppercase() -> "◈ "
                else -> "▸ "
            }
            val cleanTitle = title.removePrefix("⚠ ").removePrefix("⚠").trim()
            header.addView(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = "$sectionIcon$cleanTitle"; textSize = 10f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                isAllCaps = true; letterSpacing = 0.12f; setTextColor(accentColor); setTextIsSelectable(true)
            })
            header.addView(TextView(ctx).apply {
                text = "${rows.size}"; textSize = 8f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setTextColor(Color.argb(160, r, g, b))
            })
            inner.addView(header)
            inner.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f))
                setBackgroundColor(accentColor); alpha = 0.25f
            })
        }

        for ((label, value) in rows) {
            val rowView = buildDataRow(ctx, label, value)
            inner.addView(rowView)
        }

        card.addView(inner)
        return card
    }

    private fun buildDataRow(ctx: android.content.Context, label: String, value: String): View {
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()

        val isPivot = value.startsWith("pivot://")
        val isLink = value.startsWith("http://") || value.startsWith("https://")
        val isWarning = label.startsWith("⚠")
        val isCredential = label == "Login" || label == "Password / Hash" || label == "Leaked Record"
        val isNsfwLink = isWarning && isLink
        val isPhone = !isPivot && !isLink && value.matches(Regex("\\+?1?[\\s.\\-]?\\(?\\d{3}\\)?[\\s.\\-]\\d{3}[\\s.\\-]\\d{4}.*"))
        val isEmail = !isPivot && !isLink && !isPhone && value.contains("@") && value.contains(".") && !value.contains(" ") && value.length < 100

        val bgColor = when {
            isNsfwLink || isWarning -> ContextCompat.getColor(ctx, R.color.error_dim)
            isCredential -> ContextCompat.getColor(ctx, R.color.surface_elevated)
            else -> Color.TRANSPARENT
        }
        val accentColor = when {
            isNsfwLink || isWarning -> ContextCompat.getColor(ctx, R.color.score_red)
            isPivot || isPhone || isEmail -> ContextCompat.getColor(ctx, R.color.accent_cyan)
            isLink -> {
                val tv = TypedValue()
                ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
                tv.data
            }
            else -> Color.TRANSPARENT
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            minimumHeight = dp(46)
            gravity = Gravity.CENTER_VERTICAL
        }

        val accentStripe = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(3, ViewGroup.LayoutParams.MATCH_PARENT).also { it.marginEnd = dp(14) }
            visibility = if (accentColor != Color.TRANSPARENT) View.VISIBLE else View.GONE
            setBackgroundColor(accentColor)
        }

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPaddingRelative(dp(14), dp(8), dp(14), dp(8))
        }

        val labelTv = TextView(ctx).apply {
            text = label; textSize = 9f; setTextColor(ContextCompat.getColor(ctx, R.color.accent_cyan))
            letterSpacing = 0.18f; isAllCaps = true; setTextIsSelectable(true)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(2) }
        }
        val valueTv = TextView(ctx).apply {
            text = if (isPivot) value.removePrefix("pivot://").split("/", limit = 2).getOrNull(1) ?: "" else value
            textSize = 13f; setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            setTextIsSelectable(true)
            if (isCredential) { typeface = Typeface.MONOSPACE; textSize = 12f }
        }
        content.addView(labelTv)
        content.addView(valueTv)

        row.addView(accentStripe)
        row.addView(content)
        return row
    }
}
