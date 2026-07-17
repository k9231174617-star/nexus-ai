package com.nexus.agent.core.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.nexus.agent.core.agents.AgentCoordinator
import com.nexus.agent.core.agents.AgentTask
import com.nexus.agent.core.planner.TaskStatus
import com.nexus.agent.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based background task planner.
 * Periodically processes pending planning tasks via AgentCoordinator.
 */
class TaskPlannerWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TaskPlannerWorker"
        private const val WORK_NAME = "nexus_task_planner"

        fun schedule(workManager: WorkManager, intervalMinutes: Long = 15) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<TaskPlannerWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Running task planner worker")

            val db = AppDatabase.getInstance(applicationContext)
            val plannerDao = db.plannerDao()

            val pendingCount = plannerDao.getPendingCount()
            Log.i(TAG, "Pending tasks: $pendingCount")

            if (pendingCount > 0) {
                val task = plannerDao.getNextPending()
                if (task != null) {
                    // Mark as running (use uppercase to match TaskStatus enum storage)
                    plannerDao.updateStatus(task.id, TaskStatus.RUNNING.name)
                    Log.i(TAG, "Processing task: ${task.id} - ${task.title}")

                    // Delegate to AgentCoordinator for execution
                    val coordinator = AgentCoordinator()
                    coordinator.registerDefaultAgents()

                    val agentTask = AgentTask(
                        type = task.agentType.lowercase(),
                        description = task.title + ": " + task.description,
                        context = mapOf("taskId" to task.id, "priority" to task.priority.name),
                        priority = task.priority.ordinal,
                    )
                    val result = coordinator.routeTask(agentTask)
                    Log.i(TAG, "Task ${task.id} completed by ${result.results.size} agents, consensus: ${result.consensus?.take(100)}")

                    plannerDao.updateStatus(task.id, TaskStatus.DONE.name)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            Result.retry()
        }
    }
}
