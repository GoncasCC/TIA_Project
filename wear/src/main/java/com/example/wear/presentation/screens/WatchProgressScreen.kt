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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun WatchProgressScreen(
    progress: Float = 0.45f,
    level: Int = 1,
    paused: Boolean = false,
    isStopped: Boolean = false,
    difficulty: String = "JUST VIBING",
    vibrationEnabled: Boolean = true, // <--- ADICIONADO AQUI
    onPauseToggle: () -> Unit = {},
    onResume: () -> Unit = {},
    onEndSession: () -> Unit = {},
    onSpeakRequest: (String) -> Unit = {}
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

    val progressColor = when {
        isStopped -> Color(0xFF7B1FA2)
        localPaused -> Color(0xFF7B1FA2)
        difficulty == "PUSHING LIMITS" -> Color(0xFFFF6F00)
        else -> Color(0xFF1565C0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(askingToEnd, vibrationEnabled) { // <--- vibrationEnabled adicionado aos "keys" do pointerInput

                coroutineScope {
                    if (askingToEnd) {
                        launch {
                            detectTapGestures(
                                onTap = {
                                    askingToEnd = false
                                    if (!wasPausedBeforeAsking) {
                                        localPaused = false
                                        onResume()
                                    }
                                },
                                onDoubleTap = {
                                    if (vibrationEnabled) { // <--- VERIFICAÇÃO ADICIONADA
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
                        launch {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                            }
                        }

                    } else {
                        launch {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (vibrationEnabled) { // <--- VERIFICAÇÃO ADICIONADA
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                                        } else {
                                            @Suppress("DEPRECATION")
                                            vibrator.vibrate(150)
                                        }
                                    }
                                    localPaused = !localPaused
                                    onPauseToggle()
                                },
                                onLongPress = {
                                    if (vibrationEnabled) { // <--- VERIFICAÇÃO ADICIONADA
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
        if (askingToEnd) {
            Text(
                text = "FINISH?",
                color = Color(0xFFD50000),
                fontSize = 60.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Canvas(modifier = Modifier.size(160.dp)) {
                drawCircle(
                    color = Color.DarkGray,
                    radius = size.minDimension / 2f,
                    style = Stroke(width = 14f)
                )
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 14f, cap = StrokeCap.Round)
                )
            }
            Text(
                text = when {
                    isStopped  -> "STOPPED"
                    localPaused -> "PAUSED"
                    else        -> "L$level"
                },
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}