package com.nexus.agent.ui.planner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.nexus.agent.R
import com.nexus.agent.core.planner.TaskModel
import com.nexus.agent.core.planner.TopologicalSorter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive graph view for visualizing task dependencies as a directed acyclic graph (DAG).
 * Supports pan, zoom, node selection, and edge highlighting.
 */
class TaskGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Data
    private var tasks: List<TaskModel> = emptyList()
    private var edges: List<TaskEdge> = emptyList()
    private var nodePositions: Map<String, NodePosition> = emptyMap()

    // Drawing paints
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val nodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val edgeArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
    }

    // Viewport
    private var scaleFactor = 1.0f
    private var translateX = 0f
    private var translateY = 0f
    private val minScale = 0.3f
    private val maxScale = 3.0f

    // Node dimensions
    private val nodeWidth = 200f
    private val nodeHeight = 100f
    private val nodeCornerRadius = 12f
    private val horizontalSpacing = 80f
    private val verticalSpacing = 60f

    // Interaction
    private var selectedNodeId: String? = null
    private var onNodeClickListener: ((TaskModel) -> Unit)? = null
    private var onNodeLongClickListener: ((TaskModel) -> Unit)? = null
    private var highlightedPath: List<String> = emptyList()

    private val gestureDetector: GestureDetector
    private val scaleGestureDetector: ScaleGestureDetector

    // Animation
    private val animator = android.animation.ValueAnimator().apply {
        interpolator = DecelerateInterpolator()
        duration = 300
    }
    private var animationProgress = 1f

    // Touch tracking
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    data class TaskEdge(
        val fromId: String,
        val toId: String,
        val type: EdgeType = EdgeType.DEPENDENCY
    )

    enum class EdgeType {
        DEPENDENCY,      // Task B depends on Task A
        SEQUENTIAL,      // Tasks are in sequence
        PARALLEL         // Tasks can run in parallel
    }

    data class NodePosition(
        val x: Float,
        val y: Float,
        val level: Int,
        val task: TaskModel
    )

    init {
        gestureDetector = GestureDetector(context, GestureListener())
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())

        updateThemeColors()
        isClickable = true
        isFocusable = true
    }

    fun setTasks(tasks: List<TaskModel>) {
        this.tasks = tasks
        buildGraph()
        calculateLayout()
        animationProgress = 0f
        startEntranceAnimation()
        invalidate()
    }

    fun setOnNodeClickListener(listener: (TaskModel) -> Unit) {
        this.onNodeClickListener = listener
    }

    fun setOnNodeLongClickListener(listener: (TaskModel) -> Unit) {
        this.onNodeLongClickListener = listener
    }

    /**
     * Highlight the path from root to the given task
     */
    fun highlightPathTo(taskId: String) {
        highlightedPath = buildPathToNode(taskId)
        invalidate()
    }

    fun clearHighlight() {
        highlightedPath = emptyList()
        invalidate()
    }

    /**
     * Center the view on a specific task
     */
    fun centerOnTask(taskId: String) {
        val pos = nodePositions[taskId] ?: return
        val targetX = width / 2f - (pos.x + nodeWidth / 2) * scaleFactor
        val targetY = height / 2f - (pos.y + nodeHeight / 2) * scaleFactor

        animator.cancel()
        animator.setFloatValues(translateX, targetX)
        animator.addUpdateListener {
            translateX = it.animatedValue as Float
            translateY = targetY
            invalidate()
        }
        animator.start()
    }

    /**
     * Reset viewport to fit all nodes
     */
    fun fitToScreen() {
        if (nodePositions.isEmpty()) return

        val minX = nodePositions.values.minOf { it.x }
        val maxX = nodePositions.values.maxOf { it.x } + nodeWidth
        val minY = nodePositions.values.minOf { it.y }
        val maxY = nodePositions.values.maxOf { it.y } + nodeHeight

        val contentWidth = maxX - minX + horizontalSpacing * 2
        val contentHeight = maxY - minY + verticalSpacing * 2

        val scaleX = width / contentWidth
        val scaleY = height / contentHeight
        scaleFactor = minOf(scaleX, scaleY, maxScale)
        scaleFactor = maxOf(scaleFactor, minScale)

        translateX = (width - (maxX + minX) * scaleFactor) / 2f
        translateY = (height - (maxY + minY) * scaleFactor) / 2f + verticalSpacing * scaleFactor

        invalidate()
    }

    private fun buildGraph() {
        edges = tasks.flatMap { task ->
            task.dependencies.map { depId ->
                TaskEdge(depId, task.id, EdgeType.DEPENDENCY)
            }
        }
    }

    private fun calculateLayout() {
        // Topological sort to determine levels
        val sorter = TopologicalSorter()
        val sorted = sorter.sort(tasks)
        val levels = mutableMapOf<String, Int>()

        sorted.forEach { task ->
            val depLevels = task.dependencies.mapNotNull { levels[it] }
            levels[task.id] = if (depLevels.isEmpty()) 0 else depLevels.max() + 1
        }

        // Group by level
        val levelGroups = levels.entries.groupBy { it.value }
            .mapValues { (_, entries) -> entries.map { it.key } }

        // Calculate positions
        val positions = mutableMapOf<String, NodePosition>()
        val maxLevel = levels.values.maxOrNull() ?: 0

        for (level in 0..maxLevel) {
            val nodesInLevel = levelGroups[level] ?: emptyList()
            val levelWidth = nodesInLevel.size * nodeWidth + (nodesInLevel.size - 1) * horizontalSpacing
            val startX = -levelWidth / 2f

            nodesInLevel.forEachIndexed { index, taskId ->
                val task = tasks.find { it.id == taskId } ?: return@forEachIndexed
                val x = startX + index * (nodeWidth + horizontalSpacing)
                val y = level * (nodeHeight + verticalSpacing)
                positions[taskId] = NodePosition(x, y, level, task)
            }
        }

        nodePositions = positions
    }

    private fun buildPathToNode(taskId: String): List<String> {
        val path = mutableListOf<String>()
        var current = taskId

        while (true) {
            path.add(current)
            val task = tasks.find { it.id == current } ?: break
            if (task.dependencies.isEmpty()) break
            // Take first dependency for the path
            current = task.dependencies.first()
        }

        return path.reversed()
    }

    private fun startEntranceAnimation() {
        animator.cancel()
        animator.setFloatValues(0f, 1f)
        animator.addUpdateListener {
            animationProgress = it.animatedValue as Float
            invalidate()
        }
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)

        // Draw grid background
        drawGrid(canvas)

        // Draw edges first (behind nodes)
        edges.forEach { edge ->
            drawEdge(canvas, edge)
        }

        // Draw nodes
        nodePositions.forEach { (id, pos) ->
            drawNode(canvas, pos, id == selectedNodeId)
        }

        canvas.restore()
    }

    private fun drawGrid(canvas: Canvas) {
        val gridSize = 100f
        val visibleLeft = (-translateX / scaleFactor - 200).toInt()
        val visibleTop = (-translateY / scaleFactor - 200).toInt()
        val visibleRight = ((width - translateX) / scaleFactor + 200).toInt()
        val visibleBottom = ((height - translateY) / scaleFactor + 200).toInt()

        for (x in visibleLeft..visibleRight step gridSize.toInt()) {
            canvas.drawLine(x.toFloat(), visibleTop.toFloat(), x.toFloat(), visibleBottom.toFloat(), gridPaint)
        }
        for (y in visibleTop..visibleBottom step gridSize.toInt()) {
            canvas.drawLine(visibleLeft.toFloat(), y.toFloat(), visibleRight.toFloat(), y.toFloat(), gridPaint)
        }
    }

    private fun drawEdge(canvas: Canvas, edge: TaskEdge) {
        val fromPos = nodePositions[edge.fromId] ?: return
        val toPos = nodePositions[edge.toId] ?: return

        val startX = fromPos.x + nodeWidth / 2
        val startY = fromPos.y + nodeHeight
        val endX = toPos.x + nodeWidth / 2
        val endY = toPos.y

        // Bezier curve for smooth edges
        val controlY1 = startY + (endY - startY) / 3
        val controlY2 = endY - (endY - startY) / 3

        val path = Path().apply {
            moveTo(startX, startY)
            cubicTo(startX, controlY1, endX, controlY2, endX, endY)
        }

        // Edge styling based on type and highlight
        val isHighlighted = edge.fromId in highlightedPath && edge.toId in highlightedPath
        val isSelected = edge.fromId == selectedNodeId || edge.toId == selectedNodeId

        edgePaint.apply {
            color = when {
                isHighlighted -> ContextCompat.getColor(context, R.color.neon_cyan)
                isSelected -> ContextCompat.getColor(context, R.color.neon_blue)
                edge.type == EdgeType.DEPENDENCY -> ContextCompat.getColor(context, R.color.edge_dependency)
                edge.type == EdgeType.SEQUENTIAL -> ContextCompat.getColor(context, R.color.edge_sequential)
                else -> ContextCompat.getColor(context, R.color.edge_parallel)
            }
            alpha = (if (isHighlighted || isSelected) 255 else 120).coerceIn(0, 255)
            strokeWidth = if (isHighlighted) 4f else 2.5f
        }

        canvas.drawPath(path, edgePaint)

        // Arrow head
        drawArrowHead(canvas, endX, endY, startX, endY)

        // Reset alpha
        edgePaint.alpha = 255
    }

    private fun drawArrowHead(canvas: Canvas, x: Float, y: Float, fromX: Float, fromY: Float) {
        val arrowSize = 12f
        val angle = kotlin.math.atan2(y - fromY, x - fromX)

        val path = Path().apply {
            moveTo(x, y)
            lineTo(
                x - arrowSize * kotlin.math.cos(angle - Math.PI / 6).toFloat(),
                y - arrowSize * kotlin.math.sin(angle - Math.PI / 6).toFloat()
            )
            lineTo(
                x - arrowSize * kotlin.math.cos(angle + Math.PI / 6).toFloat(),
                y - arrowSize * kotlin.math.sin(angle + Math.PI / 6).toFloat()
            )
            close()
        }

        edgeArrowPaint.color = edgePaint.color
        canvas.drawPath(path, edgeArrowPaint)
    }

    private fun drawNode(canvas: Canvas, pos: NodePosition, isSelected: Boolean) {
        val x = pos.x
        val y = pos.y
        val task = pos.task

        val rect = RectF(x, y, x + nodeWidth, y + nodeHeight)

        // Apply entrance animation
        val animatedY = y + (1 - animationProgress) * 50f
        val animatedAlpha = (animationProgress * 255).toInt()

        canvas.save()
        canvas.translate(0f, animatedY - y)

        // Node background
        val bgColor = when (task.status) {
            TaskModel.Status.COMPLETED -> R.color.node_bg_completed
            TaskModel.Status.IN_PROGRESS -> R.color.node_bg_active
            TaskModel.Status.BLOCKED -> R.color.node_bg_blocked
            TaskModel.Status.PENDING -> R.color.node_bg_pending
            else -> R.color.node_bg_default
        }
        nodePaint.color = ContextCompat.getColor(context, bgColor)
        nodePaint.alpha = animatedAlpha

        canvas.drawRoundRect(rect, nodeCornerRadius, nodeCornerRadius, nodePaint)

        // Border
        val borderColor = when {
            isSelected -> R.color.neon_cyan
            task.id in highlightedPath -> R.color.neon_yellow
            else -> getPriorityColorRes(task.priority)
        }
        nodeStrokePaint.color = ContextCompat.getColor(context, borderColor)
        nodeStrokePaint.alpha = animatedAlpha
        canvas.drawRoundRect(rect, nodeCornerRadius, nodeCornerRadius, nodeStrokePaint)

        // Selection glow
        if (isSelected) {
            highlightPaint.color = ContextCompat.getColor(context, R.color.neon_cyan)
            highlightPaint.alpha = (animatedAlpha * 0.3f).toInt()
            val glowRect = RectF(rect.left - 4, rect.top - 4, rect.right + 4, rect.bottom + 4)
            canvas.drawRoundRect(glowRect, nodeCornerRadius + 4, nodeCornerRadius + 4, highlightPaint)
        }

        // Title
        textPaint.apply {
            color = ContextCompat.getColor(context, R.color.neon_white)
            textSize = 24f
            alpha = animatedAlpha
        }
        val title = if (task.title.length > 18) task.title.take(18) + "…" else task.title
        canvas.drawText(title, x + nodeWidth / 2, y + 35f, textPaint)

        // Status indicator dot
        val statusColor = when (task.status) {
            TaskModel.Status.COMPLETED -> R.color.status_completed
            TaskModel.Status.IN_PROGRESS -> R.color.status_in_progress
            TaskModel.Status.BLOCKED -> R.color.status_blocked
            else -> R.color.status_pending
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, statusColor)
            alpha = animatedAlpha
        }
        canvas.drawCircle(x + 16f, y + 16f, 6f, dotPaint)

        // Progress bar
        val progress = calculateProgress(task)
        if (progress > 0 && progress < 100) {
            val progressBgRect = RectF(x + 20, y + nodeHeight - 20, x + nodeWidth - 20, y + nodeHeight - 12)
            val progressFillRect = RectF(x + 20, y + nodeHeight - 20, x + 20 + (nodeWidth - 40) * progress / 100, y + nodeHeight - 12)

            val progressBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.progress_bg)
                alpha = animatedAlpha
            }
            val progressFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.neon_cyan)
                alpha = animatedAlpha
            }

            canvas.drawRoundRect(progressBgRect, 4f, 4f, progressBgPaint)
            canvas.drawRoundRect(progressFillRect, 4f, 4f, progressFillPaint)
        }

        // Subtask count
        if (task.subtasks.isNotEmpty()) {
            textPaint.apply {
                textSize = 16f
                color = ContextCompat.getColor(context, R.color.neon_gray)
            }
            val completed = task.subtasks.count { it.isCompleted }
            canvas.drawText("$completed/${task.subtasks.size}", x + nodeWidth - 30, y + nodeHeight - 28, textPaint)
        }

        canvas.restore()

        // Reset alpha
        nodePaint.alpha = 255
        nodeStrokePaint.alpha = 255
        textPaint.alpha = 255
    }

    private fun calculateProgress(task: TaskModel): Int {
        return if (task.subtasks.isNotEmpty()) {
            task.subtasks.count { it.isCompleted } * 100 / task.subtasks.size
        } else {
            when (task.status) {
                TaskModel.Status.COMPLETED -> 100
                else -> 0
            }
        }
    }

    private fun getPriorityColorRes(priority: TaskModel.Priority): Int {
        return when (priority) {
            TaskModel.Priority.CRITICAL -> R.color.priority_critical
            TaskModel.Priority.HIGH -> R.color.priority_high
            TaskModel.Priority.MEDIUM -> R.color.priority_medium
            TaskModel.Priority.LOW -> R.color.priority_low
        }
    }

    // Touch handling
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleGestureDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    if (abs(dx) > 5 || abs(dy) > 5) {
                        isDragging = true
                    }
                    if (isDragging) {
                        translateX += dx
                        translateY += dy
                        lastTouchX = event.x
                        lastTouchY = event.y
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging && !scaleGestureDetector.isInProgress) {
                    handleTap(event.x, event.y)
                }
                isDragging = false
            }
        }

        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun handleTap(screenX: Float, screenY: Float) {
        val worldX = (screenX - translateX) / scaleFactor
        val worldY = (screenY - translateY) / scaleFactor

        val tappedNode = nodePositions.entries.find { (_, pos) ->
            worldX >= pos.x && worldX <= pos.x + nodeWidth &&
            worldY >= pos.y && worldY <= pos.y + nodeHeight
        }

        if (tappedNode != null) {
            selectedNodeId = tappedNode.key
            onNodeClickListener?.invoke(tappedNode.value.task)
            invalidate()
        } else {
            selectedNodeId = null
            invalidate()
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (scaleFactor < 1.5f) {
                scaleFactor = min(scaleFactor * 1.5f, maxScale)
            } else {
                scaleFactor = max(scaleFactor / 1.5f, minScale)
            }
            invalidate()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val worldX = (e.x - translateX) / scaleFactor
            val worldY = (e.y - translateY) / scaleFactor

            nodePositions.entries.find { (_, pos) ->
                worldX >= pos.x && worldX <= pos.x + nodeWidth &&
                worldY >= pos.y && worldY <= pos.y + nodeHeight
            }?.let { (_, pos) ->
                onNodeLongClickListener?.invoke(pos.task)
            }
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScale = scaleFactor * detector.scaleFactor
            scaleFactor = max(minScale, min(maxScale, newScale))
            invalidate()
            return true
        }
    }

    private fun updateThemeColors() {
        gridPaint.color = ContextCompat.getColor(context, R.color.grid_line)
    }

    companion object {
        private const val TAG = "TaskGraphView"
    }
}