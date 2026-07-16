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

    @Mock
    private lateinit var bottleneckAnalyzer: BottleneckAnalyzer

    @Mock
    private lateinit var prometheusExporter: PrometheusExporter

    private lateinit var tracer: Tracer

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        tracer = Tracer(metricsCollector, bottleneckAnalyzer, prometheusExporter)
    }

    @Test
    fun `startSpan creates new span with trace context`() = runTest {
        val operation = "llm_request"
        val traceId = "trace-123"
        
        val span = tracer.startSpan(operation, traceId)
        
        assertNotNull(span)
        assertEquals(operation, span.operation)
        assertEquals(traceId, span.traceId)
        assertTrue(span.startTime > 0)
        assertFalse(span.isFinished)
    }

    @Test
    fun `startSpan generates trace id if not provided`() = runTest {
        val span = tracer.startSpan("test")
        
        assertNotNull(span.traceId)
        assertFalse(span.traceId.isEmpty())
    }

    @Test
    fun `finishSpan completes span and records metrics`() = runTest {
        val span = tracer.startSpan("test")
        
        Thread.sleep(10) // Ensure some duration
        
        tracer.finishSpan(span)
        
        assertTrue(span.isFinished)
        assertTrue(span.durationMs >= 10)
        verify(metricsCollector).recordLatency("test", span.durationMs)
    }

    @Test
    fun `finishSpan with error marks span as failed`() = runTest {
        val span = tracer.startSpan("test")
        val error = RuntimeException("Failed")
        
        tracer.finishSpan(span, error)
        
        assertTrue(span.isFinished)
        assertTrue(span.hasError)
        assertEquals(error, span.error)
        verify(metricsCollector).recordError("test", error)
    }

    @Test
    fun `getTrace returns all spans for trace id`() = runTest {
        val traceId = "trace-123"
        val span1 = tracer.startSpan("op1", traceId)
        val span2 = tracer.startSpan("op2", traceId)
        
        tracer.finishSpan(span1)
        tracer.finishSpan(span2)
        
        val trace = tracer.getTrace(traceId)
        
        assertEquals(2, trace.size)
    }

    @Test
    fun `getActiveSpans returns unfinished spans`() = runTest {
        val span1 = tracer.startSpan("op1")
        val span2 = tracer.startSpan("op2")
        
        tracer.finishSpan(span1)
        
        val active = tracer.getActiveSpans()
        
        assertEquals(1, active.size)
        assertEquals("op2", active[0].operation)
    }

    @Test
    fun `cancelSpan marks as cancelled`() = runTest {
        val span = tracer.startSpan("test")
        
        tracer.cancelSpan(span)
        
        assertTrue(span.isCancelled)
        verify(metricsCollector).recordCancellation("test")
    }

    @Test
    fun `analyzeBottlenecks returns slow operations`() = runTest {
        val spans = listOf(
            Span("op1", "t1", 0, 100, true, false, null),
            Span("op2", "t1", 0, 5000, true, false, null)
        )
        
        `when`(bottleneckAnalyzer.findSlowOperations(spans, thresholdMs = 1000)).thenReturn(
            listOf(Bottleneck("op2", 5000, "Too slow"))
        )
        
        val bottlenecks = tracer.analyzeBottlenecks(spans)
        
        assertEquals(1, bottlenecks.size)
        assertEquals("op2", bottlenecks[0].operation)
    }

    @Test
    fun `exportToPrometheus sends metrics`() = runTest {
        tracer.exportToPrometheus()
        
        verify(prometheusExporter).export(any())
    }

    @Test
    fun `getSpanStats returns aggregated metrics`() = runTest {
        val span1 = tracer.startSpan("op1")
        val span2 = tracer.startSpan("op2")
        
        tracer.finishSpan(span1)
        tracer.finishSpan(span2)
        
        val stats = tracer.getSpanStats()
        
        assertTrue(stats.totalSpans >= 2)
        assertTrue(stats.avgDurationMs >= 0)
    }

    @Test
    fun `childSpan creates nested span`() = runTest {
        val parent = tracer.startSpan("parent")
        val child = tracer.childSpan("child", parent)
        
        assertEquals(parent.traceId, child.traceId)
        assertEquals(parent.id, child.parentId)
    }

    @Test
    fun `clearOldSpans removes spans older than threshold`() = runTest {
        tracer.startSpan("old")
        
        tracer.clearOldSpans(maxAgeMs = 0)
        
        assertEquals(0, tracer.getAllSpans().size)
    }
}
