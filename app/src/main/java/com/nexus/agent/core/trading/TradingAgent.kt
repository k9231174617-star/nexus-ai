package com.nexus.agent.core.trading

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Главный оркестратор торгового модуля Nexus AI.
 * Координирует стратегии, управление рисками, исполнение сделок.
 */
@Singleton
class TradingAgent @Inject constructor(
    private val strategyEngine: StrategyEngine,
    private val riskManager: RiskManager,
    private val liveTrader: LiveTrader,
    private val walletManager: WalletManager,
    private val performanceTracker: PerformanceTracker,
    private val marketDataProvider: MarketDataProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _status = MutableStateFlow(TradingStatus.IDLE)
    val status: StateFlow<TradingStatus> = _status

    private val _activePositions = MutableStateFlow<List<PositionEntity>>(emptyList())
    val activePositions: StateFlow<List<PositionEntity>> = _activePositions

    enum class TradingStatus { IDLE, ANALYZING, TRADING, PAUSED, ERROR }

    fun start() {
        if (_status.value == TradingStatus.TRADING) return
        _status.value = TradingStatus.ANALYZING
        scope.launch { runTradingLoop() }
    }

    fun stop() { _status.value = TradingStatus.IDLE }

    fun pause() { _status.value = TradingStatus.PAUSED }

    private suspend fun runTradingLoop() {
        while (_status.value == TradingStatus.ANALYZING || _status.value == TradingStatus.TRADING) {
            try {
                _status.value = TradingStatus.ANALYZING
                val signals = strategyEngine.findSignals()
                _status.value = TradingStatus.TRADING

                for (signal in signals) {
                    if (_status.value != TradingStatus.TRADING) break
                    if (!riskManager.canOpenPosition(signal.entryPrice ?: 0.0, signal.asset)) continue
                    liveTrader.executeSignal(signal)
                }

                if (_status.value == TradingStatus.TRADING) {
                    manageActivePositions()
                    delay(30_000L) // Check every 30s
                }
            } catch (e: Exception) {
                android.util.Log.e("TradingAgent", "Trading loop error", e)
                _status.value = TradingStatus.ERROR
                delay(60_000L)
                _status.value = TradingStatus.ANALYZING
            }
        }
    }

    private suspend fun manageActivePositions() {
        val positions = _activePositions.value.toMutableList()
        for (pos in positions) {
            val currentPrice = marketDataProvider.getPrice(pos.asset)
            val updated = pos.copy(currentPrice = currentPrice,
                pnlPercent = ((currentPrice - pos.entryPrice) / pos.entryPrice) * 100)
            // Check stop loss / take profit
            if (currentPrice <= pos.stopLoss || currentPrice >= pos.takeProfit) {
                closePosition(pos.id, currentPrice)
            }
        }
    }

    private suspend fun closePosition(positionId: String, price: Double) {
        _activePositions.value = _activePositions.value.map {
            if (it.id == positionId) it.copy(status = "CLOSED", closePrice = price, closedAt = System.currentTimeMillis())
            else it
        }
    }

    fun destroy() { scope.cancel() }
}
