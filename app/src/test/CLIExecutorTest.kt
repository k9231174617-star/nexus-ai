package com.nexus.agent.core.cli

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class CLIExecutorTest {

    @Mock
    private lateinit var shellSession: ShellSession

    @Mock
    private lateinit var commandParser: CommandParser

    @Mock
    private lateinit var permissionHandler: PermissionHandler

    private lateinit var cliExecutor: CLIExecutor

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        cliExecutor = CLIExecutor(shellSession, commandParser, permissionHandler)
    }

    @Test
    fun `executeCommand returns success for valid command`() = runTest {
        val rawInput = "ls -la"
        val parsedCommand = ParsedCommand("ls", listOf("-la"))
        
        `when`(commandParser.parse(rawInput)).thenReturn(parsedCommand)
        `when`(permissionHandler.hasPermission(parsedCommand)).thenReturn(true)
        `when`(shellSession.execute("ls -la")).thenReturn(
            ShellResult.Success("file1\nfile2", 0)
        )
        
        val result = cliExecutor.executeCommand(rawInput)
        
        assertTrue(result is ShellResult.Success)
        assertEquals("file1\nfile2", (result as ShellResult.Success).output)
    }

    @Test
    fun `executeCommand returns error for permission denied`() = runTest {
        val rawInput = "rm -rf /"
        val parsedCommand = ParsedCommand("rm", listOf("-rf", "/"))
        
        `when`(commandParser.parse(rawInput)).thenReturn(parsedCommand)
        `when`(permissionHandler.hasPermission(parsedCommand)).thenReturn(false)
        
        val result = cliExecutor.executeCommand(rawInput)
        
        assertTrue(result is ShellResult.Error)
        assertTrue((result as ShellResult.Error).message.contains("Permission denied"))
    }

    @Test
    fun `executeCommand handles shell session error`() = runTest {
        val rawInput = "badcmd"
        val parsedCommand = ParsedCommand("badcmd", emptyList())
        
        `when`(commandParser.parse(rawInput)).thenReturn(parsedCommand)
        `when`(permissionHandler.hasPermission(parsedCommand)).thenReturn(true)
        `when`(shellSession.execute("badcmd")).thenReturn(
            ShellResult.Error("command not found", 127)
        )
        
        val result = cliExecutor.executeCommand(rawInput)
        
        assertTrue(result is ShellResult.Error)
        assertEquals(127, (result as ShellResult.Error).exitCode)
    }

    @Test
    fun `executeCommand trims input before parsing`() = runTest {
        val rawInput = "  ls -la  "
        
        cliExecutor.executeCommand(rawInput)
        
        verify(commandParser).parse("ls -la")
    }

    @Test
    fun `executeCommand handles empty input`() = runTest {
        val result = cliExecutor.executeCommand("")
        
        assertTrue(result is ShellResult.Error)
    }

    @Test
    fun `executeCommand supports sudo prefix`() = runTest {
        val rawInput = "sudo apt update"
        val parsedCommand = ParsedCommand("apt", listOf("update"), sudo = true)
        
        `when`(commandParser.parse(rawInput)).thenReturn(parsedCommand)
        `when`(permissionHandler.hasPermission(parsedCommand)).thenReturn(true)
        `when`(shellSession.executeSudo("apt update")).thenReturn(
            ShellResult.Success("updated", 0)
        )
        
        val result = cliExecutor.executeCommand(rawInput)
        
        assertTrue(result is ShellResult.Success)
        verify(shellSession).executeSudo("apt update")
    }

    @Test
    fun `cancelExecution interrupts current process`() {
        cliExecutor.cancelExecution()
        
        verify(shellSession).interrupt()
    }

    @Test
    fun `isExecuting returns false initially`() {
        assertFalse(cliExecutor.isExecuting())
    }

    @Test
    fun `getCurrentDirectory delegates to shell session`() {
        `when`(shellSession.getCurrentDirectory()).thenReturn("/home/user")
        
        assertEquals("/home/user", cliExecutor.getCurrentDirectory())
    }

    @Test
    fun `getEnvironmentVariables returns map`() {
        val env = mapOf("PATH" to "/usr/bin", "HOME" to "/home/user")
        
        `when`(shellSession.getEnvironment()).thenReturn(env)
        
        assertEquals(env, cliExecutor.getEnvironmentVariables())
    }
}
