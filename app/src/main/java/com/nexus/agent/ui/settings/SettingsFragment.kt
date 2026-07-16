package com.nexus.agent.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexus.agent.R
import com.nexus.agent.core.chat.ChatEngine
import com.nexus.agent.core.llm.LLMBridge
import com.nexus.agent.core.memory.AgentMemory
import com.nexus.agent.databinding.FragmentSettingsBinding
import com.nexus.agent.ui.common.ToastManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var llmBridge: LLMBridge
    @Inject lateinit var agentMemory: AgentMemory
    @Inject lateinit var chatEngine: ChatEngine

    private lateinit var settingsAdapter: SettingsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeSystemState()
    }

    private fun setupUI() {
        binding.apply {
            toolbar.title = getString(R.string.settings_title)
            toolbar.setNavigationIcon(R.drawable.ic_settings)
            
            settingsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            settingsAdapter = SettingsAdapter { item -> onSettingsItemClick(item) }
            settingsRecyclerView.adapter = settingsAdapter

            // Quick action buttons
            btnClearCache.setOnClickListener { clearAppCache() }
            btnExportData.setOnClickListener { exportUserData() }
            btnResetSettings.setOnClickListener { showResetConfirmation() }
            
            // System info panel
            systemInfoPanel.setOnClickListener { 
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container, SystemInfoPanel.newInstance())
                    .addToBackStack(null)
                    .commit()
            }
        }

        loadSettingsCategories()
    }

    private fun loadSettingsCategories() {
        val categories = listOf(
            SettingsCategory(
                id = "llm_config",
                title = "LLM Configuration",
                subtitle = "API keys, models, routing",
                iconRes = R.drawable.ic_main_agent,
                fragmentClass = LLMConfigFragment::class.java
            ),
            SettingsCategory(
                id = "agent_config",
                title = "Agent Behavior",
                subtitle = "Personality, tools, permissions",
                iconRes = R.drawable.ic_universal,
                fragmentClass = AgentConfigFragment::class.java
            ),
            SettingsCategory(
                id = "memory",
                title = "Memory & Context",
                subtitle = "Vector store, embeddings, pruning",
                iconRes = R.drawable.ic_memory,
                fragmentClass = null // Opens memory settings dialog
            ),
            SettingsCategory(
                id = "appearance",
                title = "Appearance",
                subtitle = "Theme, animations, neon effects",
                iconRes = R.drawable.ic_nexus_logo,
                fragmentClass = null
            ),
            SettingsCategory(
                id = "notifications",
                title = "Notifications",
                subtitle = "Push, sounds, vibration",
                iconRes = R.drawable.ic_nexus_logo,
                fragmentClass = null
            ),
            SettingsCategory(
                id = "privacy",
                title = "Privacy & Security",
                subtitle = "Data handling, encryption, logs",
                iconRes = R.drawable.ic_cli,
                fragmentClass = null
            ),
            SettingsCategory(
                id = "about",
                title = "About Nexus AI",
                subtitle = "Version, licenses, contributors",
                iconRes = R.drawable.ic_nexus_logo,
                fragmentClass = null
            )
        )
        settingsAdapter.submitList(categories)
    }

    private fun onSettingsItemClick(item: SettingsCategory) {
        when (item.id) {
            "llm_config" -> navigateTo(LLMConfigFragment())
            "agent_config" -> navigateTo(AgentConfigFragment())
            "memory" -> showMemorySettingsDialog()
            "appearance" -> showAppearanceSettings()
            "notifications" -> openNotificationSettings()
            "privacy" -> showPrivacySettings()
            "about" -> showAboutDialog()
        }
    }

    private fun navigateTo(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun observeSystemState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    chatEngine.connectionState.collectLatest { state ->
                        binding.connectionStatusIndicator.setConnectionState(state)
                    }
                }
                launch {
                    agentMemory.getStorageStats().collectLatest { stats ->
                        binding.memoryUsageText.text = 
                            "Memory: ${stats.usedMB}MB / ${stats.totalMB}MB"
                    }
                }
            }
        }
    }

    private fun clearAppCache() {
        lifecycleScope.launch {
            try {
                requireContext().cacheDir.deleteRecursively()
                agentMemory.pruneCache()
                ToastManager.success(binding.root, "Cache cleared successfully")
            } catch (e: Exception) {
                ToastManager.error(binding.root, "Failed to clear cache: ${e.message}")
            }
        }
    }

    private fun exportUserData() {
        lifecycleScope.launch {
            try {
                val exportUri = agentMemory.exportAllData(requireContext())
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, exportUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Nexus AI Data Export")
                }
                startActivity(Intent.createChooser(shareIntent, "Export Data"))
            } catch (e: Exception) {
                ToastManager.error(binding.root, "Export failed: ${e.message}")
            }
        }
    }

    private fun showResetConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Reset All Settings?")
            .setMessage("This will restore all settings to defaults. Your chat history and memory will be preserved.")
            .setPositiveButton("Reset") { _, _ ->
                lifecycleScope.launch {
                    llmBridge.resetToDefaults()
                    agentMemory.clearTemporaryContext()
                    ToastManager.success(binding.root, "Settings reset to defaults")
                    loadSettingsCategories()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMemorySettingsDialog() {
        // Opens a BottomSheet with memory configuration
        MemorySettingsBottomSheet.newInstance().show(parentFragmentManager, "memory_settings")
    }

    private fun showAppearanceSettings() {
        AppearanceSettingsDialog.newInstance().show(parentFragmentManager, "appearance")
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
        }
        startActivity(intent)
    }

    private fun showPrivacySettings() {
        PrivacySettingsFragment.newInstance().let { navigateTo(it) }
    }

    private fun showAboutDialog() {
        AboutDialogFragment.newInstance().show(parentFragmentManager, "about")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Inner data class for RecyclerView
    data class SettingsCategory(
        val id: String,
        val title: String,
        val subtitle: String,
        val iconRes: Int,
        val fragmentClass: Class<out Fragment>?
    )
}
