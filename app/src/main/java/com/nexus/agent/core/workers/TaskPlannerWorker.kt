package com.nexus.agent.core.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.nexus.agent.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based background task planner.
 * Periodically processes pending planning tasks.
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
                    plannerDao.updateStatus(task.id, "running")
                    Log.i(TAG, "Processing task: ${task.id}")
                    // Execute task — in production, delegate to AgentCoordinator
                    plannerDao.updateStatus(task.id, "completed")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            Result.retry()
        }
    }
}
