package com.nexus.agent.ui.trading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.agent.core.trading.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TradingViewModel @Inject constructor(
    private val tradingAgent: TradingAgent,
    private val strategyEngine: StrategyEngine,
    private val riskManager: RiskManager,
    private val performanceTracker: PerformanceTrackerImpl,
    private val backtester: Backtester,
    private val memeCoinDetector: MemeCoinDetector,
    private val walletManager: WalletManager,
    private val selfLearningPipeline: SelfLearningPipeline,
) : ViewModel() {

    val agentStatus = tradingAgent.status
    val activeStrategies = strategyEngine.activeStrategies
    val performanceMetrics = performanceTracker.metrics
    val riskConfig = riskManager.config
    val walletConfigured = MutableStateFlow(walletManager.isConfigured())

    private val _memeCoins = MutableStateFlow<List<MemeCoinDetector.TokenScore>>(emptyList())
    val memeCoins: StateFlow<List<MemeCoinDetector.TokenScore>> = _memeCoins

    private val _backtestResult = MutableStateFlow<BacktestResult?>(null)
    val backtestResult: StateFlow<BacktestResult?> = _backtestResult

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    init {
        viewModelScope.launch {
            strategyEngine.registerStrategy(StrategyEntity(
                name = "Momentum Scalper", type = "SCALPING",
                assets = """["BTC","ETH","SOL"]""",
                description = "Быстрая торговля на импульсах",
            ))
            strategyEngine.registerStrategy(StrategyEntity(
                name = "Swing Trader Pro", type = "SWING_TRADING",
                assets = """["BTC","ETH"]""",
                description = "Среднесрочная торговля по тренду",
            ))
        }
    }

    fun startTrading() { viewModelScope.launch { tradingAgent.start() } }
    fun stopTrading() { tradingAgent.stop() }
    fun pauseTrading() { tradingAgent.pause() }

    fun scanMemeCoins() {
        viewModelScope.launch {
            _memeCoins.value = memeCoinDetector.scanNewTokens()
            addLog("🔍 Найдено ${_memeCoins.value.size} новых токенов")
        }
    }

    fun runBacktest(strategyId: String) {
        viewModelScope.launch {
            val strategy = StrategyEntity(id = strategyId, name = "Backtest", assets = """["BTC","ETH"]""")
            val result = backtester.run(strategy, days = 30)
            _backtestResult.value = result
            addLog("📊 Бэктест: Sharpe ${"%.2f".format(result.sharpeRatio)}, Return ${"%.1f".format(result.totalReturnPercent)}%")
        }
    }

    fun configureWallet(config: WalletConfig) {
        walletManager.configure(config)
        walletConfigured.value = true
        addLog("💼 Кошелёк настроен")
    }

    fun runOptimization() {
        viewModelScope.launch {
            addLog("🧬 Запуск ночной оптимизации...")
            selfLearningPipeline.runNightlyOptimization()
            addLog("✅ Оптимизация завершена")
        }
    }

    override fun onCleared() { tradingAgent.destroy(); super.onCleared() }

    private fun addLog(msg: String) {
        _log.value = _log.value + msg
        android.util.Log.i("TradingVM", msg)
    }
}
