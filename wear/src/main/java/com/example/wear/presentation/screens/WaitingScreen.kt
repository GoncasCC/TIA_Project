package com.example.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text

/** Idle watch screen shown while waiting for the phone to start a new session. */
@Composable
fun WaitingScreen(
    onExitRequest: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onExitRequest() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Waiting\nfor session",
            color = Color.White,
            fontSize = 50.sp,
            textAlign = TextAlign.Center
        )
    }
}
