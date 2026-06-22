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
    private val onSessionTranscript: (String) -> Unit,
    private val onSessionComplete: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private var isSessionActive = false
    private var listeningStartedAt = 0L
    private var sessionTranscript = ""
    private var consecutiveNoSpeechTimeouts = 0

    private val maxSessionDurationRunnable = Runnable {
        if (isSessionActive) {
            stopDictationSession()
        }
    }

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
            override fun onEndOfSpeech() = finishListeningAfterMinimum()

            override fun onPartialResults(partialResults: Bundle?) {
                val partialTranscript = partialResults.bestRecognitionResult()
                if (partialTranscript.isNotBlank()) {
                    onSessionTranscript(combineTranscript(sessionTranscript, partialTranscript))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                finishListeningAfterMinimum {
                    if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                        consecutiveNoSpeechTimeouts += 1
                        if (consecutiveNoSpeechTimeouts >= MAX_CONSECUTIVE_NO_SPEECH_TIMEOUTS) {
                            stopDictationSession()
                        } else {
                            speakAnythingToAddPrompt()
                        }
                    } else {
                        onError("Speech recognition error: $error")
                        if (isSessionActive) {
                            restartListening()
                        }
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val transcript = results.bestRecognitionResult().trim()
                finishListeningAfterMinimum {
                    if (transcript.isBlank()) {
                        handleNoSpeechFinalResult()
                    } else {
                        consecutiveNoSpeechTimeouts = 0
                        sessionTranscript = combineTranscript(sessionTranscript, transcript)
                        onSessionTranscript(sessionTranscript)

                        if (transcript.isStopCommand()) {
                            stopDictationSession()
                        } else if (isSessionActive) {
                            speakAnythingToAddPrompt()
                        }
                    }
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
                if (utteranceId == ANYTHING_TO_ADD_UTTERANCE_ID && isSessionActive) {
                    mainHandler.post { restartListening() }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == ANYTHING_TO_ADD_UTTERANCE_ID && isSessionActive) {
                    mainHandler.post { restartListening() }
                }
            }
        })
    }

    private fun finishListeningAfterMinimum(action: () -> Unit = {}) {
        val elapsed = System.currentTimeMillis() - listeningStartedAt
        val remaining = (MINIMUM_LISTENING_MILLIS - elapsed).coerceAtLeast(0L)
        mainHandler.postDelayed({
            isListening = false
            onListeningChanged(false)
            action()
        }, remaining)
    }

    private fun speakAnythingToAddPrompt() {
        textToSpeech?.speak(
            ANYTHING_TO_ADD_PROMPT_TEXT,
            TextToSpeech.QUEUE_FLUSH,
            null,
            ANYTHING_TO_ADD_UTTERANCE_ID
        ) ?: restartListening()
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("Speech recognition is not available on this device.")
            return
        }
        if (isSessionActive || isListening) return

        sessionTranscript = ""
        consecutiveNoSpeechTimeouts = 0
        isSessionActive = true
        onSessionTranscript("")
        mainHandler.removeCallbacks(maxSessionDurationRunnable)
        mainHandler.postDelayed(maxSessionDurationRunnable, MAX_SESSION_DURATION_MILLIS)
        restartListening()
    }

    fun speakStory(story: String) {
        if (story.isBlank()) {
            onError("There is no story to play yet.")
            return
        }
        textToSpeech?.speak(
            story,
            TextToSpeech.QUEUE_FLUSH,
            null,
            STORY_UTTERANCE_ID
        ) ?: onError("Text to speech is not available on this device.")
    }

    private fun restartListening() {
        if (!isSessionActive) return
        startSpeechRecognizer()
    }

    private fun startSpeechRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell DailySpark what you noticed today")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, MINIMUM_LISTENING_MILLIS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, MINIMUM_LISTENING_MILLIS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, MINIMUM_LISTENING_MILLIS)
        }
        speechRecognizer.startListening(intent)
    }

    private fun handleNoSpeechFinalResult() {
        consecutiveNoSpeechTimeouts += 1
        if (consecutiveNoSpeechTimeouts >= MAX_CONSECUTIVE_NO_SPEECH_TIMEOUTS) {
            stopDictationSession()
        } else {
            speakAnythingToAddPrompt()
        }
    }

    private fun stopDictationSession() {
        if (!isSessionActive) return
        isSessionActive = false
        mainHandler.removeCallbacks(maxSessionDurationRunnable)
        textToSpeech?.stop()
        if (isListening) {
            speechRecognizer.cancel()
            isListening = false
            onListeningChanged(false)
        }
        onSessionTranscript(sessionTranscript)
        onSessionComplete(sessionTranscript)
    }

    fun shutdown() {
        mainHandler.removeCallbacksAndMessages(null)
        speechRecognizer.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private fun Bundle?.bestRecognitionResult(): String = this
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        .orEmpty()

    private fun combineTranscript(existingTranscript: String, nextTranscript: String): String =
        listOf(existingTranscript.trim(), nextTranscript.trim())
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")

    private fun String.isStopCommand(): Boolean {
        val normalized = trim()
            .lowercase(Locale.getDefault())
            .replace(Regex("[。！？!?,，.\\s]+"), "")
        return STOP_COMMANDS.any { command -> normalized.contains(command) }
    }

    private companion object {
        const val ANYTHING_TO_ADD_PROMPT_TEXT = "还有想补充的吗？没有的话可以说完了。"
        const val ANYTHING_TO_ADD_UTTERANCE_ID = "daily-spark-anything-to-add"
        const val STORY_UTTERANCE_ID = "daily-spark-story"
        const val MINIMUM_LISTENING_MILLIS = 5_000L
        const val MAX_SESSION_DURATION_MILLIS = 60_000L
        const val MAX_CONSECUTIVE_NO_SPEECH_TIMEOUTS = 2
        val STOP_COMMANDS = setOf("完了", "就这样", "没有了", "done", "that'sit")
    }
}
