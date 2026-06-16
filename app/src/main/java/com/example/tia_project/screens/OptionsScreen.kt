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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun OptionsScreen(
    voiceoverEnabled: Boolean,
    musicEnabled: Boolean,
    vibrationEnabled: Boolean,
    darkModeEnabled: Boolean,
    onVoiceoverChange: (Boolean) -> Unit,
    onMusicChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val options = listOf("VOICEOVER", "MUSIC", "VIBRATION", "DARK MODE")

    var selectedIndex by remember { mutableStateOf(0) }
    var dragAmountTotal by remember { mutableStateOf(0f) }
    var hasChangedOptionThisSwipe by remember { mutableStateOf(false) }
    var hasGoneBackThisGesture by remember { mutableStateOf(false) }
    var isTwoFingerGesture by remember { mutableStateOf(false) }
    var isGoingBack by remember { mutableStateOf(false) }

    val selectedOption = options[selectedIndex]
    val context = androidx.compose.ui.platform.LocalContext.current

    val backgroundColor = if (darkModeEnabled) Color.Black else Color.White
    val textColor = if (darkModeEnabled) Color.White else Color.Black
    val cardColor = if (darkModeEnabled) Color.White else Color(0xFFD90000)
    val cardTextColor = if (darkModeEnabled) Color.Black else Color.White

    val currentValue = when (selectedOption) {
        "VOICEOVER" -> voiceoverEnabled
        "MUSIC" -> musicEnabled
        "VIBRATION" -> vibrationEnabled
        "DARK MODE" -> darkModeEnabled
        else -> false
    }


    val currentOnBack by rememberUpdatedState(onBack)
    val currentVibrationEnabled by rememberUpdatedState(vibrationEnabled)
    val currentVoiceoverEnabled by rememberUpdatedState(voiceoverEnabled)
    val currentSelectedOption by rememberUpdatedState(selectedOption)
    val currentValue2 by rememberUpdatedState(currentValue)
    val currentOnVoiceoverChange by rememberUpdatedState(onVoiceoverChange)
    val currentOnMusicChange by rememberUpdatedState(onMusicChange)
    val currentOnVibrationChange by rememberUpdatedState(onVibrationChange)
    val currentOnDarkModeChange by rememberUpdatedState(onDarkModeChange)

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

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    val currentIsTtsReady by rememberUpdatedState(isTtsReady)
    val currentTts by rememberUpdatedState(tts)

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

    LaunchedEffect(selectedOption, currentValue, isTtsReady, voiceoverEnabled) {
        if (isTtsReady && voiceoverEnabled) {
            val stateText = if (currentValue) "On" else "Off"
            tts?.speak(
                "Changing options. ${selectedOption.toReadableOptionsText()}: $stateText.",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "options_${selectedOption.lowercase().replace(" ", "_")}"
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)

            .pointerInput(Unit) {
                fun vibrate(duration: Long) {
                    if (!currentVibrationEnabled) return
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(duration)
                    }
                }

                fun speak(text: String, id: String) {
                    if (currentIsTtsReady && currentVoiceoverEnabled) {
                        currentTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
                    }
                }

                coroutineScope {

                    launch {
                        detectTapGestures(
                            onDoubleTap = {
                                if (isGoingBack) return@detectTapGestures
                                val newValue = !currentValue2

                                when (currentSelectedOption) {
                                    "VOICEOVER" -> currentOnVoiceoverChange(newValue)
                                    "MUSIC" -> currentOnMusicChange(newValue)
                                    "VIBRATION" -> currentOnVibrationChange(newValue)
                                    "DARK MODE" -> currentOnDarkModeChange(newValue)
                                }

                                vibrate(150)

                                val stateText = if (newValue) "On" else "Off"
                                speak(
                                    "${currentSelectedOption.toReadableOptionsText()} turned $stateText.",
                                    "options_toggle_${currentSelectedOption.lowercase().replace(" ", "_")}"
                                )
                            }
                        )
                    }

                    launch {
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
                            if (change.pressed != true) return@detectHorizontalDragGestures

                            if (isGoingBack || isTwoFingerGesture) {
                                dragAmountTotal = 0f
                                hasChangedOptionThisSwipe = false
                                return@detectHorizontalDragGestures
                            }

                            change.consume()
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

                    launch {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressedPointers = event.changes.filter { it.pressed }

                                isTwoFingerGesture = pressedPointers.size >= 2

                                if (pressedPointers.size == 2 && !hasGoneBackThisGesture) {
                                    val horizontalMove = pressedPointers
                                        .sumOf { it.positionChange().x.toDouble() }
                                        .toFloat()

                                    if (horizontalMove > 80f || horizontalMove < -80f) {
                                        hasGoneBackThisGesture = true
                                        isGoingBack = true
                                        vibrate(60)
                                        speak("Going back to menu.", "options_go_back")

                                        launch {
                                            kotlinx.coroutines.delay(2000)
                                            currentOnBack()
                                        }
                                    }
                                }

                                if (pressedPointers.isEmpty()) {
                                    hasGoneBackThisGesture = false
                                    isTwoFingerGesture = false
                                }
                            }
                        }
                    }
                }
            }
    ) {
        OptionsLayout(
            selectedOption = selectedOption,
            currentValue = currentValue,
            backgroundColor = backgroundColor,
            textColor = textColor,
            cardColor = cardColor,
            cardTextColor = cardTextColor
        )
    }
}

@Composable
private fun OptionsLayout(
    selectedOption: String,
    currentValue: Boolean,
    backgroundColor: Color,
    textColor: Color,
    cardColor: Color,
    cardTextColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 20.dp, vertical = 40.dp)
    ) {
        Text(
            text = "OPTIONS",
            color = textColor,
            fontSize = 72.sp,
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
                    .height(140.dp)
                    .background(cardColor, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectedOption.toReadableOptionsText(),
                    color = cardTextColor,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = if (currentValue) "ON" else "OFF",
                color = if (currentValue) Color(0xFF2196F3) else Color(0xFF9C27B0),
                fontSize = 86.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun String.toReadableOptionsText(): String {
    return lowercase()
        .split(" ")
        .joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
}
