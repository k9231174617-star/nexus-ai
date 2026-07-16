package com.nexus.agent.core.root

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * RootBridge — central coordinator for root shell sessions.
 * Manages persistent su connections, command dispatch, and output streaming.
 */
class RootBridge(
    private val context: Context,
    private val suChecker: SuChecker
) {
    companion object {
        private const val TAG = "RootBridge"
        private const val SU_TIMEOUT_MS = 30000L
        private const val IDLE_TIMEOUT_MS = 120000L
        private const val BUFFER_SIZE = 8192
    }

    private var suProcess: Process? = null
    private var suOutputStream: DataOutputStream? = null
    private var suInputStream: BufferedReader? = null
    private var suErrorStream: BufferedReader? = null

    private val commandQueue = mutableListOf<PendingCommand>()
    private val activeCommands = ConcurrentHashMap<String, ActiveCommand>()
    private val isConnected = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var idleMonitor: Thread? = null

    private val listeners = mutableListOf<RootBridgeListener>()

    interface RootBridgeListener {
        fun onConnected()
        fun onDisconnected(reason: String)
        fun onCommandOutput(commandId: String, line: String)
        fun onCommandError(commandId: String, line: String)
        fun onCommandCompleted(commandId: String, exitCode: Int)
    }

    data class PendingCommand(
        val id: String,
        val command: String,
        val callback: ((Result<CommandOutput>) -> Unit)?
    )

    data class ActiveCommand(
        val id: String,
        val command: String,
        val startTime: Long,
        val outputBuffer: StringBuilder = StringBuilder(),
        val errorBuffer: StringBuilder = StringBuilder()
    )

    data class CommandOutput(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
        val durationMs: Long
    )

    fun addListener(listener: RootBridgeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: RootBridgeListener) {
        listeners.remove(listener)
    }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (isConnected.get()) return@withContext true
        if (!suChecker.isRootAvailable()) {
            Log.w(TAG, "Root not available on device")
            return@withContext false
        }

        return@withContext try {
            val suPath = suChecker.getSuPath() ?: "su"
            val processBuilder = ProcessBuilder(suPath)
            processBuilder.redirectErrorStream(false)

            suProcess = processBuilder.start()
            suOutputStream = DataOutputStream(suProcess!!.outputStream)
            suInputStream = BufferedReader(InputStreamReader(suProcess!!.inputStream))
            suErrorStream = BufferedReader(InputStreamReader(suProcess!!.errorStream))

            // Verify root access with id command
            suOutputStream?.writeBytes("id\n")
            suOutputStream?.flush()

            val idLine = suInputStream?.readLine()
            if (idLine == null || !idLine.contains("uid=0")) {
                disconnect("Root verification failed: $idLine")
                return@withContext false
            }

            // Set up shell environment
            suOutputStream?.writeBytes("export PATH=/system/bin:/system/xbin:/sbin:/vendor/bin:/data/local/bin:$PATH\n")
            suOutputStream?.writeBytes("cd /\n")
            suOutputStream?.flush()

            isConnected.set(true)
            startOutputReader()
            startErrorReader()
            startIdleMonitor()

            listeners.forEach { it.onConnected() }
            Log.i(TAG, "Root bridge connected")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect root bridge", e)
            false
        }
    }

    fun disconnect(reason: String = "Manual disconnect") {
        if (isDestroyed.get()) return

        isConnected.set(false)
        stopIdleMonitor()

        try {
            suOutputStream?.writeBytes("exit\n")
            suOutputStream?.flush()
        } catch (_: Exception) { }

        try {
            suProcess?.waitFor(500, TimeUnit.MILLISECONDS)
        } catch (_: Exception) { }

        try {
            suProcess?.destroyForcibly()
        } catch (_: Exception) { }

        suOutputStream = null
        suInputStream = null
        suErrorStream = null
        suProcess = null

        activeCommands.clear()
        listeners.forEach { it.onDisconnected(reason) }
        Log.i(TAG, "Root bridge disconnected: $reason")
    }

    fun destroy() {
        isDestroyed.set(true)
        disconnect("Bridge destroyed")
        listeners.clear()
    }

    suspend fun execute(command: String, timeoutMs: Long = SU_TIMEOUT_MS): Result<CommandOutput> = withContext(Dispatchers.IO) {
        if (!isConnected.get()) {
            val connected = connect()
            if (!connected) {
                return@withContext Result.failure(Exception("Root bridge not connected"))
            }
        }

        val commandId = generateCommandId()
        val activeCmd = ActiveCommand(
            id = commandId,
            command = command,
            startTime = System.currentTimeMillis()
        )
        activeCommands[commandId] = activeCmd

        return@withContext try {
            // Send command with marker for tracking completion
            val marker = "__NEXUS_CMD_END_${commandId}__"
            val wrappedCommand = "$command; echo $marker \$?\n"

            suOutputStream?.writeBytes(wrappedCommand)
            suOutputStream?.flush()

            // Read output until marker found or timeout
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            var exitCode = -1
            val deadline = System.currentTimeMillis() + timeoutMs

            while (System.currentTimeMillis() < deadline) {
                val line = suInputStream?.readLine()
                if (line == null) {
                    if (!isConnected.get()) break
                    Thread.sleep(10)
                    continue
                }

                if (line.contains(marker)) {
                    val parts = line.split(marker)
                    if (parts.size > 1) {
                        exitCode = parts[1].trim().toIntOrNull() ?: -1
                    }
                    break
                }

                stdout.appendLine(line)
                activeCmd.outputBuffer.appendLine(line)
                listeners.forEach { it.onCommandOutput(commandId, line) }
            }

            // Read any remaining stderr
            while (suErrorStream?.ready() == true) {
                val errLine = suErrorStream?.readLine()
                if (errLine != null) {
                    stderr.appendLine(errLine)
                    activeCmd.errorBuffer.appendLine(errLine)
                    listeners.forEach { it.onCommandError(commandId, errLine) }
                }
            }

            val duration = System.currentTimeMillis() - activeCmd.startTime
            val output = CommandOutput(
                stdout = stdout.toString().trimEnd(),
                stderr = stderr.toString().trimEnd(),
                exitCode = exitCode,
                durationMs = duration
            )

            activeCommands.remove(commandId)
            listeners.forEach { it.onCommandCompleted(commandId, exitCode) }

            Result.success(output)
        } catch (e: Exception) {
            activeCommands.remove(commandId)
            Result.failure(e)
        }
    }

    suspend fun executeAsync(command: String, callback: (Result<CommandOutput>) -> Unit) {
        val result = execute(command)
        callback(result)
    }

    fun executeStream(command: String, onOutput: (String) -> Unit, onError: (String) -> Unit): String {
        if (!isConnected.get()) {
            return "Error: Root bridge not connected"
        }

        val commandId = generateCommandId()
        val activeCmd = ActiveCommand(
            id = commandId,
            command = command,
            startTime = System.currentTimeMillis()
        )
        activeCommands[commandId] = activeCmd

        try {
            val marker = "__NEXUS_CMD_END_${commandId}__"
            val wrappedCommand = "$command; echo $marker \$?\n"

            suOutputStream?.writeBytes(wrappedCommand)
            suOutputStream?.flush()

            // Return command ID for tracking; output delivered via callbacks
            return commandId
        } catch (e: Exception) {
            activeCommands.remove(commandId)
            return "Error: ${e.message}"
        }
    }

    suspend fun executeMultiple(commands: List<String>, continueOnError: Boolean = false): List<Result<CommandOutput>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Result<CommandOutput>>()

        for (cmd in commands) {
            val result = execute(cmd)
            results.add(result)

            if (!continueOnError && result.isFailure) {
                break
            }

            if (result.isSuccess && result.getOrNull()?.exitCode != 0 && !continueOnError) {
                break
            }
        }

        results
    }

    suspend fun writeFile(path: String, content: String, append: Boolean = false): Result<CommandOutput> {
        val redirect = if (append) ">>" else ">"
        val command = "cat << 'NEXUS_EOF' $redirect \"$path\"\n$content\nNEXUS_EOF"
        return execute(command)
    }

    suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        val result = execute("cat \"$path\" 2>/dev/null || echo \"__NEXUS_FILE_NOT_FOUND__\"")
        return@withContext if (result.isSuccess) {
            val output = result.getOrNull()?.stdout ?: ""
            if (output.contains("__NEXUS_FILE_NOT_FOUND__")) {
                Result.failure(Exception("File not found: $path"))
            } else {
                Result.success(output)
            }
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    suspend fun fileExists(path: String): Boolean {
        val result = execute("[ -f \"$path\" ] && echo \"exists\" || echo \"missing\"")
        return result.isSuccess && result.getOrNull()?.stdout?.trim() == "exists"
    }

    suspend fun directoryExists(path: String): Boolean {
        val result = execute("[ -d \"$path\" ] && echo \"exists\" || echo \"missing\"")
        return result.isSuccess && result.getOrNull()?.stdout?.trim() == "exists"
    }

    suspend fun listDirectory(path: String): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        val command = "ls -la \"$path\" 2>/dev/null || echo \"__NEXUS_DIR_ERROR__\""
        val result = execute(command)

        return@withContext if (result.isSuccess) {
            val output = result.getOrNull()?.stdout ?: ""
            if (output.contains("__NEXUS_DIR_ERROR__")) {
                Result.failure(Exception("Directory not accessible: $path"))
            } else {
                val entries = parseLsOutput(output, path)
                Result.success(entries)
            }
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    private fun parseLsOutput(output: String, basePath: String): List<FileEntry> {
        return output.lines()
            .drop(1) // Skip total line
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"), limit = 9)
                if (parts.size < 9) return@mapNotNull null

                val permissions = parts[0]
                val owner = parts[2]
                val group = parts[3]
                val size = parts[4].toLongOrNull() ?: 0L
                val name = parts[8]
                val fullPath = if (basePath.endsWith("/")) "$basePath$name" else "$basePath/$name"

                val type = when {
                    permissions.startsWith("d") -> FileEntry.Type.DIRECTORY
                    permissions.startsWith("l") -> FileEntry.Type.SYMLINK
                    permissions.startsWith("-") -> FileEntry.Type.FILE
                    else -> FileEntry.Type.OTHER
                }

                FileEntry(
                    name = name,
                    path = fullPath,
                    type = type,
                    size = size,
                    permissions = permissions,
                    owner = owner,
                    group = group
                )
            }
    }

    suspend fun chmod(path: String, mode: String): Result<CommandOutput> {
        return execute("chmod $mode \"$path\"")
    }

    suspend fun chown(path: String, owner: String, group: String? = null): Result<CommandOutput> {
        val target = if (group != null) "$owner:$group" else owner
        return execute("chown $target \"$path\"")
    }

    suspend fun mount(partition: String, mountPoint: String, options: String = "rw"): Result<CommandOutput> {
        return execute("mount -o remount,$options $partition $mountPoint")
    }

    suspend fun remountSystem(rw: Boolean = true): Result<CommandOutput> {
        val mode = if (rw) "rw" else "ro"
        return when {
            directoryExists("/system_root") -> execute("mount -o remount,$mode /system_root")
            directoryExists("/system") -> execute("mount -o remount,$mode /system")
            else -> Result.failure(Exception("System partition not found"))
        }
    }

    suspend fun killProcess(pid: Int): Result<CommandOutput> {
        return execute("kill -9 $pid")
    }

    suspend fun getProcessList(): Result<List<ProcessInfo>> = withContext(Dispatchers.IO) {
        val result = execute("ps -A -o PID,PPID,USER,NAME,VSZ,RSS,CPU,ARGS 2>/dev/null || ps -o pid,ppid,user,comm,vsz,rss,pcpu,args")
        return@withContext if (result.isSuccess) {
            val processes = parsePsOutput(result.getOrNull()?.stdout ?: "")
            Result.success(processes)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    private fun parsePsOutput(output: String): List<ProcessInfo> {
        return output.lines()
            .drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 8)
                if (parts.size < 8) return@mapNotNull null

                ProcessInfo(
                    pid = parts[0].toIntOrNull() ?: 0,
                    ppid = parts[1].toIntOrNull() ?: 0,
                    user = parts[2],
                    name = parts[3],
                    vsz = parts[4].toLongOrNull() ?: 0L,
                    rss = parts[5].toLongOrNull() ?: 0L,
                    cpuPercent = parts[6].toFloatOrNull() ?: 0f,
                    args = parts.getOrNull(7) ?: ""
                )
            }
    }

    suspend fun getMounts(): Result<List<MountEntry>> = withContext(Dispatchers.IO) {
        val result = execute("cat /proc/mounts")
        return@withContext if (result.isSuccess) {
            val mounts = result.getOrNull()?.stdout?.lines()
                ?.filter { it.isNotBlank() }
                ?.mapNotNull { line ->
                    val parts = line.split(" ")
                    if (parts.size < 4) return@mapNotNull null
                    MountEntry(
                        device = parts[0],
                        mountPoint = parts[1],
                        filesystem = parts[2],
                        options = parts[3]
                    )
                } ?: emptyList()
            Result.success(mounts)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    fun isConnected(): Boolean = isConnected.get()

    fun getActiveCommandCount(): Int = activeCommands.size

    private fun startOutputReader() {
        Thread {
            while (isConnected.get() && !isDestroyed.get()) {
                try {
                    val line = suInputStream?.readLine() ?: break
                    // Handle async output for streaming commands
                    activeCommands.values.forEach { cmd ->
                        if (!line.contains("__NEXUS_CMD_END_${cmd.id}__")) {
                            cmd.outputBuffer.appendLine(line)
                            mainHandler.post {
                                listeners.forEach { it.onCommandOutput(cmd.id, line) }
                            }
                        }
                    }
                } catch (_: Exception) {
                    if (!isConnected.get()) break
                }
            }
        }.apply {
            name = "RootBridge-OutputReader"
            isDaemon = true
            start()
        }
    }

    private fun startErrorReader() {
        Thread {
            while (isConnected.get() && !isDestroyed.get()) {
                try {
                    val line = suErrorStream?.readLine() ?: break
                    activeCommands.values.forEach { cmd ->
                        cmd.errorBuffer.appendLine(line)
                        mainHandler.post {
                            listeners.forEach { it.onCommandError(cmd.id, line) }
                        }
                    }
                } catch (_: Exception) {
                    if (!isConnected.get()) break
                }
            }
        }.apply {
            name = "RootBridge-ErrorReader"
            isDaemon = true
            start()
        }
    }

    private fun startIdleMonitor() {
        idleMonitor = Thread {
            while (isConnected.get() && !isDestroyed.get()) {
                Thread.sleep(IDLE_TIMEOUT_MS)
                if (activeCommands.isEmpty() && System.currentTimeMillis() - lastActivityTime() > IDLE_TIMEOUT_MS) {
                    disconnect("Idle timeout")
                    break
                }
            }
        }.apply {
            name = "RootBridge-IdleMonitor"
            isDaemon = true
            start()
        }
    }

    private fun stopIdleMonitor() {
        idleMonitor?.interrupt()
        idleMonitor = null
    }

    private fun lastActivityTime(): Long {
        return activeCommands.values.maxOfOrNull { it.startTime } ?: System.currentTimeMillis()
    }

    private fun generateCommandId(): String {
        return "cmd_${System.currentTimeMillis()}_${(0..9999).random()}"
    }

    data class FileEntry(
        val name: String,
        val path: String,
        val type: Type,
        val size: Long,
        val permissions: String,
        val owner: String,
        val group: String
    ) {
        enum class Type { FILE, DIRECTORY, SYMLINK, OTHER }
    }

    data class ProcessInfo(
        val pid: Int,
        val ppid: Int,
        val user: String,
        val name: String,
        val vsz: Long,
        val rss: Long,
        val cpuPercent: Float,
        val args: String
    )

    data class MountEntry(
        val device: String,
        val mountPoint: String,
        val filesystem: String,
        val options: String
    )
}
