package com.nexus.agent.core.cicd

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class CICDIntegrationTest {

    @Mock
    private lateinit var buildTrigger: BuildTrigger

    @Mock
    private lateinit var deployManager: DeployManager

    @Mock
    private lateinit var pipelineParser: PipelineParser

    private lateinit var cicdIntegration: CICDIntegration

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        cicdIntegration = CICDIntegration(buildTrigger, deployManager, pipelineParser)
    }

    @Test
    fun `parsePipeline converts YAML to pipeline config`() = runTest {
        val yaml = """
            stages:
              - build
              - test
              - deploy
            build:
              script: ./gradlew build
            test:
              script: ./gradlew test
            deploy:
              script: ./deploy.sh
        """.trimIndent()
        
        val expectedPipeline = Pipeline(
            stages = listOf("build", "test", "deploy"),
            jobs = mapOf(
                "build" to Job(listOf("./gradlew build")),
                "test" to Job(listOf("./gradlew test")),
                "deploy" to Job(listOf("./deploy.sh"))
            )
        )
        
        `when`(pipelineParser.parse(yaml)).thenReturn(expectedPipeline)
        
        val result = cicdIntegration.parsePipeline(yaml)
        
        assertEquals(3, result.stages.size)
        assertTrue(result.jobs.containsKey("build"))
    }

    @Test
    fun `parsePipeline throws on invalid YAML`() = runTest {
        val invalidYaml = "invalid: ["
        
        `when`(pipelineParser.parse(invalidYaml)).thenThrow(ParseException("Invalid YAML"))
        
        try {
            cicdIntegration.parsePipeline(invalidYaml)
            fail("Should throw exception")
        } catch (e: ParseException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `triggerBuild starts pipeline execution`() = runTest {
        val pipeline = Pipeline(
            stages = listOf("build"),
            jobs = mapOf("build" to Job(listOf("./gradlew build")))
        )
        val buildId = "build-123"
        
        `when`(buildTrigger.start(pipeline)).thenReturn(BuildResult(buildId, BuildStatus.RUNNING))
        
        val result = cicdIntegration.triggerBuild(pipeline)
        
        assertEquals(buildId, result.id)
        assertEquals(BuildStatus.RUNNING, result.status)
    }

    @Test
    fun `getBuildStatus returns current state`() = runTest {
        val buildId = "build-123"
        
        `when`(buildTrigger.getStatus(buildId)).thenReturn(BuildStatus.SUCCESS)
        
        val status = cicdIntegration.getBuildStatus(buildId)
        
        assertEquals(BuildStatus.SUCCESS, status)
    }

    @Test
    fun `cancelBuild stops running build`() = runTest {
        val buildId = "build-123"
        
        cicdIntegration.cancelBuild(buildId)
        
        verify(buildTrigger).cancel(buildId)
    }

    @Test
    fun `deployArtifact triggers deployment`() = runTest {
        val artifactPath = "/build/app.apk"
        val environment = "staging"
        val deployId = "deploy-456"
        
        `when`(deployManager.deploy(artifactPath, environment)).thenReturn(
            DeployResult(deployId, DeployStatus.IN_PROGRESS)
        )
        
        val result = cicdIntegration.deployArtifact(artifactPath, environment)
        
        assertEquals(deployId, result.id)
        assertEquals(DeployStatus.IN_PROGRESS, result.status)
    }

    @Test
    fun `getDeployStatus returns deployment state`() = runTest {
        val deployId = "deploy-456"
        
        `when`(deployManager.getStatus(deployId)).thenReturn(DeployStatus.COMPLETED)
        
        val status = cicdIntegration.getDeployStatus(deployId)
        
        assertEquals(DeployStatus.COMPLETED, status)
    }

    @Test
    fun `rollbackDeploy reverts to previous version`() = runTest {
        val deployId = "deploy-456"
        
        `when`(deployManager.rollback(deployId)).thenReturn(
            DeployResult("deploy-457", DeployStatus.IN_PROGRESS)
        )
        
        val result = cicdIntegration.rollbackDeploy(deployId)
        
        assertEquals("deploy-457", result.id)
    }

    @Test
    fun `getBuildLogs returns execution output`() = runTest {
        val buildId = "build-123"
        val logs = listOf("Step 1: Compiling...", "Step 2: Tests passed")
        
        `when`(buildTrigger.getLogs(buildId)).thenReturn(logs)
        
        val result = cicdIntegration.getBuildLogs(buildId)
        
        assertEquals(2, result.size)
        assertTrue(result[0].contains("Compiling"))
    }

        @Test
    fun `validatePipeline checks for required stages`() = runTest {
        val pipeline = Pipeline(
            stages = emptyList(),
            jobs = emptyMap()
        )
        
        `when`(pipelineParser.validate(pipeline)).thenReturn(
            ValidationResult(false, listOf("Missing stages", "No jobs defined"))
        )
        
        val result = cicdIntegration.validatePipeline(pipeline)
        
        assertFalse(result.isValid)
        assertEquals(2, result.errors.size)
    }

    @Test
    fun `retryFailedBuild restarts failed pipeline`() = runTest {
        val buildId = "build-123"
        val newBuildId = "build-124"
        
        `when`(buildTrigger.retry(buildId)).thenReturn(
            BuildResult(newBuildId, BuildStatus.RUNNING)
        )
        
        val result = cicdIntegration.retryFailedBuild(buildId)
        
        assertEquals(newBuildId, result.id)
        assertEquals(BuildStatus.RUNNING, result.status)
    }

    @Test
    fun `getPipelineHistory returns past executions`() = runTest {
        val pipelineName = "android-release"
        val history = listOf(
            BuildResult("build-1", BuildStatus.SUCCESS),
            BuildResult("build-2", BuildStatus.FAILED)
        )
        
        `when`(buildTrigger.getHistory(pipelineName, limit = 10)).thenReturn(history)
        
        val result = cicdIntegration.getPipelineHistory(pipelineName, limit = 10)
        
        assertEquals(2, result.size)
    }

    @Test
    fun `getBuildArtifacts returns output files`() = runTest {
        val buildId = "build-123"
        val artifacts = listOf("app-debug.apk", "app-release.apk")
        
        `when`(buildTrigger.getArtifacts(buildId)).thenReturn(artifacts)
        
        val result = cicdIntegration.getBuildArtifacts(buildId)
        
        assertEquals(2, result.size)
        assertTrue(result.contains("app-release.apk"))
    }

    @Test
    fun `deployArtifact to production requires approval`() = runTest {
        val artifactPath = "/build/app.apk"
        val environment = "production"
        
        `when`(deployManager.requiresApproval(environment)).thenReturn(true)
        
        try {
            cicdIntegration.deployArtifact(artifactPath, environment)
            fail("Should throw exception")
        } catch (e: ApprovalRequiredException) {
            assertTrue(e.message.contains("approval"))
        }
    }

    @Test
    fun `approveDeploy allows production deployment`() = runTest {
        val deployId = "deploy-456"
        
        `when`(deployManager.approve(deployId)).thenReturn(
            DeployResult(deployId, DeployStatus.IN_PROGRESS)
        )
        
        val result = cicdIntegration.approveDeploy(deployId)
        
        assertEquals(DeployStatus.IN_PROGRESS, result.status)
    }

    @Test
    fun `getDeployMetrics returns success rate`() = runTest {
        `when`(deployManager.getMetrics()).thenReturn(
            DeployMetrics(total = 100, successful = 95, failed = 5, successRate = 0.95)
        )
        
        val metrics = cicdIntegration.getDeployMetrics()
        
        assertEquals(0.95, metrics.successRate, 0.001)
    }

    @Test
    fun `triggerBuildFromGitHook handles push event`() = runTest {
        val repoUrl = "https://github.com/user/repo.git"
        val branch = "main"
        val commit = "abc123"
        val buildId = "build-auto-1"
        
        `when`(buildTrigger.startFromGitHook(repoUrl, branch, commit)).thenReturn(
            BuildResult(buildId, BuildStatus.RUNNING)
        )
        
        val result = cicdIntegration.triggerBuildFromGitHook(repoUrl, branch, commit)
        
        assertEquals(buildId, result.id)
    }

    @Test
    fun `cancelAllBuilds stops everything`() = runTest {
        cicdIntegration.cancelAllBuilds()
        
        verify(buildTrigger).cancelAll()
    }

    @Test
    fun `getActiveBuilds returns running pipelines`() = runTest {
        val activeBuilds = listOf(
            BuildResult("build-1", BuildStatus.RUNNING),
            BuildResult("build-2", BuildStatus.RUNNING)
        )
        
        `when`(buildTrigger.getActive()).thenReturn(activeBuilds)
        
        val result = cicdIntegration.getActiveBuilds()
        
        assertEquals(2, result.size)
        assertTrue(result.all { it.status == BuildStatus.RUNNING })
    }
}
