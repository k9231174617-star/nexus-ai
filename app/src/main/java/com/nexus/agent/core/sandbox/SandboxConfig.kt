package com.nexus.agent.core.sandbox

data class SandboxConfig(
    val timeoutMs: Long = 10_000L,
    val maxOutputBytes: Int = 1024 * 64,
    val maxMemoryMb: Int = 128,
    val allowNetwork: Boolean = false,
    val allowFileSystem: Boolean = false,
    val allowProcessSpawn: Boolean = false,
    val workingDir: String? = null,
    val env: Map<String, String> = emptyMap(),
    val stdin: String = "",
)