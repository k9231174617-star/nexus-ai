package com.nexus.agent.ui.cli

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.nexus.agent.R

/**
 * RootBadgeView — animated status indicator showing root access state.
 * Displays shield icon with pulsing glow effect when root is active.
 */
class RootBadgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var isRootActive = false
    private var hasRootAccess = false
    private var pulseAnimator: ValueAnimator? = null
    private var pulseAlpha = 255

    private val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val shieldStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val badgeBounds = RectF()

    init {
        isClickable = true
        isFocusable = true
        setupBackground()
    }

    private fun setupBackground() {
        val drawable = GradientDrawable().apply {
            cornerRadius = 12f
            setColor(ContextCompat.getColor(context, R.color.root_badge_background))
        }
        background = drawable
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = 120
        val desiredHeight = 48

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(desiredWidth, widthSize)
            else -> desiredWidth
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val shieldSize = minOf(width, height) * 0.35f

        // Draw glow effect when root is active
        if (isRootActive) {
            drawGlowEffect(canvas, centerX, centerY, shieldSize)
        }

        // Draw shield icon
        drawShield(canvas, centerX - shieldSize - 4, centerY, shieldSize * 0.8f)

        // Draw status text
        val statusText = when {
            isRootActive -> "ROOT"
            hasRootAccess -> "SU"
            else -> "USER"
        }
        canvas.drawText(statusText, centerX + 8, centerY + 8, textPaint)
    }

    private fun drawGlowEffect(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val glowRadius = radius * 1.5f
        val alpha = (pulseAlpha * 0.3f).toInt()
        glowPaint.color = Color.argb(alpha, 255, 50, 50)
        canvas.drawCircle(cx - radius - 4, cy, glowRadius, glowPaint)
    }

    private fun drawShield(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val path = android.graphics.Path().apply {
            // Shield shape
            moveTo(cx, cy - size)
            lineTo(cx + size * 0.8f, cy - size * 0.3f)
            lineTo(cx + size * 0.8f, cy + size * 0.2f)
            quadTo(cx + size * 0.8f, cy + size * 0.8f, cx, cy + size)
            quadTo(cx - size * 0.8f, cy + size * 0.8f, cx - size * 0.8f, cy + size * 0.2f)
            lineTo(cx - size * 0.8f, cy - size * 0.3f)
            close()
        }

        val color = when {
            isRootActive -> ContextCompat.getColor(context, R.color.root_active)
            hasRootAccess -> ContextCompat.getColor(context, R.color.root_available)
            else -> ContextCompat.getColor(context, R.color.root_unavailable)
        }

        shieldPaint.color = color
        shieldStrokePaint.color = Color.argb(180, 255, 255, 255)

        canvas.drawPath(path, shieldPaint)
        canvas.drawPath(path, shieldStrokePaint)

        // Draw keyhole or lock icon inside shield
        if (isRootActive) {
            val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy - size * 0.1f, size * 0.15f, lockPaint)
            canvas.drawRect(cx - size * 0.1f, cy - size * 0.1f, cx + size * 0.1f, cy + size * 0.25f, lockPaint)
        }
    }

    fun setRootActive(active: Boolean) {
        if (isRootActive == active) return
        isRootActive = active

        if (active) {
            startPulseAnimation()
            textPaint.color = ContextCompat.getColor(context, R.color.root_active_text)
        } else {
            stopPulseAnimation()
            textPaint.color = Color.WHITE
        }
        invalidate()
    }

    fun setHasRoot(hasRoot: Boolean) {
        hasRootAccess = hasRoot
        invalidate()
    }

    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofInt(50, 255, 50).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                pulseAlpha = animator.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulseAlpha = 255
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopPulseAnimation()
    }

    fun getStatusText(): String {
        return when {
            isRootActive -> "Root mode active"
            hasRootAccess -> "Root available"
            else -> "User mode"
        }
    }

    companion object {
        private const val TAG = "RootBadgeView"
    }
}
