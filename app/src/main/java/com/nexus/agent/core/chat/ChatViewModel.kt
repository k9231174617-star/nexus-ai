package com.nexus.agent.core.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.agent.core.context.ContextManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<MessageModel> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val error: String? = null,
    val tokenCount: Int = 0,
    val latencyMs: Long = 0,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatEngine: ChatEngine,
    private val repository: ChatRepository,
    private val contextManager: ContextManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentAgent = AgentType.MAIN

    fun setAgent(agent: AgentType) {
        currentAgent = agent
        loadHistory()
    }

    fun sendMessage(text: String, fileContext: String? = null) {
        if (text.isBlank()) return

        viewModelScope.launch {
            val userMsg = MessageModel(role = "user", content = text)
            addMessage(userMsg)
            repository.saveMessage(userMsg.copy(agentType = currentAgent.name))

            _uiState.update { it.copy(isStreaming = true, streamingText = "", error = null) }

            val t0 = System.currentTimeMillis()
            val buffer = StringBuilder()

            try {
                chatEngine.sendMessage(text, currentAgent, fileContext)
                    .collect { chunk ->
                        buffer.append(chunk)
                        _uiState.update { it.copy(streamingText = buffer.toString()) }
                    }

                val latency = System.currentTimeMillis() - t0
                val assistantMsg = MessageModel(
                    role = "assistant",
                    content = buffer.toString(),
                    tokenCount = buffer.length / 4
                )
                addMessage(assistantMsg)
                repository.saveMessage(assistantMsg.copy(agentType = currentAgent.name))

                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        streamingText = "",
                        latencyMs = latency,
                        tokenCount = it.tokenCount + assistantMsg.tokenCount
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isStreaming = false, error = e.message)
                }
            }
        }
    }

    fun clearChat() {
        chatEngine.clearHistory(currentAgent)
        _uiState.update { it.copy(messages = emptyList(), tokenCount = 0) }
        viewModelScope.launch { repository.clearMessages(currentAgent.name) }
    }

    private fun addMessage(msg: MessageModel) {
        _uiState.update { it.copy(messages = it.messages + msg) }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val msgs = repository.getMessages(currentAgent.name)
            _uiState.update { it.copy(messages = msgs) }
        }
    }
}