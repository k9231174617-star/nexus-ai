package com.nexus.agent.core.llm

import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedResponse(
    val content: String,
    val finishReason: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val model: String,
)

@Singleton
class ResponseParser @Inject constructor() {

    fun extractContent(chunk: String): String {
        return try {
            val json = JSONObject(chunk)
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("delta")
                .optString("content", "")
        } catch (_: Exception) { "" }
    }

    fun parseFullResponse(json: String): ParsedResponse {
        val obj = JSONObject(json)
        val choice = obj.getJSONArray("choices").getJSONObject(0)
        val usage = obj.optJSONObject("usage")
        return ParsedResponse(
            content         = choice.getJSONObject("message").getString("content"),
            finishReason    = choice.optString("finish_reason", ""),
            promptTokens    = usage?.optInt("prompt_tokens", 0) ?: 0,
            completionTokens= usage?.optInt("completion_tokens", 0) ?: 0,
            totalTokens     = usage?.optInt("total_tokens", 0) ?: 0,
            model           = obj.optString("model", ""),
        )
    }

    fun extractCodeBlocks(text: String): List<Pair<String, String>> {
        val regex = Regex("```(\\w+)?\\n([\\s\\S]*?)```")
        return regex.findAll(text).map { match ->
            val lang = match.groupValues[1].ifBlank { "text" }
            val code = match.groupValues[2].trim()
            Pair(lang, code)
        }.toList()
    }

    fun extractJsonFromResponse(text: String): JSONObject? {
        val jsonRegex = Regex("```json\\n([\\s\\S]*?)```")
        val match = jsonRegex.find(text)
        return try {
            JSONObject(match?.groupValues?.get(1)?.trim() ?: text.trim())
        } catch (_: Exception) { null }
    }

    fun extractCommands(text: String): List<String> {
        val cmdRegex = Regex("```(?:bash|sh|shell)\\n([\\s\\S]*?)```")
        return cmdRegex.findAll(text).flatMap { match ->
            match.groupValues[1].trim()
                .split("\n")
                .filter { it.isNotBlank() && !it.startsWith("#") }
        }.toList()
    }
}