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
    var askingToEnd by remember { mutableStateOf(false) }
    var endOptionIsYes by remember { mutableStateOf(false) }
    var halfwayAnnounced by remember { mutableStateOf(false) }
    var levelEndAnnounced by remember { mutableStateOf(false) }
    var stopWarningSent by remember { mutableStateOf(false) }
    var lastDistanceForStopCheck by remember { mutableStateOf(0f) }

    val vibrator = remember(context) { context.getVibratorCompat() }

    fun vibrateNormal() {
        if (!vibrationEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(180)
        }
    }

    fun vibrateStopWarning() {
        if (!vibrationEnabled) return
        val pattern = longArrayOf(0, 120, 80, 120, 80, 350)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1)
        }
    }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    fun speak(text: String, id: String) {
        if (isTtsReady && voiceoverEnabled) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
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

    LaunchedEffect(isPaused, askingToEnd, totalProgress) {
        while (!isPaused && !askingToEnd && totalProgress < 1f) {
            delay(1000)
            elapsedSeconds += 1

            // IMPORTANT: distanceMeters does not increase here anymore.
            // Later, connect this value to GPS, step counter, or smartwatch data.
        }
    }

    LaunchedEffect(isPaused, askingToEnd, distanceMeters) {
        while (!isPaused && !askingToEnd && totalProgress < 1f) {
            delay(8000)
            val hasMoved = distanceMeters > lastDistanceForStopCheck + 1f
            if (!hasMoved && !stopWarningSent) {
                stopWarningSent = true
                vibrateStopWarning()
                speak("You stopped. Keep going.", "stopped_keep_going")
            }
            if (hasMoved) stopWarningSent = false
            lastDistanceForStopCheck = distanceMeters
        }
    }

    LaunchedEffect(levelProgress, isTtsReady) {
        if (!halfwayAnnounced && levelProgress >= 0.5f) {
            halfwayAnnounced = true
            vibrateNormal()
            speak("You are halfway through level $levelNumber.", "halfway_level_$levelNumber")
        }
        if (!levelEndAnnounced && levelProgress >= 0.98f) {
            levelEndAnnounced = true
            vibrateNormal()
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

    LaunchedEffect(totalProgress) {
        if (totalProgress >= 1f) {
            finishSession(endedEarly = false)
            vibrateNormal()
            speak("Workout complete.", "workout_complete")
            delay(1200)
            onFinish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(isPaused, askingToEnd, endOptionIsYes) {
                coroutineScope {
                    launch {
                        detectTapGestures(
                            onDoubleTap = {
                                vibrateNormal()
                                if (askingToEnd) {
                                    if (endOptionIsYes) {
                                        finishSession(endedEarly = true)
                                        speak("Workout ended early.", "ended_early")
                                        onCancel()
                                    } else {
                                        askingToEnd = false
                                        isPaused = false
                                        speak("Workout resumed.", "resumed_from_end_question")
                                    }
                                } else {
                                    isPaused = !isPaused
                                    speak(if (isPaused) "Workout paused." else "Workout resumed.", "pause_toggle")
                                }
                            },
                            onLongPress = {
                                vibrateNormal()
                                askingToEnd = true
                                isPaused = true
                                endOptionIsYes = false
                                speak(
                                    "Do you want to end the workout early? No selected. Swipe right for yes. Double tap to confirm.",
                                    "ask_end_early"
                                )
                            }
                        )
                    }
                    launch {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (askingToEnd && dragAmount > 25f) {
                                endOptionIsYes = true
                                vibrateNormal()
                                speak("Yes selected.", "yes_selected")
                            } else if (askingToEnd && dragAmount < -25f) {
                                endOptionIsYes = false
                                vibrateNormal()
                                speak("No selected.", "no_selected")
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (askingToEnd) {
            EndSessionQuestion(textColor, progressColor, endOptionIsYes)
        } else {
            TrainingProgressContent(
                isPaused = isPaused,
                levelNumber = levelNumber,
                totalProgress = totalProgress,
                isDistanceGoal = isDistanceGoal,
                distanceMeters = distanceMeters,
                elapsedSeconds = elapsedSeconds,
                textColor = textColor,
                progressColor = progressColor
            )
        }
    }
}

@Composable
private fun EndSessionQuestion(textColor: Color, progressColor: Color, endOptionIsYes: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("END SESSION?", color = textColor, fontSize = 54.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(50.dp))
        Text(
            text = if (endOptionIsYes) "YES" else "NO",
            color = if (endOptionIsYes) Color(0xFFD50000) else progressColor,
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TrainingProgressContent(
    isPaused: Boolean,
    levelNumber: Int,
    totalProgress: Float,
    isDistanceGoal: Boolean,
    distanceMeters: Float,
    elapsedSeconds: Int,
    textColor: Color,
    progressColor: Color
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (isPaused) "PAUSED" else "LEVEL $levelNumber",
            color = textColor,
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(330.dp)) {
                drawCircle(color = Color(0xFF1B5E20), radius = size.minDimension / 2f, style = Stroke(width = 34f))
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * totalProgress,
                    useCenter = false,
                    style = Stroke(width = 34f, cap = StrokeCap.Round)
                )
            }
            Text("${(totalProgress * 100).toInt()}%", color = textColor, fontSize = 62.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            text = if (isDistanceGoal) "${String.format(Locale.US, "%.2f", distanceMeters / 1000f)} KM" else elapsedSeconds.toTimerText(),
            color = textColor,
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
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
