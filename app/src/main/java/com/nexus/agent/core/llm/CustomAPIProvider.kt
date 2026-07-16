package com.nexus.agent.core.llm

import com.nexus.agent.data.local.SettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomAPIProvider @Inject constructor(
    private val settingsDao: SettingsDao,
    private val client: OkHttpClient,
) {
    fun isConfigured(): Boolean {
        val s = settingsDao.getSettingsSync()
        return !s?.apiKey.isNullOrBlank()
    }

    fun stream(
        messages: List<Map<String, String>>,
        model: String,
    ): Flow<String> = flow {
        val settings = settingsDao.getSettingsSync() ?: return@flow
        val endpoint = settings.endpoint
            ?: "https://api.openai.com/v1/chat/completions"

        val body = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("temperature", settings.temperature)
            put("max_tokens", settings.maxTokens)
            put("messages", JSONArray().apply {
                messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg["role"])
                        put("content", msg["content"])
                    })
                }
            })
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("API Error ${response.code}: ${response.body?.string()}")
        }

        response.body?.source()?.let { source ->
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val json = JSONObject(data)
                        val content = json
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("delta")
                            .optString("content", "")
                        if (content.isNotEmpty()) emit(content)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    suspend fun complete(
        messages: List<Map<String, String>>,
        model: String,
    ): String {
        val buffer = StringBuilder()
        stream(messages, model).collect { buffer.append(it) }
        return buffer.toString()
    }
}