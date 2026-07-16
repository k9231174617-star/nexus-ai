package com.nexus.agent.ui.code

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.nexus.agent.R
import com.nexus.agent.ui.common.CodeAIDrawer

/**
 * Корневой фрагмент Code Agent.
 * Управляет layout с боковой панелью (ProjectBrowser), много-вкладочным редактором,
 * и нижним/боковым AI-ассистентом (CodeAIDrawer).
 */
class CodeAgentFragment : Fragment() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var projectBrowser: ProjectBrowser
    private lateinit var multiTabEditor: MultiTabEditor
    private lateinit var btnToggleBrowser: ImageButton
    private lateinit var btnNewFile: ImageButton
    private lateinit var btnSaveAll: ImageButton
    private lateinit var btnRunCode: ImageButton
    private lateinit var btnAiAssist: ImageButton
    private lateinit var codeAiDrawer: CodeAIDrawer
    private lateinit var apkWorkspaceContainer: LinearLayout

    companion object {
        fun newInstance() = CodeAgentFragment()
        private const val DRAWER_GRAVITY = GravityCompat.START
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_code_agent, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupProjectBrowser()
        setupMultiTabEditor()
        setupToolbar()
        setupCodeAiDrawer()
    }

    private fun initViews(view: View) {
        drawerLayout = view.findViewById(R.id.drawerLayout)
        projectBrowser = view.findViewById(R.id.projectBrowser)
        multiTabEditor = view.findViewById(R.id.multiTabEditor)
        btnToggleBrowser = view.findViewById(R.id.btnToggleBrowser)
        btnNewFile = view.findViewById(R.id.btnNewFile)
        btnSaveAll = view.findViewById(R.id.btnSaveAll)
        btnRunCode = view.findViewById(R.id.btnRunCode)
        btnAiAssist = view.findViewById(R.id.btnAiAssist)
        codeAiDrawer = view.findViewById(R.id.codeAiDrawer)
        apkWorkspaceContainer = view.findViewById(R.id.apkWorkspaceContainer)
    }

    private fun setupProjectBrowser() {
        projectBrowser.apply {
            onFileClick = { file ->
                multiTabEditor.openFile(file)
            }
            onFileLongClick = { file, view ->
                showFileContextMenu(file, view)
            }
            onDirectoryToggle = { dir, isExpanded ->
                if (isExpanded) projectBrowser.expandDirectory(dir)
                else projectBrowser.collapseDirectory(dir)
            }
            onApkClick = { apkFile ->
                openApkWorkspace(apkFile)
            }
        }

        // Загрузка начального проекта
        projectBrowser.loadProject("/sdcard/NexusAI/Projects")
    }

    private fun setupMultiTabEditor() {
        multiTabEditor.apply {
            onTabClose = { tab -> closeTab(tab) }
            onTabSwitch = { tab -> onEditorTabSwitched(tab) }
            onCodeModified = { tab, isModified ->
                projectBrowser.markFileModified(tab.filePath, isModified)
            }
            onRequestAiAssist = { selectedCode ->
                codeAiDrawer.analyzeCode(selectedCode)
                codeAiDrawer.expand()
            }
        }
    }

    private fun setupToolbar() {
        btnToggleBrowser.setOnClickListener {
            if (drawerLayout.isDrawerOpen(DRAWER_GRAVITY)) {
                drawerLayout.closeDrawer(DRAWER_GRAVITY)
            } else {
                drawerLayout.openDrawer(DRAWER_GRAVITY)
            }
        }

        btnNewFile.setOnClickListener {
            showNewFileDialog()
        }

        btnSaveAll.setOnClickListener {
            multiTabEditor.saveAllTabs()
            projectBrowser.refresh()
        }

        btnRunCode.setOnClickListener {
            runCurrentFile()
        }

        btnAiAssist.setOnClickListener {
            codeAiDrawer.toggle()
        }
    }

    private fun setupCodeAiDrawer() {
        codeAiDrawer.apply {
            onInsertCode = { code ->
                multiTabEditor.insertAtCursor(code)
            }
            onReplaceSelection = { code ->
                multiTabEditor.replaceSelection(code)
            }
            onExplainRequest = { code ->
                // Запрос к LLM на объяснение
                explainCodeWithAI(code)
            }
            onRefactorRequest = { code, instruction ->
                refactorCodeWithAI(code, instruction)
            }
        }
    }

    private fun openApkWorkspace(apkPath: String) {
        apkWorkspaceContainer.visibility = View.VISIBLE
        val apkWorkspace = APKWorkspace(requireContext()).apply {
            loadApk(apkPath)
            onClose = {
                apkWorkspaceContainer.visibility = View.GONE
                apkWorkspaceContainer.removeAllViews()
            }
            onFileSelect = { smaliFile ->
                multiTabEditor.openFile(smaliFile)
            }
        }
        apkWorkspaceContainer.removeAllViews()
        apkWorkspaceContainer.addView(apkWorkspace)
    }

    private fun showFileContextMenu(file: java.io.File, anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_file_context, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rename -> { showRenameDialog(file); true }
                R.id.action_delete -> { deleteFile(file); true }
                R.id.action_copy_path -> { copyPath(file); true }
                R.id.action_ai_analyze -> {
                    codeAiDrawer.analyzeFile(file)
                    codeAiDrawer.expand()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showNewFileDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("New File")
            .setView(R.layout.dialog_new_file)
            .setPositiveButton("Create") { _, _ ->
                // Создание файла
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun closeTab(tab: MultiTabEditor.Tab) {
        if (tab.isModified) {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Unsaved Changes")
                .setMessage("Save changes to ${tab.fileName}?")
                .setPositiveButton("Save") { _, _ ->
                    multiTabEditor.saveTab(tab)
                    multiTabEditor.closeTab(tab)
                }
                .setNegativeButton("Discard") { _, _ ->
                    multiTabEditor.closeTab(tab)
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            multiTabEditor.closeTab(tab)
        }
    }

    private fun onEditorTabSwitched(tab: MultiTabEditor.Tab) {
        projectBrowser.highlightFile(tab.filePath)
        codeAiDrawer.setCurrentFile(tab.filePath)
    }

    private fun runCurrentFile() {
        val currentTab = multiTabEditor.getCurrentTab() ?: return
        val sandbox = com.nexus.agent.core.sandbox.CodeSandbox(requireContext())
        sandbox.execute(currentTab.language, currentTab.content) { result ->
            // Показать результат в панели вывода
        }
    }

    private fun explainCodeWithAI(code: String) {
        val prompt = "Explain this code:\n```\n$code\n```"
        // Отправка в LLMBridge
    }

    private fun refactorCodeWithAI(code: String, instruction: String) {
        val prompt = "Refactor this code: $instruction\n```\n$code\n```"
        // Отправка в LLMBridge
    }

    private fun deleteFile(file: java.io.File) {
        if (file.delete()) {
            projectBrowser.refresh()
            multiTabEditor.closeTabByPath(file.absolutePath)
        }
    }

    private fun copyPath(file: java.io.File) {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("path", file.absolutePath))
    }

    private fun showRenameDialog(file: java.io.File) {
        // Диалог переименования
    }

    override fun onPause() {
        super.onPause()
        multiTabEditor.saveAllTabs()
    }
}
