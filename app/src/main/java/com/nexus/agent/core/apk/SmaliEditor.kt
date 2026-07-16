package com.nexus.agent.core.apk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class SmaliSearchResult(
    val filePath: String,
    val lineNumber: Int,
    val lineContent: String,
    val context: List<String>,
)

@Singleton
class SmaliEditor @Inject constructor() {

    suspend fun searchInSmali(
        smaliDir: String,
        query: String,
        contextLines: Int = 3,
    ): List<SmaliSearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SmaliSearchResult>()
        File(smaliDir).walkTopDown()
            .filter { it.extension == "smali" }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { idx, line ->
                    if (line.contains(query, ignoreCase = true)) {
                        val start = maxOf(0, idx - contextLines)
                        val end = minOf(lines.size - 1, idx + contextLines)
                        results.add(
                            SmaliSearchResult(
                                filePath = file.absolutePath,
                                lineNumber = idx + 1,
                                lineContent = line,
                                context = lines.subList(start, end + 1),
                            )
                        )
                    }
                }
            }
        results
    }

    suspend fun replaceInSmali(
        filePath: String,
        searchText: String,
        replaceText: String,
    ): Int = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val original = file.readText()
        val modified = original.replace(searchText, replaceText)
        val count = (original.length - modified.replace(searchText, "").length) / searchText.length
        if (count > 0) file.writeText(modified)
        count
    }

    suspend fun insertAfterMethod(
        filePath: String,
        methodSignature: String,
        code: String,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(filePath)
            val lines = file.readLines().toMutableList()
            val idx = lines.indexOfFirst { it.contains(methodSignature) }
            if (idx != -1) {
                lines.add(idx + 1, code)
                file.writeText(lines.joinToString("\n"))
                true
            } else false
        }.getOrDefault(false)
    }

    suspend fun readMethod(filePath: String, methodSignature: String): String =
        withContext(Dispatchers.IO) {
            val lines = File(filePath).readLines()
            val startIdx = lines.indexOfFirst { it.contains(methodSignature) }
            if (startIdx == -1) return@withContext ""

            val methodLines = mutableListOf<String>()
            var depth = 0
            for (i in startIdx until lines.size) {
                val line = lines[i]
                methodLines.add(line)
                if (line.trimStart().startsWith(".method")) depth++
                if (line.trimStart().startsWith(".end method")) {
                    depth--
                    if (depth <= 0) break
                }
            }
            methodLines.joinToString("\n")
        }

    suspend fun listMethods(filePath: String): List<String> = withContext(Dispatchers.IO) {
        File(filePath).readLines()
            .filter { it.trimStart().startsWith(".method") }
            .map { it.trim() }
    }
}