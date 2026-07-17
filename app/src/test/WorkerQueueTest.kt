package com.nexus.agent.core.workers

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class WorkerQueueTest {

    private lateinit var workQueue: WorkQueue

    @Before
    fun setup() {
        workQueue = WorkQueue()
    }

    @Test
    fun `enqueue adds task to queue`() = runTest {
        workQueue.enqueue(Task(id = "1", type = "test", payload = "{}"))

        val size = workQueue.size()
        assertEquals(1, size)
    }

    @Test
    fun `dequeue removes and returns task`() = runTest {
        workQueue.enqueue(Task(id = "1", type = "test", payload = "{}"))

        val task = workQueue.dequeue()

        assertNotNull(task)
        assertEquals("1", task?.id)
    }

    @Test
    fun `dequeue returns null when empty`() = runTest {
        val task = workQueue.dequeue()
        assertNull(task)
    }
}
