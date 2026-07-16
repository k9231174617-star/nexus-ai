package com.nexus.agent.core.sandbox

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class SandboxTest {

    @Mock
    private lateinit var sandboxConfig: SandboxConfig

    @Mock
    private lateinit var namespaceContainer: NamespaceContainer

    @Mock
    private lateinit var processContainer: ProcessContainer

    @Mock
    private lateinit var resourceLimiter: ResourceLimiter

    @Mock
    private lateinit var languageRunner: LanguageRunner

    private lateinit var codeSandbox: CodeSandbox

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        codeSandbox = CodeSandbox(sandboxConfig, namespaceContainer, processContainer, resourceLimiter, languageRunner)
    }

    @Test
    fun `executeCode runs Python script safely`() = runTest {
        val code = "print('Hello')"
        val language = "python"
        val expectedOutput = "Hello\n"
        
        `when`(sandboxConfig.isLanguageAllowed(language)).thenReturn(true)
        `when`(languageRunner.run(language, code)).thenReturn(
            SandboxResult.Success(expectedOutput, 0, 100)
        )
        
        val result = codeSandbox.executeCode(code, language)
        
        assertTrue(result is SandboxResult.Success)
        assertEquals(expectedOutput, (result as SandboxResult.Success).output)
    }

    @Test
    fun `executeCode rejects disallowed language`() = runTest {
        val code = "rm -rf /"
        val language = "bash"
        
        `when`(sandboxConfig.isLanguageAllowed(language)).thenReturn(false)
        
        val result = codeSandbox.executeCode(code, language)
        
        assertTrue(result is SandboxResult.Error)
        assertTrue((result as SandboxResult.Error).message.contains("not allowed"))
    }

    @Test
    fun `executeCode enforces timeout`() = runTest {
        val code = "while True: pass"
        val language = "python"
        
        `when`(sandboxConfig.isLanguageAllowed(language)).thenReturn(true)
        `when`(sandboxConfig.getTimeoutMs()).thenReturn(1000L)
        `when`(languageRunner.run(language, code)).thenReturn(
            SandboxResult.Timeout
        )
        
        val result = codeSandbox.executeCode(code, language)
        
        assertTrue(result is SandboxResult.Timeout)
    }

    @Test
    fun `executeCode limits memory usage`() = runTest {
        val code = "x = ' ' * 1024 * 1024 * 1024"
        val language = "python"
        
        `when`(sandboxConfig.isLanguageAllowed(language)).thenReturn(true)
        `when`(sandboxConfig.getMaxMemoryMb()).thenReturn(64)
        `when`(languageRunner.run(language, code)).thenReturn(
            SandboxResult.MemoryExceeded
        )
        
        val result = codeSandbox.executeCode(code, language)
        
        assertTrue(result is SandboxResult.MemoryExceeded)
    }

    @Test
    fun `executeCode handles runtime errors`() = runTest {
        val code = "1/0"
        val language = "python"
        
        `when`(sandboxConfig.isLanguageAllowed(language)).thenReturn(true)
        `when`(languageRunner.run(language, code)).thenReturn(
            SandboxResult.Error("ZeroDivisionError", 1)
        )
        
        val result = codeSandbox.executeCode(code, language)
        
        assertTrue(result is SandboxResult.Error)
    }

    @Test
    fun `createNamespace isolates execution environment`() = runTest {
        val namespace = "ns-1"
        
        codeSandbox.createNamespace(namespace)
        
        verify(namespaceContainer).create(namespace)
    }

    @Test
    fun `destroyNamespace cleans up resources`() = runTest {
        val namespace = "ns-1"
        
        codeSandbox.destroyNamespace(namespace)
        
        verify(namespaceContainer).destroy(namespace)
        verify(processContainer).killAll(namespace)
    }

    @Test
    fun `getResourceUsage returns current metrics`() = runTest {
        val expectedMetrics = ResourceMetrics(cpuPercent = 10.5, memoryMb = 32, ioReads = 100)
        
        `when`(resourceLimiter.getCurrentUsage()).thenReturn(expectedMetrics)
        
        assertEquals(expectedMetrics, codeSandbox.getResourceUsage())
    }

    @Test
    fun `isRunning returns true for active sandbox`() = runTest {
        val sandboxId = "sb-1"
        
        `when`(processContainer.isActive(sandboxId)).thenReturn(true)
        
        assertTrue(codeSandbox.isRunning(sandboxId))
    }

    @Test
    fun `killForcefully terminates sandbox`() = runTest {
        val sandboxId = "sb-1"
        
        codeSandbox.killForcefully(sandboxId)
        
        verify(processContainer).kill(sandboxId, force = true)
    }
}
