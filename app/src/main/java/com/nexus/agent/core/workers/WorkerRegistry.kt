package com.nexus.agent.core.workers

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * WorkerRegistry — центральный реестр всех воркеров в системе.
 * Управляет регистрацией, обнаружением, балансировкой нагрузки и failover.
 */
class WorkerRegistry(
    private val config: RegistryConfig = RegistryConfig()
) {
    data class RegistryConfig(
        val heartbeatTimeoutMs: Long = 15000,    // Таймаут heartbeat (воркер считается мёртвым)
        val healthCheckIntervalMs: Long = 10000,  // Интервал проверки здоровья
        val enableAutoScaling: Boolean = false,   // Автомасштабирование
        val minWorkers: Int = 1,
        val maxWorkers: Int = 10,
        val taskCapacityThreshold: Double = 0.8   // Порог загрузки для масштабирования
    )

    /**
     * Информация о зарегистрированном воркере.
     */
    data class WorkerInfo(
        val worker: Worker,
        val registeredAt: Long = System.currentTimeMillis(),
        @Volatile var lastHeartbeat: Long = System.currentTimeMillis(),
        @Volatile var status: WorkerStatus = WorkerStatus.HEALTHY,
        @Volatile var totalTasksProcessed: Int = 0,
        @Volatile var currentLoad: Double = 0.0  // 0.0 - 1.0
    )

    enum class WorkerStatus {
        HEALTHY,      // Работает нормально
        OVERLOADED,   // Перегружен
        UNRESPONSIVE, // Не отвечает на heartbeat
        DEGRADED,     // Работает с деградацией
        OFFLINE       // Офлайн
    }

    /**
     * События реестра.
     */
    sealed class RegistryEvent {
        data class WorkerRegistered(val workerId: String, val workerName: String) : RegistryEvent()
        data class WorkerUnregistered(val workerId: String, val reason: String) : RegistryEvent()
        data class WorkerFailed(val workerId: String, val error: String) : RegistryEvent()
        data class HeartbeatMissed(val workerId: String, val missedCount: Int) : RegistryEvent()
        data class LoadRebalanced(val fromWorker: String, val toWorker: String, val taskCount: Int) : RegistryEvent()
        data class AutoScaled(val action: ScaleAction, val workerCount: Int) : RegistryEvent()

        enum class ScaleAction {
            SCALE_UP, SCALE_DOWN
        }
    }

    private val workers = ConcurrentHashMap<String, WorkerInfo>()
    private val workersByCapability = ConcurrentHashMap<Task.TaskType, MutableSet<String>>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _events = MutableSharedFlow<RegistryEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<RegistryEvent> = _events.asSharedFlow()

    private val totalCapacity = AtomicInteger(0)
    private val isRunning = kotlinx.coroutines.atomic.AtomicBoolean(false)

    // Стратегия балансировки
    private var balancingStrategy: LoadBalancingStrategy = RoundRobinStrategy()

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            scope.launch { healthCheckLoop() }
            scope.launch { loadMonitorLoop() }
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            scope.cancel()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Registration
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Регистрирует воркер в реестре.
     */
    fun register(worker: Worker): Boolean {
        if (workers.containsKey(worker.id)) {
            return false  // Уже зарегистрирован
        }

        val info = WorkerInfo(worker = worker)
        workers[worker.id] = info

        // Индексируем по capabilities (типам задач)
        // В реальном проекте — определяем по handlers воркера
        Task.TaskType.values().forEach { type ->
            workersByCapability.getOrPut(type) { ConcurrentHashMap.newKeySet() }.add(worker.id)
        }

        totalCapacity.addAndGet(worker.config.maxConcurrentTasks)

        emitEvent(RegistryEvent.WorkerRegistered(worker.id, worker.name))

        // Подписываемся на события воркера
        scope.launch {
            worker.events.collect { event ->
                handleWorkerEvent(worker.id, event)
            }
        }

        return true
    }

    /**
     * Удаляет воркер из реестра.
     */
    fun unregister(worker: Worker, reason: String = "Normal shutdown") {
        val info = workers.remove(worker.id) ?: return

        // Удаляем из индексов capabilities
        workersByCapability.values.forEach { it.remove(worker.id) }

        totalCapacity.addAndGet(-worker.config.maxConcurrentTasks)

        emitEvent(RegistryEvent.WorkerUnregistered(worker.id, reason))
    }

    /**
     * Обновляет heartbeat воркера.
     */
    fun updateHeartbeat(worker: Worker) {
        workers[worker.id]?.let { info ->
            info.lastHeartbeat = System.currentTimeMillis()
            if (info.status == WorkerStatus.UNRESPONSIVE) {
                info.status = WorkerStatus.HEALTHY
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Discovery
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Возвращает воркер по ID.
     */
    fun getWorker(id: String): Worker? = workers[id]?.worker

    /**
     * Возвращает все зарегистрированные воркеры.
     */
    fun getAllWorkers(): List<Worker> = workers.values.map { it.worker }

    /**
     * Возвращает здоровые воркеры.
     */
    fun getHealthyWorkers(): List<Worker> = workers.values
        .filter { it.status == WorkerStatus.HEALTHY }
        .map { it.worker }

    /**
     * Возвращает воркеры, способные обработать задачу данного типа.
     */
    fun getWorkersForTaskType(type: Task.TaskType): List<Worker> {
        val capableIds = workersByCapability[type] ?: return emptyList()
        return capableIds.mapNotNull { workers[it]?.worker }
    }

    /**
     * Возвращает количество активных воркеров.
     */
    fun getWorkerCount(): Int = workers.size

    /**
     * Возвращает общую ёмкость системы.
     */
    fun getTotalCapacity(): Int = totalCapacity.get()

    /**
     * Возвращает текущую нагрузку системы.
     */
    fun getSystemLoad(): Double {
        val totalActive = workers.values.sumOf { it.worker.getActiveTaskCount() }
        val capacity = totalCapacity.get()
        return if (capacity > 0) totalActive.toDouble() / capacity else 0.0
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Load Balancing
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Выбирает лучший воркер для задачи.
     */
    fun selectWorkerForTask(task: Task): Worker? {
        val candidates = getWorkersForTaskType(task.type)
            .filter { it.getState() != Worker.State.PAUSED && it.getState() != Worker.State.SHUTTING_DOWN }

        if (candidates.isEmpty()) return null

        return balancingStrategy.select(candidates, task)
    }

    /**
     * Устанавливает стратегию балансировки.
     */
    fun setBalancingStrategy(strategy: LoadBalancingStrategy) {
        balancingStrategy = strategy
    }

    /**
     * Интерфейс стратегии балансировки нагрузки.
     */
    interface LoadBalancingStrategy {
        fun select(candidates: List<Worker>, task: Task): Worker?
    }

    /**
     * Round Robin — циклическое распределение.
     */
    class RoundRobinStrategy : LoadBalancingStrategy {
        private val counter = AtomicInteger(0)

        override fun select(candidates: List<Worker>, task: Task): Worker? {
            if (candidates.isEmpty()) return null
            val index = counter.getAndIncrement() % candidates.size
            return candidates[index.coerceAtLeast(0)]
        }
    }

    /**
     * Least Connections — воркер с наименьшим количеством активных задач.
     */
    class LeastConnectionsStrategy : LoadBalancingStrategy {
        override fun select(candidates: List<Worker>, task: Task): Worker? {
            return candidates.minByOrNull { it.getActiveTaskCount() }
        }
    }

    /**
     * Weighted Response Time — учитывает latency и загрузку.
     */
    class WeightedLatencyStrategy : LoadBalancingStrategy {
        override fun select(candidates: List<Worker>, task: Task): Worker? {
            return candidates.minByOrNull { worker ->
                val active = worker.getActiveTaskCount()
                val maxConcurrent = worker.config.maxConcurrentTasks
                val loadFactor = if (maxConcurrent > 0) active.toDouble() / maxConcurrent else 1.0

                // Чем меньше loadFactor, тем лучше
                loadFactor * 1000 + (worker.getMetrics().avgTaskDurationMs)
            }
        }
    }

    /**
     * Priority-aware — учитывает приоритет задачи и специализацию воркера.
     */
    class PriorityAwareStrategy : LoadBalancingStrategy {
        override fun select(candidates: List<Worker>, task: Task): Worker? {
            return candidates.maxByOrNull { worker ->
                val availableSlots = worker.config.maxConcurrentTasks - worker.getActiveTaskCount()
                val healthBonus = if (worker.getState() == Worker.State.IDLE) 10 else 0
                val priorityMatch = if (task.priority == Task.Priority.CRITICAL) availableSlots * 2 else availableSlots

                priorityMatch + healthBonus
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Health monitoring
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun healthCheckLoop() {
        while (isRunning.value) {
            try {
                delay(config.healthCheckIntervalMs)

                val now = System.currentTimeMillis()
                val deadWorkers = mutableListOf<String>()

                workers.forEach { (id, info) ->
                    val timeSinceHeartbeat = now - info.lastHeartbeat

                    when {
                        timeSinceHeartbeat > config.heartbeatTimeoutMs * 2 -> {
                            info.status = WorkerStatus.OFFLINE
                            deadWorkers.add(id)
                            emitEvent(RegistryEvent.WorkerFailed(id, "Worker offline (no heartbeat)"))
                        }
                        timeSinceHeartbeat > config.heartbeatTimeoutMs -> {
                            info.status = WorkerStatus.UNRESPONSIVE
                            emitEvent(RegistryEvent.HeartbeatMissed(id, 1))
                        }
                        info.worker.getActiveTaskCount() >= info.worker.config.maxConcurrentTasks -> {
                            info.status = WorkerStatus.OVERLOADED
                            info.currentLoad = 1.0
                        }
                        info.worker.getActiveTaskCount() >= info.worker.config.maxConcurrentTasks * 0.8 -> {
                            info.status = WorkerStatus.DEGRADED
                            info.currentLoad = 0.8
                        }
                        else -> {
                            info.status = WorkerStatus.HEALTHY
                            info.currentLoad = info.worker.getActiveTaskCount().toDouble() /
                                    info.worker.config.maxConcurrentTasks
                        }
                    }
                }

                // Удаляем мёртвых воркеров
                deadWorkers.forEach { id ->
                    workers[id]?.worker?.let { unregister(it, "Health check: offline") }
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Логирование
            }
        }
    }

    private suspend fun loadMonitorLoop() {
        if (!config.enableAutoScaling) return

        while (isRunning.value) {
            try {
                delay(30000)  // Проверка каждые 30 секунд

                val load = getSystemLoad()
                val workerCount = getWorkerCount()

                when {
                    load > config.taskCapacityThreshold && workerCount < config.maxWorkers -> {
                        emitEvent(RegistryEvent.AutoScaled(RegistryEvent.ScaleAction.SCALE_UP, workerCount + 1))
                        // В реальном проекте — запуск нового воркера
                    }
                    load < 0.2 && workerCount > config.minWorkers -> {
                        emitEvent(RegistryEvent.AutoScaled(RegistryEvent.ScaleAction.SCALE_DOWN, workerCount - 1))
                        // В реальном проекте — остановка лишнего воркера
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { /* ignore */ }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event handling
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleWorkerEvent(workerId: String, event: Worker.WorkerEvent) {
        when (event) {
            is Worker.WorkerEvent.TaskCompleted -> {
                workers[workerId]?.let { info ->
                    info.totalTasksProcessed++
                }
            }
            is Worker.WorkerEvent.TaskFailed -> {
                // Можно реализовать circuit breaker
            }
            is Worker.WorkerEvent.Error -> {
                workers[workerId]?.let { info ->
                    info.status = WorkerStatus.DEGRADED
                }
                emitEvent(RegistryEvent.WorkerFailed(workerId, event.message))
            }
            else -> { /* ignore */ }
        }
    }

    private fun emitEvent(event: RegistryEvent) {
        scope.launch {
            _events.emit(event)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Statistics
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Возвращает агрегированную статистику по всем воркерам.
     */
    fun getSystemStats(): SystemStats {
        val workerStats = workers.values.map {
            WorkerStats(
                id = it.worker.id,
                name = it.worker.name,
                status = it.status,
                activeTasks = it.worker.getActiveTaskCount(),
                totalProcessed = it.worker.getTotalProcessed(),
                totalFailed = it.worker.getTotalFailed(),
                currentLoad = it.currentLoad,
                metrics = it.worker.getMetrics()
            )
        }

        return SystemStats(
            totalWorkers = workers.size,
            healthyWorkers = workers.count { it.value.status == WorkerStatus.HEALTHY },
            totalCapacity = totalCapacity.get(),
            systemLoad = getSystemLoad(),
            totalTasksProcessed = workerStats.sumOf { it.totalProcessed },
            totalTasksFailed = workerStats.sumOf { it.totalFailed },
            workerStats = workerStats
        )
    }

    data class SystemStats(
        val totalWorkers: Int,
        val healthyWorkers: Int,
        val totalCapacity: Int,
        val systemLoad: Double,
        val totalTasksProcessed: Int,
        val totalTasksFailed: Int,
        val workerStats: List<WorkerStats>
    )

    data class WorkerStats(
        val id: String,
        val name: String,
        val status: WorkerStatus,
        val activeTasks: Int,
        val totalProcessed: Int,
        val totalFailed: Int,
        val currentLoad: Double,
        val metrics: Worker.WorkerMetrics
    )
}
