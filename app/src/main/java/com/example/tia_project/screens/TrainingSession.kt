package com.example.tia_project.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import android.media.MediaPlayer
import com.example.tia_project.R
import com.example.tia_project.WatchDataRepository
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TrainingSession(
    goalType: String,
    goalValue: String,
    difficulty: String,
    voiceoverEnabled: Boolean,
    vibrationEnabled: Boolean,
    darkModeEnabled: Boolean,
    musicEnabled: Boolean,
    onFinish: (Float, Int, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val backgroundColor = Color.Black

    val targetDistanceMeters = remember(goalValue) { goalValue.toGoalDistanceMeters().toFloat() }
    val isOneMinuteMode = goalType == "TIME" && goalValue.extractNumber() == 1
    val personalBest = remember(goalType, goalValue) {
        getPersonalBestForMode(context, goalType, goalValue)
    }
    val introData = remember(goalType, goalValue, personalBest) {
        buildSessionIntro(goalType, goalValue, personalBest)
    }

    var isPaused by remember { mutableStateOf(false) }
    var isStarting by remember { mutableStateOf(true) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var introTitle by remember { mutableStateOf(introData?.title ?: "") }
    var introValue by remember { mutableStateOf(introData?.value ?: "") }

    var musicLevel by remember { mutableStateOf(1) }

    val watchCommand by WatchDataRepository.command.collectAsState()
    val watchResult by WatchDataRepository.result.collectAsState()

    val isJustVibing = difficulty == "JUST VIBING"
    val isPushingLimits = difficulty == "PUSHING LIMITS"

    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val textToSpeech = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) isTtsReady = true
        }
        tts = textToSpeech
        textToSpeech.language = java.util.Locale.US
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        WatchDataRepository.clearSessionState()

        sendMessageToWatch(
            context, "/session_start", mapOf(
                "goalType"                to goalType,
                "goalValue"               to goalValue,
                "difficulty"              to difficulty,
                "targetSteps"             to estimateTargetSteps(targetDistanceMeters),
                "vibrationEnabled"        to vibrationEnabled,
                "voiceoverEnabled"        to voiceoverEnabled,
                "personalBestDistanceKm"  to (personalBest?.distanceKm ?: 0f),
                "personalBestTimeSeconds" to (personalBest?.timeSeconds ?: 0),
                "introTitle"              to introTitle,
                "introValue"              to introValue
            )
        )

        if (isPushingLimits && voiceoverEnabled && introData != null) {
            var waited = 0
            while (!isTtsReady && waited < 3000) {
                delay(100)
                waited += 100
            }
            tts?.speak(introData.speech, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "pb_announce")
            while (tts?.isSpeaking == true) {
                delay(100)
            }
            delay(200)
            tts?.speak("Ok, let's start moving.", android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "session_go")
            while (tts?.isSpeaking == true) {
                delay(100)
            }
        } else {
            delay(1200)
        }

        sendMessageToWatch(context, "/session_go")
        introTitle = ""
        introValue = ""
        isStarting = false
    }

    var lastHandledCommandTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(watchCommand) {
        if (watchCommand.timestamp <= lastHandledCommandTimestamp) return@LaunchedEffect
        lastHandledCommandTimestamp = watchCommand.timestamp

        val cmd = watchCommand.command.replace("\"", "").trim()
        when (cmd) {
            "pause" -> {
                isPaused = true
            }
            "resume" -> {
                isPaused = false
            }
        }
    }

    var lastHandledResultTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(watchResult) {
        if (watchResult.timestamp <= lastHandledResultTimestamp) return@LaunchedEffect
        if (isStarting) return@LaunchedEffect
        lastHandledResultTimestamp = watchResult.timestamp

        sendMessageToWatch(context, "/session_end")

        saveTrainingSession(
            context       = context,
            goalType      = goalType,
            goalValue     = goalValue,
            difficulty    = difficulty,
            distanceMeters = watchResult.distanceMeters,
            elapsedSeconds = watchResult.elapsedSeconds,
            endedEarly    = watchResult.endedEarly
        )

        delay(1200)
        onFinish(
            watchResult.distanceMeters,
            watchResult.elapsedSeconds,
            watchResult.isNewPersonalBest
        )
    }

    val watchLevel by WatchDataRepository.level.collectAsState()
    val watchProgress by WatchDataRepository.progress.collectAsState()

    LaunchedEffect(watchLevel, watchProgress.progress) {
        if (watchLevel > 0) musicLevel = watchLevel
    }

    val audioResId = when {
        !musicEnabled -> R.raw.footsteps
        isJustVibing  -> R.raw.relaxing_music
        isOneMinuteMode -> R.raw.song5
        else -> when (musicLevel) {
            1    -> R.raw.song1
            2    -> R.raw.song2
            3    -> R.raw.song3
            4    -> R.raw.song4
            else -> R.raw.song5
        }
    }

    val shouldPlay = !isStarting && !isPaused

    DisposableEffect(audioResId) {
        val player = MediaPlayer.create(context, audioResId).apply {
            isLooping = true
        }
        mediaPlayer = player

        if (shouldPlay) {
            try { player.start() } catch (e: Exception) {}
        }

        onDispose {
            try {
                player.stop()
                player.release()
            } catch (e: Exception) {}
            mediaPlayer = null
        }
    }

    LaunchedEffect(shouldPlay) {
        try {
            mediaPlayer?.let { player ->
                if (shouldPlay && !player.isPlaying) {
                    player.start()
                } else if (!shouldPlay && player.isPlaying) {
                    player.pause()
                }
            }
        } catch (e: Exception) {}
    }
    DisposableEffect(Unit) {
        onDispose {
            WatchDataRepository.clearSessionState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (isStarting && introTitle.isNotBlank() && introValue.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = introTitle,
                    color = Color(0xFFFFCC00),
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = introValue,
                    color = Color.White,
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        }
    }
}

