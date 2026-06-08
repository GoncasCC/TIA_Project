package com.example.tia_project.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import android.media.MediaPlayer
import com.example.tia_project.R
import com.example.tia_project.sensors.ActivityRecognitionManager
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.tia_project.WatchDataRepository

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
    val textColor = if (darkModeEnabled) Color.White else Color.Black
    val progressColor = Color(0xFF00C853)

    val isDistanceGoal = goalType == "DISTANCE"
    val targetDistanceMeters = remember(goalValue) { goalValue.extractNumber() * 1000f }
    val targetTimeSeconds = remember(goalValue) { goalValue.extractNumber() * 60 }

    var elapsedSeconds by remember { mutableStateOf(0) }
    var distanceMeters by remember { mutableStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }
    var halfwayAnnounced by remember { mutableStateOf(false) }
    var levelEndAnnounced by remember { mutableStateOf(false) }
    var stopWarningSent by remember { mutableStateOf(false) }
    var lastDistanceForStopCheck by remember { mutableStateOf(0f) }
    var detectedActivity by remember { mutableStateOf("Detecting...") }
    val watchProgress by WatchDataRepository.progress.collectAsState()
    val watchCommand by WatchDataRepository.command.collectAsState()

    val isJustVibing = difficulty == "JUST VIBING"
    val isStartingToSweat = difficulty == "STARTING TO SWEAT"
    val isPushingLimits = difficulty == "PUSHING LIMITS"

    val currentSpeedKmh = if (elapsedSeconds > 0) {
        (distanceMeters / 1000f) / (elapsedSeconds / 3600f)
    } else {
        0f
    }

    var lastMotivationSecond by remember { mutableStateOf(0) }

    val personalBest = remember { getPersonalBestSession(context) }
    var personalBestAnnounced by remember { mutableStateOf(false) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    var isStoppedByInactivity by remember { mutableStateOf(false) }
    var isStarting by remember { mutableStateOf(true) }
    val shouldStopSessionProgress = isPaused || isStoppedByInactivity || isStarting

    fun speak(text: String, id: String) {
        if (isTtsReady && voiceoverEnabled) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }
    }

    val activityRecognitionManager = remember {
        ActivityRecognitionManager(
            context = context,
            onActivityChanged = { label -> detectedActivity = label },
            onError = { message -> detectedActivity = message }
        )
    }

    DisposableEffect(Unit) {
        val hasActivityPermission =
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACTIVITY_RECOGNITION
                    ) == PackageManager.PERMISSION_GRANTED

        if (hasActivityPermission) {
            activityRecognitionManager.startListening()
        }

        onDispose {
            activityRecognitionManager.stopListening()
        }
    }
    LaunchedEffect(Unit) {
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                android.util.Log.d("WearDebug", "Nós encontrados: ${nodes.size}")
                nodes.forEach {
                    android.util.Log.d(
                        "WearDebug",
                        "  → ${it.displayName} nearby=${it.isNearby}"
                    )
                }
            }
        sendMessageToWatch(context, "/session_start")


        delay(2500)
        isStarting = false
    }

    LaunchedEffect(elapsedSeconds, distanceMeters, difficulty) {
        if (!isPushingLimits) return@LaunchedEffect
        if (personalBest == null) return@LaunchedEffect
        if (elapsedSeconds <= 0) return@LaunchedEffect

        if (elapsedSeconds - lastMotivationSecond >= 10) {
            lastMotivationSecond = elapsedSeconds

            val bestDistanceMeters = personalBest.distanceKm * 1000f
            val bestTimeSeconds = personalBest.timeSeconds

            val remainingDistanceMeters = bestDistanceMeters - distanceMeters
            val remainingTimeSeconds = bestTimeSeconds - elapsedSeconds

            when {
                distanceMeters >= bestDistanceMeters -> {
                    speak(
                        "You are beating your personal best. Keep going.",
                        "pb_beating_$elapsedSeconds"
                    )
                }

                remainingTimeSeconds <= 0 -> {
                    speak(
                        "Keep going. Focus on finishing strong.",
                        "pb_not_possible_$elapsedSeconds"
                    )
                }

                currentSpeedKmh <= 0f -> {
                    speak(
                        "Start moving to chase your personal best.",
                        "pb_start_moving_$elapsedSeconds"
                    )
                }

                else -> {
                    val requiredSpeedKmh =
                        (remainingDistanceMeters / 1000f) / (remainingTimeSeconds / 3600f)

                    if (currentSpeedKmh >= requiredSpeedKmh) {
                        speak(
                            "You can still beat your personal best. Keep this pace.",
                            "pb_keep_pace_$elapsedSeconds"
                        )
                    } else {
                        speak(
                            "You can still beat your personal best if you speed up a little.",
                            "pb_speed_up_$elapsedSeconds"
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(watchProgress) {

        if (isDistanceGoal) {
            distanceMeters = watchProgress.progress * targetDistanceMeters
        } else {
            elapsedSeconds = (watchProgress.progress * targetTimeSeconds)
                .toInt()
                .coerceIn(0, targetTimeSeconds)
        }
    }

    var lastHandledCommandTimestamp by remember { mutableStateOf(0L) }

    fun finishSession(endedEarly: Boolean) {
        saveTrainingSession(
            context = context,
            activity = if (detectedActivity.isNotBlank() && detectedActivity != "A detetar...") detectedActivity else activity,
            goalType = goalType,
            goalValue = goalValue,
            difficulty = difficulty,
            distanceMeters = distanceMeters,
            elapsedSeconds = elapsedSeconds,
            endedEarly = endedEarly
        )
    }

    LaunchedEffect(watchCommand) {
        if (watchCommand.timestamp <= lastHandledCommandTimestamp) return@LaunchedEffect
        lastHandledCommandTimestamp = watchCommand.timestamp


        val cmd = watchCommand.command.replace("\"", "").trim()

        when (cmd) {
            "pause" -> {
                isPaused = true
                try {
                    mediaPlayer?.pause()
                } catch (e: Exception) {
                }
            }

            "resume" -> {
                isPaused = false
                try {
                    mediaPlayer?.start()
                } catch (e: Exception) {
                }
            }

            "end_session" -> {

                try {
                    mediaPlayer?.pause()
                } catch (e: Exception) {
                }
                finishSession(endedEarly = true)
                speak("Workout ended.", "watch_end")
                delay(1200)
                onFinish(distanceMeters / 1000f, elapsedSeconds)
            }
        }
    }

    LaunchedEffect(distanceMeters, difficulty) {
        if (!isPushingLimits) return@LaunchedEffect

        val bestDistanceMeters = (personalBest?.distanceKm ?: 0f) * 1000f

        if (!personalBestAnnounced && bestDistanceMeters > 0f && distanceMeters > bestDistanceMeters) {
            personalBestAnnounced = true

            speak(
                "You have a new personal best. ${
                    String.format(Locale.US, "%.2f", distanceMeters / 1000f)
                } kilometers.",
                "new_personal_best"
            )
        }
    }

    DisposableEffect(Unit) {
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) isTtsReady = true
        }
        tts = textToSpeech
        textToSpeech.language = Locale.US
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    val totalProgress = if (isDistanceGoal) {
        (distanceMeters / targetDistanceMeters).coerceIn(0f, 1f)
    } else {
        (elapsedSeconds.toFloat() / targetTimeSeconds).coerceIn(0f, 1f)
    }

    val levelNumber = if (isDistanceGoal) {
        (distanceMeters / 1000f).toInt() + 1
    } else {
        (elapsedSeconds / 60) + 1
    }

    val musicLevel = when {
        goalType == "DISTANCE" && goalValue == "1 KILOMETER" -> 5
        goalType == "TIME" && goalValue == "1 MINUTE" -> 5
        goalType == "DISTANCE" && goalValue == "5 KILOMETERS" ->
            ((distanceMeters / 1000f).toInt() + 1).coerceIn(1, 5)

        goalType == "TIME" && goalValue == "5 MINUTES" ->
            ((elapsedSeconds / 60) + 1).coerceIn(1, 5)

        else -> 5
    }

    val audioResId = when {
        !musicEnabled -> R.raw.footsteps

        isJustVibing -> R.raw.relaxing_music

        isStartingToSweat -> when (musicLevel) {
            1 -> R.raw.song1
            2 -> R.raw.song2
            3 -> R.raw.song3
            4 -> R.raw.song4
            5 -> R.raw.song5
            else -> R.raw.song1
        }

        isPushingLimits -> when (musicLevel) {
            1 -> R.raw.song1
            2 -> R.raw.song2
            3 -> R.raw.song3
            4 -> R.raw.song4
            5 -> R.raw.song5
            else -> R.raw.song1
        }

        else -> R.raw.footsteps
    }

    val levelProgress = when {
        goalType == "DISTANCE" && goalValue == "1 KILOMETER" ->
            ((distanceMeters % 100f) / 100f).coerceIn(0f, 1f)

        goalType == "DISTANCE" && goalValue == "5 KILOMETERS" ->
            ((distanceMeters % 1000f) / 1000f).coerceIn(0f, 1f)

        goalType == "TIME" && goalValue == "1 MINUTE" ->
            ((elapsedSeconds % 60) / 60f).coerceIn(0f, 1f)

        goalType == "TIME" && goalValue == "5 MINUTES" ->
            ((elapsedSeconds % 60) / 60f).coerceIn(0f, 1f)

        else -> 0f
    }

    LaunchedEffect(audioResId) {
        mediaPlayer?.stop()
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer.create(context, audioResId).apply {
            isLooping = true

            if (!shouldStopSessionProgress) {
                start()
            }
        }
    }

    LaunchedEffect(shouldStopSessionProgress) {
        try {
            mediaPlayer?.let { player ->
                if (shouldStopSessionProgress && player.isPlaying) {
                    player.pause()
                } else if (!shouldStopSessionProgress && !player.isPlaying) {
                    player.start()
                }
            }
        } catch (e: Exception) {

        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (e: Exception) {

            }
            mediaPlayer = null

            try {
                activityRecognitionManager.stopListening()
            } catch (e: Exception) {
            }
        }
    }
    LaunchedEffect(totalProgress, levelNumber, isPaused, difficulty) {
        sendMessageToWatch(
            context, "/session_progress", mapOf(
                "progress" to totalProgress,
                "level" to levelNumber,
                "paused" to isPaused,
                "isStopped" to isStoppedByInactivity,
                "difficulty" to difficulty,
                "goalType" to goalType,
                "targetSteps" to estimateTargetSteps(targetDistanceMeters),
                "vibrationEnabled" to vibrationEnabled
            )
        )
    }

    LaunchedEffect(shouldStopSessionProgress, totalProgress, isDistanceGoal) {
        while (!shouldStopSessionProgress && totalProgress < 1f) {
            delay(1000)

            if (isDistanceGoal) {
                elapsedSeconds += 1
            }
        }
    }

    LaunchedEffect(isPaused, distanceMeters, difficulty) {
        if (isJustVibing) return@LaunchedEffect

        while (!isPaused && totalProgress < 1f) {
            delay(8000)

            val hasMoved = distanceMeters > lastDistanceForStopCheck + 1f
            val isStill = detectedActivity == "Parado"

            if ((!hasMoved || isStill) && !stopWarningSent) {
                stopWarningSent = true
                isStoppedByInactivity = true
                speak("You stopped. Let's keep going.", "stopped_keep_going")
                sendWatchVibrationEvent(context, "stop_warning")
            }

            if (hasMoved) {
                stopWarningSent = false
                isStoppedByInactivity = false
            }

            lastDistanceForStopCheck = distanceMeters
        }
    }

    LaunchedEffect(levelProgress, isTtsReady, difficulty) {
        if (isJustVibing) return@LaunchedEffect

        if (!halfwayAnnounced && levelProgress >= 0.5f) {
            halfwayAnnounced = true
            sendWatchVibrationEvent(context, "halfway")
            speak("You are halfway through level $levelNumber.", "halfway_level_$levelNumber")
        }

        if (!levelEndAnnounced && levelProgress >= 0.98f) {
            levelEndAnnounced = true
            sendWatchVibrationEvent(context, "level_complete")
            speak("End of level $levelNumber.", "end_level_$levelNumber")
        }
    }

    LaunchedEffect(levelNumber) {
        halfwayAnnounced = false
        levelEndAnnounced = false
    }

    LaunchedEffect(totalProgress) {
        if (isStarting) return@LaunchedEffect
        if (totalProgress >= 1f) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            finishSession(endedEarly = false)
            sendWatchVibrationEvent(context, "session_complete")
            speak("Workout complete.", "workout_complete")
            delay(1200)
            onFinish(distanceMeters / 1000f, elapsedSeconds)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
    }
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

    prefs.edit()
        .putStringSet("sessions", oldSessions + newSession)
        .apply()
}

private fun String.extractNumber(): Int = substringBefore(" ").toIntOrNull() ?: 1

private fun estimateTargetSteps(targetDistanceMeters: Float): Int {
    val averageStrideMeters = 0.78f
    return (targetDistanceMeters / averageStrideMeters).toInt().coerceAtLeast(1)
}

private fun Int.toTimerText(): String {
    val minutes = this / 60
    val seconds = this % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun getPersonalBestSession(context: Context): SavedSession? {
    val prefs = context.getSharedPreferences("training_sessions", Context.MODE_PRIVATE)
    val rawSessions = prefs.getStringSet("sessions", emptySet()) ?: emptySet()

    return rawSessions.mapNotNull { raw ->
        val parts = raw.split("|")

        if (parts.size == 3) {
            SavedSession(
                date = parts[0],
                distanceKm = parts[1].toFloatOrNull() ?: 0f,
                timeSeconds = parts[2].toIntOrNull() ?: 0
            )
        } else {
            null
        }
    }.maxByOrNull { it.distanceKm }
}

private fun sendWatchVibrationEvent(context: Context, vibrationType: String) {
    sendMessageToWatch(context, "/watch_vibration", mapOf("type" to vibrationType))
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

    Wearable.getNodeClient(context).connectedNodes
        .addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                Wearable.getMessageClient(context).sendMessage(node.id, path, payload)
                    .addOnSuccessListener {
                        android.util.Log.d(
                            "WearDebug",
                            "✓ $path enviado para ${node.displayName}"
                        )
                    }
                    .addOnFailureListener {
                        android.util.Log.e(
                            "WearDebug",
                            "✗ $path falhou: ${it.message}"
                        )
                    }
            }
        }
}