package com.nexus.agent.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "graph_entities")
data class EntityNodeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // person, organization, concept, code, file, etc.
    val description: String? = null,
    val properties: String = "", // JSON serialized additional properties
    val embedding: ByteArray? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sourceDocumentId: String? = null,
    val confidence: Float = 1.0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EntityNodeEntity
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}

@Entity(tableName = "graph_relations")
data class RelationEdgeEntity(
    @PrimaryKey val id: String,
    val sourceEntityId: String,
    val targetEntityId: String,
    val relationType: String, // works_at, contains, references, implements, etc.
    val properties: String = "", // JSON serialized
    val weight: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val bidirectional: Boolean = false,
    val sourceDocumentId: String? = null
)

@Entity(tableName = "graph_queries")
data class GraphQueryEntity(
    @PrimaryKey val id: String,
    val queryText: String,
    val queryType: String = "cypher", // cypher, natural, path
    val results: String = "", // JSON serialized
    val executedAt: Long = System.currentTimeMillis(),
    val executionTimeMs: Long = 0
)

@Dao
interface GraphDao {
    // Entity operations
    @Query("SELECT * FROM graph_entities ORDER BY updatedAt DESC")
    fun getAllEntities(): Flow<List<EntityNodeEntity>>

    @Query("SELECT * FROM graph_entities WHERE id = :entityId")
    suspend fun getEntityById(entityId: String): EntityNodeEntity?

    @Query("SELECT * FROM graph_entities WHERE name = :name LIMIT 1")
    suspend fun getEntityByName(name: String): EntityNodeEntity?

    @Query("SELECT * FROM graph_entities WHERE type = :type ORDER BY updatedAt DESC")
    fun getEntitiesByType(type: String): Flow<List<EntityNodeEntity>>

    @Query("SELECT * FROM graph_entities WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchEntities(query: String): Flow<List<EntityNodeEntity>>

    @Query("SELECT * FROM graph_entities WHERE sourceDocumentId = :documentId")
    suspend fun getEntitiesByDocument(documentId: String): List<EntityNodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntity(entity: EntityNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntities(entities: List<EntityNodeEntity>)

    @Update
    suspend fun updateEntity(entity: EntityNodeEntity)

    @Query("UPDATE graph_entities SET properties = :properties, updatedAt = :timestamp WHERE id = :entityId")
    suspend fun updateEntityProperties(entityId: String, properties: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE graph_entities SET embedding = :embedding WHERE id = :entityId")
    suspend fun updateEntityEmbedding(entityId: String, embedding: ByteArray)

    @Delete
    suspend fun deleteEntity(entity: EntityNodeEntity)

    @Query("DELETE FROM graph_entities WHERE id = :entityId")
    suspend fun deleteEntityById(entityId: String)

    @Query("DELETE FROM graph_entities WHERE sourceDocumentId = :documentId")
    suspend fun deleteEntitiesByDocument(documentId: String)

    @Query("SELECT COUNT(*) FROM graph_entities")
    fun getEntityCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM graph_entities WHERE type = :type")
    suspend fun getEntityCountByType(type: String): Int

    // Relation operations
    @Query("SELECT * FROM graph_relations WHERE sourceEntityId = :entityId OR targetEntityId = :entityId")
    suspend fun getRelationsByEntity(entityId: String): List<RelationEdgeEntity>

    @Query("SELECT * FROM graph_relations WHERE sourceEntityId = :sourceId AND targetEntityId = :targetId")
    suspend fun getRelation(sourceId: String, targetId: String): RelationEdgeEntity?

    @Query("SELECT * FROM graph_relations WHERE relationType = :type")
    suspend fun getRelationsByType(type: String): List<RelationEdgeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelation(relation: RelationEdgeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelations(relations: List<RelationEdgeEntity>)

    @Update
    suspend fun updateRelation(relation: RelationEdgeEntity)

    @Delete
    suspend fun deleteRelation(relation: RelationEdgeEntity)

    @Query("DELETE FROM graph_relations WHERE sourceEntityId = :entityId OR targetEntityId = :entityId")
    suspend fun deleteRelationsByEntity(entityId: String)

    @Query("DELETE FROM graph_relations WHERE sourceDocumentId = :documentId")
    suspend fun deleteRelationsByDocument(documentId: String)

    @Query("SELECT COUNT(*) FROM graph_relations")
    fun getRelationCount(): Flow<Int>

    // Query history
    @Query("SELECT * FROM graph_queries ORDER BY executedAt DESC LIMIT :limit")
    suspend fun getRecentQueries(limit: Int = 50): List<GraphQueryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuery(query: GraphQueryEntity)

    @Query("DELETE FROM graph_queries WHERE executedAt < :olderThan")
    suspend fun deleteOldQueries(olderThan: Long)
}
