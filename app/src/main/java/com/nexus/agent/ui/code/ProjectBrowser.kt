package com.nexus.agent.ui.code

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nexus.agent.R
import java.io.File

/**
 * Браузер проектов с древовидным отображением файлов.
 * Поддерживает сворачивание/разворачивание директорий, поиск, фильтрацию.
 */
class ProjectBrowser @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onFileClick: ((File) -> Unit)? = null
    var onFileLongClick: ((File, View) -> Unit)? = null
    var onDirectoryToggle: ((File, Boolean) -> Unit)? = null
    var onApkClick: ((String) -> Unit)? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvProjectName: TextView
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnCollapseAll: ImageButton
    private lateinit var btnSearch: ImageButton
    private lateinit var searchBar: androidx.appcompat.widget.SearchView

    private val fileTreeAdapter: FileTreeAdapter
    private var rootDirectory: File? = null
    private val expandedDirs = mutableSetOf<String>()
    private val modifiedFiles = mutableSetOf<String>()
    private var currentFilter: String = ""

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_project_browser, this, true)
        initViews()
        setupRecyclerView()
    }

    private fun initViews() {
        tvProjectName = findViewById(R.id.tvProjectName)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnCollapseAll = findViewById(R.id.btnCollapseAll)
        btnSearch = findViewById(R.id.btnSearch)
        searchBar = findViewById(R.id.searchBar)
        recyclerView = findViewById(R.id.recyclerFileTree)

        btnRefresh.setOnClickListener { refresh() }
        btnCollapseAll.setOnClickListener { collapseAll() }
        btnSearch.setOnClickListener { toggleSearch() }

        searchBar.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                currentFilter = newText ?: ""
                applyFilter()
                return true
            }
        })
    }

    private fun setupRecyclerView() {
        fileTreeAdapter = FileTreeAdapter(
            onFileClick = { file -> onFileClick?.invoke(file) },
            onFileLongClick = { file, view -> onFileLongClick?.invoke(file, view) },
            onDirectoryClick = { dir -> toggleDirectory(dir) },
            isExpanded = { dir -> expandedDirs.contains(dir.absolutePath) },
            isModified = { file -> modifiedFiles.contains(file.absolutePath) }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = fileTreeAdapter
        }
    }

    fun loadProject(path: String) {
        rootDirectory = File(path)
        tvProjectName.text = rootDirectory?.name ?: "No Project"
        expandedDirs.clear()
        rootDirectory?.let { expandedDirs.add(it.absolutePath) }
        refresh()
    }

    fun refresh() {
        rootDirectory?.let { root ->
            val items = buildFileTree(root, 0)
            fileTreeAdapter.submitList(items)
        }
    }

    private fun buildFileTree(dir: File, depth: Int): List<FileTreeAdapter.FileItem> {
        val items = mutableListOf<FileTreeAdapter.FileItem>()

        val children = dir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }
                .thenBy { it.name.lowercase() }
        ) ?: return items

        for (child in children) {
            val isExpanded = expandedDirs.contains(child.absolutePath)
            items.add(FileTreeAdapter.FileItem(child, depth, isExpanded))

            if (child.isDirectory && isExpanded) {
                items.addAll(buildFileTree(child, depth + 1))
            }
        }

        return items
    }

    fun expandDirectory(dir: File) {
        expandedDirs.add(dir.absolutePath)
        refresh()
    }

    fun collapseDirectory(dir: File) {
        // Collapse this and all nested
        expandedDirs.removeAll { it.startsWith(dir.absolutePath) }
        refresh()
    }

    private fun toggleDirectory(dir: File) {
        val path = dir.absolutePath
        if (expandedDirs.contains(path)) {
            expandedDirs.remove(path)
            onDirectoryToggle?.invoke(dir, false)
        } else {
            expandedDirs.add(path)
            onDirectoryToggle?.invoke(dir, true)
        }
        refresh()
    }

    fun collapseAll() {
        rootDirectory?.let { expandedDirs.retainAll { it == it } } // Keep only root
        refresh()
    }

    fun markFileModified(path: String, modified: Boolean) {
        if (modified) modifiedFiles.add(path) else modifiedFiles.remove(path)
        refresh()
    }

    fun highlightFile(path: String) {
        // Scroll to and highlight file in tree
        val position = fileTreeAdapter.currentList.indexOfFirst { it.file.absolutePath == path }
        if (position >= 0) {
            recyclerView.scrollToPosition(position)
            fileTreeAdapter.setHighlightedPosition(position)
        }
    }

    private fun toggleSearch() {
        searchBar.visibility = if (searchBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun applyFilter() {
        if (currentFilter.isBlank()) {
            refresh()
            return
        }

        rootDirectory?.let { root ->
            val allFiles = mutableListOf<FileTreeAdapter.FileItem>()
            collectAllFiles(root, 0, allFiles)

            val filtered = allFiles.filter {
                it.file.name.contains(currentFilter, ignoreCase = true)
            }
            fileTreeAdapter.submitList(filtered)
        }
    }

    private fun collectAllFiles(dir: File, depth: Int, list: MutableList<FileTreeAdapter.FileItem>) {
        val children = dir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }
                .thenBy { it.name.lowercase() }
        ) ?: return

        for (child in children) {
            list.add(FileTreeAdapter.FileItem(child, depth, false))
            if (child.isDirectory) {
                collectAllFiles(child, depth + 1, list)
            }
        }
    }

    fun getRootDirectory(): File? = rootDirectory
}
