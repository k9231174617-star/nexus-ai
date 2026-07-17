package com.nexus.agent.core.trading

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

// ============================================================================
// ENUMS
// ============================================================================
enum class Timeframe { M1, M5, M15, M30, H1, H4, D1, W1 }
enum class Direction { LONG, SHORT }
enum class SignalType { ENTRY, EXIT, ADJUST_STOP, ADD_TO_POSITION, REDUCE_POSITION }
enum class PositionStatus { ACTIVE, CLOSED, STOPPED_OUT, LIQUIDATED }
enum class TradeType { BUY, SELL }

enum class StrategyType {
    SCALPING, DAY_TRADING, SWING_TRADING, ARBITRAGE, MOMENTUM,
    MEAN_REVERSION, BREAKOUT, GRID, DCA, CUSTOM
}

enum class StrategySource {
    AI_GENERATED, BACKTESTED, IMPORTED, EVOLVED
}

// ============================================================================
// DATA MODELS
// ============================================================================
data class Condition(
    val indicator: String,
    val operator: String,
    val value: Double,
    val timeframe: Timeframe
)

data class StrategyParameters(
    val maxPositionSizePercent: Double = 5.0,
    val stopLossPercent: Double = 2.0,
    val takeProfitPercent: Double = 5.0,
    val trailingStopPercent: Double = 1.5,
    val maxDrawdownPercent: Double = 15.0,
    val leverage: Double = 1.0,
    val maxConcurrentPositions: Int = 3,
    val cooldownPeriodMinutes: Int = 5,
    val extraParams: Map<String, Double> = emptyMap()
)

data class SignalMetadata(
    val indicators: Map<String, Double>,
    val volume24h: Double,
    val marketCap: Double,
    val volatilityPercent: Double,
    val sentimentScore: Double? = null,
    val onChainSignals: List<String> = emptyList()
)

data class TradingHours(
    val open: Int,   // Час открытия (0-23)
    val close: Int,  // Час закрытия
    val timezone: String = "UTC"
)

// ============================================================================
// ROOM ENTITIES
// ============================================================================
@Entity(tableName = "trading_strategies")
data class StrategyEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val type: String = "SCALPING",
    val parameters: String = "{}",
    val entryConditions: String = "[]",
    val exitConditions: String = "[]",
    val timeframes: String = "[]",
    val assets: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: String = "AI_GENERATED",
    val version: Int = 1,
    val currentSharpeRatio: Double = 0.0,
    val winRate: Double = 0.0,
    val totalTrades: Int = 0,
    val isActive: Boolean = false,
)

@Entity(tableName = "trading_positions")
data class PositionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val strategyId: String,
    val asset: String,
    val direction: String = "LONG",
    val entryPrice: Double,
    val currentPrice: Double,
    val size: Double,
    val leverage: Double = 1.0,
    val stopLoss: Double = 0.0,
    val takeProfit: Double = 0.0,
    val status: String = "ACTIVE",
    val pnlPercent: Double = 0.0,
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val closePrice: Double? = null,
)

@Entity(tableName = "trading_signals")
data class TradeSignalEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val asset: String,
    val type: String = "ENTRY",
    val direction: String = "LONG",
    val entryPrice: Double? = null,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val confidence: Double = 0.0,
    val strategyId: String? = null,
    val reason: String = "",
    val metadata: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val executedAt: Long? = null,
)

@Entity(tableName = "trading_performance")
data class PerformanceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String, // YYYY-MM-DD
    val totalPnlPercent: Double = 0.0,
    val totalTrades: Int = 0,
    val winningTrades: Int = 0,
    val losingTrades: Int = 0,
    val sharpeRatio: Double = 0.0,
    val maxDrawdown: Double = 0.0,
    val winRate: Double = 0.0,
    val balanceUsd: Double = 0.0,
)

data class ExecutedTrade(
    val signal: TradeSignalEntity,
    val entryTime: Long,
    val entryPrice: Double,
    val size: Double,
    val exitTime: Long? = null,
    val exitPrice: Double? = null,
    val pnlPercent: Double = 0.0,
    val fees: Double = 0.0,
)

data class PerformanceMetrics(
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRate: Double,
    val totalPnlPercent: Double,
    val sharpeRatio: Double,
    val maxDrawdown: Double,
    val averageWinPercent: Double,
    val averageLossPercent: Double,
    val profitFactor: Double,
    val bestTradePercent: Double,
    val worstTradePercent: Double,
    val lastUpdated: Long = System.currentTimeMillis(),
)

// ============================================================================
// WALLET
// ============================================================================
data class WalletConfig(
    val exchangeApiKey: String = "",
    val exchangeApiSecret: String = "",
    val privateKey: String = "",
    val rpcUrl: String = "",
    val preferredDex: String = "JUPITER",
)

// ============================================================================
// RISK
// ============================================================================
data class RiskConfig(
    val maxPositionSizePercent: Double = 5.0,
    val maxPortfolioRiskPercent: Double = 20.0,
    val maxDailyLossPercent: Double = 5.0,
    val maxLeverage: Double = 3.0,
    val minLiquidityUsd: Double = 50000.0,
    val maxMemePositionPercent: Double = 2.0,
    val minHoldTimeMinutes: Int = 5,
    val allowedAssets: List<String> = emptyList(),
)

// ============================================================================
// MEME COIN
// ============================================================================
data class MemeCoinSignal(
    val tokenAddress: String,
    val score: MemeCoinScore,
    val recommendedEntry: Double = 0.0,
)

data class MemeCoinScore(
    val momentumScore: Double = 0.0,
    val socialScore: Double = 0.0,
    val volumeScore: Double = 0.0,
    val liquidityScore: Double = 0.0,
    val combinedScore: Double = 0.0,
    val riskLevel: String = "HIGH",
    val signals: List<String> = emptyList(),
    val detectedAt: Long = System.currentTimeMillis(),
)

// ============================================================================
// BACKTEST
// ============================================================================
data class BacktestResult(
    val strategyId: String,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRate: Double,
    val totalReturnPercent: Double,
    val sharpeRatio: Double,
    val maxDrawdown: Double,
    val profitFactor: Double,
    val averageWinPercent: Double,
    val averageLossPercent: Double,
    val bestTradePercent: Double,
    val worstTradePercent: Double,
    val trades: List<ExecutedTrade> = emptyList(),
    val equityCurve: List<Double> = emptyList(),
    val durationMs: Long = 0,
)

// ============================================================================
// MISTAKE PATTERN
// ============================================================================
data class MistakePattern(
    val patternType: String,
    val frequency: Int,
    val avgLossPercent: Double,
    val description: String,
    val suggestion: String,
)

data class StrategyImprovement(
    val originalStrategyId: String,
    val modifiedParameters: StrategyParameters,
    val improvedSharpeRatio: Double,
    val changes: List<String>,
)

// ============================================================================
// TRANSACTION
// ============================================================================
data class TransactionReceipt(
    val txHash: String,
    val status: String, // confirmed, pending, failed
    val blockNumber: Long = 0,
    val gasUsed: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
)

// ============================================================================
// EXCEPTIONS
// ============================================================================
class RiskRejectedException(message: String) : Exception(message)
class InsufficientLiquidityException(val asset: String) : Exception("Insufficient liquidity for $asset")
class WalletNotFoundException(message: String = "Wallet not configured") : Exception(message)
class InsufficientBalanceException(message: String) : Exception(message)

// ============================================================================
// EXTENSIONS
// ============================================================================
fun String.isNativeToken(): Boolean =
    this.uppercase() in setOf("SOL", "ETH", "BNB", "MATIC", "AVAX", "ARB", "OP")
