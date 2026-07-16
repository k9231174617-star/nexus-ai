package com.nexus.agent.core.cicd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class DeploymentRecord(
    val environment: String,
    val version: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "pending",
    val notes: String = "",
)

@Singleton
class DeployManager @Inject constructor() {
    private val history = mutableListOf<DeploymentRecord>()

    suspend fun deploy(environment: String, version: String): String =
        withContext(Dispatchers.IO) {
            val record = DeploymentRecord(environment, version)
            history.add(record)
            // In production: call deployment API (Railway, Fly.io, K8s, etc.)
            "Deployment of v$version to $environment initiated"
        }

    fun getHistory(environment: String? = null): List<DeploymentRecord> =
        if (environment != null) history.filter { it.environment == environment }
        else history.toList()

    fun getLatest(environment: String): DeploymentRecord? =
        history.filter { it.environment == environment }.maxByOrNull { it.timestamp }
}