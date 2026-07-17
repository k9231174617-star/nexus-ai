package com.nexus.agent.data.local

import androidx.room.TypeConverter
import com.nexus.agent.core.planner.TaskPriority
import com.nexus.agent.core.planner.TaskStatus

/**
 * Room TypeConverters for enum classes.
 * Stores enums as lowercase strings to match existing DAO queries.
 */
class Converters {

    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String = status.name.lowercase()

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus {
        return try {
            TaskStatus.valueOf(value.uppercase())
        } catch (e: IllegalArgumentException) {
            TaskStatus.PENDING
        }
    }

    @TypeConverter
    fun fromTaskPriority(priority: TaskPriority): String = priority.name.lowercase()

    @TypeConverter
    fun toTaskPriority(value: String): TaskPriority {
        return try {
            TaskPriority.valueOf(value.uppercase())
        } catch (e: IllegalArgumentException) {
            TaskPriority.NORMAL
        }
    }
}
