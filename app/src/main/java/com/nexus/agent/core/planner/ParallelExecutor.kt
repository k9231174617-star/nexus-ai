package com.nexus.agent.core.planner

import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParallelExecutor @Inject constructor() {

    suspend fun executeParallel(
        tasks: List<TaskModel>,
        maxConcurrency: Int = 4,
        onComplete: suspend (TaskModel, Result<String>) -> Unit,
    ) = coroutineScope {
        val semaphore = kotlinx.coroutines.sync.Semaphore(maxConcurrency)
        tasks.map { task ->
            launch {
                semaphore.acquire()
                try {
                    val result = runCatching { executeTask(task) }
                    onComplete(task, result)
                } finally {
                    semaphore.release()
                }
            }
        }.joinAll()
    }

    private suspend fun executeTask(task: TaskModel): String {
        // Simulate task execution — in production delegates to ChatEngine
        delay(100)
        return "Task '${task.title}' completed"
    }
}