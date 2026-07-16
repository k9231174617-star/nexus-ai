package com.nexus.agent.core.context

import javax.inject.Inject
import javax.inject.Singleton

data class InjectedContext(
    val content: String,
    val type: ContextType,
    val priority: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class ContextType {
    USER_NOTE, FILE_CONTENT, ENVIRONMENT, SYSTEM_INFO, RAG_RESULT, MEMORY_RECALL
}

@Singleton
class ContextInjector @Inject constructor() {
    private val queue = ArrayDeque<InjectedContext>()

    fun inject(content: String, type: ContextType = ContextType.USER_NOTE, priority: Int = 0) {
        queue.addLast(InjectedContext(content, type, priority))
    }

    fun consumeAll(): List<InjectedContext> {
        val all = queue.sortedByDescending { it.priority }
        queue.clear()
        return all
    }

    fun consumeOne(): InjectedContext? = queue.removeFirstOrNull()

    fun buildContextBlock(): String {
        val all = consumeAll()
        if (all.isEmpty()) return ""
        return buildString {
            append("[INJECTED CONTEXT]\n")
            all.forEach { ctx ->
                append("[${ctx.type.name}]\n${ctx.content}\n\n")
            }
            append("[END CONTEXT]")
        }
    }

    fun hasContext() = queue.isNotEmpty()
    fun count() = queue.size
}