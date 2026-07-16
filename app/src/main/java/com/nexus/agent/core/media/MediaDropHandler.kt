package com.nexus.agent.core.media

import android.content.Context
import android.net.Uri
import android.view.DragEvent
import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class DroppedMedia(
    val uri: Uri,
    val mimeType: String,
    val fileName: String,
    val sizeBytes: Long,
)

@Singleton
class MediaDropHandler @Inject constructor(
    private val context: Context,
) {
    private val _droppedFiles = MutableStateFlow<List<DroppedMedia>>(emptyList())
    val droppedFiles: StateFlow<List<DroppedMedia>> = _droppedFiles

    fun attachToView(view: View, onDropped: (List<DroppedMedia>) -> Unit) {
        view.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED  -> {
                    view.alpha = 0.7f
                    true
                }
                DragEvent.ACTION_DRAG_ENTERED  -> {
                    view.alpha = 0.5f
                    true
                }
                DragEvent.ACTION_DRAG_EXITED   -> {
                    view.alpha = 1f
                    true
                }
                DragEvent.ACTION_DROP          -> {
                    view.alpha = 1f
                    val items = event.clipData
                    if (items != null) {
                        val dropped = (0 until items.itemCount).mapNotNull { i ->
                            val item = items.getItemAt(i)
                            item.uri?.let { uri ->
                                resolveMedia(uri)
                            }
                        }
                        _droppedFiles.value = dropped
                        onDropped(dropped)
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED    -> {
                    view.alpha = 1f
                    true
                }
                else -> false
            }
        }
    }

    private fun resolveMedia(uri: Uri): DroppedMedia? {
        return try {
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            var fileName = "unknown"
            var sizeBytes = 0L
            context.contentResolver.query(
                uri,
                arrayOf("_display_name", "_size"),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(0) ?: "unknown"
                    sizeBytes = cursor.getLong(1)
                }
            }
            DroppedMedia(uri, mimeType, fileName, sizeBytes)
        } catch (e: Exception) {
            null
        }
    }

    fun classifyMedia(mimeType: String): MediaClass = when {
        mimeType.startsWith("image/") -> MediaClass.IMAGE
        mimeType.startsWith("video/") -> MediaClass.VIDEO
        mimeType.startsWith("audio/") -> MediaClass.AUDIO
        mimeType.contains("pdf")      -> MediaClass.PDF
        mimeType.contains("word") || mimeType.contains("document") -> MediaClass.DOCUMENT
        mimeType.contains("text")     -> MediaClass.TEXT
        else -> MediaClass.OTHER
    }

    fun clearDropped() { _droppedFiles.value = emptyList() }
}

enum class MediaClass { IMAGE, VIDEO, AUDIO, PDF, DOCUMENT, TEXT, OTHER }