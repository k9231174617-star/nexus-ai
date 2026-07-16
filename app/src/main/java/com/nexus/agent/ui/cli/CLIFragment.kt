package com.nexus.agent.ui.cli

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexus.agent.core.cli.CLIExecutor
import com.nexus.agent.core.cli.ShellSession
import com.nexus.agent.core.cli.CommandParser
import com.nexus.agent.core.cli.PermissionHandler
import com.nexus.agent.core.cli.AutocompleteEngine
import com.nexus.agent.core.cli.CommandHistory
import com.nexus.agent.databinding.FragmentCliBinding
import com.nexus.agent.ui.common.ToastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CLIFragment — main terminal interface for executing shell commands.
 * Supports both standard Android shell and root commands via JNI bridge.
 */
class CLIFragment : Fragment() {

    private var _binding: FragmentCliBinding? = null
    private val binding get() = _binding!!

    private lateinit var cliExecutor: CLIExecutor
    private lateinit var shellSession: ShellSession
    private lateinit var commandParser: CommandParser
    private lateinit var permissionHandler: PermissionHandler
    private lateinit var autocompleteEngine: AutocompleteEngine
    private lateinit var commandHistory: CommandHistory

    private val outputBuilder = SpannableStringBuilder()
    private var isRootMode = false
    private var currentDirectory = "/data/data/com.nexus.agent/files"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            ToastManager.show(requireContext(), "Storage permission granted")
        } else {
            ToastManager.showError(requireContext(), "Storage permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cliExecutor = CLIExecutor(requireContext())
        shellSession = ShellSession()
        commandParser = CommandParser()
        permissionHandler = PermissionHandler(requireContext())
        autocompleteEngine = AutocompleteEngine(requireContext())
        commandHistory = CommandHistory(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCliBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTerminalView()
        setupCommandInput()
        setupQuickCommandsBar()
        setupRootBadge()
        checkPermissions()
        printWelcomeMessage()
    }

    private fun setupTerminalView() {
        binding.terminalView.setOnLinkClickListener { link ->
            when {
                link.startsWith("cd ") -> executeCommand(link)
                link.startsWith("cat ") -> executeCommand(link)
                link.startsWith("nano ") -> openFileInEditor(link.removePrefix("nano ").trim())
                else -> executeCommand(link)
            }
        }
    }

    private fun setupCommandInput() {
        binding.commandInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                val command = binding.commandInput.text.toString().trim()
                if (command.isNotEmpty()) {
                    executeCommand(command)
                    commandHistory.add(command)
                    binding.commandInput.text?.clear()
                }
                true
            } else {
                false
            }
        }

        binding.commandInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        binding.commandInput.setText(commandHistory.getPrevious())
                        binding.commandInput.setSelection(binding.commandInput.text?.length ?: 0)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        binding.commandInput.setText(commandHistory.getNext())
                        binding.commandInput.setSelection(binding.commandInput.text?.length ?: 0)
                        true
                    }
                    KeyEvent.KEYCODE_TAB -> {
                        showAutocompleteSuggestions()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }

        binding.btnSend.setOnClickListener {
            val command = binding.commandInput.text.toString().trim()
            if (command.isNotEmpty()) {
                executeCommand(command)
                commandHistory.add(command)
                binding.commandInput.text?.clear()
            }
        }
    }

    private fun setupQuickCommandsBar() {
        binding.quickCommandsBar.setOnCommandClickListener { command ->
            when (command) {
                QuickCommandsBar.Command.CLEAR -> clearTerminal()
                QuickCommandsBar.Command.LS -> executeCommand("ls -la")
                QuickCommandsBar.Command.PWD -> executeCommand("pwd")
                QuickCommandsBar.Command.CD_UP -> executeCommand("cd ..")
                QuickCommandsBar.Command.GREP -> {
                    binding.commandInput.setText("grep -r \"\" .")
                    binding.commandInput.setSelection(7)
                }
                QuickCommandsBar.Command.CHMOD -> {
                    binding.commandInput.setText("chmod 755 ")
                    binding.commandInput.setSelection(10)
                }
                QuickCommandsBar.Command.PS -> executeCommand("ps -A")
                QuickCommandsBar.Command.TOP -> executeCommand("top -n 1")
                QuickCommandsBar.Command.IFCONFIG -> executeCommand("ifconfig")
                QuickCommandsBar.Command.NETSTAT -> executeCommand("netstat -tuln")
                QuickCommandsBar.Command.ADB -> executeCommand("adb devices")
                QuickCommandsBar.Command.LOGCAT -> executeCommand("logcat -d | tail -50")
                QuickCommandsBar.Command.DUMPSYS -> executeCommand("dumpsys battery")
                QuickCommandsBar.Command.PM_LIST -> executeCommand("pm list packages")
                QuickCommandsBar.Command.AM_START -> {
                    binding.commandInput.setText("am start -n com.package/.Activity")
                    binding.commandInput.setSelection(8)
                }
            }
        }
    }

    private fun setupRootBadge() {
        binding.rootBadgeView.setOnClickListener {
            toggleRootMode()
        }
        updateRootBadge()
    }

    private fun toggleRootMode() {
        if (!isRootMode) {
            if (!cliExecutor.hasRootAccess()) {
                ToastManager.showError(requireContext(), "Root access not available")
                return
            }
        }
        isRootMode = !isRootMode
        updateRootBadge()
        val modeText = if (isRootMode) "ROOT MODE ENABLED" else "USER MODE"
        appendOutput("\n=== $modeText ===\n", OutputType.SYSTEM)
    }

    private fun updateRootBadge() {
        binding.rootBadgeView.setRootActive(isRootMode)
        binding.rootBadgeView.setHasRoot(cliExecutor.hasRootAccess())
    }

    private fun executeCommand(command: String) {
        appendOutput("$ $command\n", OutputType.COMMAND)

        viewLifecycleOwner.lifecycleScope.launch {
            showLoading(true)
            try {
                val result = withContext(Dispatchers.IO) {
                    if (isRootMode) {
                        cliExecutor.executeAsRoot(command, currentDirectory)
                    } else {
                        cliExecutor.execute(command, currentDirectory)
                    }
                }

                when {
                    result.stdout.isNotEmpty() -> {
                        appendOutput(result.stdout + "\n", OutputType.STDOUT)
                    }
                    result.stderr.isNotEmpty() -> {
                        appendOutput(result.stderr + "\n", OutputType.STDERR)
                    }
                }

                if (result.exitCode != 0) {
                    appendOutput("Exit code: ${result.exitCode}\n", OutputType.ERROR)
                }

                // Update current directory if cd command
                if (command.startsWith("cd ")) {
                    val newDir = command.removePrefix("cd ").trim()
                    currentDirectory = resolvePath(newDir)
                }

                binding.tvCurrentPath.text = currentDirectory

            } catch (e: Exception) {
                appendOutput("Error: ${e.message}\n", OutputType.ERROR)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun resolvePath(path: String): String {
        return when {
            path.startsWith("/") -> path
            path == ".." -> currentDirectory.substringBeforeLast("/", "/")
            path == "~" -> "/data/data/com.nexus.agent/files"
            else -> "$currentDirectory/$path"
        }
    }

    private fun showAutocompleteSuggestions() {
        val partial = binding.commandInput.text.toString()
        val suggestions = autocompleteEngine.getSuggestions(partial, currentDirectory)
        if (suggestions.isNotEmpty()) {
            val dialog = AutocompleteDialog(requireContext(), suggestions) { selected ->
                binding.commandInput.setText(selected)
                binding.commandInput.setSelection(selected.length)
            }
            dialog.show()
        }
    }

    private fun openFileInEditor(path: String) {
        val bundle = Bundle().apply {
            putString("file_path", resolvePath(path))
        }
        findNavController().navigate(R.id.action_cliFragment_to_codeEditor, bundle)
    }

    private fun clearTerminal() {
        outputBuilder.clear()
        binding.terminalView.setText("")
        printWelcomeMessage()
    }

    private fun printWelcomeMessage() {
        val welcome = buildString {
            append("Nexus AI Terminal v1.0\n")
            append("Type 'help' for available commands\n")
            append("Current directory: $currentDirectory\n")
            append("Use ↑↓ for command history, Tab for autocomplete\n")
            append("Root access: ${if (cliExecutor.hasRootAccess()) "available" else "not available"}\n")
            append("----------------------------------------\n")
        }
        appendOutput(welcome, OutputType.SYSTEM)
    }

    private fun appendOutput(text: String, type: OutputType) {
        val color = when (type) {
            OutputType.COMMAND -> ContextCompat.getColor(requireContext(), R.color.terminal_command)
            OutputType.STDOUT -> ContextCompat.getColor(requireContext(), R.color.terminal_stdout)
            OutputType.STDERR -> ContextCompat.getColor(requireContext(), R.color.terminal_stderr)
            OutputType.ERROR -> ContextCompat.getColor(requireContext(), R.color.terminal_error)
            OutputType.SYSTEM -> ContextCompat.getColor(requireContext(), R.color.terminal_system)
        }

        val start = outputBuilder.length
        outputBuilder.append(text)
        outputBuilder.setSpan(
            ForegroundColorSpan(color),
            start,
            outputBuilder.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        binding.terminalView.text = outputBuilder
        binding.terminalScroll.post {
            binding.terminalScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun checkPermissions() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                ToastManager.show(requireContext(), "Storage access needed for file operations")
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shellSession.close()
        _binding = null
    }

    enum class OutputType {
        COMMAND, STDOUT, STDERR, ERROR, SYSTEM
    }
}
