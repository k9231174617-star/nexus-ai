package com.nexus.agent.ui.universal

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nexus.agent.core.media.DocumentReader
import com.nexus.agent.core.media.DocumentEditor
import com.nexus.agent.databinding.FragmentDocumentViewerBinding
import com.nexus.agent.ui.common.ToastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DocumentViewer — fragment for viewing, editing, and AI-processing documents.
 * Supports PDF, DOCX, TXT, MD, and code files with syntax highlighting.
 */
class DocumentViewer : Fragment() {

    private var _binding: FragmentDocumentViewerBinding? = null
    private val binding get() = _binding!!

    private lateinit var documentReader: DocumentReader
    private lateinit var documentEditor: DocumentEditor
    private var currentDocumentUri: Uri? = null
    private var currentDocumentType: DocumentType = DocumentType.UNKNOWN
    private var isEditMode = false
    private var originalContent: String = ""

    enum class DocumentType {
        PDF, DOCX, TXT, MD, CODE, UNKNOWN
    }

    private val documentImportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> loadDocument(uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        documentReader = DocumentReader(requireContext())
        documentEditor = DocumentEditor(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDocumentViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWebView()
        setupEditorControls()
        setupAIPanel()
    }

    private fun setupWebView() {
        binding.documentWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        binding.documentWebView.webViewClient = DocumentWebViewClient()
    }

    private fun setupEditorControls() {
        binding.btnEditMode.setOnClickListener { toggleEditMode() }
        binding.btnSave.setOnClickListener { saveDocument() }
        binding.btnFindReplace.setOnClickListener { showFindReplaceDialog() }
        binding.btnFormat.setOnClickListener { formatDocument() }
        binding.fontSizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) adjustFontSize(progress + 10)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupAIPanel() {
        binding.btnAISummarize.setOnClickListener { generateSummary() }
        binding.btnAITranslate.setOnClickListener { showTranslateDialog() }
        binding.btnAIExplain.setOnClickListener { explainSelection() }
        binding.btnAIRewrite.setOnClickListener { rewriteSelection() }
        binding.btnAIChat.setOnClickListener { openDocumentChat() }
    }

    fun showImportDialog() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain",
                "text/markdown",
                "text/x-python",
                "text/x-java-source",
                "text/javascript"
            ))
        }
        documentImportLauncher.launch(intent)
    }

    private fun loadDocument(uri: Uri) {
        currentDocumentUri = uri
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading(true)
            try {
                val fileName = getFileName(uri)
                currentDocumentType = detectDocumentType(fileName)
                binding.tvDocumentTitle.text = fileName

                when (currentDocumentType) {
                    DocumentType.PDF -> renderPdf(uri)
                    DocumentType.DOCX -> renderDocx(uri)
                    DocumentType.TXT, DocumentType.MD, DocumentType.CODE -> renderTextDocument(uri)
                    DocumentType.UNKNOWN -> showUnsupportedFormat()
                }
                updateToolbarState()
            } catch (e: Exception) {
                ToastManager.showError(requireContext(), "Failed to load document: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "Unknown"
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun detectDocumentType(fileName: String): DocumentType {
        return when {
            fileName.endsWith(".pdf", ignoreCase = true) -> DocumentType.PDF
            fileName.endsWith(".docx", ignoreCase = true) -> DocumentType.DOCX
            fileName.endsWith(".md", ignoreCase = true) -> DocumentType.MD
            fileName.endsWith(".txt", ignoreCase = true) -> DocumentType.TXT
            fileName.matches(Regex(".*\\.(kt|java|py|js|ts|cpp|c|h|go|rs|swift)$", RegexOption.IGNORE_CASE)) -> DocumentType.CODE
            else -> DocumentType.UNKNOWN
        }
    }

    private suspend fun renderPdf(uri: Uri) {
        val pages = withContext(Dispatchers.IO) {
            documentReader.renderPdfPages(uri)
        }
        val html = buildString {
            append("<html><body style=\'background:#1a1a1a;\'>")
            pages.forEachIndexed { index, bitmap ->
                val base64 = bitmapToBase64(bitmap)
                append("<img src=\'data:image/png;base64,$base64\' style=\'width:100%;margin-bottom:10px;\' />")
            }
            append("</body></html>")
        }
        binding.documentWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private suspend fun renderDocx(uri: Uri) {
        val html = withContext(Dispatchers.IO) {
            documentReader.convertDocxToHtml(uri)
        }
        binding.documentWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private suspend fun renderTextDocument(uri: Uri) {
        val content = withContext(Dispatchers.IO) {
            documentReader.readText(uri)
        }
        originalContent = content
        val html = if (currentDocumentType == DocumentType.CODE) {
            documentReader.syntaxHighlight(content, getLanguageFromUri(uri))
        } else if (currentDocumentType == DocumentType.MD) {
            documentReader.renderMarkdown(content)
        } else {
            "<pre style=\'color:#e0e0e0;background:#1a1a1a;padding:20px;white-space:pre-wrap;\'>${escapeHtml(content)}</pre>"
        }
        binding.documentWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        binding.textEditor.setText(content)
    }

    private fun getLanguageFromUri(uri: Uri): String {
        val fileName = getFileName(uri)
        return when {
            fileName.endsWith(".kt") -> "kotlin"
            fileName.endsWith(".java") -> "java"
            fileName.endsWith(".py") -> "python"
            fileName.endsWith(".js") -> "javascript"
            fileName.endsWith(".ts") -> "typescript"
            fileName.endsWith(".cpp") || fileName.endsWith(".c") -> "cpp"
            fileName.endsWith(".go") -> "go"
            fileName.endsWith(".rs") -> "rust"
            fileName.endsWith(".swift") -> "swift"
            else -> "plaintext"
        }
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("\'", "&#x27;")
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.DEFAULT)
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        if (isEditMode) {
            binding.documentWebView.visibility = View.GONE
            binding.textEditor.visibility = View.VISIBLE
            binding.textEditor.setText(originalContent)
            binding.btnEditMode.text = "View"
            binding.editorToolbar.visibility = View.VISIBLE
        } else {
            binding.documentWebView.visibility = View.VISIBLE
            binding.textEditor.visibility = View.GONE
            binding.btnEditMode.text = "Edit"
            binding.editorToolbar.visibility = View.GONE
            renderTextDocument(currentDocumentUri!!)
        }
    }

    private fun saveDocument() {
        if (!isEditMode) return
        val newContent = binding.textEditor.text.toString()
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading(true)
            try {
                val success = withContext(Dispatchers.IO) {
                    documentEditor.saveText(currentDocumentUri!!, newContent)
                }
                if (success) {
                    originalContent = newContent
                    ToastManager.show(requireContext(), "Document saved")
                } else {
                    ToastManager.showError(requireContext(), "Failed to save")
                }
            } catch (e: Exception) {
                ToastManager.showError(requireContext(), "Save error: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showFindReplaceDialog() {
        val dialog = FindReplaceDialog(requireContext()) { find, replace, replaceAll ->
            performFindReplace(find, replace, replaceAll)
        }
        dialog.show()
    }

    private fun performFindReplace(find: String, replace: String, replaceAll: Boolean) {
        val content = if (isEditMode) binding.textEditor.text.toString() else originalContent
        val newContent = if (replaceAll) {
            content.replace(find, replace)
        } else {
            content.replaceFirst(find, replace)
        }
        if (isEditMode) {
            binding.textEditor.setText(newContent)
        } else {
            originalContent = newContent
            renderTextDocument(currentDocumentUri!!)
        }
    }

    private fun formatDocument() {
        if (currentDocumentType != DocumentType.CODE) return
        val content = binding.textEditor.text.toString()
        viewLifecycleOwner.lifecycleScope.launch {
            val formatted = withContext(Dispatchers.Default) {
                documentEditor.formatCode(content, getLanguageFromUri(currentDocumentUri!!))
            }
            binding.textEditor.setText(formatted)
        }
    }

    private fun adjustFontSize(size: Int) {
        binding.documentWebView.settings.defaultFontSize = size
    }

    private fun generateSummary() {
        val content = if (isEditMode) binding.textEditor.text.toString() else originalContent
        if (content.isBlank()) return

        viewLifecycleOwner.lifecycleScope.launch {
            showLoading(true)
            try {
                val summary = withContext(Dispatchers.IO) {
                    documentEditor.generateSummary(content)
                }
                binding.aiPanel.visibility = View.VISIBLE
                binding.aiOutput.text = summary
            } catch (e: Exception) {
                ToastManager.showError(requireContext(), "Summary failed: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showTranslateDialog() {
        val languages = arrayOf("English", "Russian", "Spanish", "French", "German", "Chinese", "Japanese")
        AlertDialog.Builder(requireContext())
            .setTitle("Translate to")
            .setItems(languages) { _, which ->
                translateDocument(languages[which])
            }
            .show()
    }

    private fun translateDocument(targetLanguage: String) {
        val content = if (isEditMode) binding.textEditor.text.toString() else originalContent
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading(true)
            try {
                val translated = withContext(Dispatchers.IO) {
                    documentEditor.translate(content, targetLanguage)
                }
                binding.aiPanel.visibility = View.VISIBLE
                binding.aiOutput.text = translated
            } catch (e: Exception) {
                ToastManager.showError(requireContext(), "Translation failed: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun explainSelection() {
        val selectedText = binding.documentWebView.evaluateJavascript(
            "window.getSelection().toString()",
            null
        )
        if (selectedText.isNullOrBlank()) {
            ToastManager.show(requireContext(), "Select text first")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading(true)
            try {
                val explanation = withContext(Dispatchers.IO) {
                    documentEditor.explain(selectedText)
                }
                binding.aiPanel.visibility = View.VISIBLE
                binding.aiOutput.text = explanation
            } catch (e: Exception) {
                ToastManager.showError(requireContext(), "Explanation failed: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun rewriteSelection() {
        val selectedText = binding.documentWebView.evaluateJavascript(
            "window.getSelection().toString()",
            null
        )
        if (selectedText.isNullOrBlank()) {
            ToastManager.show(requireContext(), "Select text first")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading(true)
            try {
                val rewritten = withContext(Dispatchers.IO) {
                    documentEditor.rewrite(selectedText)
                }
                binding.aiPanel.visibility = View.VISIBLE
                binding.aiOutput.text = rewritten
            } catch (e: Exception) {
                ToastManager.showError(requireContext(), "Rewrite failed: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun openDocumentChat() {
        val content = if (isEditMode) binding.textEditor.text.toString() else originalContent
        val bundle = Bundle().apply {
            putString("document_context", content.take(4000))
            putString("document_title", binding.tvDocumentTitle.text.toString())
        }
        findNavController().navigate(R.id.action_documentViewer_to_chat, bundle)
    }

    private fun showUnsupportedFormat() {
        ToastManager.showError(requireContext(), "Unsupported document format")
    }

    fun shareCurrentDocument() {
        currentDocumentUri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = when (currentDocumentType) {
                    DocumentType.PDF -> "application/pdf"
                    DocumentType.DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    else -> "text/plain"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
            }
            startActivity(Intent.createChooser(intent, "Share Document"))
        }
    }

    fun exportDocument() {
        currentDocumentUri?.let { uri ->
            viewLifecycleOwner.lifecycleScope.launch {
                showLoading(true)
                try {
                    val exportUri = withContext(Dispatchers.IO) {
                        documentEditor.export(uri, currentDocumentType.name.lowercase())
                    }
                    exportUri?.let {
                        ToastManager.show(requireContext(), "Document exported")
                    }
                } catch (e: Exception) {
                    ToastManager.showError(requireContext(), "Export failed: ${e.message}")
                } finally {
                    showLoading(false)
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateToolbarState() {
        val parent = parentFragment as? UniversalAgentFragment
        parent?.getMediaToolbar()?.apply {
            setShareEnabled(currentDocumentUri != null)
            setExportEnabled(currentDocumentUri != null)
        }
    }

    private inner class DocumentWebViewClient : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            binding.progressBar.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.documentWebView.destroy()
        _binding = null
    }
}
