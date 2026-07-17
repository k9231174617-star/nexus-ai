package com.nexus.agent.core.chat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class StreamingHandler(private val client: OkHttpClient) {

    fun streamCompletion(
        endpoint: String,
        apiKey: String,
        requestBody: JSONObject
    ): Flow<StreamEvent> = flow {
        val body = requestBody.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val call = client.newCall(request)

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    emit(StreamEvent.Error("HTTP ${response.code}"))
                    return@use
                }

                val source = response.body?.source() ?: run {
                    emit(StreamEvent.Error("Empty response body"))
                    return@use
                }

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: continue
                    if (!line.startsWith("data: ")) continue

                    val data = line.substring(6)
                    if (data == "[DONE]") {
                        emit(StreamEvent.Done)
                        break
                    }

                    try {
                        val json = JSONObject(data)
                        val choices = json.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = delta?.optString("content", "") ?: ""
                            if (content.isNotEmpty()) {
                                emit(StreamEvent.Chunk(content))
                            }
                        }
                    } catch (e: Exception) {
                        // Skip malformed chunks
                    }
                }
            }
        } catch (e: IOException) {
            emit(StreamEvent.Error(e.message ?: "Network error"))
        }
    }

    fun parseSSEStream(response: okhttp3.Response): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
        try {
            val source = response.body?.source() ?: return@flow
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue
                if (!line.startsWith("data: ")) continue
                val data = line.substring(6).trim()
                if (data == "[DONE]") break
                try {
                    val json = org.json.JSONObject(data)
                    val choices = json.getJSONArray("choices")
                    if (choices.length() > 0) {
                        val delta = choices.getJSONObject(0).optJSONObject("delta")
                        val content = delta?.optString("content", "") ?: ""
                        if (content.isNotEmpty()) emit(content)
                    }
                } catch (_: Exception) {}
            }
        } catch (e: java.io.IOException) {
            // Stream closed
        }
    }

    sealed class StreamEvent {
        data class Chunk(val content: String) : StreamEvent()
        object Done : StreamEvent()
        data class Error(val message: String) : StreamEvent()
    }
}
