package com.nexus.agent.core.workers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/**
 * Task — единица работы, помещаемая в очередь и исполняемая воркерами.
 * Поддерживает приоритеты, зависимости, retry-логику и таймауты.
 */
@Serializable
data class Task(
    val id: String = UUID.randomUUID().toString(),
    val type: TaskType,
    val payload: JsonObject,
    val priority: Priority = Priority.NORMAL,
    val dependencies: List<String> = emptyList(),  // IDs зависимых задач
    val maxRetries: Int = 3,
    val timeoutMs: Long = 300_000,  // 5 минут по умолчанию
    val createdAt: Long = System.currentTimeMillis(),
    val scheduledAt: Long? = null,   // Отложенное выполнение
    val tags: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
) {
    enum class Priority(val weight: Int) {
        CRITICAL(100),
        HIGH(75),
        NORMAL(50),
        LOW(25),
        BACKGROUND(10)
    }

    enum class TaskType {
        LLM_INFERENCE,      // Запрос к LLM
        CODE_EXECUTION,     // Выполнение кода в sandbox
        FILE_PROCESSING,    // Обработка файла
        MEDIA_GENERATION,   // Генерация изображения/видео
        WEB_SCRAPING,       // Парсинг веб-страницы
        EMBEDDING,          // Создание эмбеддингов
        INDEXING,           // Индексация документов (RAG)
        GRAPH_UPDATE,       // Обновление графа знаний
        CI_BUILD,           // CI/CD сборка
        CUSTOM              // Произвольная задача
    }

    fun withRetryIncrement(): Task = this.copy(
        metadata = metadata.toMutableMap().apply {
            val current = this["retryCount"]?.toIntOrNull() ?: 0
            this["retryCount"] = (current + 1).toString()
        }
    )

    fun retryCount(): Int = metadata["retryCount"]?.toIntOrNull() ?: 0

    fun isDelayed(): Boolean = scheduledAt != null && scheduledAt > System.currentTimeMillis()
}
