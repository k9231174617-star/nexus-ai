package com.nexus.agent.core.cicd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildTrigger @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun trigger(
        baseUrl: String,
        token: String,
        projectId: String,
        branch: String,
        variables: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("ref", branch)
            if (variables.isNotEmpty()) {
                put("variables", org.json.JSONArray().apply {
                    variables.forEach { (k, v) ->
                        put(JSONObject().apply { put("key", k); put("value", v) })
                    }
                })
            }
        }
        val request = Request.Builder()
            .url("$baseUrl/api/v4/projects/$projectId/pipeline")
            .addHeader("PRIVATE-TOKEN", token)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext "Error: ${response.code}"
        val json = JSONObject(response.body?.string() ?: "{}")
        "Pipeline ${json.optString("id")} triggered on branch $branch"
    }
}