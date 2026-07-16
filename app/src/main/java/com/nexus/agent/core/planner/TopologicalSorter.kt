package com.nexus.agent.core.planner

import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TopologicalSorter @Inject constructor() {

    fun sort(tasks: List<TaskModel>): List<TaskModel> {
        val idToTask = tasks.associateBy { it.id }
        val visited = mutableSetOf<String>()
        val result = mutableListOf<TaskModel>()

        fun visit(task: TaskModel) {
            if (task.id in visited) return
            visited.add(task.id)
            val deps = JSONArray(task.dependsOn)
            for (i in 0 until deps.length()) {
                val depId = deps.getString(i)
                idToTask[depId]?.let { visit(it) }
            }
            result.add(task)
        }

        tasks.forEach { if (it.id !in visited) visit(it) }
        return result
    }

    fun detectCycles(tasks: List<TaskModel>): List<String> {
        val cycles = mutableListOf<String>()
        val idToTask = tasks.associateBy { it.id }
        val inStack = mutableSetOf<String>()
        val visited = mutableSetOf<String>()

        fun dfs(task: TaskModel, path: List<String>) {
            if (task.id in inStack) {
                cycles.add(path.joinToString(" -> ") + " -> ${task.id}")
                return
            }
            if (task.id in visited) return
            visited.add(task.id)
            inStack.add(task.id)
            val deps = JSONArray(task.dependsOn)
            for (i in 0 until deps.length()) {
                idToTask[deps.getString(i)]?.let { dfs(it, path + task.id) }
            }
            inStack.remove(task.id)
        }

        tasks.forEach { if (it.id !in visited) dfs(it, emptyList()) }
        return cycles
    }
}