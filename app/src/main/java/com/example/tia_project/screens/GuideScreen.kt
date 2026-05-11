package com.example.tia_project.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

private enum class GuideStep {
    SWIPE,
    DOUBLE_TAP,
    TWO_FINGER_BACK,
    LONG_PRESS,
    FINISHED
}

@Composable
fun GuideScreen(
    voiceoverEnabled: Boolean,
    vibrationEnabled: Boolean,
    darkModeEnabled: Boolean,
    showReopenGuideMessage: Boolean,
    onFinish: () -> Unit
) {
    var step by remember { mutableStateOf(GuideStep.SWIPE) }
    var dragAmountTotal by remember { mutableStateOf(0f) }
    var twoFingerMoveTotal by remember { mutableStateOf(0f) }
    var hasFinished by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val backgroundColor = if (darkModeEnabled) Color.Black else Color.White
    val textColor = if (darkModeEnabled) Color.White else Color.Black
    val accentColor = if (darkModeEnabled) Color(0xFFFFCC00) else Color(0xFFD90000)
    val cardColor = if (darkModeEnabled) Color.White else Color(0xFFD90000)
    val cardTextColor = if (darkModeEnabled) Color.Black else Color.White

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

    fun vibrate(duration: Long) {
        if (!vibrationEnabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    duration,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    fun vibratePattern(pattern: LongArray) {
        if (!vibrationEnabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
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

    LaunchedEffect(step, isTtsReady, voiceoverEnabled) {
        if (!isTtsReady && voiceoverEnabled) return@LaunchedEffect

        when (step) {
            GuideStep.SWIPE -> speak(
                "Welcome! Let’s learn how to navigate the app using gestures and understand what the vibrations mean. Swipe left or right with one finger to change options.",
                "guide_swipe"
            )

            GuideStep.DOUBLE_TAP -> speak(
                "Good. Swipe changes options. Now double tap to select.",
                "guide_double_tap"
            )

            GuideStep.TWO_FINGER_BACK -> speak(
                "Good. Double tap selects. Now swipe left or right with two fingers to go back.",
                "guide_two_finger_back"
            )

            GuideStep.LONG_PRESS -> speak(
                "Good. Two finger swipe goes back. Now long press to cancel a new session.",
                "guide_long_press"
            )

            GuideStep.FINISHED -> {
                if (!hasFinished) {
                    hasFinished = true

                    if (isTtsReady && voiceoverEnabled) {
                        val finalMessage = if (showReopenGuideMessage) {
                            "Guide completed. You can open this guide again from the main menu by selecting Guide. Going back to menu."
                        } else {
                            "Guide completed. Going back to menu."
                        }

                        tts?.speak(
                            finalMessage,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "guide_finished"
                        )

                        delay(if (showReopenGuideMessage) 7500 else 3500)
                    }

                    onFinish()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(step) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }

                        if (step == GuideStep.TWO_FINGER_BACK && pressed.size == 2) {
                            val move = pressed
                                .sumOf { it.positionChange().x.toDouble() }
                                .toFloat()

                            twoFingerMoveTotal += move

                            if (twoFingerMoveTotal > 100f || twoFingerMoveTotal < -100f) {
                                vibratePattern(longArrayOf(0, 60, 80, 60))
                                speak("Go back.", "guide_back_done")
                                twoFingerMoveTotal = 0f
                                step = GuideStep.LONG_PRESS
                            }
                        }

                        if (pressed.isEmpty()) {
                            twoFingerMoveTotal = 0f
                        }
                    }
                }
            }
            .pointerInput(step) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragAmountTotal = 0f
                    },
                    onDragEnd = {
                        dragAmountTotal = 0f
                    },
                    onDragCancel = {
                        dragAmountTotal = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    dragAmountTotal += dragAmount

                    if (step == GuideStep.SWIPE &&
                        (dragAmountTotal > 120f || dragAmountTotal < -120f)
                    ) {
                        vibrate(30)
                        speak("Changing option.", "guide_swipe_done")
                        dragAmountTotal = 0f
                        step = GuideStep.DOUBLE_TAP
                    }
                }
            }
            .pointerInput(step) {
                detectTapGestures(
                    onDoubleTap = {
                        if (step == GuideStep.DOUBLE_TAP) {
                            vibrate(150)
                            speak("Selected.", "guide_select_done")
                            step = GuideStep.TWO_FINGER_BACK
                        }
                    },
                    onLongPress = {
                        if (step == GuideStep.LONG_PRESS) {
                            vibrate(500)
                            speak("Cancelled.", "guide_cancel_done")
                            step = GuideStep.FINISHED
                        }
                    }
                )
            }
    ) {
        GuideTrainingLayout(
            step = step,
            textColor = textColor,
            accentColor = accentColor,
            cardColor = cardColor,
            cardTextColor = cardTextColor
        )
    }
}

@Composable
private fun GuideTrainingLayout(
    step: GuideStep,
    textColor: Color,
    accentColor: Color,
    cardColor: Color,
    cardTextColor: Color
) {
    val instruction = when (step) {
        GuideStep.SWIPE -> "SWIPE"
        GuideStep.DOUBLE_TAP -> "DOUBLE TAP"
        GuideStep.TWO_FINGER_BACK -> "TWO FINGER\nSWIPE"
        GuideStep.LONG_PRESS -> "LONG PRESS"
        GuideStep.FINISHED -> "DONE"
    }

    val vibration = when (step) {
        GuideStep.SWIPE -> "short vibration"
        GuideStep.DOUBLE_TAP -> "medium vibration"
        GuideStep.TWO_FINGER_BACK -> "double vibration"
        GuideStep.LONG_PRESS -> "long vibration"
        GuideStep.FINISHED -> "guide completed"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 40.dp)
    ) {
        Text(
            text = "GUIDE",
            color = textColor,
            fontSize = 76.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(cardColor, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = instruction,
                    color = cardTextColor,
                    fontSize = if (instruction.length > 12) 42.sp else 58.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = vibration.uppercase(),
                color = accentColor,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}