package com.nexus.agent.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "workers")
data class WorkerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // local, remote, gpu, etc.
    val status: String = "idle", // idle, busy, offline, error
    val endpoint: String? = null, // URL or socket address
    val capabilities: String = "", // JSON array of supported tasks
    val priority: Int = 0, // higher = more preferred
    val lastHeartbeatAt: Long = System.currentTimeMillis(),
    val registeredAt: Long = System.currentTimeMillis(),
    val totalTasksCompleted: Long = 0,
    val totalTasksFailed: Long = 0,
    val avgExecutionTimeMs: Long = 0,
    val currentLoad: Float = 0f, // 0.0 - 1.0
    val maxConcurrentTasks: Int = 1,
    val metadata: String = "" // JSON serialized extra info
)

@Entity(tableName = "work_tasks")
data class WorkTaskEntity(
    @PrimaryKey val id: String,
    val workerId: String? = null, // null = unassigned
    val taskType: String, // code_gen, analysis, embedding, etc.
    val payload: String, // JSON serialized task data
    val priority: Int = 0, // higher = more urgent
    val status: String = "pending", // pending, assigned, running, completed, failed, cancelled
    val result: String? = null, // JSON serialized result
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val assignedAt: Long? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val dependencies: String = "" // comma-separated task IDs
)

@Entity(tableName = "work_queue")
data class QueueItemEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val priority: Int = 0,
    val enqueuedAt: Long = System.currentTimeMillis(),
    val position: Int = 0
)

@Dao
interface WorkerDao {
    // Workers
    @Query("SELECT * FROM workers ORDER BY priority DESC, lastHeartbeatAt DESC")
    fun getAllWorkers(): Flow<List<WorkerEntity>>

    @Query("SELECT * FROM workers WHERE id = :workerId")
    suspend fun getWorkerById(workerId: String): WorkerEntity?

    @Query("SELECT * FROM workers WHERE status = :status")
    suspend fun getWorkersByStatus(status: String): List<WorkerEntity>

    @Query("SELECT * FROM workers WHERE status = 'idle' AND currentLoad < 0.8 ORDER BY priority DESC LIMIT :limit")
    suspend fun getAvailableWorkers(limit: Int = 5): List<WorkerEntity>

    @Query("SELECT * FROM workers WHERE capabilities LIKE '%' || :capability || '%' AND status != 'offline'")
    suspend fun getWorkersByCapability(capability: String): List<WorkerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun registerWorker(worker: WorkerEntity)

    @Update
    suspend fun updateWorker(worker: WorkerEntity)

    @Query("UPDATE workers SET status = :status, lastHeartbeatAt = :timestamp WHERE id = :workerId")
    suspend fun updateWorkerStatus(workerId: String, status: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE workers SET currentLoad = :load, totalTasksCompleted = totalTasksCompleted + :completedDelta WHERE id = :workerId")
    suspend fun updateWorkerLoad(workerId: String, load: Float, completedDelta: Long = 0)

    @Query("UPDATE workers SET totalTasksFailed = totalTasksFailed + 1 WHERE id = :workerId")
    suspend fun incrementFailedCount(workerId: String)

    @Delete
    suspend fun unregisterWorker(worker: WorkerEntity)

    @Query("DELETE FROM workers WHERE lastHeartbeatAt < :staleThreshold")
    suspend fun removeStaleWorkers(staleThreshold: Long)

    @Query("SELECT COUNT(*) FROM workers")
    fun getWorkerCount(): Flow<Int>

    // Tasks
    @Query("SELECT * FROM work_tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<WorkTaskEntity>>

    @Query("SELECT * FROM work_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): WorkTaskEntity?

    @Query("SELECT * FROM work_tasks WHERE status = :status ORDER BY priority DESC, createdAt ASC")
    fun getTasksByStatus(status: String): Flow<List<WorkTaskEntity>>

    @Query("SELECT * FROM work_tasks WHERE workerId = :workerId AND status = 'running'")
    suspend fun getRunningTasksForWorker(workerId: String): List<WorkTaskEntity>

    @Query("SELECT * FROM work_tasks WHERE status = 'pending' ORDER BY priority DESC, createdAt ASC LIMIT :limit")
    suspend fun getPendingTasks(limit: Int = 10): List<WorkTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: WorkTaskEntity)

    @Update
    suspend fun updateTask(task: WorkTaskEntity)

    @Query("UPDATE work_tasks SET status = :status, workerId = :workerId, assignedAt = :timestamp WHERE id = :taskId")
    suspend fun assignTask(taskId: String, workerId: String, status: String = "assigned", timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE work_tasks SET status = 'running', startedAt = :timestamp WHERE id = :taskId")
    suspend fun markTaskRunning(taskId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE work_tasks SET status = :status, result = :result, completedAt = :timestamp WHERE id = :taskId")
    suspend fun completeTask(taskId: String, status: String, result: String?, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE work_tasks SET status = 'failed', errorMessage = :error, retryCount = retryCount + 1 WHERE id = :taskId")
    suspend fun failTask(taskId: String, error: String?)

    @Query("UPDATE work_tasks SET status = 'pending', workerId = NULL, assignedAt = NULL WHERE id = :taskId")
    suspend fun requeueTask(taskId: String)

    @Delete
    suspend fun deleteTask(task: WorkTaskEntity)

    @Query("DELETE FROM work_tasks WHERE completedAt < :olderThan AND status IN ('completed', 'failed', 'cancelled')")
    suspend fun deleteOldTasks(olderThan: Long)

    @Query("SELECT COUNT(*) FROM work_tasks WHERE status = :status")
    suspend fun getTaskCountByStatus(status: String): Int

    // Queue
    @Query("SELECT * FROM work_queue ORDER BY position ASC")
    fun getQueue(): Flow<List<QueueItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: QueueItemEntity)

    @Query("DELETE FROM work_queue WHERE taskId = :taskId")
    suspend fun dequeue(taskId: String)

    @Query("DELETE FROM work_queue")
    suspend fun clearQueue()
}
