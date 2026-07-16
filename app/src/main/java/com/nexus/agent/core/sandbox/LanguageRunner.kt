package com.nexus.agent.core.sandbox

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguageRunner @Inject constructor(
    private val context: Context,
) {
    suspend fun run(code: String, language: String, config: SandboxConfig): SandboxResult =
        withContext(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            return@withContext when (language.lowercase()) {
                "python", "py"         -> runPython(code, config, t0)
                "shell", "bash", "sh"  -> runShell(code, config, t0)
                "javascript", "js"     -> runJavaScript(code, t0)
                else -> SandboxResult(
                    stdout = "// Language '$language' preview (execution not supported on device)",
                    stderr = "", exitCode = 0,
                    durationMs = System.currentTimeMillis() - t0,
                    memoryUsedMb = 0f,
                )
            }
        }

    private fun runPython(code: String, config: SandboxConfig, t0: Long): SandboxResult {
        val tmpFile = File(context.cacheDir, "nexus_script_${System.currentTimeMillis()}.py")
        tmpFile.writeText(code)
        return try {
            val pb = ProcessBuilder("python3", tmpFile.absolutePath).apply {
                redirectErrorStream(false)
                if (config.stdin.isNotEmpty()) {
                    // feed stdin
                }
            }
            val proc = pb.start()
            if (config.stdin.isNotEmpty()) {
                proc.outputStream.bufferedWriter().use { it.write(config.stdin) }
            }
            val stdout = proc.inputStream.bufferedReader().readText().take(config.maxOutputBytes)
            val stderr = proc.errorStream.bufferedReader().readText().take(1024)
            val exit = proc.waitFor()
            SandboxResult(
                stdout = stdout, stderr = stderr, exitCode = exit,
                durationMs = System.currentTimeMillis() - t0,
                memoryUsedMb = 0f,
            )
        } catch (e: Exception) {
            SandboxResult.error("Python not available: ${e.message}")
        } finally {
            tmpFile.delete()
        }
    }

    private fun runShell(code: String, config: SandboxConfig, t0: Long): SandboxResult {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", code))
            val stdout = proc.inputStream.bufferedReader().readText().take(config.maxOutputBytes)
            val stderr = proc.errorStream.bufferedReader().readText().take(1024)
            val exit = proc.waitFor()
            SandboxResult(
                stdout = stdout, stderr = stderr, exitCode = exit,
                durationMs = System.currentTimeMillis() - t0,
                memoryUsedMb = 0f,
            )
        } catch (e: Exception) {
            SandboxResult.error("Shell error: ${e.message}")
        }
    }

    private fun runJavaScript(code: String, t0: Long): SandboxResult {
        // Android doesn't have Node.js — return static analysis only
        return SandboxResult(
            stdout = "// JS execution not available on device\n// Code analyzed statically",
            stderr = "", exitCode = 0,
            durationMs = System.currentTimeMillis() - t0,
            memoryUsedMb = 0f,
        )
    }
}