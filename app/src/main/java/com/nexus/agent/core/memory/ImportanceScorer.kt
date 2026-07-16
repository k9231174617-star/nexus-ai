package com.nexus.agent.core.memory

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportanceScorer @Inject constructor() {

    private val highImportanceKeywords = setOf(
        "ошибка", "error", "критично", "critical", "важно", "important",
        "запомни", "remember", "никогда", "всегда", "always", "never",
        "секрет", "secret", "пароль", "password", "ключ", "key",
        "задача", "task", "цель", "goal", "план", "plan",
    )

    private val lowImportanceKeywords = setOf(
        "привет", "hello", "спасибо", "thanks", "пока", "bye",
        "хорошо", "ok", "ладно", "понял", "ясно",
    )

    fun score(content: String): Float {
        val lower = content.lowercase()
        var score = 0.5f

        // Length factor
        val wordCount = content.split("\\s+".toRegex()).size
        score += when {
            wordCount > 100 -> 0.2f
            wordCount > 50  -> 0.1f
            wordCount < 5   -> -0.1f
            else -> 0f
        }

        // Keyword factors
        highImportanceKeywords.forEach { kw ->
            if (lower.contains(kw)) score += 0.1f
        }
        lowImportanceKeywords.forEach { kw ->
            if (lower.contains(kw)) score -= 0.05f
        }

        // Code block bonus
        if (content.contains("```")) score += 0.15f

        // Question penalty (questions are less important to store long-term)
        if (content.trimEnd().endsWith("?")) score -= 0.05f

        return score.coerceIn(0f, 1f)
    }
}