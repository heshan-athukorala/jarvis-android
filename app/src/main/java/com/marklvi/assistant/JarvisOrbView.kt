package com.marklvi.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Lightweight animated, 3D-inspired particle orb. It reacts to listening and TTS speaking states. */
class JarvisOrbView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    enum class Mode { IDLE, LISTENING, SPEAKING }
    private var mode = Mode.IDLE
    private var rotation = 0f
    private var pulse = 0f
    private var audioLevel = 0f
    private val points = (0 until 520).map { i ->
        val y = 1f - 2f * (i + 0.5f) / 520f
        val radius = sqrt(1f - y * y)
        val angle = i * 2.3999632f
        Point(cos(angle) * radius, y, sin(angle) * radius, Random(i * 97 + 11).nextFloat())
    }
    private data class Point(val x: Float, val y: Float, val z: Float, val brightness: Float)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.65f }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 16_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { rotation = it.animatedValue as Float * (Math.PI.toFloat() * 2f); pulse += 0.075f; invalidate() }
    }

    init { setLayerType(View.LAYER_TYPE_SOFTWARE, null) }
    fun setMode(newMode: Mode) { if (mode != newMode) { mode = newMode; invalidate() } }
    fun setAudioLevel(level: Float) { audioLevel = ((level + 2f) / 12f).coerceIn(0f, 1f); invalidate() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (!animator.isStarted) animator.start() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        val base = (minOf(width, height) * 0.36f).coerceAtLeast(10f)
        val intensity = when (mode) { Mode.IDLE -> 0.72f; Mode.LISTENING -> 1.35f; Mode.SPEAKING -> 1.6f }
        val beat = when (mode) { Mode.IDLE -> 1f + 0.018f * sin(pulse); Mode.LISTENING -> 1f + (0.035f + audioLevel * 0.11f) * sin(pulse * 1.8f); Mode.SPEAKING -> 1f + 0.09f * sin(pulse * 2.5f) }
        val r = base * beat
        glowPaint.shader = RadialGradient(cx, cy, r * 1.18f,
            intArrayOf(Color.argb((35 * intensity).toInt().coerceIn(0, 255), 30, 150, 255), Color.argb(12, 0, 100, 220), Color.TRANSPARENT),
            floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 1.18f, glowPaint)
        glowPaint.shader = null

        val projected = ArrayList<FloatArray>(points.size)
        for (p in points) {
            val x = p.x * cos(rotation) - p.z * sin(rotation)
            val z = p.x * sin(rotation) + p.z * cos(rotation)
            val breathe = if (mode == Mode.SPEAKING) 1f + 0.07f * sin(pulse * 2f + p.brightness * 8f) else 1f
            val perspective = 2.7f / (2.7f - z * 0.45f)
            projected.add(floatArrayOf(cx + x * r * perspective * breathe, cy + p.y * r * perspective * breathe, z, p.brightness))
        }
        // Thin connective strands give the sphere a dimensional neural-network look.
        linePaint.color = Color.argb((20 * intensity).toInt().coerceIn(0, 255), 45, 160, 255)
        for (i in projected.indices step 5) {
            val a = projected[i]
            val b = projected[(i + 17) % projected.size]
            if (a[2] > -0.25f && b[2] > -0.25f) canvas.drawLine(a[0], a[1], b[0], b[1], linePaint)
        }
        projected.sortedBy { it[2] }.forEach { p ->
            val front = ((p[2] + 1f) / 2f).coerceIn(0f, 1f)
            val radius = (0.65f + front * 1.45f) * (if (mode == Mode.SPEAKING) 1.18f else 1f)
            val alpha = ((55 + front * 185) * intensity * (0.45f + p[3] * 0.65f)).toInt().coerceIn(0, 255)
            particlePaint.color = Color.argb(alpha, 105, 210, 255)
            canvas.drawCircle(p[0], p[1], radius, particlePaint)
        }
        val coreRadius = when (mode) { Mode.IDLE -> 5f; Mode.LISTENING -> 7f + (2f + audioLevel * 4f) * sin(pulse * 2f); Mode.SPEAKING -> 8f + 3f * sin(pulse * 2.5f) }
        corePaint.shader = RadialGradient(cx, cy, coreRadius * 4f,
            intArrayOf(Color.WHITE, Color.rgb(90, 210, 255), Color.argb(0, 0, 110, 255)), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, coreRadius * 4f, corePaint)
        corePaint.shader = null
        canvas.drawCircle(cx, cy, coreRadius, particlePaint.apply { color = Color.WHITE; alpha = 255 })
    }
}
