package com.nexus.agent.core.cli

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CommandHistory(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val maxHistorySize = 500

    data class HistoryEntry(
        val command: String,
        val timestamp: Long,
        val exitCode: Int,
        val workingDir: String
    )

    fun add(command: String, exitCode: Int = 0, workingDir: String = "/") {
        val history = getAll().toMutableList()
        history.add(0, HistoryEntry(command, System.currentTimeMillis(), exitCode, workingDir))
        
        if (history.size > maxHistorySize) {
            history.removeAt(history.size - 1)
        }
        
        save(history)
    }

    fun getAll(): List<HistoryEntry> {
        val json = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val type = object : TypeToken<List<HistoryEntry>>() {}.type
        return gson.fromJson(json, type)
    }

    fun search(query: String): List<HistoryEntry> {
        return getAll().filter { it.command.contains(query, ignoreCase = true) }
    }

    fun getRecent(limit: Int = 50): List<HistoryEntry> {
        return getAll().take(limit)
    }

    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun save(history: List<HistoryEntry>) {
        val json = gson.toJson(history)
        prefs.edit().putString(KEY_HISTORY, json).apply()
    }

    companion object {
        private const val PREFS_NAME = "cli_history"
        private const val KEY_HISTORY = "commands"
    }
}
