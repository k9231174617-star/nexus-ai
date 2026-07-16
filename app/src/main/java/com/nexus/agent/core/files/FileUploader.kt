package com.nexus.agent.core.files

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class FileUploader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    data class UploadResult(
        val success: Boolean,
        val url: String?,
        val error: String?
    )

    suspend fun uploadFile(
        file: File,
        uploadUrl: String,
        headers: Map<String, String> = emptyMap()
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val mediaType = getMediaType(file).toMediaType()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody(mediaType)
                )
                .build()

            val requestBuilder = Request.Builder()
                .url(uploadUrl)
                .post(requestBody)

            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    UploadResult(
                        success = true,
                        url = extractUrl(responseBody),
                        error = null
                    )
                } else {
                    UploadResult(
                        success = false,
                        url = null,
                        error = "HTTP ${response.code}: ${response.message}"
                    )
                }
            }
        } catch (e: Exception) {
            UploadResult(false, null, e.message)
        }
    }

    private fun getMediaType(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    private fun extractUrl(response: String?): String? {
        return response?.let {
            val regex = """"url"\s*:\s*"([^"]+)"""".toRegex()
            regex.find(it)?.groupValues?.get(1)
        }
    }
}
