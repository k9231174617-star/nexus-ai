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
    private lateinit var resourceLimiter: ResourceLimiter

    @Mock
    private lateinit var languageRunner: LanguageRunner

    private lateinit var codeSandbox: CodeSandbox

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        codeSandbox = CodeSandbox(resourceLimiter, languageRunner)
    }

    @Test
    fun `execute runs code via language runner`() = runTest {
        val config = SandboxConfig()
        val expected = SandboxResult("hello", "", 0, 10, 0f)

        `when`(resourceLimiter.hasCapacity()).thenReturn(true)
        `when`(languageRunner.run("print('hello')", "python", config)).thenReturn(expected)

        val result = codeSandbox.execute("print('hello')", "python", config)

        assertEquals(expected.stdout, result.stdout)
    }

    @Test
    fun `execute returns error when no capacity`() = runTest {
        `when`(resourceLimiter.hasCapacity()).thenReturn(false)

        val result = codeSandbox.execute("code", "python")

        assertTrue(result.stderr.contains("resources exhausted"))
    }

    @Test
    fun `validate returns warnings for dangerous code`() = runTest {
        val warnings = codeSandbox.validate("import os; os.system('rm -rf /')", "python")

        assertTrue(warnings.isNotEmpty())
    }
}
