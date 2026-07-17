package com.nexus.agent.data.local

import androidx.room.*
import com.nexus.agent.core.planner.TaskModel
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannerDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<TaskModel>>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TaskModel?

    @Query("SELECT * FROM tasks WHERE status = 'pending' ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextPending(): TaskModel?

    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'pending'")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE status = :status")
    suspend fun getCountByStatus(status: String): Int

    @Query("UPDATE tasks SET status = :status WHERE id = :taskId")
    suspend fun updateStatus(taskId: String, status: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskModel)

    @Delete
    suspend fun deleteTask(task: TaskModel)

    @Query("DELETE FROM tasks WHERE status = 'completed' AND createdAt < :olderThan")
    suspend fun deleteOldCompleted(olderThan: Long)
}
