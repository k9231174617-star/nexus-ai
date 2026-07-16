package com.nexus.agent.core.files

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.XML
import java.io.File

class FileAnalyzer(private val context: Context) {

    data class AnalysisResult(
        val fileType: String,
        val structure: Map<String, Any>,
        val statistics: FileStatistics,
        val issues: List<Issue>
    )

    data class FileStatistics(
        val totalLines: Int,
        val codeLines: Int,
        val commentLines: Int,
        val blankLines: Int,
        val complexity: Int
    )

    data class Issue(
        val severity: Severity,
        val line: Int,
        val message: String,
        val rule: String
    )

    enum class Severity { INFO, WARNING, ERROR }

    suspend fun analyze(file: File): AnalysisResult = withContext(Dispatchers.IO) {
        when (file.extension.lowercase()) {
            "json" -> analyzeJson(file)
            "xml" -> analyzeXml(file)
            "kt", "java" -> analyzeCode(file)
            "apk" -> analyzeApk(file)
            else -> genericAnalysis(file)
        }
    }

    private fun analyzeJson(file: File): AnalysisResult {
        val content = file.readText()
        val json = JSONObject(content)
        
        return AnalysisResult(
            fileType = "JSON",
            structure = mapOf(
                "keys" to json.keys().asSequence().toList(),
                "depth" to calculateJsonDepth(json),
                "arrays" to countArrays(json)
            ),
            statistics = FileStatistics(
                totalLines = content.lines().size,
                codeLines = content.lines().size,
                commentLines = 0,
                blankLines = content.lines().count { it.isBlank() },
                complexity = 1
            ),
            issues = validateJson(json)
        )
    }

    private fun analyzeXml(file: File): AnalysisResult {
        val content = file.readText()
        val xml = XML.toJSONObject(content)
        
        return AnalysisResult(
            fileType = "XML",
            structure = mapOf(
                "rootElement" to xml.keys().next(),
                "depth" to calculateJsonDepth(xml)
            ),
            statistics = FileStatistics(
                totalLines = content.lines().size,
                codeLines = content.lines().size,
                commentLines = content.lines().count { it.contains("<!--") },
                blankLines = content.lines().count { it.isBlank() },
                complexity = 1
            ),
            issues = emptyList()
        )
    }

    private fun analyzeCode(file: File): AnalysisResult {
        val content = file.readText()
        val lines = content.lines()
        
        val codeLines = lines.count { it.trim().isNotEmpty() && !it.trim().startsWith("//") && !it.trim().startsWith("/*") }
        val commentLines = lines.count { it.trim().startsWith("//") || it.trim().startsWith("/*") || it.trim().startsWith("*") }
        val blankLines = lines.count { it.trim().isEmpty() }
        
        val complexity = calculateCyclomaticComplexity(content)
        
        return AnalysisResult(
            fileType = if (file.extension == "kt") "Kotlin" else "Java",
            structure = mapOf(
                "classes" to countClasses(content),
                "methods" to countMethods(content),
                "imports" to countImports(content)
            ),
            statistics = FileStatistics(
                totalLines = lines.size,
                codeLines = codeLines,
                commentLines = commentLines,
                blankLines = blankLines,
                complexity = complexity
            ),
            issues = lintCode(content, file.extension)
        )
    }

    private fun analyzeApk(file: File): AnalysisResult {
        // Базовый анализ APK без полной декомпиляции
        return AnalysisResult(
            fileType = "APK",
            structure = mapOf(
                "size" to file.length(),
                "name" to file.name
            ),
            statistics = FileStatistics(
                totalLines = 0,
                codeLines = 0,
                commentLines = 0,
                blankLines = 0,
                complexity = 0
            ),
            issues = emptyList()
        )
    }

    private fun genericAnalysis(file: File): AnalysisResult {
        val content = file.readText()
        return AnalysisResult(
            fileType = "Generic",
            structure = mapOf("size" to file.length()),
            statistics = FileStatistics(
                totalLines = content.lines().size,
                codeLines = content.lines().size,
                commentLines = 0,
                blankLines = content.lines().count { it.isBlank() },
                complexity = 1
            ),
            issues = emptyList()
        )
    }

    private fun calculateJsonDepth(json: JSONObject, depth: Int = 1): Int {
        var maxDepth = depth
        json.keys().forEach { key ->
            when (val value = json.get(key)) {
                is JSONObject -> maxDepth = maxOf(maxDepth, calculateJsonDepth(value, depth + 1))
                is JSONArray -> {
                    for (i in 0 until value.length()) {
                        val item = value.optJSONObject(i)
                        if (item != null) {
                            maxDepth = maxOf(maxDepth, calculateJsonDepth(item, depth + 1))
                        }
                    }
                }
            }
        }
        return maxDepth
    }

    private fun countArrays(json: JSONObject): Int {
        var count = 0
        json.keys().forEach { key ->
            when (val value = json.get(key)) {
                is JSONArray -> count++
                is JSONObject -> count += countArrays(value)
            }
        }
        return count
    }

    private fun validateJson(json: JSONObject): List<Issue> {
        val issues = mutableListOf<Issue>()
        // Базовая валидация
        return issues
    }

    private fun calculateCyclomaticComplexity(content: String): Int {
        val branches = Regex("""\b(if|when|for|while|catch|throw|return|&&|\|\|)\b""")
        return branches.findAll(content).count() + 1
    }

    private fun countClasses(content: String): Int {
        return Regex("""\b(class|interface|object)\s+\w+""").findAll(content).count()
    }

    private fun countMethods(content: String): Int {
        return Regex("""\bfun\s+\w+""").findAll(content).count()
    }

    private fun countImports(content: String): Int {
        return Regex("""^import\s+""", RegexOption.MULTILINE).findAll(content).count()
    }

    private fun lintCode(content: String, extension: String): List<Issue> {
        val issues = mutableListOf<Issue>()
        val lines = content.lines()
        
        lines.forEachIndexed { index, line ->
            if (line.length > 120) {
                issues.add(Issue(
                    Severity.WARNING,
                    index + 1,
                    "Line exceeds 120 characters",
                    "max-line-length"
                ))
            }
            if (line.contains("TODO") || line.contains("FIXME")) {
                issues.add(Issue(
                    Severity.INFO,
                    index + 1,
                    "Found TODO/FIXME marker",
                    "todo-marker"
                ))
            }
        }
        
        return issues
    }
}
