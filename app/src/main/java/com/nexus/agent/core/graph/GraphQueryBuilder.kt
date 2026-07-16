package com.nexus.agent.core.graph

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GraphQueryBuilder @Inject constructor() {

    fun buildFindPath(fromId: Long, toId: Long, maxDepth: Int = 5): String =
        "MATCH p=shortestPath((a)-[*..${maxDepth}]-(b)) WHERE id(a)=$fromId AND id(b)=$toId RETURN p"

    fun buildNeighborQuery(entityId: Long, relation: String? = null): String =
        if (relation != null)
            "MATCH (n)-[:$relation]->(m) WHERE id(n)=$entityId RETURN m"
        else
            "MATCH (n)-[]->(m) WHERE id(n)=$entityId RETURN m"

    fun buildSearchQuery(namePattern: String, type: String? = null): String =
        if (type != null)
            "MATCH (n:$type) WHERE n.name CONTAINS '$namePattern' RETURN n"
        else
            "MATCH (n) WHERE n.name CONTAINS '$namePattern' RETURN n"

    fun buildSubgraphQuery(entityIds: List<Long>): String {
        val ids = entityIds.joinToString(",")
        return "MATCH (n)-[r]->(m) WHERE id(n) IN [$ids] OR id(m) IN [$ids] RETURN n, r, m"
    }
}