package com.nexus.agent.core.planner

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskStatus { PENDING, RUNNING, DONE, FAILED, CANCELLED }
enum class TaskPriority { LOW, NORMAL, HIGH, CRITICAL }

@Entity(tableName = "tasks")
data class TaskModel(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val agentType: String = "MAIN",
    val status: TaskStatus = TaskStatus.PENDING,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val dependsOn: String = "[]",    // JSON array of task IDs
    val result: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val estimatedTokens: Int = 0,
    val actualTokens: Int = 0,
    val parentTaskId: String? = null,
    val metadata: String = "{}",
)