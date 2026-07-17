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
    private lateinit var routePreferences: RoutePreferences

    @Mock
    private lateinit var providerHealth: ProviderHealth

    @Mock
    private lateinit var costEstimator: CostEstimator

    @Mock
    private lateinit var latencyTracker: LatencyTracker

    @Mock
    private lateinit var fallbackChain: FallbackChain

    private lateinit var modelRouter: ModelRouter

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        modelRouter = ModelRouter(routePreferences, providerHealth, costEstimator, latencyTracker, fallbackChain)
    }

    @Test
    fun `route selects preferred provider`() = runTest {
        val model = "gpt-4"

        `when`(providerHealth.getHealthiestProvider(model)).thenReturn("openai")
        `when`(routePreferences.getPreferredProvider()).thenReturn("openai")

        val result = modelRouter.route(model)

        assertEquals("openai", result)
    }

    @Test
    fun `route falls back when preferred unhealthy`() = runTest {
        `when`(providerHealth.getHealthiestProvider("gpt-4")).thenReturn(null)
        `when`(fallbackChain.getFallback("gpt-4")).thenReturn("anthropic")

        val result = modelRouter.route("gpt-4")

        assertEquals("anthropic", result)
    }

    @Test
    fun `estimateCost returns price`() = runTest {
        `when`(costEstimator.estimate("gpt-4", 1000)).thenReturn(0.03)

        val cost = modelRouter.estimateCost("gpt-4", 1000)

        assertEquals(0.03, cost, 0.001)
    }

    @Test
    fun `recordLatency updates latency tracker`() = runTest {
        modelRouter.recordLatency("openai", 150L)
        verify(latencyTracker).record("openai", 150L)
    }

    @Test
    fun `recordFailure marks provider`() = runTest {
        modelRouter.recordFailure("openai")
        verify(providerHealth).markFailure("openai")
    }
}
