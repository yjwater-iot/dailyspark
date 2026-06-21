package com.example.dailyspark

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceManager(
    context: Context,
    private val onTranscript: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
    private var textToSpeech: TextToSpeech? = null

    init {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onListeningChanged(true)
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = onListeningChanged(false)
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                onListeningChanged(false)
                onError("Speech recognition error: $error")
            }

            override fun onResults(results: Bundle?) {
                onListeningChanged(false)
                val transcript = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                onTranscript(transcript)
            }
        })

        textToSpeech = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
            }
        }
    }

    fun speakPrompt() {
        textToSpeech?.speak(
            "What small thing did you notice today?",
            TextToSpeech.QUEUE_FLUSH,
            null,
            "daily-spark-prompt"
        )
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("Speech recognition is not available on this device.")
            return
        }
        speakPrompt()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell DailySpark what you noticed today")
        }
        speechRecognizer.startListening(intent)
    }

    fun shutdown() {
        speechRecognizer.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
