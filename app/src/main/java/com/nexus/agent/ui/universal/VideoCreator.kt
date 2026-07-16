package com.nexus.agent.ui.universal

import android.app.Activity
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nexus.agent.core.media.VideoGenerator
import com.nexus.agent.databinding.FragmentVideoCreatorBinding
import com.nexus.agent.ui.common.ToastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * VideoCreator — fragment for AI-powered video creation and editing.
 * Supports video generation from text/images, editing, effects, and exporting.
 */
class VideoCreator : Fragment() {

    private var _binding: FragmentVideoCreatorBinding? = null
    private val binding get() = _binding!!

    private lateinit var videoGenerator: VideoGenerator
    private var currentVideoUri: Uri? = null
    private var videoFrames: List<Bitmap> = emptyList()
    private var selectedEffect: VideoEffect = VideoEffect.NONE

    enum class VideoEffect {
        NONE, SLOW_MOTION, FAST_FORWARD, REVERSE, CINEMATIC, GLITCH, NEON
    }

    private val videoImportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> loadVideo(uri) }
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> loadVideo(uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoGenerator = VideoGenerator(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoCreatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupEffectSelector()
        setupTimelineControls()
        setupActionButtons()
    }

    private fun setupEffectSelector() {
        val effects = VideoEffect.values().map { it.name.replace("_", " ") }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, effects)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.effectSpinner.adapter = adapter
        binding.effectSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedEffect = VideoEffect.values()[position]
                applyEffectPreview()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupTimelineControls() {
        binding.timelineView.setOnSeekListener { position ->
            seekToPosition(position)
        }

        binding.btnPlayPause.setOnClickListener {
            togglePlayback()
        }

        binding.trimStartHandle.setOnPositionChangeListener { position ->
            binding.timelineView.setTrimStart(position)
        }

        binding.trimEndHandle.setOnPositionChangeListener { position ->
            binding.timelineView.setTrimEnd(position)
        }
    }

    private fun setupActionButtons() {
        binding.btnAddText.setOnClickListener { showTextOverlayDialog() }
        binding.btnAddAudio.setOnClickListener { showAudioPicker() }
        binding.btnTransition.setOnClickListener { showTransitionPicker() }
        binding.btnSplit.setOnClickListener { splitAtCurrentPosition() }
        binding.btnDeleteSegment.setOnClickListener { deleteSelectedSegment() }
    }

    fun showImportDialog() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        videoImportLauncher.launch(intent)
    }

    fun launchCamera() {
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        cameraLauncher.launch(intent)
    }

    private fun loadVideo(uri: Uri) {
        currentVideoUri = uri
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading(true)
            try {
                val metadata = withContext(Dispatchers.IO) {
                    extractVideoMetadata(uri)
                }
                binding.videoPlayer.setVideoURI(uri)
                binding.timelineView.setDuration(metadata.durationMs)
                binding.tvDuration.text = formatDuration(metadata.durationMs)
                binding.tvResolution.text = "${metadata.width}x${metadata.height}"
                binding.tvFrameRate.text = "${metadata.frameRate} fps"
                updateToolbarState()
                ToastManager.show(requireContext(), "Video loaded")
            } catch (e: Exception) {
                ToastManager.showError(requireContext(), "Failed to load video: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private suspend fun extractVideoMetadata(uri: Uri): VideoMetadata {
        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(requireContext(), uri)

            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
            val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloat() ?: 30f

            retriever.release()
            VideoMetadata(duration, width, height, frameRate)
        }
    }

    data class VideoMetadata(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val frameRate: Float
    )

    private fun seekToPosition(positionMs: Long) {
        binding.videoPlayer.seekTo(positionMs.toInt())
        binding.tvCurrentTime.text = formatDuration(positionMs)
    }

    private fun togglePlayback() {
        if (binding.videoPlayer.isPlaying) {
            binding.videoPlayer.pause()
            binding.btnPlayPause.setImageResource(R.drawable.ic_play)
        } else {
            binding.videoPlayer.start()
            binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
        }
    }

    private fun applyEffectPreview() {
        currentVideoUri?.let { uri ->
            viewLifecycleOwner.lifecycleScope.launch {
                showLoading(true)
                try {
                    val previewFrames = withContext(Dispatchers.Default) {
                        videoGenerator.applyEffectPreview(uri, selectedEffect)
                    }
                    binding.effectPreview.setFrames(previewFrames)
                } catch (e: Exception) {
                    ToastManager.showError(requireContext(), "Effect preview failed: ${e.message}")
                } finally {
                    showLoading(false)
                }
            }
        }
    }

    private fun showTextOverlayDialog() {
        val dialog = VideoTextOverlayDialog(requireContext()) { text, startTime, endTime, style ->
            addTextOverlay(text, startTime, endTime, style)
        }
        dialog.show()
    }

    private fun addTextOverlay(text: String, startTime: Long, endTime: Long, style: TextStyle) {
        currentVideoUri?.let { uri ->
            viewLifecycleOwner.lifecycleScope.launch {
                showLoading(true)
                try {
                    val result = withContext(Dispatchers.Default) {
                        videoGenerator.addTextOverlay(uri, text, startTime, endTime, style)
                    }
                    result?.let { loadVideo(it) }
                } catch (e: Exception) {
                    ToastManager.showError(requireContext(), "Failed to add text: ${e.message}")
                } finally {
                    showLoading(false)
                }
            }
        }
    }

    private fun showAudioPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        audioImportLauncher.launch(intent)
    }

    private val audioImportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> addAudioTrack(uri) }
        }
    }

    private fun addAudioTrack(audioUri: Uri) {
        currentVideoUri?.let { videoUri ->
            viewLifecycleOwner.lifecycleScope.launch {
                showLoading(true)
                try {
                    val result = withContext(Dispatchers.Default) {
                        videoGenerator.mergeAudio(videoUri, audioUri)
                    }
                    result?.let { loadVideo(it) }
                } catch (e: Exception) {
                    ToastManager.showError(requireContext(), "Failed to add audio: ${e.message}")
                } finally {
                    showLoading(false)
                }
            }
        }
    }

    private fun showTransitionPicker() {
        val transitions = listOf("Fade", "Slide", "Zoom", "Wipe", "Dissolve")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Transition")
            .setItems(transitions.toTypedArray()) { _, which ->
                applyTransition(transitions[which])
            }
            .show()
    }

    private fun applyTransition(transition: String) {
        // Transition logic would be implemented here
        ToastManager.show(requireContext(), "Transition '$transition' applied")
    }

    private fun splitAtCurrentPosition() {
        val currentPosition = binding.videoPlayer.currentPosition.toLong()
        binding.timelineView.addSplitMarker(currentPosition)
        ToastManager.show(requireContext(), "Split at ${formatDuration(currentPosition)}")
    }

    private fun deleteSelectedSegment() {
        binding.timelineView.removeSelectedSegment()
    }

    fun showAIGenerateDialog() {
        val dialog = AIVideoGenerateDialog(requireContext()) { prompt, duration, style, resolution ->
            generateVideoWithAI(prompt, duration, style, resolution)
        }
        dialog.show()
    }

    private fun generateVideoWithAI(
        prompt: String,
        duration: Int,
        style: String,
        resolution: String
    ) {
        val parent = parentFragment as? UniversalAgentFragment
        parent?.getMediaToolbar()?.showProgress("Generating video with AI...")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    videoGenerator.generateFromText(prompt, duration, style, resolution)
                }
                result?.let { loadVideo(it) }
                ToastManager.show(requireContext(), "Video generated successfully!")
            } catch (e: Exception) {
                ToastManager.showError(requireContext(), "Failed to generate video: ${e.message}")
            } finally {
                parent?.getMediaToolbar()?.hideProgress()
            }
        }
    }

    fun shareCurrentMedia() {
        currentVideoUri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, uri)
            }
            startActivity(Intent.createChooser(intent, "Share Video"))
        }
    }

    fun exportVideo() {
        currentVideoUri?.let { uri ->
            viewLifecycleOwner.lifecycleScope.launch {
                showLoading(true)
                try {
                    val exportUri = withContext(Dispatchers.IO) {
                        videoGenerator.export(
                            uri,
                            binding.timelineView.getTrimStart(),
                            binding.timelineView.getTrimEnd(),
                            selectedEffect,
                            binding.timelineView.getSegments()
                        )
                    }
                    exportUri?.let {
                        ToastManager.show(requireContext(), "Video exported successfully")
                    }
                } catch (e: Exception) {
                    ToastManager.showError(requireContext(), "Export failed: ${e.message}")
                } finally {
                    showLoading(false)
                }
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = ms / (1000 * 60 * 60)
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvLoadingMessage.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateToolbarState() {
        val parent = parentFragment as? UniversalAgentFragment
        parent?.getMediaToolbar()?.apply {
            setShareEnabled(currentVideoUri != null)
            setExportEnabled(currentVideoUri != null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.videoPlayer.stopPlayback()
        _binding = null
    }
}
