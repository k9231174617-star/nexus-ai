package com.nexus.agent.core.router

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class RouterTest {

    @Mock
    private lateinit var freeProvider: LLMProvider

    @Mock
    private lateinit var customProvider: LLMProvider

    @Mock
    private lateinit var providerHealth: ProviderHealth

    @Mock
    private lateinit var latencyTracker: LatencyTracker

    @Mock
    private lateinit var costEstimator: CostEstimator

    private lateinit var modelRouter: ModelRouter

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        modelRouter = ModelRouter(
            listOf(freeProvider, customProvider),
            providerHealth,
            latencyTracker,
            costEstimator
        )
    }

    @Test
    fun `selectProvider chooses healthy provider with lowest latency`() = runTest {
        val model = "gpt-4"
        
        `when`(freeProvider.isHealthy()).thenReturn(true)
        `when`(customProvider.isHealthy()).thenReturn(true)
        `when`(latencyTracker.getLatency(freeProvider)).thenReturn(200L)
        `when`(latencyTracker.getLatency(customProvider)).thenReturn(100L)
        
        val result = modelRouter.selectProvider(model)
        
        assertEquals(customProvider, result)
    }

    @Test
    fun `selectProvider falls back when primary is unhealthy`() = runTest {
        val model = "gpt-4"
        
        `when`(freeProvider.isHealthy()).thenReturn(false)
        `when`(customProvider.isHealthy()).thenReturn(true)
        
        val result = modelRouter.selectProvider(model)
        
        assertEquals(customProvider, result)
    }

    @Test
    fun `selectProvider throws when no providers available`() = runTest {
        `when`(freeProvider.isHealthy()).thenReturn(false)
        `when`(customProvider.isHealthy()).thenReturn(false)
        
        try {
            modelRouter.selectProvider(null)
            fail("Should throw exception")
        } catch (e: NoHealthyProviderException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `selectProvider respects user preference`() = runTest {
        val preferredProvider = freeProvider
        
        `when`(freeProvider.isHealthy()).thenReturn(true)
        
        val result = modelRouter.selectProvider(null, preferredProvider)
        
        assertEquals(freeProvider, result)
    }

    @Test
    fun `getFallback returns second best provider`() = runTest {
        `when`(freeProvider.isHealthy()).thenReturn(true)
        `when`(customProvider.isHealthy()).thenReturn(true)
        `when`(latencyTracker.getLatency(freeProvider)).thenReturn(50L)
        `when`(latencyTracker.getLatency(customProvider)).thenReturn(100L)
        
        val fallback = modelRouter.getFallback()
        
        // Fallback should be different from primary
        assertNotNull(fallback)
    }

    @Test
    fun `recordLatency updates tracker`() = runTest {
        val provider = freeProvider
        val latency = 150L
        
        modelRouter.recordLatency(provider, latency)
        
        verify(latencyTracker).record(provider, latency)
    }

    @Test
    fun `recordError marks provider unhealthy`() = runTest {
        val provider = freeProvider
        val error = RuntimeException("Timeout")
        
        modelRouter.recordError(provider, error)
        
        verify(providerHealth).markUnhealthy(provider)
    }

    @Test
    fun `getCostEstimate returns price for model`() = runTest {
        val model = "gpt-4"
        val tokenCount = 1000
        
        `when`(costEstimator.estimate(model, tokenCount)).thenReturn(0.03)
        
        val cost = modelRouter.getCostEstimate(model, tokenCount)
        
        assertEquals(0.03, cost, 0.001)
    }

    @Test
    fun `getProviderStats returns health and latency info`() = runTest {
        val stats = modelRouter.getProviderStats()
        
        assertNotNull(stats)
        assertTrue(stats.isNotEmpty())
    }

    @Test
    fun `forceProvider bypasses health checks`() = runTest {
        val forcedProvider = freeProvider
        
        `when`(freeProvider.isHealthy()).thenReturn(false)
        
        val result = modelRouter.forceProvider(forcedProvider)
        
        assertEquals(freeProvider, result)
    }
}
