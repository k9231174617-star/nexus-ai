package com.nexus.agent.core.context

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.nexus.agent.core.root.SuChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceEnvironment(
    val androidVersion: String,
    val sdkInt: Int,
    val deviceModel: String,
    val manufacturer: String,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val totalStorageMb: Long,
    val availableStorageMb: Long,
    val isRooted: Boolean,
    val cpuAbi: String,
    val isEmulator: Boolean,
)

@Singleton
class EnvironmentState @Inject constructor(
    private val context: Context,
    private val suChecker: SuChecker,
) {
    suspend fun capture(): DeviceEnvironment = withContext(Dispatchers.IO) {
        val actMgr = context.getSystemService(Context.ACTIVITY_SERVICE)
            as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actMgr.getMemoryInfo(memInfo)

        val stat = StatFs(Environment.getDataDirectory().path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availBlocks = stat.availableBlocksLong

        DeviceEnvironment(
            androidVersion   = Build.VERSION.RELEASE,
            sdkInt           = Build.VERSION.SDK_INT,
            deviceModel      = Build.MODEL,
            manufacturer     = Build.MANUFACTURER,
            totalRamMb       = memInfo.totalMem / 1024 / 1024,
            availableRamMb   = memInfo.availMem / 1024 / 1024,
            totalStorageMb   = (totalBlocks * blockSize) / 1024 / 1024,
            availableStorageMb = (availBlocks * blockSize) / 1024 / 1024,
            isRooted         = suChecker.isRootAvailable(),
            cpuAbi           = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            isEmulator       = isEmulator(),
        )
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.contains("emulator")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.BRAND.startsWith("generic")
    }
}