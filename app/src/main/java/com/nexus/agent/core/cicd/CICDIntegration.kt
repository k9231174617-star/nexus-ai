package com.nexus.agent.core.cicd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class PipelineStatus(
    val id: String,
    val name: String,
    val status: String,
    val branch: String,
    val commitSha: String,
    val startedAt: String,
    val finishedAt: String?,
    val duration: Long,
    val webUrl: String,
)

@Singleton
class CICDIntegration @Inject constructor(
    private val client: OkHttpClient,
    private val buildTrigger: BuildTrigger,
    private val deployManager: DeployManager,
) {
    private var baseUrl: String = ""
    private var token: String = ""

    fun configure(baseUrl: String, token: String) {
        this.baseUrl = baseUrl
        this.token = token
    }

    suspend fun getPipelines(projectId: String): List<PipelineStatus> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/v4/projects/$projectId/pipelines?per_page=10")
                .addHeader("PRIVATE-TOKEN", token)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val arr = org.json.JSONArray(response.body?.string() ?: "[]")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PipelineStatus(
                    id = obj.getString("id"),
                    name = obj.optString("name", "Pipeline ${obj.getString("id")}"),
                    status = obj.getString("status"),
                    branch = obj.optString("ref", ""),
                    commitSha = obj.optString("sha", ""),
                    startedAt = obj.optString("created_at", ""),
                    finishedAt = obj.optString("finished_at").takeIf { it.isNotBlank() },
                    duration = obj.optLong("duration", 0),
                    webUrl = obj.optString("web_url", ""),
                )
            }
        }

    suspend fun triggerBuild(projectId: String, branch: String = "main"): String =
        withContext(Dispatchers.IO) {
            buildTrigger.trigger(baseUrl, token, projectId, branch)
        }

    suspend fun deploy(environment: String, version: String): String =
        withContext(Dispatchers.IO) {
            deployManager.deploy(environment, version)
        }

    suspend fun getBuildStatus(projectId: String, pipelineId: String): PipelineStatus? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$baseUrl/api/v4/projects/$projectId/pipelines/$pipelineId")
                    .addHeader("PRIVATE-TOKEN", token)
                    .build()
                val obj = JSONObject(client.newCall(request).execute().body?.string() ?: "{}")
                PipelineStatus(
                    id = obj.getString("id"),
                    name = obj.optString("name", ""),
                    status = obj.getString("status"),
                    branch = obj.optString("ref", ""),
                    commitSha = obj.optString("sha", ""),
                    startedAt = obj.optString("created_at", ""),
                    finishedAt = obj.optString("finished_at").takeIf { it.isNotBlank() },
                    duration = obj.optLong("duration", 0),
                    webUrl = obj.optString("web_url", ""),
                )
            }.getOrNull()
        }
}