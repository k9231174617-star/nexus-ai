package com.nexus.agent.ui.common

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.nexus.agent.R

/**
 * Animated typing indicator with three bouncing dots (neon style)
 */
class TypingIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.neon_cyan)
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.neon_cyan)
        alpha = 60
        maskFilter = android.graphics.BlurMaskFilter(12f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }

    private val dotCount = 3
    private val dotRadius = 6f
    private val dotSpacing = 20f
    private val bounceHeight = 15f

    private val animators = mutableListOf<ValueAnimator>()
    private val dotOffsets = FloatArray(dotCount) { 0f }

    var isAnimating = false
        set(value) {
            if (field == value) return
            field = value
            if (value) startAnimation() else stopAnimation()
        }

    init {
        // Start animation by default if visible
        if (visibility == VISIBLE) {
            isAnimating = true
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE && !isAnimating) {
            isAnimating = true
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        isAnimating = visibility == VISIBLE
    }

    private fun startAnimation() {
        stopAnimation()
        
        repeat(dotCount) { index ->
            val animator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
                duration = 600
                startDelay = (index * 150).toLong()
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                
                addUpdateListener { animation ->
                    dotOffsets[index] = animation.animatedValue as Float
                    invalidate()
                }
            }
            animators.add(animator)
            animator.start()
        }
    }

    private fun stopAnimation() {
        animators.forEach { it.cancel() }
        animators.clear()
        dotOffsets.fill(0f)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = ((dotCount * dotRadius * 2) + ((dotCount - 1) * dotSpacing) + paddingLeft + paddingRight).toInt()
        val desiredHeight = ((dotRadius * 2) + bounceHeight + paddingTop + paddingBottom).toInt()
        
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val totalWidth = (dotCount * dotRadius * 2) + ((dotCount - 1) * dotSpacing)
        val startX = (width - totalWidth) / 2 + dotRadius
        val baseY = height / 2f + dotRadius

        repeat(dotCount) { i ->
            val x = startX + i * (dotRadius * 2 + dotSpacing)
            val y = baseY - (dotOffsets[i] * bounceHeight)
            
            // Glow
            canvas.drawCircle(x, y, dotRadius + 4, glowPaint)
            // Dot
            canvas.drawCircle(x, y, dotRadius, dotPaint)
        }
    }

    fun setDotColor(colorRes: Int) {
        val color = ContextCompat.getColor(context, colorRes)
        dotPaint.color = color
        glowPaint.color = color
        invalidate()
    }
}
