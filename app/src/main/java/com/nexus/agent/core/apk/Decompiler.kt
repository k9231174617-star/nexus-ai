package com.nexus.agent.core.apk

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DecompileResult(
    val outputDir: String,
    val smaliFiles: List<String>,
    val resourceFiles: List<String>,
    val manifestPath: String?,
    val success: Boolean,
    val error: String? = null,
)

@Singleton
class Decompiler @Inject constructor(
    private val context: Context,
) {
    private val workDir get() = File(context.cacheDir, "nexus_apktool").also { it.mkdirs() }

    suspend fun decompile(apkPath: String): DecompileResult = withContext(Dispatchers.IO) {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) return@withContext DecompileResult(
            outputDir = "", smaliFiles = emptyList(),
            resourceFiles = emptyList(), manifestPath = null,
            success = false, error = "APK not found: $apkPath"
        )

        val outDir = File(workDir, apkFile.nameWithoutExtension + "_${System.currentTimeMillis()}")
        outDir.mkdirs()

        return@withContext try {
            // Extract APK contents
            java.util.zip.ZipFile(apkPath).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (!entry.isDirectory) {
                        val outFile = File(outDir, entry.name)
                        outFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            outFile.outputStream().use { input.copyTo(it) }
                        }
                    }
                }
            }

            // Collect smali (in real impl, use smali library via JNI)
            val smaliFiles = outDir.walkTopDown()
                .filter { it.extension == "smali" }
                .map { it.absolutePath }
                .toList()

            val resourceFiles = outDir.walkTopDown()
                .filter { it.extension in listOf("xml", "png", "jpg", "webp") }
                .map { it.absolutePath }
                .toList()

            val manifest = File(outDir, "AndroidManifest.xml")
                .takeIf { it.exists() }?.absolutePath

            DecompileResult(
                outputDir = outDir.absolutePath,
                smaliFiles = smaliFiles,
                resourceFiles = resourceFiles,
                manifestPath = manifest,
                success = true,
            )
        } catch (e: Exception) {
            DecompileResult(
                outputDir = outDir.absolutePath,
                smaliFiles = emptyList(),
                resourceFiles = emptyList(),
                manifestPath = null,
                success = false,
                error = e.message,
            )
        }
    }

    suspend fun listClasses(apkPath: String): List<String> = withContext(Dispatchers.IO) {
        val classes = mutableListOf<String>()
        runCatching {
            java.util.zip.ZipFile(apkPath).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name == "classes.dex" || it.name.matches(Regex("classes\\d+\\.dex")) }
                    .forEach { _ -> classes.add("[DEX class — use smali for full listing]") }
            }
        }
        classes
    }
}