package com.twoskoops707.sixdegrees.ui.results

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.twoskoops707.sixdegrees.R

object FindingClickBinder {

    fun bind(
        card: MaterialCardView,
        fragment: Fragment,
        finding: DossierFinding,
        meta: Map<String, String> = emptyMap()
    ) {
        val ctx = fragment.requireContext()
        card.isClickable = true
        card.isFocusable = true

        card.setOnClickListener {
            when {
                finding.isPivot -> {
                    val parts = finding.value.removePrefix("pivot://").split("/", limit = 2)
                    val bundle = Bundle().apply {
                        putString("query", parts.getOrNull(1).orEmpty())
                        putString("type", parts.getOrNull(0) ?: "person")
                    }
                    fragment.findNavController().navigate(R.id.action_results_to_progress, bundle)
                }
                else -> {
                    val url = FindingUrlHelper.resolveUrl(finding, meta)
                    if (url != null) openUrl(ctx, url)
                }
            }
        }

        card.setOnLongClickListener {
            val text = finding.value.removePrefix("pivot://").substringAfter("/", finding.value)
            copyToClipboard(ctx, text)
            Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun openUrl(ctx: Context, url: String) {
        val prefs = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val pkg = when (prefs.getString("pref_browser", "firefox")) {
            "ddg" -> "com.duckduckgo.mobile.android"
            "chrome" -> "com.android.chrome"
            "default" -> null
            else -> "org.mozilla.firefox"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (pkg != null) intent.setPackage(pkg)
        try {
            ctx.startActivity(intent)
        } catch (_: Exception) {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun copyToClipboard(ctx: Context, text: String) {
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("finding", text))
    }
}
