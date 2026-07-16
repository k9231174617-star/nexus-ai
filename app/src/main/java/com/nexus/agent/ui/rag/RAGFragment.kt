package com.nexus.agent.ui.rag

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nexus.agent.R
import com.nexus.agent.core.rag.RAGSystem
import com.nexus.agent.core.rag.DocumentIngestor
import com.nexus.agent.core.rag.RetrievalResult
import com.nexus.agent.core.rag.VectorSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main RAG (Retrieval-Augmented Generation) fragment.
 * Provides document ingestion, chunking visualization, semantic search,
 * and source attribution for AI-powered responses.
 */
class RAGFragment : Fragment() {

    private lateinit var documentUploadPanel: DocumentUploadPanel
    private lateinit var chunkPreviewView: ChunkPreviewView
    private lateinit var searchQueryView: SearchQueryView
    private lateinit var sourceAttributionView: SourceAttributionView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var btnSettings: ImageButton
    private lateinit var btnClearAll: ImageButton
    private lateinit var statsContainer: LinearLayout
    private lateinit var statDocuments: TextView
    private lateinit var statChunks: TextView
    private lateinit var statVectors: TextView

    private val ragSystem = RAGSystem()
    private val documentIngestor = DocumentIngestor()
    private val vectorSearch = VectorSearch()

    private var isProcessing = false

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            ingestDocuments(uris)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_rag, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupListeners()
        setupRAGSystem()
        updateStats()
    }

    private fun initViews(view: View) {
        documentUploadPanel = view.findViewById(R.id.document_upload_panel)
        chunkPreviewView = view.findViewById(R.id.chunk_preview_view)
        searchQueryView = view.findViewById(R.id.search_query_view)
        sourceAttributionView = view.findViewById(R.id.source_attribution_view)
        progressBar = view.findViewById(R.id.rag_progress)
        statusText = view.findViewById(R.id.rag_status_text)
        btnSettings = view.findViewById(R.id.btn_rag_settings)
        btnClearAll = view.findViewById(R.id.btn_clear_all)
        statsContainer = view.findViewById(R.id.stats_container)
        statDocuments = view.findViewById(R.id.stat_documents)
        statChunks = view.findViewById(R.id.stat_chunks)
        statVectors = view.findViewById(R.id.stat_vectors)

        statusText.text = "Ready"
    }

    private fun setupListeners() {
        documentUploadPanel.setOnUploadClickListener {
            openFilePicker()
        }

        documentUploadPanel.setOnDocumentRemoveListener { documentId ->
            removeDocument(documentId)
        }

        documentUploadPanel.setOnDocumentPreviewListener { documentId ->
            previewDocumentChunks(documentId)
        }

        searchQueryView.setOnSearchListener { query ->
            performSearch(query)
        }

        searchQueryView.setOnQueryFocusChangeListener { hasFocus ->
            if (hasFocus) {
                sourceAttributionView.hide()
            }
        }

        sourceAttributionView.setOnSourceClickListener { source ->
            // Navigate to source in chunk preview
            chunkPreviewView.highlightChunk(source.chunkId)
        }

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        btnClearAll.setOnClickListener {
            clearAllDocuments()
        }
    }

    private fun setupRAGSystem() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ragSystem.initialize(requireContext())
                val stats = ragSystem.getStats()

                withContext(Dispatchers.Main) {
                    updateStatsDisplay(stats)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Initialization failed"
                    statusText.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.neon_red)
                    )
                }
            }
        }
    }

    private fun openFilePicker() {
        filePickerLauncher.launch("*/*")
    }

    private fun ingestDocuments(uris: List<Uri>) {
        if (isProcessing) return

        isProcessing = true
        updateProcessingState(true)
        statusText.text = "Processing ${uris.size} document(s)..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val results = uris.map { uri ->
                    documentIngestor.ingest(uri, requireContext())
                }

                withContext(Dispatchers.Main) {
                    results.forEach { document ->
                        documentUploadPanel.addDocument(document)
                        chunkPreviewView.addChunks(document.chunks)
                    }

                    val stats = ragSystem.getStats()
                    updateStatsDisplay(stats)

                    statusText.text = "Ingested ${results.size} document(s)"
                    Toast.makeText(
                        context,
                        "Successfully processed ${results.sumOf { it.chunkCount }} chunks",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Ingestion failed"
                    Toast.makeText(
                        context,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    updateProcessingState(false)
                }
            }
        }
    }

    private fun removeDocument(documentId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ragSystem.removeDocument(documentId)

                withContext(Dispatchers.Main) {
                    chunkPreviewView.removeChunksByDocument(documentId)
                    val stats = ragSystem.getStats()
                    updateStatsDisplay(stats)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to remove: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun previewDocumentChunks(documentId: String) {
        chunkPreviewView.filterByDocument(documentId)
    }

    private fun performSearch(query: String) {
        if (query.isBlank() || isProcessing) return

        isProcessing = true
        updateProcessingState(true)
        statusText.text = "Searching..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val results = vectorSearch.search(
                    query = query,
                    topK = 5,
                    minScore = 0.7f
                )

                withContext(Dispatchers.Main) {
                    if (results.isEmpty()) {
                        statusText.text = "No relevant sources found"
                        sourceAttributionView.showNoResults()
                    } else {
                        statusText.text = "Found ${results.size} relevant sources"
                        sourceAttributionView.showResults(results)
                        chunkPreviewView.highlightRelevantChunks(results.map { it.chunkId })
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Search failed"
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    updateProcessingState(false)
                }
            }
        }
    }

    private fun clearAllDocuments() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ragSystem.clearAll()

                withContext(Dispatchers.Main) {
                    documentUploadPanel.clear()
                    chunkPreviewView.clear()
                    sourceAttributionView.hide()
                    updateStatsDisplay(ragSystem.getStats())
                    statusText.text = "All documents cleared"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Clear failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSettingsDialog() {
        // Show RAG configuration dialog (chunk size, overlap, embedding model, etc.)
    }

    private fun updateStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            val stats = ragSystem.getStats()
            withContext(Dispatchers.Main) {
                updateStatsDisplay(stats)
            }
        }
    }

    private fun updateStatsDisplay(stats: RAGSystem.Stats) {
        statDocuments.text = "Docs: ${stats.documentCount}"
        statChunks.text = "Chunks: ${stats.chunkCount}"
        statVectors.text = "Vectors: ${stats.vectorCount}"
    }

    private fun updateProcessingState(processing: Boolean) {
        progressBar.visibility = if (processing) View.VISIBLE else View.GONE
        documentUploadPanel.setEnabled(!processing)
        searchQueryView.setEnabled(!processing)
    }

    override fun onDestroy() {
        super.onDestroy()
        ragSystem.cleanup()
    }

    companion object {
        private const val TAG = "RAGFragment"

        fun newInstance(): RAGFragment {
            return RAGFragment()
        }
    }
}
