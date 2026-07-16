package com.nexus.agent.data.remote

object FreeLLMEndpoints {
    const val OPENROUTER_BASE = "https://openrouter.ai/api/v1"
    const val OPENROUTER_CHAT = "$OPENROUTER_BASE/chat/completions"
    const val OPENROUTER_MODELS = "$OPENROUTER_BASE/models"

    const val TOGETHER_BASE = "https://api.together.xyz/v1"
    const val TOGETHER_CHAT = "$TOGETHER_BASE/chat/completions"

    const val GROQ_BASE = "https://api.groq.com/openai/v1"
    const val GROQ_CHAT = "$GROQ_BASE/chat/completions"

    const val OLLAMA_BASE = "http://localhost:11434/api"
    const val OLLAMA_CHAT = "$OLLAMA_BASE/chat"
    const val OLLAMA_GENERATE = "$OLLAMA_BASE/generate"

    val FREE_MODELS_OPENROUTER = mapOf(
        "main"      to "mistralai/mistral-7b-instruct:free",
        "code"      to "deepseek/deepseek-coder:free",
        "universal" to "nousresearch/nous-hermes-2-mixtral-8x7b-dpo:free",
    )
}