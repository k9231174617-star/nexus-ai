package com.nexus.agent.core.llm

import com.nexus.agent.core.chat.AgentType
import com.nexus.agent.data.local.SettingsDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRouter @Inject constructor(
    private val settingsDao: SettingsDao,
) {
    private val freeModels = mapOf(
        AgentType.MAIN      to "dolphin-2.6-mistral-7b",
        AgentType.CODE      to "deepseek-coder-v2",
        AgentType.UNIVERSAL to "nous-hermes-2-mixtral-8x7b",
    )

    suspend fun routeForAgent(agent: AgentType): String {
        val settings = settingsDao.getSettings()
        val apiKey = settings?.apiKey

        return if (!apiKey.isNullOrBlank()) {
            when (agent) {
                AgentType.MAIN      -> settings.mainModel ?: "gpt-4o-mini"
                AgentType.CODE      -> settings.codeModel ?: "gpt-4o-mini"
                AgentType.UNIVERSAL -> settings.uniModel  ?: "gpt-4o-mini"
            }
        } else {
            freeModels[agent] ?: "dolphin-2.6-mistral-7b"
        }
    }
}