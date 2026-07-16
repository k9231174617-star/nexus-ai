package com.nexus.agent.ui.observability

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nexus.agent.R
import com.nexus.agent.core.observability.MetricsCollector
import com.nexus.agent.core.observability.Tracer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ObservabilityFragment : Fragment() {

    private lateinit var traceTimelineView: TraceTimelineView
    private lateinit var metricsDashboard: MetricsDashboard
    private lateinit var bottleneckAlertView: BottleneckAlertView
    
    private lateinit var tvTotalTraces: TextView
    private lateinit var tvActiveTraces: TextView
    private lateinit var tvAvgLatency: TextView
    private lateinit var tvErrorRate: TextView
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnExport: ImageButton
    private lateinit var btnClear: ImageButton
    private lateinit var layoutTraces: FrameLayout
    private lateinit var layoutMetrics: FrameLayout
    private lateinit var layoutAlerts: FrameLayout
    private lateinit var tabTraces: LinearLayout
    private lateinit var tabMetrics: LinearLayout
    private lateinit var tabAlerts: LinearLayout

    private var tracer: Tracer? = null
    private var metricsCollector: MetricsCollector? = null
    private var autoRefreshJob: kotlinx.coroutines.Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_observability, container, false)
        initViews(view)
        initObservability()
        setupListeners()
        startAutoRefresh()
        return view
    }

    private fun initViews(view: View) {
        traceTimelineView = TraceTimelineView(requireContext())
        metricsDashboard = MetricsDashboard(requireContext())
        bottleneckAlertView = BottleneckAlertView(requireContext())

        tvTotalTraces = view.findViewById(R.id.tvTotalTraces)
        tvActiveTraces = view.findViewById(R.id.tvActiveTraces)
        tvAvgLatency = view.findViewById(R.id.tvAvgLatency)
        tvErrorRate = view.findViewById(R.id.tvErrorRate)
        btnRefresh = view.findViewById(R.id.btnRefreshObservability)
        btnExport = view.findViewById(R.id.btnExportObservability)
        btnClear = view.findViewById(R.id.btnClearObservability)

        layoutTraces = view.findViewById(R.id.layoutTraces)
        layoutMetrics = view.findViewById(R.id.layoutMetrics)
        layoutAlerts = view.findViewById(R.id.layoutAlerts)

        tabTraces = view.findViewById(R.id.tabTraces)
        tabMetrics = view.findViewById(R.id.tabMetrics)
        tabAlerts = view.findViewById(R.id.tabAlerts)

        layoutTraces.addView(traceTimelineView)
        layoutMetrics.addView(metricsDashboard)
        layoutAlerts.addView(bottleneckAlertView)

        showTab(TAB_TRACES)
    }

    private fun initObservability() {
        tracer = Tracer.getInstance(requireContext())
        metricsCollector = MetricsCollector.getInstance(requireContext())
    }

    private fun setupListeners() {
        btnRefresh.setOnClickListener {
            refreshData()
        }

        btnExport.setOnClickListener {
            exportData()
        }

        btnClear.setOnClickListener {
            clearData()
        }

        tabTraces.setOnClickListener { showTab(TAB_TRACES) }
        tabMetrics.setOnClickListener { showTab(TAB_METRICS) }
        tabAlerts.setOnClickListener { showTab(TAB_ALERTS) }

        traceTimelineView.setOnSpanClickListener { span ->
            showSpanDetails(span)
        }

        metricsDashboard.setOnMetricClickListener { metric, value ->
            // Handle metric click
        }

        bottleneckAlertView.setOnAlertActionListener { alert, action ->
            when (action) {
                BottleneckAlertView.ACTION_INVESTIGATE -> investigateBottleneck(alert)
                BottleneckAlertView.ACTION_IGNORE -> ignoreAlert(alert)
                BottleneckAlertView.ACTION_RESOLVE -> resolveAlert(alert)
            }
        }
    }

    private fun showTab(tab: Int) {
        layoutTraces.visibility = if (tab == TAB_TRACES) View.VISIBLE else View.GONE
        layoutMetrics.visibility = if (tab == TAB_METRICS) View.VISIBLE else View.GONE
        layoutAlerts.visibility = if (tab == TAB_ALERTS) View.VISIBLE else View.GONE

        updateTabAppearance(tabTraces, tab == TAB_TRACES)
        updateTabAppearance(tabMetrics, tab == TAB_METRICS)
        updateTabAppearance(tabAlerts, tab == TAB_ALERTS)
    }

    private fun updateTabAppearance(tab: LinearLayout, isSelected: Boolean) {
        tab.alpha = if (isSelected) 1.0f else 0.5f
        val indicator = tab.findViewById<View>(R.id.tabIndicator)
        indicator?.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
    }

    private fun startAutoRefresh() {
        autoRefreshJob = lifecycleScope.launch {
            while (isActive) {
                refreshData()
                delay(5000) // Refresh every 5 seconds
            }
        }
    }

    private fun refreshData() {
        lifecycleScope.launch {
            try {
                val traces = tracer?.getRecentTraces(100) ?: emptyList()
                val metrics = metricsCollector?.getCurrentMetrics() ?: emptyMap()
                val alerts = metricsCollector?.getBottleneckAlerts() ?: emptyList()

                // Update summary
                tvTotalTraces.text = traces.size.toString()
                tvActiveTraces.text = traces.count { !it.isCompleted }.toString()
                
                val avgLatency = if (traces.isNotEmpty()) {
                    traces.filter { it.isCompleted }.map { it.durationMs }.average()
                } else 0.0
                tvAvgLatency.text = "${avgLatency.toInt()}ms"

                val errorCount = traces.count { it.hasError }
                val errorRate = if (traces.isNotEmpty()) (errorCount * 100.0 / traces.size) else 0.0
                tvErrorRate.text = String.format("%.1f%%", errorRate)

                // Update views
                traceTimelineView.setTraces(traces)
                metricsDashboard.setMetrics(metrics)
                bottleneckAlertView.setAlerts(alerts)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showSpanDetails(span: com.nexus.agent.core.observability.Span) {
        val details = buildString {
            appendLine("Span: ${span.name}")
            appendLine("ID: ${span.id}")
            appendLine("Trace ID: ${span.traceId}")
            appendLine("Status: ${if (span.isCompleted) "Completed" else "Active"}")
            appendLine("Duration: ${span.durationMs}ms")
            appendLine("Start: ${span.startTime}")
            appendLine("End: ${span.endTime ?: "N/A"}")
            appendLine("Tags:")
            span.tags.forEach { (k, v) -> appendLine("  $k: $v") }
            if (span.error != null) {
                appendLine("Error: ${span.error}")
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Span Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .setNeutralButton("View Children") { _, _ ->
                val children = span.childSpans
                if (children.isNotEmpty()) {
                    traceTimelineView.focusOnSpan(span.id)
                }
            }
            .show()
    }

    private fun investigateBottleneck(alert: com.nexus.agent.core.observability.BottleneckAlert) {
        traceTimelineView.highlightTraces(alert.affectedTraceIds)
        showTab(TAB_TRACES)
    }

    private fun ignoreAlert(alert: com.nexus.agent.core.observability.BottleneckAlert) {
        lifecycleScope.launch {
            metricsCollector?.ignoreAlert(alert.id)
            refreshData()
        }
    }

    private fun resolveAlert(alert: com.nexus.agent.core.observability.BottleneckAlert) {
        lifecycleScope.launch {
            metricsCollector?.resolveAlert(alert.id)
            refreshData()
        }
    }

    private fun exportData() {
        lifecycleScope.launch {
            try {
                val exportData = buildString {
                    appendLine("=== NEXUS AI OBSERVABILITY EXPORT ===")
                    appendLine("Timestamp: ${java.util.Date()}")
                    appendLine()
                    
                    val traces = tracer?.getAllTraces() ?: emptyList()
                    appendLine("--- TRACES (${traces.size}) ---")
                    traces.forEach { trace ->
                        appendLine("Trace: ${trace.id}")
                        appendLine("  Name: ${trace.name}")
                        appendLine("  Duration: ${trace.durationMs}ms")
                        appendLine("  Completed: ${trace.isCompleted}")
                        appendLine("  Error: ${trace.hasError}")
                        appendLine()
                    }

                    val metrics = metricsCollector?.getAllMetrics() ?: emptyList()
                    appendLine("--- METRICS (${metrics.size}) ---")
                    metrics.forEach { metric ->
                        appendLine("${metric.name}: ${metric.value} ${metric.unit}")
                    }
                }

                // Save to file or share
                val file = java.io.File(requireContext().cacheDir, "observability_export_${System.currentTimeMillis()}.txt")
                file.writeText(exportData)

                android.content.Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.fileprovider",
                        file
                    ))
                    putExtra(Intent.EXTRA_SUBJECT, "Nexus AI Observability Export")
                    startActivity(Intent.createChooser(this, "Export Observability Data"))
                }

            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearData() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Clear Observability Data")
            .setMessage("This will delete all traces, metrics, and alerts. This action cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                lifecycleScope.launch {
                    tracer?.clearAll()
                    metricsCollector?.clearAll()
                    refreshData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        autoRefreshJob?.cancel()
    }

    companion object {
        private const val TAB_TRACES = 0
        private const val TAB_METRICS = 1
        private const val TAB_ALERTS = 2
    }
}
