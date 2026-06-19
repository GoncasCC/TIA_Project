package com.example.wear.presentation.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val PaceWarningColor = Color(0xFFE69F00)

@Composable
fun WatchProgressScreen(
    progress: Float = 0.45f,
    level: Int = 1,
    sessionStarted: Boolean = false,
    paused: Boolean = false,
    isStopped: Boolean = false,
    difficulty: String = "JUST VIBING",
    needsSpeedUp: Boolean = false,
    introTitle: String = "",
    introValue: String = "",
    vibrationEnabled: Boolean = true,
    onPauseToggle: () -> Unit = {},
    onResume: () -> Unit = {},
    onEndSession: () -> Unit = {},
    onSpeakRequest: (String) -> Unit = {},
    onStopSpeaking: () -> Unit = {}
) {
    var askingToEnd by remember { mutableStateOf(false) }
    var wasPausedBeforeAsking by remember { mutableStateOf(false) }

    var localPaused by remember { mutableStateOf(paused) }
    LaunchedEffect(paused) { localPaused = paused }

    val context = LocalContext.current
    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }


    val blinkFrequency = when {
        !sessionStarted                                  -> 0
        localPaused                                     -> 500
        isStopped                                       -> 200
        difficulty == "PUSHING LIMITS" && needsSpeedUp -> 600
        else                                            -> 0
    }

    var blinkVisible by remember { mutableStateOf(true) }

    LaunchedEffect(blinkFrequency) {
        if (blinkFrequency == 0) {
            blinkVisible = true
        } else {
            while (true) {
                blinkVisible = true
                kotlinx.coroutines.delay(blinkFrequency.toLong())
                blinkVisible = false
                kotlinx.coroutines.delay(blinkFrequency.toLong())
            }
        }
    }

    val arcColor = when {
        !sessionStarted                                  -> Color.White
        localPaused                                     -> Color.White
        isStopped                                       -> Color(0xFF9C27B0)
        difficulty == "PUSHING LIMITS" && needsSpeedUp -> PaceWarningColor
        else                                            -> Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(sessionStarted, askingToEnd, vibrationEnabled, isStopped) {
                if (!sessionStarted) return@pointerInput
                coroutineScope {
                    if (askingToEnd) {
                        launch (start = CoroutineStart.UNDISPATCHED) {
                            detectTapGestures(
                                onTap = {
                                    onStopSpeaking()
                                    askingToEnd = false
                                    if (!wasPausedBeforeAsking) {
                                        localPaused = false
                                        onResume()
                                    }
                                },
                                onDoubleTap = {
                                    onStopSpeaking()
                                    if (vibrationEnabled) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                                        } else {
                                            @Suppress("DEPRECATION")
                                            vibrator.vibrate(500)
                                        }
                                    }
                                    onEndSession()
                                    askingToEnd = false
                                }
                            )
                        }
                        launch (start = CoroutineStart.UNDISPATCHED) {
                            detectHorizontalDragGestures { change, _ -> change.consume() }
                        }
                    } else {
                        launch (start = CoroutineStart.UNDISPATCHED) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (vibrationEnabled) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                                        } else {
                                            @Suppress("DEPRECATION")
                                            vibrator.vibrate(150)
                                        }
                                    }
                                    localPaused = !localPaused
                                    if (localPaused) onSpeakRequest("You paused.")
                                    else onSpeakRequest("Returning to session.")
                                    onPauseToggle()
                                },
                                onLongPress = {
                                    if (vibrationEnabled) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                                        } else {
                                            @Suppress("DEPRECATION")
                                            vibrator.vibrate(400)
                                        }
                                    }
                                    wasPausedBeforeAsking = localPaused
                                    if (!localPaused) {
                                        localPaused = true
                                        onPauseToggle()
                                    }
                                    askingToEnd = true
                                    onSpeakRequest("Finish session? Single tap for no, double tap for yes.")
                                }
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (!sessionStarted && introTitle.isNotBlank() && introValue.isNotBlank()) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = introTitle,
                    color = Color(0xFFFFCC00),
                    fontSize = 50.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = introValue,
                    color = Color.White,
                    fontSize = 50.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        } else if (askingToEnd) {
            Text(
                text = "FINISH?",
                color = Color(0xFFFFCC00),
                fontSize = 60.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Canvas(modifier = Modifier.size(160.dp)) {
                if (blinkVisible) {
                    drawArc(
                        color = arcColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = true
                    )
                }
            }
        }
    }
}
