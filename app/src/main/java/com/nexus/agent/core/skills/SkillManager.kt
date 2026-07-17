package com.nexus.agent.core.skills

import com.nexus.agent.core.agents.AgentCoordinator
import com.nexus.agent.core.agents.AgentSpec
import com.nexus.agent.core.agents.AgentType
import com.nexus.agent.core.agents.AgentTask
import com.nexus.agent.core.mcp.MCPClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Skill Manager — bridges skills from awesome-agent-skills catalog
 * with the AgentCoordinator and MCPClient systems.
 */
@Singleton
class SkillManager @Inject constructor(
    private val mcpClient: MCPClient,
    private val agentCoordinator: AgentCoordinator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _activeSkills = MutableStateFlow<List<String>>(emptyList())
    val activeSkills: StateFlow<List<String>> = _activeSkills

    companion object {
        private const val TAG = "SkillManager"
        private val CATEGORY_AGENT_MAP = mapOf(
            "mcp" to AgentType.UNIVERSAL,
            "llm-ai" to AgentType.MAIN,
            "security" to AgentType.REVIEW,
            "android" to AgentType.CODE,
            "browser" to AgentType.RESEARCH,
            "database" to AgentType.CODE,
            "testing" to AgentType.TESTING,
            "observability" to AgentType.OBSERVABILITY,
            "media" to AgentType.UNIVERSAL,
            "cicd" to AgentType.PLANNER,
        )
    }

    fun initialize() {
        mcpClient.registerDefaultSkills()
        registerSkillAgents()
        android.util.Log.i(TAG, "SkillManager initialized with ${mcpClient.skills.value.size} skills")
    }

    private fun registerSkillAgents() {
        val skillAgents = listOf(
            AgentSpec(type = AgentType.REVIEW, name = "Security Auditor (Trail of Bits)",
                description = "Static analysis, supply chain audit, fuzzing, crypto review",
                capabilities = listOf("security", "audit", "vulnerability", "static-analysis", "fuzzing")),
            AgentSpec(type = AgentType.UNIVERSAL, name = "MCP Integrator (Composio/Cloudflare)",
                description = "MCP server creation, 1000+ app integrations, AI agent SDK",
                capabilities = listOf("mcp", "integration", "api", "tools")),
            AgentSpec(type = AgentType.RESEARCH, name = "Browser Automator (Stagehand/Playwright)",
                description = "AI-driven browser automation, Playwright testing, web scraping",
                capabilities = listOf("browser", "automation", "playwright", "scraping")),
            AgentSpec(type = AgentType.TESTING, name = "Test Generator (TestMu/Sentry)",
                description = "API testing, E2E testing, CI/CD pipelines, observability setup",
                capabilities = listOf("testing", "api-test", "e2e", "cicd", "monitoring")),
            AgentSpec(type = AgentType.CODE, name = "Android Developer (Espresso/Expo)",
                description = "Android UI testing, mobile automation, React Native optimization",
                capabilities = listOf("android", "mobile", "espresso", "react-native")),
        )
        skillAgents.forEach { agentCoordinator.registerAgent(it) }
        android.util.Log.i(TAG, "Registered ${skillAgents.size} skill-based agents")
    }

    suspend fun routeTask(query: String, context: Map<String, String> = emptyMap()): String {
        val matches = mcpClient.findSkills(query)
        if (matches.isEmpty()) return "No matching skill found for: $query"

        val bestSkill = matches.first()
        val agentType = CATEGORY_AGENT_MAP[bestSkill.category] ?: AgentType.MAIN

        val task = AgentTask(
            type = bestSkill.category,
            description = "${bestSkill.name}: ${bestSkill.description}",
            context = context + mapOf(
                "skill" to bestSkill.name,
                "skillUrl" to bestSkill.url,
                "skillCompany" to bestSkill.company,
            ),
        )
        agentCoordinator.routeTask(task)
        _activeSkills.value = _activeSkills.value + bestSkill.id

        return """
            |🧰 Skill: ${bestSkill.name}
            |👤 Source: ${bestSkill.company}
            |📂 Category: ${bestSkill.category}
            |📝 ${bestSkill.description}
            |📎 ${bestSkill.url}
            |✅ Routed to: ${agentType.name} agent
        """.trimMargin()
    }

    fun getAllSkills() = mcpClient.skills.value
    fun getSkillsByCategory(category: String) = mcpClient.getSkillsByCategory(category)
    fun searchSkills(query: String) = mcpClient.findSkills(query)

    fun getStats(): Map<String, Any> = mcpClient.getSkillStats() + mapOf(
        "activeSkills" to _activeSkills.value.size,
    )

    fun destroy() { scope.cancel() }
}
