package com.twoskoops707.sixdegrees.ui.results

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
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
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.databinding.FragmentDossierSectionBinding

class ReportPagerAdapter(
    private val fragment: Fragment,
    private val sections: List<DossierSection>
) : RecyclerView.Adapter<ReportPagerAdapter.TabPageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabPageViewHolder {
        val binding = FragmentDossierSectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TabPageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabPageViewHolder, position: Int) {
        val section = sections[position]
        val ctx = fragment.requireContext()
        val binding = holder.binding

        binding.dossierSectionTitle.text = "${section.icon}  ${section.title}"
        binding.dossierSectionTitle.visibility = View.VISIBLE
        binding.dossierSectionCount.text = "${section.findings.size} findings"
        binding.dossierSectionCount.visibility = View.VISIBLE

        binding.dossierSectionContainer.removeAllViews()
        section.findings.forEach { finding ->
            binding.dossierSectionContainer.addView(buildFindingCard(ctx, finding))
        }

        binding.dossierSkippedContainer.removeAllViews()
        if (section.skippedNotes.isNotEmpty()) {
            binding.dossierSkippedContainer.visibility = View.VISIBLE
            section.skippedNotes.forEach { note ->
                binding.dossierSkippedContainer.addView(buildSkippedNote(ctx, note))
            }
        } else {
            binding.dossierSkippedContainer.visibility = View.GONE
        }
    }

    override fun getItemCount() = sections.size

    class TabPageViewHolder(val binding: FragmentDossierSectionBinding) : RecyclerView.ViewHolder(binding.root)

    private fun buildFindingCard(ctx: Context, finding: DossierFinding): View {
        val density = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * density).toInt()

        val card = MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(8f) }
            radius = dp(12f).toFloat()
            strokeWidth = dp(1f)
            strokeColor = ContextCompat.getColor(ctx, R.color.border)
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface))
        }

        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
        }

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        finding.label?.let { label ->
            topRow.addView(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = label.uppercase()
                textSize = 9f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            })
        }
        topRow.addView(buildConfidenceBadge(ctx, finding.confidence))
        inner.addView(topRow)

        val displayValue = if (finding.isPivot) {
            finding.value.removePrefix("pivot://").split("/", limit = 2).getOrNull(1) ?: finding.value
        } else finding.value
        inner.addView(TextView(ctx).apply {
            text = displayValue
            textSize = 14f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        })
        inner.addView(TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(8f) }
            text = finding.source
            textSize = 10f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_dim))
        })

        card.addView(inner)
        if (finding.isLink) {
            card.setOnClickListener {
                fragment.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(finding.value)))
            }
        } else if (finding.isPivot) {
            val parts = finding.value.removePrefix("pivot://").split("/", limit = 2)
            card.setOnClickListener {
                val bundle = Bundle().apply {
                    putString("query", parts.getOrNull(1).orEmpty())
                    putString("type", parts.getOrNull(0) ?: "person")
                }
                fragment.findNavController().navigate(R.id.action_results_to_progress, bundle)
            }
        }
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

    private fun buildSkippedNote(ctx: Context, note: String): View {
        val density = ctx.resources.displayMetrics.density
        return MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (6 * density).toInt() }
            radius = (8 * density)
            strokeWidth = (1 * density).toInt()
            strokeColor = ContextCompat.getColor(ctx, R.color.score_yellow)
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.warning_dim))
            addView(TextView(ctx).apply {
                setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
                text = note
                textSize = 12f
                setTextColor(ContextCompat.getColor(ctx, R.color.score_yellow))
            })
        }
    }
}
