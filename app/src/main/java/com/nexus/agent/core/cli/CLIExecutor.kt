package com.nexus.agent.core.cli

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

data class CLIResult(
    val output: String,
    val error: String,
    val exitCode: Int,
    val isSuccess: Boolean = exitCode == 0,
)

@Singleton
class CLIExecutor @Inject constructor(
    private val shellSession: ShellSession,
    private val commandParser: CommandParser,
    private val permissionHandler: PermissionHandler,
    private val commandHistory: CommandHistory,
) {
    fun executeStream(rawCommand: String, useRoot: Boolean = false): Flow<String> = flow {
        commandHistory.add(rawCommand)
        val parsed = commandParser.parse(rawCommand)

        if (!permissionHandler.canExecute(parsed, useRoot)) {
            emit("[ERROR] Permission denied: ${parsed.command}")
            return@flow
        }

        val session = if (useRoot) shellSession.getRootSession() else shellSession.getUserSession()
        session?.let { proc ->
            proc.outputStream.bufferedWriter().apply {
                write("$rawCommand\n")
                flush()
            }
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                emit(line!!)
            }
        } ?: emit("[ERROR] Shell session not available")
    }.flowOn(Dispatchers.IO)

    suspend fun execute(command: String, useRoot: Boolean = false): CLIResult =
        withContext(Dispatchers.IO) {
            commandHistory.add(command)
            try {
                val pb = if (useRoot) {
                    ProcessBuilder("su", "-c", command)
                } else {
                    ProcessBuilder("sh", "-c", command)
                }
                pb.redirectErrorStream(true)
                val proc = pb.start()
                val output = proc.inputStream.bufferedReader().readText()
                val exit = proc.waitFor()
                CLIResult(output = output.trim(), error = "", exitCode = exit)
            } catch (e: Exception) {
                CLIResult(output = "", error = e.message ?: "Unknown error", exitCode = -1)
            }
        }
}