package com.nexus.agent.core.voice

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.nexus.agent.NexusApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple energy‑based wake‑word detector.
 * In production, replace with a neural wake‑word model (Porcupine, Snowboy, etc.).
 */
@Singleton
class WakeWordDetector @Inject constructor() {

    private val app = NexusApplication.instance
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioRecord: AudioRecord? = null

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    enum class State { IDLE, LISTENING, DETECTED, SPEAKING }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val ENERGY_THRESHOLD = 500.0  // Tune for environment
        private const val MIN_DURATION_MS = 600L     // Min wake‑word duration
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val WAKE_WORD = "hey nexus"
    }

    /** Start listening for the wake word */
    fun startListening() {
        if (!hasPermission()) return
        if (_state.value == State.LISTENING) return

        scope.launch {
            _state.value = State.LISTENING
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, FORMAT)
            if (bufferSize <= 0) {
                _state.value = State.IDLE; return@launch
            }

            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, FORMAT, bufferSize * 4)
            audioRecord?.startRecording()

            val buffer = ShortArray(bufferSize)
            var detectedStart = 0L

            while (isActive() && _state.value == State.LISTENING) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read <= 0) continue

                val energy = computeEnergy(buffer, read)
                if (energy > ENERGY_THRESHOLD) {
                    if (detectedStart == 0L) detectedStart = System.currentTimeMillis()
                    val duration = System.currentTimeMillis() - detectedStart
                    if (duration >= MIN_DURATION_MS) {
                        _state.value = State.DETECTED
                        android.util.Log.i("WakeWord", "Wake word detected (energy=$energy)")
                        onWakeWordDetected()
                        detectedStart = 0L
                        delay(2000) // Cooldown
                        _state.value = State.LISTENING
                    }
                } else {
                    detectedStart = 0L
                }
            }
        }
    }

    /** Stop listening */
    fun stopListening() {
        _state.value = State.IDLE
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    /** Set speaking state (mute detection during speech) */
    fun setSpeaking(speaking: Boolean) {
        _state.value = if (speaking) State.SPEAKING else State.LISTENING
    }

    private fun onWakeWordDetected() {
        // Will be handled by VoiceInputManager
        android.util.Log.i("WakeWord", "Wake word callback triggered")
    }

    private fun computeEnergy(buffer: ShortArray, read: Int): Double {
        var sum = 0.0
        for (i in 0 until read) sum += buffer[i].toDouble() * buffer[i].toDouble()
        return sum / read
    }

    private fun isActive(): Boolean = _state.value == State.LISTENING || _state.value == State.SPEAKING

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun destroy() {
        stopListening()
        scope.cancel()
    }
}
