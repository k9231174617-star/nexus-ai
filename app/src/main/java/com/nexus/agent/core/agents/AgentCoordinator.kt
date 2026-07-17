package com.nexus.agent.core.agents

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class AgentType {
    MAIN, CODE, UNIVERSAL, REVIEW, SECURITY, TESTING, PLANNER, DEBUG, RESEARCH, OBSERVABILITY,
}

data class AgentSpec(
    val id: String = UUID.randomUUID().toString(),
    val type: AgentType,
    val name: String,
    val description: String = "",
    val systemPrompt: String = "",
    val capabilities: List<String> = emptyList(),
)

data class AgentTask(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val description: String,
    val context: Map<String, String> = emptyMap(),
    val assignedTo: String? = null,
    val priority: Int = 0,
    val status: TaskStatus = TaskStatus.PENDING,
    val result: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class TaskStatus { PENDING, ASSIGNED, RUNNING, COMPLETED, FAILED, REVIEW_NEEDED }

data class SwarmResult(
    val taskId: String,
    val results: Map<String, String>,
    val consensus: String? = null,
    val confidence: Float = 0f,
    val durationMs: Long = 0,
)

@Singleton
class AgentCoordinator @Inject constructor() {

    private val agents = ConcurrentHashMap<String, AgentSpec>()
    private val taskHistory = ConcurrentHashMap<String, List<AgentTask>>()
    private val _activeTasks = MutableStateFlow<List<AgentTask>>(emptyList())
    val activeTasks: StateFlow<List<AgentTask>> = _activeTasks

    private val _metrics = MutableStateFlow<Map<String, Double>>(emptyMap())
    val metrics: StateFlow<Map<String, Double>> = _metrics

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun registerAgent(spec: AgentSpec) {
        agents[spec.id] = spec
        updateMetrics()
    }

    suspend fun routeTask(task: AgentTask): SwarmResult = withContext(Dispatchers.Default) {
        val t0 = System.currentTimeMillis()
        addTask(task)

        val candidates = findCandidates(task)
        if (candidates.isEmpty()) {
            return@withContext SwarmResult(taskId = task.id, results = emptyMap(), confidence = 0f, durationMs = System.currentTimeMillis() - t0)
        }

        val deferreds = candidates.map { agent -> async { agent.id to executeTask(agent, task) } }
        val results = deferreds.awaitAll().toMap()
        val completed = task.copy(status = TaskStatus.COMPLETED, result = results.values.joinToString("\n---\n"))
        updateTask(completed)

        val nonEmpty = results.filter { it.value.isNotBlank() }
        val consensus = if (nonEmpty.isNotEmpty()) nonEmpty.values.groupBy { it }.maxByOrNull { it.value.size }?.key else null

        updateMetrics()
        SwarmResult(taskId = task.id, results = results, consensus = consensus,
            confidence = results.size.toFloat() / candidates.size.coerceAtLeast(1),
            durationMs = System.currentTimeMillis() - t0)
    }

    private fun findCandidates(task: AgentTask): List<AgentSpec> {
        return agents.values.filter { agent ->
            task.type.lowercase().let { type ->
                agent.capabilities.any { it.lowercase() in type } ||
                agent.type.name.lowercase() in type ||
                agent.description.lowercase().contains(type)
            }
        }.ifEmpty {
            agents.values.filter { it.type in listOf(AgentType.MAIN, AgentType.UNIVERSAL) }
        }.take(3)
    }

    private suspend fun executeTask(agent: AgentSpec, task: AgentTask): String {
        delay(100)
        return "[${agent.name}] Processed: ${task.description.take(50)}"
    }

    suspend fun learnFromResult(result: SwarmResult) {
        taskHistory[result.taskId] = agents.values.toList()
        updateMetrics()
    }

    fun getAgentStats(): Map<String, Map<String, Any>> = agents.values.associate { agent ->
        agent.name to mapOf("type" to agent.type.name, "capabilities" to agent.capabilities.size, "tasksCompleted" to taskHistory.size)
    }

    fun registerDefaultAgents() {
        registerAgent(AgentSpec(type = AgentType.MAIN, name = "Main Agent", description = "General conversation and assistance", capabilities = listOf("chat", "general", "help")))
        registerAgent(AgentSpec(type = AgentType.CODE, name = "Code Agent", description = "Code generation, analysis, and review", capabilities = listOf("code", "programming", "development")))
        registerAgent(AgentSpec(type = AgentType.UNIVERSAL, name = "Universal Agent", description = "Multi-modal processing", capabilities = listOf("media", "files", "documents")))
        registerAgent(AgentSpec(type = AgentType.REVIEW, name = "Code Reviewer", description = "Automated code review and best practices", capabilities = listOf("review", "quality", "lint")))
        registerAgent(AgentSpec(type = AgentType.SECURITY, name = "Security Analyst", description = "Vulnerability detection and security audit", capabilities = listOf("security", "audit", "vulnerability")))
        registerAgent(AgentSpec(type = AgentType.TESTING, name = "Test Generator", description = "Test case generation and execution", capabilities = listOf("test", "testing", "coverage")))
        registerAgent(AgentSpec(type = AgentType.PLANNER, name = "Task Planner", description = "Task decomposition and workflow planning", capabilities = listOf("plan", "planning", "workflow")))
        registerAgent(AgentSpec(type = AgentType.DEBUG, name = "Debug Assistant", description = "Error analysis and debugging", capabilities = listOf("debug", "error", "fix")))
        registerAgent(AgentSpec(type = AgentType.RESEARCH, name = "Research Agent", description = "Web research and information gathering", capabilities = listOf("research", "search", "rag")))
        registerAgent(AgentSpec(type = AgentType.OBSERVABILITY, name = "Monitor", description = "System monitoring and alerting", capabilities = listOf("monitor", "metrics", "observe")))
    }

    private fun addTask(task: AgentTask) { _activeTasks.value = _activeTasks.value + task }
    private fun updateTask(task: AgentTask) { _activeTasks.value = _activeTasks.value.map { if (it.id == task.id) task else it } }
    private fun updateMetrics() {
        _metrics.value = mapOf(
            "agents" to agents.size.toDouble(),
            "activeTasks" to _activeTasks.value.count { it.status == TaskStatus.RUNNING }.toDouble(),
            "completedTasks" to taskHistory.size.toDouble(),
        )
    }

    fun destroy() { scope.cancel() }
}
