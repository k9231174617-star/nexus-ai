package com.nexus.agent.core.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RecognitionResult(
    val text: String,
    val alternatives: List<String>,
    val confidence: Float,
    val languageCode: String,
)

@Singleton
class NexusSpeechRecognizer @Inject constructor(
    private val context: Context,
    private val voiceInputManager: VoiceInputManager,
) {
    suspend fun recognizeOnce(languageCode: String = "ru-RU"): RecognitionResult =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val stateJob = kotlinx.coroutines.GlobalScope.kotlinx.coroutines.launch(Dispatchers.Main) {
                    voiceInputManager.state.collect { state ->
                        when (state) {
                            is VoiceState.Result -> {
                                if (!cont.isCompleted) {
                                    cont.resume(
                                        RecognitionResult(
                                            text = state.text,
                                            alternatives = emptyList(),
                                            confidence = state.confidence,
                                            languageCode = languageCode,
                                        )
                                    )
                                }
                            }
                            is VoiceState.Error -> {
                                if (!cont.isCompleted) {
                                    cont.resumeWithException(
                                        RuntimeException(state.message)
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }
                cont.invokeOnCancellation {
                    stateJob.cancel()
                    voiceInputManager.stopListening()
                }
                voiceInputManager.startListening(languageCode)
            }
        }

    fun buildRecognizeIntent(languageCode: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
}