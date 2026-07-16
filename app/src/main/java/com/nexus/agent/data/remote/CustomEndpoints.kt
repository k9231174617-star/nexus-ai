package com.nexus.agent.data.remote

object CustomEndpoints {
    const val OPENAI_BASE   = "https://api.openai.com/v1"
    const val OPENAI_CHAT   = "$OPENAI_BASE/chat/completions"
    const val OPENAI_MODELS = "$OPENAI_BASE/models"

    const val ANTHROPIC_BASE = "https://api.anthropic.com/v1"
    const val ANTHROPIC_CHAT = "$ANTHROPIC_BASE/messages"

    const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta"
    const val GEMINI_CHAT = "$GEMINI_BASE/models/%s:generateContent"

    val OPENAI_MODELS = mapOf(
        "gpt-4o"       to "gpt-4o",
        "gpt-4o-mini"  to "gpt-4o-mini",
        "gpt-4-turbo"  to "gpt-4-turbo",
    )

    val ANTHROPIC_MODELS = mapOf(
        "claude-3-5-sonnet" to "claude-sonnet-4-6",
        "claude-3-haiku"    to "claude-haiku-4-5-20251001",
    )

    val GEMINI_MODELS = mapOf(
        "gemini-pro"        to "gemini-pro",
        "gemini-pro-vision" to "gemini-pro-vision",
        "gemini-1.5-pro"    to "gemini-1.5-pro",
    )
}