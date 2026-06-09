package com.twoskoops707.sixdegrees.ui.common

import android.view.View
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import com.twoskoops707.sixdegrees.R

enum class InvestigationStep { INTAKE, COLLECT, RESOLVE, DOSSIER }

object InvestigationPipelineView {

    fun bind(root: View, activeStep: InvestigationStep) {
        val steps = listOf(
            StepViews(
                InvestigationStep.INTAKE,
                root.findViewById(R.id.pipeline_dot_intake),
                root.findViewById(R.id.pipeline_label_intake)
            ),
            StepViews(
                InvestigationStep.COLLECT,
                root.findViewById(R.id.pipeline_dot_collect),
                root.findViewById(R.id.pipeline_label_collect)
            ),
            StepViews(
                InvestigationStep.RESOLVE,
                root.findViewById(R.id.pipeline_dot_resolve),
                root.findViewById(R.id.pipeline_label_resolve)
            ),
            StepViews(
                InvestigationStep.DOSSIER,
                root.findViewById(R.id.pipeline_dot_dossier),
                root.findViewById(R.id.pipeline_label_dossier)
            )
        )
        val ctx = root.context
        val primary = MaterialColors.getColor(ctx, MaterialR.attr.colorPrimary, "Pipeline")
        val onPrimary = MaterialColors.getColor(ctx, MaterialR.attr.colorOnPrimary, "Pipeline")
        val surfaceVariant = MaterialColors.getColor(ctx, MaterialR.attr.colorSurfaceVariant, "Pipeline")
        val onSurfaceVariant = MaterialColors.getColor(ctx, MaterialR.attr.colorOnSurfaceVariant, "Pipeline")
        val activeOrdinal = activeStep.ordinal

        steps.forEachIndexed { index, step ->
            val isActive = index == activeOrdinal
            val isComplete = index < activeOrdinal
            step.dot.setBackgroundColor(if (isActive || isComplete) primary else surfaceVariant)
            step.dot.setTextColor(if (isActive || isComplete) onPrimary else onSurfaceVariant)
            step.label.setTextColor(if (isActive) primary else onSurfaceVariant)
        }

        listOf(
            R.id.pipeline_connector_1 to InvestigationStep.COLLECT,
            R.id.pipeline_connector_2 to InvestigationStep.RESOLVE,
            R.id.pipeline_connector_3 to InvestigationStep.DOSSIER
        ).forEach { (id, step) ->
            root.findViewById<View>(id)?.setBackgroundColor(
                if (step.ordinal <= activeOrdinal) primary else surfaceVariant
            )
        }
    }

    private data class StepViews(
        val step: InvestigationStep,
        val dot: TextView,
        val label: TextView
    )
}
