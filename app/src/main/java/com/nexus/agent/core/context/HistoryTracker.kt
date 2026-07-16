package com.nexus.agent.core.context

import javax.inject.Inject
import javax.inject.Singleton

data class InteractionRecord(
    val userMessage: String,
    val agentReply: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
)

@Singleton
class HistoryTracker @Inject constructor() {
    private val records = ArrayDeque<InteractionRecord>(MAX_SIZE)

    fun record(user: String, reply: String, durationMs: Long = 0) {
        if (records.size >= MAX_SIZE) records.removeFirst()
        records.addLast(InteractionRecord(user, reply, durationMs = durationMs))
    }

    fun getRecent(n: Int = 10): List<InteractionRecord> =
        records.takeLast(n)

    fun searchByKeyword(keyword: String): List<InteractionRecord> =
        records.filter {
            it.userMessage.contains(keyword, ignoreCase = true) ||
            it.agentReply.contains(keyword, ignoreCase = true)
        }

    fun clear() = records.clear()

    fun size() = records.size

    fun averageDurationMs(): Double =
        if (records.isEmpty()) 0.0
        else records.map { it.durationMs }.average()

    companion object { const val MAX_SIZE = 500 }
}