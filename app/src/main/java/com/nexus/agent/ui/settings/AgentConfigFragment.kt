package com.nexus.agent.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nexus.agent.R
import com.nexus.agent.core.context.ContextManager
import com.nexus.agent.core.planner.TaskPlanner
import com.nexus.agent.databinding.FragmentAgentConfigBinding
import com.nexus.agent.ui.common.RangeSliderView
import com.nexus.agent.ui.common.ToastManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AgentConfigFragment : Fragment() {

    private var _binding: FragmentAgentConfigBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var contextManager: ContextManager
    @Inject lateinit var taskPlanner: TaskPlanner

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAgentConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        loadCurrentConfig()
    }

    private fun setupUI() {
        binding.apply {
            toolbar.title = "Agent Configuration"
            toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

            // Personality selector
            chipGroupPersonality.setOnCheckedStateChangeListener { _, checkedIds ->
                val personality = when (checkedIds.firstOrNull()) {
                    R.id.chipBalanced -> AgentPersonality.BALANCED
                    R.id.chipCreative -> AgentPersonality.CREATIVE
                    R.id.chipPrecise -> AgentPersonality.PRECISE
                    R.id.chipConcise -> AgentPersonality.CONCISE
                    else -> AgentPersonality.BALANCED
                }
                updatePersonalityDescription(personality)
            }

            // Tool toggles
            switchFileAccess.setOnCheckedChangeListener { _, checked ->
                binding.tvFileAccessStatus.text = if (checked) "Enabled" else "Disabled"
            }

            switchShellAccess.setOnCheckedChangeListener { _, checked ->
                binding.tvShellAccessStatus.text = if (checked) "Enabled (Root required for system)" else "Disabled"
            }

            switchBrowserAccess.setOnCheckedChangeListener { _, checked ->
                binding.tvBrowserAccessStatus.text = if (checked) "Enabled" else "Disabled"
            }

            switchCodeExecution.setOnCheckedChangeListener { _, checked ->
                binding.tvCodeExecutionStatus.text = if (checked) "Sandboxed execution" else "Disabled"
                binding.sliderSandboxTimeout.isEnabled = checked
            }

            // Context depth slider
            sliderContextDepth.setLabelFormatter { value ->
                when (value.toInt()) {
                    1 -> "Minimal"
                    2 -> "Low"
                    3 -> "Normal"
                    4 -> "High"
                    5 -> "Maximum"
                    else -> value.toInt().toString()
                }
            }

            // Auto-planning toggle
            switchAutoPlan.setOnCheckedChangeListener { _, checked ->
                binding.cardPlanningOptions.visibility = if (checked) View.VISIBLE else View.GONE
            }

            // Save button
            btnSaveAgentConfig.setOnClickListener { saveConfiguration() }

            // Reset to defaults
            btnResetAgentConfig.setOnClickListener { resetToDefaults() }

            // Advanced options
            btnAdvancedOptions.setOnClickListener {
                binding.cardAdvancedOptions.visibility = 
                    if (binding.cardAdvancedOptions.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
    }

    private fun loadCurrentConfig() {
        lifecycleScope.launch {
            val config = contextManager.getAgentConfig()
            binding.apply {
                // Personality
                val chipId = when (config.personality) {
                    AgentPersonality.BALANCED -> R.id.chipBalanced
                    AgentPersonality.CREATIVE -> R.id.chipCreative
                    AgentPersonality.PRECISE -> R.id.chipPrecise
                    AgentPersonality.CONCISE -> R.id.chipConcise
                }
                chipGroupPersonality.check(chipId)

                // Tools
                switchFileAccess.isChecked = config.toolsEnabled.fileAccess
                switchShellAccess.isChecked = config.toolsEnabled.shellAccess
                switchBrowserAccess.isChecked = config.toolsEnabled.browserAccess
                switchCodeExecution.isChecked = config.toolsEnabled.codeExecution

                // Context
                sliderContextDepth.value = config.contextDepth.toFloat()

                // Planning
                switchAutoPlan.isChecked = config.autoPlanning
                switchConfirmDestructive.isChecked = config.confirmDestructive
                switchParallelExecution.isChecked = config.parallelExecution

                // Advanced
                switchDebugMode.isChecked = config.debugMode
                switchVerboseLogging.isChecked = config.verboseLogging
                etCustomPersona.setText(config.customPersona ?: "")
            }
        }
    }

    private fun updatePersonalityDescription(personality: AgentPersonality) {
        val description = when (personality) {
            AgentPersonality.BALANCED -> "Balanced between creativity and precision. Good for general tasks."
            AgentPersonality.CREATIVE -> "More imaginative and exploratory responses. Good for brainstorming."
            AgentPersonality.PRECISE -> "Focuses on accuracy and detail. Good for technical tasks."
            AgentPersonality.CONCISE -> "Short, direct responses. Good for quick queries."
        }
        binding.tvPersonalityDescription.text = description
    }

    private fun saveConfiguration() {
        lifecycleScope.launch {
            try {
                val config = AgentConfig(
                    personality = when (binding.chipGroupPersonality.checkedChipId) {
                        R.id.chipCreative -> AgentPersonality.CREATIVE
                        R.id.chipPrecise -> AgentPersonality.PRECISE
                        R.id.chipConcise -> AgentPersonality.CONCISE
                        else -> AgentPersonality.BALANCED
                    },
                    toolsEnabled = ToolPermissions(
                        fileAccess = binding.switchFileAccess.isChecked,
                        shellAccess = binding.switchShellAccess.isChecked,
                        browserAccess = binding.switchBrowserAccess.isChecked,
                        codeExecution = binding.switchCodeExecution.isChecked
                    ),
                    contextDepth = binding.sliderContextDepth.value.toInt(),
                    autoPlanning = binding.switchAutoPlan.isChecked,
                    confirmDestructive = binding.switchConfirmDestructive.isChecked,
                    parallelExecution = binding.switchParallelExecution.isChecked,
                    debugMode = binding.switchDebugMode.isChecked,
                    verboseLogging = binding.switchVerboseLogging.isChecked,
                    customPersona = binding.etCustomPersona.text?.toString()?.takeIf { it.isNotBlank() }
                )

                contextManager.updateAgentConfig(config)
                ToastManager.success(binding.root, "Agent configuration saved")
            } catch (e: Exception) {
                ToastManager.error(binding.root, "Failed to save: ${e.message}")
            }
        }
    }

    private fun resetToDefaults() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Reset Agent Config?")
            .setMessage("Restore default agent behavior settings?")
            .setPositiveButton("Reset") { _, _ ->
                lifecycleScope.launch {
                    contextManager.resetAgentConfig()
                    loadCurrentConfig()
                    ToastManager.success(binding.root, "Reset to defaults")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Data classes (normally in domain/model layer)
enum class AgentPersonality { BALANCED, CREATIVE, PRECISE, CONCISE }

data class AgentConfig(
    val personality: AgentPersonality,
    val toolsEnabled: ToolPermissions,
    val contextDepth: Int,
    val autoPlanning: Boolean,
    val confirmDestructive: Boolean,
    val parallelExecution: Boolean,
    val debugMode: Boolean,
    val verboseLogging: Boolean,
    val customPersona: String?
)

data class ToolPermissions(
    val fileAccess: Boolean,
    val shellAccess: Boolean,
    val browserAccess: Boolean,
    val codeExecution: Boolean
)
