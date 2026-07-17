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

    fun destroy() {
        scope.cancel()
        sessions.values.forEach { it.close(1001, "Shutdown") }
        sessions.clear()
    }
}
