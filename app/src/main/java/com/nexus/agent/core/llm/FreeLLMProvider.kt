package com.nexus.agent.core.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreeLLMProvider @Inject constructor(
    private val client: OkHttpClient,
    private val streamingHandler: com.nexus.agent.core.chat.StreamingHandler,
) {
    companion object {
        // OpenRouter free tier
        private const val BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
        private val FREE_MODELS = mapOf(
            "main"      to "mistralai/mistral-7b-instruct:free",
            "code"      to "deepseek/deepseek-coder:free",
            "universal" to "nousresearch/nous-hermes-2-mixtral-8x7b-dpo:free",
        )
    }

    fun stream(messages: List<Map<String, String>>, model: String): Flow<String> = flow {
        val resolvedModel = FREE_MODELS[model] ?: FREE_MODELS["main"]!!
        val body = buildRequestBody(messages, resolvedModel, stream = true)
        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://nexus.ai")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("FreeLLM Error ${response.code}: ${response.body?.string()}")
        }
        streamingHandler.parseSSEStream(response).collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    suspend fun complete(messages: List<Map<String, String>>, model: String): String =
        withContext(Dispatchers.IO) {
            val resolvedModel = FREE_MODELS[model] ?: FREE_MODELS["main"]!!
            val body = buildRequestBody(messages, resolvedModel, stream = false)
            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://nexus.ai")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("FreeLLM Error ${response.code}")
            val json = JSONObject(response.body?.string() ?: "{}")
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }

    private fun buildRequestBody(
        messages: List<Map<String, String>>,
        model: String,
        stream: Boolean,
    ): String = JSONObject().apply {
        put("model", model)
        put("stream", stream)
        put("max_tokens", 2048)
        put("temperature", 0.7)
        put("messages", JSONArray().apply {
            messages.forEach { msg ->
                put(JSONObject().apply {
                    put("role", msg["role"])
                    put("content", msg["content"])
                })
            }
        })
    }.toString()
}