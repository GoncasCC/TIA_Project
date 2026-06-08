package com.example.tia_project.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
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
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun SessionSummaryScreen(
    distanceKm: Float,
    timeSeconds: Int,
    voiceoverEnabled: Boolean,
    vibrationEnabled: Boolean,
    darkModeEnabled: Boolean,
    onBackToMenu: () -> Unit
) {
    val context = LocalContext.current
    val backgroundColor = if (darkModeEnabled) Color.Black else Color.White
    val textColor = if (darkModeEnabled) Color.White else Color.Black
    val accentColor = Color(0xFF00C853) // Verde para sucesso

    val minutes = timeSeconds / 60
    val distanceStr = String.format(Locale.US, "%.2f", distanceKm)

    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

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

    LaunchedEffect(isTtsReady, voiceoverEnabled) {
        if (isTtsReady && voiceoverEnabled) {
            delay(500)
            val speechText = "Session finished. You did $distanceStr kilometers in $minutes minutes. Double tap to go back to menu."
            tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "summary_stats")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(Unit) {
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

                        if (isTtsReady && voiceoverEnabled) {
                            tts?.speak("Going back to menu.", TextToSpeech.QUEUE_FLUSH, null, "back_to_menu")
                        }

                        onBackToMenu()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "FINISHED!",
                color = accentColor,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "$distanceStr KM",
                color = textColor,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "$minutes MIN",
                color = textColor,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}