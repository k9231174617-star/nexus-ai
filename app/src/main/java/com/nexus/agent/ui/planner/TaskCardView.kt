package com.nexus.agent.ui.planner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.agent.R
import com.nexus.agent.core.planner.TaskModel
import com.nexus.agent.ui.common.NeonToggleView

/**
 * Custom view for displaying a single task card in the planner.
 * Supports status indicators, priority badges, and drag-and-drop visuals.
 */
class TaskCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val titleText: TextView
    private val descriptionText: TextView
    private val priorityBadge: TextView
    private val statusIndicator: NeonToggleView
    private val progressText: TextView
    private val subtaskCountText: TextView

    private var task: TaskModel? = null
    private var onTaskClickListener: ((TaskModel) -> Unit)? = null
    private var onTaskLongClickListener: ((TaskModel) -> Unit)? = null

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val cornerRadius = 16f
    private val cardRect = RectF()

    init {
        orientation = VERTICAL
        setPadding(24, 20, 24, 20)
        elevation = 8f

        LayoutInflater.from(context).inflate(R.layout.item_task, this, true)

        titleText = findViewById(R.id.task_title)
        descriptionText = findViewById(R.id.task_description)
        priorityBadge = findViewById(R.id.task_priority_badge)
        statusIndicator = findViewById(R.id.task_status_indicator)
        progressText = findViewById(R.id.task_progress)
        subtaskCountText = findViewById(R.id.task_subtask_count)

        setOnClickListener {
            task?.let { onTaskClickListener?.invoke(it) }
        }

        setOnLongClickListener {
            task?.let {
                onTaskLongClickListener?.invoke(it)
                true
            } ?: false
        }

        updateThemeColors()
    }

    fun bind(task: TaskModel) {
        this.task = task

        titleText.text = task.title
        descriptionText.text = task.description.takeIf { it.isNotBlank() } ?: "No description"
        descriptionText.visibility = if (task.description.isBlank()) GONE else VISIBLE

        // Priority badge
        priorityBadge.text = task.priority.name
        priorityBadge.setBackgroundColor(getPriorityColor(task.priority))
        priorityBadge.setTextColor(ContextCompat.getColor(context, R.color.neon_white))

        // Status indicator
        statusIndicator.isChecked = task.status == TaskModel.Status.COMPLETED

        // Progress
        val progressPercent = if (task.subtasks.isNotEmpty()) {
            (task.subtasks.count { it.isCompleted } * 100 / task.subtasks.size)
        } else {
            if (task.status == TaskModel.Status.COMPLETED) 100 else 0
        }
        progressText.text = "$progressPercent%"

        // Subtask count
        if (task.subtasks.isNotEmpty()) {
            subtaskCountText.text = "${task.subtasks.count { it.isCompleted }}/${task.subtasks.size}"
            subtaskCountText.visibility = VISIBLE
        } else {
            subtaskCountText.visibility = GONE
        }

        // Visual feedback for completed tasks
        alpha = if (task.status == TaskModel.Status.COMPLETED) 0.6f else 1.0f

        invalidate()
    }

    fun setOnTaskClickListener(listener: (TaskModel) -> Unit) {
        this.onTaskClickListener = listener
    }

    fun setOnTaskLongClickListener(listener: (TaskModel) -> Unit) {
        this.onTaskLongClickListener = listener
    }

    fun getTask(): TaskModel? = task

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()

        cardRect.set(4f, 4f, width - 4f, height - 4f)

        // Shadow
        shadowPaint.color = ContextCompat.getColor(context, R.color.neon_shadow)
        canvas.drawRoundRect(
            cardRect.left + 2f, cardRect.top + 4f,
            cardRect.right + 2f, cardRect.bottom + 4f,
            cornerRadius, cornerRadius, shadowPaint
        )

        // Card background
        val bgColor = when (task?.status) {
            TaskModel.Status.COMPLETED -> R.color.card_bg_completed
            TaskModel.Status.IN_PROGRESS -> R.color.card_bg_active
            TaskModel.Status.BLOCKED -> R.color.card_bg_blocked
            else -> R.color.card_bg_default
        }
        cardPaint.color = ContextCompat.getColor(context, bgColor)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardPaint)

        // Neon border based on priority
        val borderColor = when (task?.priority) {
            TaskModel.Priority.CRITICAL -> R.color.neon_red
            TaskModel.Priority.HIGH -> R.color.neon_orange
            TaskModel.Priority.MEDIUM -> R.color.neon_yellow
            TaskModel.Priority.LOW -> R.color.neon_green
            else -> R.color.neon_blue
        }
        strokePaint.color = ContextCompat.getColor(context, borderColor)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, strokePaint)

        // Left accent bar
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokePaint.color
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            cardRect.left, cardRect.top + 12f,
            cardRect.left + 4f, cardRect.bottom - 12f,
            2f, 2f, accentPaint
        )

        super.onDraw(canvas)
    }

    override fun dispatchDraw(canvas: Canvas) {
        onDraw(canvas)
        super.dispatchDraw(canvas)
    }

    private fun getPriorityColor(priority: TaskModel.Priority): Int {
        return ContextCompat.getColor(context, when (priority) {
            TaskModel.Priority.CRITICAL -> R.color.priority_critical
            TaskModel.Priority.HIGH -> R.color.priority_high
            TaskModel.Priority.MEDIUM -> R.color.priority_medium
            TaskModel.Priority.LOW -> R.color.priority_low
        })
    }

    private fun updateThemeColors() {
        titleText.setTextColor(ContextCompat.getColor(context, R.color.neon_white))
        descriptionText.setTextColor(ContextCompat.getColor(context, R.color.neon_gray))
        progressText.setTextColor(ContextCompat.getColor(context, R.color.neon_cyan))
    }

    /**
     * Visual state for drag-and-drop operations
     */
    fun setDragging(isDragging: Boolean) {
        if (isDragging) {
            alpha = 0.7f
            scaleX = 1.05f
            scaleY = 1.05f
            elevation = 16f
        } else {
            alpha = if (task?.status == TaskModel.Status.COMPLETED) 0.6f else 1.0f
            scaleX = 1.0f
            scaleY = 1.0f
            elevation = 8f
        }
        invalidate()
    }

    /**
     * Highlight this card (e.g., when selected)
     */
    fun setHighlighted(highlighted: Boolean) {
        val targetAlpha = if (highlighted) 255 else 0
        strokePaint.alpha = targetAlpha
        invalidate()
    }

    companion object {
        private const val TAG = "TaskCardView"
    }
}
