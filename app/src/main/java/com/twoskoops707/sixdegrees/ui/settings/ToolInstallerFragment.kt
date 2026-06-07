package com.twoskoops707.sixdegrees.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.databinding.FragmentToolInstallerBinding
import kotlinx.coroutines.launch

class ToolInstallerFragment : Fragment() {

    private var _binding: FragmentToolInstallerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ToolInstallerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolInstallerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        buildCompanionSection()
        buildCliSection()
        observeStates()
    }

    private fun buildCompanionSection() {
        val ctx = requireContext()
        val d = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * d).toInt()

        viewModel.companionApks.forEach { apk ->
            val card = com.google.android.material.card.MaterialCardView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(10f) }
                setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.fi_charcoal))
                radius = 0f
                cardElevation = 0f
                strokeColor = ContextCompat.getColor(ctx, R.color.fi_border)
                strokeWidth = dp(1f)
            }
            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            }
            val textCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(ctx).apply {
                text = apk.displayName
                textSize = 14f
                setTextColor(ContextCompat.getColor(ctx, R.color.fi_parchment))
                typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
            })
            textCol.addView(TextView(ctx).apply {
                text = apk.description
                textSize = 11f
                setTextColor(ContextCompat.getColor(ctx, R.color.fi_ash))
                typeface = android.graphics.Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(2f) }
            })
            val isInstalled = viewModel.isInstalled(apk.packageName)
            val btn = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = if (isInstalled) "INSTALLED" else "GET"
                isEnabled = !isInstalled
                textSize = 10f
                letterSpacing = 0.1f
                cornerRadius = 0
                strokeColor = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, if (isInstalled) R.color.fi_ash else R.color.fi_orange)
                )
                setTextColor(ContextCompat.getColor(ctx, if (isInstalled) R.color.fi_ash else R.color.fi_orange))
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apk.downloadUrl)))
                }
            }
            inner.addView(textCol)
            inner.addView(btn)
            card.addView(inner)
            binding.containerFoundation.addView(card)
        }
    }

    private fun buildFoundationSection() {
        val ctx = requireContext()
        val d = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * d).toInt()

        viewModel.cliApks.filter { it.fileName.isNotEmpty() }.forEach { apk ->
            val card = com.google.android.material.card.MaterialCardView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(10f) }
                setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.fi_charcoal))
                radius = 0f
                cardElevation = 0f
                strokeColor = ContextCompat.getColor(ctx, R.color.fi_border)
                strokeWidth = dp(1f)
            }

            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            }

            val nameTv = TextView(ctx).apply {
                text = apk.displayName
                textSize = 15f
                setTextColor(ContextCompat.getColor(ctx, R.color.fi_parchment))
                typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
                letterSpacing = 0.04f
            }

            val descTv = TextView(ctx).apply {
                text = apk.description
                textSize = 11f
                setTextColor(ContextCompat.getColor(ctx, R.color.fi_ash))
                typeface = android.graphics.Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(2f); it.bottomMargin = dp(10f) }
            }

            val progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(4f)
                ).also { it.bottomMargin = dp(8f) }
                progressTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.fi_orange)
                )
                max = 100
                visibility = View.GONE
            }

            val btn = MaterialButton(ctx).apply {
                tag = apk.id
                text = if (viewModel.isInstalled(apk.packageName)) "INSTALLED" else "DOWNLOAD"
                isEnabled = !viewModel.isInstalled(apk.packageName)
                setBackgroundColor(ContextCompat.getColor(ctx,
                    if (viewModel.isInstalled(apk.packageName)) R.color.fi_charcoal else R.color.fi_orange))
                setTextColor(ContextCompat.getColor(ctx,
                    if (viewModel.isInstalled(apk.packageName)) R.color.fi_ash else R.color.fi_ink))
                textSize = 11f
                letterSpacing = 0.1f
                cornerRadius = 0
                setOnClickListener { viewModel.download(apk) }
            }

            inner.addView(nameTv)
            inner.addView(descTv)
            inner.addView(progressBar)
            inner.addView(btn)
            card.addView(inner)
            binding.containerFoundation.addView(card)

            card.setTag(R.id.source_spinner, progressBar)
            card.setTag(R.id.source_icon, btn)
        }
    }

    private fun buildCliSection() {
        val ctx = requireContext()
        val d = ctx.resources.displayMetrics.density
        fun dp(f: Float) = (f * d).toInt()

        viewModel.cliApks.filter { it.fileName.isEmpty() }.forEach { apk ->
            val card = com.google.android.material.card.MaterialCardView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(10f) }
                setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.fi_charcoal))
                radius = 0f
                cardElevation = 0f
                strokeColor = ContextCompat.getColor(ctx, R.color.fi_border)
                strokeWidth = dp(1f)
            }

            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            }

            val textCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            textCol.addView(TextView(ctx).apply {
                text = apk.displayName
                textSize = 14f
                setTextColor(ContextCompat.getColor(ctx, R.color.fi_parchment))
                typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
            })
            textCol.addView(TextView(ctx).apply {
                text = apk.description
                textSize = 11f
                setTextColor(ContextCompat.getColor(ctx, R.color.fi_ash))
                typeface = android.graphics.Typeface.MONOSPACE
            })

            val btn = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "VIEW"
                textSize = 10f
                letterSpacing = 0.1f
                cornerRadius = 0
                strokeColor = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.fi_orange)
                )
                setTextColor(ContextCompat.getColor(ctx, R.color.fi_orange))
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apk.downloadUrl)))
                }
            }

            inner.addView(textCol)
            inner.addView(btn)
            card.addView(inner)
            binding.containerBrowser.addView(card)
        }
    }

    private fun observeStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.installStates.collect { states ->
                states.forEach { (apkId, state) ->
                    val apk = viewModel.foundationApks.find { it.id == apkId } ?: return@forEach
                    val cardIndex = viewModel.foundationApks.indexOf(apk)
                    val card = binding.containerFoundation.getChildAt(cardIndex)
                        as? com.google.android.material.card.MaterialCardView ?: return@forEach
                    val pb = card.getTag(R.id.source_spinner) as? ProgressBar ?: return@forEach
                    val btn = card.getTag(R.id.source_icon) as? MaterialButton ?: return@forEach

                    when (state) {
                        is InstallState.Downloading -> {
                            pb.visibility = View.VISIBLE
                            pb.progress = state.percent
                            btn.text = "${state.percent}%"
                            btn.isEnabled = false
                        }
                        is InstallState.ReadyToInstall -> {
                            pb.visibility = View.GONE
                            btn.text = "INSTALL"
                            btn.isEnabled = true
                            btn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.fi_orange))
                            btn.setOnClickListener {
                                startActivity(viewModel.installApk(apk, state.file))
                            }
                        }
                        is InstallState.Error -> {
                            pb.visibility = View.GONE
                            btn.text = "RETRY"
                            btn.isEnabled = true
                            btn.setOnClickListener { viewModel.download(apk) }
                        }
                        InstallState.Idle, InstallState.Installed -> {}
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
