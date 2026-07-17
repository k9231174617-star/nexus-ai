package com.nexus.agent.core.trading

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TradingModule {

    @Provides @Singleton
    fun provideTradingDao(db: com.nexus.agent.data.local.AppDatabase): TradingDao =
        TradingDaoInstance.getInstance(db)

    @Provides @Singleton
    fun provideMarketDataProvider(): MarketDataProvider =
        MarketDataProviderImpl()

    @Provides @Singleton
    fun providePerformanceTracker(impl: PerformanceTrackerImpl): PerformanceTracker = impl

    @Provides @Singleton
    fun provideMemeCoinDetector(): MemeCoinDetector = MemeCoinDetector()
}

// Singleton holder for TradingDao (since it's not in AppDatabase yet)
object TradingDaoInstance {
    private var dao: TradingDao? = null

    fun getInstance(db: com.nexus.agent.data.local.AppDatabase): TradingDao {
        if (dao == null) {
            // In a full implementation, AppDatabase would expose tradingDao()
            dao = object : TradingDao {
                // Stub implementation - real implementation requires Room @Database update
                override fun getAllStrategies() = kotlinx.coroutines.flow.flowOf(emptyList())
                override fun getActiveStrategies() = kotlinx.coroutines.flow.flowOf(emptyList())
                override suspend fun getStrategyById(id: String) = null
                override suspend fun insertStrategy(strategy: StrategyEntity) {}
                override suspend fun setStrategyActive(id: String, active: Boolean) {}
                override suspend fun deleteStrategy(strategy: StrategyEntity) {}
                override fun getAllPositions() = kotlinx.coroutines.flow.flowOf(emptyList())
                override fun getActivePositions() = kotlinx.coroutines.flow.flowOf(emptyList())
                override suspend fun insertPosition(position: PositionEntity) {}
                override suspend fun closePosition(id: String, status: String, closePrice: Double, closedAt: Long) {}
                override fun getRecentSignals() = kotlinx.coroutines.flow.flowOf(emptyList())
                override suspend fun insertSignal(signal: TradeSignalEntity) {}
                override fun getRecentPerformance() = kotlinx.coroutines.flow.flowOf(emptyList())
                override suspend fun insertPerformance(perf: PerformanceEntity) {}
            }
        }
        return dao!!
    }
}
