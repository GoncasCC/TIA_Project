package com.example.tia_project.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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

@Composable
fun TrainingSession(
    activity: String,
    goalType: String,
    goalValue: String,
    difficulty: String,
    voiceoverEnabled: Boolean,
    vibrationEnabled: Boolean,
    darkModeEnabled: Boolean,
    onFinish: () -> Unit,
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

    val vibrator = remember(context) { context.getVibratorCompat() }

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

    fun vibratePhoneAfterSession() {
        if (!vibrationEnabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(180)
        }
    }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    fun speak(text: String, id: String) {
        if (isTtsReady && voiceoverEnabled) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }
    }

    LaunchedEffect(elapsedSeconds, difficulty) {
        if (!isPushingLimits) return@LaunchedEffect

        if (elapsedSeconds > 0 && elapsedSeconds - lastMotivationSecond >= 60) {
            lastMotivationSecond = elapsedSeconds

            if (currentSpeedKmh > 0f) {
                speak(
                    "Keep pushing. Your current speed is ${
                        String.format(Locale.US, "%.1f", currentSpeedKmh)
                    } kilometers per hour.",
                    "pushing_speed_$elapsedSeconds"
                )
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

    val levelProgress = if (isDistanceGoal) {
        ((distanceMeters % 1000f) / 1000f).coerceIn(0f, 1f)
    } else {
        ((elapsedSeconds % 60) / 60f).coerceIn(0f, 1f)
    }

    LaunchedEffect(totalProgress, levelNumber, isPaused, difficulty) {
        val request = PutDataMapRequest.create("/session_progress").apply {
            dataMap.putFloat("progress", totalProgress)
            dataMap.putInt("level", levelNumber)
            dataMap.putBoolean("paused", isPaused)
            dataMap.putString("difficulty", difficulty)
            dataMap.putLong("timestamp", System.currentTimeMillis())
            dataMap.putBoolean("vibrationEnabled", vibrationEnabled)
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request)
    }

    LaunchedEffect(isPaused, totalProgress) {
        while (!isPaused && totalProgress < 1f) {
            delay(1000)
            elapsedSeconds += 1

            // IMPORTANT: distanceMeters does not increase here anymore.
            // Later, connect this value to GPS, step counter, or smartwatch data.
        }
    }

    LaunchedEffect(isPaused, distanceMeters, difficulty) {
        if (isJustVibing) return@LaunchedEffect

        while (!isPaused && totalProgress < 1f) {
            delay(8000)

            val hasMoved = distanceMeters > lastDistanceForStopCheck + 1f

            if (!hasMoved && !stopWarningSent) {
                stopWarningSent = true
                speak("You stopped. Keep going.", "stopped_keep_going")
            }

            if (hasMoved) stopWarningSent = false

            lastDistanceForStopCheck = distanceMeters
        }
    }

    LaunchedEffect(levelProgress, isTtsReady, difficulty) {
        if (isJustVibing) return@LaunchedEffect

        if (!halfwayAnnounced && levelProgress >= 0.5f) {
            halfwayAnnounced = true
            speak("You are halfway through level $levelNumber.", "halfway_level_$levelNumber")
        }

        if (!levelEndAnnounced && levelProgress >= 0.98f) {
            levelEndAnnounced = true
            speak("End of level $levelNumber.", "end_level_$levelNumber")
        }
    }

    LaunchedEffect(levelNumber) {
        halfwayAnnounced = false
        levelEndAnnounced = false
    }

    fun finishSession(endedEarly: Boolean) {
        saveTrainingSession(
            context = context,
            activity = activity,
            goalType = goalType,
            goalValue = goalValue,
            difficulty = difficulty,
            distanceMeters = distanceMeters,
            elapsedSeconds = elapsedSeconds,
            endedEarly = endedEarly
        )
    }

    var lastWatchCommandTimestamp by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)

            val prefs = context.getSharedPreferences("watch_commands", Context.MODE_PRIVATE)
            val command = prefs.getString("command", null)
            val timestamp = prefs.getLong("timestamp", 0L)

            if (timestamp > lastWatchCommandTimestamp) {
                lastWatchCommandTimestamp = timestamp

                when (command) {
                    "pause" -> {
                        isPaused = true
                        speak("Workout paused.", "watch_pause")
                        // TODO: pause music
                    }

                    "resume" -> {
                        isPaused = false
                        speak("Workout resumed.", "watch_resume")
                        // TODO: resume music
                    }

                    "end_session" -> {
                        finishSession(endedEarly = true)
                        speak("Workout ended.", "watch_end")
                        delay(1200)
                        onCancel()
                    }
                }
            }
        }
    }

    LaunchedEffect(totalProgress) {
        if (totalProgress >= 1f) {
            finishSession(endedEarly = false)
            vibratePhoneAfterSession()
            speak("Workout complete.", "workout_complete")
            delay(1200)
            onFinish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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

private fun Context.getVibratorCompat(): Vibrator {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
}

private fun String.extractNumber(): Int = substringBefore(" ").toIntOrNull() ?: 1

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

