package com.nexus.agent.core.chat

import com.nexus.agent.core.llm.LLMBridge
import com.nexus.agent.core.llm.ModelRouter
import com.nexus.agent.core.context.ContextManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class ChatEngineTest {

    @Mock
    private lateinit var llmBridge: LLMBridge

    @Mock
    private lateinit var modelRouter: ModelRouter

    @Mock
    private lateinit var contextManager: ContextManager

    private lateinit var chatEngine: ChatEngine

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        chatEngine = ChatEngine(llmBridge, modelRouter, contextManager)
    }

    @Test
    fun `sendMessage emits streaming response`() = runTest {
        val messages = listOf(MessageModel(role = "user", content = "Hello"))
        val expectedChunks = listOf("Hi", " there")

        `when`(contextManager.injectContext(messages)).thenReturn(messages)
        `when`(modelRouter.route("gpt-4")).thenReturn("openai")
        `when`(llmBridge.streamCompletion(any(), any(), any()))
            .thenReturn(flowOf("Hi", " there"))

        val result = chatEngine.sendMessage("Hello", "session-1").toList()

        assertEquals(expectedChunks, result)
    }

    @Test
    fun `sendMessage returns full response`() = runTest {
        `when`(contextManager.injectContext(anyList())).thenAnswer { it.arguments[0] as List<MessageModel> }
        `when`(modelRouter.route("gpt-4")).thenReturn("openai")
        `when`(llmBridge.complete(any(), any(), any())).thenReturn("Full response")

        val result = chatEngine.sendMessageSync("Hello", "session-1")

        assertEquals("Full response", result)
    }
}
