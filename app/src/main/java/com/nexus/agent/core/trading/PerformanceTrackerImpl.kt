package com.nexus.agent.core.trading

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerformanceTrackerImpl @Inject constructor() : PerformanceTracker {
    private val trades = mutableListOf<ExecutedTrade>()
    private val _metrics = MutableStateFlow(PerformanceMetrics(0,0,0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0))
    val metrics: StateFlow<PerformanceMetrics> = _metrics

    override suspend fun getPerformance(days: Int): PerformanceMetrics = _metrics.value
    override suspend fun getLosingTrades(days: Int): List<ExecutedTrade> = trades.filter { it.pnlPercent <= 0 }
    override suspend fun recordTrade(trade: ExecutedTrade) { trades.add(trade); recalculate() }
    override suspend fun getDailyPnl(days: Int): List<Pair<String, Double>> = listOf("Today" to trades.sumOf { it.pnlPercent })

    private fun recalculate() {
        val winning = trades.filter { it.pnlPercent > 0 }
        val losing = trades.filter { it.pnlPercent <= 0 }
        _metrics.value = PerformanceMetrics(
            totalTrades = trades.size,
            winningTrades = winning.size,
            losingTrades = losing.size,
            winRate = if (trades.isNotEmpty()) winning.size.toDouble() / trades.size else 0.0,
            totalPnlPercent = trades.sumOf { it.pnlPercent },
            sharpeRatio = calculateSharpe(trades.map { it.pnlPercent }),
            maxDrawdown = 0.0,
            averageWinPercent = winning.map { it.pnlPercent }.average(),
            averageLossPercent = losing.map { it.pnlPercent }.average(),
            profitFactor = calculateProfitFactor(winning, losing),
            bestTradePercent = trades.maxOfOrNull { it.pnlPercent } ?: 0.0,
            worstTradePercent = trades.minOfOrNull { it.pnlPercent } ?: 0.0,
        )
    }

    private fun calculateSharpe(returns: List<Double>): Double = if (returns.size < 2) 0.0 else {
        val mean = returns.average()
        val stdDev = kotlin.math.sqrt(returns.map { (it - mean) * (it - mean) }.average())
        if (stdDev > 0) (mean / stdDev) * kotlin.math.sqrt(365.0) else 0.0
    }

    private fun calculateProfitFactor(winning: List<ExecutedTrade>, losing: List<ExecutedTrade>): Double {
        val grossWin = winning.sumOf { maxOf(it.pnlPercent, 0.0) }
        val grossLoss = losing.sumOf { minOf(it.pnlPercent, 0.0) }.let { -it }
        return if (grossLoss > 0) grossWin / grossLoss else 0.0
    }
}
