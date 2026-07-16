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
    private lateinit var neo4jDriver: Neo4jDriver

    @Mock
    private lateinit var graphQueryBuilder: GraphQueryBuilder

    private lateinit var graphMemory: GraphMemory

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        graphMemory = GraphMemory(neo4jDriver, graphQueryBuilder)
    }

    @Test
    fun `addEntity creates node with properties`() = runTest {
        val entity = EntityNode(
            id = "ent-1",
            type = "Person",
            properties = mapOf("name" to "Alice", "age" to "30")
        )
        
        `when`(graphQueryBuilder.createEntityQuery(entity)).thenReturn("CREATE (n:Person {id:'ent-1',name:'Alice',age:'30'})")
        
        graphMemory.addEntity(entity)
        
        verify(neo4jDriver).execute("CREATE (n:Person {id:'ent-1',name:'Alice',age:'30'})")
    }

    @Test
    fun `addRelation creates edge between entities`() = runTest {
        val relation = RelationEdge(
            id = "rel-1",
            fromId = "ent-1",
            toId = "ent-2",
            type = "KNOWS",
            properties = mapOf("since" to "2020")
        )
        
        `when`(graphQueryBuilder.createRelationQuery(relation)).thenReturn(
            "MATCH (a),(b) WHERE a.id='ent-1' AND b.id='ent-2' CREATE (a)-[:KNOWS {since:'2020'}]->(b)"
        )
        
        graphMemory.addRelation(relation)
        
        verify(neo4jDriver).execute(anyString())
    }

    @Test
    fun `getEntity returns node by id`() = runTest {
        val entityId = "ent-1"
        val expectedEntity = EntityNode("ent-1", "Person", mapOf("name" to "Alice"))
        
        `when`(graphQueryBuilder.getEntityQuery(entityId)).thenReturn("MATCH (n {id:'ent-1'}) RETURN n")
        `when`(neo4jDriver.querySingle(anyString())).thenReturn(expectedEntity)
        
        val result = graphMemory.getEntity(entityId)
        
        assertEquals(expectedEntity, result)
    }

    @Test
    fun `getEntity returns null when not found`() = runTest {
        `when`(neo4jDriver.querySingle(anyString())).thenReturn(null)
        
        val result = graphMemory.getEntity("nonexistent")
        
        assertNull(result)
    }

    @Test
    fun `getRelations returns edges for entity`() = runTest {
        val entityId = "ent-1"
        val relations = listOf(
            RelationEdge("rel-1", "ent-1", "ent-2", "KNOWS", emptyMap()),
            RelationEdge("rel-2", "ent-1", "ent-3", "WORKS_WITH", emptyMap())
        )
        
        `when`(graphQueryBuilder.getRelationsQuery(entityId)).thenReturn("MATCH (n {id:'ent-1'})-[r]-() RETURN r")
        `when`(neo4jDriver.queryList(anyString())).thenReturn(relations)
        
        val result = graphMemory.getRelations(entityId)
        
        assertEquals(2, result.size)
    }

    @Test
    fun `findPath returns shortest path between entities`() = runTest {
        val fromId = "ent-1"
        val toId = "ent-4"
        val path = listOf(
            EntityNode("ent-1", "Person", emptyMap()),
            RelationEdge("rel-1", "ent-1", "ent-2", "KNOWS", emptyMap()),
            EntityNode("ent-2", "Person", emptyMap()),
            RelationEdge("rel-2", "ent-2", "ent-4", "KNOWS", emptyMap()),
            EntityNode("ent-4", "Person", emptyMap())
        )
        
        `when`(graphQueryBuilder.shortestPathQuery(fromId, toId)).thenReturn("MATCH p=shortestPath((a {id:'ent-1'})-[:KNOWS*]-(b {id:'ent-4'})) RETURN p")
        `when`(neo4jDriver.queryPath(anyString())).thenReturn(path)
        
        val result = graphMemory.findPath(fromId, toId)
        
        assertEquals(5, result.size)
    }

    @Test
    fun `searchByType returns entities of given type`() = runTest {
        val type = "Person"
        val entities = listOf(
            EntityNode("ent-1", "Person", mapOf("name" to "Alice")),
            EntityNode("ent-2", "Person", mapOf("name" to "Bob"))
        )
        
        `when`(graphQueryBuilder.searchByTypeQuery(type)).thenReturn("MATCH (n:Person) RETURN n")
        `when`(neo4jDriver.queryList(anyString())).thenReturn(entities)
        
        val result = graphMemory.searchByType(type)
        
        assertEquals(2, result.size)
    }

    @Test
    fun `updateEntity modifies node properties`() = runTest {
        val entityId = "ent-1"
        val newProps = mapOf("name" to "Alice Smith", "age" to "31")
        
        `when`(graphQueryBuilder.updateEntityQuery(entityId, newProps)).thenReturn(
            "MATCH (n {id:'ent-1'}) SET n.name='Alice Smith', n.age='31'"
        )
        
        graphMemory.updateEntity(entityId, newProps)
        
        verify(neo4jDriver).execute(anyString())
    }

    @Test
    fun `deleteEntity removes node and relations`() = runTest {
        val entityId = "ent-1"
        
        `when`(graphQueryBuilder.deleteEntityQuery(entityId)).thenReturn(
            "MATCH (n {id:'ent-1'}) DETACH DELETE n"
        )
        
        graphMemory.deleteEntity(entityId)
        
        verify(neo4jDriver).execute(anyString())
    }

    @Test
    fun `getGraphStats returns node and edge counts`() = runTest {
        `when`(neo4jDriver.querySingle("MATCH (n) RETURN count(n) as count")).thenReturn(100L)
        `when`(neo4jDriver.querySingle("MATCH ()-[r]->() RETURN count(r) as count")).thenReturn(250L)
        
        val stats = graphMemory.getGraphStats()
        
        assertEquals(100, stats.nodeCount)
        assertEquals(250, stats.edgeCount)
    }
}
