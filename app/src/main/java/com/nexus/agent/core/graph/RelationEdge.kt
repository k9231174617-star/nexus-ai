package com.nexus.agent.core.graph

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "relation_edges")
data class RelationEdge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromId: Long,
    val toId: Long,
    val relation: String,
    val weight: Float = 1f,
    val properties: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
)