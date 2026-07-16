package com.nexus.agent.core.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResponseCache @Inject constructor(
    private val semanticCache: SemanticCache,
    private val cacheDao: CacheDao,
) {
    suspend fun get(prompt: String, agentType: String): String? = withContext(Dispatchers.IO) {
        // First: exact match
        val exact = cacheDao.getExact(prompt.hashCode().toString(), agentType)
        if (exact != null && !isExpired(exact)) return@withContext exact.response

        // Second: semantic similarity
        semanticCache.findSimilar(prompt, threshold = 0.92f, agentType = agentType)?.response
    }

    suspend fun put(
        prompt: String,
        response: String,
        agentType: String,
        ttlMs: Long = DEFAULT_TTL_MS,
    ) = withContext(Dispatchers.IO) {
        val entry = CacheEntry(
            promptHash = prompt.hashCode().toString(),
            prompt = prompt,
            response = response,
            agentType = agentType,
            expiresAt = System.currentTimeMillis() + ttlMs,
        )
        cacheDao.insert(entry)
        semanticCache.store(entry)
    }

    suspend fun invalidate(agentType: String) = withContext(Dispatchers.IO) {
        cacheDao.deleteByAgent(agentType)
        semanticCache.clear(agentType)
    }

    suspend fun cleanup() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cacheDao.deleteExpired(now)
    }

    private fun isExpired(entry: CacheEntry): Boolean =
        System.currentTimeMillis() > entry.expiresAt

    suspend fun stats(): Map<String, Any> = withContext(Dispatchers.IO) {
        mapOf(
            "totalEntries" to cacheDao.count(),
            "semanticEntries" to semanticCache.size(),
        )
    }

    companion object { const val DEFAULT_TTL_MS = 3_600_000L } // 1 hour
}