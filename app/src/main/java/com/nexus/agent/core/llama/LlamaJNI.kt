package com.nexus.agent.core.llama

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JNI bridge to llama.cpp for on-device LLM inference.
 * Loads a GGUF model and provides streaming token generation.
 */
@Singleton
class LlamaJNI @Inject constructor(
    private val context: Context,
) {
    companion object {
        private var nativeLoaded = false

        fun loadNative() {
            if (!nativeLoaded) {
                try {
                    System.loadLibrary("nexus_llama")
                    nativeLoaded = true
                } catch (e: UnsatisfiedLinkError) {
                    android.util.Log.w("LlamaJNI", "Native lib not available: ${e.message}")
                }
            }
        }
    }

    private var modelLoaded = false
    private var modelPath: String = ""

    /** Path where models are stored */
    private val modelsDir: File get() = File(context.filesDir, "models").also { it.mkdirs() }

    /** Find GGUF model files */
    fun findModels(): List<File> {
        return modelsDir.listFiles { f -> f.extension == "gguf" }
            ?.sortedByDescending { it.length() }
            ?: emptyList()
    }

    /** Load a GGUF model */
    suspend fun loadModel(path: String): Boolean = withContext(Dispatchers.IO) {
        if (!nativeLoaded) return@withContext false
        val file = File(path)
        if (!file.exists()) return@withContext false
        try {
            val result = nativeLoadModel(path)
            modelLoaded = result
            if (result) modelPath = path
            result
        } catch (e: Exception) {
            android.util.Log.e("LlamaJNI", "Failed to load model", e)
            false
        }
    }

    /** Generate text completion (streaming) */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
    ): Flow<String> = flow {
        if (!modelLoaded) {
            emit("[Model not loaded]")
            return@flow
        }
        try {
            nativeGenerate(prompt, maxTokens, temperature) { token ->
                if (token.isNotEmpty()) emit(token)
            }
        } catch (e: Exception) {
            emit("[Error: ${e.message}]")
        }
    }

    /** Get model info */
    fun getModelInfo(): Map<String, Any> {
        if (!modelLoaded) return mapOf("status" to "not loaded")
        return mapOf(
            "status" to "loaded",
            "path" to modelPath,
            "nativeLoaded" to nativeLoaded,
        )
    }

    /** Unload model and free memory */
    fun unloadModel() {
        if (modelLoaded) {
            try { nativeUnloadModel() } catch (_: Exception) {}
            modelLoaded = false
            modelPath = ""
        }
    }

    // ─── Native methods ────────────────────────────────────────
    private external fun nativeLoadModel(path: String): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float, callback: (String) -> Unit)
    private external fun nativeUnloadModel()
    private external fun nativeTokenize(text: String): IntArray
    private external fun nativeDetokenize(tokens: IntArray): String
}
