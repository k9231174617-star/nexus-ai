// app/src/main/java/com/nexus/agent/ui/files/FilePreviewPanel.kt
package com.nexus.agent.ui.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.caverock.androidsvg.SVG
import com.nexus.agent.R
import com.nexus.agent.core.files.model.ApkInfo
import com.nexus.agent.core.files.model.FileItem
import com.nexus.agent.core.files.model.FilePreviewState
import com.nexus.agent.core.files.model.PreviewType
import com.nexus.agent.core.files.model.SyntaxSpan
import com.nexus.agent.core.files.model.SyntaxStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile

class FilePreviewPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val contentContainer: FrameLayout
    private val loadingView: View
    private val errorView: TextView
    private val unsupportedView: View
    
    private var currentMediaPlayer: MediaPlayer? = null
    private var currentPdfRenderer: PdfRenderer? = null
    private var onActionListener: ((PreviewAction) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.panel_file_preview, this, true)
        
        contentContainer = findViewById(R.id.fl_preview_content)
        loadingView = findViewById(R.id.v_loading)
        errorView = findViewById(R.id.tv_error)
        unsupportedView = findViewById(R.id.v_unsupported)
        
        // Кнопки действий
        findViewById<View>(R.id.btn_open).setOnClickListener {
            onActionListener?.invoke(PreviewAction.OPEN)
        }
        findViewById<View>(R.id.btn_share).setOnClickListener {
            onActionListener?.invoke(PreviewAction.SHARE)
        }
        findViewById<View>(R.id.btn_send_to_rag).setOnClickListener {
            onActionListener?.invoke(PreviewAction.SEND_TO_RAG)
        }
        findViewById<View>(R.id.btn_send_to_chat).setOnClickListener {
            onActionListener?.invoke(PreviewAction.SEND_TO_CHAT)
        }
    }

    fun setState(state: FilePreviewState) {
        cleanup()
        
        loadingView.isVisible = state.isLoading
        errorView.isVisible = state.error != null
        errorView.text = state.error
        
        if (state.error != null || state.isLoading) {
            contentContainer.removeAllViews()
            return
        }

        val file = state.file ?: return
        renderPreview(file, state)
    }

    fun setOnActionListener(listener: (PreviewAction) -> Unit) {
        onActionListener = listener
    }

    private fun renderPreview(file: FileItem, state: FilePreviewState) {
        contentContainer.removeAllViews()
        
        when (state.previewType) {
            PreviewType.TEXT -> renderTextPreview(state.textContent, state.highlightedSpans)
            PreviewType.IMAGE -> renderImagePreview(file)
            PreviewType.SVG -> renderSvgPreview(file)
            PreviewType.PDF -> renderPdfPreview(file, state.currentPdfPage)
            PreviewType.VIDEO -> renderVideoPreview(file)
            PreviewType.AUDIO -> renderAudioPreview(file, state.isPlaying)
            PreviewType.APK -> renderApkPreview(state.apkInfo)
            PreviewType.ARCHIVE -> renderArchivePreview(state.archiveContents)
            PreviewType.BINARY, PreviewType.UNSUPPORTED -> showUnsupported()
        }
    }

    // ==================== RENDERERS ====================

    private fun renderTextPreview(content: String?, spans: List<SyntaxSpan>) {
        val textView = TextView(context).apply {
            textSize = 12f
            setTextColor(context.getColor(R.color.text_primary))
            setPadding(16, 16, 16, 16)
            isVerticalScrollBarEnabled = true
        }
        
        content?.let { text ->
            // Простая подсветка синтаксиса
            val spannable = android.text.SpannableString(text.take(5000)) // Лимит 5000 символов
            spans.forEach { span ->
                val color = when (span.style) {
                    SyntaxStyle.KEYWORD -> R.color.syntax_keyword
                    SyntaxStyle.STRING -> R.color.syntax_string
                    SyntaxStyle.COMMENT -> R.color.syntax_comment
                    SyntaxStyle.NUMBER -> R.color.syntax_number
                    SyntaxStyle.FUNCTION -> R.color.syntax_function
                    SyntaxStyle.TYPE -> R.color.syntax_type
                    else -> R.color.text_primary
                }
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(context.getColor(color)),
                    span.start.coerceIn(0, spannable.length),
                    span.end.coerceIn(0, spannable.length),
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            textView.text = spannable
        }
        
        contentContainer.addView(textView)
    }

    private fun renderImagePreview(file: FileItem) {
        val imageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        Glide.with(context)
            .load(file.absolutePath)
            .fitCenter()
            .into(imageView)
        
        contentContainer.addView(imageView)
    }

    private fun renderSvgPreview(file: FileItem) {
        val imageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val svg = SVG.getFromString(File(file.absolutePath).readText())
                val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                svg.renderToCanvas(canvas)
                
                withContext(Dispatchers.Main) {
                    imageView.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorView.text = "SVG Error: ${e.message}"
                    errorView.isVisible = true
                }
            }
        }
        
        contentContainer.addView(imageView)
    }

    private fun renderPdfPreview(file: FileItem, pageIndex: Int) {
        try {
            val fd = ParcelFileDescriptor.open(File(file.absolutePath), ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            currentPdfRenderer = renderer
            
            if (pageIndex < renderer.pageCount) {
                val page = renderer.openPage(pageIndex)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                val imageView = ImageView(context).apply {
                    setImageBitmap(bitmap)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                contentContainer.addView(imageView)
            }
        } catch (e: Exception) {
            errorView.text = "PDF Error: ${e.message}"
            errorView.isVisible = true
        }
    }

    private fun renderVideoPreview(file: FileItem) {
        // Упрощённое превью — первый кадр через Glide
        val imageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        Glide.with(context)
            .load(file.absolutePath)
            .placeholder(R.drawable.ic_video)
            .into(imageView)
        
        // Индикатор "play"
        val playOverlay = ImageView(context).apply {
            setImageResource(R.drawable.ic_play_circle)
            layoutParams = LayoutParams(64, 64).apply {
                gravity = android.view.Gravity.CENTER
            }
        }
        
        val container = FrameLayout(context).apply {
            addView(imageView)
            addView(playOverlay)
        }
        
        contentContainer.addView(container)
    }

    private fun renderAudioPreview(file: FileItem, isPlaying: Boolean) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        
        val playButton = ImageView(context).apply {
            setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            layoutParams = LinearLayout.LayoutParams(64, 64)
            setOnClickListener {
                toggleAudioPlayback(file.absolutePath)
            }
        }
        
        val nameText = TextView(context).apply {
            text = file.name
            textSize = 14f
            setTextColor(context.getColor(R.color.text_primary))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }
        
        layout.addView(playButton)
        layout.addView(nameText)
        contentContainer.addView(layout)
    }

    private fun renderApkPreview(apkInfo: ApkInfo?) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        
        val iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(72, 72).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
            setImageResource(R.drawable.ic_apk)
        }
        
        apkInfo?.let { info ->
            listOf(
                "App: ${info.appName}" to 16f,
                "Package: ${info.packageName}" to 12f,
                "Version: ${info.versionName} (${info.versionCode})" to 12f
            ).forEach { (text, size) ->
                layout.addView(TextView(context).apply {
                    this.text = text
                    textSize = size
                    setTextColor(context.getColor(R.color.text_primary))
                    setPadding(0, 8, 0, 0)
                })
            }
        }
        
        layout.addView(iconView, 0)
        contentContainer.addView(layout)
    }

    private fun renderArchivePreview(contents: List<String>) {
        val textView = TextView(context).apply {
            text = contents.take(50).joinToString("\n") // Первые 50 файлов
            textSize = 12f
            setTextColor(context.getColor(R.color.text_primary))
            setPadding(16, 16, 16, 16)
            isVerticalScrollBarEnabled = true
        }
        
        if (contents.isEmpty()) {
            textView.text = "Empty archive or unable to read contents"
        }
        
        contentContainer.addView(textView)
    }

    private fun showUnsupported() {
        unsupportedView.isVisible = true
        contentContainer.removeAllViews()
        contentContainer.addView(unsupportedView)
    }

    // ==================== AUDIO PLAYBACK ====================

    private fun toggleAudioPlayback(path: String) {
        currentMediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.start()
            }
            return
        }
        
        currentMediaPlayer = MediaPlayer().apply {
            setDataSource(path)
            prepare()
            start()
            setOnCompletionListener {
                cleanup()
            }
        }
    }

    // ==================== CLEANUP ====================

    fun cleanup() {
        currentMediaPlayer?.release()
        currentMediaPlayer = null
        currentPdfRenderer?.close()
        currentPdfRenderer = null
        contentContainer.removeAllViews()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cleanup()
    }

    // ==================== ACTIONS ====================

    enum class PreviewAction {
        OPEN, SHARE, SEND_TO_RAG, SEND_TO_CHAT, EDIT, EXECUTE
    }
}
