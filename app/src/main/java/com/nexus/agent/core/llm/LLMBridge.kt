package com.nexus.agent.core.llm

import com.nexus.agent.data.remote.LLMAPI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LLMBridge @Inject constructor(
    private val api: LLMAPI,
    private val freeLLMProvider: FreeLLMProvider,
    private val customAPIProvider: CustomAPIProvider,
    private val promptEngineer: PromptEngineer,
    private val responseParser: ResponseParser,
) {
    suspend fun streamCompletion(
        messages: List<Map<String, String>>,
        systemPrompt: String,
        model: String,
    ): Flow<String> = flow {
        val engineered = promptEngineer.prepare(messages, systemPrompt)

        val provider = if (customAPIProvider.isConfigured()) {
            customAPIProvider
        } else {
            freeLLMProvider
        }

        provider.stream(engineered, model).collect { chunk ->
            val parsed = responseParser.extractContent(chunk)
            if (parsed.isNotEmpty()) emit(parsed)
        }
    }

    suspend fun complete(
        messages: List<Map<String, String>>,
        systemPrompt: String,
        model: String,
    ): String {
        val engineered = promptEngineer.prepare(messages, systemPrompt)
        val provider = if (customAPIProvider.isConfigured()) {
            customAPIProvider
        } else {
            freeLLMProvider
        }
        return provider.complete(engineered, model)
    }
}
