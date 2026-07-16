package com.nexus.agent.core.llm

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptEngineer @Inject constructor() {

    fun prepare(
        messages: List<Map<String, String>>,
        systemPrompt: String,
    ): List<Map<String, String>> {
        val result = mutableListOf<Map<String, String>>()
        if (systemPrompt.isNotBlank()) {
            result.add(mapOf("role" to "system", "content" to systemPrompt))
        }
        result.addAll(messages)
        return result
    }

    fun addCodeContext(messages: List<Map<String, String>>, code: String, language: String) =
        messages.toMutableList().apply {
            val last = lastOrNull { it["role"] == "user" } ?: return@apply
            val idx = indexOf(last)
            val enhanced = last.toMutableMap()
            enhanced["content"] = "```$language\n$code\n```\n\n${last["content"]}"
            set(idx, enhanced)
        }

    fun addFileContext(messages: List<Map<String, String>>, fileName: String, content: String) =
        messages.toMutableList().apply {
            val injection = "[File: $fileName]\n$content\n[End File]"
            val last = lastOrNull { it["role"] == "user" } ?: return@apply
            val idx = indexOf(last)
            val enhanced = last.toMutableMap()
            enhanced["content"] = "$injection\n\n${last["content"]}"
            set(idx, enhanced)
        }

    fun truncateToTokenLimit(
        messages: List<Map<String, String>>,
        maxTokens: Int = 8000,
        reserveTokens: Int = 1000,
    ): List<Map<String, String>> {
        val limit = maxTokens - reserveTokens
        var total = 0
        val result = mutableListOf<Map<String, String>>()

        // Always keep system message
        val system = messages.firstOrNull { it["role"] == "system" }
        system?.let {
            total += estimateTokens(it["content"] ?: "")
            result.add(it)
        }

        // Add messages from newest to oldest until limit
        val nonSystem = messages.filter { it["role"] != "system" }.reversed()
        val selected = mutableListOf<Map<String, String>>()
        for (msg in nonSystem) {
            val tokens = estimateTokens(msg["content"] ?: "")
            if (total + tokens > limit) break
            total += tokens
            selected.add(0, msg)
        }
        result.addAll(selected)
        return result
    }

    private fun estimateTokens(text: String): Int = text.length / 4
}