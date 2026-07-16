package com.nexus.agent.core.root

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * SuChecker — detects root availability and locates su binary.
 * Supports multiple root solutions: Magisk, SuperSU, KingRoot, etc.
 */
class SuChecker(private val context: Context) {
    companion object {
        private const val TAG = "SuChecker"
        private val KNOWN_SU_PATHS = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/magisk/.core/bin/su",
            "/system/sbin/su",
            "/vendor/bin/su",
            "/vendor/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/su/bin/su",
            "/su/xbin/su",
            "/system/sd/xbin/su",
            "/system/app/Superuser.apk",
            "/cache/xbin/su",
            "/data/adb/magisk/su",
            "/sbin/.magisk/img/magisk/su"
        )
        private val ROOT_INDICATORS = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/system/etc/init.d",
            "/su/bin",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sbin/su",
            "/vendor/bin/su",
            "/vendor/xbin/su",
            "/system/app/Kinguser.apk",
            "/data/adb/magisk",
            "/sbin/.magisk"
        )
    }

    private var cachedSuPath: String? = null
    private var cachedRootStatus: Boolean? = null

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        cachedRootStatus?.let { return@withContext it }

        val status = checkRootMethod1() || checkRootMethod2() || checkRootMethod3() || checkRootMethod4()
        cachedRootStatus = status
        status
    }

    suspend fun getSuPath(): String? = withContext(Dispatchers.IO) {
        cachedSuPath?.let { return@withContext it }

        for (path in KNOWN_SU_PATHS) {
            if (File(path).exists()) {
                cachedSuPath = path
                return@withContext path
            }
        }

        // Fallback: try which su
        val whichResult = executeSilent("which su")
        if (whichResult.isNotBlank()) {
            cachedSuPath = whichResult.trim()
            return@withContext cachedSuPath
        }

        null
    }

    suspend fun getRootManager(): RootManagerType = withContext(Dispatchers.IO) {
        when {
            File("/data/adb/magisk").exists() -> RootManagerType.MAGISK
            File("/su/bin").exists() -> RootManagerType.SUPERSU
            File("/system/app/Kinguser.apk").exists() -> RootManagerType.KINGROOT
            File("/system/bin/.ext").exists() -> RootManagerType.KINGROOT
            File("/system/xbin/ku.sud").exists() -> RootManagerType.KINGROOT
            File("/system/app/Superuser.apk").exists() -> RootManagerType.SUPERSU
            File("/data/local/tmp/.sysd").exists() -> RootManagerType.OTHER
            else -> RootManagerType.UNKNOWN
        }
    }

    suspend fun getMagiskVersion(): String? = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext null

        val result = executeWithSu("magisk -v 2>/dev/null || echo \"__NO_MAGISK__\"")
        if (result.contains("__NO_MAGISK__")) {
            // Try alternative path
            val altResult = executeWithSu("/data/adb/magisk/magisk -v 2>/dev/null || echo \"__NO_MAGISK__\"")
            if (altResult.contains("__NO_MAGISK__")) return@withContext null
            return@withContext altResult.trim()
        }
        result.trim()
    }

    suspend fun getMagiskVersionCode(): Int? = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext null

        val result = executeWithSu("magisk -V 2>/dev/null || echo \"0\"")
        result.trim().toIntOrNull()
    }

    suspend fun isMagiskHideEnabled(): Boolean = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext false

        val result = executeWithSu("magiskhide status 2>/dev/null || echo \"disabled\"")
        result.trim().lowercase().contains("enabled")
    }

    suspend fun listMagiskModules(): List<MagiskModule> = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext emptyList()

        val modulesDir = File("/data/adb/modules")
        if (!modulesDir.exists()) return@withContext emptyList()

        modulesDir.listFiles { file -> file.isDirectory }?.mapNotNull { moduleDir ->
            val propFile = File(moduleDir, "module.prop")
            if (!propFile.exists()) return@mapNotNull null

            val props = parsePropFile(propFile.readText())
            MagiskModule(
                id = props["id"] ?: moduleDir.name,
                name = props["name"] ?: moduleDir.name,
                version = props["version"] ?: "unknown",
                versionCode = props["versionCode"]?.toIntOrNull() ?: 0,
                author = props["author"] ?: "unknown",
                description = props["description"] ?: "",
                path = moduleDir.absolutePath,
                enabled = !File(moduleDir, "disable").exists()
            )
        } ?: emptyList()
    }

    suspend fun isBusyboxAvailable(): Boolean = withContext(Dispatchers.IO) {
        executeWithSu("busybox --help >/dev/null 2>&1 && echo \"yes\" || echo \"no\"").trim() == "yes"
    }

    suspend fun getBusyboxPath(): String? = withContext(Dispatchers.IO) {
        val paths = listOf(
            "/system/xbin/busybox",
            "/system/bin/busybox",
            "/sbin/busybox",
            "/data/local/bin/busybox",
            "/su/bin/busybox",
            "/magisk/.core/bin/busybox"
        )

        for (path in paths) {
            if (File(path).exists()) return@withContext path
        }

        val whichResult = executeWithSu("which busybox 2>/dev/null || echo \"\"").trim()
        if (whichResult.isNotBlank()) return@withContext whichResult

        null
    }

    suspend fun checkSelinuxStatus(): SelinuxStatus = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext SelinuxStatus.UNKNOWN

        val result = executeWithSu("getenforce 2>/dev/null || echo \"unknown\"").trim().lowercase()
        when {
            result.contains("enforcing") -> SelinuxStatus.ENFORCING
            result.contains("permissive") -> SelinuxStatus.PERMISSIVE
            result.contains("disabled") -> SelinuxStatus.DISABLED
            else -> SelinuxStatus.UNKNOWN
        }
    }

    suspend fun setSelinuxMode(mode: SelinuxStatus): Boolean = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext false

        val command = when (mode) {
            SelinuxStatus.ENFORCING -> "setenforce 1"
            SelinuxStatus.PERMISSIVE -> "setenforce 0"
            else -> return@withContext false
        }

        val result = executeWithSu("$command 2>/dev/null; echo $?").trim()
        result == "0"
    }

    fun invalidateCache() {
        cachedSuPath = null
        cachedRootStatus = null
    }

    private fun checkRootMethod1(): Boolean {
        val buildTags = android.os.Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    private fun checkRootMethod2(): Boolean {
        return ROOT_INDICATORS.any { File(it).exists() }
    }

    private fun checkRootMethod3(): Boolean {
        return try {
            Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun checkRootMethod4(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c id")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine() ?: ""
            reader.close()
            process.waitFor()
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    private fun executeSilent(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            reader.close()
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun executeWithSu(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec("su -c \"$command\"")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            reader.close()
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun parsePropFile(content: String): Map<String, String> {
        return content.lines()
            .filter { it.contains("=") }
            .associate { line ->
                val parts = line.split("=", limit = 2)
                parts[0].trim() to (parts.getOrNull(1)?.trim() ?: "")
            }
    }

    enum class RootManagerType {
        MAGISK, SUPERSU, KINGROOT, OTHER, UNKNOWN
    }

    enum class SelinuxStatus {
        ENFORCING, PERMISSIVE, DISABLED, UNKNOWN
    }

    data class MagiskModule(
        val id: String,
        val name: String,
        val version: String,
        val versionCode: Int,
        val author: String,
        val description: String,
        val path: String,
        val enabled: Boolean
    )
}
