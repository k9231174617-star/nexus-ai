package com.nexus.agent.core.trading

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TradingDao {
    // Strategies
    @Query("SELECT * FROM trading_strategies ORDER BY updatedAt DESC")
    fun getAllStrategies(): Flow<List<StrategyEntity>>

    @Query("SELECT * FROM trading_strategies WHERE isActive = 1")
    fun getActiveStrategies(): Flow<List<StrategyEntity>>

    @Query("SELECT * FROM trading_strategies WHERE id = :id")
    suspend fun getStrategyById(id: String): StrategyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrategy(strategy: StrategyEntity)

    @Query("UPDATE trading_strategies SET isActive = :active WHERE id = :id")
    suspend fun setStrategyActive(id: String, active: Boolean)

    @Delete
    suspend fun deleteStrategy(strategy: StrategyEntity)

    // Positions
    @Query("SELECT * FROM trading_positions ORDER BY openedAt DESC")
    fun getAllPositions(): Flow<List<PositionEntity>>

    @Query("SELECT * FROM trading_positions WHERE status = 'ACTIVE'")
    fun getActivePositions(): Flow<List<PositionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosition(position: PositionEntity)

    @Query("UPDATE trading_positions SET status = :status, closePrice = :closePrice, closedAt = :closedAt WHERE id = :id")
    suspend fun closePosition(id: String, status: String, closePrice: Double, closedAt: Long = System.currentTimeMillis())

    // Signals
    @Query("SELECT * FROM trading_signals ORDER BY createdAt DESC LIMIT 50")
    fun getRecentSignals(): Flow<List<TradeSignalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignal(signal: TradeSignalEntity)

    // Performance
    @Query("SELECT * FROM trading_performance ORDER BY date DESC LIMIT 30")
    fun getRecentPerformance(): Flow<List<PerformanceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerformance(perf: PerformanceEntity)
}
