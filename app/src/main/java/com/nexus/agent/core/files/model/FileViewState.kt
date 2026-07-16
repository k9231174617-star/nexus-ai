// app/src/main/java/com/nexus/agent/core/files/model/FileViewState.kt
package com.nexus.agent.core.files.model

data class FileViewState(
    val currentPath: String = "/sdcard",
    val breadcrumbs: List<BreadcrumbItem> = listOf(BreadcrumbItem("sdcard", "/sdcard")),
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedItems: Set<FileItem> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val viewMode: ViewMode = ViewMode.GRID,
    val sortBy: SortBy = SortBy.NAME,
    val sortAscending: Boolean = true,
    val filterType: FileFilterType = FileFilterType.ALL,
    val isRootAvailable: Boolean = false,
    val searchQuery: String = "",
    val operationProgress: Map<String, Float> = emptyMap(),
    val clipboardOperation: ClipboardOperation? = null
)

data class BreadcrumbItem(
    val displayName: String,
    val fullPath: String
)

data class ClipboardOperation(
    val files: List<FileItem>,
    val isCut: Boolean
)

enum class ViewMode { GRID, LIST }
enum class SortBy { NAME, SIZE, DATE, TYPE }
enum class FileFilterType { 
    ALL, DOCUMENTS, IMAGES, VIDEO, AUDIO, APK, CODE, ARCHIVES 
}
