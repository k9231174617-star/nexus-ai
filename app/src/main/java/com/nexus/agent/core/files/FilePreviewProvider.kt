package com.nexus.agent.core.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import com.nexus.agent.core.media.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Locale

/**
 * Unified file preview provider supporting images, videos, PDFs, text, code,
 * archives, APKs, and generic binary files.
 */
class FilePreviewProvider(
    private val context: Context,
    private val imageProcessor: ImageProcessor
) {
    companion object {
        private const val MAX_TEXT_PREVIEW_BYTES = 128 * 1024
        private const val MAX_IMAGE_PREVIEW_DIM = 2048
        private const val VIDEO_THUMB_TIME_US = 1_000_000L
        private const val PDF_PAGE_INDEX = 0
    }

    sealed class PreviewResult {
        data class Image(val bitmap: Bitmap, val mimeType: String) : PreviewResult()
        data class Text(val content: String, val mimeType: String, val lineCount: Int) : PreviewResult()
        data class Video(val thumbnail: Bitmap, val durationMs: Long, val mimeType: String) : PreviewResult()
        data class Pdf(val thumbnail: Bitmap, val pageCount: Int, val mimeType: String) : PreviewResult()
        data class Audio(val waveform: Bitmap?, val durationMs: Long, val mimeType: String) : PreviewResult()
        data class Archive(val entries: List<String>, val entryCount: Int, val mimeType: String) : PreviewResult()
        data class Apk(val icon: Bitmap?, val appName: String, val packageName: String, val version: String) : PreviewResult()
        data class Generic(val iconType: GenericIcon, val mimeType: String, val size: Long) : PreviewResult()
        data class Error(val message: String) : PreviewResult()
    }

    enum class GenericIcon {
        BINARY, DOCUMENT, SPREADSHEET, PRESENTATION, DATABASE, SCRIPT, UNKNOWN
    }

    private val mimeTypeMap = MimeTypeMap.getSingleton()

    suspend fun generatePreview(uri: Uri): PreviewResult = withContext(Dispatchers.IO) {
        try {
            val file = File(uri.path ?: return@withContext PreviewResult.Error("Invalid path"))
            if (!file.exists()) {
                return@withContext PreviewResult.Error("File not found")
            }

            val mimeType = resolveMimeType(file)
            val extension = file.extension.lowercase(Locale.ROOT)

            when {
                isImage(mimeType) -> generateImagePreview(file, mimeType)
                isVideo(mimeType) -> generateVideoPreview(file, mimeType)
                isAudio(mimeType) -> generateAudioPreview(file, mimeType)
                isPdf(mimeType) || extension == "pdf" -> generatePdfPreview(file, mimeType)
                isText(mimeType) || isCodeFile(extension) -> generateTextPreview(file, mimeType)
                isArchive(mimeType) || isArchiveExtension(extension) -> generateArchivePreview(file, mimeType)
                isApk(extension) -> generateApkPreview(file)
                else -> generateGenericPreview(file, mimeType)
            }
        } catch (e: Exception) {
            PreviewResult.Error("Preview failed: ${e.message}")
        }
    }

    suspend fun generatePreview(file: File): PreviewResult = generatePreview(Uri.fromFile(file))

    private fun resolveMimeType(file: File): String {
        val ext = file.extension
        return mimeTypeMap.getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun isImage(mimeType: String): Boolean = mimeType.startsWith("image/")
    private fun isVideo(mimeType: String): Boolean = mimeType.startsWith("video/")
    private fun isAudio(mimeType: String): Boolean = mimeType.startsWith("audio/")
    private fun isPdf(mimeType: String): Boolean = mimeType == "application/pdf"
    private fun isText(mimeType: String): Boolean = mimeType.startsWith("text/")
    private fun isApk(ext: String): Boolean = ext == "apk"

    private fun isCodeFile(ext: String): Boolean = ext in setOf(
        "kt", "java", "c", "cpp", "h", "hpp", "rs", "go", "py", "js", "ts",
        "jsx", "tsx", "swift", "m", "mm", "rb", "php", "cs", "scala", "clj",
        "sh", "bash", "zsh", "fish", "ps1", "bat", "cmd", "sql", "json",
        "xml", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties",
        "gradle", "pro", "md", "markdown", "html", "htm", "css", "scss",
        "sass", "less", "dockerfile", "makefile", "cmake", "lua", "r",
        "pl", "pm", "t", "dart", "groovy", "kts", "vb", "fs", "fsx",
        "ml", "mli", "erl", "hrl", "ex", "exs", "elm", "hs", "lhs",
        "nim", "cr", "pas", "pp", "dpr", "lpr", "asm", "s", "nasm",
        "f", "f90", "f95", "for", "cob", "cbl", "ada", "adb", "ads"
    )

    private fun isArchive(mimeType: String): Boolean = mimeType in setOf(
        "application/zip", "application/x-zip-compressed",
        "application/gzip", "application/x-gzip",
        "application/x-tar", "application/x-bzip2",
        "application/x-7z-compressed", "application/x-rar-compressed",
        "application/x-rar", "application/java-archive"
    )

    private fun isArchiveExtension(ext: String): Boolean = ext in setOf(
        "zip", "tar", "gz", "tgz", "bz2", "tbz2", "7z", "rar", "jar",
        "war", "ear", "apk", "xapk", "apks", "aab", "deb", "rpm",
        "iso", "dmg", "cab", "lz", "lzma", "xz", "txz", "zst"
    )

    private fun generateImagePreview(file: File, mimeType: String): PreviewResult.Image {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)

        val scale = calculateInSampleSize(options, MAX_IMAGE_PREVIEW_DIM, MAX_IMAGE_PREVIEW_DIM)

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = scale
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            ?: return PreviewResult.Error("Failed to decode image")

        val processed = imageProcessor.normalizeForPreview(bitmap, MAX_IMAGE_PREVIEW_DIM)
        bitmap.recycle()

        return PreviewResult.Image(processed, mimeType)
    }

    private fun generateVideoPreview(file: File, mimeType: String): PreviewResult {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            val thumb = retriever.getFrameAtTime(VIDEO_THUMB_TIME_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime

            if (thumb != null) {
                val scaled = imageProcessor.normalizeForPreview(thumb, MAX_IMAGE_PREVIEW_DIM)
                thumb.recycle()
                PreviewResult.Video(scaled, durationMs, mimeType)
            } else {
                PreviewResult.Error("Failed to extract video frame")
            }
        } catch (e: Exception) {
            PreviewResult.Error("Video preview failed: ${e.message}")
        } finally {
            retriever.release()
        }
    }

    private fun generateAudioPreview(file: File, mimeType: String): PreviewResult {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            val waveform = generateWaveformPlaceholder(file, durationMs)
            PreviewResult.Audio(waveform, durationMs, mimeType)
        } catch (e: Exception) {
            PreviewResult.Audio(null, 0L, mimeType)
        } finally {
            retriever.release()
        }
    }

    private fun generateWaveformPlaceholder(file: File, durationMs: Long): Bitmap? {
        return try {
            val width = 512
            val height = 128
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint().apply {
                color = Color.parseColor("#00E5FF")
                strokeWidth = 2f
                isAntiAlias = true
            }

            canvas.drawColor(Color.parseColor("#121212"))

            val random = java.util.Random(file.absolutePath.hashCode().toLong())
            val bars = 64
            val barWidth = width / bars.toFloat()

            for (i in 0 until bars) {
                val amplitude = random.nextFloat()
                val barHeight = amplitude * height * 0.8f
                val left = i * barWidth
                val top = (height - barHeight) / 2
                canvas.drawRect(left, top, left + barWidth - 2, top + barHeight, paint)
            }

            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun generatePdfPreview(file: File, mimeType: String): PreviewResult {
        var pdfRenderer: PdfRenderer? = null
        var fileDescriptor: ParcelFileDescriptor? = null

        return try {
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor)
            val pageCount = pdfRenderer.pageCount

            if (pageCount == 0) {
                return PreviewResult.Error("PDF has no pages")
            }

            val page = pdfRenderer.openPage(PDF_PAGE_INDEX)
            val width = page.width
            val height = page.height

            val scale = MAX_IMAGE_PREVIEW_DIM.toFloat() / maxOf(width, height)
            val bitmapWidth = (width * scale).toInt()
            val bitmapHeight = (height * scale).toInt()

            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            val renderWidth = (width * scale).toInt()
            val renderHeight = (height * scale).toInt()
            val renderRect = Rect(0, 0, renderWidth, renderHeight)

            page.render(bitmap, renderRect, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            PreviewResult.Pdf(bitmap, pageCount, mimeType)
        } catch (e: Exception) {
            PreviewResult.Error("PDF preview failed: ${e.message}")
        } finally {
            pdfRenderer?.close()
            fileDescriptor?.close()
        }
    }

    private fun generateTextPreview(file: File, mimeType: String): PreviewResult {
        return try {
            val bytesToRead = minOf(file.length(), MAX_TEXT_PREVIEW_BYTES.toLong()).toInt()
            val buffer = ByteArray(bytesToRead)

            FileInputStream(file).use { fis ->
                fis.read(buffer)
            }

            val charset = detectCharset(buffer)
            val content = String(buffer, charset)

            val lines = content.lines()
            val lineCount = lines.size

            PreviewResult.Text(content, mimeType, lineCount)
        } catch (e: Exception) {
            PreviewResult.Error("Text preview failed: ${e.message}")
        }
    }

    private fun detectCharset(buffer: ByteArray): java.nio.charset.Charset {
        return try {
            if (buffer.size >= 3 && buffer[0] == 0xEF.toByte() && buffer[1] == 0xBB.toByte() && buffer[2] == 0xBF.toByte()) {
                java.nio.charset.Charset.forName("UTF-8")
            } else if (buffer.size >= 2 && buffer[0] == 0xFF.toByte() && buffer[1] == 0xFE.toByte()) {
                java.nio.charset.Charset.forName("UTF-16LE")
            } else if (buffer.size >= 2 && buffer[0] == 0xFE.toByte() && buffer[1] == 0xFF.toByte()) {
                java.nio.charset.Charset.forName("UTF-16BE")
            } else {
                java.nio.charset.Charset.forName("UTF-8")
            }
        } catch (e: Exception) {
            java.nio.charset.Charset.forName("UTF-8")
        }
    }

    private fun generateArchivePreview(file: File, mimeType: String): PreviewResult {
        return try {
            val entries = mutableListOf<String>()
            val process = Runtime.getRuntime().exec(arrayOf("unzip", "-l", file.absolutePath))
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            reader.useLines { lines ->
                lines.drop(3).forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("--------") && !trimmed.contains("Archive:")) {
                        val parts = trimmed.split(Regex("\\s+"), limit = 4)
                        if (parts.size >= 4) {
                            entries.add(parts[3])
                        }
                    }
                }
            }

            process.waitFor()
            PreviewResult.Archive(entries.take(50), entries.size, mimeType)
        } catch (e: Exception) {
            PreviewResult.Archive(emptyList(), 0, mimeType)
        }
    }

    private fun generateApkPreview(file: File): PreviewResult {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0)

            if (info != null) {
                info.applicationInfo?.sourceDir = file.absolutePath
                info.applicationInfo?.publicSourceDir = file.absolutePath

                val appName = info.applicationInfo?.loadLabel(pm)?.toString() ?: "Unknown"
                val packageName = info.packageName ?: "unknown"
                val version = info.versionName ?: "unknown"

                val icon = info.applicationInfo?.loadIcon(pm)
                val iconBitmap = if (icon != null) {
                    val width = icon.intrinsicWidth.coerceAtLeast(1)
                    val height = icon.intrinsicHeight.coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    icon.setBounds(0, 0, width, height)
                    icon.draw(canvas)
                    bitmap
                } else null

                PreviewResult.Apk(iconBitmap, appName, packageName, version)
            } else {
                PreviewResult.Apk(null, "Unknown", "unknown", "unknown")
            }
        } catch (e: Exception) {
            PreviewResult.Error("APK preview failed: ${e.message}")
        }
    }

    private fun generateGenericPreview(file: File, mimeType: String): PreviewResult {
        val iconType = when {
            mimeType.contains("spreadsheet") || file.extension in setOf("xls", "xlsx", "csv", "ods") -> GenericIcon.SPREADSHEET
            mimeType.contains("presentation") || file.extension in setOf("ppt", "pptx", "odp") -> GenericIcon.PRESENTATION
            mimeType.contains("document") || file.extension in setOf("doc", "docx", "odt", "rtf") -> GenericIcon.DOCUMENT
            mimeType.contains("database") || file.extension in setOf("db", "sqlite", "sql", "mdb") -> GenericIcon.DATABASE
            isCodeFile(file.extension) -> GenericIcon.SCRIPT
            mimeType == "application/octet-stream" -> GenericIcon.BINARY
            else -> GenericIcon.UNKNOWN
        }

        return PreviewResult.Generic(iconType, mimeType, file.length())
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    fun getPreviewIconType(result: PreviewResult): GenericIcon {
        return when (result) {
            is PreviewResult.Image -> GenericIcon.DOCUMENT
            is PreviewResult.Text -> GenericIcon.SCRIPT
            is PreviewResult.Video -> GenericIcon.DOCUMENT
            is PreviewResult.Pdf -> GenericIcon.DOCUMENT
            is PreviewResult.Audio -> GenericIcon.DOCUMENT
            is PreviewResult.Archive -> GenericIcon.BINARY
            is PreviewResult.Apk -> GenericIcon.BINARY
            is PreviewResult.Generic -> result.iconType
            is PreviewResult.Error -> GenericIcon.UNKNOWN
        }
    }

    fun getFileCategory(file: File): String {
        val mimeType = resolveMimeType(file)
        val ext = file.extension.lowercase(Locale.ROOT)

        return when {
            isImage(mimeType) -> "image"
            isVideo(mimeType) -> "video"
            isAudio(mimeType) -> "audio"
            isPdf(mimeType) || ext == "pdf" -> "pdf"
            isText(mimeType) || isCodeFile(ext) -> "text"
            isArchive(mimeType) || isArchiveExtension(ext) -> "archive"
            isApk(ext) -> "apk"
            mimeType.contains("spreadsheet") || ext in setOf("xls", "xlsx", "csv") -> "spreadsheet"
            mimeType.contains("presentation") || ext in setOf("ppt", "pptx") -> "presentation"
            mimeType.contains("document") || ext in setOf("doc", "docx") -> "document"
            else -> "unknown"
        }
    }
}
