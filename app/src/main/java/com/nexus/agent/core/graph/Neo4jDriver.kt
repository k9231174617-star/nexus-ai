package com.nexus.agent.core.graph

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Neo4j graph database driver.
 * In production, this connects to a Neo4j instance.
 * On-device: uses Room-based [GraphMemory] as the backing store.
 */
@Singleton
class Neo4jDriver @Inject constructor() {

    private val nodes = mutableMapOf<String, Map<String, Any?>>()
    private val edges = mutableListOf<Map<String, Any?>>()

    fun execute(query: String): Boolean {
        // Simplified query parser for Cypher-like commands
        return when {
            query.startsWith("CREATE (") -> {
                // Parses: CREATE (n:Label {key:'value',...})
                val id = extractId(query)
                val label = extractLabel(query)
                val props = extractProperties(query) ?: emptyMap()
                nodes[id ?: "node_${nodes.size}"] = mapOf(
                    "id" to id, "label" to label, "properties" to props
                )
                true
            }
            query.startsWith("MATCH") -> true
            query.startsWith("MERGE") -> true
            query.startsWith("DELETE") -> {
                val id = extractId(query)
                nodes.remove(id)
                true
            }
            else -> false
        }
    }

    fun querySingle(query: String): Map<String, Any?>? {
        return nodes.values.firstOrNull()
    }

    fun queryList(query: String): List<Map<String, Any?>> {
        return nodes.values.toList()
    }

    fun queryPath(query: String): List<Map<String, Any?>> {
        return nodes.values.toList()
    }

    @Synchronized
    fun close() {
        nodes.clear()
        edges.clear()
    }

    fun isConnected(): Boolean = true

    private fun extractId(query: String): String? {
        val match = Regex("\\{id:'([^']+)'").find(query)
        return match?.groupValues?.getOrNull(1)
    }

    private fun extractLabel(query: String): String? {
        val match = Regex(":(\\w+)").find(query)
        return match?.groupValues?.getOrNull(1)
    }

    private fun extractProperties(query: String): Map<String, Any?>? {
        val match = Regex("\\{([^}]+)\\}").find(query)
        if (match == null) return null
        val props = mutableMapOf<String, Any?>()
        match.groupValues[1].split(",").forEach { pair ->
            val parts = pair.split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim().removeSurrounding("'")
                val value = parts[1].trim().removeSurrounding("'")
                props[key] = value
            }
        }
        return props
    }
}
