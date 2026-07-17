package com.nexus.agent.core.memory

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class MemoryTest {

    @Mock
    private lateinit var memoryDao: MemoryDao

    @Mock
    private lateinit var vectorStore: VectorStore

    @Mock
    private lateinit var localEmbedder: LocalEmbedder

    private lateinit var agentMemory: AgentMemory

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        agentMemory = AgentMemory(memoryDao, vectorStore, localEmbedder)
    }

    @Test
    fun `saveMemory stores and indexes`() = runTest {
        val text = "Important information"
        `when`(localEmbedder.embed(text)).thenReturn(FloatArray(384))
        `when`(memoryDao.insert(any())).thenReturn(Unit)

        agentMemory.saveMemory(text, "conversation")

        verify(memoryDao).insert(any())
    }

    @Test
    fun `searchMemory returns results`() = runTest {
        `when`(vectorStore.search(any(), anyInt())).thenReturn(listOf())

        val results = agentMemory.searchMemory("test", 5)

        assertNotNull(results)
    }
}
