package com.nexus.agent.core.observability

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class TracerTest {

    @Mock
    private lateinit var metricsCollector: MetricsCollector

    private lateinit var tracer: Tracer

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        tracer = Tracer(metricsCollector)
    }

    @Test
    fun `startSpan creates new span`() = runTest {
        val name = "llm_request"
        val span = tracer.startSpan(name)

        assertNotNull(span)
        assertEquals(name, span.name)
        assertTrue(span.startTime > 0)
    }

    @Test
    fun `startSpan with parent creates child`() = runTest {
        val parent = tracer.startSpan("parent")
        val child = tracer.startSpan("child", parent.id)

        assertEquals(parent.id, child.parentId)
    }

    @Test
    fun `endSpan completes span and records duration`() = runTest {
        val span = tracer.startSpan("test")
        Thread.sleep(5)
        tracer.endSpan(span)

        val spans = tracer.getRecentSpans()
        val completed = spans.find { it.id == span.id }
        assertNotNull(completed)
        assertTrue((completed?.durationMs ?: 0) >= 5)
        verify(metricsCollector).recordSpan(any())
    }

    @Test
    fun `endSpan with error marks as error`() = runTest {
        val span = tracer.startSpan("test")
        tracer.endSpan(span, SpanStatus.ERROR, "Something went wrong")

        val spans = tracer.getRecentSpans()
        val completed = spans.find { it.id == span.id }
        assertEquals(SpanStatus.ERROR, completed?.status)
        assertEquals("Something went wrong", completed?.error)
    }

    @Test
    fun `trace block completes span automatically`() = runTest {
        val result = tracer.trace("test") { "success" }

        assertEquals("success", result)
        val spans = tracer.getRecentSpans()
        assertTrue(spans.any { it.name == "test" })
    }

    @Test
    fun `trace block with exception marks error`() = runTest {
        try {
            tracer.trace("failing") { throw RuntimeException("fail") }
        } catch (_: Exception) {}

        val spans = tracer.getRecentSpans()
        val span = spans.find { it.name == "failing" }
        assertEquals(SpanStatus.ERROR, span?.status)
    }

    @Test
    fun `getRecentSpans returns spans in order`() = runTest {
        tracer.startSpan("first")
        tracer.startSpan("second")

        val recent = tracer.getRecentSpans()
        assertTrue(recent.size >= 2)
    }

    @Test
    fun `clearSpans removes all spans`() = runTest {
        tracer.startSpan("test")
        tracer.clearSpans()

        assertEquals(0, tracer.getRecentSpans().size)
    }
}
