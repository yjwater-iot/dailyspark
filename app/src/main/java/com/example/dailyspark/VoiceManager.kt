package com.example.dailyspark

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class VoiceManager(
    context: Context,
    private val onTranscript: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private var listeningStartedAt = 0L

    init {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                listeningStartedAt = System.currentTimeMillis()
                onListeningChanged(true)
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = finishListeningAfterMinimum { onListeningChanged(false) }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                finishListeningAfterMinimum {
                    onError("Speech recognition error: $error")
                }
            }

            override fun onResults(results: Bundle?) {
                val transcript = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                finishListeningAfterMinimum {
                    onTranscript(transcript)
                }
            }
        })

        textToSpeech = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
            }
        }
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId == PROMPT_UTTERANCE_ID) {
                    mainHandler.post { startSpeechRecognizer() }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == PROMPT_UTTERANCE_ID) {
                    mainHandler.post { startSpeechRecognizer() }
                }
            }
        })
    }

    private fun finishListeningAfterMinimum(action: () -> Unit) {
        val elapsed = System.currentTimeMillis() - listeningStartedAt
        val remaining = (MINIMUM_LISTENING_MILLIS - elapsed).coerceAtLeast(0L)
        mainHandler.postDelayed({
            isListening = false
            onListeningChanged(false)
            action()
        }, remaining)
    }

    private fun speakPrompt() {
        textToSpeech?.speak(
            PROMPT_TEXT,
            TextToSpeech.QUEUE_FLUSH,
            null,
            PROMPT_UTTERANCE_ID
        )
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("Speech recognition is not available on this device.")
            return
        }
        if (isListening) return
        speakPrompt()
    }

    private fun startSpeechRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell DailySpark what you noticed today")
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, MINIMUM_LISTENING_MILLIS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, MINIMUM_LISTENING_MILLIS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, MINIMUM_LISTENING_MILLIS)
        }
        speechRecognizer.startListening(intent)
    }

    fun shutdown() {
        mainHandler.removeCallbacksAndMessages(null)
        speechRecognizer.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private companion object {
        const val PROMPT_TEXT = "What small thing did you notice today?"
        const val PROMPT_UTTERANCE_ID = "daily-spark-prompt"
        const val MINIMUM_LISTENING_MILLIS = 5_000L
    }
}
