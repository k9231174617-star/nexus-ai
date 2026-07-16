package com.nexus.agent.ui.graph

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nexus.agent.R
import com.nexus.agent.core.graph.EntityNode
import com.nexus.agent.core.graph.GraphMemory
import com.nexus.agent.core.graph.RelationEdge
import kotlinx.coroutines.launch

class RelationEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var tvSourceNode: TextView
    private lateinit var tvTargetNode: TextView
    private lateinit var btnSelectTarget: Button
    private lateinit var actvRelationType: AutoCompleteTextView
    private lateinit var etRelationProperties: EditText
    private lateinit var btnSave: Button
    private lateinit var btnDelete: Button
    private lateinit var btnCancel: ImageButton
    private lateinit var tvEditorTitle: TextView

    private var graphMemory: GraphMemory? = null
    private var sourceNode: EntityNode? = null
    private var targetNode: EntityNode? = null
    private var currentRelation: RelationEdge? = null
    private var onRelationChangedListener: (() -> Unit)? = null

    private val relationTypes = listOf(
        "relates_to", "part_of", "contains", "created_by", 
        "located_in", "works_at", "knows", "owns", "uses",
        "depends_on", "implements", "extends", "references"
    )

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_relation_editor, this, true)
        initViews()
        setupListeners()
    }

    private fun initViews() {
        tvSourceNode = findViewById(R.id.tvSourceNode)
        tvTargetNode = findViewById(R.id.tvTargetNode)
        btnSelectTarget = findViewById(R.id.btnSelectTarget)
        actvRelationType = findViewById(R.id.actvRelationType)
        etRelationProperties = findViewById(R.id.etRelationProperties)
        btnSave = findViewById(R.id.btnSaveRelation)
        btnDelete = findViewById(R.id.btnDeleteRelation)
        btnCancel = findViewById(R.id.btnCancelEditor)
        tvEditorTitle = findViewById(R.id.tvEditorTitle)

        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, relationTypes)
        actvRelationType.setAdapter(adapter)
    }

    private fun setupListeners() {
        btnSelectTarget.setOnClickListener {
            showTargetSelector()
        }

        btnSave.setOnClickListener {
            saveRelation()
        }

        btnDelete.setOnClickListener {
            deleteRelation()
        }

        btnCancel.setOnClickListener {
            clear()
            visibility = GONE
        }
    }

    fun setGraphMemory(memory: GraphMemory) {
        this.graphMemory = memory
    }

    fun setSourceNode(node: EntityNode) {
        this.sourceNode = node
        this.targetNode = null
        this.currentRelation = null
        tvSourceNode.text = "From: ${node.name} (${node.type})"
        tvTargetNode.text = "To: (select target)"
        tvEditorTitle.text = "New Relation"
        btnDelete.visibility = GONE
        actvRelationType.setText("", false)
        etRelationProperties.setText("")
    }

    fun setTargetNode(node: EntityNode) {
        this.targetNode = node
        tvTargetNode.text = "To: ${node.name} (${node.type})"
    }

    fun setRelation(relation: RelationEdge) {
        this.currentRelation = relation
        this.sourceNode = null
        this.targetNode = null
        
        tvEditorTitle.text = "Edit Relation"
        tvSourceNode.text = "From: ${relation.fromId}"
        tvTargetNode.text = "To: ${relation.toId}"
        actvRelationType.setText(relation.type, false)
        
        val propsText = relation.properties.entries.joinToString("\n") { "${it.key}=${it.value}" }
        etRelationProperties.setText(propsText)
        
        btnDelete.visibility = VISIBLE
    }

    private fun showTargetSelector() {
        if (graphMemory == null) {
            Toast.makeText(context, "Graph memory not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        val lifecycleOwner = context as? LifecycleOwner ?: return
        
        lifecycleOwner.lifecycleScope.launch {
            try {
                val entities = graphMemory!!.getAllEntities()
                val items = entities.map { "${it.name} (${it.type})" }.toTypedArray()
                val entityMap = entities.associateBy { "${it.name} (${it.type})" }

                androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Select Target Entity")
                    .setItems(items) { _, which ->
                        val selected = entityMap[items[which]]
                        selected?.let { setTargetNode(it) }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveRelation() {
        val type = actvRelationType.text.toString().trim()
        if (type.isEmpty()) {
            actvRelationType.error = "Relation type is required"
            return
        }

        val properties = parseProperties(etRelationProperties.text.toString())

        val lifecycleOwner = context as? LifecycleOwner ?: return
        
        lifecycleOwner.lifecycleScope.launch {
            try {
                if (currentRelation != null) {
                    // Update existing relation
                    graphMemory?.updateRelation(
                        currentRelation!!.id,
                        type = type,
                        properties = properties
                    )
                    Toast.makeText(context, "Relation updated", Toast.LENGTH_SHORT).show()
                } else {
                    // Create new relation
                    val source = sourceNode ?: run {
                        Toast.makeText(context, "Source node not set", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val target = targetNode ?: run {
                        Toast.makeText(context, "Target node not set", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    graphMemory?.createRelation(
                        fromId = source.id,
                        toId = target.id,
                        type = type,
                        properties = properties
                    )
                    Toast.makeText(context, "Relation created", Toast.LENGTH_SHORT).show()
                }

                onRelationChangedListener?.invoke()
                clear()
                visibility = GONE
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteRelation() {
        val relation = currentRelation ?: return
        
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Delete Relation")
            .setMessage("Are you sure you want to delete this relation?")
            .setPositiveButton("Delete") { _, _ ->
                val lifecycleOwner = context as? LifecycleOwner ?: return@setPositiveButton
                
                lifecycleOwner.lifecycleScope.launch {
                    try {
                        graphMemory?.deleteRelation(relation.id)
                        Toast.makeText(context, "Relation deleted", Toast.LENGTH_SHORT).show()
                        onRelationChangedListener?.invoke()
                        clear()
                        visibility = GONE
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun parseProperties(text: String): Map<String, String> {
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains("=") }
            .associate { line ->
                val parts = line.split("=", limit = 2)
                parts[0].trim() to (parts.getOrNull(1)?.trim() ?: "")
            }
    }

    fun clear() {
        sourceNode = null
        targetNode = null
        currentRelation = null
        tvSourceNode.text = "From: (select source)"
        tvTargetNode.text = "To: (select target)"
        actvRelationType.setText("", false)
        etRelationProperties.setText("")
        btnDelete.visibility = GONE
    }

    fun setOnRelationChangedListener(listener: () -> Unit) {
        onRelationChangedListener = listener
    }
}
