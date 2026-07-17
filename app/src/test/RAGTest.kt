package com.nexus.agent.core.rag

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class RAGTest {

    @Mock
    private lateinit var documentIngestor: DocumentIngestor

    @Mock
    private lateinit var chunkEmbedder: ChunkEmbedder

    @Mock
    private lateinit var vectorSearch: VectorSearch

    @Mock
    private lateinit var contextAssembler: ContextAssembler

    private lateinit var ragSystem: RAGSystem

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        ragSystem = RAGSystem(documentIngestor, chunkEmbedder, vectorSearch, contextAssembler)
    }

    @Test
    fun `query returns context`() = runTest {
        `when`(contextAssembler.assemble(any(), anyInt())).thenReturn("Assisted context")

        val result = ragSystem.query("test query")

        assertEquals("Assisted context", result)
    }
}
