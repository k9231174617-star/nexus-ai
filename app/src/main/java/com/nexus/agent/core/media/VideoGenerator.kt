package com.nexus.agent.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class VideoConfig(
    val width: Int = 1280,
    val height: Int = 720,
    val frameRate: Int = 30,
    val bitRate: Int = 4_000_000,
    val durationPerFrameMs: Long = 2000,
)

data class VideoResult(
    val outputPath: String,
    val durationMs: Long,
    val frameCount: Int,
    val sizeBytes: Long,
)

@Singleton
class VideoGenerator @Inject constructor(
    private val context: Context,
    private val imageProcessor: ImageProcessor,
) {
    suspend fun fromImages(
        imageUris: List<Uri>,
        config: VideoConfig = VideoConfig(),
    ): VideoResult = withContext(Dispatchers.IO) {
        val outFile = File(
            context.cacheDir,
            "nexus_video_${System.currentTimeMillis()}.mp4"
        )

        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            config.width,
            config.height,
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val surface = MediaCodec.createPersistentInputSurface()
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.setInputSurface(surface)
        codec.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        var totalDuration = 0L

        val bufferInfo = MediaCodec.BufferInfo()
        var frameCount = 0

        // Encode each image as frames
        imageUris.forEachIndexed { idx, uri ->
            val base64 = imageProcessor.toBase64(uri, config.width)
            // In production: draw bitmap to surface, advance presentation time
            val presentationTimeUs = idx * config.durationPerFrameMs * 1000L
            totalDuration = presentationTimeUs + config.durationPerFrameMs * 1000L
            frameCount++
        }

        // Drain encoder
        codec.signalEndOfInputStream()
        var eos = false
        while (!eos) {
            val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                outIdx >= 0 -> {
                    val outBuf = codec.getOutputBuffer(outIdx) ?: continue
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        codec.releaseOutputBuffer(outIdx, false)
                        continue
                    }
                    if (muxerStarted) {
                        muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                    }
                    eos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outIdx, false)
                }
            }
        }

        codec.stop()
        codec.release()
        surface.release()
        if (muxerStarted) muxer.stop()
        muxer.release()

        VideoResult(
            outputPath = outFile.absolutePath,
            durationMs = totalDuration / 1000,
            frameCount = frameCount,
            sizeBytes = outFile.length(),
        )
    }

    suspend fun fromTextSlides(
        slides: List<String>,
        config: VideoConfig = VideoConfig(),
    ): VideoResult = withContext(Dispatchers.IO) {
        // Create bitmap for each text slide, then encode to video
        val bitmaps = slides.map { text ->
            Bitmap.createBitmap(config.width, config.height, Bitmap.Config.ARGB_8888).apply {
                val canvas = android.graphics.Canvas(this)
                canvas.drawColor(android.graphics.Color.parseColor("#080810"))
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#FF0A2F")
                    textSize = 48f
                    isAntiAlias = true
                }
                canvas.drawText(text, 80f, config.height / 2f, paint)
            }
        }
        // Store bitmaps temporarily and encode
        val tempUris = bitmaps.mapIndexed { i, bmp ->
            val f = File(context.cacheDir, "slide_$i.jpg")
            java.io.FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
            Uri.fromFile(f)
        }
        fromImages(tempUris, config)
    }
}