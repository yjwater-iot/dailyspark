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

    DisposableEffect(Unit) {
        onDispose { voiceManager.shutdown() }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        DailySparkScreen(
            transcript = transcript,
            status = status,
            onTalk = {
                if (hasAudioPermission) {
                    voiceManager.startListening()
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onStoryBuilding = {
                val story = buildGeneratedStory(transcript)
                status = "Playing generated story…"
                voiceManager.speakStory(story)
            },
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun DailySparkScreen(
    transcript: String,
    status: String,
    onTalk: () -> Unit,
    onStoryBuilding: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
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
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onStoryBuilding,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Text(text = "Story Building", fontSize = 24.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = status, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = transcript,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun buildGeneratedStory(transcript: String): String {
    val detail = transcript.trim()
    return if (detail.isBlank() || detail == "Tap Talk and share what you noticed today.") {
        "Your DailySpark story is ready when you share what you noticed today. Tap Talk, tell me a small moment, then tap Story Building to listen."
    } else {
        "Here is your DailySpark story. Today, you noticed $detail. That small spark became a bright reminder to pause, wonder, and carry a little more attention into the rest of your day."
    }
}
