package com.nexus.agent.core.learning

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Self-Learning Loop — inspired by Ruflo's learning system.
 * Agents learn from completed tasks and optimize future behavior.
 */
data class Experience(
    val id: String,
    val taskType: String,
    val prompt: String,
    val result: String,
    val success: Boolean,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList(),
)

data class Pattern(
    val id: String,
    val taskType: String,
    val successfulPrompt: String,
    val avgDurationMs: Long,
    val frequency: Int,
    val tags: List<String> = emptyList(),
)

@Singleton
class LearningLoop @Inject constructor() {

    private val experiences = ConcurrentLinkedQueue<Experience>()
    private val _patterns = MutableStateFlow<List<Pattern>>(emptyList())
    val patterns: StateFlow<List<Pattern>> = _patterns

    private val _stats = MutableStateFlow(Map<String, Double>())
    val stats: StateFlow<Map<String, Double>> = _stats

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Record a new experience from task execution */
    fun record(exp: Experience) {
        experiences.add(exp)
        if (experiences.size > 1000) experiences.poll() // Keep last 1000

        // Analyze and extract patterns
        if (exp.success) {
            analyzePatterns(exp)
        }
        updateStats()
    }

    /** Get optimized prompt based on past experience */
    fun optimizePrompt(taskType: String, originalPrompt: String): String {
        val relevantPatterns = _patterns.value.filter { it.taskType == taskType }
        if (relevantPatterns.isEmpty()) return originalPrompt

        val bestPattern = relevantPatterns.maxByOrNull { it.frequency } ?: return originalPrompt

        // Inject learned patterns into prompt
        return """
            $originalPrompt

            [Learning Context]
            Based on ${bestPattern.frequency} successful executions:
            - Recommended approach: ${bestPattern.successfulPrompt.take(100)}
            - Expected duration: ${bestPattern.avgDurationMs}ms
        """.trimIndent()
    }

    /** Get learning statistics */
    fun getLearningStats(): Map<String, Any> = mapOf(
        "totalExperiences" to experiences.size,
        "patternsFound" to _patterns.value.size,
        "averageSuccessRate" to calculateSuccessRate(),
        "optimizationSuggestions" to generateSuggestions(),
    )

    /** Generate optimization suggestions based on learning */
    fun generateSuggestions(): List<String> {
        val suggestions = mutableListOf<String>()
        val byType = experiences.groupBy { it.taskType }

        byType.forEach { (type, exps) ->
            val successRate = exps.count { it.success }.toFloat() / exps.size
            if (successRate < 0.5) {
                suggestions.add("Low success rate ($successRate%) for '$type' — consider adjusting approach")
            }
            val avgDuration = exps.map { it.durationMs }.average()
            if (avgDuration > 5000) {
                suggestions.add("High latency ($avgDuration ms) for '$type' — consider optimization")
            }
        }
        return suggestions
    }

    private fun analyzePatterns(exp: Experience) {
        val existing = _patterns.value.toMutableList()
        val idx = existing.indexOfFirst { it.taskType == exp.taskType }

        if (idx >= 0) {
            val pattern = existing[idx]
            existing[idx] = pattern.copy(
                frequency = pattern.frequency + 1,
                avgDurationMs = (pattern.avgDurationMs * pattern.frequency + exp.durationMs) / (pattern.frequency + 1),
            )
        } else {
            existing.add(Pattern(
                id = "pat-${System.currentTimeMillis()}",
                taskType = exp.taskType,
                successfulPrompt = exp.prompt.take(200),
                avgDurationMs = exp.durationMs,
                frequency = 1,
                tags = exp.tags,
            ))
        }
        _patterns.value = existing
    }

    private fun calculateSuccessRate(): Double {
        if (experiences.isEmpty()) return 0.0
        return experiences.count { it.success }.toDouble() / experiences.size
    }

    private fun updateStats() {
        val byType = experiences.groupBy { it.taskType }
        _stats.value = byType.mapValues { (_, exps) ->
            exps.count { it.success }.toDouble() / exps.size
        }
    }

    /** Export learning data as JSON */
    fun exportAsJson(): String = JSONObject().apply {
        put("totalExperiences", experiences.size)
        put("patterns", JSONArray(_patterns.value.map { p ->
            JSONObject().apply {
                put("taskType", p.taskType)
                put("frequency", p.frequency)
                put("avgDurationMs", p.avgDurationMs)
            }
        }))
        put("suggestions", JSONArray(generateSuggestions()))
    }.toString(2)

    fun destroy() { scope.cancel() }
}
