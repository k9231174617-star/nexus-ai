package com.nexus.agent.core.apk

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class APKInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val permissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val receivers: List<String>,
    val sizeBytes: Long,
    val isDebuggable: Boolean,
)

@Singleton
class APKTool @Inject constructor(
    private val context: Context,
    private val decompiler: Decompiler,
) {
    suspend fun analyzeAPK(apkPath: String): APKInfo = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packageInfo = pm.getPackageArchiveInfo(
            apkPath,
            PackageManager.GET_PERMISSIONS or
            PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS
        ) ?: throw IllegalArgumentException("Cannot parse APK: $apkPath")

        val appInfo = packageInfo.applicationInfo?.apply {
            sourceDir = apkPath
            publicSourceDir = apkPath
        }

        APKInfo(
            packageName  = packageInfo.packageName,
            versionName  = packageInfo.versionName ?: "unknown",
            versionCode  = packageInfo.longVersionCode,
            minSdk       = appInfo?.minSdkVersion ?: 0,
            targetSdk    = appInfo?.targetSdkVersion ?: 0,
            permissions  = packageInfo.requestedPermissions?.toList() ?: emptyList(),
            activities   = packageInfo.activities?.map { it.name } ?: emptyList(),
            services     = packageInfo.services?.map { it.name } ?: emptyList(),
            receivers    = packageInfo.receivers?.map { it.name } ?: emptyList(),
            sizeBytes    = File(apkPath).length(),
            isDebuggable = (appInfo?.flags?.and(ApplicationInfo.FLAG_DEBUGGABLE) ?: 0) != 0,
        )
    }

    suspend fun extractResources(apkPath: String, outputDir: String): List<String> =
        withContext(Dispatchers.IO) {
            val dir = File(outputDir).also { it.mkdirs() }
            val extracted = mutableListOf<String>()

            java.util.zip.ZipFile(apkPath).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (!entry.isDirectory) {
                        val outFile = File(dir, entry.name.replace("/", "_"))
                        zip.getInputStream(entry).use { input ->
                            outFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        extracted.add(outFile.absolutePath)
                    }
                }
            }
            extracted
        }

    suspend fun getInstalledApps(): List<APKInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).mapNotNull { pkg ->
            runCatching {
                APKInfo(
                    packageName  = pkg.packageName,
                    versionName  = pkg.versionName ?: "",
                    versionCode  = pkg.longVersionCode,
                    minSdk       = pkg.applicationInfo?.minSdkVersion ?: 0,
                    targetSdk    = pkg.applicationInfo?.targetSdkVersion ?: 0,
                    permissions  = pkg.requestedPermissions?.toList() ?: emptyList(),
                    activities   = emptyList(),
                    services     = emptyList(),
                    receivers    = emptyList(),
                    sizeBytes    = File(pkg.applicationInfo?.sourceDir ?: "").length(),
                    isDebuggable = (pkg.applicationInfo?.flags?.and(ApplicationInfo.FLAG_DEBUGGABLE) ?: 0) != 0,
                )
            }.getOrNull()
        }
    }
}