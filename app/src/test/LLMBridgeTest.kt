package com.nexus.agent.core.llm

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
    private lateinit var freeProvider: FreeLLMProvider

    @Mock
    private lateinit var customProvider: CustomAPIProvider

    @Mock
    private lateinit var modelRouter: ModelRouter

    @Mock
    private lateinit var tokenCounter: TokenCounter

    private lateinit var llmBridge: LLMBridge

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        llmBridge = LLMBridge(freeProvider, customProvider, modelRouter, tokenCounter)
    }

    @Test
    fun `sendPrompt routes to free provider when no custom key`() = runTest {
        val prompt = "Hello"
        val response = "Hi there!"
        
        `when`(customProvider.hasApiKey()).thenReturn(false)
        `when`(modelRouter.selectProvider(null)).thenReturn(ProviderType.FREE)
        `when`(freeProvider.sendPrompt(prompt)).thenReturn(flowOf(response))
        
        val result = llmBridge.sendPrompt(prompt).toList()
        
        assertEquals(listOf(response), result)
        verify(freeProvider).sendPrompt(prompt)
        verify(customProvider, never()).sendPrompt(anyString())
    }

    @Test
    fun `sendPrompt routes to custom provider when API key exists`() = runTest {
        val prompt = "Hello"
        val response = "Custom response"
        
        `when`(customProvider.hasApiKey()).thenReturn(true)
        `when`(modelRouter.selectProvider(null)).thenReturn(ProviderType.CUSTOM)
        `when`(customProvider.sendPrompt(prompt)).thenReturn(flowOf(response))
        
        val result = llmBridge.sendPrompt(prompt).toList()
        
        assertEquals(listOf(response), result)
        verify(customProvider).sendPrompt(prompt)
    }

    @Test
    fun `sendPrompt with specific model overrides router`() = runTest {
        val prompt = "Hello"
        val model = "gpt-4"
        
        `when`(modelRouter.selectProvider(model)).thenReturn(ProviderType.CUSTOM)
        `when`(customProvider.sendPrompt(prompt, model)).thenReturn(flowOf("ok"))
        
        llmBridge.sendPrompt(prompt, model).toList()
        
        verify(modelRouter).selectProvider(model)
    }

    @Test
    fun `sendPrompt counts tokens before sending`() = runTest {
        val prompt = "test prompt"
        
        `when`(freeProvider.sendPrompt(anyString())).thenReturn(flowOf("ok"))
        `when`(tokenCounter.count(prompt)).thenReturn(2)
        
        llmBridge.sendPrompt(prompt).toList()
        
        verify(tokenCounter).count(prompt)
    }

    @Test
    fun `sendPrompt falls back on provider failure`() = runTest {
        val prompt = "Hello"
        
        `when`(modelRouter.selectProvider(null)).thenReturn(ProviderType.CUSTOM)
        `when`(customProvider.sendPrompt(prompt)).thenThrow(RuntimeException("API Error"))
        `when`(modelRouter.getFallback()).thenReturn(ProviderType.FREE)
        `when`(freeProvider.sendPrompt(prompt)).thenReturn(flowOf("fallback"))
        
        val result = llmBridge.sendPrompt(prompt).toList()
        
        assertEquals(listOf("fallback"), result)
    }

    @Test
    fun `getAvailableModels aggregates from all providers`() {
        val freeModels = listOf("model-a", "model-b")
        val customModels = listOf("gpt-4", "claude-3")
        
        `when`(freeProvider.getAvailableModels()).thenReturn(freeModels)
        `when`(customProvider.getAvailableModels()).thenReturn(customModels)
        
        val result = llmBridge.getAvailableModels()
        
        assertEquals(4, result.size)
        assertTrue(result.containsAll(freeModels + customModels))
    }

    @Test
    fun `cancelRequest cancels active stream`() {
        llmBridge.cancelRequest()
        
        verify(freeProvider).cancel()
        verify(customProvider).cancel()
    }

    @Test
    fun `isReady returns true when at least one provider is ready`() {
        `when`(freeProvider.isReady()).thenReturn(false)
        `when`(customProvider.isReady()).thenReturn(true)
        
        assertTrue(llmBridge.isReady())
    }

    @Test
    fun `isReady returns false when no providers ready`() {
        `when`(freeProvider.isReady()).thenReturn(false)
        `when`(customProvider.isReady()).thenReturn(false)
        
        assertFalse(llmBridge.isReady())
    }

    @Test
    fun `getLastLatency returns value from active provider`() {
        `when`(modelRouter.getLastUsedProvider()).thenReturn(ProviderType.FREE)
        `when`(freeProvider.getLastLatency()).thenReturn(150L)
        
        assertEquals(150L, llmBridge.getLastLatency())
    }

    @Test
    fun `sendSystemPrompt prepends system context`() = runTest {
        val systemPrompt = "You are helpful"
        val userPrompt = "Hello"
        
        `when`(freeProvider.sendPrompt(anyString())).thenReturn(flowOf("ok"))
        
        llmBridge.sendSystemPrompt(systemPrompt, userPrompt).toList()
        
        verify(freeProvider).sendPrompt(argThat { it.contains(systemPrompt) && it.contains(userPrompt) })
    }
}
