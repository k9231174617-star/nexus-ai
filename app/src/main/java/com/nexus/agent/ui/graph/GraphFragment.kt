package com.nexus.agent.ui.graph

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nexus.agent.R
import com.nexus.agent.core.graph.GraphMemory
import com.nexus.agent.core.graph.EntityNode
import com.nexus.agent.core.graph.RelationEdge
import kotlinx.coroutines.launch

class GraphFragment : Fragment() {

    private lateinit var graphView: EntityGraphView
    private lateinit var queryPanel: GraphQueryPanel
    private lateinit var relationEditor: RelationEditor
    private lateinit var graphMemory: GraphMemory
    private lateinit var searchInput: EditText
    private lateinit var btnAddEntity: ImageButton
    private lateinit var btnSearch: ImageButton
    private lateinit var btnLayout: ImageButton
    private lateinit var btnToggleEditor: ImageButton
    private lateinit var editorContainer: FrameLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_graph, container, false)
        initViews(view)
        initGraphMemory()
        setupListeners()
        loadInitialData()
        return view
    }

    private fun initViews(view: View) {
        graphView = view.findViewById(R.id.entityGraphView)
        queryPanel = view.findViewById(R.id.graphQueryPanel)
        relationEditor = RelationEditor(requireContext())
        searchInput = view.findViewById(R.id.etGraphSearch)
        btnAddEntity = view.findViewById(R.id.btnAddEntity)
        btnSearch = view.findViewById(R.id.btnGraphSearch)
        btnLayout = view.findViewById(R.id.btnGraphLayout)
        btnToggleEditor = view.findViewById(R.id.btnToggleEditor)
        editorContainer = view.findViewById(R.id.relationEditorContainer)
        
        editorContainer.addView(relationEditor)
        editorContainer.visibility = View.GONE
    }

    private fun initGraphMemory() {
        graphMemory = GraphMemory.getInstance(requireContext())
    }

    private fun setupListeners() {
        btnAddEntity.setOnClickListener {
            showAddEntityDialog()
        }

        btnSearch.setOnClickListener {
            performSearch(searchInput.text.toString())
        }

        btnLayout.setOnClickListener {
            graphView.applyForceLayout()
        }

        btnToggleEditor.setOnClickListener {
            toggleEditor()
        }

        graphView.setOnNodeClickListener { node ->
            onNodeSelected(node)
        }

        graphView.setOnEdgeClickListener { edge ->
            relationEditor.setRelation(edge)
            editorContainer.visibility = View.VISIBLE
        }

        queryPanel.setOnQueryExecuteListener { query ->
            executeGraphQuery(query)
        }
    }

    private fun loadInitialData() {
        lifecycleScope.launch {
            try {
                val entities = graphMemory.getAllEntities()
                val relations = graphMemory.getAllRelations()
                graphView.setData(entities, relations)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load graph: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddEntityDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Add Entity")
            .setView(R.layout.dialog_add_entity)
            .setPositiveButton("Add") { _, _ ->
                // Handle add entity
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) return
        
        lifecycleScope.launch {
            val results = graphMemory.searchEntities(query)
            graphView.highlightNodes(results.map { it.id })
            if (results.isNotEmpty()) {
                graphView.centerOnNode(results.first().id)
            }
        }
    }

    private fun onNodeSelected(node: EntityNode) {
        relationEditor.setSourceNode(node)
        editorContainer.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val related = graphMemory.getRelatedEntities(node.id)
            graphView.highlightNeighbors(node.id, related.map { it.id })
        }
    }

    private fun toggleEditor() {
        editorContainer.visibility = if (editorContainer.visibility == View.VISIBLE) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun executeGraphQuery(query: String) {
        lifecycleScope.launch {
            try {
                val results = graphMemory.executeQuery(query)
                queryPanel.displayResults(results)
            } catch (e: Exception) {
                queryPanel.showError(e.message ?: "Query failed")
            }
        }
    }

    fun addEntity(name: String, type: String, properties: Map<String, String> = emptyMap()) {
        lifecycleScope.launch {
            val entity = graphMemory.createEntity(name, type, properties)
            graphView.addNode(entity)
        }
    }

    fun addRelation(fromId: String, toId: String, type: String, properties: Map<String, String> = emptyMap()) {
        lifecycleScope.launch {
            val relation = graphMemory.createRelation(fromId, toId, type, properties)
            graphView.addEdge(relation)
        }
    }
}
