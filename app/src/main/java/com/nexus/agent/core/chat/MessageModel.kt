package com.nexus.agent.core.chat

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "messages")
data class MessageModel(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val role: String,           // "user" | "assistant" | "system"
    val content: String,        // full content sent to API
    val displayText: String = content,
    val agentType: String = "MAIN",
    val timestamp: Long = System.currentTimeMillis(),
    val tokenCount: Int = 0,
    val isError: Boolean = false,
) {
    fun toApiMessage(): Map<String, String> = mapOf(
        "role" to role,
        "content" to content
    )

    val isUser get() = role == "user"
    val isAssistant get() = role == "assistant"
}