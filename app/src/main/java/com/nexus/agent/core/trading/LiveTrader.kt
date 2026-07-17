package com.nexus.agent.core.trading

import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveTrader @Inject constructor(
    private val walletManager: WalletManager,
    private val riskManager: RiskManager,
    private val strategyEngine: StrategyEngine,
    private val marketDataProvider: MarketDataProvider,
) {
    suspend fun executeSignal(signal: TradeSignalEntity) {
        if (!riskManager.canOpenPosition(signal)) {
            android.util.Log.w("LiveTrader", "Rejected: ${riskManager.lastRejectionReason}")
            throw RiskRejectedException(riskManager.lastRejectionReason)
        }
        val liquidity = marketDataProvider.getLiquidity(signal.asset)
        if (liquidity < riskManager.minLiquidity) {
            throw InsufficientLiquidityException(signal.asset)
        }
        android.util.Log.i("LiveTrader", "Executing ${signal.direction} ${signal.asset} @ ${signal.entryPrice}")
    }
}
