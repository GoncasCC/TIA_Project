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

/** Ordered tutorial steps for the gesture and workout-mode onboarding flow. */
private enum class GuideStep {
    SWIPE,
    DOUBLE_TAP,
    TWO_FINGER_BACK,
    LONG_PRESS,
    EASYMODE,
    MEDIUMMODE,
    HARDMODE,
    FINISHED
}

/**
 * Interactive onboarding screen that teaches the gesture vocabulary and the
 * meaning of each training mode.
 */
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
    val accentColor = if (darkModeEnabled) Color(0xFFFFCC00) else Color(0xFFB71C1C)
    val cardColor = if (darkModeEnabled) Color.White else Color(0xFF333333)
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
                "Good. Two finger swipe goes back. Now, long press does different things depending on where you are and which device you are using. If you are on the smartphone setting up a new session, it cancels that setup. If you are on the smartwatch in a workout, it ends the session. If you are on the smartphone menu, it opens the option to leave the app. Long press to test.\n",
                "guide_long_press"
            )

            GuideStep.EASYMODE -> speak(
                "Now let's learn about the exercise modes. You can choose one of three modes. Just Vibing is the easiest mode. It is designed for relaxed physical activity, with no progress feedback during the session. You will hear relaxing music, or footsteps if music is disabled. Double tap to continue.",
                "guide_modes"
            )

            GuideStep.MEDIUMMODE -> speak(
                "Starting to Sweat is a medium intensity mode. Here, your workout is divided into stages with halfway milestones. You receive progress feedback as you exercise. The music changes as you advance through each stage to tell you how close you are to finishing. If music is disabled, you will hear footsteps instead. Double tap to continue.",
                "guide_modes"
            )

            GuideStep.HARDMODE -> speak(
                "Pushing Limits is the most intense mode. Just like Starting to Sweat, the music changes as you advance to show your progress towards the end, but the system also encourages you to improve your pace and beat your personal best. For the distance mode, the target is 1000 meters. You can disable the music and hear footsteps instead. Double tap to finish the guide.",
                "guide_modes"
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
            .pointerInput(Unit) {
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
            .pointerInput(Unit) {
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
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {},
                    onDoubleTap = {
                        when (step) {
                            GuideStep.DOUBLE_TAP -> {
                                vibrate(150)
                                speak("Selected.", "guide_select_done")
                                step = GuideStep.TWO_FINGER_BACK
                            }

                            GuideStep.EASYMODE -> {
                                vibrate(150)
                                step = GuideStep.MEDIUMMODE
                            }

                            GuideStep.MEDIUMMODE -> {
                                vibrate(150)
                                step = GuideStep.HARDMODE
                            }

                            GuideStep.HARDMODE -> {
                                vibrate(150)
                                step = GuideStep.FINISHED
                            }

                            else -> Unit
                        }
                    },
                    onLongPress = {
                        if (step == GuideStep.LONG_PRESS) {
                            vibrate(500)
                            speak("Cancelled.", "guide_cancel_done")
                            step = GuideStep.EASYMODE
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

/** Visual card used by the guide when explaining each workout intensity. */
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
        GuideStep.EASYMODE -> "JUST VIBING"
        GuideStep.MEDIUMMODE -> "STARTING\nTO SWEAT"
        GuideStep.HARDMODE -> "PUSHING\nLIMITS"
        GuideStep.FINISHED -> "DONE"
    }

    val vibration = when (step) {
        GuideStep.SWIPE -> "short vibration"
        GuideStep.DOUBLE_TAP -> "medium vibration"
        GuideStep.TWO_FINGER_BACK -> "double vibration"
        GuideStep.LONG_PRESS -> "long vibration"
        GuideStep.EASYMODE -> ""
        GuideStep.MEDIUMMODE -> ""
        GuideStep.HARDMODE -> ""
        GuideStep.FINISHED -> "guide completed"
    }

    val isDarkMode = textColor == Color.White

    val currentCardColor = when (step) {
        GuideStep.EASYMODE   -> if (isDarkMode) Color(0xFF2196F3) else Color(0xFF1565C0)
        GuideStep.MEDIUMMODE -> if (isDarkMode) Color(0xFFFF9800) else Color(0xFFB25F00)
        GuideStep.HARDMODE   -> if (isDarkMode) Color(0xFF9C27B0) else Color(0xFF9C27B0)
        else -> cardColor
    }

    val currentCardTextColor = when (step) {
        GuideStep.EASYMODE   -> if (isDarkMode) Color.Black else Color.White
        GuideStep.MEDIUMMODE -> if (isDarkMode) Color.Black else Color.White
        GuideStep.HARDMODE   -> if (isDarkMode) Color.Black else Color.White
        else -> cardTextColor
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

        if (step == GuideStep.FINISHED) {
            Text(
                text = "DONE",
                color = accentColor,
                fontSize = 100.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(currentCardColor, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = instruction,
                        color = currentCardTextColor,
                        fontSize = if (instruction.length > 12) 42.sp else 58.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                val starCount = when (step) {
                    GuideStep.EASYMODE -> 1
                    GuideStep.MEDIUMMODE -> 2
                    GuideStep.HARDMODE -> 3
                    else -> 0
                }

                if (starCount > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        repeat(starCount) {
                            Text(
                                text = "\u2605",
                                color = if (isDarkMode) Color.White else Color(0xFF333333),
                                fontSize = 100.sp,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
                    }
                } else {
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
    }
}
