package com.nexus.agent.ui.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.agent.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Panel for recording, displaying, and replaying user actions in the browser.
 * Supports action sequences export for automation and AI training.
 */
class ActionRecorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val actionList: LinearLayout
    private val scrollView: ScrollView
    private val statusText: TextView
    private val countText: TextView

    private val actions = CopyOnWriteArrayList<WebContentView.RecordedAction>()
    private var isRecording = false
    private var startTime: Long = 0

    private val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    // Colors
    private val colorClick: Int
    private val colorLongClick: Int
    private val colorScroll: Int
    private val colorInput: Int
    private val colorNavigate: Int

    private var onActionClickListener: ((WebContentView.RecordedAction) -> Unit)? = null
    private var onReplayListener: ((List<WebContentView.RecordedAction>) -> Unit)? = null

    init {
        colorClick = ContextCompat.getColor(context, R.color.action_click)
        colorLongClick = ContextCompat.getColor(context, R.color.action_long_click)
        colorScroll = ContextCompat.getColor(context, R.color.action_scroll)
        colorInput = ContextCompat.getColor(context, R.color.action_input)
        colorNavigate = ContextCompat.getColor(context, R.color.action_navigate)

        setBackgroundColor(ContextCompat.getColor(context, R.color.recorder_bg))
        setPadding(12, 12, 12, 12)

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Header
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
        }

        statusText = TextView(context).apply {
            text = "IDLE"
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.neon_gray))
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        countText = TextView(context).apply {
            text = "0 actions"
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.neon_cyan))
        }

        header.addView(statusText)
        header.addView(countText)
        layout.addView(header)

        // Action list
        scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        actionList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        scrollView.addView(actionList)
        layout.addView(scrollView)

        addView(layout)
    }

    fun startRecording() {
        isRecording = true
        startTime = System.currentTimeMillis()
        actions.clear()
        actionList.removeAllViews()
        statusText.text = "● REC"
        statusText.setTextColor(ContextCompat.getColor(context, R.color.neon_red))
        countText.text = "0 actions"
        invalidate()
    }

    fun stopRecording() {
        isRecording = false
        statusText.text = "STOPPED"
        statusText.setTextColor(ContextCompat.getColor(context, R.color.neon_yellow))
        countText.text = "${actions.size} actions"
        invalidate()
    }

    fun addAction(action: WebContentView.RecordedAction) {
        if (!isRecording) return

        actions.add(action)
        addActionView(action)
        countText.text = "${actions.size} actions"

        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun addActionView(action: WebContentView.RecordedAction) {
        val itemView = ActionItemView(context, action)
        itemView.setOnClickListener {
            onActionClickListener?.invoke(action)
        }
        actionList.addView(itemView)

        // Animate entrance
        itemView.alpha = 0f
        itemView.animate()
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun getRecordedActions(): List<WebContentView.RecordedAction> = actions.toList()

    fun clear() {
        actions.clear()
        actionList.removeAllViews()
        countText.text = "0 actions"
    }

    fun replay() {
        if (actions.isEmpty()) return
        onReplayListener?.invoke(actions.toList())
    }

    fun exportAsScript(): String {
        val sb = StringBuilder()
        sb.appendLine("// Nexus AI - Recorded Actions")
        sb.appendLine("// Generated: ${Date()}")
        sb.appendLine("// Total actions: ${actions.size}")
        sb.appendLine()

        actions.forEachIndexed { index, action ->
            val delay = if (index > 0) action.timestamp - actions[index - 1].timestamp else 0

            sb.appendLine("// Step ${index + 1}")
            sb.appendLine("// Delay: ${delay}ms")
            sb.appendLine(when (action.type) {
                WebContentView.ActionType.CLICK -> "await page.clickAt(${action.x.toInt()}, ${action.y.toInt()});"
                WebContentView.ActionType.LONG_CLICK -> "await page.longClickAt(${action.x.toInt()}, ${action.y.toInt()});"
                WebContentView.ActionType.SCROLL -> "await page.scrollTo(${action.x.toInt()}, ${action.y.toInt()});"
                WebContentView.ActionType.TEXT_INPUT -> "await page.type('${action.value?.replace("'", "\\'") ?: ""}');"
                WebContentView.ActionType.NAVIGATE -> "await page.navigate('${action.value ?: ""}');"
            })
            sb.appendLine()
        }

        return sb.toString()
    }

    fun setOnActionClickListener(listener: (WebContentView.RecordedAction) -> Unit) {
        onActionClickListener = listener
    }

    fun setOnReplayListener(listener: (List<WebContentView.RecordedAction>) -> Unit) {
        onReplayListener = listener
    }

    /**
     * Individual action item view
     */
    private inner class ActionItemView @JvmOverloads constructor(
        context: Context,
        private val action: WebContentView.RecordedAction,
        attrs: AttributeSet? = null
    ) : FrameLayout(context, attrs) {

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
        }
        private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            color = ContextCompat.getColor(context, R.color.neon_gray)
        }

        private val cornerRadius = 8f
        private val rect = RectF()

        init {
            setPadding(16, 12, 16, 12)
            isClickable = true
            isFocusable = true
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                (56 * resources.displayMetrics.density).toInt()
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            rect.set(4f, 4f, width - 4f, height - 4f)

            // Background
            bgPaint.color = ContextCompat.getColor(context, R.color.action_item_bg)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

            // Type indicator
            val indicatorColor = when (action.type) {
                WebContentView.ActionType.CLICK -> colorClick
                WebContentView.ActionType.LONG_CLICK -> colorLongClick
                WebContentView.ActionType.SCROLL -> colorScroll
                WebContentView.ActionType.TEXT_INPUT -> colorInput
                WebContentView.ActionType.NAVIGATE -> colorNavigate
            }
            indicatorPaint.color = indicatorColor
            canvas.drawRoundRect(
                rect.left, rect.top,
                rect.left + 4f, rect.bottom,
                cornerRadius, cornerRadius, indicatorPaint
            )

            // Action type text
            textPaint.color = ContextCompat.getColor(context, R.color.neon_white)
            val typeText = action.type.name.replace("_", " ")
            canvas.drawText(typeText, 24f, height / 2f + 8f, textPaint)

            // Details
            val details = when (action.type) {
                WebContentView.ActionType.CLICK,
                WebContentView.ActionType.LONG_CLICK -> "(${action.x.toInt()}, ${action.y.toInt()})"
                WebContentView.ActionType.SCROLL -> "→ (${action.x.toInt()}, ${action.y.toInt()})"
                WebContentView.ActionType.TEXT_INPUT -> action.value?.take(20) ?: ""
                WebContentView.ActionType.NAVIGATE -> action.value?.take(30) ?: ""
            }
            canvas.drawText(details, width - 200f, height / 2f + 8f, timePaint)

            // Timestamp
            val time = timestampFormat.format(Date(action.timestamp))
            canvas.drawText(time, width - 100f, height / 2f + 8f, timePaint)
        }

        override fun dispatchDraw(canvas: Canvas) {
            onDraw(canvas)
            super.dispatchDraw(canvas)
        }
    }

    companion object {
        private const val TAG = "ActionRecorderView"
    }
}
