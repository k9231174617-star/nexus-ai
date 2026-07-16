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
    // Whitelist of allowed shell commands
    private val allowedCommands = setOf(
        "echo", "cat", "ls", "pwd", "whoami", "date", "cal",
        "uname", "uptime", "df", "du", "free", "ps", "head",
        "tail", "wc", "sort", "uniq", "cut", "tr", "grep",
        "find", "which", "env", "printenv", "id", "hostname",
        "basename", "dirname", "realpath", "readlink",
    )

    private val blockedPatterns = listOf(
        Regex("rm\\s+-rf"), Regex("mkfs"), Regex("dd\\s+if="),
        Regex(">\\s*/dev/"), Regex(":(){"), Regex("chmod\\s+777"),
        Regex("wget"), Regex("curl"), Regex("nc\\s"),
        Regex("shutdown"), Regex("reboot"), Regex("init\\s+0"),
    )

    suspend fun run(code: String, language: String, config: SandboxConfig): SandboxResult =
        withContext(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            // Validate code before execution
            val warnings = CodeSandbox.validate(code, language)
            if (warnings.isNotEmpty()) {
                return@withContext SandboxResult.error(warnings.joinToString("; "))
            }
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

    private fun validateShellCommand(code: String): String? {
        val firstToken = code.trim().split("\\s+".toRegex()).firstOrNull() ?: return "Empty command"
        if (firstToken !in allowedCommands) {
            return "Command '$firstToken' is not in the allowed whitelist"
        }
        for (pattern in blockedPatterns) {
            if (pattern.containsMatchIn(code)) {
                return "Code contains blocked pattern: ${pattern.pattern}"
            }
        }
        return null
    }

    private fun runPython(code: String, config: SandboxConfig, t0: Long): SandboxResult {
        // Validate Python code (basic safety)
        val dangerousPatterns = listOf(
            Regex("__import__\\('os'\\)"), Regex("__import__\\('subprocess'\\)"),
            Regex("os\\.system"), Regex("subprocess\\."), Regex("eval\\("),
            Regex("exec\\("), Regex("pickle\\.loads"), Regex("compile\\("),
        )
        for (pattern in dangerousPatterns) {
            if (pattern.containsMatchIn(code)) {
                return SandboxResult.error("Blocked dangerous Python pattern: ${pattern.pattern}")
            }
        }

        val tmpFile = File(context.cacheDir, "nexus_script_${System.currentTimeMillis()}.py")
        tmpFile.writeText(code)
        return try {
            val pb = ProcessBuilder("python3", tmpFile.absolutePath).apply {
                redirectErrorStream(false)
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
        // Validate against whitelist
        val validationError = validateShellCommand(code)
        if (validationError != null) {
            return SandboxResult.error("Shell validation failed: $validationError")
        }

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
        return SandboxResult(
            stdout = "// JS execution not available on device\n// Code analyzed statically",
            stderr = "", exitCode = 0,
            durationMs = System.currentTimeMillis() - t0,
            memoryUsedMb = 0f,
        )
    }
}
