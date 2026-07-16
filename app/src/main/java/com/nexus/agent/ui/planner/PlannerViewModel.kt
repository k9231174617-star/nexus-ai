package com.nexus.agent.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.agent.core.planner.TaskModel
import com.nexus.agent.core.planner.TaskPlanner
import com.nexus.agent.core.planner.WorkflowEngine
import com.nexus.agent.core.planner.ParallelExecutor
import com.nexus.agent.core.planner.TaskRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel для PlannerFragment.
 * Управляет задачами, workflow, их выполнением и логированием.
 */
@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val taskPlanner: TaskPlanner,
    private val workflowEngine: WorkflowEngine,
    private val parallelExecutor: ParallelExecutor,
    private val taskRegistry: TaskRegistry
) : ViewModel() {

    private val _tasks = MutableStateFlow<List<TaskModel>>(emptyList())
    val tasks: StateFlow<List<TaskModel>> = _tasks.asStateFlow()

    private val _executionLogs = MutableStateFlow<List<ExecutionLogEntry>>(emptyList())
    val executionLogs: StateFlow<List<ExecutionLogEntry>> = _executionLogs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentExecution = MutableStateFlow<ExecutionState?>(null)
    val currentExecution: StateFlow<ExecutionState?> = _currentExecution.asStateFlow()

    private val _events = MutableSharedFlow<PlannerEvent>()
    val events: SharedFlow<PlannerEvent> = _events.asSharedFlow()

    private val _stats = MutableStateFlow(PlannerStats())
    val stats: StateFlow<PlannerStats> = _stats.asStateFlow()

    private var currentSort = SortCriteria.PRIORITY
    private var currentFilter = TaskFilter()
    private val expandedTasks = mutableSetOf<String>()
    private val pendingNodePositions = mutableMapOf<String, Pair<Float, Float>>()

    fun loadTasks() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val allTasks = taskRegistry.getAllTasks()
                _tasks.value = applySortAndFilter(allTasks)
                updateStats(allTasks)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createTask(
        name: String,
        description: String,
        priority: TaskModel.Priority,
        dependencies: List<String>,
        estimatedDuration: Long
    ) {
        viewModelScope.launch {
            val task = TaskModel(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                description = description,
                priority = priority,
                dependencies = dependencies,
                estimatedDurationMs = estimatedDuration,
                status = TaskModel.Status.PENDING,
                createdAt = System.currentTimeMillis()
            )
            
            if (taskPlanner.detectCycle(task)) {
                _events.emit(PlannerEvent.CycleDetected)
                return@launch
            }
            
            taskRegistry.register(task)
            loadTasks()
            _events.emit(PlannerEvent.TaskCreated)
        }
    }

    fun updateTask(
        id: String,
        name: String,
        description: String,
        priority: TaskModel.Priority,
        dependencies: List<String>,
        estimatedDuration: Long
    ) {
        viewModelScope.launch {
            taskRegistry.update(id) { task ->
                task.copy(
                    name = name,
                    description = description,
                    priority = priority,
                    dependencies = dependencies,
                    estimatedDurationMs = estimatedDuration
                )
            }
            loadTasks()
        }
    }

    fun deleteTask(id: String) {
        viewModelScope.launch {
            taskRegistry.delete(id)
            loadTasks()
        }
    }

    fun executeTask(id: String) {
        viewModelScope.launch {
            _currentExecution.value = ExecutionState(
                phase = ExecutionPhase.EXECUTING,
                currentTask = taskRegistry.get(id),
                completed = 0,
                total = 1
            )
            
            try {
                val result = workflowEngine.executeTask(id)
                addLogEntry(ExecutionLogEntry(
                    taskId = id,
                    timestamp = System.currentTimeMillis(),
                    status = if (result.success) ExecutionStatus.SUCCESS else ExecutionStatus.FAILED,
                    message = result.message,
                    duration = result.durationMs
                ))
                
                _currentExecution.value = ExecutionState(
                    phase = if (result.success) ExecutionPhase.COMPLETED else ExecutionPhase.FAILED,
                    currentTask = taskRegistry.get(id),
                    completed = 1,
                    total = 1,
                    error = if (!result.success) result.message else null
                )
                
                loadTasks()
            } catch (e: Exception) {
                _currentExecution.value = ExecutionState(
                    phase = ExecutionPhase.FAILED,
                    currentTask = taskRegistry.get(id),
                    completed = 0,
                    total = 1,
                    error = e.message
                )
                addLogEntry(ExecutionLogEntry(
                    taskId = id,
                    timestamp = System.currentTimeMillis(),
                    status = ExecutionStatus.FAILED,
                    message = "Exception: ${e.message}",
                    duration = 0
                ))
            }
        }
    }

    fun executeAllPending() {
        viewModelScope.launch {
            val pendingTasks = taskRegistry.getAllTasks().filter { it.status == TaskModel.Status.PENDING }
            if (pendingTasks.isEmpty()) {
                _events.emit(PlannerEvent.ShowToast("Нет ожидающих задач"))
                return@launch
            }
            
            _currentExecution.value = ExecutionState(
                phase = ExecutionPhase.PLANNING,
                currentTask = null,
                completed = 0,
                total = pendingTasks.size
            )
            
            try {
                val sorted = taskPlanner.topologicalSort(pendingTasks)
                
                var completed = 0
                val failed = mutableListOf<String>()
                
                sorted.forEach { task ->
                    _currentExecution.value = _currentExecution.value?.copy(
                        phase = ExecutionPhase.EXECUTING,
                        currentTask = task
                    )
                    
                    val result = workflowEngine.executeTask(task.id)
                    
                    addLogEntry(ExecutionLogEntry(
                        taskId = task.id,
                        timestamp = System.currentTimeMillis(),
                        status = if (result.success) ExecutionStatus.SUCCESS else ExecutionStatus.FAILED,
                        message = result.message,
                        duration = result.durationMs
                    ))
                    
                    if (result.success) {
                        completed++
                    } else {
                        failed.add(task.name)
                    }
                }
                
                _currentExecution.value = ExecutionState(
                    phase = if (failed.isEmpty()) ExecutionPhase.COMPLETED else ExecutionPhase.FAILED,
                    currentTask = null,
                    completed = completed,
                    total = pendingTasks.size,
                    error = if (failed.isNotEmpty()) "Failed: ${failed.joinToString(", ")}" else null
                )
                
                _events.emit(PlannerEvent.ExecutionComplete(completed, pendingTasks.size))
                loadTasks()
            } catch (e: Exception) {
                _currentExecution.value = ExecutionState(
                    phase = ExecutionPhase.FAILED,
                    currentTask = null,
                    completed = 0,
                    total = pendingTasks.size,
                    error = e.message
                )
            }
        }
    }

    fun markComplete(id: String) {
        viewModelScope.launch {
            taskRegistry.update(id) { it.copy(status = TaskModel.Status.COMPLETED) }
            loadTasks()
        }
    }

    fun retryTask(id: String) {
        viewModelScope.launch {
            taskRegistry.update(id) { it.copy(status = TaskModel.Status.PENDING, retryCount = it.retryCount + 1) }
            executeTask(id)
        }
    }

    fun duplicateTask(id: String) {
        viewModelScope.launch {
            val original = taskRegistry.get(id) ?: return@launch
            val copy = original.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = original.name + " (копия)",
                status = TaskModel.Status.PENDING,
                createdAt = System.currentTimeMillis()
            )
            taskRegistry.register(copy)
            loadTasks()
        }
    }

    fun toggleTaskExpand(id: String) {
        if (id in expandedTasks) {
            expandedTasks.remove(id)
        } else {
            expandedTasks.add(id)
        }
        loadTasks()
    }

    fun focusTask(id: String) {
        // Переключение на граф и центрирование на задаче
    }

    fun searchTasks(query: String) {
        currentFilter = currentFilter.copy(searchQuery = query)
        applyCurrentFilter()
    }

    fun filterByStatuses(statuses: List<TaskModel.Status>) {
        currentFilter = currentFilter.copy(statuses = statuses)
        applyCurrentFilter()
    }

    fun sortBy(criteria: SortCriteria) {
        currentSort = criteria
        applyCurrentFilter()
    }

    fun autoPlan(description: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val generatedTasks = taskPlanner.autoGenerateTasks(description)
                generatedTasks.forEach { taskRegistry.register(it) }
                loadTasks()
                _events.emit(PlannerEvent.ShowToast("Сгенерировано ${generatedTasks.size} задач"))
            } catch (e: Exception) {
                _events.emit(PlannerEvent.ShowToast("Ошибка планирования: ${e.message}"))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setPendingNodePosition(x: Float, y: Float) {
        // Сохраняет позицию для будущей задачи
    }

    fun getTask(id: String): TaskModel? {
        return taskRegistry.get(id)
    }

    fun exportLogs() {
        viewModelScope.launch {
            try {
                val logs = _executionLogs.value
                val json = kotlinx.serialization.json.Json.encodeToString(
                    kotlinx.serialization.serializer(),
                    logs
                )
                val path = "/sdcard/nexus_ai/execution_logs_${System.currentTimeMillis()}.json"
                java.io.File(path).parentFile?.mkdirs()
                java.io.File(path).writeText(json)
                _events.emit(PlannerEvent.ShowToast("Логи экспортированы: $path"))
            } catch (e: Exception) {
                _events.emit(PlannerEvent.ShowToast("Ошибка экспорта: ${e.message}"))
            }
        }
    }

    private fun applyCurrentFilter() {
        viewModelScope.launch {
            val all = taskRegistry.getAllTasks()
            _tasks.value = applySortAndFilter(all)
        }
    }

    private fun applySortAndFilter(tasks: List<TaskModel>): List<TaskModel> {
        var result = tasks.filter { task ->
            val matchesQuery = currentFilter.searchQuery.isBlank() ||
                task.name.contains(currentFilter.searchQuery, ignoreCase = true) ||
                task.description.contains(currentFilter.searchQuery, ignoreCase = true)
            
            val matchesStatus = currentFilter.statuses.isEmpty() || task.status in currentFilter.statuses
            
            matchesQuery && matchesStatus
        }
        
        result = when (currentSort) {
            SortCriteria.PRIORITY -> result.sortedByDescending { it.priority.ordinal }
            SortCriteria.DATE -> result.sortedByDescending { it.createdAt }
            SortCriteria.STATUS -> result.sortedBy { it.status.ordinal }
            SortCriteria.NAME -> result.sortedBy { it.name }
        }
        
        return result.map { task ->
            task.copy(isExpanded = task.id in expandedTasks)
        }
    }

    private fun updateStats(tasks: List<TaskModel>) {
        _stats.value = PlannerStats(
            total = tasks.size,
            pending = tasks.count { it.status == TaskModel.Status.PENDING },
            running = tasks.count { it.status == TaskModel.Status.RUNNING },
            completed = tasks.count { it.status == TaskModel.Status.COMPLETED },
            failed = tasks.count { it.status == TaskModel.Status.FAILED }
        )
    }

    private fun addLogEntry(entry: ExecutionLogEntry) {
        val current = _executionLogs.value.toMutableList()
        current.add(0, entry)
        if (current.size > 1000) {
            current.removeAt(current.lastIndex)
        }
        _executionLogs.value = current
    }
}

