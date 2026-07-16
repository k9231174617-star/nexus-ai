package com.nexus.agent.ui.universal

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nexus.agent.core.media.ImageProcessor
import com.nexus.agent.databinding.FragmentImageEditorBinding
import com.nexus.agent.ui.common.ToastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ImageEditor — comprehensive image editing fragment with AI-powered features.
 * Supports filters, cropping, drawing, text overlay, and AI image generation.
 */
class ImageEditor : Fragment() {

    private var _binding: FragmentImageEditorBinding? = null
    private val binding get() = _binding!!

    private lateinit var imageProcessor: ImageProcessor
    private var currentBitmap: Bitmap? = null
    private var originalBitmap: Bitmap? = null
    private var isDrawingMode = false
    private var paintColor = Color.RED
    private var brushSize = 10f

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> loadImage(uri) }
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let { setImage(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageProcessor = ImageProcessor(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawingCanvas()
        setupFilterControls()
        setupToolButtons()
    }

    private fun setupDrawingCanvas() {
        binding.drawingView.setOnTouchListener { _, event ->
            if (!isDrawingMode) return@setOnTouchListener false
            handleDrawingTouch(event)
            true
        }
    }

    private fun handleDrawingTouch(event: MotionEvent) {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> binding.drawingView.startPath(x, y, paintColor, brushSize)
            MotionEvent.ACTION_MOVE -> binding.drawingView.movePath(x, y)
            MotionEvent.ACTION_UP -> binding.drawingView.endPath()
        }
    }

    private fun setupFilterControls() {
        binding.seekBarBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) applyBrightness(progress - 100)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekBarContrast.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) applyContrast(progress / 50f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekBarSaturation.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) applySaturation(progress / 50f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupToolButtons() {
        binding.btnFilter.setOnClickListener { showFilterPanel() }
        binding.btnCrop.setOnClickListener { enterCropMode() }
        binding.btnDraw.setOnClickListener { toggleDrawingMode() }
        binding.btnText.setOnClickListener { showTextOverlayDialog() }
        binding.btnUndo.setOnClickListener { undoLastAction() }
        binding.btnReset.setOnClickListener { resetImage() }
        binding.btnAIGenerate.setOnClickListener { showAIGenerateDialog() }
    }

    fun showImportDialog() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        importLauncher.launch(intent)
    }

    fun launchCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }

    private fun loadImage(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading(true)
            val bitmap = withContext(Dispatchers.IO) {
                imageProcessor.loadFromUri(uri)
            }
            bitmap?.let { setImage(it) }
            showLoading(false)
        }
    }

    private fun setImage(bitmap: Bitmap) {
        currentBitmap = bitmap
        originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        binding.imagePreview.setImageBitmap(bitmap)
        binding.drawingView.setBaseBitmap(bitmap)
        updateToolbarState()
    }

    private fun applyBrightness(value: Int) {
        currentBitmap?.let { bitmap ->
            viewLifecycleOwner.lifecycleScope.launch {
                val result = withContext(Dispatchers.Default) {
                    imageProcessor.adjustBrightness(bitmap, value)
                }
                binding.imagePreview.setImageBitmap(result)
            }
        }
    }

    private fun applyContrast(value: Float) {
        currentBitmap?.let { bitmap ->
            viewLifecycleOwner.lifecycleScope.launch {
                val result = withContext(Dispatchers.Default) {
                    imageProcessor.adjustContrast(bitmap, value)
                }
                binding.imagePreview.setImageBitmap(result)
            }
        }
    }

    private fun applySaturation(value: Float) {
        currentBitmap?.let { bitmap ->
            viewLifecycleOwner.lifecycleScope.launch {
                val result = withContext(Dispatchers.Default) {
                    imageProcessor.adjustSaturation(bitmap, value)
                }
                binding.imagePreview.setImageBitmap(result)
            }
        }
    }

    private fun showFilterPanel() {
        binding.filterPanel.visibility = View.VISIBLE
        binding.toolPanel.visibility = View.GONE
    }

    private fun enterCropMode() {
        binding.cropOverlay.visibility = View.VISIBLE
        binding.cropOverlay.setOnCropCompleteListener { rect ->
            cropImage(rect)
        }
    }

    private fun cropImage(rect: RectF) {
        currentBitmap?.let { bitmap ->
            viewLifecycleOwner.lifecycleScope.launch {
                val result = withContext(Dispatchers.Default) {
                    imageProcessor.crop(bitmap, rect)
                }
                setImage(result)
                binding.cropOverlay.visibility = View.GONE
            }
        }
    }

    private fun toggleDrawingMode() {
        isDrawingMode = !isDrawingMode
        binding.drawingView.isDrawingEnabled = isDrawingMode
        binding.btnDraw.isSelected = isDrawingMode
        binding.brushControls.visibility = if (isDrawingMode) View.VISIBLE else View.GONE
    }

    private fun showTextOverlayDialog() {
        val dialog = TextOverlayDialog(requireContext()) { text, color, size, x, y ->
            addTextOverlay(text, color, size, x, y)
        }
        dialog.show()
    }

    private fun addTextOverlay(text: String, color: Int, size: Float, x: Float, y: Float) {
        currentBitmap?.let { bitmap ->
            viewLifecycleOwner.lifecycleScope.launch {
                val result = withContext(Dispatchers.Default) {
                    imageProcessor.addText(bitmap, text, color, size, x, y)
                }
                setImage(result)
            }
        }
    }

    private fun undoLastAction() {
        binding.drawingView.undo()
    }

    private fun resetImage() {
        originalBitmap?.let {
            setImage(it.copy(Bitmap.Config.ARGB_8888, true))
        }
    }

    fun showAIGenerateDialog() {
        val dialog = AIGenerateDialog(requireContext()) { prompt, style, resolution ->
            generateImageWithAI(prompt, style, resolution)
        }
        dialog.show()
    }

    private fun generateImageWithAI(prompt: String, style: String, resolution: String) {
        val parent = parentFragment as? UniversalAgentFragment
        parent?.getMediaToolbar()?.showProgress("Generating image with AI...")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    imageProcessor.generateWithAI(prompt, style, resolution)
                }
                result?.let { setImage(it) }
                ToastManager.show(requireContext(), "Image generated successfully!")
            } catch (e: Exception) {
                ToastManager.showError(requireContext(), "Failed to generate image: ${e.message}")
            } finally {
                parent?.getMediaToolbar()?.hideProgress()
            }
        }
    }

    fun shareCurrentMedia() {
        currentBitmap?.let { bitmap ->
            viewLifecycleOwner.lifecycleScope.launch {
                val uri = withContext(Dispatchers.IO) {
                    imageProcessor.saveToCache(bitmap)
                }
                uri?.let { shareImage(it) }
            }
        }
    }

    private fun shareImage(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        startActivity(Intent.createChooser(intent, "Share Image"))
    }

    fun exportImage() {
        currentBitmap?.let { bitmap ->
            viewLifecycleOwner.lifecycleScope.launch {
                showLoading(true)
                val success = withContext(Dispatchers.IO) {
                    imageProcessor.saveToGallery(bitmap, "NexusAI_Edit_${System.currentTimeMillis()}")
                }
                showLoading(false)
                if (success) {
                    ToastManager.show(requireContext(), "Image saved to gallery")
                } else {
                    ToastManager.showError(requireContext(), "Failed to save image")
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateToolbarState() {
        val parent = parentFragment as? UniversalAgentFragment
        parent?.getMediaToolbar()?.apply {
            setShareEnabled(currentBitmap != null)
            setExportEnabled(currentBitmap != null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentBitmap?.recycle()
        originalBitmap?.recycle()
        _binding = null
    }
}
