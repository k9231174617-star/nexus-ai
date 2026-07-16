package com.nexus.agent.core.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class ShareManager(private val context: Context) {

    fun shareFile(file: File, mimeType: String? = null) {
        val uri = getFileUri(file)
        val type = mimeType ?: getMimeType(file)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = type
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
    }

    fun shareText(text: String, subject: String = "") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        
        context.startActivity(Intent.createChooser(intent, "Share"))
    }

    fun shareMultiple(files: List<File>) {
        val uris = files.map { getFileUri(it) }
        
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "Share ${files.size} files"))
    }

    private fun getFileUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "apk" -> "application/vnd.android.package-archive"
            else -> "*/*"
        }
    }
}
