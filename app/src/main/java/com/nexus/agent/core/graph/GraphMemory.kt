package com.nexus.agent.core.graph

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GraphMemory @Inject constructor(
    private val graphDao: GraphDao,
    private val queryBuilder: GraphQueryBuilder,
) {
    suspend fun addEntity(name: String, type: String, properties: Map<String, String> = emptyMap()): EntityNode =
        withContext(Dispatchers.IO) {
            val node = EntityNode(
                name = name, type = type,
                properties = properties.toString()
            )
            val id = graphDao.insertEntity(node)
            node.copy(id = id)
        }

    suspend fun addRelation(
        fromId: Long, toId: Long,
        relation: String,
        weight: Float = 1f,
    ): RelationEdge = withContext(Dispatchers.IO) {
        val edge = RelationEdge(fromId = fromId, toId = toId, relation = relation, weight = weight)
        val id = graphDao.insertEdge(edge)
        edge.copy(id = id)
    }

    suspend fun findRelated(entityId: Long, depth: Int = 2): List<EntityNode> =
        withContext(Dispatchers.IO) {
            val visited = mutableSetOf<Long>()
            val result = mutableListOf<EntityNode>()
            fun traverse(id: Long, d: Int) {
                if (d == 0 || id in visited) return
                visited.add(id)
                val edges = graphDao.getEdgesFrom(id)
                edges.forEach { edge ->
                    graphDao.getEntity(edge.toId)?.let {
                        result.add(it)
                        traverse(edge.toId, d - 1)
                    }
                }
            }
            traverse(entityId, depth)
            result.distinctBy { it.id }
        }

    suspend fun searchEntities(query: String): List<EntityNode> = withContext(Dispatchers.IO) {
        graphDao.searchEntities("%$query%")
    }

    suspend fun getNeighbors(entityId: Long): List<Pair<RelationEdge, EntityNode>> =
        withContext(Dispatchers.IO) {
            graphDao.getEdgesFrom(entityId).mapNotNull { edge ->
                graphDao.getEntity(edge.toId)?.let { node -> Pair(edge, node) }
            }
        }

    suspend fun deleteEntity(entityId: Long) = withContext(Dispatchers.IO) {
        graphDao.deleteEdgesForEntity(entityId)
        graphDao.deleteEntity(entityId)
    }

    suspend fun getStats(): Map<String, Int> = withContext(Dispatchers.IO) {
        mapOf(
            "entities" to graphDao.countEntities(),
            "edges" to graphDao.countEdges(),
        )
    }
}