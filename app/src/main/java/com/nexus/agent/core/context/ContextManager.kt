package com.nexus.agent.core.context

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class MemoryEntry(
    val type: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val agent: String = "MAIN",
)

@Singleton
class ContextManager @Inject constructor(
    private val sessionMemory: SessionMemory,
    private val historyTracker: HistoryTracker,
) {
    private var injectedContext: String? = null

    private val _memoryEntries = MutableStateFlow<List<MemoryEntry>>(emptyList())
    val memoryEntries: StateFlow<List<MemoryEntry>> = _memoryEntries

    fun injectContext(context: String) {
        injectedContext = context
        addEntry(MemoryEntry(type = "context", content = context))
    }

    fun consumeInjectedContext(): String? {
        val ctx = injectedContext
        injectedContext = null
        return ctx
    }

    fun recordInteraction(userMsg: String, agentReply: String, agent: String) {
        addEntry(MemoryEntry(type = "user",  content = userMsg,   agent = agent))
        addEntry(MemoryEntry(type = "agent", content = agentReply.take(120), agent = agent))
        historyTracker.record(userMsg, agentReply)
        sessionMemory.store("last_interaction", userMsg)
    }

    fun addEntry(entry: MemoryEntry) {
        _memoryEntries.value = _memoryEntries.value + entry
    }

    fun clearSession() {
        _memoryEntries.value = emptyList()
        injectedContext = null
        sessionMemory.clear()
    }

    val entryCount get() = _memoryEntries.value.size
}