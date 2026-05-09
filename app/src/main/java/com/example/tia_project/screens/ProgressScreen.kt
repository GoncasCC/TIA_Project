package com.example.tia_project.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ProgressScreen(
    voiceoverEnabled: Boolean,
    vibrationEnabled: Boolean,
    darkModeEnabled: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val backgroundColor = if (darkModeEnabled) Color.Black else Color.White
    val textColor = if (darkModeEnabled) Color.White else Color.Black
    val greenColor = Color(0xFF00C853)

    val periods = listOf("TODAY", "YESTERDAY", "THIS WEEK", "THIS MONTH", "ALL TIME")

    var selectedIndex by remember { mutableStateOf(0) }
    val selectedPeriod = periods[selectedIndex]

    val sessions = remember { loadSavedSessions(context) }
    val progressData = remember(selectedPeriod, sessions) {
        calculateProgressForPeriod(selectedPeriod, sessions)
    }

    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun vibrateNormal() {
        if (!vibrationEnabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(120)
        }
    }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var isGoingBack by remember { mutableStateOf(false) }

    fun speak(text: String, id: String) {
        if (isTtsReady && voiceoverEnabled) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }
    }

    DisposableEffect(Unit) {
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
            }
        }

        tts = textToSpeech
        textToSpeech.language = Locale.US

        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    LaunchedEffect(selectedPeriod, isTtsReady, voiceoverEnabled) {
        if (!isGoingBack) {
            speak(
                progressData.toSpeechText(selectedPeriod),
                "progress_${selectedPeriod.lowercase().replace(" ", "_")}"
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(selectedPeriod, vibrationEnabled, voiceoverEnabled) {
                coroutineScope {
                    launch {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()

                            if (!isGoingBack) {
                                if (dragAmount > 25f) {
                                    selectedIndex =
                                        if (selectedIndex == 0) periods.lastIndex else selectedIndex - 1
                                    vibrateNormal()
                                } else if (dragAmount < -25f) {
                                    selectedIndex =
                                        if (selectedIndex == periods.lastIndex) 0 else selectedIndex + 1
                                    vibrateNormal()
                                }
                            }
                        }
                    }

                    launch {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressedPointers = event.changes.filter { it.pressed }

                                if (pressedPointers.size == 2 && !isGoingBack) {
                                    val horizontalMove = pressedPointers
                                        .sumOf { it.positionChange().x.toDouble() }
                                        .toFloat()

                                    if (horizontalMove > 80f || horizontalMove < -80f) {
                                        isGoingBack = true
                                        vibrateNormal()
                                        speak("Going back to menu.", "progress_back_menu")

                                        launch {
                                            while (tts?.isSpeaking == true) {
                                                delay(100)
                                            }
                                            delay(200)
                                            onBack()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedPeriod,
                color = greenColor,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format(Locale.US, "%.2f KM", progressData.totalDistanceKm),
                    color = textColor,
                    fontSize = 70.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = progressData.totalTimeSeconds.toReadableDuration(),
                    color = textColor,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "SESSIONS: ${progressData.totalSessions}",
                    color = textColor,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = "Swipe with one finger for more progress details.\nSwipe with two fingers to go back.",
                color = textColor,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

data class SavedSession(
    val date: String,
    val distanceKm: Float,
    val timeSeconds: Int
)

data class ProgressData(
    val totalDistanceKm: Float,
    val totalTimeSeconds: Int,
    val totalSessions: Int
)

private fun loadSavedSessions(context: Context): List<SavedSession> {
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
    }
}

private fun calculateProgressForPeriod(
    period: String,
    sessions: List<SavedSession>
): ProgressData {
    val now = System.currentTimeMillis()
    val oneDay = 24 * 60 * 60 * 1000L

    val filteredSessions = sessions.filter { session ->
        val sessionTime = session.date.toLongOrNull() ?: return@filter false
        val diff = now - sessionTime

        when (period) {
            "TODAY" -> diff in 0 until oneDay
            "YESTERDAY" -> diff in oneDay until (2 * oneDay)
            "THIS WEEK" -> diff in 0 until (7 * oneDay)
            "THIS MONTH" -> diff in 0 until (30 * oneDay)
            "ALL TIME" -> true
            else -> false
        }
    }

    return ProgressData(
        totalDistanceKm = filteredSessions.sumOf { it.distanceKm.toDouble() }.toFloat(),
        totalTimeSeconds = filteredSessions.sumOf { it.timeSeconds },
        totalSessions = filteredSessions.size
    )
}

private fun ProgressData.toSpeechText(period: String): String {
    val periodText = when (period) {
        "TODAY" -> "Today"
        "YESTERDAY" -> "Yesterday"
        "THIS WEEK" -> "This week"
        "THIS MONTH" -> "This month"
        "ALL TIME" -> "All time"
        else -> period.lowercase()
    }

    return "$periodText, you completed ${String.format(Locale.US, "%.2f", totalDistanceKm)} kilometers in ${totalTimeSeconds.toReadableDurationForSpeech()}. Total sessions: $totalSessions. Swipe with one finger for more progress details. Swipe with two fingers to go back to menu."
}

private fun Int.toReadableDuration(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60

    return when {
        hours > 0 -> String.format(Locale.US, "%dH %02dM", hours, minutes)
        minutes > 0 -> String.format(Locale.US, "%dM %02dS", minutes, seconds)
        else -> String.format(Locale.US, "%dS", seconds)
    }
}

private fun Int.toReadableDurationForSpeech(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60

    return when {
        hours > 0 && minutes > 0 -> "$hours hour${if (hours == 1) "" else "s"} and $minutes minute${if (minutes == 1) "" else "s"}"
        hours > 0 -> "$hours hour${if (hours == 1) "" else "s"}"
        minutes > 0 && seconds > 0 -> "$minutes minute${if (minutes == 1) "" else "s"} and $seconds second${if (seconds == 1) "" else "s"}"
        minutes > 0 -> "$minutes minute${if (minutes == 1) "" else "s"}"
        else -> "$seconds second${if (seconds == 1) "" else "s"}"
    }
}