package com.nexus.agent.ui.observability

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.agent.R
import com.nexus.agent.core.observability.BottleneckAlert

class BottleneckAlertView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private val container: LinearLayout
    private val alerts = mutableListOf<BottleneckAlert>()
    private val alertViews = mutableMapOf<String, AlertViewHolder>()

    // Severity colors
    private val severityColors = mapOf(
        BottleneckAlert.SEVERITY_CRITICAL to Color.parseColor("#FF1744"),
        BottleneckAlert.SEVERITY_HIGH to Color.parseColor("#FF9100"),
        BottleneckAlert.SEVERITY_MEDIUM to Color.parseColor("#FFC400"),
        BottleneckAlert.SEVERITY_LOW to Color.parseColor("#00E5FF"),
        BottleneckAlert.SEVERITY_INFO to Color.parseColor("#76FF03")
    )

    private val severityBackgrounds = mapOf(
        BottleneckAlert.SEVERITY_CRITICAL to Color.parseColor("#33FF1744"),
        BottleneckAlert.SEVERITY_HIGH to Color.parseColor("#33FF9100"),
        BottleneckAlert.SEVERITY_MEDIUM to Color.parseColor("#33FFC400"),
        BottleneckAlert.SEVERITY_LOW to Color.parseColor("#3300E5FF"),
        BottleneckAlert.SEVERITY_INFO to Color.parseColor("#3376FF03")
    )

    private var onAlertActionListener: ((BottleneckAlert, Int) -> Unit)? = null

    companion object {
        const val ACTION_INVESTIGATE = 0
        const val ACTION_IGNORE = 1
        const val ACTION_RESOLVE = 2
    }

    init {
        container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        addView(container)
        isFillViewport = true
    }

    fun setAlerts(newAlerts: List<BottleneckAlert>) {
        // Remove resolved alerts
        val currentIds = newAlerts.map { it.id }.toSet()
        val toRemove = alerts.filter { it.id !in currentIds }
        toRemove.forEach { removeAlert(it.id) }

        // Add or update new alerts
        newAlerts.forEach { alert ->
            val existing = alerts.find { it.id == alert.id }
            if (existing == null) {
                addAlertView(alert)
            } else {
                updateAlertView(alert)
            }
        }

        alerts.clear()
        alerts.addAll(newAlerts)

        if (alerts.isEmpty()) {
            showEmptyState()
        } else {
            hideEmptyState()
        }
    }

    private fun addAlertView(alert: BottleneckAlert) {
        val view = LayoutInflater.from(context).inflate(R.layout.item_bottleneck_alert, container, false)
        val holder = AlertViewHolder(view)

        holder.tvTitle.text = alert.title
        holder.tvDescription.text = alert.description
        holder.tvSeverity.text = alert.severity.uppercase()
        holder.tvTimestamp.text = formatTimestamp(alert.timestamp)
        holder.tvMetric.text = "${alert.metricName}: ${String.format("%.2f", alert.metricValue)} ${alert.metricUnit}"
        holder.tvAffectedTraces.text = "Affected traces: ${alert.affectedTraceIds.size}"

        val severityColor = severityColors[alert.severity] ?: Color.parseColor("#90A4AE")
        val bgColor = severityBackgrounds[alert.severity] ?: Color.parseColor("#142536")

        holder.tvSeverity.setTextColor(severityColor)
        holder.viewColorIndicator.setBackgroundColor(severityColor)

        val background = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = 16f
            setStroke(2, Color.parseColor("#1E293B"))
        }
        view.background = background

        // Set icon based on severity
        val iconRes = when (alert.severity) {
            BottleneckAlert.SEVERITY_CRITICAL -> R.drawable.ic_alert_critical
            BottleneckAlert.SEVERITY_HIGH -> R.drawable.ic_alert_high
            BottleneckAlert.SEVERITY_MEDIUM -> R.drawable.ic_alert_medium
            BottleneckAlert.SEVERITY_LOW -> R.drawable.ic_alert_low
            else -> R.drawable.ic_alert_info
        }
        holder.ivIcon.setImageResource(iconRes)

        // Button actions
        holder.btnInvestigate.setOnClickListener {
            onAlertActionListener?.invoke(alert, ACTION_INVESTIGATE)
        }

        holder.btnIgnore.setOnClickListener {
            onAlertActionListener?.invoke(alert, ACTION_IGNORE)
            removeAlert(alert.id)
        }

        holder.btnResolve.setOnClickListener {
            onAlertActionListener?.invoke(alert, ACTION_RESOLVE)
            removeAlert(alert.id)
        }

        // Expand/collapse details
        holder.viewHeader.setOnClickListener {
            val isExpanded = holder.layoutDetails.visibility == View.VISIBLE
            holder.layoutDetails.visibility = if (isExpanded) View.GONE else View.VISIBLE
            holder.ivExpand.rotation = if (isExpanded) 0f else 180f
        }

        // Populate details
        if (alert.recommendations.isNotEmpty()) {
            holder.tvRecommendations.text = alert.recommendations.joinToString("\n") { "• $it" }
        } else {
            holder.tvRecommendations.text = "No recommendations available."
        }

        if (alert.affectedTraceIds.isNotEmpty()) {
            holder.tvAffectedTraces.text = alert.affectedTraceIds.joinToString("\n")
        }

        // Add to container
        container.addView(view, 0) // Newest first
        alertViews[alert.id] = holder
    }

    private fun updateAlertView(alert: BottleneckAlert) {
        val holder = alertViews[alert.id] ?: return

        holder.tvMetric.text = "${alert.metricName}: ${String.format("%.2f", alert.metricValue)} ${alert.metricUnit}"
        holder.tvTimestamp.text = formatTimestamp(alert.timestamp)

        if (alert.isResolved) {
            holder.viewOverlay.visibility = View.VISIBLE
            holder.tvResolved.visibility = View.VISIBLE
            holder.btnResolve.isEnabled = false
            holder.btnResolve.alpha = 0.5f
        }
    }

    private fun removeAlert(alertId: String) {
        val holder = alertViews[alertId] ?: return
        val index = container.indexOfChild(holder.rootView)
        if (index >= 0) {
            container.removeViewAt(index)
        }
        alertViews.remove(alertId)
        alerts.removeAll { it.id == alertId }

        if (alerts.isEmpty()) {
            showEmptyState()
        }
    }

    private fun showEmptyState() {
        if (container.findViewWithTag<View>("empty_state") == null) {
            val emptyView = LayoutInflater.from(context).inflate(R.layout.view_empty_alerts, container, false)
            emptyView.tag = "empty_state"
            container.addView(emptyView)
        }
    }

    private fun hideEmptyState() {
        val emptyView = container.findViewWithTag<View>("empty_state")
        emptyView?.let { container.removeView(it) }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            else -> "${diff / 86_400_000}d ago"
        }
    }

    fun setOnAlertActionListener(listener: (BottleneckAlert, Int) -> Unit) {
        onAlertActionListener = listener
    }

    fun clearAll() {
        container.removeAllViews()
        alerts.clear()
        alertViews.clear()
        showEmptyState()
    }

    // ViewHolder
    private class AlertViewHolder(val rootView: View) {
        val viewHeader: View = rootView.findViewById(R.id.alertHeader)
        val viewColorIndicator: View = rootView.findViewById(R.id.alertColorIndicator)
        val ivIcon: ImageView = rootView.findViewById(R.id.ivAlertIcon)
        val tvTitle: TextView = rootView.findViewById(R.id.tvAlertTitle)
        val tvSeverity: TextView = rootView.findViewById(R.id.tvAlertSeverity)
        val tvTimestamp: TextView = rootView.findViewById(R.id.tvAlertTimestamp)
        val ivExpand: ImageView = rootView.findViewById(R.id.ivExpandAlert)
        val layoutDetails: LinearLayout = rootView.findViewById(R.id.layoutAlertDetails)
        val tvDescription: TextView = rootView.findViewById(R.id.tvAlertDescription)
        val tvMetric: TextView = rootView.findViewById(R.id.tvAlertMetric)
        val tvRecommendations: TextView = rootView.findViewById(R.id.tvAlertRecommendations)
        val tvAffectedTraces: TextView = rootView.findViewById(R.id.tvAlertAffectedTraces)
        val btnInvestigate: Button = rootView.findViewById(R.id.btnInvestigate)
        val btnIgnore: Button = rootView.findViewById(R.id.btnIgnoreAlert)
        val btnResolve: Button = rootView.findViewById(R.id.btnResolveAlert)
        val viewOverlay: View = rootView.findViewById(R.id.alertResolvedOverlay)
        val tvResolved: TextView = rootView.findViewById(R.id.tvResolvedLabel)
    }
}
