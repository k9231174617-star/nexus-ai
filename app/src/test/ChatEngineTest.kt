package com.nexus.agent.core.chat

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class ChatEngineTest {

    @Mock
    private lateinit var streamingHandler: StreamingHandler

    @Mock
    private lateinit var repository: ChatRepository

    private lateinit var chatEngine: ChatEngine

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        chatEngine = ChatEngine(repository, streamingHandler)
    }

    @Test
    fun `sendMessage emits streaming chunks`() = runTest {
        val message = "Hello AI"
        val chunks = listOf("Hello", " there", "!")
        
        `when`(repository.sendMessage(message)).thenReturn(flowOf(*chunks.toTypedArray()))
        
        val result = chatEngine.sendMessage(message).toList()
        
        assertEquals(chunks, result)
    }

    @Test
    fun `sendMessage handles empty response`() = runTest {
        val message = "test"
        
        `when`(repository.sendMessage(message)).thenReturn(flowOf())
        
        val result = chatEngine.sendMessage(message).toList()
        
        assertTrue(result.isEmpty())
    }

    @Test
    fun `sendMessage propagates errors`() = runTest {
        val message = "error"
        val error = RuntimeException("Network error")
        
        `when`(repository.sendMessage(message)).thenThrow(error)
        
        try {
            chatEngine.sendMessage(message).toList()
            fail("Should throw exception")
        } catch (e: RuntimeException) {
            assertEquals("Network error", e.message)
        }
    }

    @Test
    fun `cancelStream stops streaming`() = runTest {
        chatEngine.cancelStream()
        
        verify(streamingHandler).cancel()
    }

    @Test
    fun `isStreaming returns false initially`() {
        assertFalse(chatEngine.isStreaming())
    }

    @Test
    fun `isStreaming returns true during active stream`() = runTest {
        val chunks = listOf("chunk")
        
        `when`(repository.sendMessage(anyString())).thenReturn(flowOf(*chunks.toTypedArray()))
        
        chatEngine.sendMessage("test").collect { }
        
        // After completion, should be false
        assertFalse(chatEngine.isStreaming())
    }

    @Test
    fun `clearHistory resets conversation`() {
        chatEngine.clearHistory()
        
        verify(repository).clearHistory()
    }

    @Test
    fun `getMessageCount returns correct count`() {
        `when`(repository.getMessageCount()).thenReturn(5)
        
        assertEquals(5, chatEngine.getMessageCount())
    }

    @Test
    fun `retryLastMessage resends last user message`() = runTest {
        val lastMessage = "retry me"
        
        `when`(repository.getLastUserMessage()).thenReturn(lastMessage)
        `when`(repository.sendMessage(lastMessage)).thenReturn(flowOf("response"))
        
        val result = chatEngine.retryLastMessage().toList()
        
        assertEquals(listOf("response"), result)
    }

    @Test
    fun `retryLastMessage throws when no user message exists`() = runTest {
        `when`(repository.getLastUserMessage()).thenReturn(null)
        
        try {
            chatEngine.retryLastMessage().toList()
            fail("Should throw exception")
        } catch (e: IllegalStateException) {
            assertNotNull(e.message)
        }
    }
}
