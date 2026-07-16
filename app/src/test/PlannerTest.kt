package com.nexus.agent.core.planner

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class PlannerTest {

    @Mock
    private lateinit var taskRegistry: TaskRegistry

    @Mock
    private lateinit var topologicalSorter: TopologicalSorter

    @Mock
    private lateinit var workflowEngine: WorkflowEngine

    @Mock
    private lateinit var parallelExecutor: ParallelExecutor

    private lateinit var taskPlanner: TaskPlanner

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        taskPlanner = TaskPlanner(taskRegistry, topologicalSorter, workflowEngine, parallelExecutor)
    }

    @Test
    fun `createPlan generates task graph from goal`() = runTest {
        val goal = "Build an APK"
        val tasks = listOf(
            TaskModel("1", "Setup", emptyList(), TaskStatus.PENDING),
            TaskModel("2", "Build", listOf("1"), TaskStatus.PENDING)
        )
        
        `when`(workflowEngine.decompose(goal)).thenReturn(tasks)
        `when`(topologicalSorter.sort(tasks)).thenReturn(tasks)
        
        val plan = taskPlanner.createPlan(goal)
        
        assertEquals(2, plan.tasks.size)
        assertEquals("Setup", plan.tasks[0].name)
    }

    @Test
    fun `createPlan detects circular dependencies`() = runTest {
        val goal = "Bad goal"
        val tasks = listOf(
            TaskModel("1", "A", listOf("2"), TaskStatus.PENDING),
            TaskModel("2", "B", listOf("1"), TaskStatus.PENDING)
        )
        
        `when`(workflowEngine.decompose(goal)).thenReturn(tasks)
        `when`(topologicalSorter.sort(tasks)).thenThrow(CircularDependencyException("Cycle detected"))
        
        try {
            taskPlanner.createPlan(goal)
            fail("Should throw exception")
        } catch (e: CircularDependencyException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `executePlan runs tasks in order`() = runTest {
        val plan = Plan(
            id = "plan-1",
            tasks = listOf(
                TaskModel("1", "Step 1", emptyList(), TaskStatus.PENDING),
                TaskModel("2", "Step 2", listOf("1"), TaskStatus.PENDING)
            )
        )
        
        `when`(topologicalSorter.sort(plan.tasks)).thenReturn(plan.tasks)
        
        taskPlanner.executePlan(plan)
        
        verify(workflowEngine).execute(plan.tasks[0])
        verify(workflowEngine).execute(plan.tasks[1])
    }

    @Test
    fun `executePlan runs independent tasks in parallel`() = runTest {
        val plan = Plan(
            id = "plan-1",
            tasks = listOf(
                TaskModel("1", "A", emptyList(), TaskStatus.PENDING),
                TaskModel("2", "B", emptyList(), TaskStatus.PENDING)
            )
        )
        
        `when`(topologicalSorter.sort(plan.tasks)).thenReturn(plan.tasks)
        `when`(topologicalSorter.getParallelGroups(plan.tasks)).thenReturn(
            listOf(listOf(plan.tasks[0], plan.tasks[1]))
        )
        
        taskPlanner.executePlan(plan)
        
        verify(parallelExecutor).execute(listOf(plan.tasks[0], plan.tasks[1]))
    }

    @Test
    fun `cancelPlan stops execution`() = runTest {
        val planId = "plan-1"
        
        taskPlanner.cancelPlan(planId)
        
        verify(workflowEngine).cancel(planId)
    }

    @Test
    fun `getPlanStatus returns current state`() = runTest {
        val planId = "plan-1"
        val expectedStatus = PlanStatus.RUNNING
        
        `when`(taskRegistry.getStatus(planId)).thenReturn(expectedStatus)
        
        assertEquals(expectedStatus, taskPlanner.getPlanStatus(planId))
    }

    @Test
    fun `retryFailedTasks re-executes failed ones`() = runTest {
        val planId = "plan-1"
        val failedTasks = listOf(
            TaskModel("2", "Failed", listOf("1"), TaskStatus.FAILED)
        )
        
        `when`(taskRegistry.getFailedTasks(planId)).thenReturn(failedTasks)
        
        taskPlanner.retryFailedTasks(planId)
        
        verify(workflowEngine).execute(failedTasks[0])
    }

    @Test
    fun `getProgress returns completion percentage`() = runTest {
        val planId = "plan-1"
        
        `when`(taskRegistry.getCompletedCount(planId)).thenReturn(3)
        `when`(taskRegistry.getTotalCount(planId)).thenReturn(10)
        
        val progress = taskPlanner.getProgress(planId)
        
        assertEquals(30, progress)
    }

    @Test
    fun `addTaskToPlan inserts new task with dependencies`() = runTest {
        val planId = "plan-1"
        val newTask = TaskModel("3", "New", listOf("1"), TaskStatus.PENDING)
        
        taskPlanner.addTaskToPlan(planId, newTask)
        
        verify(taskRegistry).addTask(planId, newTask)
    }

    @Test
    fun `getExecutionLog returns ordered events`() = runTest {
        val planId = "plan-1"
        val logs = listOf(
            ExecutionLog("Task started", System.currentTimeMillis()),
            ExecutionLog("Task completed", System.currentTimeMillis() + 1000)
        )
        
        `when`(taskRegistry.getLogs(planId)).thenReturn(logs)
        
        val result = taskPlanner.getExecutionLog(planId)
        
        assertEquals(2, result.size)
    }
}
