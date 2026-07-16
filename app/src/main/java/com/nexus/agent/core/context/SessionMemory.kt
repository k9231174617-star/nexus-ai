package com.nexus.agent.core.context

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionMemory @Inject constructor() {
    private val store = mutableMapOf<String, String>()
    private val timestamps = mutableMapOf<String, Long>()

    fun store(key: String, value: String) {
        store[key] = value
        timestamps[key] = System.currentTimeMillis()
    }

    fun get(key: String): String? = store[key]

    fun getAll(): Map<String, String> = store.toMap()

    fun remove(key: String) {
        store.remove(key)
        timestamps.remove(key)
    }

    fun clear() {
        store.clear()
        timestamps.clear()
    }

    fun getRecent(limit: Int): Map<String, String> {
        return timestamps.entries
            .sortedByDescending { it.value }
            .take(limit)
            .associate { it.key to (store[it.key] ?: "") }
    }

    fun size() = store.size
}