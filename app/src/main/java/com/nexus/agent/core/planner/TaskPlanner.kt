package com.nexus.agent.core.planner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskPlanner @Inject constructor(
    private val sorter: TopologicalSorter,
    private val workflowEngine: WorkflowEngine,
    private val registry: TaskRegistry,
) {
    private val _tasks = MutableStateFlow<List<TaskModel>>(emptyList())
    val tasks: StateFlow<List<TaskModel>> = _tasks

    fun planFromGoal(goal: String, agentType: String = "MAIN"): List<TaskModel> {
        // Decompose goal into sub-tasks
        val subTasks = decomposeGoal(goal, agentType)
        val sorted = sorter.sort(subTasks)
        _tasks.value = sorted
        registry.registerAll(sorted)
        return sorted
    }

    private fun decomposeGoal(goal: String, agentType: String): List<TaskModel> {
        // Simple rule-based decomposition
        // In production: use LLM to decompose
        return when {
            goal.contains("apk", ignoreCase = true) -> listOf(
                TaskModel(title = "Analyse APK", agentType = "CODE"),
                TaskModel(title = "Decompile", agentType = "CODE"),
                TaskModel(title = "Modify smali", agentType = "CODE"),
                TaskModel(title = "Recompile", agentType = "CODE"),
                TaskModel(title = "Sign APK", agentType = "CODE"),
            )
            goal.contains("code", ignoreCase = true) -> listOf(
                TaskModel(title = "Analyze requirements", agentType = agentType),
                TaskModel(title = "Generate code", agentType = agentType),
                TaskModel(title = "Review and test", agentType = agentType),
            )
            else -> listOf(
                TaskModel(title = "Research: $goal", agentType = agentType),
                TaskModel(title = "Execute: $goal", agentType = agentType),
                TaskModel(title = "Verify: $goal", agentType = agentType),
            )
        }
    }

    fun updateStatus(taskId: String, status: TaskStatus, result: String? = null) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) task.copy(
                status = status,
                result = result,
                completedAt = if (status == TaskStatus.DONE) System.currentTimeMillis() else null,
            ) else task
        }
    }

    fun getNextReady(): List<TaskModel> {
        val done = _tasks.value.filter { it.status == TaskStatus.DONE }.map { it.id }.toSet()
        return _tasks.value.filter { task ->
            task.status == TaskStatus.PENDING &&
            org.json.JSONArray(task.dependsOn).let { arr ->
                (0 until arr.length()).all { arr.getString(it) in done }
            }
        }
    }

    fun cancelAll() {
        _tasks.value = _tasks.value.map {
            if (it.status == TaskStatus.PENDING || it.status == TaskStatus.RUNNING)
                it.copy(status = TaskStatus.CANCELLED) else it
        }
    }
}