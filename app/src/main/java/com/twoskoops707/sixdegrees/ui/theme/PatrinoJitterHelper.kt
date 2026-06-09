package com.twoskoops707.sixdegrees.ui.theme

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.random.Random

/**
 * Occasional subtle shake on visible [TextView]s when el/la Patrino/a theme is active.
 * Respects reduce-motion accessibility and the app's animation preference.
 */
class PatrinoJitterHelper(
    private val root: View,
    private val isEnabled: () -> Boolean,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private val jitterRunnable = Runnable {
        if (running && isEnabled()) {
            jitterOnce()
            scheduleNext()
        }
    }

    fun start() {
        if (running || !isEnabled()) return
        running = true
        scheduleNext()
    }

    fun stop() {
        running = false
        handler.removeCallbacks(jitterRunnable)
    }

    private fun scheduleNext() {
        if (!running) return
        val delayMs = 3000L + Random.nextLong(5001L) // 3–8 sec
        handler.postDelayed(jitterRunnable, delayMs)
    }

    private fun jitterOnce() {
        val candidates = collectVisibleTextViews(root)
        if (candidates.isEmpty()) return

        val count = minOf(Random.nextInt(1, 4), candidates.size)
        candidates.shuffled().take(count).forEach { tv ->
            if (tv.isShown && tv.width > 0) {
                applyJitter(tv)
            }
        }
    }

    private fun applyJitter(view: TextView) {
        val density = view.resources.displayMetrics.density
        val amplitude = (2 + Random.nextInt(3)) * density // 2–4 dp
        val dx = (if (Random.nextBoolean()) 1 else -1) * amplitude
        val dy = (if (Random.nextBoolean()) 1 else -1) * amplitude * 0.6f

        val set = AnimatorSet()
        set.playTogether(
            ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, dx, -dx * 0.5f, 0f),
            ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, dy, 0f),
        )
        set.duration = 200L
        set.start()
    }

    private fun collectVisibleTextViews(view: View, out: MutableList<TextView> = mutableListOf()): List<TextView> {
        if (view.visibility != View.VISIBLE) return out
        when (view) {
            is TextView -> {
                if (view.text.isNotBlank()) out.add(view)
            }
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    collectVisibleTextViews(view.getChildAt(i), out)
                }
            }
        }
        return out
    }

    companion object {
        fun isReduceMotionEnabled(context: Context): Boolean {
            val cr = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    if (Settings.Global.getInt(cr, Settings.Global.ACCESSIBILITY_REDUCE_MOTION, 0) == 1) {
                        return true
                    }
                } catch (_: Exception) { }
            }
            return try {
                Settings.Global.getFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f ||
                    Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            } catch (_: Exception) {
                false
            }
        }
    }
}
