package com.nexus.agent.core.trading

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketDataProviderImpl @Inject constructor() : MarketDataProvider {

    override suspend fun getPrice(asset: String): Double {
        // Симуляция — в реальности вызывает CoinGecko/Binance API
        return when (asset.uppercase()) {
            "BTC" -> 65000.0 + (Math.random() - 0.5) * 2000
            "ETH" -> 3500.0 + (Math.random() - 0.5) * 100
            "SOL" -> 140.0 + (Math.random() - 0.5) * 10
            "BNB" -> 580.0 + (Math.random() - 0.5) * 20
            else -> 1.0 + Math.random() * 100
        }
    }

    override suspend fun getOHLCV(asset: String, timeframe: Timeframe, limit: Int): List<OHLCV> {
        val base = getPrice(asset)
        return (1..limit).map { i ->
            val change = (Math.random() - 0.5) * base * 0.02
            OHLCV(
                timestamp = System.currentTimeMillis() - (i * timeframe.ordinal * 3600000L),
                open = base + change, high = base + change + Math.random() * 50,
                low = base + change - Math.random() * 50, close = base + change,
                volume = 1_000_000.0 + Math.random() * 10_000_000,
            )
        }
    }

    override suspend fun getLiquidity(asset: String): Double = 1_000_000.0 + Math.random() * 10_000_000
    override suspend fun getVolume24h(asset: String): Double = 100_000.0 + Math.random() * 1_000_000
    override suspend fun getOrderBook(asset: String, depth: Int): OrderBook = OrderBook(
        bids = (1..depth).map { getPrice(asset) * (1 - it * 0.001) to Math.random() * 10 },
        asks = (1..depth).map { getPrice(asset) * (1 + it * 0.001) to Math.random() * 10 },
        timestamp = System.currentTimeMillis(),
    )

    override suspend fun getCurrentContext(): String = "Market: Bullish. BTC dominance: 52%. Fear & Greed: 68."
    override fun getAvailableAssets(): List<String> = listOf("BTC", "ETH", "SOL", "BNB", "ARB", "OP", "MATIC")
}