private fun modeKey(goalType: String, goalValue: String): String {
    return if (goalType == "DISTANCE") {
        if (goalValue.toGoalDistanceMeters() == 1000) "1 KM" else "${goalValue.toGoalDistanceMeters()} M"
    } else {
        "${goalValue.extractNumber()} MIN"
    }
}

private fun saveTrainingSession(
    context: Context,
    goalType: String,
    goalValue: String,
    difficulty: String,
    distanceMeters: Float,
    elapsedSeconds: Int,
    endedEarly: Boolean
) {
    val prefs = context.getSharedPreferences("training_sessions", Context.MODE_PRIVATE)
    val oldSessions = prefs.getStringSet("sessions", emptySet()) ?: emptySet()
    val mode = modeKey(goalType, goalValue)
    val newSession = "${System.currentTimeMillis()}|${distanceMeters / 1000f}|$elapsedSeconds|$mode"
    prefs.edit().putStringSet("sessions", oldSessions + newSession).apply()
}


fun getPersonalBestForMode(context: Context, goalType: String, goalValue: String): SavedSession? {
    val mode = modeKey(goalType, goalValue)
    val prefs = context.getSharedPreferences("training_sessions", Context.MODE_PRIVATE)
    val rawSessions = prefs.getStringSet("sessions", emptySet()) ?: emptySet()
    val modeSessions = rawSessions.mapNotNull { raw ->
        val parts = raw.split("|")
        when {
            parts.size == 4 && parts[3] == mode -> SavedSession(
                date        = parts[0],
                distanceKm  = parts[1].toFloatOrNull() ?: 0f,
                timeSeconds = parts[2].toIntOrNull() ?: 0,
                mode        = parts[3]
            )
            else -> null
        }
    }
    return if (goalType == "DISTANCE") {
        modeSessions.filter { it.timeSeconds > 0 }.minByOrNull { it.timeSeconds }
    } else {
        modeSessions.maxByOrNull { it.distanceKm }
    }
}

private fun String.extractNumber(): Int = substringBefore(" ").toIntOrNull() ?: 1

private fun String.toGoalDistanceMeters(): Int {
    return when {
        contains("KILOMETER", ignoreCase = true) -> extractNumber() * 1000
        contains("METER", ignoreCase = true) -> extractNumber()
        else -> extractNumber() * 1000
    }
}

private fun estimateTargetSteps(targetDistanceMeters: Float): Int {
    val averageStrideMeters = 0.7f
    return (targetDistanceMeters / averageStrideMeters).toInt().coerceAtLeast(1)
}

private fun Int.toTimerText(): String {
    val minutes = this / 60
    val seconds = this % 60
    return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
}

private fun sendMessageToWatch(
    context: Context,
    path: String,
    data: Map<String, Any> = emptyMap()
) {
    val jsonData = data.entries.joinToString(",", "{", "}") { (k, v) ->
        "\"$k\":${if (v is String) "\"$v\"" else v}"
    }
    val payload = jsonData.toByteArray(Charsets.UTF_8)
    com.google.android.gms.wearable.Wearable.getNodeClient(context).connectedNodes
        .addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                com.google.android.gms.wearable.Wearable.getMessageClient(context)
                    .sendMessage(node.id, path, payload)
                    .addOnSuccessListener { android.util.Log.d("WearDebug", "✓ $path enviado") }
                    .addOnFailureListener { android.util.Log.e("WearDebug", "✗ $path falhou: ${it.message}") }
            }
        }
}

private data class SessionIntro(
    val title: String,
    val value: String,
    val speech: String
)

private fun buildSessionIntro(
    goalType: String,
    goalValue: String,
    personalBest: SavedSession?
): SessionIntro? {
    if (personalBest == null) return null

    return if (goalType == "DISTANCE") {
        val minutes = personalBest.timeSeconds / 60
        val seconds = personalBest.timeSeconds % 60
        SessionIntro(
            title = "1000 M BEST",
            value = String.format(Locale.US, "%02d:%02d", minutes, seconds),
            speech = "Your personal best for 1000 meters is $minutes minutes and $seconds seconds. Let's try to beat it!"
        )
    } else {
        val meters = (personalBest.distanceKm * 1000f).roundToInt()
        val modeLabel = if (goalValue.extractNumber() == 1) "1 minute" else "${goalValue.extractNumber()} minutes"
        SessionIntro(
            title = "${goalValue.extractNumber()} MIN BEST",
            value = "$meters M",
            speech = "Your personal best for $modeLabel is $meters meters. Let's try to beat it!"
        )
    }
}
