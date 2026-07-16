package com.nexus.agent.core.files

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import java.io.File

class FileManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    data class FileTree(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
        val permissions: String,
        val children: List<FileTree> = emptyList()
    )

    suspend fun listDirectory(path: String): List<FileTree> = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()

        dir.listFiles()?.map { file ->
            FileTree(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = file.length(),
                lastModified = file.lastModified(),
                permissions = getPermissions(file),
                children = if (file.isDirectory) listDirectory(file.absolutePath) else emptyList()
            )
        } ?: emptyList()
    }

    suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
        File(path).readText()
    }

    suspend fun writeFile(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(path).writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun createDirectory(path: String): Boolean = withContext(Dispatchers.IO) {
        File(path).mkdirs()
    }

    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    suspend fun copy(source: String, dest: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(source).copyTo(File(dest), overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun move(source: String, dest: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(source).renameTo(File(dest))
        } catch (e: Exception) {
            false
        }
    }

    suspend fun uploadToChat(file: File): Uri {
        val chatDir = File(context.filesDir, "chat_uploads")
        chatDir.mkdirs()
        
        val dest = File(chatDir, file.name)
        file.copyTo(dest, overwrite = true)
        
        return Uri.fromFile(dest)
    }

    suspend fun analyzeFile(file: File): FileAnalysis = withContext(Dispatchers.IO) {
        FileAnalysis(
            name = file.name,
            size = file.length(),
            type = detectMimeType(file),
            hash = calculateHash(file),
            isText = isTextFile(file),
            lineCount = if (isTextFile(file)) file.readLines().size else 0,
            preview = if (isTextFile(file)) file.readText().take(5000) else null
        )
    }

    suspend fun searchFiles(dir: String, query: String): List<FileTree> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileTree>()
        val root = File(dir)
        
        fun searchRecursive(current: File) {
            current.listFiles()?.forEach { file ->
                if (file.name.contains(query, ignoreCase = true)) {
                    results.add(FileTree(
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = file.isDirectory,
                        size = file.length(),
                        lastModified = file.lastModified(),
                        permissions = getPermissions(file)
                    ))
                }
                if (file.isDirectory) searchRecursive(file)
            }
        }
        
        searchRecursive(root)
        results
    }

    private fun getPermissions(file: File): String {
        return buildString {
            append(if (file.canRead()) 'r' else '-')
            append(if (file.canWrite()) 'w' else '-')
            append(if (file.canExecute()) 'x' else '-')
        }
    }

    private fun detectMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "kt" -> "text/x-kotlin"
            "java" -> "text/x-java"
            "py" -> "text/x-python"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "md" -> "text/markdown"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "mp4" -> "video/mp4"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }

    private fun calculateHash(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isTextFile(file: File): Boolean {
        val textExtensions = setOf(
            "txt", "md", "json", "xml", "kt", "java", "py", "js", "c", "cpp", 
            "h", "sh", "smali", "yml", "yaml", "gradle", "properties", "ini",
            "conf", "cfg", "log", "csv", "tsv", "html", "css", "scss", "sass"
        )
        return textExtensions.contains(file.extension.lowercase())
    }

    data class FileAnalysis(
        val name: String,
        val size: Long,
        val type: String,
        val hash: String,
        val isText: Boolean,
        val lineCount: Int,
        val preview: String?
    )
}
