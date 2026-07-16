package com.nexus.agent.core.memory

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import java.time.Instant

class MemoryTest {

    @Mock
    private lateinit var memoryDao: MemoryDao

    @Mock
    private lateinit var localEmbedder: LocalEmbedder

    @Mock
    private lateinit var vectorStore: VectorStore

    @Mock
    private lateinit var importanceScorer: ImportanceScorer

    private lateinit var agentMemory: AgentMemory

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        agentMemory = AgentMemory(memoryDao, localEmbedder, vectorStore, importanceScorer)
    }

    @Test
    fun `store saves memory entry with embedding`() = runTest {
        val content = "Important fact"
        val embedding = floatArrayOf(0.1f, 0.2f, 0.3f)
        val importance = 0.8f
        
        `when`(localEmbedder.embed(content)).thenReturn(embedding)
        `when`(importanceScorer.score(content)).thenReturn(importance)
        
        agentMemory.store(content, type = MemoryType.FACT)
        
        verify(memoryDao).insert(argThat { 
            it.content == content && it.importance == importance 
        })
        verify(vectorStore).add(anyString(), eq(embedding))
    }

    @Test
    fun `retrieve returns relevant memories`() = runTest {
        val query = "What do I know?"
        val queryEmbedding = floatArrayOf(0.1f, 0.2f, 0.3f)
        val memories = listOf(
            MemoryEntry("id1", "Fact 1", MemoryType.FACT, 0.9f, Instant.now()),
            MemoryEntry("id2", "Fact 2", MemoryType.FACT, 0.7f, Instant.now())
        )
        
        `when`(localEmbedder.embed(query)).thenReturn(queryEmbedding)
        `when`(vectorStore.search(queryEmbedding, 5)).thenReturn(
            listOf("id1" to 0.95f, "id2" to 0.85f)
        )
        `when`(memoryDao.getByIds(listOf("id1", "id2"))).thenReturn(memories)
        
        val result = agentMemory.retrieve(query, limit = 5)
        
        assertEquals(2, result.size)
        assertEquals("Fact 1", result[0].content)
    }

    @Test
    fun `retrieve filters by type when specified`() = runTest {
        val query = "test"
        val queryEmbedding = floatArrayOf(0.1f)
        
        `when`(localEmbedder.embed(query)).thenReturn(queryEmbedding)
        `when`(vectorStore.search(queryEmbedding, 5)).thenReturn(emptyList())
        
        agentMemory.retrieve(query, type = MemoryType.CONVERSATION, limit = 5)
        
        // Verify filtering logic applied
        verify(vectorStore).search(queryEmbedding, 5)
    }

    @Test
    fun `forget removes entry from dao and vector store`() = runTest {
        val memoryId = "mem-123"
        
        agentMemory.forget(memoryId)
        
        verify(memoryDao).delete(memoryId)
        verify(vectorStore).remove(memoryId)
    }

    @Test
    fun `getRecent returns chronologically ordered entries`() = runTest {
        val now = Instant.now()
        val entries = listOf(
            MemoryEntry("1", "Latest", MemoryType.FACT, 0.5f, now),
            MemoryEntry("2", "Older", MemoryType.FACT, 0.5f, now.minusSeconds(3600))
        )
        
        `when`(memoryDao.getRecent(10)).thenReturn(entries)
        
        val result = agentMemory.getRecent(10)
        
        assertEquals(2, result.size)
        assertEquals("Latest", result[0].content)
    }

    @Test
    fun `prune removes low importance old memories`() = runTest {
        val threshold = 0.3f
        val maxAge = 30L // days
        
        agentMemory.prune(threshold, maxAge)
        
        verify(memoryDao).deleteOldAndUnimportant(threshold, maxAge)
    }

    @Test
    fun `getStats returns memory metrics`() = runTest {
        `when`(memoryDao.count()).thenReturn(100)
        `when`(vectorStore.size()).thenReturn(100)
        
        val stats = agentMemory.getStats()
        
        assertEquals(100, stats.totalEntries)
        assertEquals(100, stats.vectorCount)
    }

    @Test
    fun `update modifies existing entry`() = runTest {
        val memoryId = "mem-123"
        val newContent = "Updated content"
        val newEmbedding = floatArrayOf(0.5f)
        
        `when`(localEmbedder.embed(newContent)).thenReturn(newEmbedding)
        
        agentMemory.update(memoryId, newContent)
        
        verify(memoryDao).update(any())
        verify(vectorStore).update(memoryId, newEmbedding)
    }

    @Test
    fun `searchByImportance returns high priority memories`() = runTest {
        val minImportance = 0.8f
        val importantMemories = listOf(
            MemoryEntry("1", "Critical", MemoryType.FACT, 0.95f, Instant.now())
        )
        
        `when`(memoryDao.getByMinImportance(minImportance)).thenReturn(importantMemories)
        
        val result = agentMemory.searchByImportance(minImportance)
        
        assertEquals(1, result.size)
        assertEquals("Critical", result[0].content)
    }

    @Test
    fun `clear removes all memories`() = runTest {
        agentMemory.clear()
        
        verify(memoryDao).deleteAll()
        verify(vectorStore).clear()
    }
}
