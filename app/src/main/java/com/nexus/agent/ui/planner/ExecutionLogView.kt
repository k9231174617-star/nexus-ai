package com.nexus.agent.ui.planner

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nexus.agent.R
import com.nexus.agent.databinding.ViewExecutionLogBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Custom View для отображения лога выполнения задач.
 * Показывает хронологию выполнения с цветовой индикацией статуса и возможностью фильтрации.
 */
class ExecutionLogView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewExecutionLogBinding.inflate(
        LayoutInflater.from(context), this, true
    )

    var onLogEntryClick: ((ExecutionLogEntry) -> Unit)? = null
    var onExportLogs: (() -> Unit)? = null

    private lateinit var logAdapter: ExecutionLogAdapter
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    init {
        orientation = VERTICAL
        setupRecyclerView()
        setupFilters()
        setupExport()
    }

    private fun setupRecyclerView() {
        logAdapter = ExecutionLogAdapter { entry ->
            onLogEntryClick?.invoke(entry)
        }

        binding.recyclerViewLogs.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = logAdapter
        }
    }

    private fun setupFilters() {
        binding.chipGroupLogFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val statuses = checkedIds.map { id ->
                when (id) {
                    R.id.chip_log_success -> ExecutionStatus.SUCCESS
                    R.id.chip_log_failed -> ExecutionStatus.FAILED
                    R.id.chip_log_running -> ExecutionStatus.RUNNING
                    else -> null
                }
            }.filterNotNull()

            logAdapter.filterByStatuses(statuses)
        }

        binding.btnClearLogs.setOnClickListener {
            logAdapter.clear()
        }
    }

    private fun setupExport() {
        binding.btnExportLogs.setOnClickListener {
            onExportLogs?.invoke()
        }
    }

    fun setLogs(logs: List<ExecutionLogEntry>) {
        logAdapter.submitList(logs)
        binding.emptyState.isVisible = logs.isEmpty()
        updateStats(logs)
    }

    fun addLogEntry(entry: ExecutionLogEntry) {
        logAdapter.addEntry(entry)
        binding.recyclerViewLogs.scrollToPosition(0)
    }

    private fun updateStats(logs: List<ExecutionLogEntry>) {
        val success = logs.count { it.status == ExecutionStatus.SUCCESS }
        val failed = logs.count { it.status == ExecutionStatus.FAILED }
        val avgDuration = if (logs.isNotEmpty()) logs.map { it.duration }.average().toLong() else 0

        binding.tvSuccessCount.text = "✓ $success"
        binding.tvFailedCount.text = "✗ $failed"
        binding.tvAvgDuration.text = "⏱ ${formatDuration(avgDuration)}"
    }

    private fun formatDuration(durationMs: Long): String {
        return when {
            durationMs > 60000 -> "${durationMs / 60000}м ${(durationMs % 60000) / 1000}с"
            durationMs > 1000 -> "${durationMs / 1000}.${(durationMs % 1000) / 100}с"
            else -> "${durationMs}мс"
        }
    }
}

/**
 * Adapter для списка логов выполнения
 */
class ExecutionLogAdapter(
    private val onItemClick: (ExecutionLogEntry) -> Unit
) : RecyclerView.Adapter<ExecutionLogAdapter.LogViewHolder>() {

    private var allLogs: List<ExecutionLogEntry> = emptyList()
    private var displayedLogs: List<ExecutionLogEntry> = emptyList()
    private var activeFilters: List<ExecutionStatus> = emptyList()

    fun submitList(logs: List<ExecutionLogEntry>) {
        allLogs = logs
        applyFilter()
    }

    fun addEntry(entry: ExecutionLogEntry) {
        allLogs = listOf(entry) + allLogs
        applyFilter()
    }

    fun clear() {
        allLogs = emptyList()
        displayedLogs = emptyList()
        notifyDataSetChanged()
    }

    fun filterByStatuses(statuses: List<ExecutionStatus>) {
        activeFilters = statuses
        applyFilter()
    }

    private fun applyFilter() {
        displayedLogs = if (activeFilters.isEmpty()) {
            allLogs
        } else {
            allLogs.filter { it.status in activeFilters }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemExecutionLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(displayedLogs[position])
    }

    override fun getItemCount(): Int = displayedLogs.size

    inner class LogViewHolder(
        private val binding: ItemExecutionLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: ExecutionLogEntry) {
            // Время
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            binding.tvTimestamp.text = timeFormat.format(Date(entry.timestamp))

            // Статус с иконкой
            val (icon, color) = when (entry.status) {
                ExecutionStatus.SUCCESS -> "✓" to R.color.log_success
                ExecutionStatus.FAILED -> "✗" to R.color.log_failed
                ExecutionStatus.RUNNING -> "⟳" to R.color.log_running
                ExecutionStatus.PENDING → "○" to R.color.log_pending
            }

            binding.tvStatusIcon.text = icon
            binding.tvStatusIcon.setTextColor(itemView.context.getColor(color))

            // Сообщение
            binding.tvMessage.text = entry.message

            // Длительность
            binding.tvDuration.isVisible = entry.duration > 0
            binding.tvDuration.text = formatDuration(entry.duration)

            // ID задачи
            binding.tvTaskId.text = "#${entry.taskId.take(8)}"

            // Цветовая подсветка строки
            val bgColor = when (entry.status) {
                ExecutionStatus.SUCCESS -> R.color.log_bg_success
                ExecutionStatus.FAILED -> R.color.log_bg_failed
                else -> R.color.log_bg_normal
            }
            binding.root.setBackgroundColor(itemView.context.getColor(bgColor))

            itemView.setOnClickListener { onItemClick(entry) }
        }

        private fun formatDuration(durationMs: Long): String {
            return when {
                durationMs > 60000 -> "${durationMs / 60000}м"
                durationMs > 1000 -> "${durationMs / 1000}с"
                else -> "${durationMs}мс"
            }
        }
    }
}
