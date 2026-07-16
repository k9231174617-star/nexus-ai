package com.nexus.agent.core.rag

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentParser @Inject constructor() {

    fun clean(text: String): String = text
        .replace(Regex("\\s+"), " ")
        .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"), "")
        .replace(Regex("(\\n\\s*){3,}"), "\n\n")
        .trim()

    fun splitBySentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?])\\s+"))
            .filter { it.length > 10 }

    fun splitByParagraphs(text: String): List<String> =
        text.split(Regex("\\n\\n+"))
            .filter { it.trim().isNotBlank() }

    fun extractKeywords(text: String, topN: Int = 10): List<String> {
        val stopWords = setOf(
            "the","a","an","is","it","in","on","at","to","for",
            "of","and","or","but","with","as","that","this","are","was","be"
        )
        return text.lowercase()
            .split(Regex("[^a-zа-яё]+"))
            .filter { it.length > 3 && it !in stopWords }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(topN)
            .map { it.key }
    }

    fun detectLanguage(text: String): String {
        val cyrillicCount = text.count { it in 'а'..'я' || it in 'А'..'Я' }
        return if (cyrillicCount > text.length * 0.2) "ru" else "en"
    }
}