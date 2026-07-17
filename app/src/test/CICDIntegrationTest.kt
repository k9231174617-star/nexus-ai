package com.nexus.agent.core.cicd

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import okhttp3.OkHttpClient

class CICDIntegrationTest {

    @Mock
    private lateinit var client: OkHttpClient

    @Mock
    private lateinit var buildTrigger: BuildTrigger

    @Mock
    private lateinit var deployManager: DeployManager

    private lateinit var cicdIntegration: CICDIntegration

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        cicdIntegration = CICDIntegration(client, buildTrigger, deployManager)
    }

    @Test
    fun `triggerBuild calls build trigger`() = runTest {
        `when`(buildTrigger.trigger("main")).thenReturn(true)

        val result = cicdIntegration.triggerBuild("main")

        assertTrue(result)
        verify(buildTrigger).trigger("main")
    }

    @Test
    fun `deploy calls deploy manager`() = runTest {
        cicdIntegration.deploy("production")

        verify(deployManager).deploy("production")
    }
}
