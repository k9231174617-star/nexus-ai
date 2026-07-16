package com.nexus.agent.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.agent.R
import com.nexus.agent.core.llm.*
import com.nexus.agent.databinding.FragmentLlmConfigBinding
import com.nexus.agent.ui.common.ToastManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LLMConfigFragment : Fragment() {

    private var _binding: FragmentLlmConfigBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var llmBridge: LLMBridge
    @Inject lateinit var modelRouter: ModelRouter
    @Inject lateinit var costEstimator: CostEstimator

    private lateinit var apiKeyManager: APIKeyManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLlmConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        apiKeyManager = APIKeyManager(requireContext())
        setupUI()
        loadCurrentConfig()
        observeProviders()
    }

    private fun setupUI() {
        binding.apply {
            toolbar.title = "LLM Configuration"
            toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

            // Provider selector
            providerSpinner.onItemSelectedListener = 
                object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val provider = parent?.getItemAtPosition(position) as String
                        onProviderSelected(provider)
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }

            // Model selector
            btnSelectModel.setOnClickListener {
                ModelSelector.newInstance(currentProvider = binding.providerSpinner.selectedItem as String)
                    .show(childFragmentManager, "model_selector")
            }

            // Temperature slider
            sliderTemperature.addOnChangeListener { _, value, _ ->
                tvTemperatureValue.text = "%.1f".format(value)
            }

            // Max tokens slider
            sliderMaxTokens.addOnChangeListener { _, value, _ ->
                tvMaxTokensValue.text = value.toInt().toString()
            }

            // Top P slider
            sliderTopP.addOnChangeListener { _, value, _ ->
                tvTopPValue.text = "%.2f".format(value)
            }

            // Custom API endpoint
            switchCustomEndpoint.setOnCheckedChangeListener { _, isChecked ->
                tilCustomEndpoint.visibility = if (isChecked) View.VISIBLE else View.GONE
            }

            // Test connection
            btnTestConnection.setOnClickListener { testConnection() }

            // Save button
            btnSaveConfig.setOnClickListener { saveConfiguration() }

            // API Key management
            btnManageKeys.setOnClickListener {
                APIKeyManagerDialog.newInstance().show(childFragmentManager, "api_keys")
            }

            // Routing preferences
            btnRoutingPrefs.setOnClickListener {
                RoutingPreferencesDialog.newInstance().show(childFragmentManager, "routing")
            }
        }
    }

    private fun loadCurrentConfig() {
        lifecycleScope.launch {
            val config = llmBridge.getCurrentConfig()
            binding.apply {
                sliderTemperature.value = config.temperature.coerceIn(0.0f, 2.0f)
                tvTemperatureValue.text = "%.1f".format(config.temperature)
                
                sliderMaxTokens.value = config.maxTokens.toFloat().coerceIn(256f, 8192f)
                tvMaxTokensValue.text = config.maxTokens.toString()
                
                sliderTopP.value = config.topP.coerceIn(0.0f, 1.0f)
                tvTopPValue.text = "%.2f".format(config.topP)
                
                etSystemPrompt.setText(config.systemPrompt)
                switchStreamResponses.isChecked = config.streamResponses
                switchFallbackChain.isChecked = config.enableFallback
                switchCostOptimization.isChecked = config.costOptimization
                
                // Load provider list
                val providers = modelRouter.getAvailableProviders()
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    providers.map { it.displayName }
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                providerSpinner.adapter = adapter
                
                // Select current provider
                val currentIndex = providers.indexOfFirst { it.id == config.providerId }
                if (currentIndex >= 0) providerSpinner.setSelection(currentIndex)
            }
        }
    }

    private fun observeProviders() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    modelRouter.providerHealth.collectLatest { healthMap ->
                        updateProviderHealthIndicators(healthMap)
                    }
                }
                launch {
                    costEstimator.estimateFlow.collectLatest { estimate ->
                        binding.tvCostEstimate.text = "Est. cost: $${"%.4f".format(estimate.costPer1K)} / 1K tokens"
                    }
                }
            }
        }
    }

    private fun onProviderSelected(providerName: String) {
        lifecycleScope.launch {
            val provider = modelRouter.getProviderByName(providerName) ?: return@launch
            
            binding.apply {
                // Update available models for this provider
                btnSelectModel.text = provider.defaultModel
                
                // Update endpoint hint
                tilCustomEndpoint.hint = provider.defaultEndpoint
                
                // Show/hide API key field based on provider requirements
                tilApiKey.visibility = if (provider.requiresKey) View.VISIBLE else View.GONE
                
                // Update cost estimate
                costEstimator.estimateForProvider(provider.id)
            }
        }
    }

    private fun testConnection() {
        val providerName = binding.providerSpinner.selectedItem as? String ?: return
        val apiKey = binding.etApiKey.text?.toString()
        
        lifecycleScope.launch {
            binding.btnTestConnection.isEnabled = false
            binding.progressTest.visibility = View.VISIBLE
            
            try {
                val result = llmBridge.testConnection(providerName, apiKey)
                if (result.success) {
                    ToastManager.success(binding.root, "Connection successful! Latency: ${result.latency}ms")
                    binding.ivConnectionStatus.setImageResource(R.drawable.ic_connection_ok)
                } else {
                    ToastManager.error(binding.root, "Connection failed: ${result.error}")
                    binding.ivConnectionStatus.setImageResource(R.drawable.ic_connection_error)
                }
            } catch (e: Exception) {
                ToastManager.error(binding.root, "Test error: ${e.message}")
            } finally {
                binding.btnTestConnection.isEnabled = true
                binding.progressTest.visibility = View.GONE
            }
        }
    }

    private fun saveConfiguration() {
        lifecycleScope.launch {
            try {
                val config = LLMConfig(
                    providerId = modelRouter.getProviderIdByName(
                        binding.providerSpinner.selectedItem as String
                    ),
                    modelId = binding.btnSelectModel.text.toString(),
                    apiKey = binding.etApiKey.text?.toString()?.takeIf { it.isNotBlank() },
                    temperature = binding.sliderTemperature.value,
                    maxTokens = binding.sliderMaxTokens.value.toInt(),
                    topP = binding.sliderTopP.value,
                    systemPrompt = binding.etSystemPrompt.text?.toString() ?: "",
                    streamResponses = binding.switchStreamResponses.isChecked,
                    enableFallback = binding.switchFallbackChain.isChecked,
                    costOptimization = binding.switchCostOptimization.isChecked,
                    customEndpoint = if (binding.switchCustomEndpoint.isChecked) {
                        binding.etCustomEndpoint.text?.toString()
                    } else null
                )

                llmBridge.updateConfig(config)
                
                // Save API key securely if provided
                binding.etApiKey.text?.toString()?.takeIf { it.isNotBlank() }?.let { key ->
                    apiKeyManager.storeKey(config.providerId, key)
                }

                ToastManager.success(binding.root, "Configuration saved")
                parentFragmentManager.popBackStack()
            } catch (e: Exception) {
                ToastManager.error(binding.root, "Failed to save: ${e.message}")
            }
        }
    }

    private fun updateProviderHealthIndicators(healthMap: Map<String, ProviderHealth>) {
        // Update UI indicators for each provider's health status
        healthMap.forEach { (providerId, health) ->
            // Update spinner item colors or add badges
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
