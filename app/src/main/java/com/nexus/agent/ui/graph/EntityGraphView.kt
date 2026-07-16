package com.nexus.agent.ui.graph

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import com.nexus.agent.R
import com.nexus.agent.core.graph.EntityNode
import com.nexus.agent.core.graph.RelationEdge
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class EntityGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Data
    private val nodes = mutableListOf<GraphNode>()
    private val edges = mutableListOf<GraphEdge>()
    private val nodeMap = mutableMapOf<String, GraphNode>()

    // Rendering
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#00E5FF")
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#4A6572")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#FF6E40")
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#4A6572")
    }
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#0A1929")
    }

    // Viewport
    private var scaleFactor = 1.0f
    private var translateX = 0f
    private var translateY = 0f
    private val minScale = 0.2f
    private val maxScale = 5.0f

    // Interaction
    private var selectedNodeId: String? = null
    private var highlightedNodeIds = mutableSetOf<String>()
    private var highlightedEdgeIds = mutableSetOf<String>()
    private var draggedNode: GraphNode? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Callbacks
    private var onNodeClickListener: ((EntityNode) -> Unit)? = null
    private var onEdgeClickListener: ((RelationEdge) -> Unit)? = null
    private var onNodeLongClickListener: ((EntityNode) -> Unit)? = null

    // Gestures
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            handleTap(e.x, e.y)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val node = findNodeAt(e.x, e.y)
            node?.let { onNodeLongClickListener?.invoke(it.entity) }
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (draggedNode != null) {
                val worldX = (e2.x - translateX) / scaleFactor
                val worldY = (e2.y - translateY) / scaleFactor
                draggedNode?.let { node ->
                    node.x = worldX
                    node.y = worldY
                    invalidate()
                }
            } else {
                translateX -= distanceX
                translateY -= distanceY
                invalidate()
            }
            return true
        }
    })

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScale = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)
            val focusX = detector.focusX
            val focusY = detector.focusY
            
            translateX += (focusX - translateX) * (1 - newScale / scaleFactor)
            translateY += (focusY - translateY) * (1 - newScale / scaleFactor)
            scaleFactor = newScale
            
            invalidate()
            return true
        }
    })

    // Type colors
    private val typeColors = mapOf(
        "person" to Color.parseColor("#FF6E40"),
        "organization" to Color.parseColor("#00E5FF"),
        "location" to Color.parseColor("#76FF03"),
        "concept" to Color.parseColor("#E040FB"),
        "event" to Color.parseColor("#FFFF00"),
        "document" to Color.parseColor("#448AFF"),
        "default" to Color.parseColor("#78909C")
    )

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        
        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)

        // Draw edges first (behind nodes)
        edges.forEach { drawEdge(canvas, it) }

        // Draw nodes
        nodes.forEach { drawNode(canvas, it) }

        canvas.restore()
    }

    private fun drawNode(canvas: Canvas, node: GraphNode) {
        val color = typeColors[node.entity.type] ?: typeColors["default"]!!
        val isHighlighted = highlightedNodeIds.contains(node.entity.id)
        val isSelected = selectedNodeId == node.entity.id
        val radius = if (isSelected) 45f else 35f

        // Glow effect for highlighted nodes
        if (isHighlighted) {
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#33FF6E40")
            }
            canvas.drawCircle(node.x, node.y, radius + 15f, glowPaint)
        }

        // Node circle
        nodePaint.color = color
        canvas.drawCircle(node.x, node.y, radius, nodePaint)

        // Stroke
        if (isSelected || isHighlighted) {
            canvas.drawCircle(node.x, node.y, radius, highlightPaint)
        } else {
            canvas.drawCircle(node.x, node.y, radius, strokePaint)
        }

        // Label
        val label = if (node.entity.name.length > 12) {
            node.entity.name.take(10) + "..."
        } else {
            node.entity.name
        }
        canvas.drawText(label, node.x, node.y + radius + 20f, textPaint)

        // Type badge
        val typeBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.FILL
        }
        val badgeRect = RectF(
            node.x - 30f, node.y - radius - 18f,
            node.x + 30f, node.y - radius - 2f
        )
        canvas.drawRoundRect(badgeRect, 8f, 8f, typeBadgePaint)
        
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = color
            textSize = 16f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(node.entity.type.take(4), node.x, node.y - radius - 6f, badgeTextPaint)
    }

    private fun drawEdge(canvas: Canvas, edge: GraphEdge) {
        val source = edge.source
        val target = edge.target
        val isHighlighted = highlightedEdgeIds.contains(edge.relation.id)

        val paint = if (isHighlighted) {
            Paint(edgePaint).apply {
                color = Color.parseColor("#FF6E40")
                strokeWidth = 4f
            }
        } else {
            edgePaint
        }

        // Draw line
        canvas.drawLine(source.x, source.y, target.x, target.y, paint)

        // Draw arrow
        val angle = atan2(target.y - source.y, target.x - source.x)
        val arrowLength = 15f
        val arrowAngle = Math.PI / 6
        val nodeRadius = 35f

        val endX = target.x - nodeRadius * cos(angle)
        val endY = target.y - nodeRadius * sin(angle)

        val path = Path().apply {
            moveTo(endX, endY)
            lineTo(
                (endX - arrowLength * cos(angle - arrowAngle)).toFloat(),
                (endY - arrowLength * sin(angle - arrowAngle)).toFloat()
            )
            lineTo(
                (endX - arrowLength * cos(angle + arrowAngle)).toFloat(),
                (endY - arrowLength * sin(angle + arrowAngle)).toFloat()
            )
            close()
        }
        canvas.drawPath(path, if (isHighlighted) Paint(arrowPaint).apply { 
            color = Color.parseColor("#FF6E40") 
        } else arrowPaint)

        // Relation label
        val midX = (source.x + target.x) / 2
        val midY = (source.y + target.y) / 2
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#90A4AE")
            textSize = 20f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(edge.relation.type, midX, midY - 5f, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val worldX = (event.x - translateX) / scaleFactor
                val worldY = (event.y - translateY) / scaleFactor
                draggedNode = findNodeAt(event.x, event.y)
                lastTouchX = event.x
                lastTouchY = event.y
                if (draggedNode != null) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggedNode != null) {
                    val worldX = (event.x - translateX) / scaleFactor
                    val worldY = (event.y - translateY) / scaleFactor
                    draggedNode?.let { node ->
                        node.x = worldX
                        node.y = worldY
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggedNode = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return true
    }

    private fun handleTap(x: Float, y: Float) {
        val node = findNodeAt(x, y)
        if (node != null) {
            selectedNodeId = node.entity.id
            onNodeClickListener?.invoke(node.entity)
            invalidate()
            return
        }

        val edge = findEdgeAt(x, y)
        if (edge != null) {
            onEdgeClickListener?.invoke(edge.relation)
            invalidate()
            return
        }

        selectedNodeId = null
        invalidate()
    }

    private fun findNodeAt(x: Float, y: Float): GraphNode? {
        val worldX = (x - translateX) / scaleFactor
        val worldY = (y - translateY) / scaleFactor
        return nodes.find { node ->
            hypot(node.x - worldX, node.y - worldY) < 40f
        }
    }

    private fun findEdgeAt(x: Float, y: Float): GraphEdge? {
        val worldX = (x - translateX) / scaleFactor
        val worldY = (y - translateY) / scaleFactor
        return edges.find { edge ->
            distanceToLine(worldX, worldY, edge.source.x, edge.source.y, edge.target.x, edge.target.y) < 10f
        }
    }

    private fun distanceToLine(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val lineLength = hypot(x2 - x1, y2 - y1)
        if (lineLength == 0f) return hypot(px - x1, py - y1)
        val t = ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / (lineLength * lineLength)
        val clampedT = t.coerceIn(0f, 1f)
        val projX = x1 + clampedT * (x2 - x1)
        val projY = y1 + clampedT * (y2 - y1)
        return hypot(px - projX, py - projY)
    }

    // Public API
    fun setData(entities: List<EntityNode>, relations: List<RelationEdge>) {
        nodes.clear()
        edges.clear()
        nodeMap.clear()

        // Create nodes with initial positions (circular layout)
        val count = entities.size
        val radius = 300f
        entities.forEachIndexed { index, entity ->
            val angle = 2 * Math.PI * index / count
            val node = GraphNode(
                entity = entity,
                x = (width / 2f + radius * cos(angle)).toFloat(),
                y = (height / 2f + radius * sin(angle)).toFloat()
            )
            nodes.add(node)
            nodeMap[entity.id] = node
        }

        // Create edges
        relations.forEach { relation ->
            val source = nodeMap[relation.fromId]
            val target = nodeMap[relation.toId]
            if (source != null && target != null) {
                edges.add(GraphEdge(source, target, relation))
            }
        }

        invalidate()
    }

    fun addNode(entity: EntityNode) {
        if (nodeMap.containsKey(entity.id)) return
        val node = GraphNode(
            entity = entity,
            x = width / 2f + Random.nextFloat() * 100f - 50f,
            y = height / 2f + Random.nextFloat() * 100f - 50f
        )
        nodes.add(node)
        nodeMap[entity.id] = node
        invalidate()
    }

    fun addEdge(relation: RelationEdge) {
        val source = nodeMap[relation.fromId]
        val target = nodeMap[relation.toId]
        if (source != null && target != null) {
            edges.add(GraphEdge(source, target, relation))
            invalidate()
        }
    }

    fun removeNode(nodeId: String) {
        val node = nodeMap.remove(nodeId) ?: return
        nodes.remove(node)
        edges.removeAll { it.source == node || it.target == node }
        invalidate()
    }

    fun highlightNodes(nodeIds: List<String>) {
        highlightedNodeIds.clear()
        highlightedNodeIds.addAll(nodeIds)
        invalidate()
    }

    fun highlightNeighbors(centerId: String, neighborIds: List<String>) {
        highlightedNodeIds.clear()
        highlightedNodeIds.add(centerId)
        highlightedNodeIds.addAll(neighborIds)
        
        highlightedEdgeIds.clear()
        edges.filter { 
            (it.source.entity.id == centerId && neighborIds.contains(it.target.entity.id)) ||
            (it.target.entity.id == centerId && neighborIds.contains(it.source.entity.id))
        }.forEach { highlightedEdgeIds.add(it.relation.id) }
        
        invalidate()
    }

    fun centerOnNode(nodeId: String) {
        val node = nodeMap[nodeId] ?: return
        val targetX = width / 2f - node.x * scaleFactor
        val targetY = height / 2f - node.y * scaleFactor
        
        animateTranslation(targetX, targetY)
    }

    fun applyForceLayout() {
        // Simple force-directed layout
        val iterations = 100
        val repulsionForce = 5000f
        val attractionForce = 0.01f
        val damping = 0.8f

        val velocities = nodes.associateWith { Pair(0f, 0f) }.toMutableMap()

        repeat(iterations) {
            // Repulsion
            for (i in nodes.indices) {
                for (j in i + 1 until nodes.size) {
                    val a = nodes[i]
                    val b = nodes[j]
                    val dx = b.x - a.x
                    val dy = b.y - a.y
                    val dist = hypot(dx, dy).coerceAtLeast(1f)
                    val force = repulsionForce / (dist * dist)
                    val fx = force * dx / dist
                    val fy = force * dy / dist

                    velocities[a] = Pair(velocities[a]!!.first - fx, velocities[a]!!.second - fy)
                    velocities[b] = Pair(velocities[b]!!.first + fx, velocities[b]!!.second + fy)
                }
            }

            // Attraction (edges)
            edges.forEach { edge ->
                val dx = edge.target.x - edge.source.x
                val dy = edge.target.y - edge.source.y
                val dist = hypot(dx, dy).coerceAtLeast(1f)
                val force = attractionForce * dist
                val fx = force * dx / dist
                val fy = force * dy / dist

                velocities[edge.source] = Pair(velocities[edge.source]!!.first + fx, velocities[edge.source]!!.second + fy)
                velocities[edge.target] = Pair(velocities[edge.target]!!.first - fx, velocities[edge.target]!!.second - fy)
            }

            // Apply velocities
            nodes.forEach { node ->
                val (vx, vy) = velocities[node]!!
                node.x += vx * damping
                node.y += vy * damping
                velocities[node] = Pair(vx * damping, vy * damping)
            }
        }

        invalidate()
    }

    private fun animateTranslation(targetX: Float, targetY: Float) {
        val startX = translateX
        val startY = translateY
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                translateX = startX + (targetX - startX) * fraction
                translateY = startY + (targetY - startY) * fraction
                invalidate()
            }
        }
        animator.start()
    }

    fun setOnNodeClickListener(listener: (EntityNode) -> Unit) {
        onNodeClickListener = listener
    }

    fun setOnEdgeClickListener(listener: (RelationEdge) -> Unit) {
        onEdgeClickListener = listener
    }

    fun setOnNodeLongClickListener(listener: (EntityNode) -> Unit) {
        onNodeLongClickListener = listener
    }

    fun resetView() {
        scaleFactor = 1.0f
        translateX = 0f
        translateY = 0f
        invalidate()
    }

    // Inner classes
    private data class GraphNode(
        val entity: EntityNode,
        var x: Float,
        var y: Float
    )

    private data class GraphEdge(
        val source: GraphNode,
        val target: GraphNode,
        val relation: RelationEdge
    )
}
