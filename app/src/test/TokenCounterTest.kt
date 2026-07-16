package com.nexus.agent.core.llm

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TokenCounterTest {

    private lateinit var tokenCounter: TokenCounter

    @Before
    fun setup() {
        tokenCounter = TokenCounter()
    }

    @Test
    fun `count returns zero for empty string`() {
        assertEquals(0, tokenCounter.count(""))
    }

    @Test
    fun `count returns approximate value for English text`() {
        val text = "Hello world"
        // Approx 2-3 tokens for simple English
        val count = tokenCounter.count(text)
        assertTrue(count > 0)
        assertTrue(count <= 5)
    }

    @Test
    fun `count handles unicode characters`() {
        val text = "Привет мир 🌍"
        val count = tokenCounter.count(text)
        assertTrue(count >= 3)
    }

    @Test
    fun `count handles long text`() {
        val text = "word ".repeat(1000)
        val count = tokenCounter.count(text)
        assertTrue(count >= 1000)
    }

    @Test
    fun `countWithModel uses model-specific tokenizer`() {
        val text = "Hello"
        
        val gptCount = tokenCounter.countWithModel(text, "gpt-4")
        val claudeCount = tokenCounter.countWithModel(text, "claude-3")
        
        // Different models may tokenize differently
        assertTrue(gptCount >= 1)
        assertTrue(claudeCount >= 1)
    }

    @Test
    fun `countMessages sums tokens for message list`() {
        val messages = listOf(
            MessageModel(role = "user", content = "Hello"),
            MessageModel(role = "assistant", content = "Hi there!")
        )
        
        val count = tokenCounter.countMessages(messages)
        assertTrue(count > 2)
    }

    @Test
    fun `estimateCost calculates price for given tokens`() {
        val tokens = 1000
        val model = "gpt-4"
        
        val cost = tokenCounter.estimateCost(tokens, model)
        
        assertTrue(cost >= 0.0)
    }

    @Test
    fun `truncateToLimit cuts text to token budget`() {
        val text = "This is a very long text that needs truncation"
        val limit = 5
        
        val truncated = tokenCounter.truncateToLimit(text, limit)
        val truncatedTokens = tokenCounter.count(truncated)
        
        assertTrue(truncatedTokens <= limit)
    }

    @Test
    fun `truncateToLimit returns full text if under limit`() {
        val text = "Hi"
        val limit = 100
        
        assertEquals(text, tokenCounter.truncateToLimit(text, limit))
    }

    @Test
    fun `countCodeTokens handles code with special chars`() {
        val code = "fun main() { println(\"Hello\") }"
        val count = tokenCounter.countCodeTokens(code)
        
        assertTrue(count > 0)
    }

    @Test
    fun `getTokenizerInfo returns model metadata`() {
        val info = tokenCounter.getTokenizerInfo("gpt-4")
        
        assertNotNull(info)
        assertTrue(info.containsKey("vocab_size") || info.containsKey("name"))
    }
}
