package com.nexus.agent.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.agent.core.memory.MemoryDao
import com.nexus.agent.core.memory.MemoryEntry
import com.nexus.agent.core.memory.LocalEmbedder
import com.nexus.agent.core.memory.VectorStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel для MemoryFragment.
 * Управляет загрузкой, фильтрацией, поиском и модификацией воспоминаний.
 */
@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryDao: MemoryDao,
    private val localEmbedder: LocalEmbedder,
    private val vectorStore: VectorStore
) : ViewModel() {

    private val _memories = MutableStateFlow<List<MemoryEntry>>(emptyList())
    val memories: StateFlow<List<MemoryEntry>> = _memories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedMemory = MutableStateFlow<MemoryEntry?>(null)
    val selectedMemory: StateFlow<MemoryEntry?> = _selectedMemory.asStateFlow()

    private val _events = MutableSharedFlow<MemoryEvent>()
    val events: SharedFlow<MemoryEvent> = _events.asSharedFlow()

    private val _stats = MutableStateFlow(MemoryStats())
    val stats: StateFlow<MemoryStats> = _stats.asStateFlow()

    private var currentFilter = MemoryFilter()

    fun loadMemories() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val allMemories = memoryDao.getAllMemories()
                _memories.value = applyFilter(allMemories, currentFilter)
                updateStats(allMemories)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchMemories(query: String) {
        currentFilter = currentFilter.copy(searchQuery = query)
        applyCurrentFilter()
        
        if (query.isNotBlank()) {
            viewModelScope.launch {
                try {
                    val embedding = localEmbedder.embed(query)
                    val semanticResults = vectorStore.similaritySearch(embedding, topK = 10)
                } catch (e: Exception) {
                    _events.emit(MemoryEvent.ShowToast("Ошибка семантического поиска: ${e.message}"))
                }
            }
        }
    }

    fun filterByTimeRange(start: Long, end: Long) {
        currentFilter = currentFilter.copy(timeStart = start, timeEnd = end)
        applyCurrentFilter()
    }

    fun filterByTypes(types: List<MemoryEntry.Type>) {
        currentFilter = currentFilter.copy(types = types)
        applyCurrentFilter()
    }

    fun filterByMinImportance(minImportance: Float) {
        currentFilter = currentFilter.copy(minImportance = minImportance)
        applyCurrentFilter()
    }

    fun clearFilters() {
        currentFilter = MemoryFilter()
        applyCurrentFilter()
    }

    fun jumpToTimestamp(timestamp: Long) {
        viewModelScope.launch {
            val entry = memoryDao.getMemoryAtTimestamp(timestamp)
            _selectedMemory.value = entry
        }
    }

    fun injectContext(contextText: String, importance: Float, tags: List<String>) {
        viewModelScope.launch {
            try {
                val embedding = localEmbedder.embed(contextText)
                val entry = MemoryEntry(
                    id = System.currentTimeMillis(),
                    content = contextText,
                    embedding = embedding,
                    importanceScore = importance,
                    tags = tags,
                    source = "manual_inject",
                    type = MemoryEntry.Type.USER,
                    timestamp = System.currentTimeMillis()
                )
                memoryDao.insert(entry)
                vectorStore.add(entry.id, embedding)
                _events.emit(MemoryEvent.MemoryInjected)
                loadMemories()
            } catch (e: Exception) {
                _events.emit(MemoryEvent.ShowToast("Ошибка инжекции: ${e.message}"))
            }
        }
    }

    fun clearInjectedContext() {
        viewModelScope.launch {
            memoryDao.deleteBySource("manual_inject")
            loadMemories()
        }
    }

    fun addMemory(content: String, importance: Float, tags: List<String>) {
        viewModelScope.launch {
            try {
                val embedding = localEmbedder.embed(content)
                val entry = MemoryEntry(
                    id = System.currentTimeMillis(),
                    content = content,
                    embedding = embedding,
                    importanceScore = importance,
                    tags = tags,
                    source = "user_added",
                    type = MemoryEntry.Type.USER,
                    timestamp = System.currentTimeMillis()
                )
                memoryDao.insert(entry)
                vectorStore.add(entry.id, embedding)
                loadMemories()
                _events.emit(MemoryEvent.ShowToast("Воспоминание добавлено"))
            } catch (e: Exception) {
                _events.emit(MemoryEvent.ShowToast("Ошибка: ${e.message}"))
            }
        }
    }

    fun updateMemory(id: Long, content: String, importance: Float, tags: List<String>) {
        viewModelScope.launch {
            try {
                val embedding = localEmbedder.embed(content)
                memoryDao.update(id, content, embedding, importance, tags)
                vectorStore.update(id, embedding)
                loadMemories()
            } catch (e: Exception) {
                _events.emit(MemoryEvent.ShowToast("Ошибка обновления: ${e.message}"))
            }
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryDao.delete(id)
            vectorStore.delete(id)
            loadMemories()
        }
    }

    fun toggleRelevance(id: Long) {
        viewModelScope.launch {
            memoryDao.toggleRelevance(id)
            loadMemories()
        }
    }

    fun boostImportance(id: Long) {
        viewModelScope.launch {
            memoryDao.boostImportance(id, boost = 0.1f)
            loadMemories()
        }
    }

    fun archiveMemory(id: Long) {
        viewModelScope.launch {
            memoryDao.archive(id)
            loadMemories()
        }
    }

    fun exportMemories() {
        viewModelScope.launch {
            try {
                val memories = memoryDao.getAllMemories()
                val json = kotlinx.serialization.json.Json.encodeToString(
                    kotlinx.serialization.serializer(),
                    memories
                )
                val path = "/sdcard/nexus_ai/memories_export_${System.currentTimeMillis()}.json"
                java.io.File(path).parentFile?.mkdirs()
                java.io.File(path).writeText(json)
                _events.emit(MemoryEvent.ExportComplete(path))
            } catch (e: Exception) {
                _events.emit(MemoryEvent.ShowToast("Ошибка экспорта: ${e.message}"))
            }
        }
    }

    private fun applyCurrentFilter() {
        viewModelScope.launch {
            val all = memoryDao.getAllMemories()
            _memories.value = applyFilter(all, currentFilter)
        }
    }

    private fun applyFilter(
        memories: List<MemoryEntry>,
        filter: MemoryFilter
    ): List<MemoryEntry> {
        return memories.filter { entry ->
            val matchesQuery = filter.searchQuery.isBlank() ||
                entry.content.contains(filter.searchQuery, ignoreCase = true) ||
                entry.tags.any { it.contains(filter.searchQuery, ignoreCase = true) }
            
            val matchesTime = (filter.timeStart == null || entry.timestamp >= filter.timeStart) &&
                (filter.timeEnd == null || entry.timestamp <= filter.timeEnd)
            
            val matchesType = filter.types.isEmpty() || entry.type in filter.types
            
            val matchesImportance = entry.importanceScore >= filter.minImportance
            
            matchesQuery && matchesTime && matchesType && matchesImportance
        }.sortedByDescending { it.importanceScore }
    }

    private fun updateStats(memories: List<MemoryEntry>) {
        _stats.value = MemoryStats(
            totalCount = memories.size,
            totalTokens = memories.sumOf { it.tokenCount },
            avgImportance = if (memories.isNotEmpty()) {
                memories.map { it.importanceScore }.average().toFloat()
            } else 0f,
            storageSize = memories.sumOf { it.content.length * 2L }
        )
    }
}

data class MemoryFilter(
    val searchQuery: String = "",
    val timeStart: Long? = null,
    val timeEnd: Long? = null,
    val types: List<MemoryEntry.Type> = emptyList(),
    val minImportance: Float = 0f
)

data class MemoryStats(
    val totalCount: Int = 0,
    val totalTokens: Int = 0,
    val avgImportance: Float = 0f,
    val storageSize: Long = 0L
)

sealed class MemoryEvent {
    data class ShowToast(val message: String) : MemoryEvent()
    object MemoryInjected : MemoryEvent()
    data class ExportComplete(val path: String) : MemoryEvent()
}

enum class MemoryAction {
    DELETE, BOOST, ARCHIVE, SHARE
}
