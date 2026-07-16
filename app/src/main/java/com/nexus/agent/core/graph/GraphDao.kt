package com.nexus.agent.core.graph

import androidx.room.*

@Dao
interface GraphDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntity(node: EntityNode): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdge(edge: RelationEdge): Long

    @Query("SELECT * FROM entity_nodes WHERE id = :id")
    fun getEntity(id: Long): EntityNode?

    @Query("SELECT * FROM relation_edges WHERE fromId = :id")
    fun getEdgesFrom(id: Long): List<RelationEdge>

    @Query("SELECT * FROM relation_edges WHERE toId = :id")
    fun getEdgesTo(id: Long): List<RelationEdge>

    @Query("SELECT * FROM entity_nodes WHERE name LIKE :pattern OR type LIKE :pattern")
    suspend fun searchEntities(pattern: String): List<EntityNode>

    @Query("DELETE FROM entity_nodes WHERE id = :id")
    suspend fun deleteEntity(id: Long)

    @Query("DELETE FROM relation_edges WHERE fromId = :id OR toId = :id")
    suspend fun deleteEdgesForEntity(id: Long)

    @Query("SELECT COUNT(*) FROM entity_nodes")
    fun countEntities(): Int

    @Query("SELECT COUNT(*) FROM relation_edges")
    fun countEdges(): Int
}