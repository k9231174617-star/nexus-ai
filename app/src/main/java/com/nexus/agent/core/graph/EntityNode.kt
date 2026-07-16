package com.nexus.agent.core.graph

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entity_nodes")
data class EntityNode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val properties: String = "{}",
    val embedding: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)