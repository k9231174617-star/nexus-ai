package com.nexus.agent.core.chat

import com.nexus.agent.core.context.ContextManager
import com.nexus.agent.core.llm.LLMBridge
import com.nexus.agent.core.llm.ModelRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

enum class AgentType { MAIN, CODE, UNIVERSAL }

@Singleton
class ChatEngine @Inject constructor(
    private val llmBridge: LLMBridge,
    private val modelRouter: ModelRouter,
    private val contextManager: ContextManager,
) {
    private val histories = mutableMapOf<AgentType, MutableList<MessageModel>>()

    fun getHistory(agent: AgentType): List<MessageModel> =
        histories.getOrDefault(agent, mutableListOf())

    fun sendMessage(
        text: String,
        agent: AgentType,
        fileContext: String? = null
    ): Flow<String> = flow {
        val history = histories.getOrPut(agent) { mutableListOf() }

        val injected = contextManager.consumeInjectedContext()
        val fullPrompt = buildString {
            injected?.let { append("[Context: $it]\n\n") }
            fileContext?.let { append("[File:\n$it\n]\n\n") }
            append(text)
        }

        history.add(MessageModel(role = "user", content = fullPrompt, displayText = text))
        if (history.size > MAX_HISTORY * 2) {
            histories[agent] = history.takeLast(MAX_HISTORY).toMutableList()
        }

        val model = modelRouter.routeForAgent(agent)
        val systemPrompt = SYSTEM_PROMPTS[agent] ?: ""

        val response = StringBuilder()
        llmBridge.streamCompletion(
            messages = history.map { it.toApiMessage() },
            systemPrompt = systemPrompt,
            model = model
        ).collect { chunk ->
            response.append(chunk)
            emit(chunk)
        }

        history.add(
            MessageModel(role = "assistant", content = response.toString())
        )
        contextManager.recordInteraction(text, response.toString(), agent.name)
    }

    fun clearHistory(agent: AgentType) {
        histories[agent]?.clear()
    }

    fun clearAll() = histories.clear()

    companion object {
        const val MAX_HISTORY = 20
        val SYSTEM_PROMPTS = mapOf(
            AgentType.MAIN to """You are NEXUS AI — a powerful mobile AI agent on Android.
Help with code, shell commands, file operations, APK work, media.
Be concise and technical. Use markdown for code blocks.
Answer in the language the user writes in.""",
            AgentType.CODE to """You are NEXUS Code Agent — specialized in Android development,
Kotlin, Java, APK analysis and reverse engineering.
Provide complete working implementations with markdown code blocks.""",
            AgentType.UNIVERSAL to """You are NEXUS Universal Agent — specialized in media processing,
document analysis, image editing and creative tasks.
Help analyze images, documents, create and edit media."""
        )
    }
}