package com.nexus.agent.core.sandbox

data class SandboxResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long,
    val memoryUsedMb: Float,
    val timedOut: Boolean = false,
    val success: Boolean = exitCode == 0,
) {
    companion object {
        fun error(message: String) = SandboxResult(
            stdout = "", stderr = message, exitCode = -1,
            durationMs = 0, memoryUsedMb = 0f, success = false,
        )
    }
}