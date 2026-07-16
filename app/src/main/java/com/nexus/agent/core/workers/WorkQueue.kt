package com.nexus.agent.core.workers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap

/**
 * WorkQueue — потокобезопасная приоритетная очередь задач.
 * Поддерживает delayed execution, retry с backoff, отслеживание состояния задач.
 */
class WorkQueue(
    private val config: QueueConfig = QueueConfig()
) {
    data class QueueConfig(
        val maxSize: Int = 10_000,           // Максимальный размер очереди
        val defaultTtlMs: Long = 3600_000,   // TTL задачи по умолчанию (1 час)
        val enablePersistence: Boolean = false,
        val persistencePath: String? = null
    )

    /**
     * Состояние задачи в очереди.
     */
    enum class TaskStatus {
        PENDING,      // В очереди, ожидает выполнения
        DELAYED,      // Отложена (scheduledAt в будущем)
        RUNNING,      // Выполняется воркером
        COMPLETED,    // Успешно завершена
        FAILED,       // Завершена с ошибкой (исчерпаны retry)
        CANCELLED,    // Отменена
        RETRYING      // Возвращена в очередь для повторной попытки
    }

    /**
     * Запись о задаче в очереди.
     */
    data class QueueEntry(
        val task: Task,
        var status: TaskStatus,
        var attempts: Int = 0,
        var workerId: String? = null,
        var result: TaskHandler.TaskResult? = null,
        var enqueuedAt: Long = System.currentTimeMillis(),
        var startedAt: Long? = null,
        var completedAt: Long? = null,
        var errorMessage: String? = null
    )

    // Приоритетная очередь (меньший score = выше приоритет)
    private val queue = PriorityQueue<QueueEntry>(compareBy {
        // Сортировка: scheduledAt (null first), затем priority weight (desc), затем createdAt
        val scheduledScore = it.task.scheduledAt ?: 0L
        val priorityScore = -it.task.priority.weight  // Отрицательный для обратного порядка
        val timeScore = it.task.createdAt
        Triple(scheduledScore, priorityScore, timeScore)
    })

    // Индексы для быстрого доступа
    private val entriesById = ConcurrentHashMap<String, QueueEntry>()
    private val completedTasks = ConcurrentHashMap<String, QueueEntry>()
    private val failedTasks = ConcurrentHashMap<String, QueueEntry>()

    private val queueMutex = Mutex()
    private var _size = 0

    // Метрики
    private val metrics = QueueMetrics()

    // ─────────────────────────────────────────────────────────────────────────
    // Enqueue / Dequeue
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Добавляет задачу в очередь.
     *
     * @param delayMs Отложить выполнение на указанное время
     */
    suspend fun enqueue(task: Task, delayMs: Long = 0): Boolean {
        queueMutex.withLock {
            if (_size >= config.maxSize) {
                return false  // Очередь переполнена
            }

            val status = when {
                delayMs > 0 || task.isDelayed() -> TaskStatus.DELAYED
                else -> TaskStatus.PENDING
            }

            val entry = QueueEntry(
                task = if (delayMs > 0) {
                    task.copy(scheduledAt = System.currentTimeMillis() + delayMs)
                } else {
                    task
                },
                status = status
            )

            queue.add(entry)
            entriesById[task.id] = entry
            _size++
            metrics.enqueued.incrementAndGet()

            return true
        }
    }

    /**
     * Добавляет несколько задач пакетом.
     */
    suspend fun enqueueBatch(tasks: List<Task>): Int {
        var count = 0
        tasks.forEach { if (enqueue(it)) count++ }
        return count
    }

    /**
     * Забирает следующую задачу из очереди.
     */
    suspend fun dequeue(): Task? {
        queueMutex.withLock {
            cleanupExpired()

            val now = System.currentTimeMillis()

            // Ищем первую подходящую задачу
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()

                // Пропускаем отложенные задачи
                if (entry.task.scheduledAt != null && entry.task.scheduledAt > now) {
                    continue
                }

                // Проверяем TTL
                if (isExpired(entry)) {
                    iterator.remove()
                    entriesById.remove(entry.task.id)
                    _size--
                    metrics.expired.incrementAndGet()
                    continue
                }

                // Нашли подходящую задачу
                iterator.remove()
                entry.status = TaskStatus.RUNNING
                entry.startedAt = now
                entry.attempts++
                _size--

                metrics.dequeued.incrementAndGet()
                return entry.task
            }

            return null
        }
    }

    /**
     * Просматривает очередь без извлечения (peek).
     */
    suspend fun peek(): Task? {
        queueMutex.withLock {
            return queue.peek()?.task
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Task lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Отмечает задачу как успешно завершённую.
     */
    suspend fun completeTask(task: Task, result: TaskHandler.TaskResult.Success) {
        queueMutex.withLock {
            val entry = entriesById.remove(task.id) ?: return

            entry.status = TaskStatus.COMPLETED
            entry.result = result
            entry.completedAt = System.currentTimeMillis()
            entry.workerId = null

            completedTasks[task.id] = entry
            metrics.completed.incrementAndGet()

            // Ограничиваем размер истории
            if (completedTasks.size > 10000) {
                completedTasks.keys.firstOrNull()?.let { completedTasks.remove(it) }
            }
        }
    }

    /**
     * Отмечает задачу как проваленную.
     */
    suspend fun reportFailure(
        task: Task,
        error: String,
        errorType: TaskHandler.TaskResult.ErrorType = TaskHandler.TaskResult.ErrorType.UNKNOWN
    ) {
        reportFailure(
            task,
            TaskHandler.TaskResult.Failure(error, errorType, isRetryable = false)
        )
    }

    /**
     * Отмечает задачу как проваленную с полным результатом.
     */
    suspend fun reportFailure(task: Task, result: TaskHandler.TaskResult) {
        queueMutex.withLock {
            val entry = entriesById.remove(task.id) ?: return

            entry.status = TaskStatus.FAILED
            entry.result = result
            entry.completedAt = System.currentTimeMillis()
            entry.errorMessage = when (result) {
                is TaskHandler.TaskResult.Failure -> result.error
                else -> "Unknown failure"
            }

            failedTasks[task.id] = entry
            metrics.failed.incrementAndGet()

            if (failedTasks.size > 5000) {
                failedTasks.keys.firstOrNull()?.let { failedTasks.remove(it) }
            }
        }
    }

    /**
     * Отменяет задачу.
     */
    suspend fun cancel(taskId: String, reason: String = "Cancelled by user"): Boolean {
        queueMutex.withLock {
            val entry = entriesById[taskId] ?: return false

            if (entry.status == TaskStatus.RUNNING) {
                return false  // Уже выполняется — отменяет воркер
            }

            queue.remove(entry)
            entriesById.remove(taskId)
            entry.status = TaskStatus.CANCELLED
            entry.errorMessage = reason
            _size--

            metrics.cancelled.incrementAndGet()
            return true
        }
    }

    /**
     * Проверяет, завершена ли задача (успешно или с ошибкой).
     */
    fun isTaskCompleted(taskId: String): Boolean {
        return completedTasks.containsKey(taskId) || failedTasks.containsKey(taskId)
    }

    /**
     * Возвращает результат задачи.
     */
    fun getTaskResult(taskId: String): TaskHandler.TaskResult? {
        return completedTasks[taskId]?.result ?: failedTasks[taskId]?.result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Querying
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Возвращает статус задачи.
     */
    fun getTaskStatus(taskId: String): TaskStatus? {
        return entriesById[taskId]?.status
            ?: if (completedTasks.containsKey(taskId)) TaskStatus.COMPLETED else null
            ?: if (failedTasks.containsKey(taskId)) TaskStatus.FAILED else null
    }

    /**
     * Возвращает информацию о задаче.
     */
    fun getTaskInfo(taskId: String): QueueEntry? {
        return entriesById[taskId]
            ?: completedTasks[taskId]
            ?: failedTasks[taskId]
    }

    /**
     * Возвращает все задачи в очереди.
     */
    suspend fun getPendingTasks(): List<Task> {
        queueMutex.withLock {
            return queue.filter { it.status == TaskStatus.PENDING }.map { it.task }
        }
    }

    /**
     * Возвращает задачи по типу.
     */
    suspend fun getTasksByType(type: Task.TaskType): List<Task> {
        queueMutex.withLock {
            return queue.filter { it.task.type == type }.map { it.task }
        }
    }

    /**
     * Возвращает задачи по тегу.
     */
    suspend fun getTasksByTag(tag: String): List<Task> {
        queueMutex.withLock {
            return queue.filter { tag in it.task.tags }.map { it.task }
        }
    }

    /**
     * Размер очереди (только PENDING + DELAYED).
     */
    suspend fun size(): Int {
        queueMutex.withLock {
            return _size
        }
    }

    /**
     * Проверяет, пуста ли очередь.
     */
    suspend fun isEmpty(): Boolean = size() == 0

    // ─────────────────────────────────────────────────────────────────────────
    // Management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Очищает завершённые задачи старше указанного возраста.
     */
    suspend fun cleanupCompleted(olderThanMs: Long) {
        val cutoff = System.currentTimeMillis() - olderThanMs

        queueMutex.withLock {
            completedTasks.entries.removeIf { it.value.completedAt ?: 0 < cutoff }
            failedTasks.entries.removeIf { it.value.completedAt ?: 0 < cutoff }
        }
    }

    /**
     * Очищает просроченные задачи.
     */
    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val iterator = queue.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (isExpired(entry)) {
                iterator.remove()
                entriesById.remove(entry.task.id)
                _size--
                metrics.expired.incrementAndGet()
            }
        }
    }

    private fun isExpired(entry: QueueEntry): Boolean {
        val ttl = entry.task.metadata["ttlMs"]?.toLongOrNull() ?: config.defaultTtlMs
        return (entry.enqueuedAt + ttl) < System.currentTimeMillis()
    }

    /**
     * Полная очистка очереди.
     */
    suspend fun clear() {
        queueMutex.withLock {
            queue.clear()
            entriesById.clear()
            completedTasks.clear()
            failedTasks.clear()
            _size = 0
            metrics.reset()
        }
    }

    /**
     * Возвращает метрики очереди.
     */
    fun getMetrics(): QueueMetricsSnapshot {
        return QueueMetricsSnapshot(
            pending = _size,
            completed = metrics.completed.get(),
            failed = metrics.failed.get(),
            expired = metrics.expired.get(),
            cancelled = metrics.cancelled.get(),
            totalEnqueued = metrics.enqueued.get(),
            totalDequeued = metrics.dequeued.get()
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Persistence (optional)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Сохраняет состояние очереди (если включена персистентность).
     */
    suspend fun persist() {
        if (!config.enablePersistence || config.persistencePath == null) return

        queueMutex.withLock {
            val snapshot = QueueSnapshot(
                pending = queue.toList().map { it.task },
                completed = completedTasks.values.map { it.task },
                failed = failedTasks.values.map { it.task }
            )

            try {
                val json = Json.encodeToString(snapshot)
                java.io.File(config.persistencePath).writeText(json)
            } catch (e: Exception) {
                // Логирование ошибки
            }
        }
    }

    /**
     * Восстанавливает состояние очереди.
     */
    suspend fun restore() {
        if (!config.enablePersistence || config.persistencePath == null) return

        try {
            val file = java.io.File(config.persistencePath)
            if (!file.exists()) return

            val json = file.readText()
            val snapshot = Json.decodeFromString<QueueSnapshot>(json)

            snapshot.pending.forEach { enqueue(it) }
        } catch (e: Exception) {
            // Логирование ошибки
        }
    }

    @kotlinx.serialization.Serializable
    private data class QueueSnapshot(
        val pending: List<Task>,
        val completed: List<Task>,
        val failed: List<Task>
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Metrics
    // ─────────────────────────────────────────────────────────────────────────

    data class QueueMetricsSnapshot(
        val pending: Int,
        val completed: Long,
        val failed: Long,
        val expired: Long,
        val cancelled: Long,
        val totalEnqueued: Long,
        val totalDequeued: Long
    )

    private class QueueMetrics {
        val enqueued = java.util.concurrent.atomic.AtomicLong(0)
        val dequeued = java.util.concurrent.atomic.AtomicLong(0)
        val completed = java.util.concurrent.atomic.AtomicLong(0)
        val failed = java.util.concurrent.atomic.AtomicLong(0)
        val expired = java.util.concurrent.atomic.AtomicLong(0)
        val cancelled = java.util.concurrent.atomic.AtomicLong(0)

        fun reset() {
            enqueued.set(0)
            dequeued.set(0)
            completed.set(0)
            failed.set(0)
            expired.set(0)
            cancelled.set(0)
        }
    }
}
