package com.nexus.agent.core.cache

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class ResponseCacheTest {

    @Mock
    private lateinit var cacheDao: CacheDao

    @Mock
    private lateinit var semanticCache: SemanticCache

    private lateinit var responseCache: ResponseCache

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        responseCache = ResponseCache(cacheDao, semanticCache)
    }

    @Test
    fun `get returns cached response for exact match`() = runTest {
        val query = "What is Kotlin?"
        val cachedResponse = "Kotlin is a programming language"
        val entry = CacheEntry(query, cachedResponse, System.currentTimeMillis(), 0.95f)
        
        `when`(cacheDao.get(query)).thenReturn(entry)
        
        val result = responseCache.get(query)
        
        assertEquals(cachedResponse, result)
    }

    @Test
    fun `get returns null for cache miss`() = runTest {
        val query = "Unknown query"
        
        `when`(cacheDao.get(query)).thenReturn(null)
        `when`(semanticCache.findSimilar(query)).thenReturn(null)
        
        val result = responseCache.get(query)
        
        assertNull(result)
    }

    @Test
    fun `get uses semantic cache for similar queries`() = runTest {
        val query = "Tell me about Kotlin"
        val similarQuery = "What is Kotlin?"
        val cachedResponse = "Kotlin is a programming language"
        
        `when`(cacheDao.get(query)).thenReturn(null)
        `when`(semanticCache.findSimilar(query)).thenReturn(similarQuery to 0.92f)
        `when`(cacheDao.get(similarQuery)).thenReturn(
            CacheEntry(similarQuery, cachedResponse, System.currentTimeMillis(), 0.95f)
        )
        
        val result = responseCache.get(query)
        
        assertEquals(cachedResponse, result)
    }

    @Test
    fun `put stores response in cache`() = runTest {
        val query = "What is Java?"
        val response = "Java is a programming language"
        
        responseCache.put(query, response)
        
        verify(cacheDao).insert(argThat { 
            it.query == query && it.response == response 
        })
        verify(semanticCache).index(query)
    }

    @Test
    fun `put rejects low confidence responses`() = runTest {
        val query = "test"
        val response = "uncertain answer"
        val confidence = 0.3f
        
        responseCache.put(query, response, confidence)
        
        verify(cacheDao, never()).insert(any())
    }

    @Test
    fun `invalidate removes specific entry`() = runTest {
        val query = "test query"
        
        responseCache.invalidate(query)
        
        verify(cacheDao).delete(query)
        verify(semanticCache).remove(query)
    }

    @Test
    fun `invalidatePattern removes matching entries`() = runTest {
        val pattern = "Kotlin.*"
        val matchingQueries = listOf("Kotlin basics", "Kotlin coroutines")
        
        `when`(cacheDao.findByPattern(pattern)).thenReturn(matchingQueries)
        
        responseCache.invalidatePattern(pattern)
        
        verify(cacheDao).delete("Kotlin basics")
        verify(cacheDao).delete("Kotlin coroutines")
    }

    @Test
    fun `clear removes all entries`() = runTest {
        responseCache.clear()
        
        verify(cacheDao).deleteAll()
        verify(semanticCache).clear()
    }

    @Test
    fun `getStats returns cache metrics`() = runTest {
        `when`(cacheDao.count()).thenReturn(100)
        `when`(cacheDao.hitRate()).thenReturn(0.75)
        `when`(semanticCache.size()).thenReturn(100)
        
        val stats = responseCache.getStats()
        
        assertEquals(100, stats.entries)
        assertEquals(0.75, stats.hitRate, 0.01)
    }

    @Test
    fun `evictExpired removes stale entries`() = runTest {
        val maxAgeMs = 3600000L // 1 hour
        
        responseCache.evictExpired(maxAgeMs)
        
        verify(cacheDao).deleteOlderThan(anyLong())
    }

    @Test
    fun `getWithFallback returns fallback on miss`() = runTest {
        val query = "test"
        val fallback = "default answer"
        
        `when`(cacheDao.get(query)).thenReturn(null)
        `when`(semanticCache.findSimilar(query)).thenReturn(null)
        
        val result = responseCache.getWithFallback(query) { fallback }
        
        assertEquals(fallback, result)
    }
}
