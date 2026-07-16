package com.nexus.agent.core.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentMemory @Inject constructor(
    private val memoryDao: MemoryDao,
    private val vectorStore: VectorStore,
    private val localEmbedder: LocalEmbedder,
    private val importanceScorer: ImportanceScorer,
    private val memoryPruner: MemoryPruner,
) {
    suspend fun store(
        content: String,
        type: String = "interaction",
        agentId: String = "main",
        metadata: Map<String, String> = emptyMap(),
    ): MemoryEntry = withContext(Dispatchers.IO) {
        val importance = importanceScorer.score(content)
        val embedding = localEmbedder.embed(content)
        val entry = MemoryEntry(
            content    = content,
            type       = type,
            agentId    = agentId,
            importance = importance,
            metadata   = metadata.toString(),
        )
        val saved = memoryDao.insert(entry)
        vectorStore.store(saved, embedding)
        memoryPruner.pruneIfNeeded()
        entry.copy(id = saved)
    }

    suspend fun recall(
        query: String,
        limit: Int = 5,
        agentId: String? = null,
    ): List<MemoryEntry> = withContext(Dispatchers.IO) {
        val queryEmbedding = localEmbedder.embed(query)
        val vectorResults = vectorStore.search(queryEmbedding, limit * 2)
        val ids = vectorResults.map { it.id }
        val entries = memoryDao.getByIds(ids)
        val filtered = if (agentId != null) entries.filter { it.agentId == agentId } else entries
        filtered.sortedByDescending { entry ->
            val vectorScore = vectorResults.find { it.id == entry.id }?.score ?: 0f
            vectorScore * 0.7f + (entry.importance / 10f) * 0.3f
        }.take(limit)
    }

    suspend fun recallRecent(limit: Int = 10, agentId: String? = null): List<MemoryEntry> =
        withContext(Dispatchers.IO) {
            if (agentId != null) memoryDao.getRecentByAgent(agentId, limit)
            else memoryDao.getRecent(limit)
        }

    suspend fun forget(entryId: Long) = withContext(Dispatchers.IO) {
        memoryDao.delete(entryId)
        vectorStore.remove(entryId)
    }

    suspend fun forgetAll(agentId: String) = withContext(Dispatchers.IO) {
        val entries = memoryDao.getByAgent(agentId)
        entries.forEach { vectorStore.remove(it.id) }
        memoryDao.deleteByAgent(agentId)
    }

    suspend fun getStats(): Map<String, Any> = withContext(Dispatchers.IO) {
        mapOf(
            "total" to memoryDao.count(),
            "vectorSize" to vectorStore.size(),
            "byAgent" to memoryDao.countByAgent(),
        )
    }
}