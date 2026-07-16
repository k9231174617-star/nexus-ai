package com.nexus.agent.core.llm

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenCounter @Inject constructor() {

    // Approximate token counting (GPT-style: ~4 chars per token)
    fun estimate(text: String): Int = (text.length / 4.0).toInt().coerceAtLeast(1)

    fun estimateMessages(messages: List<Map<String, String>>): Int {
        return messages.sumOf { msg ->
            estimate(msg["content"] ?: "") + 4 // ~4 tokens overhead per message
        } + 2 // conversation overhead
    }

    fun estimateCost(tokens: Int, model: String): Double {
        val pricePerMillion = when {
            model.contains("gpt-4o")            -> 5.0
            model.contains("gpt-4o-mini")       -> 0.15
            model.contains("claude-3-5-sonnet") -> 3.0
            model.contains("claude-3-haiku")    -> 0.25
            model.contains("gemini-pro")        -> 0.5
            model.contains("deepseek")          -> 0.14
            else -> 0.0 // free models
        }
        return (tokens / 1_000_000.0) * pricePerMillion
    }

    fun willExceedLimit(text: String, limit: Int): Boolean = estimate(text) > limit

    fun truncateToLimit(text: String, limit: Int): String {
        if (estimate(text) <= limit) return text
        val charLimit = limit * 4
        return text.take(charLimit) + "..."
    }

    data class TokenBudget(
        val total: Int,
        val used: Int,
        val remaining: Int,
        val percentage: Float,
    )

    fun calculateBudget(
        messages: List<Map<String, String>>,
        maxTokens: Int = 8192,
    ): TokenBudget {
        val used = estimateMessages(messages)
        return TokenBudget(
            total = maxTokens,
            used = used,
            remaining = (maxTokens - used).coerceAtLeast(0),
            percentage = (used.toFloat() / maxTokens).coerceIn(0f, 1f),
        )
    }
}