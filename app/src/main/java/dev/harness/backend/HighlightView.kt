package dev.harness.backend

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View

/**
 * A pulsing orange outline around [target] (screen pixels) with an optional label pill above or below it.
 * The window is full-screen and not touchable, so taps pass straight through to the app underneath.
 */
internal class HighlightView(
    context: Context,
    private val target: RectF,
    private val label: String?,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val orange = 0xFFFF6D00.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(0x33, 0xFF, 0x6D, 0x00) }
    private val halo = stroke(Color.WHITE, 12f)
    private val ring = stroke(orange, 7f)
    private val pillBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(0xF2, 0x20, 0x20, 0x20) }
    private val pillText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20f * resources.displayMetrics.scaledDensity
        typeface = Typeface.DEFAULT_BOLD
    }

    private var phase = 0f
    private val pulse = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 900
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    private fun stroke(argb: Int, widthDp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = widthDp * density
        color = argb
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pulse.start()
    }

    override fun onDetachedFromWindow() {
        pulse.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val grow = (6f + 4f * phase) * density
        val box = RectF(target).apply { inset(-grow, -grow) }
        val corner = 16f * density
        canvas.drawRoundRect(box, corner, corner, fill)
        canvas.drawRoundRect(box, corner, corner, halo)
        canvas.drawRoundRect(box, corner, corner, ring)
        label?.takeIf { it.isNotBlank() }?.let { drawLabel(canvas, box, it) }
    }

    private fun drawLabel(canvas: Canvas, box: RectF, text: String) {
        val margin = 12f * density
        val gap = 14f * density
        val height = 52f * density
        val text = TextUtils.ellipsize(text, pillText, width - 2 * margin - 32f * density, TextUtils.TruncateAt.END).toString()
        val pillWidth = pillText.measureText(text) + 32f * density

        val left = (box.centerX() - pillWidth / 2).coerceIn(margin, maxOf(margin, width - pillWidth - margin))
        val above = box.top - gap - height
        val top = (if (above >= margin) above else box.bottom + gap).coerceAtMost(this.height - height - margin)

        val pill = RectF(left, top, left + pillWidth, top + height)
        canvas.drawRoundRect(pill, height / 2, height / 2, pillBackground)
        val baseline = pill.centerY() - (pillText.descent() + pillText.ascent()) / 2
        canvas.drawText(text, pill.left + 16f * density, baseline, pillText)
    }
}
