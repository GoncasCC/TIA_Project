package com.example.tia_project.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tia_project.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ActivityScreen(
    voiceoverEnabled: Boolean,
    vibrationEnabled: Boolean,
    darkModeEnabled: Boolean,
    onNext: (String) -> Unit,
    onCancel: () -> Unit
) {
    OptionSelectionScreen(
        screenKey = "activity",
        options = listOf("WALK", "RUN"),
        imageForOption = { option ->
            if (option == "WALK") {
                if (darkModeEnabled) R.drawable.walk_darkmodeicon else R.drawable.walk_lightmodeicon
            } else {
                if (darkModeEnabled) R.drawable.run_darkmodeicon else R.drawable.run_lightmodeicon
            }
        },
        speechForOption = { option -> "Selecting activity: ${option.toReadableText()}." },
        voiceoverEnabled = voiceoverEnabled,
        vibrationEnabled = vibrationEnabled,
        darkModeEnabled = darkModeEnabled,
        onNext = onNext,
        onCancel = onCancel
    )
}

@Composable
fun GoalTypeScreen(
    voiceoverEnabled: Boolean,
    vibrationEnabled: Boolean,
    darkModeEnabled: Boolean,
    onNext: (String) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit
) {
    OptionSelectionScreen(
        screenKey = "goalType",
        options = listOf("TIME", "DISTANCE"),
        imageForOption = { option ->
            if (option == "TIME") {
                if (darkModeEnabled) R.drawable.time_darkmodeicon else R.drawable.time_lightmodeicon
            } else {
                if (darkModeEnabled) R.drawable.distance_darkmodeicon else R.drawable.distance_lightmodeicon
            }
        },
        speechForOption = { option -> "Selecting goal type: ${option.toReadableText()}." },
        voiceoverEnabled = voiceoverEnabled,
        vibrationEnabled = vibrationEnabled,
        darkModeEnabled = darkModeEnabled,
        onNext = onNext,
        onBack = onBack,
        onCancel = onCancel
    )
}

@Composable
fun GoalValueScreen(
    goalType: String,
    voiceoverEnabled: Boolean,
    vibrationEnabled: Boolean,
    darkModeEnabled: Boolean,
    onNext: (String) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit
) {
    val options = if (goalType == "TIME") {
        listOf("1 MINUTE", "5 MINUTES")
    } else {
        listOf("1 KILOMETER", "5 KILOMETERS")
    }

    OptionSelectionScreen(
        screenKey = "goalValue_$goalType",
        options = options,
        imageForOption = { if (darkModeEnabled) R.drawable.one_darkmodeicon else R.drawable.one_lightmodeicon },
        speechForOption = { option -> "Selecting goal value: $option." },
        voiceoverEnabled = voiceoverEnabled,
        vibrationEnabled = vibrationEnabled,
        darkModeEnabled = darkModeEnabled,
        onNext = onNext,
        onBack = onBack,
        onCancel = onCancel,
        valueMode = true
    )
}

