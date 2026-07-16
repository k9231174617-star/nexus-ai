package com.nexus.agent.core.cli

import java.io.File

class AutocompleteEngine {

    private val commonCommands = listOf(
        "ls", "cd", "pwd", "cat", "echo", "mkdir", "rm", "cp", "mv",
        "chmod", "chown", "ps", "top", "kill", "grep", "find", "tar",
        "zip", "unzip", "wget", "curl", "ping", "ifconfig", "netstat",
        "su", "busybox", "am", "pm", "dumpsys", "getprop", "setprop",
        "iptables", "logcat", "dmesg", "mount", "umount", "reboot",
        "install", "uninstall", "push", "pull", "shell", "root"
    )

    private val fileExtensions = listOf(
        ".kt", ".java", ".py", ".c", ".cpp", ".h", ".sh", ".xml",
        ".json", ".md", ".txt", ".gradle", ".properties", ".smali",
        ".apk", ".dex", ".so", ".jar", ".zip", ".tar", ".gz"
    )

    fun suggest(input: String, currentDir: String = "/"): List<String> {
        if (input.isEmpty()) return emptyList()

        val tokens = input.split(" ")
        val lastToken = tokens.last()

        return when {
            tokens.size == 1 -> suggestCommands(lastToken)
            lastToken.startsWith("-") -> suggestFlags(tokens[0], lastToken)
            lastToken.startsWith("/") || lastToken.startsWith(".") -> {
                suggestPaths(lastToken, currentDir)
            }
            else -> suggestFiles(lastToken, currentDir)
        }
    }

    private fun suggestCommands(prefix: String): List<String> {
        return commonCommands.filter { it.startsWith(prefix) }
    }

    private fun suggestFlags(command: String, prefix: String): List<String> {
        val flags = when (command) {
            "ls" -> listOf("-l", "-a", "-la", "-lh", "-R", "-t", "-S")
            "rm" -> listOf("-r", "-f", "-rf", "-i", "-v")
            "cp" -> listOf("-r", "-f", "-i", "-v", "-p")
            "tar" -> listOf("-cvf", "-xvf", "-czvf", "-xzvf", "-tf")
            "grep" -> listOf("-i", "-r", "-n", "-v", "-l", "-c")
            else -> emptyList()
        }
        return flags.filter { it.startsWith(prefix) }
    }

    private fun suggestPaths(prefix: String, currentDir: String): List<String> {
        val basePath = if (prefix.startsWith("/")) prefix else "$currentDir/$prefix"
        val dir = File(basePath.substringBeforeLast("/"))
        
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        return dir.listFiles()?.map { it.absolutePath }?.filter {
            it.startsWith(basePath)
        } ?: emptyList()
    }

    private fun suggestFiles(prefix: String, currentDir: String): List<String> {
        val dir = File(currentDir)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        return dir.listFiles()?.map { it.name }?.filter {
            it.startsWith(prefix)
        } ?: emptyList()
    }
}
