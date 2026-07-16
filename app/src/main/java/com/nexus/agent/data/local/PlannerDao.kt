package com.nexus.agent.data.local

import androidx.room.*
import com.nexus.agent.core.planner.TaskModel
import com.nexus.agent.core.planner.TaskStatus

@Dao
interface PlannerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskModel)

    @Update
    suspend fun update(task: TaskModel)

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    suspend fun getAll(): List<TaskModel>

    @Query("SELECT * FROM tasks WHERE status = :status")
    suspend fun getByStatus(status: TaskStatus): List<TaskModel>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId")
    suspend fun getChildren(parentId: String): List<TaskModel>

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}