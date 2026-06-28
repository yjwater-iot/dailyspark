package com.example.dailyspark

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.dailyspark.ui.theme.DailysparkTheme
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DailyReminderWorker.scheduleDaily7Pm(this)
        setContent {
            DailysparkTheme {
                DailySparkApp()
            }
        }
    }
}

@Composable
private fun DailySparkApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember {
        StoryRepository(DailySparkDatabase.getInstance(context).storyDao())
    }
    var step by remember { mutableStateOf(DailySparkStep.FirstThoughts) }
    var rawTranscript by remember { mutableStateOf("") }
    var activeTranscript by remember { mutableStateOf("") }
    var cleanedObservation by remember { mutableStateOf("") }
    var generatedStory by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Listening mode") }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    var voiceManager: VoiceManager? = null

    fun beginStep(nextStep: DailySparkStep) {
        step = nextStep
        activeTranscript = ""
        status = nextStep.statusText
        voiceManager?.speakThenListen(
            nextStep.spokenPrompt,
            nextStep.recognizerPrompt,
            finishAfterFirstResult = nextStep == DailySparkStep.ReadyToBuildStory
        )
    }

    voiceManager = remember {
        VoiceManager(
            context = context,
            onSessionTranscript = { spokenText ->
                activeTranscript = spokenText
            },
            onSessionComplete = { spokenText ->
                val captured = spokenText.trim()
                when (step) {
                    DailySparkStep.FirstThoughts -> {
                        if (captured.isNotBlank()) {
                            rawTranscript = captured
                        }
                        beginStep(DailySparkStep.ChangedBecause)
                    }
                    DailySparkStep.ChangedBecause -> {
                        rawTranscript = listOf(rawTranscript, captured)
                            .filter { it.isNotBlank() }
                            .joinToString(separator = "\n\n")
                        status = "Let me clean it up."
                        val draft = OfflineStoryGenerator().generate(rawTranscript)
                        cleanedObservation = draft.cleanedObservation
                        generatedStory = draft.generatedStory
                        scope.launch { repository.saveTranscript(rawTranscript) }
                        voiceManager?.speak("Let me clean it up. Here is the raw transcript and the cleaned observation. Ready to build story? Please say yes or no.") {
                            beginStep(DailySparkStep.ReadyToBuildStory)
                        }
                    }
                    DailySparkStep.ReadyToBuildStory -> {
                        if (captured.isYes()) {
                            status = "Generating your story…"
                            val story = generatedStory.ifBlank { OfflineStoryGenerator().generate(rawTranscript).generatedStory }
                            generatedStory = story
                            voiceManager?.speak("Your story is ready. I will read it now. $story When you need me again, I am back in listening mode.") {
                                status = "Done. Back in listening mode."
                            }
                        } else {
                            status = "Okay. Back in listening mode."
                            voiceManager?.speak("Okay. I will not build the story yet. I am back in listening mode.")
                        }
                    }
                }
            },
            onListeningChanged = { isListening ->
                status = if (isListening) "Listening… say finished when you are done." else step.statusText
            },
            onError = { message -> status = message }
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            beginStep(DailySparkStep.FirstThoughts)
        } else {
            status = "Microphone permission is needed to listen."
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(repository) {
        repository.stories.collect { stories ->
            stories.firstOrNull()?.let { latestStory ->
                if (rawTranscript.isBlank()) rawTranscript = latestStory.transcript
                if (cleanedObservation.isBlank()) cleanedObservation = latestStory.cleanedObservation
                if (generatedStory.isBlank()) generatedStory = latestStory.generatedStory
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { voiceManager?.shutdown() }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        DailySparkScreen(
            step = step,
            rawTranscript = rawTranscript,
            activeTranscript = activeTranscript,
            cleanedObservation = cleanedObservation,
            generatedStory = generatedStory,
            status = status,
            onStart = {
                if (hasAudioPermission) {
                    beginStep(DailySparkStep.FirstThoughts)
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onClearAllRecords = {
                scope.launch {
                    repository.clearAllRecords()
                    rawTranscript = ""
                    activeTranscript = ""
                    cleanedObservation = ""
                    generatedStory = ""
                    status = "All records cleared. Back in listening mode."
                }
            },
            showDebugControls = BuildConfig.DEBUG,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun DailySparkScreen(
    step: DailySparkStep,
    rawTranscript: String,
    activeTranscript: String,
    cleanedObservation: String,
    generatedStory: String,
    status: String,
    onStart: () -> Unit,
    onClearAllRecords: () -> Unit,
    showDebugControls: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "DailySpark",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = step.screenPrompt,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        ) {
            Text(text = "Start listening", fontSize = 30.sp)
        }
        if (showDebugControls) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onClearAllRecords,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(text = "Clear all records", fontSize = 18.sp)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = status, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(16.dp))
        LabeledText(label = "Raw transcript", text = visibleRawTranscript(rawTranscript, activeTranscript))
        if (step == DailySparkStep.ReadyToBuildStory || cleanedObservation.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            LabeledText(label = "Cleaned observation", text = cleanedObservation.ifBlank { "I will show this after I clean up your words." })
        }
        if (generatedStory.isNotBlank() && status.startsWith("Done")) {
            Spacer(modifier = Modifier.height(16.dp))
            LabeledText(label = "Story", text = generatedStory)
        }
    }
}

@Composable
private fun LabeledText(
    label: String,
    text: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text.ifBlank { "Listening…" },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun visibleRawTranscript(rawTranscript: String, activeTranscript: String): String =
    listOf(rawTranscript, activeTranscript)
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n\n")

private fun String.isYes(): Boolean {
    val normalized = lowercase(Locale.getDefault()).replace(Regex("[^a-z]"), " ")
    return normalized.split(Regex("\\s+")).any { it in setOf("yes", "yeah", "yep", "sure", "okay", "ok") }
}

private enum class DailySparkStep(
    val spokenPrompt: String,
    val recognizerPrompt: String,
    val screenPrompt: String,
    val statusText: String
) {
    FirstThoughts(
        spokenPrompt = "How's your day? Tell me anything you want to remember. Say finished when you are done.",
        recognizerPrompt = "How's your day?",
        screenPrompt = "How's your day?",
        statusText = "Listening mode"
    ),
    ChangedBecause(
        spokenPrompt = "What changed because of this? Say finished when you are done.",
        recognizerPrompt = "What changed because of this?",
        screenPrompt = "What changed because of this?",
        statusText = "Listening mode"
    ),
    ReadyToBuildStory(
        spokenPrompt = "Ready to build story? Please say yes or no.",
        recognizerPrompt = "Ready to build story?",
        screenPrompt = "Ready to build story? Say yes or no.",
        statusText = "Waiting for yes or no"
    )
}
