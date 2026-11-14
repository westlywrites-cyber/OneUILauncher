package com.westly.oneuilauncher.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * The custom status bar: time on the left; network, battery percentage and a battery icon
 * on the right. Everything is drawn directly, so it needs no layout files.
 */
class StatusBarView(context: Context) : View(context) {

    enum class Network { NONE, WIFI, CELLULAR }

    var timeText: String = ""
        set(value) { field = value; invalidate() }
    var batteryLevel: Int = 100
        set(value) { field = value.coerceIn(0, 100); invalidate() }
    var charging: Boolean = false
        set(value) { field = value; invalidate() }
    var network: Network = Network.NONE
        set(value) { field = value; invalidate() }

    /** App icons of active notifications, drawn after the time. */
    var notificationIcons: List<Bitmap> = emptyList()
        set(value) { field = value; invalidate() }
    var extraNotifications: Int = 0
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * scaledDensity
        isFakeBoldText = true
    }
    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * scaledDensity
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.6f * density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val rect = RectF()
    private val iconRect = RectF()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bolt = Path()

    private fun dp(value: Float) = value * density

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)

        val w = width.toFloat()
        val h = height.toFloat()
        val pad = dp(20f)
        val centerY = h / 2f

        // Time, left.
        val timeBaseline = centerY - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(timeText, pad, timeBaseline, textPaint)

        // Notification icons, right after the time.
        var iconX = pad + textPaint.measureText(timeText) + dp(10f)
        val iconSize = dp(16f)
        for (bitmap in notificationIcons) {
            iconRect.set(iconX, centerY - iconSize / 2f, iconX + iconSize, centerY + iconSize / 2f)
            canvas.drawBitmap(bitmap, null, iconRect, bitmapPaint)
            iconX += iconSize + dp(6f)
        }
        if (extraNotifications > 0) {
            val extraBaseline = centerY - (percentPaint.ascent() + percentPaint.descent()) / 2f
            canvas.drawText("+$extraNotifications", iconX, extraBaseline, percentPaint)
        }

        // Right side, drawn from the right edge towards the middle.
        var x = w - pad

        // Battery icon.
        val bodyW = dp(22f)
        val bodyH = dp(11f)
        val bodyLeft = x - bodyW
        val bodyTop = centerY - bodyH / 2f
        val levelColor = when {
            charging -> Color.parseColor("#4ADE80")
            batteryLevel <= 15 -> Color.parseColor("#FF5C5C")
            else -> Color.WHITE
        }
        strokePaint.color = Color.WHITE
        rect.set(bodyLeft, bodyTop, bodyLeft + bodyW, bodyTop + bodyH)
        canvas.drawRoundRect(rect, dp(3f), dp(3f), strokePaint)
        // Little nub on the right.
        fillPaint.color = Color.WHITE
        rect.set(bodyLeft + bodyW + dp(1f), centerY - dp(2.5f), bodyLeft + bodyW + dp(3f), centerY + dp(2.5f))
        canvas.drawRoundRect(rect, dp(1f), dp(1f), fillPaint)
        // Charge level.
        val inset = dp(2.2f)
        val fullInner = bodyW - inset * 2f
        fillPaint.color = levelColor
        rect.set(
            bodyLeft + inset,
            bodyTop + inset,
            bodyLeft + inset + fullInner * batteryLevel / 100f,
            bodyTop + bodyH - inset
        )
        canvas.drawRoundRect(rect, dp(1.2f), dp(1.2f), fillPaint)
        if (charging) {
            val cx = bodyLeft + bodyW / 2f
            bolt.reset()
            bolt.moveTo(cx + dp(1f), bodyTop + dp(1.5f))
            bolt.lineTo(cx - dp(2.5f), centerY + dp(0.8f))
            bolt.lineTo(cx - dp(0.2f), centerY + dp(0.8f))
            bolt.lineTo(cx - dp(1f), bodyTop + bodyH - dp(1.5f))
            bolt.lineTo(cx + dp(2.5f), centerY - dp(0.8f))
            bolt.lineTo(cx + dp(0.2f), centerY - dp(0.8f))
            bolt.close()
            fillPaint.color = Color.WHITE
            canvas.drawPath(bolt, fillPaint)
        }
        x = bodyLeft - dp(6f)

        // Battery percentage.
        val percent = "$batteryLevel%"
        val percentWidth = percentPaint.measureText(percent)
        val percentBaseline = centerY - (percentPaint.ascent() + percentPaint.descent()) / 2f
        canvas.drawText(percent, x - percentWidth, percentBaseline, percentPaint)
        x -= percentWidth + dp(10f)

        // Network.
        when (network) {
            Network.WIFI -> drawWifi(canvas, x - dp(16f), centerY)
            Network.CELLULAR -> drawBars(canvas, x - dp(15f), centerY)
            Network.NONE -> Unit
        }
    }

    private fun drawWifi(canvas: Canvas, left: Float, centerY: Float) {
        val width = dp(16f)
        val cx = left + width / 2f
        val baseY = centerY + dp(5f)
        fillPaint.color = Color.WHITE
        canvas.drawCircle(cx, baseY, dp(1.4f), fillPaint)
        strokePaint.color = Color.WHITE
        for (i in 1..3) {
            val r = dp(3.2f) * i
            rect.set(cx - r, baseY - r, cx + r, baseY + r)
            canvas.drawArc(rect, 225f, 90f, false, strokePaint)
        }
    }

    private fun drawBars(canvas: Canvas, left: Float, centerY: Float) {
        val barW = dp(2.6f)
        val gap = dp(1.6f)
        val bottom = centerY + dp(5.5f)
        fillPaint.color = Color.WHITE
        for (i in 0 until 4) {
            val barH = dp(3f) + dp(2.6f) * i
            val l = left + i * (barW + gap)
            rect.set(l, bottom - barH, l + barW, bottom)
            canvas.drawRoundRect(rect, dp(0.8f), dp(0.8f), fillPaint)
        }
    }
}
