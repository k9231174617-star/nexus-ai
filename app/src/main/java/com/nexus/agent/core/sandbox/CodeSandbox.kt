package com.nexus.agent.core.sandbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CodeSandbox @Inject constructor(
    private val resourceLimiter: ResourceLimiter,
    private val languageRunner: LanguageRunner,
) {
    suspend fun execute(
        code: String,
        language: String,
        config: SandboxConfig = SandboxConfig(),
    ): SandboxResult = withContext(Dispatchers.IO) {
        if (!resourceLimiter.hasCapacity()) {
            return@withContext SandboxResult.error("System resources exhausted")
        }

        val result = withTimeoutOrNull(config.timeoutMs) {
            languageRunner.run(code, language, config)
        } ?: SandboxResult.error("Execution timed out after ${config.timeoutMs}ms")

        resourceLimiter.release()
        result
    }

    suspend fun validate(code: String, language: String): List<String> =
        withContext(Dispatchers.IO) {
            val warnings = mutableListOf<String>()
            val dangerousPatterns = when (language.lowercase()) {
                "python" -> listOf(
                    "import os" to "OS access",
                    "import subprocess" to "Subprocess execution",
                    "__import__" to "Dynamic import",
                    "exec(" to "Code execution",
                    "eval(" to "Code evaluation",
                    "open(" to "File access",
                )
                "javascript" -> listOf(
                    "require(" to "Module import",
                    "process.env" to "Environment access",
                    "fs." to "File system access",
                    "child_process" to "Process spawning",
                )
                else -> emptyList()
            }
            dangerousPatterns.forEach { (pattern, desc) ->
                if (code.contains(pattern)) warnings.add("⚠️ $desc detected: '$pattern'")
            }
            warnings
        }
}