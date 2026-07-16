package com.nexus.agent.core.rag

import android.content.Context
import android.net.Uri
import com.nexus.agent.core.media.DocumentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class DocumentChunk(
    val id: String = UUID.randomUUID().toString(),
    val docId: String,
    val content: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val metadata: Map<String, String> = emptyMap(),
)

@Singleton
class DocumentIngestor @Inject constructor(
    private val context: Context,
    private val documentReader: DocumentReader,
    private val parser: DocumentParser,
) {
    companion object {
        const val CHUNK_SIZE = 512
        const val CHUNK_OVERLAP = 64
    }

    suspend fun ingest(uri: Uri, docId: String? = null): List<DocumentChunk> =
        withContext(Dispatchers.IO) {
            val id = docId ?: UUID.randomUUID().toString()
            val doc = documentReader.read(uri)
            val meta = documentReader.extractMetadata(uri)
            val cleaned = parser.clean(doc.text)
            chunk(cleaned, id, meta)
        }

    suspend fun ingestText(text: String, docId: String = UUID.randomUUID().toString()): List<DocumentChunk> =
        withContext(Dispatchers.IO) {
            chunk(text, docId, emptyMap())
        }

    private fun chunk(text: String, docId: String, meta: Map<String, String>): List<DocumentChunk> {
        val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val chunks = mutableListOf<List<String>>()
        var i = 0
        while (i < words.size) {
            chunks.add(words.subList(i, minOf(i + CHUNK_SIZE, words.size)))
            i += CHUNK_SIZE - CHUNK_OVERLAP
        }
        val total = chunks.size
        return chunks.mapIndexed { idx, words ->
            DocumentChunk(
                docId = docId,
                content = words.joinToString(" "),
                chunkIndex = idx,
                totalChunks = total,
                metadata = meta,
            )
        }
    }
}