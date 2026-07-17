package com.nexus.agent.core.mcp

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Model Context Protocol (MCP) Client
 * Connects to MCP servers via JSON-RPC over WebSocket/SSE.
 * Inspired by Ruflo's MCP integration for tool discovery and execution.
 */
data class MCPServer(
    val id: String,
    val name: String,
    val url: String,
    val transport: String = "websocket", // websocket, sse, stdio
    val status: ServerStatus = ServerStatus.DISCONNECTED,
)

enum class ServerStatus { CONNECTED, DISCONNECTED, ERROR }

data class MCPTool(
    val name: String,
    val description: String,
    val inputSchema: JSONObject = JSONObject(),
    val serverId: String = "",
)

data class MCPResource(
    val uri: String,
    val name: String,
    val description: String = "",
    val mimeType: String = "text/plain",
)

data class MCPPrompt(
    val name: String,
    val description: String = "",
    val arguments: List<String> = emptyList(),
)

@Singleton
class MCPClient @Inject constructor(
    private val client: OkHttpClient,
) {
    private val _servers = MutableStateFlow<List<MCPServer>>(emptyList())
    val servers: StateFlow<List<MCPServer>> = _servers

    private val _tools = MutableStateFlow<List<MCPTool>>(emptyList())
    val tools: StateFlow<List<MCPTool>> = _tools

    private val sessions = ConcurrentHashMap<String, WebSocket>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Connect to an MCP server */
    suspend fun connect(server: MCPServer): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(server.url.replace("ws://", "http://").replace("wss://", "https://"))
                .addHeader("Content-Type", "application/json")
                .build()

            // Initialize connection via SSE endpoint
            val initRequest = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", "initialize")
                put("params", JSONObject().apply {
                    put("protocolVersion", "0.1.0")
                    put("capabilities", JSONObject().apply {
                        put("tools", JSONObject())
                        put("resources", JSONObject())
                        put("prompts", JSONObject())
                    })
                    put("clientInfo", JSONObject().apply {
                        put("name", "nexus-ai")
                        put("version", "2.0")
                    })
                })
                put("id", "init-1")
            }

            val response = client.newCall(
                request.newBuilder()
                    .post(initRequest.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)

                // Discover tools
                val toolsResult = json.optJSONObject("result")?.optJSONObject("capabilities")
                if (toolsResult != null) {
                    val toolsArr = toolsResult.optJSONArray("tools") ?: JSONArray()
                    for (i in 0 until toolsArr.length()) {
                        val tool = toolsArr.getJSONObject(i)
                        _tools.value = _tools.value + MCPTool(
                            name = tool.getString("name"),
                            description = tool.optString("description", ""),
                            inputSchema = tool.optJSONObject("inputSchema") ?: JSONObject(),
                            serverId = server.id,
                        )
                    }
                }

                updateServer(server.id) { it.copy(status = ServerStatus.CONNECTED) }
                true
            } else {
                updateServer(server.id) { it.copy(status = ServerStatus.ERROR) }
                false
            }
        } catch (e: Exception) {
            updateServer(server.id) { it.copy(status = ServerStatus.ERROR) }
            false
        }
    }

    /** Execute a tool on the connected server */
    suspend fun executeTool(serverId: String, toolName: String, args: Map<String, Any?> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val request = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", "tools/call")
                put("params", JSONObject().apply {
                    put("name", toolName)
                    put("arguments", JSONObject(args.mapValues { it.value.toString() }))
                })
                put("id", "tool-${System.currentTimeMillis()}")
            }

            val server = _servers.value.find { it.id == serverId } ?: return@withContext ""
            val response = client.newCall(
                Request.Builder()
                    .url(server.url)
                    .post(request.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()

            response.body?.string() ?: ""
        }

    /** List available resources from server */
    suspend fun listResources(serverId: String): List<MCPResource> = withContext(Dispatchers.IO) {
        val request = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", "resources/list")
            put("id", "res-${System.currentTimeMillis()}")
        }

        val server = _servers.value.find { it.id == serverId } ?: return@withContext emptyList()
        val response = client.newCall(
            Request.Builder()
                .url(server.url)
                .post(request.toString().toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()

        val body = response.body?.string() ?: return@withContext emptyList()
        val json = JSONObject(body)
        val resources = json.optJSONObject("result")?.optJSONArray("resources") ?: JSONArray()
        (0 until resources.length()).map { i ->
            val r = resources.getJSONObject(i)
            MCPResource(
                uri = r.getString("uri"),
                name = r.optString("name", ""),
                description = r.optString("description", ""),
                mimeType = r.optString("mimeType", "text/plain"),
            )
        }
    }

    /** Disconnect from server */
    fun disconnect(serverId: String) {
        sessions.remove(serverId)?.close(1000, "Client disconnect")
        updateServer(serverId) { it.copy(status = ServerStatus.DISCONNECTED) }
    }

    /** Register a new server */
    fun registerServer(server: MCPServer) {
        if (_servers.value.none { it.id == server.id }) {
            _servers.value = _servers.value + server
        }
    }

    private fun updateServer(id: String, transform: (MCPServer) -> MCPServer) {
        _servers.value = _servers.value.map { if (it.id == id) transform(it) else it }
    }

    private fun String.toRequestBody(contentType: String) =
        RequestBody.create(MediaType.parse(contentType), this)

    /** Skill Registry — discover and route skills from awesome-agent-skills catalog */
    data class SkillDefinition(
        val id: String,
        val name: String,
        val company: String,
        val category: String,
        val description: String,
        val url: String,
        val tier: String, // "free" or "paid"
        val tags: List<String> = emptyList(),
    )

    private val _skills = MutableStateFlow<List<SkillDefinition>>(emptyList())
    val skills: StateFlow<List<SkillDefinition>> = _skills

    /** Register a skill from the catalog */
    fun registerSkill(skill: SkillDefinition) {
        if (_skills.value.none { it.id == skill.id }) {
            _skills.value = _skills.value + skill
        }
    }

    /** Register multiple skills at once */
    fun registerSkills(skills: List<SkillDefinition>) {
        val existingIds = _skills.value.map { it.id }.toSet()
        val newSkills = skills.filter { it.id !in existingIds }
        if (newSkills.isNotEmpty()) {
            _skills.value = _skills.value + newSkills
        }
    }

    /** Find skills matching a query */
    fun findSkills(query: String): List<SkillDefinition> {
        val q = query.lowercase()
        return _skills.value.filter { s ->
            s.name.lowercase().contains(q) ||
            s.description.lowercase().contains(q) ||
            s.tags.any { it.contains(q) } ||
            s.category.contains(q)
        }
    }

    /** Get skills by category */
    fun getSkillsByCategory(category: String): List<SkillDefinition> =
        _skills.value.filter { it.category == category }

    /** Get all skill categories */
    fun getSkillCategories(): List<String> =
        _skills.value.map { it.category }.distinct().sorted()

    /** Get skill stats */
    fun getSkillStats(): Map<String, Int> = mapOf(
        "total" to _skills.value.size,
        "categories" to getSkillCategories().size,
    )

    /** Route a request to the best matching skill */
    suspend fun routeToSkill(query: String): String {
        val matches = findSkills(query)
        if (matches.isEmpty()) return "No matching skill found for: $query"
        val best = matches.first()
        return "Routed to skill: ${best.name} (${best.company}) — ${best.description}"
    }

    /** Register default skills from awesome-agent-skills catalog */
    fun registerDefaultSkills() {
        registerSkills(listOf(
            // MCP & Tools
            SkillDefinition("mcp-builder", "MCP Builder", "anthropics", "mcp",
                "Create MCP servers to integrate external APIs", "https://officialskills.sh/anthropics/skills/mcp-builder", "free", listOf("mcp", "tools", "api")),
            SkillDefinition("composio", "Composio", "composiohq", "mcp",
                "Connect AI agents to 1000+ external apps", "https://officialskills.sh/composiohq/skills/composio", "free", listOf("mcp", "integration")),
            // LLM / AI
            SkillDefinition("gemini-api", "Gemini API", "google-gemini", "llm-ai",
                "Gemini API for text, chat, streaming, image generation", "https://officialskills.sh/google-gemini/skills/gemini-interactions-api", "free", listOf("llm", "gemini", "multimodal")),
            SkillDefinition("qdrant-skills", "Qdrant Vector Search", "qdrant", "llm-ai",
                "Vector search scaling, optimization, quality, monitoring", "https://officialskills.sh/qdrant/skills", "free", listOf("vector", "search", "rag")),
            // Security
            SkillDefinition("static-analysis", "Static Security Analysis", "trailofbits", "security",
                "Static security code analysis by Trail of Bits", "https://officialskills.sh/trailofbits/skills/static-analysis", "free", listOf("security", "code-review")),
            SkillDefinition("fuzzing", "Fuzz Testing", "trailofbits", "security",
                "Fuzz testing for vulnerability discovery", "https://officialskills.sh/trailofbits/skills/fuzzing", "free", listOf("security", "testing")),
            // Android
            SkillDefinition("espresso-skill", "Espresso UI Tests", "testmu-ai", "android",
                "Espresso UI tests for Android in Kotlin/Java", "https://github.com/LambdaTest/agent-skills/tree/main/espresso-skill", "free", listOf("android", "testing")),
            // Browser
            SkillDefinition("stagehand", "Stagehand Browser Automation", "browserbase", "browser",
                "AI-driven browser automation framework", "https://officialskills.sh/browserbase/skills/stagehand", "free", listOf("browser", "automation")),
            // Database
            SkillDefinition("redis-development", "Redis Development", "redis", "database",
                "Redis: data structures, vector search, caching", "https://officialskills.sh/redis/skills/redis-development", "free", listOf("database", "caching")),
            // Testing
            SkillDefinition("api-skill", "API Testing Suite", "testmu-ai", "testing",
                "Design, mock, document, test REST/GraphQL/gRPC APIs", "https://github.com/LambdaTest/agent-skills/tree/main/api-skill", "free", listOf("testing", "api")),
            // Observability
            SkillDefinition("sentry-feature-setup", "Sentry AI Monitoring", "getsentry", "observability",
                "Sentry: AI monitoring, OTel pipelines, alerts", "https://officialskills.sh/getsentry/skills/sentry-feature-setup", "free", listOf("monitoring", "sentry")),
            // Community
            SkillDefinition("recursive-decomposition", "Recursive Decomposition", "massimodeluisa", "community",
                "Handle 100+ files / 50k+ token context", "https://github.com/massimodeluisa/recursive-decomposition-skill", "free", listOf("context", "large-files")),
        ))
        android.util.Log.i("MCPClient", "Registered ${_skills.value.size} default skills")
    }

    fun destroy() {
        scope.cancel()
        sessions.values.forEach { it.close(1001, "Shutdown") }
        sessions.clear()
    }
}
