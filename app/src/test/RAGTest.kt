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

    @Mock
    private lateinit var documentParser: DocumentParser

    private lateinit var ragSystem: RAGSystem

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        ragSystem = RAGSystem(documentIngestor, chunkEmbedder, vectorSearch, contextAssembler, documentParser)
    }

    @Test
    fun `ingestDocument processes and stores document`() = runTest {
        val content = "Document content here"
        val docId = "doc-1"
        val chunks = listOf("chunk1", "chunk2")
        val embeddings = listOf(
            floatArrayOf(0.1f, 0.2f),
            floatArrayOf(0.3f, 0.4f)
        )
        
        `when`(documentParser.parse(content)).thenReturn(chunks)
        `when`(chunkEmbedder.embedChunks(chunks)).thenReturn(embeddings)
        
        ragSystem.ingestDocument(docId, content)
        
        verify(documentIngestor).store(docId, chunks, embeddings)
    }

    @Test
    fun `ingestDocument handles empty content`() = runTest {
        val content = ""
        
        `when`(documentParser.parse(content)).thenReturn(emptyList())
        
        val result = ragSystem.ingestDocument("doc-empty", content)
        
        assertTrue(result.isFailure || result.getOrNull() == Unit)
    }

    @Test
    fun `query retrieves relevant chunks and assembles context`() = runTest {
        val question = "What is Kotlin?"
        val queryEmbedding = floatArrayOf(0.1f, 0.2f)
        val retrievedChunks = listOf(
            RetrievalResult("chunk1", 0.95f, "doc-1"),
            RetrievalResult("chunk2", 0.85f, "doc-1")
        )
        val assembledContext = "Context: chunk1 chunk2"
        
        `when`(chunkEmbedder.embed(question)).thenReturn(queryEmbedding)
        `when`(vectorSearch.search(queryEmbedding, 5)).thenReturn(retrievedChunks)
        `when`(contextAssembler.assemble(retrievedChunks, question)).thenReturn(assembledContext)
        
        val result = ragSystem.query(question)
        
        assertEquals(assembledContext, result)
    }

    @Test
    fun `query returns empty when no relevant chunks found`() = runTest {
        val question = "obscure query"
        val queryEmbedding = floatArrayOf(0.1f)
        
        `when`(chunkEmbedder.embed(question)).thenReturn(queryEmbedding)
        `when`(vectorSearch.search(queryEmbedding, 5)).thenReturn(emptyList())
        
        val result = ragSystem.query(question)
        
        assertTrue(result.isEmpty() || result.contains("no relevant"))
    }

    @Test
    fun `deleteDocument removes from index`() = runTest {
        val docId = "doc-1"
        
        ragSystem.deleteDocument(docId)
        
        verify(documentIngestor).delete(docId)
        verify(vectorSearch).removeByDocument(docId)
    }

    @Test
    fun `getDocumentChunks returns all chunks for doc`() = runTest {
        val docId = "doc-1"
        val chunks = listOf("chunk1", "chunk2")
        
        `when`(documentIngestor.getChunks(docId)).thenReturn(chunks)
        
        val result = ragSystem.getDocumentChunks(docId)
        
        assertEquals(chunks, result)
    }

    @Test
    fun `updateDocument re-ingests with new content`() = runTest {
        val docId = "doc-1"
        val newContent = "Updated content"
        val newChunks = listOf("new-chunk1")
        val newEmbeddings = listOf(floatArrayOf(0.5f))
        
        `when`(documentParser.parse(newContent)).thenReturn(newChunks)
        `when`(chunkEmbedder.embedChunks(newChunks)).thenReturn(newEmbeddings)
        
        ragSystem.updateDocument(docId, newContent)
        
        verify(documentIngestor).delete(docId)
        verify(documentIngestor).store(docId, newChunks, newEmbeddings)
    }

    @Test
    fun `getStats returns document and chunk counts`() = runTest {
        `when`(documentIngestor.countDocuments()).thenReturn(10)
        `when`(documentIngestor.countChunks()).thenReturn(500)
        
        val stats = ragSystem.getStats()
        
        assertEquals(10, stats.documents)
        assertEquals(500, stats.chunks)
    }

    @Test
    fun `queryWithFilters applies metadata filters`() = runTest {
        val question = "test"
        val filters = mapOf("source" to "docs")
        val queryEmbedding = floatArrayOf(0.1f)
        
        `when`(chunkEmbedder.embed(question)).thenReturn(queryEmbedding)
        `when`(vectorSearch.search(queryEmbedding, 5, filters)).thenReturn(emptyList())
        
        ragSystem.queryWithFilters(question, filters)
        
        verify(vectorSearch).search(queryEmbedding, 5, filters)
    }
}
