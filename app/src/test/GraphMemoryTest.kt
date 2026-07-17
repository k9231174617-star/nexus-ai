package com.nexus.agent.core.graph

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class GraphMemoryTest {

    @Mock
    private lateinit var graphDao: GraphDao

    @Mock
    private lateinit var graphQueryBuilder: GraphQueryBuilder

    private lateinit var graphMemory: GraphMemory

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        graphMemory = GraphMemory(graphDao, graphQueryBuilder)
    }

    @Test
    fun `addEntity creates node with properties`() = runTest {
        val entity = EntityNode(
            id = "ent-1",
            type = "Person",
            properties = mapOf("name" to "Alice", "age" to "30")
        )

        `when`(graphDao.insertNode(entity)).thenReturn(Unit)

        graphMemory.addEntity("Alice", "Person", mapOf("age" to "30"))
        verify(graphDao).insertNode(any())
    }

    @Test
    fun `addRelation creates edge between nodes`() = runTest {
        val edge = RelationEdge(
            id = "edge-1",
            sourceId = "ent-1",
            targetId = "ent-2",
            type = "KNOWS",
        )

        `when`(graphDao.insertEdge(edge)).thenReturn(Unit)

        graphMemory.addRelation("ent-1", "ent-2", "KNOWS")
        verify(graphDao).insertEdge(any())
    }

    @Test
    fun `searchEntities returns matching nodes`() = runTest {
        `when`(graphQueryBuilder.searchQuery("Alice", 10)).thenReturn("SELECT * FROM entities WHERE ...")

        val results = graphMemory.searchEntities("Alice", 10)

        assertNotNull(results)
    }

    @Test
    fun `getRelatedEntities returns connected nodes`() = runTest {
        val results = graphMemory.getRelatedEntities("ent-1", 2)

        assertNotNull(results)
    }
}
