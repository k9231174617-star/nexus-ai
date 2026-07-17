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

@Database(
    entities = [
        MessageModel::class,
        SettingsEntity::class,
        MemoryEntry::class,
        TaskModel::class,
        EntityNode::class,
        RelationEdge::class,
        CacheEntry::class,
        // Observability
        SpanEntity::class,
        TraceEntity::class,
        MetricEntity::class,
        BottleneckEntity::class,
        // Workers
        WorkerEntity::class,
        WorkTaskEntity::class,
        QueueItemEntity::class,
        // CI/CD
        PipelineEntity::class,
        PipelineRunEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@androidx.room.TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nexus_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }

    abstract fun chatDao(): ChatDao
    abstract fun settingsDao(): SettingsDao
    abstract fun memoryDao(): MemoryDao
    abstract fun plannerDao(): PlannerDao
    abstract fun graphDao(): GraphDao
    abstract fun cacheDao(): CacheDao
    abstract fun spanDao(): SpanDao
    abstract fun workerDao(): WorkerDao
    abstract fun cicdDao(): CICDDao
    abstract fun sandboxDao(): SandboxDao
    abstract fun browserDao(): BrowserDao
    abstract fun ragDao(): RAGDao
    abstract fun projectDao(): ProjectDao
}
