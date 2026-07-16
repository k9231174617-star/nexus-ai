package com.nexus.agent.ui.code

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.agent.R
import java.io.File
import java.util.UUID

/**
 * Много-вкладочный редактор с таб-баром и переключением между файлами.
 * Поддерживает закрытие, сохранение, индикаторы изменений и drag-and-drop табов.
 */
class MultiTabEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    data class Tab(
        val id: String = UUID.randomUUID().toString(),
        val filePath: String,
        val fileName: String,
        val language: String,
        var content: String,
        var isModified: Boolean = false,
        var isActive: Boolean = false
    )

    var onTabClose: ((Tab) -> Unit)? = null
    var onTabSwitch: ((Tab) -> Unit)? = null
    var onCodeModified: ((Tab, Boolean) -> Unit)? = null
    var onRequestAiAssist: ((String) -> Unit)? = null

    private val tabBar: HorizontalScrollView
    private val tabContainer: LinearLayout
    private val editorContainer: LinearLayout
    private val tabs = mutableListOf<Tab>()
    private val editors = mutableMapOf<String, CodeEditorView>()
    private var activeTabId: String? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_multi_tab_editor, this, true)

        tabBar = findViewById(R.id.tabBar)
        tabContainer = findViewById(R.id.tabContainer)
        editorContainer = findViewById(R.id.editorContainer)
    }

    fun openFile(file: File) {
        val existingTab = tabs.find { it.filePath == file.absolutePath }
        if (existingTab != null) {
            switchToTab(existingTab.id)
            return
        }

        val content = try {
            file.readText()
        } catch (e: Exception) {
            ""
        }

        val language = detectLanguage(file)
        val tab = Tab(
            filePath = file.absolutePath,
            fileName = file.name,
            language = language,
            content = content
        )

        tabs.add(tab)
        createEditorForTab(tab)
        createTabView(tab)
        switchToTab(tab.id)
    }

    fun openNewFile(name: String = "untitled", language: String = "kotlin") {
        val tab = Tab(
            filePath = "",
            fileName = name,
            language = language,
            content = ""
        )
        tabs.add(tab)
        createEditorForTab(tab)
        createTabView(tab)
        switchToTab(tab.id)
    }

    fun closeTab(tab: Tab) {
        val index = tabs.indexOf(tab)
        tabs.remove(tab)
        removeTabView(tab.id)
        removeEditor(tab.id)

        if (tab.id == activeTabId) {
            // Switch to nearest tab
            val newIndex = index.coerceAtMost(tabs.size - 1).coerceAtLeast(0)
            if (tabs.isNotEmpty()) {
                switchToTab(tabs[newIndex].id)
            } else {
                activeTabId = null
                showEmptyState()
            }
        }
    }

    fun closeTabByPath(path: String) {
        tabs.find { it.filePath == path }?.let { closeTab(it) }
    }

    fun saveTab(tab: Tab) {
        val editor = editors[tab.id] ?: return
        val content = editor.getCode()

        if (tab.filePath.isNotEmpty()) {
            try {
                File(tab.filePath).writeText(content)
                tab.content = content
                tab.isModified = false
                updateTabView(tab)
                onCodeModified?.invoke(tab, false)
            } catch (e: Exception) {
                // Show error
            }
        } else {
            // Show save dialog for new file
        }
    }

    fun saveAllTabs() {
        tabs.filter { it.isModified }.forEach { saveTab(it) }
    }

    fun getCurrentTab(): Tab? = tabs.find { it.id == activeTabId }

    fun insertAtCursor(text: String) {
        activeTabId?.let { editors[it]?.insertAtCursor(text) }
    }

    fun replaceSelection(text: String) {
        activeTabId?.let { editors[it]?.replaceSelection(text) }
    }

    private fun switchToTab(tabId: String) {
        // Deactivate current
        activeTabId?.let { oldId ->
            tabs.find { it.id == oldId }?.isActive = false
            editors[oldId]?.visibility = View.GONE
            updateTabViewAppearance(oldId, false)
        }

        // Activate new
        activeTabId = tabId
        val tab = tabs.find { it.id == tabId } ?: return
        tab.isActive = true
        editors[tabId]?.visibility = View.VISIBLE
        editors[tabId]?.requestFocus()
        updateTabViewAppearance(tabId, true)

        onTabSwitch?.invoke(tab)
    }

    private fun createTabView(tab: Tab) {
        val tabView = LayoutInflater.from(context).inflate(R.layout.item_editor_tab, tabContainer, false)
        tabView.tag = tab.id

        val tvName = tabView.findViewById<TextView>(R.id.tvTabName)
        val btnClose = tabView.findViewById<ImageButton>(R.id.btnTabClose)
        val ivModified = tabView.findViewById<View>(R.id.ivTabModified)

        tvName.text = tab.fileName
        ivModified.visibility = if (tab.isModified) View.VISIBLE else View.GONE

        tabView.setOnClickListener { switchToTab(tab.id) }

        btnClose.setOnClickListener {
            onTabClose?.invoke(tab)
        }

        tabContainer.addView(tabView)
    }

    private fun updateTabView(tab: Tab) {
        val tabView = tabContainer.findViewWithTag<View>(tab.id) ?: return
        val tvName = tabView.findViewById<TextView>(R.id.tvTabName)
        val ivModified = tabView.findViewById<View>(R.id.ivTabModified)

        tvName.text = tab.fileName
        ivModified.visibility = if (tab.isModified) View.VISIBLE else View.GONE
    }

    private fun updateTabViewAppearance(tabId: String, isActive: Boolean) {
        val tabView = tabContainer.findViewWithTag<View>(tabId) ?: return
        tabView.setBackgroundColor(
            if (isActive) ContextCompat.getColor(context, R.color.bg_tab_active)
            else ContextCompat.getColor(context, R.color.bg_tab_inactive)
        )
    }

    private fun removeTabView(tabId: String) {
        val tabView = tabContainer.findViewWithTag<View>(tabId) ?: return
        tabContainer.removeView(tabView)
    }

    private fun createEditorForTab(tab: Tab) {
        val editor = CodeEditorView(context).apply {
            setLanguage(tab.language)
            setCode(tab.content)
            visibility = View.GONE

            onTextChange = { newText ->
                tab.content = newText
                val wasModified = tab.isModified
                tab.isModified = true
                if (!wasModified) {
                    updateTabView(tab)
                    onCodeModified?.invoke(tab, true)
                }
            }

            onSelectionChange = { start, end ->
                // Update status bar
            }

            onRequestAiAssist = { code ->
                onRequestAiAssist?.invoke(code)
            }
        }

        editors[tab.id] = editor
        editorContainer.addView(editor, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private fun removeEditor(tabId: String) {
        editors[tabId]?.let {
            editorContainer.removeView(it)
            editors.remove(tabId)
        }
    }

    private fun showEmptyState() {
        // Show "No file open" message
    }

    private fun detectLanguage(file: File): String {
        return when (file.extension.lowercase()) {
            "kt" -> "kotlin"
            "java" -> "java"
            "xml" -> "xml"
            "json" -> "json"
            "md", "markdown" -> "markdown"
            "smali" -> "smali"
            "py" -> "python"
            "js" -> "javascript"
            "ts" -> "typescript"
            "c", "cpp", "h" -> "cpp"
            "rs" -> "rust"
            "go" -> "go"
            else -> "text"
        }
    }
}
