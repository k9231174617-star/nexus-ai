package com.nexus.agent.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cicd_pipelines")
data class PipelineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val repositoryUrl: String,
    val branch: String = "main",
    val configPath: String = ".nexus/pipeline.yml", // pipeline config file path
    val status: String = "inactive", // inactive, active, running, paused
    val lastRunId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val triggerType: String = "manual", // manual, webhook, scheduled
    val schedule: String? = null, // cron expression for scheduled runs
    val environmentVariables: String = "", // JSON serialized
    val secrets: String = "" // encrypted JSON
)

@Entity(tableName = "cicd_runs")
data class PipelineRunEntity(
    @PrimaryKey val id: String,
    val pipelineId: String,
    val runNumber: Int,
    val status: String = "pending", // pending, running, success, failed, cancelled
    val triggerSource: String = "manual", // manual, webhook, schedule, api
    val triggeredBy: String? = null, // user ID or system
    val commitSha: String? = null,
    val commitMessage: String? = null,
    val branch: String,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val durationMs: Long? = null,
    val stages: String = "", // JSON serialized stage results
    val logs: String = "", // JSON serialized log entries
    val artifacts: String = "", // JSON serialized artifact paths
    val errorMessage: String? = null
)

@Entity(tableName = "cicd_deployments")
data class DeploymentEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val pipelineId: String,
    val environment: String, // staging, production, etc.
    val status: String = "pending", // pending, in_progress, success, failed, rolled_back
    val targetUrl: String? = null,
    val version: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val durationMs: Long? = null,
    val deployedBy: String? = null,
    var rollbackTargetId: String? = null
)

@Entity(tableName = "cicd_builds")
data class BuildEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val pipelineId: String,
    val buildType: String, // apk, aab, library
    val status: String = "pending",
    val outputPath: String? = null,
    val buildConfig: String = "", // JSON serialized build configuration
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val durationMs: Long? = null,
    val fileSize: Long? = null,
    val checksum: String? = null
)

@Dao
interface CICDDao {
    // Pipelines
    @Query("SELECT * FROM cicd_pipelines ORDER BY updatedAt DESC")
    fun getAllPipelines(): Flow<List<PipelineEntity>>

    @Query("SELECT * FROM cicd_pipelines WHERE id = :pipelineId")
    suspend fun getPipelineById(pipelineId: String): PipelineEntity?

    @Query("SELECT * FROM cicd_pipelines WHERE status = 'active' OR status = 'running'")
    suspend fun getActivePipelines(): List<PipelineEntity>

    @Query("SELECT * FROM cicd_pipelines WHERE repositoryUrl = :repoUrl")
    suspend fun getPipelinesByRepo(repoUrl: String): List<PipelineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPipeline(pipeline: PipelineEntity)

    @Update
    suspend fun updatePipeline(pipeline: PipelineEntity)

    @Query("UPDATE cicd_pipelines SET status = :status, updatedAt = :timestamp, lastRunId = :runId WHERE id = :pipelineId")
    suspend fun updatePipelineStatus(pipelineId: String, status: String, runId: String? = null, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE cicd_pipelines SET triggerType = :triggerType, schedule = :schedule WHERE id = :pipelineId")
    suspend fun updatePipelineTrigger(pipelineId: String, triggerType: String, schedule: String?)

    @Delete
    suspend fun deletePipeline(pipeline: PipelineEntity)

    @Query("DELETE FROM cicd_pipelines WHERE id = :pipelineId")
    suspend fun deletePipelineById(pipelineId: String)

    @Query("SELECT COUNT(*) FROM cicd_pipelines")
    fun getPipelineCount(): Flow<Int>

    // Runs
    @Query("SELECT * FROM cicd_runs WHERE pipelineId = :pipelineId ORDER BY runNumber DESC")
    fun getRunsByPipeline(pipelineId: String): Flow<List<PipelineRunEntity>>

    @Query("SELECT * FROM cicd_runs WHERE id = :runId")
    suspend fun getRunById(runId: String): PipelineRunEntity?

    @Query("SELECT * FROM cicd_runs WHERE status = 'running'")
    suspend fun getRunningRuns(): List<PipelineRunEntity>

    @Query("SELECT * FROM cicd_runs WHERE pipelineId = :pipelineId ORDER BY runNumber DESC LIMIT 1")
    suspend fun getLatestRun(pipelineId: String): PipelineRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: PipelineRunEntity)

    @Update
    suspend fun updateRun(run: PipelineRunEntity)

    @Query("UPDATE cicd_runs SET status = :status, completedAt = :timestamp, durationMs = :durationMs WHERE id = :runId")
    suspend fun completeRun(runId: String, status: String, timestamp: Long, durationMs: Long)

    @Query("UPDATE cicd_runs SET status = 'failed', errorMessage = :error, completedAt = :timestamp WHERE id = :runId")
    suspend fun failRun(runId: String, error: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE cicd_runs SET stages = :stages, logs = :logs WHERE id = :runId")
    suspend fun updateRunDetails(runId: String, stages: String, logs: String)

    @Query("DELETE FROM cicd_runs WHERE completedAt < :olderThan AND status IN ('success', 'failed', 'cancelled')")
    suspend fun deleteOldRuns(olderThan: Long)

    @Query("SELECT COUNT(*) FROM cicd_runs WHERE pipelineId = :pipelineId")
    suspend fun getRunCountForPipeline(pipelineId: String): Int

    // Deployments
    @Query("SELECT * FROM cicd_deployments WHERE pipelineId = :pipelineId ORDER BY startedAt DESC")
    fun getDeploymentsByPipeline(pipelineId: String): Flow<List<DeploymentEntity>>

    @Query("SELECT * FROM cicd_deployments WHERE environment = :environment ORDER BY startedAt DESC")
    suspend fun getDeploymentsByEnvironment(environment: String): List<DeploymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeployment(deployment: DeploymentEntity)

    @Query("UPDATE cicd_deployments SET status = :status, completedAt = :timestamp, durationMs = :durationMs WHERE id = :deploymentId")
    suspend fun updateDeploymentStatus(deploymentId: String, status: String, timestamp: Long, durationMs: Long)

    @Query("UPDATE cicd_deployments SET status = 'rolled_back', rollbackTargetId = :targetId WHERE id = :deploymentId")
    suspend fun rollbackDeployment(deploymentId: String, targetId: String?)

    @Query("DELETE FROM cicd_deployments WHERE startedAt < :olderThan")
    suspend fun deleteOldDeployments(olderThan: Long)

    // Builds
    @Query("SELECT * FROM cicd_builds WHERE pipelineId = :pipelineId ORDER BY startedAt DESC")
    fun getBuildsByPipeline(pipelineId: String): Flow<List<BuildEntity>>

    @Query("SELECT * FROM cicd_builds WHERE runId = :runId")
    suspend fun getBuildsByRun(runId: String): List<BuildEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuild(build: BuildEntity)

    @Query("UPDATE cicd_builds SET status = :status, outputPath = :outputPath, fileSize = :fileSize, checksum = :checksum, completedAt = :timestamp, durationMs = :durationMs WHERE id = :buildId")
    suspend fun completeBuild(buildId: String, status: String, outputPath: String?, fileSize: Long?, checksum: String?, timestamp: Long, durationMs: Long)

    @Query("DELETE FROM cicd_builds WHERE startedAt < :olderThan")
    suspend fun deleteOldBuilds(olderThan: Long)
}
