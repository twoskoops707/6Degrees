package com.twoskoops707.sixdegrees.ui.results

import android.content.Context
import android.graphics.Typeface
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
import com.twoskoops707.sixdegrees.databinding.FragmentDossierSectionBinding

class DossierSectionAdapter(
    private val fragment: Fragment,
    private val sections: List<DossierSection>,
    private val showTechnicalDetails: Boolean = true,
    private val meta: Map<String, String> = emptyMap()
) : RecyclerView.Adapter<DossierSectionAdapter.SectionViewHolder>() {

    class SectionViewHolder(val binding: FragmentDossierSectionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
        val binding = FragmentDossierSectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SectionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        val section = sections[position]
        val binding = holder.binding
        val ctx = fragment.requireContext()

        binding.dossierSectionTitle.text = if (section.icon.isBlank()) {
            section.title
        } else {
            "${section.icon}  ${section.title}"
        }
        binding.dossierSectionTitle.visibility = View.VISIBLE

        val realFindings = section.findings.filter { !isPlaceholder(it) }
        binding.dossierSectionCount.text = when {
            realFindings.isEmpty() && section.skippedNotes.isNotEmpty() ->
                "${section.skippedNotes.size} filtered out"
            realFindings.isEmpty() -> "No findings"
            else -> "${realFindings.size} finding${if (realFindings.size != 1) "s" else ""}"
        }
        binding.dossierSectionCount.visibility = View.VISIBLE

        binding.dossierSectionContainer.removeAllViews()
        section.findings.forEach { finding ->
            binding.dossierSectionContainer.addView(buildFindingCard(ctx, finding))
        }

        binding.dossierSkippedContainer.removeAllViews()
        if (showTechnicalDetails && section.skippedNotes.isNotEmpty()) {
            binding.dossierSkippedContainer.visibility = View.VISIBLE
            binding.dossierSkippedContainer.addView(buildSkippedHeader(ctx))
            section.skippedNotes.forEach { note ->
                binding.dossierSkippedContainer.addView(buildSkippedNote(ctx, note))
            }
        } else {
            binding.dossierSkippedContainer.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = sections.size

    private fun isPlaceholder(finding: DossierFinding): Boolean {
        val v = finding.value.lowercase()
        return v.startsWith("no ") && v.contains("found")
    }

    private fun buildFindingCard(ctx: Context, finding: DossierFinding): View {
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()

        val card = MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also {
                it.bottomMargin = dp(8f)
            }
            radius = dp(12f).toFloat()
            strokeWidth = dp(1f)
            strokeColor = when {
                finding.isWarning -> ContextCompat.getColor(ctx, R.color.score_red)
                finding.isLink || finding.isPivot -> ContextCompat.getColor(ctx, R.color.accent_cyan)
                else -> ContextCompat.getColor(ctx, R.color.border)
            }
            cardElevation = 0f
            setCardBackgroundColor(
                if (finding.isWarning) ContextCompat.getColor(ctx, R.color.error_dim)
                else ContextCompat.getColor(ctx, R.color.surface)
            )
        }

        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
        }

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(6f) }
        }

        finding.label?.takeIf { it.isNotBlank() }?.let { label ->
            topRow.addView(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = label.uppercase()
                textSize = 9f
                letterSpacing = 0.14f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            })
        } ?: run {
            topRow.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            })
        }

        if (showTechnicalDetails) {
            topRow.addView(buildConfidenceBadge(ctx, finding.confidence))
        }
        inner.addView(topRow)

        val displayValue = when {
            finding.isPivot -> finding.value.removePrefix("pivot://").split("/", limit = 2).getOrNull(1) ?: finding.value
            else -> finding.value
        }
        inner.addView(TextView(ctx).apply {
            text = displayValue
            textSize = 14f
            setLineSpacing(0f, 1.15f)
            setTextIsSelectable(true)
            typeface = if (finding.isLink || finding.isPivot) Typeface.MONOSPACE else Typeface.DEFAULT
            setTextColor(
                when {
                    finding.isWarning -> ContextCompat.getColor(ctx, R.color.score_red)
                    finding.isLink || finding.isPivot -> ContextCompat.getColor(ctx, R.color.accent_cyan)
                    else -> ContextCompat.getColor(ctx, R.color.text_primary)
                }
            )
        })

        if (showTechnicalDetails) {
            inner.addView(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(8f) }
                text = finding.source
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setTextColor(ContextCompat.getColor(ctx, R.color.text_dim))
                setTextIsSelectable(true)
            })
        }

        card.addView(inner)
        FindingClickBinder.bind(card, fragment, finding, meta)
        return card
    }

    private fun buildConfidenceBadge(ctx: Context, confidence: DossierConfidence): TextView {
        val (label, bg, fg) = when (confidence) {
            DossierConfidence.HIGH -> Triple("HIGH", R.color.success_dim, R.color.score_green)
            DossierConfidence.MEDIUM -> Triple("MED", R.color.warning_dim, R.color.score_yellow)
            DossierConfidence.LOW -> Triple("LOW", R.color.surface_elevated, R.color.text_secondary)
        }
        val density = ctx.resources.displayMetrics.density
        return TextView(ctx).apply {
            text = label
            textSize = 9f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
            setBackgroundColor(ContextCompat.getColor(ctx, bg))
            setTextColor(ContextCompat.getColor(ctx, fg))
        }
    }

    private fun buildSkippedHeader(ctx: Context): TextView {
        return TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (6 * ctx.resources.displayMetrics.density).toInt() }
            text = "FILTERED OUT"
            textSize = 9f
            letterSpacing = 0.16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.score_yellow))
        }
    }

    private fun buildSkippedNote(ctx: Context, note: String): View {
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()
        return MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(6f) }
            radius = dp(8f).toFloat()
            strokeWidth = dp(1f)
            strokeColor = ContextCompat.getColor(ctx, R.color.score_yellow)
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.warning_dim))
            addView(TextView(ctx).apply {
                setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                text = note
                textSize = 12f
                setLineSpacing(0f, 1.2f)
                setTextColor(ContextCompat.getColor(ctx, R.color.score_yellow))
                setTextIsSelectable(true)
            })
        }
    }

}
