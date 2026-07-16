package com.nexus.agent.ui.settings

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nexus.agent.BuildConfig
import com.nexus.agent.R
import com.nexus.agent.core.chat.ChatEngine
import com.nexus.agent.core.llm.LLMBridge
import com.nexus.agent.core.memory.AgentMemory
import com.nexus.agent.databinding.FragmentSystemInfoBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat
import javax.inject.Inject

@AndroidEntryPoint
class SystemInfoPanel : Fragment() {

    companion object {
        fun newInstance() = SystemInfoPanel()
    }

    private var _binding: FragmentSystemInfoBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var chatEngine: ChatEngine
    @Inject lateinit var agentMemory: AgentMemory
    @Inject lateinit var llmBridge: LLMBridge

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateRealtimeStats()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSystemInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        loadStaticInfo()
        loadDynamicInfo()
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    private fun setupUI() {
        binding.toolbar.title = "System Information"
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun loadStaticInfo() {
        binding.apply {
            // App info
            tvAppVersion.text = BuildConfig.VERSION_NAME
            tvBuildNumber.text = BuildConfig.VERSION_CODE.toString()
            tvPackageName.text = requireContext().packageName

            // Device info
            tvDeviceModel.text = "${Build.MANUFACTURER} ${Build.MODEL}"
            tvAndroidVersion.text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            tvAbi.text = Build.SUPPORTED_ABIS.joinToString(", ")

            // Nexus AI Modules
            lifecycleScope.launch {
                val modules = listOf(
                    "Chat Engine" to chatEngine.getVersion(),
                    "LLM Bridge" to llmBridge.getVersion(),
                    "Memory System" to agentMemory.getVersion(),
                    "Task Planner" to "1.2.0",
                    "Code Sandbox" to "2.0.1",
                    "Browser Agent" to "1.1.0",
                    "RAG System" to "1.3.0",
                    "Graph Memory" to "0.9.0"
                )
                
                tvModulesInfo.text = modules.joinToString("\n") { 
                    "• ${it.first}: v${it.second}" 
                }
            }
        }
    }

    private fun loadDynamicInfo() {
        val runtime = Runtime.getRuntime()
        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        
        binding.apply {
            // Memory info
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory

            tvMaxMemory.text = formatBytes(maxMemory)
            tvTotalAllocated.text = formatBytes(totalMemory)
            tvUsedMemory.text = formatBytes(usedMemory)
            tvFreeMemory.text = formatBytes(freeMemory)

            // Memory progress
            progressMemory.max = (totalMemory / 1024 / 1024).toInt()
            progressMemory.progress = (usedMemory / 1024 / 1024).toInt()

            // Storage
            val cacheDir = requireContext().cacheDir
            val filesDir = requireContext().filesDir
            
            tvCacheSize.text = formatBytes(getFolderSize(cacheDir))
            tvDataSize.text = formatBytes(getFolderSize(filesDir))
            
            // Database stats
            lifecycleScope.launch {
                val dbStats = agentMemory.getDatabaseStats()
                tvDatabaseSize.text = formatBytes(dbStats.sizeBytes)
                tvMemoryEntries.text = "${dbStats.entryCount} entries"
                tvVectorDimensions.text = "${dbStats.vectorDimensions}d vectors"
            }
        }
    }

    private fun updateRealtimeStats() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        
        binding.apply {
            tvUsedMemory.text = formatBytes(usedMemory)
            progressMemory.progress = (usedMemory / 1024 / 1024).toInt()
            
            // Update CPU usage (approximate)
            val availableProcessors = runtime.availableProcessors()
            tvCpuCores.text = "$availableProcessors cores"
            
            // Connection status
            lifecycleScope.launch {
                val isConnected = chatEngine.isConnected()
                ivConnectionStatus.setImageResource(
                    if (isConnected) R.drawable.ic_connection_ok else R.drawable.ic_connection_error
                )
                tvConnectionStatus.text = if (isConnected) "Connected" else "Disconnected"
            }
        }
    }

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return size
    }

    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return "${DecimalFormat("#.00").format(size)} ${units[unitIndex]}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateRunnable)
        _binding = null
    }
}
