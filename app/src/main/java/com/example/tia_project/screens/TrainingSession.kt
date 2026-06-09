package com.example.tia_project.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import android.media.MediaPlayer
import com.example.tia_project.R
import com.example.tia_project.WatchDataRepository
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun TrainingSession(
    activity: String,
    goalType: String,
    goalValue: String,
    difficulty: String,
    voiceoverEnabled: Boolean,
    vibrationEnabled: Boolean,
    darkModeEnabled: Boolean,
    musicEnabled: Boolean,
    onFinish: (Float, Int) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val backgroundColor = if (darkModeEnabled) Color.Black else Color.White

    val isDistanceGoal = goalType == "DISTANCE"
    val targetDistanceMeters = remember(goalValue) { goalValue.extractNumber() * 1000f }
    val targetTimeSeconds = remember(goalValue) { goalValue.extractNumber() * 60 }

    var isPaused by remember { mutableStateOf(false) }
    var isStarting by remember { mutableStateOf(true) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    var musicLevel by remember { mutableStateOf(1) }

    val watchCommand by WatchDataRepository.command.collectAsState()
    val watchResult by WatchDataRepository.result.collectAsState()

    val isJustVibing = difficulty == "JUST VIBING"
    val isStartingToSweat = difficulty == "STARTING TO SWEAT"
    val isPushingLimits = difficulty == "PUSHING LIMITS"

    val shouldStopSessionProgress = isPaused || isStarting


    LaunchedEffect(Unit) {
        WatchDataRepository.clearResult()

        val personalBest = getPersonalBestSession(context)
        sendMessageToWatch(
            context, "/session_start", mapOf(
                "goalType"                to goalType,
                "activity"  to activity,
                "goalValue"               to goalValue,
                "difficulty"              to difficulty,
                "targetSteps"             to estimateTargetSteps(targetDistanceMeters),
                "vibrationEnabled"        to vibrationEnabled,
                "voiceoverEnabled"        to voiceoverEnabled,
                "personalBestDistanceKm"  to (personalBest?.distanceKm ?: 0f),
                "personalBestTimeSeconds" to (personalBest?.timeSeconds ?: 0)
            )
        )

        delay(2500)
        isStarting = false
    }


    var lastHandledCommandTimestamp by remember { mutableStateOf(0L) }
    LaunchedEffect(watchCommand) {
        if (watchCommand.timestamp <= lastHandledCommandTimestamp) return@LaunchedEffect
        lastHandledCommandTimestamp = watchCommand.timestamp

        val cmd = watchCommand.command.replace("\"", "").trim()
        when (cmd) {
            "pause" -> {
                isPaused = true
                try { mediaPlayer?.pause() } catch (e: Exception) { }
            }
            "resume" -> {
                isPaused = false
                try { mediaPlayer?.start() } catch (e: Exception) { }
            }
        }
    }


    var lastHandledResultTimestamp by remember { mutableStateOf(0L) }
    LaunchedEffect(watchResult) {
        if (watchResult.timestamp <= lastHandledResultTimestamp) return@LaunchedEffect
        if (isStarting) return@LaunchedEffect
        lastHandledResultTimestamp = watchResult.timestamp

        try { mediaPlayer?.pause() } catch (e: Exception) { }


        sendMessageToWatch(context, "/session_end")

        saveTrainingSession(
            context       = context,
            activity      = activity,
            goalType      = goalType,
            goalValue     = goalValue,
            difficulty    = difficulty,
            distanceMeters = watchResult.distanceMeters,
            elapsedSeconds = watchResult.elapsedSeconds,
            endedEarly    = watchResult.endedEarly
        )

        delay(1200)
        onFinish(watchResult.distanceMeters / 1000f, watchResult.elapsedSeconds)
    }


    val watchLevel by WatchDataRepository.level.collectAsState()
    val watchProgress by WatchDataRepository.progress.collectAsState()
    val totalLevels = remember(goalValue) { goalValue.extractNumber().coerceAtLeast(1) }

    LaunchedEffect(watchLevel, watchProgress.progress) {
        val level = if (watchLevel > 0) {

            watchLevel.coerceIn(1, totalLevels)
        } else if (isDistanceGoal) {
            1
        } else {

            val p = watchProgress.progress.coerceIn(0f, 1f)
            ((p * totalLevels).toInt() + 1).coerceIn(1, totalLevels)
        }
        musicLevel = level
    }

    val audioResId = when {
        !musicEnabled -> R.raw.footsteps
        isJustVibing  -> R.raw.relaxing_music
        else -> when (musicLevel) {
            1    -> R.raw.song1
            2    -> R.raw.song2
            3    -> R.raw.song3
            4    -> R.raw.song4
            else -> R.raw.song5
        }
    }

    LaunchedEffect(audioResId) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(context, audioResId).apply {
            isLooping = true
            if (!shouldStopSessionProgress) start()
        }
    }

    LaunchedEffect(shouldStopSessionProgress) {
        try {
            val player = mediaPlayer ?: return@LaunchedEffect
            if (shouldStopSessionProgress) {
                if (player.isPlaying) player.pause()
            } else {
                if (!player.isPlaying) player.start()
            }
        } catch (e: Exception) { }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (e: Exception) { }
            mediaPlayer = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) { }
}

private fun saveTrainingSession(
    context: Context,
    activity: String,
    goalType: String,
    goalValue: String,
    difficulty: String,
    distanceMeters: Float,
    elapsedSeconds: Int,
    endedEarly: Boolean
) {
    val prefs = context.getSharedPreferences("training_sessions", Context.MODE_PRIVATE)
    val oldSessions = prefs.getStringSet("sessions", emptySet()) ?: emptySet()
    val newSession = "${System.currentTimeMillis()}|${distanceMeters / 1000f}|$elapsedSeconds"
    prefs.edit().putStringSet("sessions", oldSessions + newSession).apply()
}

private fun getPersonalBestSession(context: Context): SavedSession? {
    val prefs = context.getSharedPreferences("training_sessions", Context.MODE_PRIVATE)
    val rawSessions = prefs.getStringSet("sessions", emptySet()) ?: emptySet()
    return rawSessions.mapNotNull { raw ->
        val parts = raw.split("|")
        if (parts.size == 3) SavedSession(
            date          = parts[0],
            distanceKm    = parts[1].toFloatOrNull() ?: 0f,
            timeSeconds   = parts[2].toIntOrNull() ?: 0
        ) else null
    }.maxByOrNull { it.distanceKm }
}

private fun String.extractNumber(): Int = substringBefore(" ").toIntOrNull() ?: 1

private fun estimateTargetSteps(targetDistanceMeters: Float): Int {
    val averageStrideMeters = 0.78f
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