package com.nexus.agent.ui.files

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.nexus.agent.R
import com.nexus.agent.core.files.model.BreadcrumbItem
import com.nexus.agent.core.files.model.ClipboardOperation
import com.nexus.agent.core.files.model.FileFilterType
import com.nexus.agent.core.files.model.FileItem
import com.nexus.agent.core.files.model.FileOperation
import com.nexus.agent.core.files.model.FilePreviewState
import com.nexus.agent.core.files.model.FileViewState
import com.nexus.agent.core.files.model.PreviewType
import com.nexus.agent.core.files.model.ViewMode
import com.nexus.agent.databinding.FragmentFilesBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    // State
    private val _state = MutableStateFlow(FileViewState())
    val state: StateFlow<FileViewState> = _state

    // Views
    private lateinit var filesGridView: FilesGridView
    private lateinit var breadcrumbView: BreadcrumbView
    private lateinit var fab: FloatingActionButton

    // Preview panel
    private var previewPanel: FilePreviewPanel? = null
    private var bottomSheetDialog: BottomSheetDialog? = null

    // File pickers
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris?.forEach { uri -> importFile(uri) }
    }

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { /* handle created document */ }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews()
        initMenu()
        observeState()
        loadDirectory(Environment.getExternalStorageDirectory().absolutePath)
    }

    private fun initViews() {
        filesGridView = binding.filesGridView
        breadcrumbView = binding.breadcrumbView
        fab = binding.fabAdd

        // Files grid
        filesGridView.setOnFileClickListener { file ->
            if (file.isDirectory) {
                navigateTo(file.absolutePath)
            } else {
                showPreview(file)
            }
        }

        filesGridView.setOnFileLongClickListener { file ->
            enterMultiSelectMode()
        }

        filesGridView.setOnSelectionChangedListener { selected ->
            _state.update { it.copy(selectedItems = selected) }
            updateActionMode()
        }

        filesGridView.setOnRefreshListener {
            refreshCurrentDirectory()
        }

        // Breadcrumbs
        breadcrumbView.setOnBreadcrumbClickListener { item ->
            navigateTo(item.fullPath)
        }

        // FAB
        fab.setOnClickListener {
            showAddMenu()
        }
    }

    private fun initMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.files_menu, menu)
                
                // Search
                val searchItem = menu.findItem(R.id.action_search)
                val searchView = searchItem.actionView as SearchView
                searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean = false
                    override fun onQueryTextChange(newText: String?): Boolean {
                        _state.update { it.copy(searchQuery = newText ?: "") }
                        refreshCurrentDirectory()
                        return true
                    }
                })

                // View mode toggle
                updateViewModeIcon(menu.findItem(R.id.action_view_mode))
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_view_mode -> {
                        toggleViewMode()
                        true
                    }
                    R.id.action_sort_name -> { setSort(com.nexus.agent.core.files.model.SortBy.NAME); true }
                    R.id.action_sort_size -> { setSort(com.nexus.agent.core.files.model.SortBy.SIZE); true }
                    R.id.action_sort_date -> { setSort(com.nexus.agent.core.files.model.SortBy.DATE); true }
                    R.id.action_filter_all -> { setFilter(FileFilterType.ALL); true }
                    R.id.action_filter_images -> { setFilter(FileFilterType.IMAGES); true }
                    R.id.action_filter_documents -> { setFilter(FileFilterType.DOCUMENTS); true }
                    R.id.action_filter_code -> { setFilter(FileFilterType.CODE); true }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                _state.collect { state ->
                    filesGridView.setState(state)
                    updateBreadcrumbs(state.currentPath)
                    updateFab(state.isMultiSelectMode)
                }
            }
        }
    }

    // ==================== NAVIGATION ====================

    private fun navigateTo(path: String) {
        loadDirectory(path)
    }

    private fun navigateUp() {
        val current = _state.value.currentPath
        val parent = File(current).parentFile?.absolutePath
        parent?.let { navigateTo(it) }
    }

    private fun loadDirectory(path: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val file = File(path)
                if (!file.exists() || !file.isDirectory) {
                    _state.update { 
                        it.copy(isLoading = false, error = "Directory not accessible") 
                    }
                    return@launch
                }

                val items = file.listFiles()?.map { FileItem.fromFile(it) } ?: emptyList()
                
                _state.update {
                    it.copy(
                        currentPath = path,
                        items = items,
                        isLoading = false,
                        selectedItems = emptySet(),
                        isMultiSelectMode = false
                    )
                }
            } catch (e: SecurityException) {
                _state.update { 
                    it.copy(isLoading = false, error = "Permission denied: ${e.message}") 
                }
            } catch (e: Exception) {
                _state.update { 
                    it.copy(isLoading = false, error = e.message) 
                }
            }
        }
    }

    private fun refreshCurrentDirectory() {
        loadDirectory(_state.value.currentPath)
    }

    // ==================== BREADCRUMBS ====================

    private fun updateBreadcrumbs(path: String) {
        val parts = path.split(File.separator).filter { it.isNotEmpty() }
        val breadcrumbs = mutableListOf<BreadcrumbItem>()
        
        var currentPath = ""
        parts.forEach { part ->
            currentPath += File.separator + part
            breadcrumbs.add(BreadcrumbItem(part, currentPath))
        }
        
        // Root
        if (breadcrumbs.isEmpty()) {
            breadcrumbs.add(BreadcrumbItem("root", "/"))
        }
        
        breadcrumbView.setBreadcrumbs(breadcrumbs)
        
        // Проверяем root-зону
        val isRootZone = path.startsWith("/data/data/") || path.startsWith("/system/")
        breadcrumbView.setRootZone(isRootZone)
    }

    // ==================== MULTISELECT ====================

    private fun enterMultiSelectMode() {
        _state.update { it.copy(isMultiSelectMode = true) }
    }

    private fun exitMultiSelectMode() {
        filesGridView.exitMultiSelectMode()
        _state.update { it.copy(isMultiSelectMode = false, selectedItems = emptySet()) }
    }

    private fun updateActionMode() {
        val selectedCount = _state.value.selectedItems.size
        // Здесь можно показать ActionMode toolbar
    }

    // ==================== VIEW MODE ====================

    private fun toggleViewMode() {
        _state.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
        }
    }

    private fun updateViewModeIcon(item: MenuItem?) {
        item?.setIcon(
            if (_state.value.viewMode == ViewMode.GRID) R.drawable.ic_view_list 
            else R.drawable.ic_view_grid
        )
    }

    // ==================== SORT & FILTER ====================

    private fun setSort(sortBy: com.nexus.agent.core.files.model.SortBy) {
        _state.update { 
            it.copy(
                sortBy = sortBy,
                sortAscending = if (it.sortBy == sortBy) !it.sortAscending else true
            )
        }
    }

    private fun setFilter(filter: FileFilterType) {
        _state.update { it.copy(filterType = filter) }
    }

    // ==================== PREVIEW ====================

    private fun showPreview(file: FileItem) {
        val previewType = determinePreviewType(file)
        
        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme).apply {
            setContentView(R.layout.dialog_file_preview)
        }
        
        previewPanel = dialog.findViewById(R.id.preview_panel)
        previewPanel?.setOnActionListener { action ->
            when (action) {
                FilePreviewPanel.PreviewAction.OPEN -> openFile(file)
                FilePreviewPanel.PreviewAction.SHARE -> shareFile(file)
                FilePreviewPanel.PreviewAction.SEND_TO_RAG -> sendToRag(file)
                FilePreviewPanel.PreviewAction.SEND_TO_CHAT -> sendToChat(file)
                else -> {}
            }
        }

        // Загружаем превью
        loadPreviewContent(file, previewType)
        
        bottomSheetDialog = dialog
        dialog.show()
    }

    private fun determinePreviewType(file: FileItem): PreviewType {
        return when {
            file.isDirectory -> PreviewType.UNSUPPORTED
            file.isText || file.isCode -> PreviewType.TEXT
            file.isImage -> when (file.extension) {
                "svg" -> PreviewType.SVG
                else -> PreviewType.IMAGE
            }
            file.isVideo -> PreviewType.VIDEO
            file.isAudio -> PreviewType.AUDIO
            file.isApk -> PreviewType.APK
            file.extension == "pdf" -> PreviewType.PDF
            file.isArchive -> PreviewType.ARCHIVE
            else -> PreviewType.BINARY
        }
    }

    private fun loadPreviewContent(file: FileItem, type: PreviewType) {
        viewLifecycleOwner.lifecycleScope.launch {
            previewPanel?.setState(FilePreviewState(file = file, isLoading = true))
            
            val state = when (type) {
                PreviewType.TEXT -> loadTextPreview(file)
                PreviewType.IMAGE -> FilePreviewState(file = file, previewType = PreviewType.IMAGE)
                PreviewType.SVG -> FilePreviewState(file = file, previewType = PreviewType.SVG)
                PreviewType.PDF -> FilePreviewState(
                    file = file, 
                    previewType = PreviewType.PDF,
                    pdfPageCount = getPdfPageCount(file)
                )
                PreviewType.VIDEO -> FilePreviewState(file = file, previewType = PreviewType.VIDEO)
                PreviewType.AUDIO -> FilePreviewState(file = file, previewType = PreviewType.AUDIO)
                PreviewType.APK -> FilePreviewState(
                    file = file,
                    previewType = PreviewType.APK,
                    apkInfo = extractApkInfo(file)
                )
                PreviewType.ARCHIVE -> FilePreviewState(
                    file = file,
                    previewType = PreviewType.ARCHIVE,
                    archiveContents = listArchiveContents(file)
                )
                else -> FilePreviewState(file = file, previewType = PreviewType.UNSUPPORTED)
            }
            
            previewPanel?.setState(state.copy(isLoading = false))
        }
    }

    private suspend fun loadTextPreview(file: FileItem): FilePreviewState {
        return try {
            val content = withContext(kotlinx.coroutines.Dispatchers.IO) {
                File(file.absolutePath).readText().take(10000) // Лимит 10KB
            }
            // Простая подсветка
            val spans = if (file.isCode) highlightSyntax(content, file.extension) else emptyList()
            FilePreviewState(
                file = file,
                previewType = PreviewType.TEXT,
                textContent = content,
                highlightedSpans = spans
            )
        } catch (e: Exception) {
            FilePreviewState(file = file, previewType = PreviewType.UNSUPPORTED, error = e.message)
        }
    }

    private fun highlightSyntax(content: String, extension: String): List<com.nexus.agent.core.files.model.SyntaxSpan> {
        // Упрощённая подсветка — можно заменить на полноценный парсер
        val spans = mutableListOf<com.nexus.agent.core.files.model.SyntaxSpan>()
        val keywords = setOf("fun", "val", "var", "class", "interface", "object", "if", "else", "for", "while", "return", "import", "package", "override", "suspend", "lateinit")
        val types = setOf("String", "Int", "Long", "Boolean", "Float", "Double", "Unit", "Any", "List", "Map", "Set")
        
        val regex = Regex("\\b(${keywords.joinToString("|")})\\b|\\b(${types.joinToString("|")})\\b|\"[^\"]*\"|'[^']*'|//.*|\\d+")
        
        regex.findAll(content).forEach { match ->
            val style = when {
                match.value in keywords -> com.nexus.agent.core.files.model.SyntaxStyle.KEYWORD
                match.value in types -> com.nexus.agent.core.files.model.SyntaxStyle.TYPE
                match.value.startsWith("\"") || match.value.startsWith("'") -> com.nexus.agent.core.files.model.SyntaxStyle.STRING
                match.value.startsWith("//") -> com.nexus.agent.core.files.model.SyntaxStyle.COMMENT
                match.value.matches(Regex("\\d+")) -> com.nexus.agent.core.files.model.SyntaxStyle.NUMBER
                else -> com.nexus.agent.core.files.model.SyntaxStyle.PLAIN
            }
            spans.add(com.nexus.agent.core.files.model.SyntaxSpan(match.range.first, match.range.last + 1, style))
        }
        
        return spans
    }

    private fun getPdfPageCount(file: FileItem): Int {
        return try {
            val fd = android.os.ParcelFileDescriptor.open(File(file.absolutePath), android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(fd)
            val count = renderer.pageCount
            renderer.close()
            count
        } catch (_: Exception) { 0 }
    }

    private fun extractApkInfo(file: FileItem): com.nexus.agent.core.files.model.ApkInfo? {
        return try {
            val pm = requireContext().packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
            info?.applicationInfo?.let { appInfo ->
                appInfo.sourceDir = file.absolutePath
                appInfo.publicSourceDir = file.absolutePath
                com.nexus.agent.core.files.model.ApkInfo(
                    packageName = info.packageName ?: "unknown",
                    versionName = info.versionName ?: "unknown",
                    versionCode = info.longVersionCode,
                    appName = pm.getApplicationLabel(appInfo).toString()
                )
            }
        } catch (_: Exception) { null }
    }

    private fun listArchiveContents(file: FileItem): List<String> {
        return try {
            when (file.extension) {
                "zip" -> java.util.zip.ZipFile(file.absolutePath).entries().toList().map { it.name }
                else -> emptyList()
            }
        } catch (_: Exception) { emptyList() }
    }

    // ==================== FILE OPERATIONS ====================

    private fun showAddMenu() {
        val items = arrayOf("New Folder", "New File", "Import")
        AlertDialog.Builder(requireContext())
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showCreateFolderDialog()
                    1 -> showCreateFileDialog()
                    2 -> importLauncher.launch("*/*")
                }
            }
            .show()
    }

    private fun showCreateFolderDialog() {
        val editText = android.widget.EditText(requireContext()).apply {
            hint = "Folder name"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("New Folder")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val name = editText.text.toString()
                if (name.isNotBlank()) {
                    createFolder(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreateFileDialog() {
        val editText = android.widget.EditText(requireContext()).apply {
            hint = "File name"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("New File")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val name = editText.text.toString()
                if (name.isNotBlank()) {
                    createFile(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createFolder(name: String) {
        val path = _state.value.currentPath + File.separator + name
        File(path).mkdirs()
        refreshCurrentDirectory()
    }

    private fun createFile(name: String) {
        val path = _state.value.currentPath + File.separator + name
        File(path).createNewFile()
        refreshCurrentDirectory()
    }

    private fun importFile(uri: Uri) {
        // Копируем файл в текущую директорию
        val fileName = getFileNameFromUri(uri) ?: "imported_file"
        val destFile = File(_state.value.currentPath, fileName)
        
        