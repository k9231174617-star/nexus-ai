package com.nexus.agent.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nexus.agent.core.chat.MessageModel
import com.nexus.agent.core.memory.MemoryEntry
import com.nexus.agent.core.memory.MemoryDao
import com.nexus.agent.core.planner.TaskModel
import com.nexus.agent.core.graph.EntityNode
import com.nexus.agent.core.graph.RelationEdge
import com.nexus.agent.core.graph.GraphDao
import com.nexus.agent.core.cache.CacheEntry
import com.nexus.agent.core.cache.CacheDao
import com.nexus.agent.core.observability.SpanDao

@Database(
    entities = [
        MessageModel::class,
        SettingsEntity::class,
        MemoryEntry::class,
        TaskModel::class,
        EntityNode::class,
        RelationEdge::class,
        CacheEntry::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun settingsDao(): SettingsDao
    abstract fun memoryDao(): MemoryDao
    abstract fun plannerDao(): PlannerDao
    abstract fun graphDao(): GraphDao
    abstract fun cacheDao(): CacheDao
}