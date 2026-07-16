package com.nexus.agent.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.withStyledAttributes
import com.nexus.agent.R
import kotlin.math.roundToInt

/**
 * Custom neon-styled toggle switch with glow effects
 */
class NeonToggleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Colors
    private var trackOffColor: Int = Color.parseColor("#2A2A3E")
    private var trackOnColor: Int = Color.parseColor("#00F0FF")
    private var thumbOffColor: Int = Color.parseColor("#6B6B8C")
    private var thumbOnColor: Int = Color.parseColor("#FFFFFF")
    private var glowColor: Int = Color.parseColor("#00F0FF")

    // Dimensions
    private val trackHeightRatio = 0.6f
    private val thumbSizeRatio = 0.8f
    private val cornerRadiusRatio = 0.5f

    // Animation
    private var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private var animator: ValueAnimator? = null
    private val animationDuration = 250L

    // State
    var isChecked: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            animateToggle(value)
            onCheckedChangeListener?.invoke(value)
        }

    var onCheckedChangeListener: ((Boolean) -> Unit)? = null

    // Paints
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = android.graphics.BlurMaskFilter(15f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }

    private val trackRect = RectF()
    private val thumbRect = RectF()

    init {
        context.withStyledAttributes(attrs, R.styleable.NeonToggleView) {
            isChecked = getBoolean(R.styleable.NeonToggleView_checked, false)
            trackOffColor = getColor(R.styleable.NeonToggleView_trackOffColor, trackOffColor)
            trackOnColor = getColor(R.styleable.NeonToggleView_trackOnColor, trackOnColor)
            thumbOffColor = getColor(R.styleable.NeonToggleView_thumbOffColor, thumbOffColor)
            thumbOnColor = getColor(R.styleable.NeonToggleView_thumbOnColor, thumbOnColor)
            glowColor = getColor(R.styleable.NeonToggleView_glowColor, glowColor)
        }
        
        progress = if (isChecked) 1f else 0f
        
        // Enable click
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (56 * resources.displayMetrics.density).toInt()
        val desiredHeight = (32 * resources.displayMetrics.density).toInt()
        
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val padding = height * 0.1f

        // Track dimensions
        val trackHeight = height * trackHeightRatio
        val trackTop = (height - trackHeight) / 2
        val trackRadius = trackHeight * cornerRadiusRatio

        trackRect.set(padding, trackTop, width - padding, trackTop + trackHeight)

        // Interpolate track color
        val currentTrackColor = interpolateColor(trackOffColor, trackOnColor, progress)
        trackPaint.color = currentTrackColor

        // Draw track
        canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackPaint)

        // Draw glow when on
        if (progress > 0.1f) {
            glowPaint.color = glowColor
            glowPaint.alpha = (progress * 40).roundToInt()
            canvas.drawRoundRect(
                trackRect.left - 4, trackRect.top - 4,
                trackRect.right + 4, trackRect.bottom + 4,
                trackRadius, trackRadius, glowPaint
            )
        }

        // Thumb dimensions
        val thumbSize = height * thumbSizeRatio
        val thumbRadius = thumbSize / 2
        val thumbTravelDistance = trackRect.width() - thumbSize - (padding * 2)
        val thumbX = trackRect.left + padding + (thumbTravelDistance * progress)
        val thumbY = height / 2

        // Interpolate thumb color
        val currentThumbColor = interpolateColor(thumbOffColor, thumbOnColor, progress)
        thumbPaint.color = currentThumbColor

        // Draw thumb glow
        if (progress > 0.1f) {
            glowPaint.color = glowColor
            glowPaint.alpha = (progress * 80).roundToInt()
            canvas.drawCircle(thumbX, thumbY, thumbRadius + 6, glowPaint)
        }

        // Draw thumb
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)

        // Thumb highlight
        val highlightPaint = Paint(thumbPaint).apply {
            color = Color.WHITE
            alpha = (progress * 100).roundToInt()
        }
        canvas.drawCircle(thumbX - 2, thumbY - 2, thumbRadius * 0.3f, highlightPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Visual feedback
                animatePress(true)
                return true
            }
            MotionEvent.ACTION_UP -> {
                animatePress(false)
                if (event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()) {
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                animatePress(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        isChecked = !isChecked
        return true
    }

    private fun animateToggle(targetChecked: Boolean) {
        animator?.cancel()
        
        val targetProgress = if (targetChecked) 1f else 0f
        
        animator = ValueAnimator.ofFloat(progress, targetProgress).apply {
            duration = animationDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener { progress = it.animatedValue as Float }
            start()
        }
    }

    private fun animatePress(pressed: Boolean) {
        animate()
            .scaleX(if (pressed) 0.95f else 1f)
            .scaleY(if (pressed) 0.95f else 1f)
            .setDuration(100)
            .start()
    }

    private fun interpolateColor(from: Int, to: Int, fraction: Float): Int {
        val a1 = Color.alpha(from)
        val r1 = Color.red(from)
        val g1 = Color.green(from)
        val b1 = Color.blue(from)

        val a2 = Color.alpha(to)
        val r2 = Color.red(to)
        val g2 = Color.green(to)
        val b2 = Color.blue(to)

        return Color.argb(
            (a1 + (a2 - a1) * fraction).roundToInt(),
            (r1 + (r2 - r1) * fraction).roundToInt(),
            (g1 + (g2 - g1) * fraction).roundToInt(),
            (b1 + (b2 - b1) * fraction).roundToInt()
        )
    }

    fun setChecked(checked: Boolean, animate: Boolean = true) {
        if (!animate) {
            animator?.cancel()
            progress = if (checked) 1f else 0f
            field = checked // bypass setter
            invalidate()
        } else {
            isChecked = checked
        }
    }

    fun toggle() {
        isChecked = !isChecked
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
