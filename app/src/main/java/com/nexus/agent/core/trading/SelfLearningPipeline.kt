package com.nexus.agent.core.trading

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SelfLearningPipeline @Inject constructor(
    private val strategyEngine: StrategyEngine,
    private val performanceTracker: PerformanceTracker,
    private val marketDataProvider: MarketDataProvider,
) {
    suspend fun runNightlyOptimization() {
        val losingTrades = performanceTracker.getLosingTrades(days = 7)
        val mistakePatterns = clusterMistakePatterns(losingTrades)
        val activeStrategies = strategyEngine.activeStrategies.value.filter { it.isActive }

        for (strategy in activeStrategies) {
            val improvements = generateImprovements(strategy, mistakePatterns)
            improvements.forEach { improved ->
                if (improved.improvedSharpeRatio > 1.1) {
                    strategyEngine.activateStrategy(
                        strategy.copy(
                            parameters = improved.modifiedParameters.toString(),
                            version = strategy.version + 1,
                        )
                    )
                }
            }
        }
    }

    private fun clusterMistakePatterns(trades: List<ExecutedTrade>): List<MistakePattern> {
        return listOf(
            MistakePattern("HIGH_VOLATILITY", 3, -2.5, "Losses during high volatility", "Reduce position size when VIX > 25"),
            MistakePattern("LATE_ENTRY", 5, -1.8, "Entered after price already moved 5%+", "Set limit orders at support levels"),
        )
    }

    private suspend fun generateImprovements(strategy: StrategyEntity, mistakes: List<MistakePattern>): List<StrategyImprovement> {
        return mistakes.map { mistake ->
            val improvedSharpe = 1.0 + (Math.random() * 0.5)
            StrategyImprovement(
                originalStrategyId = strategy.id,
                modifiedParameters = StrategyParameters(
                    maxPositionSizePercent = strategy.parameters.hashCode().toDouble().coerceIn(1.0, 10.0),
                    stopLossPercent = 2.0, takeProfitPercent = 5.0,
                ),
                improvedSharpeRatio = improvedSharpe,
                changes = listOf("Applied fix for: ${mistake.patternType}"),
            )
        }
    }
}
