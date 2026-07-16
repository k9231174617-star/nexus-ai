package com.nexus.agent.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "spans")
data class SpanEntity(
    @PrimaryKey val id: String,
    val traceId: String,
    val parentId: String? = null, // null = root span
    val name: String, // operation name
    val service: String, // which service/module
    val status: String = "ok", // ok, error, cancelled
    val startTime: Long,
    val endTime: Long? = null,
    val durationMs: Long? = null,
    val tags: String = "", // JSON serialized key-value pairs
    val logs: String = "", // JSON serialized events
    val errorMessage: String? = null,
    val errorType: String? = null,
    val stackTrace: String? = null
)

@Entity(tableName = "traces")
data class TraceEntity(
    @PrimaryKey val traceId: String,
    val rootSpanId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val totalDurationMs: Long? = null,
    val spanCount: Int = 0,
    val errorCount: Int = 0,
    val status: String = "ok",
    val tags: String = ""
)

@Entity(tableName = "metrics")
data class MetricEntity(
    @PrimaryKey val id: String,
    val name: String, // metric name
    val type: String, // counter, gauge, histogram, summary
    val value: Double,
    val labels: String = "", // JSON serialized labels
    val timestamp: Long = System.currentTimeMillis(),
    val service: String = "nexus-agent",
    val unit: String? = null
)

@Entity(tableName = "bottlenecks")
data class BottleneckEntity(
    @PrimaryKey val id: String,
    val traceId: String,
    val spanId: String,
    val name: String,
    val severity: String, // low, medium, high, critical
    val durationMs: Long,
    val thresholdMs: Long,
    val description: String,
    val suggestedAction: String? = null,
    val detectedAt: Long = System.currentTimeMillis(),
    val isResolved: Boolean = false,
    val resolvedAt: Long? = null
)

@Dao
interface SpanDao {
    // Spans
    @Query("SELECT * FROM spans WHERE traceId = :traceId ORDER BY startTime ASC")
    suspend fun getSpansByTrace(traceId: String): List<SpanEntity>

    @Query("SELECT * FROM spans WHERE id = :spanId")
    suspend fun getSpanById(spanId: String): SpanEntity?

    @Query("SELECT * FROM spans WHERE startTime > :since ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentSpans(since: Long, limit: Int = 100): List<SpanEntity>

    @Query("SELECT * FROM spans WHERE service = :service AND startTime > :since ORDER BY startTime DESC")
    suspend fun getSpansByService(service: String, since: Long): List<SpanEntity>

    @Query("SELECT * FROM spans WHERE status = 'error' AND startTime > :since ORDER BY startTime DESC")
    suspend fun getErrorSpans(since: Long): List<SpanEntity>

    @Query("SELECT * FROM spans WHERE durationMs > :thresholdMs AND startTime > :since ORDER BY durationMs DESC")
    suspend fun getSlowSpans(thresholdMs: Long, since: Long): List<SpanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpan(span: SpanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpans(spans: List<SpanEntity>)

    @Query("UPDATE spans SET endTime = :endTime, durationMs = :durationMs, status = :status WHERE id = :spanId")
    suspend fun finishSpan(spanId: String, endTime: Long, durationMs: Long, status: String = "ok")

    @Query("UPDATE spans SET status = 'error', errorMessage = :error, errorType = :errorType, stackTrace = :stackTrace WHERE id = :spanId")
    suspend fun markSpanError(spanId: String, error: String, errorType: String? = null, stackTrace: String? = null)

    @Query("DELETE FROM spans WHERE startTime < :olderThan")
    suspend fun deleteOldSpans(olderThan: Long)

    @Query("SELECT COUNT(*) FROM spans WHERE traceId = :traceId")
    suspend fun getSpanCountForTrace(traceId: String): Int

    // Traces
    @Query("SELECT * FROM traces ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentTraces(limit: Int = 50): List<TraceEntity>

    @Query("SELECT * FROM traces WHERE traceId = :traceId")
    suspend fun getTraceById(traceId: String): TraceEntity?

    @Query("SELECT * FROM traces WHERE status = 'error' ORDER BY startTime DESC LIMIT :limit")
    suspend fun getErrorTraces(limit: Int = 50): List<TraceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrace(trace: TraceEntity)

    @Query("UPDATE traces SET endTime = :endTime, totalDurationMs = :durationMs, spanCount = :spanCount, errorCount = :errorCount, status = :status WHERE traceId = :traceId")
    suspend fun updateTrace(traceId: String, endTime: Long, durationMs: Long, spanCount: Int, errorCount: Int, status: String)

    @Query("DELETE FROM traces WHERE startTime < :olderThan")
    suspend fun deleteOldTraces(olderThan: Long)

    // Metrics
    @Query("SELECT * FROM metrics WHERE name = :name AND timestamp > :since ORDER BY timestamp DESC")
    suspend fun getMetricsByName(name: String, since: Long): List<MetricEntity>

    @Query("SELECT * FROM metrics WHERE name = :name AND timestamp BETWEEN :from AND :to ORDER BY timestamp ASC")
    suspend fun getMetricsInRange(name: String, from: Long, to: Long): List<MetricEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetric(metric: MetricEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetrics(metrics: List<MetricEntity>)

    @Query("SELECT AVG(value) FROM metrics WHERE name = :name AND timestamp > :since")
    suspend fun getAverageMetric(name: String, since: Long): Double?

    @Query("SELECT MAX(value) FROM metrics WHERE name = :name AND timestamp > :since")
    suspend fun getMaxMetric(name: String, since: Long): Double?

    @Query("DELETE FROM metrics WHERE timestamp < :olderThan")
    suspend fun deleteOldMetrics(olderThan: Long)

    // Bottlenecks
    @Query("SELECT * FROM bottlenecks WHERE isResolved = 0 ORDER BY detectedAt DESC")
    fun getActiveBottlenecks(): Flow<List<BottleneckEntity>>

    @Query("SELECT * FROM bottlenecks ORDER BY detectedAt DESC LIMIT :limit")
    suspend fun getRecentBottlenecks(limit: Int = 50): List<BottleneckEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBottleneck(bottleneck: BottleneckEntity)

    @Query("UPDATE bottlenecks SET isResolved = 1, resolvedAt = :timestamp WHERE id = :bottleneckId")
    suspend fun resolveBottleneck(bottleneckId: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM bottlenecks WHERE detectedAt < :olderThan")
    suspend fun deleteOldBottlenecks(olderThan: Long)
}