@Composable
private fun OptionSelectionScreen(
    screenKey: String,
    options: List<String>,
    imageForOption: (String) -> Int,
    speechForOption: (String) -> String,
    voiceoverEnabled: Boolean,
    vibrationEnabled: Boolean,
    darkModeEnabled: Boolean,
    onNext: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    onCancel: () -> Unit,
    valueMode: Boolean = false
) {
    var selectedIndex by remember { mutableStateOf(0) }
    var dragAmountTotal by remember { mutableStateOf(0f) }
    var hasChangedOptionThisSwipe by remember { mutableStateOf(false) }

    val selectedOption = options[selectedIndex]
    val context = LocalContext.current

    val backgroundColor = if (darkModeEnabled) Color.Black else Color.White
    val textColor = if (darkModeEnabled) Color.White else Color.Black

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

    val vibrate: (Long) -> Unit = remember(vibrator, vibrationEnabled) {
        { duration ->
            if (vibrationEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            }
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

    LaunchedEffect(screenKey, selectedOption, isTtsReady, voiceoverEnabled) {
        delay(600)
        speak(
            speechForOption(selectedOption),
            "option_${screenKey}_${selectedOption.lowercase().replace(" ", "_")}"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(selectedOption, onBack, onCancel, vibrationEnabled, voiceoverEnabled) {
                coroutineScope {

                    launch {
                        detectTapGestures(
                            onDoubleTap = {
                                vibrate(150)
                                speak("Selected.", "selected")
                                launch {
                                    delay(500)
                                    onNext(selectedOption)
                                }
                            },
                            onLongPress = {
                                vibrate(400)
                                speak("Cancelled. Going back to menu.", "cancelled")
                                launch {
                                    delay(2000)
                                    onCancel()
                                }
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

                    if (onBack != null) {
                        launch {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressedPointers = event.changes.filter { it.pressed }

                                    if (pressedPointers.size == 2) {
                                        val horizontalMove = pressedPointers
                                            .sumOf { it.positionChange().x.toDouble() }
                                            .toFloat()

                                        if (horizontalMove > 80f || horizontalMove < -80f) {
                                            vibrate(60)
                                            speak("Go back.", "go_back")
                                            launch {
                                                delay(500)
                                                onBack()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        if (valueMode) {
            GoalValueLayout(
                selectedOption = selectedOption,
                backgroundColor = backgroundColor,
                textColor = textColor,
                darkModeEnabled = darkModeEnabled
            )
        } else {
            IconOptionLayout(
                selectedOption = selectedOption,
                imageRes = imageForOption(selectedOption),
                textColor = textColor
            )
        }
    }
}

@Composable
private fun IconOptionLayout(
    selectedOption: String,
    imageRes: Int,
    textColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val textSize = when {
            selectedOption.length > 10 -> 52.sp
            selectedOption.length > 5 -> 58.sp
            else -> 82.sp
        }

        Text(
            text = selectedOption,
            color = textColor,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 40.dp)
        )

        Image(
            painter = painterResource(id = imageRes),
            contentDescription = selectedOption,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
        )
    }
}

@Composable
private fun GoalValueLayout(
    selectedOption: String,
    backgroundColor: Color,
    textColor: Color,
    darkModeEnabled: Boolean
) {
    val isOne = selectedOption.startsWith("1")
    val unit = selectedOption.substringAfter(" ")

    val numberImage = if (isOne) {
        if (darkModeEnabled) R.drawable.one_darkmodeicon else R.drawable.one_lightmodeicon
    } else {
        if (darkModeEnabled) R.drawable.five_darkmodeicon else R.drawable.five_lightmodeicon
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Image(
            painter = painterResource(id = numberImage),
            contentDescription = selectedOption,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(620.dp)
        )

        Text(
            text = unit,
            color = textColor,
            fontSize = 72.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun String.toReadableText(): String {
    return lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
fun DifficultyScreen(
    voiceoverEnabled: Boolean,
    vibrationEnabled: Boolean,
    darkModeEnabled: Boolean,
    onNext: (String) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit
) {
    val options = listOf("JUST VIBING", "STARTING TO SWEAT", "PUSHING LIMITS")
    val colorForOption = mapOf(
        "JUST VIBING" to Color(0xFF00C853),
        "STARTING TO SWEAT" to Color(0xFFFFCC00),
        "PUSHING LIMITS" to Color(0xFFD50000)
    )

    var selectedIndex by remember { mutableStateOf(0) }
    var dragAmountTotal by remember { mutableStateOf(0f) }
    var hasChangedOptionThisSwipe by remember { mutableStateOf(false) }

    val selectedOption = options[selectedIndex]
    val context = LocalContext.current
    val backgroundColor = if (darkModeEnabled) Color.Black else Color.White

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

    val vibrate: (Long) -> Unit = remember(vibrator, vibrationEnabled) {
        { duration ->
            if (vibrationEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            }
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

    LaunchedEffect(selectedOption, isTtsReady, voiceoverEnabled) {
        delay(600)
        speak(
            "Selecting level of difficulty: ${selectedOption.toReadableText()}.",
            "difficulty_$selectedOption"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(selectedOption, vibrationEnabled, voiceoverEnabled) {
                coroutineScope {
                    launch {
                        detectTapGestures(
                            onDoubleTap = {
                                vibrate(150)
                                speak("Selected.", "selected")
                                launch {
                                    delay(500)
                                    onNext(selectedOption)
                                }
                            },
                            onLongPress = {
                                vibrate(400)
                                speak("Cancelled. Going back to menu.", "cancelled")
                                launch {
                                    delay(2000)
                                    onCancel()
                                }
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
                                if (pressedPointers.size == 2) {
                                    val horizontalMove = pressedPointers
                                        .sumOf { it.positionChange().x.toDouble() }
                                        .toFloat()
                                    if (horizontalMove > 80f || horizontalMove < -80f) {
                                        vibrate(60)
                                        speak("Go back.", "go_back")
                                        launch {
                                            delay(500)
                                            onBack()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 40.dp)
        ) {
            Text(
                text = "LEVEL",
                color = if (darkModeEnabled) Color.White else Color.Black,
                fontSize = 82.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )

            Text(
                text = selectedOption,
                color = colorForOption[selectedOption] ?: Color.White,
                fontSize = when {
                    selectedOption.length > 15 -> 54.sp
                    selectedOption.length > 10 -> 64.sp
                    else -> 80.sp
                },
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
fun SummaryScreen(
    activity: String,
    goalValue: String,
    difficulty: String,
    voiceoverEnabled: Boolean,
    vibrationEnabled: Boolean,
    darkModeEnabled: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    val backgroundColor = if (darkModeEnabled) Color.Black else Color.White
    val safetyColor = Color(0xFFFFCC00)
    val difficultyColor = when (difficulty) {
        "JUST VIBING" -> Color(0xFF00C853)
        "STARTING TO SWEAT" -> Color(0xFFFFCC00)
        "PUSHING LIMITS" -> Color(0xFFD50000)
        else -> if (darkModeEnabled) Color.White else Color.Black
    }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    var showTriangle by remember { mutableStateOf(false) }

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
        if (vibrationEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        }
    }

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

    LaunchedEffect(isTtsReady, voiceoverEnabled) {
        delay(600)
        if (isTtsReady && voiceoverEnabled) {
            tts?.speak(
                "You selected $activity. Goal: $goalValue. Difficulty: $difficulty.",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "summary1"
            )
            while (tts?.isSpeaking == true) {
                delay(100)
            }
            delay(300)
            tts?.speak(
                "Double tap to confirm you are in a safe, obstacle-free environment.",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "summary2"
            )
            showTriangle = true
        }
    }

    LaunchedEffect(confirmed) {
        if (confirmed) {
            speak("Selected. Your session is starting now.", "starting")
            delay(2500)
            onStart()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(confirmed, vibrationEnabled, voiceoverEnabled) {
                detectTapGestures(
                    onDoubleTap = {
                        if (!confirmed) {
                            vibrate(150)
                            confirmed = true
                        }
                    },
                    onLongPress = {
                        vibrate(400)
                        speak("Cancelled.", "cancelled_summary")
                        onCancel()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (!confirmed) {
            if (!showTriangle) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = activity,
                        color = if (darkModeEnabled) Color.White else Color.Black,
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = goalValue,
                        color = if (darkModeEnabled) Color.White else Color.Black,
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = difficulty,
                        color = difficultyColor,
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (showTriangle) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(380.dp)) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width / 2f, size.height * 0.05f)
                        lineTo(size.width * 0.97f, size.height * 0.95f)
                        lineTo(size.width * 0.03f, size.height * 0.95f)
                        close()
                    }
                    drawPath(path, color = safetyColor)
                    drawPath(
                        path,
                        color = if (darkModeEnabled) Color.Black else Color.White,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 30f)
                    )
                    drawRect(
                        color = Color.Black,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            size.width / 2f - 22f,
                            size.height * 0.30f
                        ),
                        size = androidx.compose.ui.geometry.Size(44f, size.height * 0.38f)
                    )
                    drawCircle(
                        color = Color.Black,
                        radius = 26f,
                        center = androidx.compose.ui.geometry.Offset(
                            size.width / 2f,
                            size.height * 0.84f
                        )
                    )
                }
            }
        } else {
            var visible by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                while (true) {
                    delay(600)
                    visible = !visible
                }
            }

            if (visible) {
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .background(
                            Color.Green,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
            }
        }
    }
}