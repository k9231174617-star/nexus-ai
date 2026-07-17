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
    private lateinit var semanticCache: SemanticCache

    @Mock
    private lateinit var cacheDao: CacheDao

    private lateinit var responseCache: ResponseCache

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        responseCache = ResponseCache(semanticCache, cacheDao)
    }

    @Test
    fun `get returns cached response if exists`() = runTest {
        val key = "test-key"
        val expected = "cached response"

        `when`(semanticCache.lookup(key)).thenReturn(CacheEntry(key = key, response = expected, timestamp = System.currentTimeMillis()))

        val result = responseCache.get(key)

        assertEquals(expected, result)
    }

    @Test
    fun `get returns null when no cache`() = runTest {
        `when`(semanticCache.lookup("missing")).thenReturn(null)

        val result = responseCache.get("missing")

        assertNull(result)
    }

    @Test
    fun `put stores in cache`() = runTest {
        responseCache.put("key", "value")

        verify(semanticCache).store(any())
    }
}
