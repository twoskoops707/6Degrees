package com.twoskoops707.sixdegrees.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.twoskoops707.sixdegrees.R

object PlugBgFactory {

    fun createBg(context: Context, variant: String): Drawable = when (variant) {
        "leaves"   -> createLeavesBg(context)
        "bricks"   -> ContextCompat.getDrawable(context, R.drawable.bg_plug_brick)
            ?: createBricksBg(context)
        "medellin" -> createMedellinBg(context)
        else       -> ColorDrawable(ContextCompat.getColor(context, R.color.plug_bg))
    }

    private fun createLeavesBg(context: Context): Drawable {
        val dp = context.resources.displayMetrics.density
        val size = (56 * dp).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply {
            color = ContextCompat.getColor(context, R.color.plug_bg)
        }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bg)

        val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.plug_green)
            style = Paint.Style.FILL
            alpha = 48
        }
        val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.plug_green_bright)
            strokeWidth = dp * 1.2f
            style = Paint.Style.STROKE
            alpha = 64
        }

        val cx = size / 2f
        val cy = size / 2f
        val leafPath = Path()
        val lw = size * 0.22f
        val lh = size * 0.38f

        for (angle in listOf(0f, 60f, 120f, 180f, 240f, 300f)) {
            c.save()
            c.translate(cx, cy)
            c.rotate(angle)
            leafPath.reset()
            leafPath.moveTo(0f, -size * 0.05f)
            leafPath.cubicTo(-lw, -lh * 0.4f, -lw * 0.8f, -lh, 0f, -lh)
            leafPath.cubicTo(lw * 0.8f, -lh, lw, -lh * 0.4f, 0f, -size * 0.05f)
            c.drawPath(leafPath, leafPaint)
            c.drawLine(0f, -size * 0.05f, 0f, -lh * 0.9f, stemPaint)
            c.restore()
        }

        return BitmapDrawable(context.resources, bmp).apply {
            setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    private fun createBricksBg(context: Context): Drawable {
        val dp = context.resources.displayMetrics.density
        val bw = (72 * dp).toInt()
        val bh = (32 * dp).toInt()
        val totalH = bh * 2
        val bmp = Bitmap.createBitmap(bw, totalH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val grout = ContextCompat.getColor(context, R.color.plug_grout)
        val brick = ContextCompat.getColor(context, R.color.plug_brick)
        val brickAlt = ContextCompat.getColor(context, R.color.plug_brick_alt)
        val bg = Paint().apply { color = grout }
        c.drawRect(0f, 0f, bw.toFloat(), totalH.toFloat(), bg)

        val brickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val mortar = dp * 2f
        val half = bw / 2f
        brickPaint.color = brick
        c.drawRect(mortar, mortar, half - mortar, bh - mortar, brickPaint)
        brickPaint.color = brickAlt
        c.drawRect(half + mortar, mortar, bw - mortar, bh - mortar, brickPaint)
        brickPaint.color = brickAlt
        c.drawRect(mortar, bh + mortar, half - mortar, totalH - mortar, brickPaint)
        brickPaint.color = brick
        c.drawRect(half + mortar, bh + mortar, bw - mortar, totalH - mortar, brickPaint)

        return BitmapDrawable(context.resources, bmp).apply {
            setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    private fun createMedellinBg(context: Context): Drawable {
        val dp = context.resources.displayMetrics.density
        val bw = (72 * dp).toInt()
        val bh = (32 * dp).toInt()
        val totalH = bh * 2
        val bmp = Bitmap.createBitmap(bw, totalH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply { color = 0xFF0D0400.toInt() }
        c.drawRect(0f, 0f, bw.toFloat(), totalH.toFloat(), bg)

        val yellowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x88FCD116.toInt()
            strokeWidth = dp
            style = Paint.Style.STROKE
        }
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x88CE1126.toInt()
            strokeWidth = dp
            style = Paint.Style.STROKE
        }
        val half = bw / 2f
        c.drawRect(dp, dp, half - dp, bh - dp, yellowPaint)
        c.drawRect(half + dp, dp, bw - dp, bh - dp, yellowPaint)
        c.drawRect(dp, bh + dp, half - dp, totalH - dp, redPaint)
        c.drawRect(half + dp, bh + dp, bw - dp, totalH - dp, redPaint)

        return BitmapDrawable(context.resources, bmp).apply {
            setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }
}
