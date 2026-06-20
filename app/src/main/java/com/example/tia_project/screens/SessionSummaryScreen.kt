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
import kotlin.math.roundToInt

/**
 * Final phone summary shown after the watch reports that the workout ended.
 *
 * It highlights a new personal best when one was achieved, otherwise it falls
 * back to the basic session result.
 */
@Composable
fun SessionSummaryScreen(
    goalType: String,
    goalValue: String,
    distanceMeters: Float,
    timeSeconds: Int,
    isNewPersonalBest: Boolean,
    voiceoverEnabled: Boolean,
    vibrationEnabled: Boolean,
    darkModeEnabled: Boolean,
    onBackToMenu: () -> Unit
) {
    val context = LocalContext.current
    val backgroundColor = if (darkModeEnabled) Color.Black else Color.White
    val textColor = if (darkModeEnabled) Color.White else Color.Black
    val accentColor = if (darkModeEnabled) Color(0xFFFFCC00) else Color(0xFFB71C1C)
    val pushingLimitsColor = if (darkModeEnabled) Color(0xFF9C27B0) else Color(0xFF9C27B0)

    val minutes = timeSeconds / 60
    val seconds = timeSeconds % 60
    val sessionDistanceText = "${distanceMeters.roundToInt()} M"
    val recordValueText = if (goalType == "TIME") {
        sessionDistanceText
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
    val timeSpeechText = "$minutes minutes and $seconds seconds"
    val modeLabelText = if (goalType == "TIME") {
        "${goalValue.substringBefore(" ").toIntOrNull() ?: 1} MIN"
    } else {
        "1000 M"
    }

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
            val speechText = if (isNewPersonalBest) {
                if (goalType == "TIME") {
                    "Session finished. New personal best. $recordValueText. $modeLabelText mode. Double tap to go back to menu."
                } else {
                    "Session finished. New personal best. $timeSpeechText. $modeLabelText mode. Double tap to go back to menu."
                }
            } else {
                if (goalType == "TIME") {
                    "Session finished. You did ${distanceMeters.roundToInt()} meters in $minutes minutes. Double tap to go back to menu."
                } else {
                    "Session finished. You completed 1000 meters in $timeSpeechText. Double tap to go back to menu."
                }
            }
            tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "summary_stats")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {},
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
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "FINISHED!",
                color = accentColor,
                fontSize = 50.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (isNewPersonalBest) {
                Text(
                    text = "NEW PERSONAL BEST",
                    color = pushingLimitsColor,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = recordValueText,
                    color = textColor,
                    fontSize = 50.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = modeLabelText,
                    color = textColor,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                if (goalType == "TIME") {
                    Text(
                        text = sessionDistanceText,
                        color = textColor,
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "$minutes MIN",
                        color = textColor,
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = recordValueText,
                        color = textColor,
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = modeLabelText,
                        color = textColor,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