data class TaskFilter(
    val searchQuery: String = "",
    val statuses: List<TaskModel.Status> = emptyList()
)

data class PlannerStats(
    val total: Int = 0,
    val pending: Int = 0,
    val running: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0
)

sealed class PlannerEvent {
    data class ShowToast(val message: String) : PlannerEvent()
    object TaskCreated : PlannerEvent()
    data class ExecutionComplete(val completed: Int, val total: Int) : PlannerEvent()
    object CycleDetected : PlannerEvent()
}

enum class SortCriteria {
    PRIORITY, DATE, STATUS, NAME
}

enum class TaskAction {
    EXECUTE, EDIT, DELETE, ADD_SUBTASK, MARK_COMPLETE, RETRY, DUPLICATE
}

data class ExecutionState(
    val phase: ExecutionPhase,
    val currentTask: TaskModel?,
    val completed: Int,
    val total: Int,
    val error: String? = null
)

enum class ExecutionPhase {
    PLANNING, EXECUTING, COMPLETED, FAILED
}

data class ExecutionLogEntry(
    val taskId: String,
    val timestamp: Long,
    val status: ExecutionStatus,
    val message: String,
    val duration: Long
)

enum class ExecutionStatus {
    SUCCESS, FAILED, RUNNING, PENDING
}
