package com.nexus.agent.core.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class EditOperation(
    val type: EditType,
    val searchText: String = "",
    val replaceText: String = "",
    val insertAt: Int = -1,
    val insertText: String = "",
    val deleteFrom: Int = -1,
    val deleteTo: Int = -1,
)

enum class EditType {
    FIND_REPLACE, INSERT, DELETE, APPEND, PREPEND, REGEX_REPLACE
}

@Singleton
class DocumentEditor @Inject constructor(
    private val context: Context,
    private val documentReader: DocumentReader,
) {
    suspend fun applyOperations(
        uri: Uri,
        operations: List<EditOperation>,
    ): String = withContext(Dispatchers.IO) {
        val doc = documentReader.read(uri)
        var text = doc.text

        operations.forEach { op ->
            text = when (op.type) {
                EditType.FIND_REPLACE ->
                    text.replace(op.searchText, op.replaceText)

                EditType.REGEX_REPLACE ->
                    text.replace(Regex(op.searchText), op.replaceText)

                EditType.INSERT -> {
                    val idx = op.insertAt.coerceIn(0, text.length)
                    text.substring(0, idx) + op.insertText + text.substring(idx)
                }

                EditType.DELETE -> {
                    val from = op.deleteFrom.coerceIn(0, text.length)
                    val to = op.deleteTo.coerceIn(from, text.length)
                    text.removeRange(from, to)
                }

                EditType.APPEND  -> text + "\n" + op.insertText
                EditType.PREPEND -> op.insertText + "\n" + text
            }
        }

        // Save result to cache
        val outFile = File(
            context.cacheDir,
            "edited_${System.currentTimeMillis()}.txt"
        )
        FileOutputStream(outFile).use { it.write(text.toByteArray()) }
        outFile.absolutePath
    }

    suspend fun saveAsMarkdown(content: String, fileName: String): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "nexus_docs").also { it.mkdirs() }
            val file = File(dir, "$fileName.md")
            file.writeText(content)
            file
        }

    suspend fun mergeDocuments(uris: List<Uri>, separator: String = "\n\n---\n\n"): String =
        withContext(Dispatchers.IO) {
            uris.joinToString(separator) { uri ->
                documentReader.read(uri).text
            }
        }

    suspend fun summarizeWithAI(text: String, maxLength: Int = 500): String {
        // Returns truncated summary — full AI summary via LLMBridge
        val words = text.split("\\s+".toRegex())
        return if (words.size <= maxLength / 5) text
        else words.take(maxLength / 5).joinToString(" ") + "..."
    }

    suspend fun findAndHighlight(text: String, query: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var idx = text.indexOf(query, ignoreCase = true)
        while (idx != -1) {
            ranges.add(idx until idx + query.length)
            idx = text.indexOf(query, idx + 1, ignoreCase = true)
        }
        return ranges
    }
}