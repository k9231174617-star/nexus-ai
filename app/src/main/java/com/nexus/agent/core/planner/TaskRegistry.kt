package com.nexus.agent.core.planner

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRegistry @Inject constructor() {
    private val registry = mutableMapOf<String, TaskModel>()

    fun register(task: TaskModel) { registry[task.id] = task }
    fun registerAll(tasks: List<TaskModel>) = tasks.forEach { register(it) }
    fun get(id: String): TaskModel? = registry[id]
    fun getAll(): List<TaskModel> = registry.values.toList()
    fun update(task: TaskModel) { registry[task.id] = task }
    fun remove(id: String) { registry.remove(id) }
    fun clear() { registry.clear() }
    fun size() = registry.size
    fun getByStatus(status: TaskStatus) = registry.values.filter { it.status == status }
}