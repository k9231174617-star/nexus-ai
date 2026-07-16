package com.nexus.agent.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    data class Result(val text: String, val confidence: Float) : VoiceState()
    data class Error(val code: Int, val message: String) : VoiceState()
}

@Singleton
class VoiceInputManager @Inject constructor(
    private val context: Context,
    private val permissionHelper: VoicePermissionHelper,
) {
    private var recognizer: SpeechRecognizer? = null
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(languageCode: String = "ru-RU") {
        if (!permissionHelper.hasPermission()) {
            _state.value = VoiceState.Error(-1, "Microphone permission not granted")
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = VoiceState.Listening
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    val text = matches?.firstOrNull() ?: ""
                    val confidence = scores?.firstOrNull() ?: 0f
                    _state.value = VoiceState.Result(text, confidence)
                }
                override fun onError(error: Int) {
                    _state.value = VoiceState.Error(error, errorMessage(error))
                }
                override fun onBeginningOfSpeech() {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: return
                    _state.value = VoiceState.Result(partial, 0f)
                }
                override fun onRmsChanged(rmsdB: Float) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
        _state.value = VoiceState.Idle
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        _state.value = VoiceState.Idle
    }

    private fun errorMessage(code: Int) = when (code) {
        SpeechRecognizer.ERROR_AUDIO            -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT           -> "Client side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK          -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT  -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH         -> "No speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY  -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER           -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT   -> "Speech timeout"
        else -> "Unknown error: $code"
    }
}