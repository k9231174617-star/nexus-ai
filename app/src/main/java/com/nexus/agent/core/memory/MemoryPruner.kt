package com.nexus.agent.core.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryPruner @Inject constructor(
    private val memoryDao: MemoryDao,
    private val vectorStore: VectorStore,
) {
    companion object {
        const val MAX_ENTRIES = 5000
        const val PRUNE_TO = 4000
        const val MIN_IMPORTANCE = 0.2f
    }

    suspend fun pruneIfNeeded() = withContext(Dispatchers.IO) {
        val count = memoryDao.count()
        if (count > MAX_ENTRIES) pruneOldLowImportance()
    }

    private suspend fun pruneOldLowImportance() {
        val toDelete = memoryDao.getLowImportanceOld(
            minImportance = MIN_IMPORTANCE,
            limit = (memoryDao.count() - PRUNE_TO).coerceAtLeast(0).toInt()
        )
        toDelete.forEach { entry ->
            memoryDao.delete(entry.id)
            vectorStore.remove(entry.id)
        }
    }

    suspend fun pruneByAge(maxAgeDays: Int = 30) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (maxAgeDays * 86_400_000L)
        val old = memoryDao.getOlderThan(cutoff)
        old.filter { it.importance < 0.7f }.forEach { entry ->
            memoryDao.delete(entry.id)
            vectorStore.remove(entry.id)
        }
    }

    suspend fun forceCompact() = withContext(Dispatchers.IO) {
        val all = memoryDao.getAll()
        val keep = all.sortedByDescending { it.importance * 0.6f + (it.accessCount / 100f) * 0.4f }
            .take(PRUNE_TO)
        val keepIds = keep.map { it.id }.toSet()
        all.filter { it.id !in keepIds }.forEach { entry ->
            memoryDao.delete(entry.id)
            vectorStore.remove(entry.id)
        }
    }
}