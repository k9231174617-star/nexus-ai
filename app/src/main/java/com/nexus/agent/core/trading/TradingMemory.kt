package com.nexus.agent.core.trading

import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TradingMemory @Inject constructor() {
    private val successfulTrades = ConcurrentLinkedQueue<String>()
    private val failedPatterns = ConcurrentLinkedQueue<String>()

    fun rememberSuccess(trade: ExecutedTrade) {
        successfulTrades.add("${trade.signal.asset}@${trade.entryPrice}:+${trade.pnlPercent}%")
        if (successfulTrades.size > 100) successfulTrades.poll()
    }

    fun rememberFailure(pattern: String) {
        failedPatterns.add(pattern)
        if (failedPatterns.size > 100) failedPatterns.poll()
    }

    fun getInsights(): Map<String, Any> = mapOf(
        "successfulTrades" to successfulTrades.size,
        "failedPatterns" to failedPatterns.size,
        "topAssets" to listOf("BTC", "ETH", "SOL"),
    )
}
