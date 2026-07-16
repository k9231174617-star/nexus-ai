// app/src/main/java/com/nexus/agent/core/files/model/FileItem.kt
package com.nexus.agent.core.files.model

import android.graphics.Bitmap
import android.net.Uri

data class FileItem(
    val uri: Uri,
    val absolutePath: String,
    val name: String,
    val extension: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val isHidden: Boolean,
    val permissions: String = "rwx------",
    val owner: String = "",
    val group: String = "",
    val isSymlink: Boolean = false,
    val symlinkTarget: String? = null,
    val apkInfo: ApkInfo? = null,
    val mediaMeta: MediaMeta? = null,
    val checksumSha256: String? = null
) {
    companion object {
        fun fromFile(file: java.io.File): FileItem {
            return FileItem(
                uri = android.net.Uri.fromFile(file),
                absolutePath = file.absolutePath,
                name = file.name,
                extension = file.extension.lowercase(),
                mimeType = getMimeType(file),
                sizeBytes = file.length(),
                lastModified = file.lastModified(),
                isDirectory = file.isDirectory,
                isHidden = file.isHidden,
                isSymlink = file.isSymlinkCompat()
            )
        }

        private fun getMimeType(file: java.io.File): String {
            return android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        }

        private fun java.io.File.isSymlinkCompat(): Boolean {
            return try {
                val canonical = canonicalPath
                val absolute = absolutePath
                canonical != absolute
            } catch (_: Exception) {
                false
            }
        }
    }

    val isApk: Boolean get() = extension == "apk"
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
    val isText: Boolean get() = extension in TEXT_EXTENSIONS
    val isArchive: Boolean get() = extension in ARCHIVE_EXTENSIONS
    val isCode: Boolean get() = extension in CODE_EXTENSIONS

    companion object {
        val TEXT_EXTENSIONS = setOf("txt", "md", "log", "csv", "json", "xml")
        val CODE_EXTENSIONS = setOf("kt", "java", "py", "sh", "js", "html", "css", "smali", "c", "cpp", "h", "rs", "go", "rb", "php", "swift")
        val ARCHIVE_EXTENSIONS = setOf("zip", "tar", "gz", "tgz", "bz2", "7z", "rar")
    }
}

data class ApkInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val appName: String,
    val iconBase64: String? = null
)

data class MediaMeta(
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val bitrate: Long? = null
)
