package com.nexus.agent.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.agent.R

/**
 * Кастомная View для отображения системной статистики агента.
 * Показывает: имя модели, использованные токены, latency, статус соединения.
 * Поддерживает анимированные переходы и градиентные индикаторы.
 */
class StatsBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class Status {
        IDLE,           // Ожидание
        CONNECTING,     // Подключение
        STREAMING,      // Получение потокового ответа
        PROCESSING,     // Обработка
        ERROR,          // Ошибка
        OFFLINE         // Нет сети
    }

    // Views
    private lateinit var tvModelName: TextView
    private lateinit var tvTokens: TextView
    private lateinit var tvLatency: TextView
    private lateinit var tvStatus: TextView
    private lateinit var statusIndicator: View
    private lateinit var progressBar: View

    // Paint для кастомной отрисовки фона
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cornerRadius by lazy { dpToPx(12).toFloat() }
    private val glowRect = RectF()

    // State
    private var currentStatus = Status.IDLE
    private var targetTokens = 0
    private var displayedTokens = 0
    private var targetLatency = 0L
    private var displayedLatency = 0L
    private var tokenAnimator: ValueAnimator? = null
    private var latencyAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    // Colors
    private val colorIdle by lazy { ContextCompat.getColor(context, R.color.neon_cyan) }
    private val colorConnecting by lazy { ContextCompat.getColor(context, R.color.neon_yellow) }
    private val colorStreaming by lazy { ContextCompat.getColor(context, R.color.neon_green) }
    private val colorProcessing by lazy { ContextCompat.getColor(context, R.color.neon_purple) }
    private val colorError by lazy { ContextCompat.getColor(context, R.color.neon_red) }
    private val colorOffline by lazy { ContextCompat.getColor(context, R.color.text_disabled) }
    private val colorBackground by lazy { ContextCompat.getColor(context, R.color.bg_card_dark) }

    init {
        initView()
        startPulseAnimation()
    }

    private fun initView() {
        LayoutInflater.from(context).inflate(R.layout.view_stats_bar, this, true)

        tvModelName = findViewById(R.id.tvModelName)
        tvTokens = findViewById(R.id.tvTokens)
        tvLatency = findViewById(R.id.tvLatency)
        tvStatus = findViewById(R.id.tvStatus)
        statusIndicator = findViewById(R.id.statusIndicator)
        progressBar = findViewById(R.id.progressBar)

        setWillNotDraw(false)
        elevation = dpToPx(4).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        // Background with gradient
        val gradient = LinearGradient(
            0f, 0f, width, 0f,
            intArrayOf(
                colorBackground,
                adjustAlpha(getStatusColor(), 0.1f),
                colorBackground
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        backgroundPaint.shader = gradient
        canvas.drawRoundRect(0f, 0f, width, height, cornerRadius, cornerRadius, backgroundPaint)

        // Glow effect at bottom
        val glowHeight = dpToPx(2).toFloat()
        glowPaint.color = getStatusColor()
        glowPaint.alpha = (255 * 0.6f).toInt()
        glowRect.set(0f, height - glowHeight, width, height)
        canvas.drawRoundRect(glowRect, cornerRadius / 2, cornerRadius / 2, glowPaint)
    }

    /**
     * Обновляет все показатели статистики.
     */
    fun updateStats(
        modelName: String? = null,
        tokensUsed: Int? = null,
        latencyMs: Long? = null,
        status: Status? = null
    ) {
        modelName?.let { tvModelName.text = it }
        
        tokensUsed?.let { newTokens ->
            animateTokens(displayedTokens, newTokens)
            targetTokens = newTokens
        }
        
        latencyMs?.let { newLatency ->
            animateLatency(displayedLatency, newLatency)
            targetLatency = newLatency
        }
        
        status?.let { newStatus ->
            if (currentStatus != newStatus) {
                currentStatus = newStatus
                updateStatusUI()
            }
        }
    }

    /**
     * Инкрементирует счётчик токенов (для стриминга).
     */
    fun addTokens(count: Int) {
        targetTokens += count
        animateTokens(displayedTokens, targetTokens)
    }

    /**
     * Устанавливает latency для текущего запроса.
     */
    fun setLatency(ms: Long) {
        targetLatency = ms
        animateLatency(displayedLatency, ms)
    }

    private fun animateTokens(from: Int, to: Int) {
        tokenAnimator?.cancel()
        tokenAnimator = ValueAnimator.ofInt(from, to).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                displayedTokens = animator.animatedValue as Int
                tvTokens.text = formatTokens(displayedTokens)
            }
            start()
        }
    }

    private fun animateLatency(from: Long, to: Long) {
        latencyAnimator?.cancel()
        latencyAnimator = ValueAnimator.ofFloat(from.toFloat(), to.toFloat()).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                displayedLatency = (animator.animatedValue as Float).toLong()
                tvLatency.text = formatLatency(displayedLatency)
            }
            start()
        }
    }

    private fun updateStatusUI() {
        val color = getStatusColor()
        val statusText = getStatusText()
        val isActive = currentStatus == Status.STREAMING || currentStatus == Status.PROCESSING

        tvStatus.text = statusText
        tvStatus.setTextColor(color)
        statusIndicator.setBackgroundColor(color)

        // Animate status indicator
        statusIndicator.animate()
            .scaleX(if (isActive) 1.2f else 1f)
            .scaleY(if (isActive) 1.2f else 1f)
            .setDuration(200)
            .start()

        // Show/hide progress bar
        progressBar.visibility = if (isActive) View.VISIBLE else View.GONE
        
        // Redraw for glow effect
        invalidate()

        // Update pulse animation speed based on status
        updatePulseAnimation()
    }

    private fun startPulseAnimation() {
        pulseAnimator = ValueAnimator.ofFloat(0.4f, 1f, 0.4f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val alpha = animator.animatedValue as Float
                statusIndicator.alpha = alpha
            }
            start()
        }
    }

    private fun updatePulseAnimation() {
        pulseAnimator?.cancel()
        val duration = when (currentStatus) {
            Status.STREAMING -> 500L
            Status.PROCESSING -> 800L
            Status.CONNECTING -> 1000L
            else -> 2000L
        }
        pulseAnimator = ValueAnimator.ofFloat(0.4f, 1f, 0.4f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val alpha = animator.animatedValue as Float
                statusIndicator.alpha = alpha
            }
            start()
        }
    }

    private fun getStatusColor(): Int {
        return when (currentStatus) {
            Status.IDLE -> colorIdle
            Status.CONNECTING -> colorConnecting
            Status.STREAMING -> colorStreaming
            Status.PROCESSING -> colorProcessing
            Status.ERROR -> colorError
            Status.OFFLINE -> colorOffline
        }
    }

    private fun getStatusText(): String {
        return when (currentStatus) {
            Status.IDLE -> "Ready"
            Status.CONNECTING -> "Connecting..."
            Status.STREAMING -> "Streaming"
            Status.PROCESSING -> "Thinking..."
            Status.ERROR -> "Error"
            Status.OFFLINE -> "Offline"
        }
    }

    private fun formatTokens(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        } + " tokens"
    }

    private fun formatLatency(ms: Long): String {
        return when {
            ms < 1000 -> "${ms}ms"
            else -> String.format("%.1fs", ms / 1000.0)
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (android.graphics.Color.alpha(color) * factor).toInt()
        val red = android.graphics.Color.red(color)
        val green = android.graphics.Color.green(color)
        val blue = android.graphics.Color.blue(color)
        return android.graphics.Color.argb(alpha, red, green, blue)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        tokenAnimator?.cancel()
        latencyAnimator?.cancel()
        pulseAnimator?.cancel()
    }
}
