package com.nexus.agent.core.cli

import android.util.Log

/**
 * CommandParser - парсер команд с поддержкой аргументов и флагов
 */
class CommandParser {

    companion object {
        private const val TAG = "CommandParser"
    }

    /**
     * Парсить пользовательский ввод
     */
    fun parse(input: String): ParsedCommand {
        val trimmed = input.trim()
        
        if (trimmed.isEmpty()) {
            return ParsedCommand(
                command = "",
                args = emptyList(),
                flags = emptyMap(),
                raw = input
            )
        }

        val parts = tokenize(trimmed)
        val command = parts.firstOrNull() ?: ""
        val rawArgs = parts.drop(1)

        // Разделяем аргументы и флаги
        val args = mutableListOf<String>()
        val flags = mutableMapOf<String, String>()

        for (part in rawArgs) {
            if (part.startsWith("--")) {
                // Флаг с значением --key=value
                val flagParts = part.substring(2).split("=", limit = 2)
                if (flagParts.size == 2) {
                    flags[flagParts[0]] = flagParts[1]
                } else {
                    flags[part.substring(2)] = "true"
                }
            } else if (part.startsWith("-")) {
                // Короткий флаг -v
                flags[part.substring(1)] = "true"
            } else {
                // Обычный аргумент
                args.add(part)
            }
        }

        return ParsedCommand(
            command = command,
            args = args,
            flags = flags,
            raw = input
        )
    }

    /**
     * Разбить строку на токены с учетом кавычек
     */
    private fun tokenize(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        var quoteChar: Char? = null

        for (char in input) {
            when {
                inQuote -> {
                    if (char == quoteChar) {
                        inQuote = false
                    } else {
                        current.append(char)
                    }
                }
                char == '"' || char == '\'' -> {
                    inQuote = true
                    quoteChar = char
                }
                char.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }
                else -> {
                    current.append(char)
                }
            }
        }

        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }

        return tokens
    }

    /**
     * Сгенерировать подсказку для команды
     */
    fun generateHelp(command: String): String? {
        val helpMap = mapOf(
            "help" to "help [command] - Показать справку по командам",
            "version" to "version - Показать версию приложения",
            "clear" to "clear - Очистить экран терминала",
            "exit" to "exit - Выйти из терминала",
            "echo" to "echo <text> - Вывести текст",
            "ls" to "ls [path] - Список файлов в директории",
            "pwd" to "pwd - Показать текущую директорию",
            "cd" to "cd <directory> - Изменить директорию",
            "whoami" to "whoami - Показать текущего пользователя",
            "date" to "date - Показать текущую дату и время",
            "uptime" to "uptime - Показать время работы системы"
        )

        return helpMap[command]
    }
}

/**
 * Результат парсинга команды
 */
data class ParsedCommand(
    val command: String,
    val args: List<String>,
    val flags: Map<String, String>,
    val raw: String
) {
    fun hasFlag(flag: String): Boolean {
        return flags.containsKey(flag)
    }

    fun getFlag(flag: String, default: String = ""): String {
        return flags[flag] ?: default
    }

    fun getFirstArg(default: String = ""): String {
        return args.firstOrNull() ?: default
    }
}
