package com.nexus.agent.core.planner

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class WorkflowEvent(
    val taskId: String,
    val type: EventType,
    val data: String = "",
)

enum class EventType { STARTED, COMPLETED, FAILED, PROGRESS }

@Singleton
class WorkflowEngine @Inject constructor(
    private val planner: TaskPlanner,
    private val parallelExecutor: ParallelExecutor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _events = MutableSharedFlow<WorkflowEvent>()
    val events: SharedFlow<WorkflowEvent> = _events

    fun startWorkflow(goal: String) {
        scope.launch {
            val tasks = planner.planFromGoal(goal)
            executeWorkflow(tasks)
        }
    }

    private suspend fun executeWorkflow(tasks: List<TaskModel>) {
        while (true) {
            val ready = planner.getNextReady()
            if (ready.isEmpty()) {
                val anyPending = planner.tasks.value.any {
                    it.status == TaskStatus.PENDING || it.status == TaskStatus.RUNNING
                }
                if (!anyPending) break
                delay(500)
                continue
            }
            // Execute ready tasks in parallel
            parallelExecutor.executeParallel(ready) { task, result ->
                val status = if (result.isSuccess) TaskStatus.DONE else TaskStatus.FAILED
                planner.updateStatus(task.id, status, result.getOrNull())
                _events.emit(WorkflowEvent(
                    taskId = task.id,
                    type = if (result.isSuccess) EventType.COMPLETED else EventType.FAILED,
                    data = result.getOrNull() ?: result.exceptionOrNull()?.message ?: "",
                ))
            }
        }
    }

    fun stopWorkflow() {
        scope.coroutineContext.cancelChildren()
        planner.cancelAll()
    }
}