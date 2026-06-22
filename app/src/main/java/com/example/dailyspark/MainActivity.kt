package com.example.dailyspark

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
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
    var transcript by remember { mutableStateOf("Tap Talk and share what you noticed today.") }
    var cleanedObservation by remember { mutableStateOf("") }
    var followUpQuestion by remember { mutableStateOf("") }
    var storySeed by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready") }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val voiceManager = remember {
        VoiceManager(
            context = context,
            onTranscript = { spokenText ->
                transcript = spokenText.ifBlank { "No speech captured." }
                scope.launch { repository.saveTranscript(spokenText) }
            },
            onListeningChanged = { isListening -> status = if (isListening) "Listening…" else "Ready" },
            onError = { message -> status = message }
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            voiceManager.startListening()
        } else {
            status = "Microphone permission is needed to use Talk."
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
                cleanedObservation = latestStory.cleanedObservation
                followUpQuestion = latestStory.followUpQuestion
                storySeed = latestStory.storySeed
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { voiceManager.shutdown() }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        DailySparkScreen(
            transcript = transcript,
            cleanedObservation = cleanedObservation,
            followUpQuestion = followUpQuestion,
            storySeed = storySeed,
            status = status,
            onTalk = {
                if (hasAudioPermission) {
                    voiceManager.startListening()
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun DailySparkScreen(
    transcript: String,
    cleanedObservation: String,
    followUpQuestion: String,
    storySeed: String,
    status: String,
    onTalk: () -> Unit,
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
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onTalk,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        ) {
            Text(text = "Talk", fontSize = 32.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = status, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(16.dp))
        LabeledText(label = "Raw transcript", text = transcript)
        if (cleanedObservation.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            LabeledText(label = "Cleaned observation", text = cleanedObservation)
            Spacer(modifier = Modifier.height(16.dp))
            LabeledText(label = "Follow-up question", text = followUpQuestion)
            Spacer(modifier = Modifier.height(16.dp))
            LabeledText(label = "Story seed", text = storySeed)
        }
    }
}

@Composable
private fun LabeledText(
    label: String,
    text: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
