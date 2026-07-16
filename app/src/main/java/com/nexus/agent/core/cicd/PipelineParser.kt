package com.nexus.agent.core.cicd

import javax.inject.Inject
import javax.inject.Singleton

data class PipelineConfig(
    val stages: List<String>,
    val jobs: List<PipelineJob>,
    val variables: Map<String, String>,
    val trigger: String?,
)

data class PipelineJob(
    val name: String,
    val stage: String,
    val script: List<String>,
    val image: String?,
    val environment: String?,
)

@Singleton
class PipelineParser @Inject constructor() {

    fun parseGitLabCI(yaml: String): PipelineConfig {
        // Simplified YAML parser for .gitlab-ci.yml
        val lines = yaml.lines()
        val stages = mutableListOf<String>()
        val variables = mutableMapOf<String, String>()
        val jobs = mutableListOf<PipelineJob>()

        var currentJob: String? = null
        var currentStage = ""
        var currentScript = mutableListOf<String>()
        var currentImage: String? = null

        lines.forEach { line ->
            when {
                line.startsWith("stages:") -> { /* next lines are stages */ }
                line.trimStart().startsWith("- ") && stages.isEmpty() ->
                    stages.add(line.trim().removePrefix("- "))
                line.matches(Regex("[a-zA-Z_-]+:")) && !line.startsWith(" ") -> {
                    currentJob?.let {
                        jobs.add(PipelineJob(it, currentStage, currentScript.toList(), currentImage, null))
                    }
                    currentJob = line.removeSuffix(":")
                    currentScript = mutableListOf()
                    currentStage = ""
                    currentImage = null
                }
                line.trimStart().startsWith("stage:") ->
                    currentStage = line.substringAfter("stage:").trim()
                line.trimStart().startsWith("image:") ->
                    currentImage = line.substringAfter("image:").trim()
                line.trimStart().startsWith("- ") && currentJob != null ->
                    currentScript.add(line.trim().removePrefix("- "))
            }
        }

        currentJob?.let {
            jobs.add(PipelineJob(it, currentStage, currentScript.toList(), currentImage, null))
        }

        return PipelineConfig(stages, jobs, variables, null)
    }

    fun parseGitHubActions(yaml: String): PipelineConfig {
        return PipelineConfig(
            stages = listOf("build", "test", "deploy"),
            jobs = emptyList(),
            variables = emptyMap(),
            trigger = "push",
        )
    }
}