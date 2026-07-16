package com.nexus.agent.core.workers

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class WorkerQueueTest {

    @Mock
    private lateinit var workerRegistry: WorkerRegistry

    @Mock
    private lateinit var taskHandler: TaskHandler

    private lateinit var workQueue: WorkQueue

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        workQueue = WorkQueue(workerRegistry, taskHandler)
    }

    @Test
    fun `enqueue adds task to queue`() = runTest {
        val task = Task(id = "task-1", type = "compute", payload = "data", priority = 1)
        
        workQueue.enqueue(task)
        
        verify(taskHandler).onTaskQueued(task)
    }

    @Test
    fun `enqueue with high priority inserts at front`() = runTest {
        val lowPriority = Task(id = "task-1", type = "batch", payload = "data", priority = 5)
        val highPriority = Task(id = "task-2", type = "urgent", payload = "data", priority = 1)
        
        workQueue.enqueue(lowPriority)
        workQueue.enqueue(highPriority)
        
        val next = workQueue.peek()
        assertEquals("task-2", next?.id)
    }

    @Test
    fun `dequeue returns and removes next task`() = runTest {
        val task = Task(id = "task-1", type = "compute", payload = "data", priority = 1)
        
        workQueue.enqueue(task)
        
        val result = workQueue.dequeue()
        
        assertEquals(task, result)
        assertNull(workQueue.peek())
    }

    @Test
    fun `dequeue returns null when empty`() = runTest {
        val result = workQueue.dequeue()
        
        assertNull(result)
    }

    @Test
    fun `assignTask delegates to available worker`() = runTest {
        val task = Task(id = "task-1", type = "compute", payload = "data", priority = 1)
        val worker = Worker(id = "worker-1", status = WorkerStatus.IDLE, capabilities = listOf("compute"))
        
        `when`(workerRegistry.findAvailableWorker("compute")).thenReturn(worker)
        
        workQueue.assignTask(task)
        
        verify(workerRegistry).findAvailableWorker("compute")
        verify(taskHandler).onTaskAssigned(task, worker)
    }

    @Test
    fun `assignTask requeues when no worker available`() = runTest {
        val task = Task(id = "task-1", type = "gpu", payload = "data", priority = 1)
        
        `when`(workerRegistry.findAvailableWorker("gpu")).thenReturn(null)
        
        workQueue.assignTask(task)
        
        verify(taskHandler).onTaskRequeued(task)
        assertEquals(task, workQueue.peek())
    }

    @Test
    fun `cancelTask removes from queue`() = runTest {
        val taskId = "task-1"
        val task = Task(id = taskId, type = "compute", payload = "data", priority = 1)
        
        workQueue.enqueue(task)
        workQueue.cancelTask(taskId)
        
        assertNull(workQueue.peek())
        verify(taskHandler).onTaskCancelled(task)
    }

    @Test
    fun `getQueueSize returns correct count`() = runTest {
        workQueue.enqueue(Task("1", "type", "data", 1))
        workQueue.enqueue(Task("2", "type", "data", 2))
        
        assertEquals(2, workQueue.getQueueSize())
    }

    @Test
    fun `getTasksByType filters correctly`() = runTest {
        val computeTask = Task("1", "compute", "data", 1)
        val ioTask = Task("2", "io", "data", 1)
        
        workQueue.enqueue(computeTask)
        workQueue.enqueue(ioTask)
        
        val computeTasks = workQueue.getTasksByType("compute")
        
        assertEquals(1, computeTasks.size)
        assertEquals("compute", computeTasks[0].type)
    }

    @Test
    fun `processQueue assigns all possible tasks`() = runTest {
        val task1 = Task("1", "compute", "data", 1)
        val task2 = Task("2", "compute", "data", 2)
        val worker = Worker("w1", WorkerStatus.IDLE, listOf("compute"))
        
        workQueue.enqueue(task1)
        workQueue.enqueue(task2)
        
        `when`(workerRegistry.findAvailableWorker("compute"))
            .thenReturn(worker)
            .thenReturn(null)
        
        workQueue.processQueue()
        
        verify(taskHandler).onTaskAssigned(task1, worker)
        assertEquals(1, workQueue.getQueueSize()) // task2 remains
    }

    @Test
    fun `clear removes all tasks`() = runTest {
        workQueue.enqueue(Task("1", "type", "data", 1))
        workQueue.enqueue(Task("2", "type", "data", 2))
        
        workQueue.clear()
        
        assertEquals(0, workQueue.getQueueSize())
    }
}
