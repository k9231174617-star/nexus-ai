package com.nexus.agent.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ImageInfo(
    val width: Int,
    val height: Int,
    val format: String,
    val sizeBytes: Long,
    val orientation: Int,
    val hasExif: Boolean,
)

data class ProcessResult(
    val outputPath: String,
    val originalSize: Long,
    val newSize: Long,
    val width: Int,
    val height: Int,
)

@Singleton
class ImageProcessor @Inject constructor(
    private val context: Context,
) {
    suspend fun getInfo(uri: Uri): ImageInfo = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open: $uri")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(stream, null, options)
        stream.close()

        val exifStream = context.contentResolver.openInputStream(uri)
        val exif = exifStream?.let { ExifInterface(it) }
        exifStream?.close()

        val orientation = exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        ) ?: ExifInterface.ORIENTATION_NORMAL

        val fileStat = context.contentResolver.query(
            uri, arrayOf("_size"), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        } ?: 0L

        ImageInfo(
            width = options.outWidth,
            height = options.outHeight,
            format = options.outMimeType ?: "unknown",
            sizeBytes = fileStat,
            orientation = orientation,
            hasExif = exif != null,
        )
    }

    suspend fun resize(
        uri: Uri,
        maxWidth: Int,
        maxHeight: Int,
        quality: Int = 85,
    ): ProcessResult = withContext(Dispatchers.IO) {
        val bmp = loadBitmap(uri)
        val scaled = scaleBitmap(bmp, maxWidth, maxHeight)
        val outFile = createTempFile("resized")
        FileOutputStream(outFile).use { fos ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, fos)
        }
        bmp.recycle()
        ProcessResult(
            outputPath = outFile.absolutePath,
            originalSize = outFile.length(),
            newSize = outFile.length(),
            width = scaled.width,
            height = scaled.height,
        )
    }

    suspend fun compress(uri: Uri, quality: Int = 70): ProcessResult =
        withContext(Dispatchers.IO) {
            val bmp = loadBitmap(uri)
            val outFile = createTempFile("compressed")
            FileOutputStream(outFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }
            bmp.recycle()
            ProcessResult(
                outputPath = outFile.absolutePath,
                originalSize = 0L,
                newSize = outFile.length(),
                width = bmp.width,
                height = bmp.height,
            )
        }

    suspend fun toGrayscale(uri: Uri): ProcessResult = withContext(Dispatchers.IO) {
        val bmp = loadBitmap(uri)
        val result = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().also { it.setSaturation(0f) })
        }
        canvas.drawBitmap(bmp, 0f, 0f, paint)
        bmp.recycle()
        val outFile = createTempFile("grayscale")
        FileOutputStream(outFile).use { fos ->
            result.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        ProcessResult(outFile.absolutePath, 0L, outFile.length(), result.width, result.height)
    }

    suspend fun rotate(uri: Uri, degrees: Float): ProcessResult = withContext(Dispatchers.IO) {
        val bmp = loadBitmap(uri)
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        bmp.recycle()
        val outFile = createTempFile("rotated")
        FileOutputStream(outFile).use { fos ->
            rotated.compress(Bitmap.CompressFormat.JPEG, 95, fos)
        }
        ProcessResult(outFile.absolutePath, 0L, outFile.length(), rotated.width, rotated.height)
    }

    suspend fun crop(uri: Uri, x: Int, y: Int, w: Int, h: Int): ProcessResult =
        withContext(Dispatchers.IO) {
            val bmp = loadBitmap(uri)
            val cropped = Bitmap.createBitmap(bmp, x, y, w, h)
            bmp.recycle()
            val outFile = createTempFile("cropped")
            FileOutputStream(outFile).use { fos ->
                cropped.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }
            ProcessResult(outFile.absolutePath, 0L, outFile.length(), cropped.width, cropped.height)
        }

    suspend fun toBase64(uri: Uri, maxDimension: Int = 1024): String =
        withContext(Dispatchers.IO) {
            val bmp = loadBitmap(uri)
            val scaled = scaleBitmap(bmp, maxDimension, maxDimension)
            bmp.recycle()
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
        }

    private fun loadBitmap(uri: Uri): Bitmap {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open: $uri")
        return BitmapFactory.decodeStream(stream).also { stream.close() }
            ?: throw IllegalStateException("Failed to decode bitmap")
    }

    private fun scaleBitmap(bmp: Bitmap, maxW: Int, maxH: Int): Bitmap {
        val ratio = minOf(maxW.toFloat() / bmp.width, maxH.toFloat() / bmp.height)
        if (ratio >= 1f) return bmp
        val newW = (bmp.width * ratio).toInt()
        val newH = (bmp.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bmp, newW, newH, true)
    }

    private fun createTempFile(prefix: String): File {
        val dir = File(context.cacheDir, "nexus_images").also { it.mkdirs() }
        return File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
    }
}