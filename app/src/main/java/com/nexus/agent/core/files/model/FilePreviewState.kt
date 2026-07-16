// app/src/main/java/com/nexus/agent/core/files/model/FilePreviewState.kt
package com.nexus.agent.core.files.model

import android.graphics.Bitmap

data class FilePreviewState(
    val file: FileItem? = null,
    val previewType: PreviewType = PreviewType.UNSUPPORTED,
    val textContent: String? = null,
    val highlightedSpans: List<SyntaxSpan> = emptyList(),
    val imageBitmap: Bitmap? = null,
    val pdfPageCount: Int = 0,
    val currentPdfPage: Int = 0,
    val apkInfo: ApkInfo? = null,
    val audioWaveformData: FloatArray? = null,
    val isPlaying: Boolean = false,
    val isFullPreviewAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val archiveContents: List<String> = emptyList()
)

enum class PreviewType {
    TEXT, IMAGE, SVG, PDF, VIDEO, AUDIO, APK, ARCHIVE, BINARY, UNSUPPORTED
}

data class SyntaxSpan(
    val start: Int,
    val end: Int,
    val style: SyntaxStyle
)

enum class SyntaxStyle {
    KEYWORD, STRING, COMMENT, NUMBER, FUNCTION, TYPE, OPERATOR, PLAIN
}
