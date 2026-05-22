package com.example.wear.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.wear.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun WatchProgressScreen(
    progress: Float = 0.45f,
    level: Int = 1,
    paused: Boolean = false,
    difficulty: String = "JUST VIBING",
    onPauseToggle: () -> Unit = {},
    onEndSession: () -> Unit = {}
) {
    var askingToEnd by remember { mutableStateOf(false) }

    val progressColor = when (difficulty) {
        "JUST VIBING" -> Color(0xFF00C853)
        "STARTING TO SWEAT" -> Color(0xFFFFCC00)
        "PUSHING LIMITS" -> Color(0xFFD50000)
        else -> Color(0xFF00C853)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(askingToEnd) {
                detectTapGestures(
                    onDoubleTap = {
                        if (askingToEnd) {
                            onEndSession()
                        } else {
                            onPauseToggle()
                        }
                    },
                    onLongPress = {
                        askingToEnd = true
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (askingToEnd) {
            Text(
                text = "END?",
                color = Color(0xFFD50000),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
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
                    style = Stroke(
                        width = 14f,
                        cap = StrokeCap.Round
                    )
                )
            }

            Text(
                text = when {
                    paused -> "PAUSED"
                    difficulty == "JUST VIBING" -> "${(progress * 100).toInt()}%"
                    else -> "L$level"
                },
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}