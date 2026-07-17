package com.nexus.agent.core.apk

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * APK Decompiler — extracts and decompiles APK contents.
 * Uses built-in ZIP extraction for resources/classes.dex.
 * For full Java decompilation, jadx-core can be integrated.
 */
@Singleton
class Decompiler @Inject constructor(
    private val context: Context,
) {
    companion object {
        private const val TAG = "Decompiler"
    }

    data class DecompileResult(
        val success: Boolean,
        val outputDir: File? = null,
        val packages: List<String> = emptyList(),
        val totalFiles: Int = 0,
        val error: String? = null,
    )

    /** Decompile an APK to a temporary directory */
    fun decompile(apkPath: String): DecompileResult {
        return try {
            val apkFile = File(apkPath)
            if (!apkFile.exists()) return DecompileResult(false, error = "APK not found")

            val outputDir = File(context.cacheDir, "decompiled_${apkFile.nameWithoutExtension}")
            outputDir.mkdirs()

            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                val packages = mutableSetOf<String>()

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val target = File(outputDir, entry.name)

                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(target).use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Extract package names from DEX
                        if (entry.name.endsWith(".dex")) {
                            val pkg = extractPackageFromDex(target)
                            if (pkg != null) packages.add(pkg)
                        }
                    }
                }

                DecompileResult(
                    success = true,
                    outputDir = outputDir,
                    packages = packages.toList().sorted(),
                    totalFiles = outputDir.walkTopDown().count { it.isFile },
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Decompile failed", e)
            DecompileResult(false, error = e.message)
        }
    }

    /** Extract AndroidManifest.xml as text (simplified) */
    fun extractManifest(apkPath: String): String? {
        return try {
            ZipFile(apkPath).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml") ?: return@use null
                val binary = zip.getInputStream(entry).readBytes()
                // AXML parser would go here; for now return raw bytes as hex
                "AndroidManifest.xml (${binary.size} bytes, AXML binary format)"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract manifest", e)
            null
        }
    }

    /** List resources in APK */
    fun listResources(apkPath: String): List<String> {
        val resources = mutableListOf<String>()
        try {
            ZipFile(apkPath).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith("res/") && !entry.isDirectory) {
                        resources.add(entry.name)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list resources", e)
        }
        return resources.sorted()
    }

    /** Check if APK contains certain classes/packages */
    fun containsPackage(apkPath: String, packagePrefix: String): Boolean {
        return try {
            ZipFile(apkPath).use { zip ->
                val entries = zip.entries()
                var found = false
                while (entries.hasMoreElements() && !found) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith(".dex")) {
                        val tempFile = File(context.cacheDir, "tmp_${entry.name.replace('/', '_')}")
                        zip.getInputStream(entry).use { input ->
                            tempFile.outputStream().use { input.copyTo(it) }
                        }
                        val content = tempFile.readText(Charsets.ISO_8859_1)
                        if (content.contains(packagePrefix.replace('.', '/'))) found = true
                        tempFile.delete()
                    }
                }
                found
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            false
        }
    }

    private fun extractPackageFromDex(dexFile: File): String? {
        return try {
            val content = dexFile.readText(Charsets.ISO_8859_1)
            // Simplified heuristic: find first package-like string
            val regex = Regex("L([a-z][a-z0-9]*(?:/[a-z][a-z0-9]*)+);")
            val match = regex.find(content)
            match?.groupValues?.getOrNull(1)?.replace('/', '.')
        } catch (e: Exception) { null }
    }
}
