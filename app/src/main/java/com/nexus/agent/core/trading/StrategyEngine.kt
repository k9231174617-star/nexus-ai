package com.nexus.agent.core.trading

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StrategyEngine @Inject constructor(
    private val marketDataProvider: MarketDataProvider,
) {
    private val _activeStrategies = MutableStateFlow<List<StrategyEntity>>(emptyList())
    val activeStrategies: StateFlow<List<StrategyEntity>> = _activeStrategies

    fun registerStrategy(strategy: StrategyEntity) {
        _activeStrategies.value = _activeStrategies.value + strategy.copy(isActive = true)
    }

    fun deactivateStrategy(id: String) {
        _activeStrategies.value = _activeStrategies.value.map {
            if (it.id == id) it.copy(isActive = false) else it
        }
    }

    suspend fun findSignals(): List<TradeSignalEntity> {
        val signals = mutableListOf<TradeSignalEntity>()
        for (strategy in _activeStrategies.value.filter { it.isActive }) {
            val assetSignals = evaluateStrategy(strategy)
            signals.addAll(assetSignals)
        }
        return signals
    }

    private suspend fun evaluateStrategy(strategy: StrategyEntity): List<TradeSignalEntity> {
        val signals = mutableListOf<TradeSignalEntity>()
        val assets = tryParseJsonArray(strategy.assets)
        for (asset in assets) {
            try {
                val price = marketDataProvider.getPrice(asset)
                val signal = generateSignal(strategy, asset, price)
                if (signal != null) signals.add(signal)
            } catch (_: Exception) {}
        }
        return signals
    }

    private fun generateSignal(strategy: StrategyEntity, asset: String, price: Double): TradeSignalEntity? {
        val confidence = (Math.random() * 30 + 40) // 40-70% симуляция
        if (confidence < 50) return null
        return TradeSignalEntity(
            asset = asset, type = "ENTRY", direction = "LONG",
            entryPrice = price,
            stopLoss = price * 0.98,
            takeProfit = price * 1.05,
            confidence = confidence,
            strategyId = strategy.id,
            reason = "${strategy.name}: сигнал на вход по $asset",
        )
    }

    fun activateStrategy(strategy: StrategyEntity) {
        _activeStrategies.value = _activeStrategies.value.map {
            if (it.id == strategy.id) strategy.copy(isActive = true) else it
        }
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
