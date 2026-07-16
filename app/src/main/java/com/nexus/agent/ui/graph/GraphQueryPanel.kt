package com.nexus.agent.ui.graph

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import com.nexus.agent.R
import org.json.JSONArray
import org.json.JSONObject

class GraphQueryPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var etQuery: EditText
    private lateinit var btnExecute: Button
    private lateinit var btnClear: ImageButton
    private lateinit var btnHistory: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvResults: TextView
    private lateinit var scrollResults: ScrollView
    private lateinit var tvStatus: TextView
    private lateinit var btnSampleQueries: Button

    private var onQueryExecuteListener: ((String) -> Unit)? = null
    private val queryHistory = mutableListOf<String>()
    private val sampleQueries = listOf(
        "MATCH (n) RETURN n LIMIT 10",
        "MATCH (a)-[r]->(b) RETURN a.name, r.type, b.name",
        "MATCH (n:person) RETURN n.name, n.properties",
        "MATCH (n)-[:works_at]->(m) RETURN n.name, m.name",
        "MATCH (n) WHERE n.name CONTAINS 'AI' RETURN n"
    )

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_graph_query_panel, this, true)
        initViews()
        setupListeners()
    }

    private fun initViews() {
        etQuery = findViewById(R.id.etGraphQuery)
        btnExecute = findViewById(R.id.btnExecuteQuery)
        btnClear = findViewById(R.id.btnClearQuery)
        btnHistory = findViewById(R.id.btnQueryHistory)
        progressBar = findViewById(R.id.queryProgressBar)
        tvResults = findViewById(R.id.tvQueryResults)
        scrollResults = findViewById(R.id.scrollQueryResults)
        tvStatus = findViewById(R.id.tvQueryStatus)
        btnSampleQueries = findViewById(R.id.btnSampleQueries)

        tvResults.text = "Results will appear here..."
        tvStatus.text = "Ready"
    }

    private fun setupListeners() {
        btnExecute.setOnClickListener {
            val query = etQuery.text.toString().trim()
            if (query.isNotEmpty()) {
                executeQuery(query)
            } else {
                etQuery.error = "Enter a query"
            }
        }

        btnClear.setOnClickListener {
            etQuery.setText("")
            tvResults.text = "Results will appear here..."
            tvStatus.text = "Ready"
        }

        btnHistory.setOnClickListener {
            showHistoryDialog()
        }

        btnSampleQueries.setOnClickListener {
            showSampleQueriesDialog()
        }
    }

    private fun executeQuery(query: String) {
        queryHistory.add(query)
        if (queryHistory.size > 50) queryHistory.removeAt(0)

        progressBar.isVisible = true
        tvStatus.text = "Executing..."
        btnExecute.isEnabled = false

        onQueryExecuteListener?.invoke(query)
    }

    fun displayResults(results: List<Map<String, Any>>) {
        progressBar.isVisible = false
        btnExecute.isEnabled = true
        tvStatus.text = "Found ${results.size} results"

        if (results.isEmpty()) {
            tvResults.text = "No results found."
            return
        }

        val sb = StringBuilder()
        results.forEachIndexed { index, row ->
            sb.append("─".repeat(40)).append("\n")
            sb.append("Result #${index + 1}\n")
            sb.append("─".repeat(40)).append("\n")
            
            row.forEach { (key, value) ->
                sb.append("$key: ${formatValue(value)}\n")
            }
            sb.append("\n")
        }

        tvResults.text = sb.toString()
        scrollResults.post { scrollResults.fullScroll(View.FOCUS_DOWN) }
    }

    fun displayResultsJson(jsonArray: JSONArray) {
        progressBar.isVisible = false
        btnExecute.isEnabled = true
        tvStatus.text = "Found ${jsonArray.length()} results"

        if (jsonArray.length() == 0) {
            tvResults.text = "No results found."
            return
        }

        val sb = StringBuilder()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            sb.append("─".repeat(40)).append("\n")
            sb.append("Result #${i + 1}\n")
            sb.append("─".repeat(40)).append("\n")
            sb.append(formatJsonObject(obj, 0))
            sb.append("\n")
        }

        tvResults.text = sb.toString()
    }

    fun showError(message: String) {
        progressBar.isVisible = false
        btnExecute.isEnabled = true
        tvStatus.text = "Error"
        tvResults.text = "ERROR: $message\n\nCheck your query syntax and try again."
    }

    private fun formatValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is Map<*, *> -> {
                value.entries.joinToString(", ", "{", "}") { 
                    "${it.key}=${formatValue(it.value)}" 
                }
            }
            is List<*> -> value.joinToString(", ", "[", "]") { formatValue(it) }
            else -> value.toString()
        }
    }

    private fun formatJsonObject(obj: JSONObject, indent: Int): String {
        val sb = StringBuilder()
        val keys = obj.keys()
        val prefix = "  ".repeat(indent)
        
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.get(key)
            sb.append("$prefix$key: ")
            
            when (value) {
                is JSONObject -> {
                    sb.append("\n")
                    sb.append(formatJsonObject(value, indent + 1))
                }
                is JSONArray -> {
                    sb.append("[\n")
                    for (i in 0 until value.length()) {
                        val item = value.get(i)
                        if (item is JSONObject) {
                            sb.append(formatJsonObject(item, indent + 2))
                        } else {
                            sb.append("${"  ".repeat(indent + 2)}$item\n")
                        }
                    }
                    sb.append("${"  ".repeat(indent + 1)}]\n")
                }
                else -> sb.append("$value\n")
            }
        }
        
        return sb.toString()
    }

    private fun showHistoryDialog() {
        if (queryHistory.isEmpty()) {
            tvResults.text = "No query history yet."
            return
        }

        val items = queryHistory.reversed().toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Query History")
            .setItems(items) { _, which ->
                etQuery.setText(items[which])
            }
            .setPositiveButton("Clear History") { _, _ ->
                queryHistory.clear()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showSampleQueriesDialog() {
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Sample Queries")
            .setItems(sampleQueries.toTypedArray()) { _, which ->
                etQuery.setText(sampleQueries[which])
            }
            .setNegativeButton("Close", null)
            .show()
    }

    fun setOnQueryExecuteListener(listener: (String) -> Unit) {
        onQueryExecuteListener = listener
    }

    fun getCurrentQuery(): String = etQuery.text.toString()

    fun setQuery(query: String) {
        etQuery.setText(query)
    }
}
