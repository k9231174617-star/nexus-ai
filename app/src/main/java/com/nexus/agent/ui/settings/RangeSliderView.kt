package com.nexus.agent.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.withStyledAttributes
import com.nexus.agent.R
import kotlin.math.max
import kotlin.math.min

/**
 * Custom dual-thumb range slider with neon styling for Nexus AI
 */
class RangeSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Configuration
    var minValue: Float = 0f
        set(value) {
            field = value
            ensureValuesValid()
            invalidate()
        }
    
    var maxValue: Float = 100f
        set(value) {
            field = value
            ensureValuesValid()
            invalidate()
        }
    
    var currentMin: Float = 0f
        set(value) {
            field = value.coerceIn(minValue, currentMax)
            onValueChangeListener?.invoke(currentMin, currentMax)
            invalidate()
        }
    
    var currentMax: Float = 100f
        set(value) {
            field = value.coerceIn(currentMin, maxValue)
            onValueChangeListener?.invoke(currentMin, currentMax)
            invalidate()
        }

    var stepSize: Float = 1f
    var showLabels: Boolean = true
    var labelFormatter: ((Float) -> String)? = null

    var onValueChangeListener: ((Float, Float) -> Unit)? = null

    // Neon styling
    private var trackColor: Int = Color.parseColor("#2A2A3E")
    private var activeTrackColor: Int = Color.parseColor("#00F0FF")
    private var thumbColor: Int = Color.parseColor("#00F0FF")
    private var thumbGlowColor: Int = Color.parseColor("#00F0FF")
    private var labelColor: Int = Color.parseColor("#B0B0C0")
    
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = trackColor
    }
    
    private val activeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = activeTrackColor
    }
    
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = thumbColor
    }
    
    private val thumbGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = thumbGlowColor
        alpha = 60
    }
    
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    // Touch handling
    private var activeThumb: Thumb? = null
    private val thumbRadius = 24f
    private val thumbTouchPadding = 40f
    private val trackHeight = 8f

    private enum class Thumb { MIN, MAX }

    private val trackRect = RectF()
    private val activeTrackRect = RectF()

    init {
        context.withStyledAttributes(attrs, R.styleable.RangeSliderView) {
            minValue = getFloat(R.styleable.RangeSliderView_minValue, 0f)
            maxValue = getFloat(R.styleable.RangeSliderView_maxValue, 100f)
            currentMin = getFloat(R.styleable.RangeSliderView_currentMin, minValue)
            currentMax = getFloat(R.styleable.RangeSliderView_currentMax, maxValue)
            stepSize = getFloat(R.styleable.RangeSliderView_stepSize, 1f)
            showLabels = getBoolean(R.styleable.RangeSliderView_showLabels, true)
            
            trackColor = getColor(R.styleable.RangeSliderView_trackColor, trackColor)
            activeTrackColor = getColor(R.styleable.RangeSliderView_activeTrackColor, activeTrackColor)
            thumbColor = getColor(R.styleable.RangeSliderView_thumbColor, thumbColor)
            thumbGlowColor = getColor(R.styleable.RangeSliderView_thumbGlowColor, thumbGlowColor)
            labelColor = getColor(R.styleable.RangeSliderView_labelColor, labelColor)
        }
        
        trackPaint.color = trackColor
        activeTrackPaint.color = activeTrackColor
        thumbPaint.color = thumbColor
        thumbGlowPaint.color = thumbGlowColor
        labelPaint.color = labelColor
        
        // Enable hardware acceleration glow
        thumbGlowPaint.maskFilter = android.graphics.BlurMaskFilter(20f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }

    private fun ensureValuesValid() {
        currentMin = currentMin.coerceIn(minValue, currentMax)
        currentMax = currentMax.coerceIn(currentMin, maxValue)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = if (showLabels) 120 else 80
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(desiredHeight, heightSize)
            else -> desiredHeight
        }
        
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            height
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val paddingLeft = paddingLeft + thumbRadius
        val paddingRight = paddingRight + thumbRadius
        val availableWidth = width - paddingLeft - paddingRight
        val centerY = height / 2f

        // Draw background track
        trackRect.set(
            paddingLeft,
            centerY - trackHeight / 2,
            width - paddingRight,
            centerY + trackHeight / 2
        )
        canvas.drawRoundRect(trackRect, trackHeight / 2, trackHeight / 2, trackPaint)

        // Draw active track
        val minX = paddingLeft + ((currentMin - minValue) / (maxValue - minValue)) * availableWidth
        val maxX = paddingLeft + ((currentMax - minValue) / (maxValue - minValue)) * availableWidth
        
        activeTrackRect.set(minX, centerY - trackHeight / 2, maxX, centerY + trackHeight / 2)
        canvas.drawRoundRect(activeTrackRect, trackHeight / 2, trackHeight / 2, activeTrackPaint)

        // Draw glow under active track (neon effect)
        val glowPaint = Paint(activeTrackPaint).apply { 
            alpha = 40 
            maskFilter = android.graphics.BlurMaskFilter(15f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(
            activeTrackRect.left, activeTrackRect.top - 4,
            activeTrackRect.right, activeTrackRect.bottom + 4,
            trackHeight, trackHeight, glowPaint
        )

        // Draw thumbs with glow
        drawThumb(canvas, minX, centerY, true)
        drawThumb(canvas, maxX, centerY, false)

        // Draw labels
        if (showLabels) {
            val minLabel = labelFormatter?.invoke(currentMin) ?: "%.0f".format(currentMin)
            val maxLabel = labelFormatter?.invoke(currentMax) ?: "%.0f".format(currentMax)
            
            canvas.drawText(minLabel, minX, centerY + thumbRadius + 35, labelPaint)
            canvas.drawText(maxLabel, maxX, centerY + thumbRadius + 35, labelPaint)
            
            // Draw min/max bounds labels
            labelPaint.alpha = 128
            canvas.drawText(
                labelFormatter?.invoke(minValue) ?: "%.0f".format(minValue),
                paddingLeft, centerY - thumbRadius - 15, labelPaint
            )
            canvas.drawText(
                labelFormatter?.invoke(maxValue) ?: "%.0f".format(maxValue),
                width - paddingRight, centerY - thumbRadius - 15, labelPaint
            )
            labelPaint.alpha = 255
        }
    }

    private fun drawThumb(canvas: Canvas, x: Float, y: Float, isMin: Boolean) {
        // Glow
        canvas.drawCircle(x, y, thumbRadius + 12, thumbGlowPaint)
        // Thumb
        canvas.drawCircle(x, y, thumbRadius, thumbPaint)
        // Inner highlight
        val highlightPaint = Paint(thumbPaint).apply { 
            color = Color.WHITE 
            alpha = 100 
        }
        canvas.drawCircle(x - 4, y - 4, thumbRadius / 3, highlightPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeThumb = detectTouchedThumb(event.x, event.y)
                return activeThumb != null
            }
            MotionEvent.ACTION_MOVE -> {
                activeThumb?.let { thumb ->
                    val paddingLeft = paddingLeft + thumbRadius
                    val paddingRight = paddingRight + thumbRadius
                    val availableWidth = width - paddingLeft - paddingRight
                    
                    val rawValue = minValue + ((event.x - paddingLeft) / availableWidth) * (maxValue - minValue)
                    val steppedValue = (rawValue / stepSize).roundToNearest() * stepSize
                    
                    when (thumb) {
                        Thumb.MIN -> {
                            currentMin = steppedValue.coerceIn(minValue, currentMax - stepSize)
                        }
                        Thumb.MAX -> {
                            currentMax = steppedValue.coerceIn(currentMin + stepSize, maxValue)
                        }
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeThumb = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun detectTouchedThumb(x: Float, y: Float): Thumb? {
        val paddingLeft = paddingLeft + thumbRadius
        val paddingRight = paddingRight + thumbRadius
        val availableWidth = width - paddingLeft - paddingRight
        val centerY = height / 2f

        val minX = paddingLeft + ((currentMin - minValue) / (maxValue - minValue)) * availableWidth
        val maxX = paddingLeft + ((currentMax - minValue) / (maxValue - minValue)) * availableWidth

        val distToMin = kotlin.math.hypot(x - minX, y - centerY)
        val distToMax = kotlin.math.hypot(x - maxX, y - centerY)

        return when {
            distToMin <= thumbRadius + thumbTouchPadding -> Thumb.MIN
            distToMax <= thumbRadius + thumbTouchPadding -> Thumb.MAX
            else -> null
        }
    }

    private fun Float.roundToNearest(): Float = kotlin.math.round(this)

    fun setValues(min: Float, max: Float) {
        currentMin = min
        currentMax = max
    }

    fun getValues(): Pair<Float, Float> = Pair(currentMin, currentMax)
}
