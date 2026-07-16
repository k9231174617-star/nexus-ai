package com.nexus.agent.ui.code

import android.graphics.Color

/**
 * Подсветка синтаксиса для различных языков программирования.
 * Поддерживает Kotlin, Java, XML, JSON, Smali, Markdown.
 */
class SyntaxHighlighter {

    data class Span(val start: Int, val end: Int, val color: Int)

    private var language: String = "kotlin"

    // Color palette
    private val colorKeyword = Color.parseColor("#FF7B72")      // Red-pink
    private val colorString = Color.parseColor("#A5D6FF")       // Light blue
    private val colorComment = Color.parseColor("#8B949E")       // Gray
    private val colorNumber = Color.parseColor("#79C0FF")        // Blue
    private val colorFunction = Color.parseColor("#D2A8FF")     // Purple
    private val colorType = Color.parseColor("#FFA657")         // Orange
    private val colorAnnotation = Color.parseColor("#FF7B72")    // Red
    private val colorTag = Color.parseColor("#7EE787")          // Green

    fun setLanguage(lang: String) {
        this.language = lang.lowercase()
    }

    fun highlight(code: String): List<Span> {
        return when (language) {
            "kotlin", "java" -> highlightJavaLike(code)
            "xml" -> highlightXml(code)
            "json" -> highlightJson(code)
            "smali" -> highlightSmali(code)
            "markdown", "md" -> highlightMarkdown(code)
            else -> emptyList()
        }
    }

    private fun highlightJavaLike(code: String): List<Span> {
        val spans = mutableListOf<Span>()

        // Keywords
        val keywords = setOf(
            "package", "import", "class", "interface", "object", "data", "sealed",
            "fun", "val", "var", "const", "lateinit", "by", "lazy",
            "if", "else", "when", "for", "while", "do", "break", "continue",
            "return", "throw", "try", "catch", "finally", "true", "false", "null",
            "public", "private", "protected", "internal", "open", "abstract", "final",
            "override", "suspend", "inline", "crossinline", "noinline", "reified",
            "companion", "init", "constructor", "super", "this", "is", "as", "in", "!in",
            "extends", "implements", "static", "void", "int", "long", "float", "double",
            "boolean", "char", "byte", "short", "new", "instanceof", "synchronized"
        )

        // Find keywords
        val keywordPattern = "\\b(${keywords.joinToString("|")})\\b".toRegex()
        keywordPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorKeyword))
        }

        // Strings
        val stringPattern = "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'".toRegex()
        stringPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorString))
        }

        // Comments
        val lineCommentPattern = "//.*$".toRegex(RegexOption.MULTILINE)
        lineCommentPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorComment))
        }

        val blockCommentPattern = "/\\*[\\s\\S]*?\\*/".toRegex()
        blockCommentPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorComment))
        }

        // Numbers
        val numberPattern = "\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFLl]?\\b".toRegex()
        numberPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorNumber))
        }

        // Functions
        val functionPattern = "\\b([a-zA-Z_]\\w*)\\s*(?=\\()".toRegex()
        functionPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorFunction))
        }

        // Types (capitalized words)
        val typePattern = "\\b[A-Z]\\w+\\b".toRegex()
        typePattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorType))
        }

        // Annotations
        val annotationPattern = "@\\w+(\\([^)]*\\))?".toRegex()
        annotationPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorAnnotation))
        }

        return spans.sortedBy { it.start }
    }

    private fun highlightXml(code: String): List<Span> {
        val spans = mutableListOf<Span>()

        // Tags
        val tagPattern = "</?[\\w:]+".toRegex()
        tagPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorTag))
        }

        val closeTagPattern = "/?>".toRegex()
        closeTagPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorTag))
        }

        // Attributes
        val attrPattern = "\\s([\\w:-]+)=".toRegex()
        attrPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first + 1, match.range.last, colorKeyword))
        }

        // Strings in attributes
        val stringPattern = "\"[^\"]*\"".toRegex()
        stringPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorString))
        }

        // Comments
        val commentPattern = "<!--[\\s\\S]*?-->".toRegex()
        commentPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorComment))
        }

        return spans.sortedBy { it.start }
    }

    private fun highlightJson(code: String): List<Span> {
        val spans = mutableListOf<Span>()

        // Keys
        val keyPattern = "\"([^\"\\\\]|\\\\.)*\"\\s*:".toRegex()
        keyPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last, colorKeyword))
        }

        // Strings
        val stringPattern = "\"([^\"\\\\]|\\\\.)*\"".toRegex()
        stringPattern.findAll(code).forEach { match ->
            if (!match.value.endsWith(":")) {
                spans.add(Span(match.range.first, match.range.last + 1, colorString))
            }
        }

        // Numbers
        val numberPattern = "-?\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b".toRegex()
        numberPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorNumber))
        }

        // Booleans and null
        val boolPattern = "\\b(true|false|null)\\b".toRegex()
        boolPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorAnnotation))
        }

        return spans.sortedBy { it.start }
    }

    private fun highlightSmali(code: String): List<Span> {
        val spans = mutableListOf<Span>()

        // Directives
        val directivePattern = "\\.\\w+".toRegex()
        directivePattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorKeyword))
        }

        // Types (L...;)
        val typePattern = "L[\\w/$]+;".toRegex()
        typePattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorType))
        }

        // Comments
        val commentPattern = "#.*$".toRegex(RegexOption.MULTILINE)
        commentPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorComment))
        }

        // Strings
        val stringPattern = "\"[^\"]*\"".toRegex()
        stringPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorString))
        }

        return spans.sortedBy { it.start }
    }

    private fun highlightMarkdown(code: String): List<Span> {
        val spans = mutableListOf<Span>()

        // Headers
        val headerPattern = "^#{1,6}\\s.*$".toRegex(RegexOption.MULTILINE)
        headerPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorKeyword))
        }

        // Bold/Italic
        val boldPattern = "\\*\\*[^*]+\\*\\*|__[^_]+__".toRegex()
        boldPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorFunction))
        }

        // Code inline
        val codePattern = "`[^`]+`".toRegex()
        codePattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorString))
        }

        // Code blocks
        val codeBlockPattern = "```[\\s\\S]*?```".toRegex()
        codeBlockPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorString))
        }

        // Links
        val linkPattern = "\\[([^\\]]+)\\]\\([^)]+\\)".toRegex()
        linkPattern.findAll(code).forEach { match ->
            spans.add(Span(match.range.first, match.range.last + 1, colorTag))
        }

        return spans.sortedBy { it.start }
    }
}
