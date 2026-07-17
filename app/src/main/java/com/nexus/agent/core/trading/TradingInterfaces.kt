package com.nexus.agent.core.trading

import kotlinx.coroutines.flow.Flow

interface MarketDataProvider {
    suspend fun getPrice(asset: String): Double
    suspend fun getOHLCV(asset: String, timeframe: Timeframe, limit: Int = 100): List<OHLCV>
    suspend fun getLiquidity(asset: String): Double
    suspend fun getVolume24h(asset: String): Double
    suspend fun getOrderBook(asset: String, depth: Int = 10): OrderBook
    suspend fun getCurrentContext(): String
    fun getAvailableAssets(): List<String>
}

interface DexIntegration {
    suspend fun swap(asset: String, amount: Double, slippagePercent: Double): TransactionReceipt
    suspend fun getQuote(assetIn: String, assetOut: String, amount: Double): SwapQuote
    suspend fun getPoolLiquidity(pair: String): Double
    suspend fun getTokenInfo(address: String): TokenInfo?
}

interface LLMBridge {
    suspend fun suggestStrategyImprovements(
        strategy: StrategyEntity,
        mistakes: List<MistakePattern>,
        marketContext: String,
    ): List<StrategyEntity>
    suspend fun generateStrategy(goal: String, context: String): StrategyEntity?
    suspend fun analyzeSentiment(text: String): SentimentResult
}

interface PerformanceTracker {
    suspend fun getPerformance(days: Int): PerformanceMetrics
    suspend fun getLosingTrades(days: Int): List<ExecutedTrade>
    suspend fun recordTrade(trade: ExecutedTrade)
    suspend fun getDailyPnl(days: Int): List<Pair<String, Double>>
}

// Supporting data classes
data class OHLCV(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

data class OrderBook(
    val bids: List<Pair<Double, Double>>, // price, amount
    val asks: List<Pair<Double, Double>>,
    val timestamp: Long,
)

data class SwapQuote(
    val assetIn: String,
    val assetOut: String,
    val amountIn: Double,
    val amountOut: Double,
    val priceImpact: Double,
    val route: List<String>,
)

data class TokenInfo(
    val address: String,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val totalSupply: Double? = null,
    val liquidity: Double? = null,
)

data class SentimentResult(
    val score: Double, // -1.0 to 1.0
    val label: String, // bullish, bearish, neutral
    val keywords: List<String> = emptyList(),
    val confidence: Double = 0.0,
)
