package com.heysem.alwayson

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.random.Random

/**
 * Draws the whole always-on screen on a black canvas:
 *  - large clock, upper middle, with the date and next alarm under it
 *  - battery level + icon, top right
 *  - a small, faint close "x", bottom right
 * Everything except the close button glides up or down a few pixels every minute
 * (the background drifts in all directions)
 * to avoid burning the same pixels on OLED screens.
 */
class AodView(context: Context) : View(context) {

    var batteryPercent = -1
    var charging = false
    var dimmed = false
    var nextAlarmMillis: Long? = null

    var onCloseTapped: () -> Unit = {}
    var onScreenTapped: () -> Unit = {}
    var onLongPressed: () -> Unit = {}

    /** Blurred background; null means pure black. */
    var background: Bitmap? = null
        set(value) { field = value; invalidate() }

    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val bgDst = RectF()

    private val dp = resources.displayMetrics.density

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(235, 235, 235)
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(160, 160, 160)
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(170, 170, 170)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textSize = 14f * dp
        textAlign = Paint.Align.RIGHT
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        strokeCap = Paint.Cap.ROUND
        color = Color.rgb(170, 170, 170)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val closePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(70, 200, 200, 200) // deliberately faint
    }

    private val closeTouch = RectF()
    // Background drifts in both directions (invisible since it's blurred);
    // text only drifts up/down so it always stays exactly centred left-to-right.
    private var shiftX = 0f
    private var shiftY = 0f
    private val tmpRect = RectF()
    private val bolt = Path()
    private var glide: ValueAnimator? = null

    /** Called once a minute by the activity: glide slowly to a new spot. */
    fun tick() {
        val range = 10f * dp
        val fromX = shiftX
        val fromY = shiftY
        val toX = Random.nextFloat() * 2 * range - range
        val toY = Random.nextFloat() * 2 * range - range
        glide?.cancel()
        glide = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4000
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                shiftX = fromX + (toX - fromX) * t
                shiftY = fromY + (toY - fromY) * t
                invalidate()
            }
            start()
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        glide?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val w = width.toFloat()
        val h = height.toFloat()
        drawBackground(canvas, w, h)

        val cutoutTop = rootWindowInsets?.displayCutout?.safeInsetTop ?: 0
        val cutoutRight = rootWindowInsets?.displayCutout?.safeInsetRight ?: 0
        val cutoutBottom = rootWindowInsets?.displayCutout?.safeInsetBottom ?: 0
        val top = max(cutoutTop.toFloat(), 12f * dp) + 16f * dp
        val right = w - cutoutRight - 20f * dp

        drawClose(canvas, right, h - cutoutBottom - 24f * dp)

        canvas.save()
        canvas.translate(0f, shiftY)
        drawBattery(canvas, right, top)
        drawClock(canvas, w, h, top)
        canvas.restore()
    }

    private fun drawClock(canvas: Canvas, w: Float, h: Float, top: Float) {
        val now = Date()
        val is24 = DateFormat.is24HourFormat(context)
        val time = DateFormat.format(if (is24) "HH:mm" else "h:mm", now).toString()

        val portrait = h >= w
        timePaint.textSize = if (portrait) w * 0.26f else h * 0.30f
        val cx = w / 2f
        val timeBaseline = top + (if (portrait) h * 0.10f else h * 0.08f) + timePaint.textSize * 0.8f
        canvas.drawText(time, cx, timeBaseline, timePaint)

        if (!is24) {
            // Small AM/PM next to the time
            val ampm = DateFormat.format("a", now).toString()
            val timeWidth = timePaint.measureText(time)
            datePaint.textSize = timePaint.textSize * 0.16f
            datePaint.textAlign = Paint.Align.LEFT
            canvas.drawText(ampm, cx + timeWidth / 2f + 6f * dp, timeBaseline, datePaint)
            datePaint.textAlign = Paint.Align.CENTER
        }

        val locale = Locale.getDefault()
        datePaint.textSize = timePaint.textSize * 0.17f
        val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        val dateBaseline = timeBaseline + datePaint.textSize * 2.0f
        canvas.drawText(DateFormat.format(datePattern, now).toString(), cx, dateBaseline, datePaint)

        nextAlarmMillis?.let { alarm ->
            // Only show alarms within the next 24 hours
            if (alarm - now.time in 0..24L * 60 * 60 * 1000) {
                val alarmText = DateFormat.format(if (is24) "HH:mm" else "h:mm a", alarm).toString()
                smallPaint.textAlign = Paint.Align.LEFT
                val textW = smallPaint.measureText(alarmText)
                val iconR = 6f * dp
                val gap = 6f * dp
                val total = iconR * 2 + gap + textW
                val baseline = dateBaseline + 28f * dp
                val iconCx = cx - total / 2f + iconR
                val iconCy = baseline - smallPaint.textSize * 0.35f
                canvas.drawCircle(iconCx, iconCy, iconR, strokePaint)
                canvas.drawLine(iconCx, iconCy, iconCx, iconCy - iconR * 0.55f, strokePaint)
                canvas.drawLine(iconCx, iconCy, iconCx + iconR * 0.45f, iconCy + iconR * 0.25f, strokePaint)
                canvas.drawText(alarmText, iconCx + iconR + gap, baseline, smallPaint)
                smallPaint.textAlign = Paint.Align.RIGHT
            }
        }
    }

    private fun drawBattery(canvas: Canvas, right: Float, top: Float) {
        if (batteryPercent < 0) return
        val bodyW = 24f * dp
        val bodyH = 12f * dp
        val nubW = 2f * dp
        val bodyRight = right - nubW
        val bodyLeft = bodyRight - bodyW
        val bodyTop = top
        val bodyBottom = top + bodyH

        val accent = when {
            charging -> Color.rgb(110, 200, 120)
            batteryPercent <= 15 -> Color.rgb(230, 90, 80)
            else -> Color.rgb(170, 170, 170)
        }

        strokePaint.color = Color.rgb(170, 170, 170)
        tmpRect.set(bodyLeft, bodyTop, bodyRight, bodyBottom)
        canvas.drawRoundRect(tmpRect, 3f * dp, 3f * dp, strokePaint)

        fillPaint.color = Color.rgb(170, 170, 170)
        tmpRect.set(bodyRight, bodyTop + bodyH * 0.3f, bodyRight + nubW, bodyBottom - bodyH * 0.3f)
        canvas.drawRect(tmpRect, fillPaint)

        val pad = 2.5f * dp
        val fillW = (bodyW - pad * 2) * (batteryPercent.coerceIn(0, 100) / 100f)
        fillPaint.color = accent
        tmpRect.set(bodyLeft + pad, bodyTop + pad, bodyLeft + pad + fillW, bodyBottom - pad)
        canvas.drawRoundRect(tmpRect, 1.5f * dp, 1.5f * dp, fillPaint)

        if (charging) {
            // Little lightning bolt to the left of the percentage
            val textW = smallPaint.measureText("$batteryPercent%")
            val bx = bodyLeft - 8f * dp - textW - 10f * dp
            val by = bodyTop - 1f * dp
            val s = 14f * dp
            bolt.reset()
            bolt.moveTo(bx + s * 0.55f, by)
            bolt.lineTo(bx + s * 0.15f, by + s * 0.55f)
            bolt.lineTo(bx + s * 0.45f, by + s * 0.55f)
            bolt.lineTo(bx + s * 0.35f, by + s)
            bolt.lineTo(bx + s * 0.80f, by + s * 0.40f)
            bolt.lineTo(bx + s * 0.50f, by + s * 0.40f)
            bolt.close()
            fillPaint.color = accent
            canvas.drawPath(bolt, fillPaint)
        }

        smallPaint.color = Color.rgb(190, 190, 190)
        canvas.drawText("$batteryPercent%", bodyLeft - 8f * dp, bodyBottom - 1f * dp, smallPaint)
    }

    private fun drawBackground(canvas: Canvas, w: Float, h: Float) {
        val bmp = background ?: return
        // Slightly oversized and drifting with the minute shift (a slow parallax),
        // so no part of the image stays on exactly the same pixels.
        val over = 24f * dp
        bgDst.set(-over, -over, w + over, h + over)
        bgDst.offset(shiftX * 1.5f, shiftY * 1.5f)
        canvas.drawBitmap(bmp, Backgrounds.centerCrop(bmp, bgDst.width(), bgDst.height()), bgDst, bgPaint)
        // Dark veil keeps the text readable and the screen gentle on the eyes and battery.
        canvas.drawColor(Color.argb(if (dimmed) 175 else 95, 0, 0, 0))
    }

    private fun drawClose(canvas: Canvas, right: Float, bottom: Float) {
        val size = 6f * dp
        val cx = right - size
        val cy = bottom - size
        canvas.drawLine(cx - size, cy - size, cx + size, cy + size, closePaint)
        canvas.drawLine(cx - size, cy + size, cx + size, cy - size, closePaint)
        // Generous invisible touch area so it's easy to hit without being big
        val touch = 28f * dp
        closeTouch.set(cx - touch, cy - touch, cx + touch, cy + touch)
    }

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (closeTouch.contains(e.x, e.y)) onCloseTapped() else performClick()
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            if (!closeTouch.contains(e.x, e.y)) onLongPressed()
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event)

    override fun performClick(): Boolean {
        super.performClick()
        onScreenTapped()
        return true
    }
}
