package com.nexus.agent.ui.memory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexus.agent.core.memory.AgentMemory
import com.nexus.agent.core.memory.MemoryEntry
import com.nexus.agent.databinding.FragmentMemoryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fragment для управления памятью агента.
 * Отображает timeline воспоминаний, позволяет искать, фильтровать и инжектировать контекст.
 */
@AndroidEntryPoint
class MemoryFragment : Fragment() {

    private var _binding: FragmentMemoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MemoryViewModel by viewModels()
    
    @Inject
    lateinit var agentMemory: AgentMemory

    private lateinit var memoryAdapter: MemoryTimelineAdapter
    private lateinit var timelineView: MemoryTimelineView
    private lateinit var contextPanel: ContextInjectPanel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMemoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViews()
        setupRecyclerView()
        setupSearch()
        setupFilters()
        observeViewModel()
        
        viewModel.loadMemories()
    }

    private fun setupViews() {
        timelineView = MemoryTimelineView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(com.nexus.agent.R.dimen.timeline_height)
            )
            onTimeRangeSelected = { start, end ->
                viewModel.filterByTimeRange(start, end)
            }
            onPointSelected = { timestamp ->
                viewModel.jumpToTimestamp(timestamp)
            }
        }
        binding.timelineContainer.addView(timelineView)

        contextPanel = ContextInjectPanel(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            onInjectRequested = { contextText, importance, tags ->
                viewModel.injectContext(contextText, importance, tags)
            }
            onClearRequested = {
                viewModel.clearInjectedContext()
            }
        }
        binding.contextPanelContainer.addView(contextPanel)

        binding.fabAddMemory.setOnClickListener {
            showAddMemoryDialog()
        }

        binding.btnClearFilters.setOnClickListener {
            viewModel.clearFilters()
            binding.searchEditText.text?.clear()
            timelineView.clearSelection()
        }

        binding.btnExport.setOnClickListener {
            viewModel.exportMemories()
        }

        binding.btnImport.setOnClickListener {
            showImportDialog()
        }
    }

    private fun setupRecyclerView() {
        memoryAdapter = MemoryTimelineAdapter(
            onItemClick = { entry ->
                showMemoryDetailDialog(entry)
            },
            onItemLongClick = { entry ->
                showMemoryOptionsMenu(entry)
            },
            onRelevanceClick = { entry ->
                viewModel.toggleRelevance(entry.id)
            },
            onDeleteClick = { entry ->
                viewModel.deleteMemory(entry.id)
            }
        )

        binding.recyclerViewMemory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = memoryAdapter
            setHasFixedSize(false)
        }
    }

    private fun setupSearch() {
        binding.searchEditText.setOnEditorActionListener { _, _, _ ->
            val query = binding.searchEditText.text?.toString()?.trim()
            viewModel.searchMemories(query ?: "")
            true
        }

        binding.btnSearch.setOnClickListener {
            val query = binding.searchEditText.text?.toString()?.trim()
            viewModel.searchMemories(query ?: "")
        }
    }

    private fun setupFilters() {
        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            val types = checkedIds.map { id ->
                when (id) {
                    com.nexus.agent.R.id.chip_conversation -> MemoryEntry.Type.CONVERSATION
                    com.nexus.agent.R.id.chip_file -> MemoryEntry.Type.FILE
                    com.nexus.agent.R.id.chip_command -> MemoryEntry.Type.COMMAND
                    com.nexus.agent.R.id.chip_system -> MemoryEntry.Type.SYSTEM
                    com.nexus.agent.R.id.chip_user -> MemoryEntry.Type.USER
                    else -> MemoryEntry.Type.CONVERSATION
                }
            }
            viewModel.filterByTypes(types)
        }

        binding.importanceSlider.addOnChangeListener { _, value, _ ->
            viewModel.filterByMinImportance(value)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.memories.collectLatest { memories ->
                        memoryAdapter.submitList(memories)
                        binding.emptyState.isVisible = memories.isEmpty()
                        binding.recyclerViewMemory.isVisible = memories.isNotEmpty()
                        timelineView.setMemories(memories)
                    }
                }

                launch {
                    viewModel.isLoading.collectLatest { isLoading ->
                        binding.progressBar.isVisible = isLoading
                    }
                }

                launch {
                    viewModel.selectedMemory.collectLatest { entry ->
                        entry?.let {
                            binding.memoryDetailContainer.isVisible = true
                            showMemoryDetail(it)
                        } ?: run {
                            binding.memoryDetailContainer.isVisible = false
                        }
                    }
                }

                launch {
                    viewModel.events.collectLatest { event ->
                        when (event) {
                            is MemoryEvent.ShowToast -> {
                                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                            }
                            is MemoryEvent.MemoryInjected -> {
                                contextPanel.showSuccessAnimation()
                                Toast.makeText(requireContext(), "Контекст инжектирован", Toast.LENGTH_SHORT).show()
                            }
                            is MemoryEvent.ExportComplete -> {
                                Toast.makeText(requireContext(), "Экспорт завершён: ${event.path}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                launch {
                    viewModel.stats.collectLatest { stats ->
                        binding.statsBar.updateStats(
                            totalMemories = stats.totalCount,
                            totalTokens = stats.totalTokens,
                            avgImportance = stats.avgImportance,
                            memorySize = stats.storageSize
                        )
                    }
                }
            }
        }
    }

    private fun showMemoryDetail(entry: MemoryEntry) {
        binding.tvDetailContent.text = entry.content
        binding.tvDetailTimestamp.text = entry.formattedTimestamp
        binding.tvDetailImportance.text = "Важность: ${entry.importanceScore}"
        binding.tvDetailTokens.text = "Токенов: ${entry.tokenCount}"
        binding.tvDetailTags.text = entry.tags.joinToString(", ")
        binding.tvDetailSource.text = "Источник: ${entry.source}"
        
        val importanceColor = when {
            entry.importanceScore >= 0.8f -> com.nexus.agent.R.color.importance_high
            entry.importanceScore >= 0.5f -> com.nexus.agent.R.color.importance_medium
            else -> com.nexus.agent.R.color.importance_low
        }
        binding.importanceIndicator.setBackgroundColor(
            requireContext().getColor(importanceColor)
        )
    }

    private fun showAddMemoryDialog() {
        val dialog = MemoryEditDialog.newInstance(null)
        dialog.onSave = { content, importance, tags ->
            viewModel.addMemory(content, importance, tags)
        }
        dialog.show(childFragmentManager, "add_memory")
    }

    private fun showMemoryDetailDialog(entry: MemoryEntry) {
        val dialog = MemoryEditDialog.newInstance(entry)
        dialog.onSave = { content, importance, tags ->
            viewModel.updateMemory(entry.id, content, importance, tags)
        }
        dialog.show(childFragmentManager, "edit_memory")
    }

    private fun showMemoryOptionsMenu(entry: MemoryEntry) {
        val bottomSheet = MemoryOptionsBottomSheet.newInstance(entry)
        bottomSheet.onAction = { action ->
            when (action) {
                MemoryAction.DELETE -> viewModel.deleteMemory(entry.id)
                MemoryAction.BOOST -> viewModel.boostImportance(entry.id)
                MemoryAction.ARCHIVE -> viewModel.archiveMemory(entry.id)
                MemoryAction.SHARE -> shareMemory(entry)
            }
        }
        bottomSheet.show(childFragmentManager, "memory_options")
    }

    private fun showImportDialog() {
        // Реализация диалога импорта
    }

    private fun shareMemory(entry: MemoryEntry) {
        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, entry.content)
        }
        startActivity(android.content.Intent.createChooser(shareIntent, "Поделиться воспоминанием"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
