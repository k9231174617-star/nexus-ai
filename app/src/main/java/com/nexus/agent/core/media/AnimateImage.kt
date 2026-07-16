package com.nexus.agent.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class AnimationType {
    ZOOM_IN, ZOOM_OUT, PAN_LEFT, PAN_RIGHT, ROTATE, FADE, KEN_BURNS
}

data class AnimationConfig(
    val type: AnimationType = AnimationType.KEN_BURNS,
    val frameCount: Int = 60,
    val durationMs: Long = 2000,
    val fps: Int = 30,
)

@Singleton
class AnimateImage @Inject constructor(
    private val context: Context,
    private val imageProcessor: ImageProcessor,
) {
    suspend fun generateFrames(
        uri: Uri,
        config: AnimationConfig = AnimationConfig(),
    ): List<File> = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open: $uri")
        val source = BitmapFactory.decodeStream(stream)
        stream.close()

        val frames = mutableListOf<File>()
        val dir = File(context.cacheDir, "nexus_anim_${System.currentTimeMillis()}").also { it.mkdirs() }

        repeat(config.frameCount) { i ->
            val progress = i.toFloat() / config.frameCount
            val frame = when (config.type) {
                AnimationType.ZOOM_IN    -> applyZoom(source, 1f + progress * 0.5f)
                AnimationType.ZOOM_OUT   -> applyZoom(source, 1.5f - progress * 0.5f)
                AnimationType.PAN_LEFT   -> applyPan(source, -progress * 0.3f, 0f)
                AnimationType.PAN_RIGHT  -> applyPan(source, progress * 0.3f, 0f)
                AnimationType.ROTATE     -> applyRotation(source, progress * 360f)
                AnimationType.FADE       -> applyFade(source, progress)
                AnimationType.KEN_BURNS  -> applyKenBurns(source, progress)
            }
            val file = File(dir, "frame_${i.toString().padStart(4, '0')}.jpg")
            FileOutputStream(file).use { frame.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            if (frame !== source) frame.recycle()
            frames.add(file)
        }
        source.recycle()
        frames
    }

    private fun applyZoom(src: Bitmap, scale: Float): Bitmap {
        val matrix = Matrix().apply {
            val cx = src.width / 2f
            val cy = src.height / 2f
            postScale(scale, scale, cx, cy)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun applyPan(src: Bitmap, dx: Float, dy: Float): Bitmap {
        val offsetX = (src.width * dx).toInt().coerceIn(0, src.width / 4)
        val offsetY = (src.height * dy).toInt().coerceIn(0, src.height / 4)
        val w = src.width - offsetX
        val h = src.height - offsetY
        return Bitmap.createBitmap(src, offsetX, offsetY, w, h)
    }

    private fun applyRotation(src: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees, src.width / 2f, src.height / 2f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun applyFade(src: Bitmap, alpha: Float): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { this.alpha = (alpha * 255).toInt().coerceIn(0, 255) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun applyKenBurns(src: Bitmap, progress: Float): Bitmap {
        // Subtle pan + zoom combined
        val scale = 1f + progress * 0.2f
        val panX = progress * 0.1f
        val matrix = Matrix().apply {
            postScale(scale, scale, src.width / 2f, src.height / 2f)
            postTranslate(-src.width * panX, 0f)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }
}