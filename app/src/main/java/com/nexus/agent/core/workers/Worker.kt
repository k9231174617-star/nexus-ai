package com.nexus.agent.core.workers

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Worker — единица исполнения, которая забирает задачи из очереди и выполняет их.
 * Поддерживает параллельное выполнение, heartbeat, graceful shutdown.
 */
class Worker(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "worker-$id",
    private val workQueue: WorkQueue,
    private val registry: WorkerRegistry,
    private val handlers: List<TaskHandler>,
    private val config: WorkerConfig = WorkerConfig()
) {
    data class WorkerConfig(
        val maxConcurrentTasks: Int = 4,           // Максимум параллельных задач
        val pollIntervalMs: Long = 1000,           // Интервал опроса очереди
        val heartbeatIntervalMs: Long = 5000,      // Интервал heartbeat
        val shutdownTimeoutMs: Long = 30000,       // Таймаут graceful shutdown
        val enableProgressReporting: Boolean = true
    )

    /**
     * Состояние воркера.
     */
    enum class State {
        IDLE,       // Простаивает, ждёт задач
        BUSY,       // Выполняет задачи
        PAUSED,     // Приостановлен
        SHUTTING_DOWN,  // Завершает работу
        STOPPED     // Остановлен
    }

    /**
     * События жизненного цикла воркера.
     */
    sealed class WorkerEvent {
        data class TaskStarted(val taskId: String, val type: Task.TaskType) : WorkerEvent()
        data class TaskCompleted(val taskId: String, val result: TaskHandler.TaskResult) : WorkerEvent()
        data class TaskFailed(val taskId: String, val error: String, val willRetry: Boolean) : WorkerEvent()
        data class Heartbeat(val workerId: String, val activeTasks: Int, val queueSize: Int) : WorkerEvent()
        data class StateChanged(val from: State, val to: State) : WorkerEvent()
        data class Error(val message: String, val throwable: Throwable? = null) : WorkerEvent()
    }

    @Volatile
    private var currentState: State = State.IDLE
    private val stateMutex = Any()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isRunning = AtomicBoolean(false)
    private val activeTaskCount = AtomicInteger(0)
    private val totalTasksProcessed = AtomicInteger(0)
    private val totalTasksFailed = AtomicInteger(0)

    // Отслеживание выполняемых задач
    private val runningTasks = ConcurrentHashMap<String, Job>()
    private val cancellationTokens = ConcurrentHashMap<String, TaskHandler.CancellationToken>()

    private val _events = MutableSharedFlow<WorkerEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<WorkerEvent> = _events.asSharedFlow()

    // Метрики
    private val taskDurations = mutableListOf<Long>()
    private val metricsMutex = Any()

    /**
     * Запускает воркер.
     */
    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            changeState(State.IDLE)
            registry.register(this)

            // Основной цикл обработки задач
            scope.launch { processingLoop() }

            // Heartbeat
            scope.launch { heartbeatLoop() }

            // Мониторинг состояния
            scope.launch { stateMonitorLoop() }
        }
    }

    /**
     * Останавливает воркер (graceful shutdown).
     */
    suspend fun stop() {
        if (!isRunning.get()) return

        changeState(State.SHUTTING_DOWN)

        // Отменяем все выполняемые задачи
        runningTasks.values.forEach { it.cancel() }
        cancellationTokens.values.forEach { it.cancel() }

        // Ждём завершения с таймаутом
        withTimeoutOrNull(config.shutdownTimeoutMs) {
            while (activeTaskCount.get() > 0) {
                delay(100)
            }
        }

        scope.cancel()
        registry.unregister(this)
        changeState(State.STOPPED)
        isRunning.set(false)
    }

    /**
     * Приостанавливает воркер (не берёт новые задачи, но завершает текущие).
     */
    fun pause() {
        if (currentState == State.BUSY || currentState == State.IDLE) {
            changeState(State.PAUSED)
        }
    }

    /**
     * Возобновляет работу.
     */
    fun resume() {
        if (currentState == State.PAUSED) {
            changeState(State.IDLE)
        }
    }

    /**
     * Отменяет конкретную задачу.
     */
    fun cancelTask(taskId: String): Boolean {
        cancellationTokens[taskId]?.cancel()
        runningTasks[taskId]?.cancel()
        return runningTasks.containsKey(taskId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal loops
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun processingLoop() {
        while (isRunning.get() && currentState != State.STOPPED) {
            try {
                if (currentState == State.PAUSED || currentState == State.SHUTTING_DOWN) {
                    delay(config.pollIntervalMs)
                    continue
                }

                // Проверяем лимит параллельных задач
                if (activeTaskCount.get() >= config.maxConcurrentTasks) {
                    delay(100)
                    continue
                }

                // Забираем задачу из очереди
                val task = workQueue.dequeue()

                if (task != null) {
                    // Проверяем зависимости
                    if (!areDependenciesMet(task)) {
                        workQueue.enqueue(task)  // Возвращаем в очередь
                        delay(config.pollIntervalMs)
                        continue
                    }

                    // Находим подходящий handler
                    val handler = handlers.firstOrNull { it.canHandle(task.type) }

                    if (handler == null) {
                        workQueue.reportFailure(
                            task,
                            "No handler found for task type: ${task.type}",
                            TaskHandler.TaskResult.ErrorType.INVALID_INPUT
                        )
                        continue
                    }

                    // Запускаем задачу
                    launchTask(task, handler)
                } else {
                    // Очередь пуста — ждём
                    if (activeTaskCount.get() == 0) {
                        changeState(State.IDLE)
                    }
                    delay(config.pollIntervalMs)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitEvent(WorkerEvent.Error("Processing loop error", e))
                delay(config.pollIntervalMs)
            }
        }
    }

    private fun launchTask(task: Task, handler: TaskHandler) {
        val token = TaskHandler.CancellationToken()
        cancellationTokens[task.id] = token

        val job = scope.launch {
            activeTaskCount.incrementAndGet()
            changeState(State.BUSY)

            val startTime = System.currentTimeMillis()
            emitEvent(WorkerEvent.TaskStarted(task.id, task.type))

            val progressReporter = if (config.enableProgressReporting) {
                FlowProgressReporter()
            } else {
                object : TaskHandler.ProgressReporter {
                    override suspend fun report(progress: Double, message: String) {}
                    override suspend fun reportStage(stage: String, detail: String) {}
                }
            }

            val context = TaskHandler.ExecutionContext(
                taskId = task.id,
                workerId = id,
                attemptNumber = task.retryCount() + 1,
                cancellationToken = token,
                progressReporter = progressReporter
            )

            try {
                // Валидация
                handler.validatePayload(task)?.let { validationError ->
                    workQueue.reportFailure(task, validationError)
                    return@launch
                }

                // Предобработка
                if (!handler.beforeExecute(task, context)) {
                    workQueue.reportFailure(task, "Pre-execution check failed")
                    return@launch
                }

                // Выполнение
                val result = handler.execute(task, context)

                // Постобработка
                handler.afterExecute(task, context, result)

                val executionTime = System.currentTimeMillis() - startTime

                when (result) {
                    is TaskHandler.TaskResult.Success -> {
                        workQueue.completeTask(task, result)
                        totalTasksProcessed.incrementAndGet()
                        emitEvent(WorkerEvent.TaskCompleted(task.id, result))

                        synchronized(metricsMutex) {
                            taskDurations.add(executionTime)
                            if (taskDurations.size > 1000) taskDurations.removeAt(0)
                        }
                    }
                    is TaskHandler.TaskResult.Failure -> {
                        handleFailure(task, result, executionTime)
                    }
                    is TaskHandler.TaskResult.Cancelled -> {
                        workQueue.reportFailure(task, "Task cancelled: ${result.reason}")
                        emitEvent(WorkerEvent.TaskFailed(task.id, result.reason, false))
                    }
                }
            } catch (e: CancellationException) {
                workQueue.reportFailure(task, "Task cancelled by worker shutdown")
                emitEvent(WorkerEvent.TaskFailed(task.id, "Cancelled", false))
            } catch (e: Exception) {
                val errorResult = TaskHandler.TaskResult.Failure(
                    error = e.message ?: "Unknown error",
                    errorType = TaskHandler.TaskResult.ErrorType.UNKNOWN,
                    isRetryable = true,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
                handleFailure(task, errorResult, errorResult.executionTimeMs)
            } finally {
                activeTaskCount.decrementAndGet()
                cancellationTokens.remove(task.id)
                runningTasks.remove(task.id)
            }
        }

        runningTasks[task.id] = job
    }

    private suspend fun handleFailure(task: Task, result: TaskHandler.TaskResult.Failure, executionTime: Long) {
        totalTasksFailed.incrementAndGet()

        val shouldRetry = result.isRetryable && task.retryCount() < task.maxRetries

        emitEvent(
            WorkerEvent.TaskFailed(
                taskId = task.id,
                error = result.error,
                willRetry = shouldRetry
            )
        )

        if (shouldRetry) {
            val retryTask = task.withRetryIncrement()
            workQueue.enqueue(retryTask, delayMs = calculateBackoff(task.retryCount() + 1))
        } else {
            workQueue.reportFailure(task, result)
        }
    }

    private fun calculateBackoff(retryCount: Int): Long {
        // Экспоненциальный backoff с jitter
        val base = 1000L * (1L shl retryCount.coerceAtMost(6))  // max ~64s
        val jitter = (Math.random() * 1000).toLong()
        return base + jitter
    }

    private suspend fun heartbeatLoop() {
        while (isRunning.get() && currentState != State.STOPPED) {
            try {
                delay(config.heartbeatIntervalMs)

                val event = WorkerEvent.Heartbeat(
                    workerId = id,
                    activeTasks = activeTaskCount.get(),
                    queueSize = workQueue.size()
                )
                emitEvent(event)
                registry.updateHeartbeat(this)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitEvent(WorkerEvent.Error("Heartbeat error", e))
            }
        }
    }

    private suspend fun stateMonitorLoop() {
        while (isRunning.get()) {
            try {
                delay(5000)
                // Автоматически переходим в IDLE если нет активных задач
                if (currentState == State.BUSY && activeTaskCount.get() == 0) {
                    changeState(State.IDLE)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { /* ignore */ }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun areDependenciesMet(task: Task): Boolean {
        if (task.dependencies.isEmpty()) return true
        // Проверяем, выполнены ли все зависимости
        // В реальном проекте — проверка через WorkQueue или хранилище
        return task.dependencies.all { depId ->
            workQueue.isTaskCompleted(depId)
        }
    }

    private fun changeState(newState: State) {
        synchronized(stateMutex) {
            val oldState = currentState
            if (oldState != newState) {
                currentState = newState
                emitEvent(WorkerEvent.StateChanged(oldState, newState))
            }
        }
    }

    private fun emitEvent(event: WorkerEvent) {
        scope.launch {
            _events.emit(event)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    fun getState(): State = currentState

    fun getActiveTaskCount(): Int = activeTaskCount.get()

    fun getTotalProcessed(): Int = totalTasksProcessed.get()

    fun getTotalFailed(): Int = totalTasksFailed.get()

    fun getRunningTaskIds(): List<String> = runningTasks.keys.toList()

    fun getMetrics(): WorkerMetrics {
        val durations = synchronized(metricsMutex) { taskDurations.toList() }
        return WorkerMetrics(
            totalProcessed = totalTasksProcessed.get(),
            totalFailed = totalTasksFailed.get(),
            avgTaskDurationMs = if (durations.isNotEmpty()) durations.average() else 0.0,
            maxTaskDurationMs = durations.maxOrNull() ?: 0,
            minTaskDurationMs = durations.minOrNull() ?: 0,
            currentActiveTasks = activeTaskCount.get(),
            state = currentState
        )
    }

    data class WorkerMetrics(
        val totalProcessed: Int,
        val totalFailed: Int,
        val avgTaskDurationMs: Double,
        val maxTaskDurationMs: Long,
        val minTaskDurationMs: Long,
        val currentActiveTasks: Int,
        val state: State
    )
}
