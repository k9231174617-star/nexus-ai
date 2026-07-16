package com.nexus.agent.core.sandbox

import android.os.*
import android.system.Os
import android.system.OsConstants
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Управляет изолированными процессами внутри sandbox.
 * Контролирует запуск, выполнение, ограничение ресурсов и завершение процессов.
 */
class ProcessContainer(
    private val namespaceContainer: NamespaceContainer,
    private val resourceLimiter: ResourceLimiter
) {
    companion object {
        private const val TAG = "ProcessContainer"
        private const val DEFAULT_TIMEOUT_MS = 30000L
        private const val BUFFER_SIZE = 8192
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeProcesses = ConcurrentHashMap<Int, SandboxProcess>()
    private val processCounter = AtomicInteger(0)
    private val isShuttingDown = AtomicBoolean(false)

    data class SandboxProcess(
        val pid: Int,
        val process: Process,
        val startTime: Long,
        val command: String,
        val stdin: OutputStream?,
        val stdout: InputStream?,
        val stderr: InputStream?,
        val job: Job,
        val result: CompletableDeferred<SandboxResult>
    )

    /**
     * Запускает команду в изолированном sandbox-окружении.
     */
    suspend fun execute(
        command: List<String>,
        input: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        envVars: Map<String, String> = emptyMap(),
        workingDir: String? = null
    ): SandboxResult = withContext(Dispatchers.IO) {
        if (isShuttingDown.get()) {
            return@withContext SandboxResult(
                exitCode = -1,
                stdout = "",
                stderr = "Sandbox is shutting down",
                executionTimeMs = 0,
                memoryUsedKb = 0,
                wasKilled = false
            )
        }

        val processId = processCounter.incrementAndGet()
        val startTime = System.currentTimeMillis()

        try {
            // Проверяем и инициализируем namespace если нужно
            if (!namespaceContainer.isInsideSandbox()) {
                namespaceContainer.initialize()
            }

            // Собираем ProcessBuilder с ограничениями
            val pb = buildProcessBuilder(command, envVars, workingDir)

            // Запускаем процесс
            val process = pb.start()

            // Применяем resource limits через JNI/cgroups
            applyResourceLimits(process)

            val result = CompletableDeferred<SandboxResult>()

            // Создаём job для мониторинга процесса
            val processJob = coroutineScope.launch {
                monitorProcess(processId, process, timeoutMs, startTime, result)
            }

            // Записываем stdin если есть input
            input?.let { writeStdin(process, it) }

            val sandboxProcess = SandboxProcess(
                pid = processId,
                process = process,
                startTime = startTime,
                command = command.joinToString(" "),
                stdin = process.outputStream,
                stdout = process.inputStream,
                stderr = process.errorStream,
                job = processJob,
                result = result
            )

            activeProcesses[processId] = sandboxProcess

            // Читаем stdout и stderr параллельно
            val stdoutDeferred = async { readStream(process.inputStream) }
            val stderrDeferred = async { readStream(process.errorStream) }

            // Ждём завершения с таймаутом
            val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)

            val executionTime = System.currentTimeMillis() - startTime
            val memoryUsed = getMemoryUsage(process)

            val sandboxResult = if (!completed) {
                // Таймаут — убиваем процесс
                killProcess(processId, force = true)
                SandboxResult(
                    exitCode = -1,
                    stdout = stdoutDeferred.await(),
                    stderr = stderrDeferred.await() + "\n[TIMEOUT: Execution exceeded ${timeoutMs}ms]",
                    executionTimeMs = executionTime,
                    memoryUsedKb = memoryUsed,
                    wasKilled = true
                )
            } else {
                SandboxResult(
                    exitCode = process.exitValue(),
                    stdout = stdoutDeferred.await(),
                    stderr = stderrDeferred.await(),
                    executionTimeMs = executionTime,
                    memoryUsedKb = memoryUsed,
                    wasKilled = false
                )
            }

            result.complete(sandboxResult)
            activeProcesses.remove(processId)
            sandboxResult

        } catch (e: Exception) {
            activeProcesses.remove(processId)
            SandboxResult(
                exitCode = -1,
                stdout = "",
                stderr = "Execution failed: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime,
                memoryUsedKb = 0,
                wasKilled = false
            )
        }
    }

    /**
     * Создаёт ProcessBuilder с нужными настройками.
     */
    private fun buildProcessBuilder(
        command: List<String>,
        envVars: Map<String, String>,
        workingDir: String?
    ): ProcessBuilder {
        val pb = ProcessBuilder(command)

        // Настраиваем окружение
        val env = pb.environment()
        env.clear()

        // Базовые переменные
        env["PATH"] = "/usr/bin:/bin:/usr/local/bin"
        env["HOME"] = "/home/sandbox"
        env["TMPDIR"] = "/tmp"
        env["LANG"] = "C.UTF-8"

        // Дополнительные переменные пользователя
        env.putAll(envVars)

        // Рабочая директория
        workingDir?.let {
            pb.directory(File(namespaceContainer.getSandboxRoot(), it.removePrefix("/")))
        } ?: pb.directory(File(namespaceContainer.getSandboxRoot()))

        // Перенаправляем stderr в stdout для удобства
        pb.redirectErrorStream(false)

        return pb
    }

    /**
     * Применяет ограничения ресурсов к процессу через JNI.
     */
    private fun applyResourceLimits(process: Process) {
        try {
            val pid = getPid(process)

            // CPU time limit
            resourceLimiter.setCpuLimit(pid, config = resourceLimiter.cpuLimitSeconds)

            // Memory limit (RSS)
            resourceLimiter.setMemoryLimit(pid, resourceLimiter.memoryLimitMb * 1024 * 1024)

            // File descriptors limit
            resourceLimiter.setFdLimit(pid, resourceLimiter.maxOpenFiles)

            // Process count limit
            resourceLimiter.setNprocLimit(pid, resourceLimiter.maxProcesses)

            // Disk I/O bandwidth (если поддерживается)
            if (resourceLimiter.ioBandwidthMbps > 0) {
                resourceLimiter.setIoLimit(pid, resourceLimiter.ioBandwidthMbps)
            }

            Log.d(TAG, "Applied resource limits to process $pid")

        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply resource limits: ${e.message}")
        }
    }

    /**
     * Получает PID процесса через reflection.
     */
    private fun getPid(process: Process): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.pid()
            } else {
                val field = process.javaClass.getDeclaredField("pid")
                field.isAccessible = true
                field.getInt(process)
            }
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Мониторит процесс: проверяет memory limits, CPU usage, таймауты.
     */
    private suspend fun monitorProcess(
        processId: Int,
        process: Process,
        timeoutMs: Long,
        startTime: Long,
        result: CompletableDeferred<SandboxResult>
    ) {
        val checkInterval = 100L // проверяем каждые 100ms
        var lastMemoryCheck = 0L

        while (process.isAlive) {
            delay(checkInterval)

            // Проверяем таймаут
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                Log.w(TAG, "Process $processId timeout")
                killProcess(processId, force = true)
                return
            }

            // Проверяем memory usage каждые 500ms
            if (System.currentTimeMillis() - lastMemoryCheck > 500) {
                lastMemoryCheck = System.currentTimeMillis()

                val memoryKb = getMemoryUsage(process)
                val memoryLimitKb = resourceLimiter.memoryLimitMb * 1024

                if (memoryKb > memoryLimitKb) {
                    Log.w(TAG, "Process $processId exceeded memory limit: ${memoryKb}KB > ${memoryLimitKb}KB")
                    killProcess(processId, force = true)
                    return
                }
            }

            // Проверяем CPU usage
            val cpuUsage = getCpuUsage(processId)
            if (cpuUsage > resourceLimiter.cpuPercentLimit) {
                Log.w(TAG, "Process $processId CPU throttled: ${cpuUsage}%")
                // Можно применить throttling через cgroups
                throttleCpu(processId)
            }
        }
    }

    /**
     * Читает поток (stdout/stderr) полностью.
     */
    private suspend fun readStream(stream: InputStream): String = withContext(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(stream))
        val output = StringBuilder()
        val buffer = CharArray(BUFFER_SIZE)

        try {
            var charsRead: Int
            while (reader.read(buffer).also { charsRead = it } != -1) {
                output.append(buffer, 0, charsRead)

                // Ограничиваем размер вывода
                if (output.length > resourceLimiter.maxOutputSizeBytes) {
                    output.append("\n[OUTPUT TRUNCATED: exceeded ${resourceLimiter.maxOutputSizeBytes} bytes]")
                    break
                }
            }
        } catch (e: IOException) {
            // Поток закрыт — нормально
        }

        output.toString()
    }

    /**
     * Записывает данные в stdin процесса.
     */
    private suspend fun writeStdin(process: Process, input: String) = withContext(Dispatchers.IO) {
        try {
            process.outputStream.use { output ->
                output.write(input.toByteArray(Charsets.UTF_8))
                output.flush()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to write stdin: ${e.message}")
        }
    }

    /**
     * Получает использование памяти процесса в KB.
     */
    private fun getMemoryUsage(process: Process): Long {
        return try {
            val pid = getPid(process)
            val statusFile = File("/proc/$pid/status")
            if (statusFile.exists()) {
                val content = statusFile.readText()
                val vmRss = content.lines()
                    .find { it.startsWith("VmRSS:") }
                    ?.substringAfter("VmRSS:")?.trim()?.removeSuffix(" kB")
                    ?.trim()?.toLongOrNull() ?: 0L
                vmRss
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Получает CPU usage процесса в процентах.
     */
    private fun getCpuUsage(processId: Int): Double {
        // Упрощённая реализация — в реальности нужно парсить /proc/stat
        return 0.0
    }

    /**
     * Применяет CPU throttling к процессу.
     */
    private fun throttleCpu(processId: Int) {
        try {
            resourceLimiter.throttleCpu(getPid(activeProcesses[processId]?.process ?: return))
        } catch (e: Exception) {
            Log.w(TAG, "CPU throttling failed: ${e.message}")
        }
    }

    /**
     * Принудительно завершает процесс.
     */
    fun killProcess(processId: Int, force: Boolean = false): Boolean {
        val sandboxProcess = activeProcesses[processId] ?: return false

        return try {
            if (force) {
                sandboxProcess.process.destroyForcibly()
            } else {
                sandboxProcess.process.destroy()
                // Даём 2 секунды на graceful shutdown
                if (!sandboxProcess.process.waitFor(2, TimeUnit.SECONDS)) {
                    sandboxProcess.process.destroyForcibly()
                }
            }

            sandboxProcess.job.cancel()
            activeProcesses.remove(processId)

            Log.d(TAG, "Killed process $processId (force=$force)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to kill process $processId", e)
            false
        }
    }

    /**
     * Отправляет сигнал процессу.
     */
    fun sendSignal(processId: Int, signal: Int): Boolean {
        val sandboxProcess = activeProcesses[processId] ?: return false

        return try {
            val pid = getPid(sandboxProcess.process)
            Os.kill(pid, signal)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send signal $signal to process $processId", e)
            false
        }
    }

    /**
     * Получает список активных процессов.
     */
    fun getActiveProcesses(): List<SandboxProcessInfo> {
        return activeProcesses.values.map { sp ->
            SandboxProcessInfo(
                id = sp.pid,
                command = sp.command,
                startTime = sp.startTime,
                isAlive = sp.process.isAlive,
                memoryKb = if (sp.process.isAlive) getMemoryUsage(sp.process) else 0
            )
        }
    }

    data class SandboxProcessInfo(
        val id: Int,
        val command: String,
        val startTime: Long,
        val isAlive: Boolean,
        val memoryKb: Long
    )

    /**
     * Выполняет batch-задачи последовательно.
     */
    suspend fun executeBatch(
        commands: List<List<String>>,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): List<SandboxResult> {
        return commands.map { cmd ->
            execute(cmd, timeoutMs = timeoutMs)
        }
    }

    /**
     * Выполняет команды параллельно с ограничением concurrency.
     */
    suspend fun executeParallel(
        commands: List<List<String>>,
        maxConcurrency: Int = 4,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): List<SandboxResult> = coroutineScope {
        val semaphore = kotlinx.coroutines.sync.Semaphore(maxConcurrency)

        commands.map { cmd ->
            async {
                semaphore.withPermit {
                    execute(cmd, timeoutMs = timeoutMs)
                }
            }
        }.awaitAll()
    }

    /**
     * Очищает все активные процессы и освобождает ресурсы.
     */
    fun shutdown() {
        Log.i(TAG, "Shutting down ProcessContainer")

        isShuttingDown.set(true)

        // Убиваем все активные процессы
        activeProcesses.keys.toList().forEach { pid ->
            killProcess(pid, force = true)
        }

        activeProcesses.clear()
        coroutineScope.cancel()

        // Очищаем namespace
        namespaceContainer.cleanup()

        Log.i(TAG, "ProcessContainer shutdown complete")
    }
}
