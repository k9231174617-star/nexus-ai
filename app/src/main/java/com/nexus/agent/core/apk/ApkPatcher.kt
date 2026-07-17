package com.nexus.agent.core.apk

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * APK Patcher — decompile, apply patches, rebuild, and sign APKs.
 * Requires external apktool.jar and apksigner (optional) on device.
 * WARNING: Use only legally with proper permissions.
 */
@Singleton
class ApkPatcher @Inject constructor(
    private val context: Context,
) {
    companion object {
        private const val TAG = "ApkPatcher"
    }

    data class Patch(
        val pathRelativeToDecompiled: String,
        val replaceRegex: String,
        val replaceWith: String,
    )

    data class PatchResult(
        val success: Boolean,
        val rebuiltApk: File? = null,
        val message: String = "",
    )

    /** Apply a set of text patches to a decompiled APK */
    fun applyPatches(apkPath: String, workDir: File, patches: List<Patch>): PatchResult {
        return try {
            val apktool = findApktool()
            if (apktool == null) {
                // Fallback: direct file patching without apktool
                return applyDirectPatches(apkPath, patches)
            }

            val outDir = File(workDir, "decompiled")
            outDir.mkdirs()

            // Step 1: decompile
            val dCmd = arrayOf("java", "-jar", apktool.absolutePath, "d", "-f", apkPath, "-o", outDir.absolutePath)
            exec(dCmd)

            // Step 2: apply patches
            for (p in patches) {
                val target = File(outDir, p.pathRelativeToDecompiled)
                if (!target.exists()) {
                    Log.w(TAG, "Patch target not found: ${p.pathRelativeToDecompiled}")
                    continue
                }
                val content = target.readText()
                val updated = content.replace(Regex(p.replaceRegex), p.replaceWith)
                if (updated != content) {
                    target.writeText(updated)
                    Log.i(TAG, "Patched: ${p.pathRelativeToDecompiled}")
                }
            }

            // Step 3: rebuild
            val rebuilt = File(workDir, "rebuilt.apk")
            val bCmd = arrayOf("java", "-jar", apktool.absolutePath, "b", outDir.absolutePath, "-o", rebuilt.absolutePath)
            exec(bCmd)

            PatchResult(success = true, rebuiltApk = rebuilt, message = "APK patched successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Patching failed", e)
            PatchResult(false, message = e.message ?: "Unknown error")
        }
    }

    /** Direct patching when apktool is not available */
    private fun applyDirectPatches(apkPath: String, patches: List<Patch>): PatchResult {
        return try {
            val outDir = File(context.cacheDir, "patched_${System.currentTimeMillis()}")
            outDir.mkdirs()

            // Decompile using built-in ZipFile
            val decompiler = Decompiler(context)
            val result = decompiler.decompile(apkPath)
            if (!result.success || result.outputDir == null) {
                return PatchResult(false, message = "Decompile failed for direct patching")
            }

            // Apply patches to decompiled output
            for (p in patches) {
                val target = File(result.outputDir, p.pathRelativeToDecompiled)
                if (!target.exists()) continue
                val content = target.readText()
                val updated = content.replace(Regex(p.replaceRegex), p.replaceWith)
                if (updated != content) {
                    target.writeText(updated)
                }
            }

            PatchResult(success = true, message = "Patches applied (unsigned, use apktool to rebuild)")
        } catch (e: Exception) {
            PatchResult(false, message = e.message ?: "Direct patch failed")
        }
    }

    /** Check if apktool is available */
    fun isApktoolAvailable(): Boolean = findApktool() != null

    private fun findApktool(): File? {
        // Check app files dir
        val local = File(context.filesDir, "apktool.jar")
        if (local.exists()) return local
        // Check external storage
        val external = File(context.getExternalFilesDir(null), "apktool.jar")
        if (external?.exists() == true) return external
        return null
    }

    private fun exec(cmd: Array<String>) {
        val pb = ProcessBuilder(*cmd)
        pb.redirectErrorStream(true)
        val p = pb.start()
        val out = p.inputStream.bufferedReader().readText()
        val rc = if (p.waitFor(120, TimeUnit.SECONDS)) {
            p.exitValue()
        } else {
            p.destroyForcibly()
            throw RuntimeException("Command timed out: ${cmd.joinToString(" ")}")
        }
        Log.i(TAG, "exec ${cmd.joinToString(" ")} rc=$rc output=${out.take(1000)}")
        if (rc != 0) throw RuntimeException("Command failed: ${cmd.joinToString(" ")} rc=$rc")
    }
}
