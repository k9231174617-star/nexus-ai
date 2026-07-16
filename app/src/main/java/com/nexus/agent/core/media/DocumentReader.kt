package com.nexus.agent.core.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

data class DocumentContent(
    val text: String,
    val pageCount: Int,
    val wordCount: Int,
    val mimeType: String,
    val title: String = "",
    val author: String = "",
)

@Singleton
class DocumentReader @Inject constructor(
    private val context: Context,
) {
    suspend fun read(uri: Uri): DocumentContent = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri) ?: "text/plain"
        when {
            mimeType.contains("pdf")  -> readPdf(uri)
            mimeType.contains("text") -> readText(uri, mimeType)
            mimeType.contains("html") -> readHtml(uri)
            mimeType.contains("word") || uri.path?.endsWith(".docx") == true -> readDocx(uri)
            mimeType.contains("markdown") || uri.path?.endsWith(".md") == true -> readText(uri, "text/markdown")
            else -> readText(uri, mimeType)
        }
    }

    private fun readText(uri: Uri, mimeType: String): DocumentContent {
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).readText()
        } ?: ""
        return DocumentContent(
            text = text,
            pageCount = 1,
            wordCount = text.split("\\s+".toRegex()).filter { it.isNotBlank() }.size,
            mimeType = mimeType,
        )
    }

    private fun readHtml(uri: Uri): DocumentContent {
        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).readText()
        } ?: ""
        // Strip HTML tags
        val text = raw.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return DocumentContent(
            text = text,
            pageCount = 1,
            wordCount = text.split(" ").filter { it.isNotBlank() }.size,
            mimeType = "text/html",
        )
    }

    private fun readPdf(uri: Uri): DocumentContent {
        // Uses Android PdfRenderer for text extraction
        val fd = context.contentResolver.openFileDescriptor(uri, "r")
        return try {
            val renderer = android.graphics.pdf.PdfRenderer(fd!!)
            val pageCount = renderer.pageCount
            val textBuilder = StringBuilder()

            // Extract text via rendering each page to bitmap then OCR
            // For now return metadata — full OCR needs ML Kit
            renderer.close()
            DocumentContent(
                text = "[PDF document — $pageCount pages. Use RAG ingestion for full text extraction.]",
                pageCount = pageCount,
                wordCount = 0,
                mimeType = "application/pdf",
            )
        } finally {
            fd?.close()
        }
    }

    private fun readDocx(uri: Uri): DocumentContent {
        // DOCX = ZIP containing XML — parse word/document.xml
        val text = try {
            val stream = context.contentResolver.openInputStream(uri) ?: return DocumentContent(
                text = "", pageCount = 1, wordCount = 0, mimeType = "application/docx"
            )
            val zip = java.util.zip.ZipInputStream(stream)
            var entry = zip.nextEntry
            var content = ""
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zip.bufferedReader().readText()
                    content = xml.replace(Regex("<[^>]+>"), " ")
                        .replace(Regex("\\s+"), " ").trim()
                    break
                }
                entry = zip.nextEntry
            }
            zip.close()
            stream.close()
            content
        } catch (e: Exception) {
            "Error reading DOCX: ${e.message}"
        }
        return DocumentContent(
            text = text,
            pageCount = 1,
            wordCount = text.split(" ").filter { it.isNotBlank() }.size,
            mimeType = "application/docx",
        )
    }

    suspend fun extractMetadata(uri: Uri): Map<String, String> = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri) ?: "unknown"
        buildMap {
            put("mimeType", mimeType)
            put("uri", uri.toString())
            context.contentResolver.query(
                uri,
                arrayOf("_display_name", "_size", "date_modified"),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    runCatching { put("name", cursor.getString(0)) }
                    runCatching { put("size", cursor.getLong(1).toString()) }
                    runCatching { put("modified", cursor.getLong(2).toString()) }
                }
            }
        }
    }
}