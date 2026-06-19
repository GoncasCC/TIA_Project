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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Main menu for the phone app.
 * It routes to setup, progress, guide, and options using the shared accessible gesture model.
 */
@Composable
fun MenuScreen(
    onStartNewSession: () -> Unit,
    onProgress: () -> Unit,
    onGuide: () -> Unit,
    onOptions: () -> Unit,
    voiceoverEnabled: Boolean,
    vibrationEnabled: Boolean,
    darkModeEnabled: Boolean
) {
    OptionTextMenuScreen(
        screenKey = "mainMenu",
        options = listOf("START NEW SESSION", "PROGRESS", "GUIDE", "OPTIONS"),
        title = "MENU",
        voiceoverEnabled = voiceoverEnabled,
        vibrationEnabled = vibrationEnabled,
        darkModeEnabled = darkModeEnabled,
        speechForOption = { option ->
            when (option) {
                "START NEW SESSION" -> "Let's start a new session."
                "PROGRESS" -> "Check your progress."
                "GUIDE" -> "Consult the guide."
                "OPTIONS" -> "Change options."
                else -> option.toReadableMenuText()
            }
        },
        selectSpeechForOption = { option ->
            when (option) {
                "START NEW SESSION" -> "Starting a new session."
                "PROGRESS" -> "Opening progress."
                "GUIDE" -> "Opening the guide."
                "OPTIONS" -> "Opening options."
                else -> "Selected."
            }
        },
        onNext = { option ->
            when (option) {
                "START NEW SESSION" -> onStartNewSession()
                "PROGRESS" -> onProgress()
                "GUIDE" -> onGuide()
                "OPTIONS" -> onOptions()
            }
        }
    )
}

/**
 * Reusable text-only menu container with spoken hints and swipe-based selection.
 */
@Composable
private fun OptionTextMenuScreen(
    screenKey: String,
    options: List<String>,
    title: String,
    voiceoverEnabled: Boolean,
    vibrationEnabled: Boolean,
    darkModeEnabled: Boolean,
    speechForOption: (String) -> String,
    selectSpeechForOption: (String) -> String,
    onNext: (String) -> Unit
) {
    var selectedIndex by remember { mutableStateOf(0) }
    var dragAmountTotal by remember { mutableStateOf(0f) }
    var hasChangedOptionThisSwipe by remember { mutableStateOf(false) }
    var hasAnnouncedMenuEntry by remember { mutableStateOf(false) }
    var isNavigating by remember { mutableStateOf(false) }

    val selectedOption = options[selectedIndex]
    val context = androidx.compose.ui.platform.LocalContext.current

    val backgroundColor = if (darkModeEnabled) Color.Black else Color.White
    val textColor = if (darkModeEnabled) Color.White else Color.Black
    val accentColor = if (darkModeEnabled) Color(0xFFFFCC00) else Color(0xFFB71C1C)

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

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    fun speakFlush(text: String, id: String) {
        if (isTtsReady && voiceoverEnabled) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }
    }

    fun speakAdd(text: String, id: String) {
        if (isTtsReady && voiceoverEnabled) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        }
    }

    suspend fun waitForSpeechToFinish() {
        delay(200)
        while (tts?.isSpeaking == true) {
            delay(100)
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

    LaunchedEffect(isTtsReady, voiceoverEnabled) {
        if (isTtsReady && voiceoverEnabled && !hasAnnouncedMenuEntry) {
            hasAnnouncedMenuEntry = true

            tts?.speak(
                "You are in the menu.",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "menu_entered"
            )

            tts?.speak(
                speechForOption(selectedOption),
                TextToSpeech.QUEUE_ADD,
                null,
                "menu_${screenKey}_${selectedOption.lowercase().replace(" ", "_")}"
            )
        }
    }

    LaunchedEffect(selectedIndex, isTtsReady, voiceoverEnabled) {
        if (isTtsReady && voiceoverEnabled && hasAnnouncedMenuEntry) {
            speakFlush(
                speechForOption(selectedOption),
                "menu_${screenKey}_${selectedOption.lowercase().replace(" ", "_")}"
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(Unit) {
                coroutineScope {
                    launch (start = CoroutineStart.UNDISPATCHED){
                        detectTapGestures(
                            onTap = {
                                android.util.Log.d(
                                    "TOUCHDEBUG",
                                    "onTap"
                                )
                            },
                            onDoubleTap = {
                                android.util.Log.d(
                                    "TOUCHDEBUG",
                                    "onDoubleTap"
                                )
                                if (isNavigating) return@detectTapGestures
                                isNavigating = true
                                val chosen = options[selectedIndex]
                                vibrate(150)

                                launch {
                                    speakFlush(
                                        selectSpeechForOption(chosen),
                                        "select_${chosen.lowercase().replace(" ", "_")}"
                                    )
                                    waitForSpeechToFinish()
                                    onNext(chosen)
                                }
                            }
                        )
                    }

                    launch (start = CoroutineStart.UNDISPATCHED) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragAmountTotal = 0f
                                hasChangedOptionThisSwipe = false
                            },
                            onDragEnd = {
                                dragAmountTotal = 0f
                                hasChangedOptionThisSwipe = false
                            },
                            onDragCancel = {
                                dragAmountTotal = 0f
                                hasChangedOptionThisSwipe = false
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            if (isNavigating) return@detectHorizontalDragGestures
                            dragAmountTotal += dragAmount

                            if (!hasChangedOptionThisSwipe) {
                                if (dragAmountTotal > 120f) {
                                    selectedIndex =
                                        if (selectedIndex == 0) options.lastIndex else selectedIndex - 1
                                    vibrate(30)
                                    hasChangedOptionThisSwipe = true
                                }

                                if (dragAmountTotal < -120f) {
                                    selectedIndex =
                                        if (selectedIndex == options.lastIndex) 0 else selectedIndex + 1
                                    vibrate(30)
                                    hasChangedOptionThisSwipe = true
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        MenuOptionLayout(
            title = title,
            selectedOption = selectedOption,
            textColor = textColor,
            accentColor = accentColor
        )
    }
}

@Composable
private fun MenuOptionLayout(
    title: String,
    selectedOption: String,
    textColor: Color,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 40.dp)
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 82.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        )

        Text(
            text = selectedOption.toReadableMenuText(),
            color = accentColor,
            fontSize = when {
                selectedOption.length > 16 -> 52.sp
                selectedOption.length > 8 -> 62.sp
                else -> 72.sp
            },
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
        )
    }
}

private fun String.toReadableMenuText(): String {
    return lowercase()
        .split(" ")
        .joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
}
