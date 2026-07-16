package com.nexus.agent.ui.sandbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nexus.agent.R
import com.nexus.agent.core.sandbox.CodeSandbox
import com.nexus.agent.core.sandbox.SandboxConfig
import com.nexus.agent.core.sandbox.SandboxResult
import com.nexus.agent.core.sandbox.LanguageRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main fragment for the Code Sandbox feature.
 * Provides an integrated environment for writing, executing, and monitoring code.
 */
class SandboxFragment : Fragment() {

    private lateinit var codeEditor: CodeEditorPanel
    private lateinit var outputConsole: OutputConsole
    private lateinit var resourceMonitor: ResourceMonitorView
    private lateinit var languageSelector: LanguageSelector
    private lateinit var runButton: ImageButton
    private lateinit var stopButton: ImageButton
    private lateinit var clearButton: ImageButton
    private lateinit var shareButton: ImageButton
    private lateinit var editorContainer: LinearLayout
    private lateinit var consoleContainer: LinearLayout

    private val sandbox = CodeSandbox()
    private var currentLanguage = LanguageRunner.Language.PYTHON
    private var isExecuting = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sandbox, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupListeners()
        setupSandbox()

        // Restore state if available
        savedInstanceState?.let {
            codeEditor.setText(it.getString(KEY_CODE, ""))
            currentLanguage = LanguageRunner.Language.valueOf(
                it.getString(KEY_LANGUAGE, "PYTHON")!!
            )
            languageSelector.selectLanguage(currentLanguage)
        }
    }

    private fun initViews(view: View) {
        codeEditor = view.findViewById(R.id.code_editor_panel)
        outputConsole = view.findViewById(R.id.output_console)
        resourceMonitor = view.findViewById(R.id.resource_monitor)
        languageSelector = view.findViewById(R.id.language_selector)
        runButton = view.findViewById(R.id.btn_run)
        stopButton = view.findViewById(R.id.btn_stop)
        stopButton.isEnabled = false
        clearButton = view.findViewById(R.id.btn_clear)
        shareButton = view.findViewById(R.id.btn_share)
        editorContainer = view.findViewById(R.id.editor_container)
        consoleContainer = view.findViewById(R.id.console_container)

        // Set initial language
        languageSelector.selectLanguage(currentLanguage)
    }

    private fun setupListeners() {
        languageSelector.setOnLanguageSelectedListener { language ->
            currentLanguage = language
            codeEditor.setLanguage(language)
            outputConsole.appendSystem("Language switched to ${language.displayName}\n")
        }

        runButton.setOnClickListener {
            if (!isExecuting) {
                executeCode()
            }
        }

        stopButton.setOnClickListener {
            stopExecution()
        }

        clearButton.setOnClickListener {
            outputConsole.clear()
            resourceMonitor.reset()
        }

        shareButton.setOnClickListener {
            shareCode()
        }

        codeEditor.setOnCodeChangedListener { code ->
            // Auto-save or validation could go here
        }
    }

    private fun setupSandbox() {
        val config = SandboxConfig(
            maxMemoryMb = 256,
            maxExecutionTimeMs = 30000,
            maxOutputLines = 1000,
            allowedFileOperations = false,
            networkAccess = false
        )
        sandbox.configure(config)
    }

    private fun executeCode() {
        val code = codeEditor.getText()
        if (code.isBlank()) {
            Toast.makeText(context, "Code is empty", Toast.LENGTH_SHORT).show()
            return
        }

        isExecuting = true
        updateExecutionState(true)
        outputConsole.clear()
        outputConsole.appendSystem("Executing ${currentLanguage.displayName} code...\n")
        resourceMonitor.startMonitoring()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = sandbox.execute(code, currentLanguage)

                withContext(Dispatchers.Main) {
                    handleExecutionResult(result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    outputConsole.appendError("Sandbox error: ${e.message}\n")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isExecuting = false
                    updateExecutionState(false)
                    resourceMonitor.stopMonitoring()
                }
            }
        }
    }

    private fun handleExecutionResult(result: SandboxResult) {
        when {
            result.isSuccess -> {
                outputConsole.appendOutput(result.stdout)
                if (result.stderr.isNotBlank()) {
                    outputConsole.appendError(result.stderr)
                }
                outputConsole.appendSystem(
                    "\nExecution completed in ${result.executionTimeMs}ms\n"
                )
            }
            result.isTimeout -> {
                outputConsole.appendError("\nExecution timed out after ${result.executionTimeMs}ms\n")
            }
            result.isMemoryExceeded -> {
                outputConsole.appendError("\nMemory limit exceeded (${result.memoryUsedMb}MB)\n")
            }
            else -> {
                outputConsole.appendError("\nExecution failed: ${result.errorMessage}\n")
            }
        }

        // Update resource monitor with final stats
        resourceMonitor.updateStats(
            cpuUsage = result.cpuUsagePercent,
            memoryUsage = result.memoryUsedMb,
            executionTime = result.executionTimeMs
        )
    }

    private fun stopExecution() {
        sandbox.terminate()
        outputConsole.appendSystem("\nExecution terminated by user\n")
        isExecuting = false
        updateExecutionState(false)
        resourceMonitor.stopMonitoring()
    }

    private fun shareCode() {
        val code = codeEditor.getText()
        if (code.isBlank()) return

        val shareText = "```${currentLanguage.fileExtension}\n$code\n```"

        val sendIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
        }

        val shareIntent = android.content.Intent.createChooser(sendIntent, "Share code")
        startActivity(shareIntent)
    }

    private fun updateExecutionState(running: Boolean) {
        runButton.isEnabled = !running
        runButton.alpha = if (running) 0.5f else 1.0f
        stopButton.isEnabled = running
        stopButton.alpha = if (running) 1.0f else 0.5f
        codeEditor.setEditable(!running)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CODE, codeEditor.getText())
        outState.putString(KEY_LANGUAGE, currentLanguage.name)
    }

    override fun onDestroy() {
        super.onDestroy()
        sandbox.cleanup()
    }

    companion object {
        private const val TAG = "SandboxFragment"
        private const val KEY_CODE = "sandbox_code"
        private const val KEY_LANGUAGE = "sandbox_language"

        fun newInstance(): SandboxFragment {
            return SandboxFragment()
        }
    }
}
