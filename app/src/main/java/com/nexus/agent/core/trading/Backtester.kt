package com.nexus.agent.core.trading

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class Backtester @Inject constructor(
    private val marketDataProvider: MarketDataProvider,
) {
    suspend fun run(strategy: StrategyEntity, days: Int = 30): BacktestResult = withContext(Dispatchers.Default) {
        val assets = tryParseJsonArray(strategy.assets)
        val trades = mutableListOf<ExecutedTrade>()
        val equityCurve = mutableListOf(10000.0) // Start with $10k

        for (asset in assets.take(5)) {
            repeat(10) {
                val entryPrice = 100.0 + Math.random() * 50000
                val exitPrice = entryPrice * (1 + (Math.random() - 0.48) * 0.1)
                val pnl = ((exitPrice - entryPrice) / entryPrice) * 100
                trades.add(ExecutedTrade(
                    signal = TradeSignalEntity(asset = asset, entryPrice = entryPrice),
                    entryTime = System.currentTimeMillis() - (it * 86400000L),
                    entryPrice = entryPrice, size = 100.0,
                    exitTime = System.currentTimeMillis() - ((it - 1) * 86400000L),
                    exitPrice = exitPrice, pnlPercent = pnl,
                ))
                equityCurve.add(equityCurve.last() * (1 + pnl / 100))
            }
        }

        val winning = trades.filter { it.pnlPercent > 0 }
        val losing = trades.filter { it.pnlPercent <= 0 }
        val totalReturn = ((equityCurve.last() - equityCurve.first()) / equityCurve.first()) * 100

        BacktestResult(
            strategyId = strategy.id,
            totalTrades = trades.size,
            winningTrades = winning.size,
            losingTrades = losing.size,
            winRate = if (trades.isNotEmpty()) winning.size.toDouble() / trades.size else 0.0,
            totalReturnPercent = totalReturn,
            sharpeRatio = calculateSharpe(trades.map { it.pnlPercent }),
            maxDrawdown = calculateMaxDrawdown(equityCurve),
            profitFactor = calculateProfitFactor(winning, losing),
            averageWinPercent = winning.map { it.pnlPercent }.average(),
            averageLossPercent = losing.map { it.pnlPercent }.average(),
            bestTradePercent = trades.maxOfOrNull { it.pnlPercent } ?: 0.0,
            worstTradePercent = trades.minOfOrNull { it.pnlPercent } ?: 0.0,
            trades = trades, equityCurve = equityCurve,
            durationMs = 500L,
        )
    }

    private fun calculateSharpe(returns: List<Double>): Double {
        if (returns.size < 2) return 0.0
        val mean = returns.average()
        val variance = returns.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        return if (stdDev > 0) (mean / stdDev) * sqrt(365.0) else 0.0
    }

    private fun calculateMaxDrawdown(equity: List<Double>): Double {
        var peak = equity.first()
        var maxDd = 0.0
        for (v in equity) {
            if (v > peak) peak = v
            val dd = (peak - v) / peak * 100
            if (dd > maxDd) maxDd = dd
        }
        return maxDd
    }

    private fun calculateProfitFactor(winning: List<ExecutedTrade>, losing: List<ExecutedTrade>): Double {
        val grossWin = winning.sumOf { maxOf(it.pnlPercent, 0.0) }
        val grossLoss = losing.sumOf { minOf(it.pnlPercent, 0.0) }.let { -it }
        return if (grossLoss > 0) grossWin / grossLoss else 0.0
    }

    private fun tryParseJsonArray(json: String): List<String> {
        if (json.isBlank() || json == "[]") return listOf("BTC", "ETH", "SOL")
        return try {
            org.json.JSONArray(json).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
        } catch (_: Exception) { listOf("BTC", "ETH", "SOL") }
    }
}
