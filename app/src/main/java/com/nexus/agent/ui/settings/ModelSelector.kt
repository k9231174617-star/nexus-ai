package com.nexus.agent.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexus.agent.R
import com.nexus.agent.core.llm.ModelRouter
import com.nexus.agent.databinding.DialogModelSelectorBinding
import com.nexus.agent.databinding.ItemModelBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ModelSelector : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_PROVIDER = "provider"
        private const val ARG_CURRENT_MODEL = "current_model"

        fun newInstance(currentProvider: String, currentModel: String = ""): ModelSelector {
            return ModelSelector().apply {
                arguments = bundleOf(
                    ARG_PROVIDER to currentProvider,
                    ARG_CURRENT_MODEL to currentModel
                )
            }
        }
    }

    private var _binding: DialogModelSelectorBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var modelRouter: ModelRouter

    private lateinit var modelAdapter: ModelAdapter
    private var onModelSelected: ((ModelInfo) -> Unit)? = null

    fun setOnModelSelectedListener(listener: (ModelInfo) -> Unit) {
        onModelSelected = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogModelSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val providerId = arguments?.getString(ARG_PROVIDER) ?: return
        val currentModel = arguments?.getString(ARG_CURRENT_MODEL) ?: ""

        setupRecyclerView(currentModel)
        loadModels(providerId)
        setupSearch()
    }

    private fun setupRecyclerView(currentModel: String) {
        modelAdapter = ModelAdapter(currentModel) { model ->
            onModelSelected?.invoke(model)
            dismiss()
        }

        binding.recyclerViewModels.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = modelAdapter
            setHasFixedSize(true)
        }
    }

    private fun loadModels(providerId: String) {
        lifecycleScope.launch {
            val models = modelRouter.getModelsForProvider(providerId)
            modelAdapter.submitList(models)
            binding.tvModelCount.text = "${models.size} models available"
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            
            override fun onQueryTextChange(newText: String?): Boolean {
                modelAdapter.filter.filter(newText)
                return true
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- Adapter ---

    inner class ModelAdapter(
        private val selectedModelId: String,
        private val onClick: (ModelInfo) -> Unit
    ) : ListAdapter<ModelInfo, ModelAdapter.ModelViewHolder>(ModelDiffCallback()), Filterable {

        private var fullList: List<ModelInfo> = emptyList()

        override fun submitList(list: List<ModelInfo>?) {
            fullList = list ?: emptyList()
            super.submitList(list)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
            val binding = ItemModelBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ModelViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val filtered = if (constraint.isNullOrBlank()) {
                        fullList
                    } else {
                        val query = constraint.toString().lowercase()
                        fullList.filter {
                            it.name.lowercase().contains(query) ||
                            it.id.lowercase().contains(query) ||
                            it.description.lowercase().contains(query)
                        }
                    }
                    return FilterResults().apply { values = filtered }
                }

                @Suppress("UNCHECKED_CAST")
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    submitList(results?.values as? List<ModelInfo> ?: fullList, false)
                }
            }
        }

        inner class ModelViewHolder(
            private val binding: ItemModelBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(model: ModelInfo) {
                binding.apply {
                    tvModelName.text = model.name
                    tvModelId.text = model.id
                    tvModelDescription.text = model.description
                    tvContextLength.text = "${model.contextLength} ctx"
                    tvCostPerToken.text = "$${model.costPer1KTokens}/1K"

                    // Capabilities chips
                    chipVision.visibility = if (model.supportsVision) View.VISIBLE else View.GONE
                    chipFunctionCalling.visibility = if (model.supportsFunctions) View.VISIBLE else View.GONE
                    chipJsonMode.visibility = if (model.supportsJsonMode) View.VISIBLE else View.GONE

                    // Selection state
                    root.isSelected = model.id == selectedModelId
                    ivSelected.visibility = if (model.id == selectedModelId) View.VISIBLE else View.GONE

                    root.setOnClickListener { onClick(model) }
                }
            }
        }
    }

    class ModelDiffCallback : DiffUtil.ItemCallback<ModelInfo>() {
        override fun areItemsTheSame(old: ModelInfo, new: ModelInfo) = old.id == new.id
        override fun areContentsTheSame(old: ModelInfo, new: ModelInfo) = old == new
    }
}

// Model info data class (normally in core module)
data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val contextLength: Int,
    val costPer1KTokens: Double,
    val supportsVision: Boolean,
    val supportsFunctions: Boolean,
    val supportsJsonMode: Boolean,
    val providerId: String
)
