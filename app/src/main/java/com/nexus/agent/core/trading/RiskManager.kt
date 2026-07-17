package com.nexus.agent.core.trading

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RiskManager @Inject constructor() {
    private val _config = MutableStateFlow(RiskConfig())
    val config: StateFlow<RiskConfig> = _config

    var lastRejectionReason: String = ""

    fun updateConfig(newConfig: RiskConfig) { _config.value = newConfig }

    fun canOpenPosition(price: Double, asset: String): Boolean {
        val cfg = _config.value
        if (cfg.allowedAssets.isNotEmpty() && asset !in cfg.allowedAssets) {
            lastRejectionReason = "$asset не в списке разрешённых"
            return false
        }
        return true
    }

    fun canOpenPosition(signal: TradeSignalEntity): Boolean {
        val cfg = _config.value
        val size = signal.entryPrice ?: return false
        if (size > cfg.maxPositionSizePercent) {
            lastRejectionReason = "Размер позиции превышает лимит"
            return false
        }
        return true
    }

    val minLiquidity: Double get() = _config.value.minLiquidityUsd
    val maxMemePositionPercent: Double get() = _config.value.maxMemePositionPercent

    fun getRiskSummary(): Map<String, Any> = mapOf(
        "maxPositionSize" to _config.value.maxPositionSizePercent,
        "maxLeverage" to _config.value.maxLeverage,
        "maxDailyLoss" to _config.value.maxDailyLossPercent,
        "minLiquidity" to _config.value.minLiquidityUsd,
    )
}
