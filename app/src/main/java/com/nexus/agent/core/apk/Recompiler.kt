package com.nexus.agent.core.apk

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class RecompileResult(
    val outputApkPath: String,
    val success: Boolean,
    val error: String? = null,
    val sizeBytes: Long = 0,
)

@Singleton
class Recompiler @Inject constructor(
    private val context: Context,
) {
    suspend fun recompile(decompileDir: String): RecompileResult = withContext(Dispatchers.IO) {
        val dir = File(decompileDir)
        if (!dir.exists()) return@withContext RecompileResult(
            outputApkPath = "", success = false, error = "Dir not found: $decompileDir"
        )

        val outApk = File(
            context.cacheDir,
            "nexus_recompiled_${System.currentTimeMillis()}.apk"
        )

        return@withContext try {
            ZipOutputStream(FileOutputStream(outApk)).use { zos ->
                dir.walkTopDown()
                    .filter { it.isFile }
                    .forEach { file ->
                        val entryName = file.relativeTo(dir).path
                        zos.putNextEntry(ZipEntry(entryName))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
            }
            RecompileResult(
                outputApkPath = outApk.absolutePath,
                success = true,
                sizeBytes = outApk.length(),
            )
        } catch (e: Exception) {
            RecompileResult(outputApkPath = "", success = false, error = e.message)
        }
    }

    suspend fun signAPK(apkPath: String): RecompileResult = withContext(Dispatchers.IO) {
        // In production: use apksig library or keystore
        // Here we simulate debug signing
        val signed = File(apkPath.replace(".apk", "_signed.apk"))
        File(apkPath).copyTo(signed, overwrite = true)
        RecompileResult(
            outputApkPath = signed.absolutePath,
            success = true,
            sizeBytes = signed.length(),
        )
    }
}