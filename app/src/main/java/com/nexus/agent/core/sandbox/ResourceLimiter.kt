package com.nexus.agent.core.sandbox

import android.app.ActivityManager
import android.content.Context
import java.util.concurrent.Semaphore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResourceLimiter @Inject constructor(
    private val context: Context,
) {
    private val semaphore = Semaphore(MAX_CONCURRENT)

    fun hasCapacity(): Boolean {
        if (!semaphore.tryAcquire()) return false
        val memInfo = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / 1024 / 1024
        if (availMb < MIN_FREE_RAM_MB) {
            semaphore.release()
            return false
        }
        return true
    }

    fun release() { semaphore.release() }

    fun getAvailableMemoryMb(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getMemoryInfo(memInfo)
        return memInfo.availMem / 1024 / 1024
    }

    companion object {
        const val MAX_CONCURRENT = 3
        const val MIN_FREE_RAM_MB = 100L
    }
}