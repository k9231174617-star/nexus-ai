package com.nexus.agent.core.llm

import com.nexus.agent.data.remote.LLMAPI
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class LLMBridgeTest {

    @Mock
    private lateinit var llmApi: LLMAPI

    @Mock
    private lateinit var freeProvider: FreeLLMProvider

    @Mock
    private lateinit var customProvider: CustomAPIProvider

    @Mock
    private lateinit var promptEngineer: PromptEngineer

    @Mock
    private lateinit var responseParser: ResponseParser

    private lateinit var llmBridge: LLMBridge

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        llmBridge = LLMBridge(llmApi, freeProvider, customProvider, promptEngineer, responseParser)
    }

    @Test
    fun `streamCompletion uses custom provider when configured`() = runTest {
        val messages = listOf(mapOf("role" to "user", "content" to "hello"))
        val expectedChunks = listOf("Hello", " World")

        `when`(customProvider.isConfigured()).thenReturn(true)
        `when`(promptEngineer.prepare(messages, "system")).thenReturn(messages)
        `when`(customProvider.stream(messages, "gpt-4")).thenReturn(flowOf("Hello", " World"))
        `when`(responseParser.extractContent("Hello")).thenReturn("Hello")
        `when`(responseParser.extractContent(" World")).thenReturn(" World")

        val result = llmBridge.streamCompletion(messages, "system", "gpt-4").toList()

        assertEquals(expectedChunks, result)
    }

    @Test
    fun `streamCompletion uses free provider when not configured`() = runTest {
        val messages = listOf(mapOf("role" to "user", "content" to "hello"))

        `when`(customProvider.isConfigured()).thenReturn(false)
        `when`(promptEngineer.prepare(messages, "system")).thenReturn(messages)
        `when`(freeProvider.stream(messages, "gpt-4")).thenReturn(flowOf("Hello"))
        `when`(responseParser.extractContent("Hello")).thenReturn("Hello")

        val result = llmBridge.streamCompletion(messages, "system", "gpt-4").toList()

        assertEquals(listOf("Hello"), result)
    }

    @Test
    fun `complete returns full response`() = runTest {
        val messages = listOf(mapOf("role" to "user", "content" to "hello"))

        `when`(customProvider.isConfigured()).thenReturn(true)
        `when`(promptEngineer.prepare(messages, "system")).thenReturn(messages)
        `when`(customProvider.complete(messages, "gpt-4")).thenReturn("Full response")

        val result = llmBridge.complete(messages, "system", "gpt-4")

        assertEquals("Full response", result)
    }
}
