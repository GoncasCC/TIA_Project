package com.example.tia_project

import android.content.Context
import android.os.Bundle
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.tia_project.screens.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Root phone activity.
 *
 * It keeps the top-level app state in Compose, switches between screens,
 * and carries the session result into the final summary screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        setContent {
            val prefs = remember {
                getSharedPreferences("app_preferences", MODE_PRIVATE)
            }

            val hasSeenGuide = remember {
                prefs.getBoolean("has_seen_guide", false)
            }

            val normalStartScreen = if (hasSeenGuide) "menu" else "guide"

            var currentScreen by remember {
                mutableStateOf(normalStartScreen)
            }

            var showReopenGuideMessage by remember {
                mutableStateOf(!hasSeenGuide)
            }

            var menuInstance by remember { mutableStateOf(0) }


            var selectedGoalType by remember { mutableStateOf("") }
            var selectedGoalValue by remember { mutableStateOf("") }
            var selectedDifficulty by remember { mutableStateOf("") }

            var voiceoverEnabled by remember { mutableStateOf(true) }
            var musicEnabled by remember { mutableStateOf(true) }
            var vibrationEnabled by remember { mutableStateOf(true) }
            var darkModeEnabled by remember { mutableStateOf(true) }


            var finalDistanceMeters by remember { mutableStateOf(0f) }
            var finalTime by remember { mutableStateOf(0) }
            var finalIsNewPersonalBest by remember { mutableStateOf(false) }
            var showExitPrompt by remember { mutableStateOf(false) }
            val uiScope = rememberCoroutineScope()

            val context = LocalContext.current
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

            var appTts by remember { mutableStateOf<TextToSpeech?>(null) }
            var isAppTtsReady by remember { mutableStateOf(false) }

            fun vibrateExitPrompt() {
                if (!vibrationEnabled) return

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 90, 70, 90), -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 90, 70, 90), -1)
                }
            }

            fun speakAppMessage(text: String, flush: Boolean = true) {
                if (voiceoverEnabled && isAppTtsReady) {
                    val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    appTts?.speak(text, queueMode, null, text.hashCode().toString())
                }
            }


            var hasAnnouncedEntryGreeting by remember { mutableStateOf(false) }

            LaunchedEffect(currentScreen, isAppTtsReady) {
                if (currentScreen == "menu" && isAppTtsReady && hasSeenGuide && !hasAnnouncedEntryGreeting) {
                    hasAnnouncedEntryGreeting = true
                    speakAppMessage("Welcome back.", flush = true)
                    speakAppMessage("You are currently in the menu.", flush = false)
                    speakAppMessage("Let's start a new session.", flush = false)
                }
            }

            DisposableEffect(Unit) {
                val textToSpeech = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        isAppTtsReady = true
                    }
                }
                appTts = textToSpeech
                textToSpeech.language = Locale.US

                onDispose {
                    textToSpeech.stop()
                    textToSpeech.shutdown()
                }
            }

            fun goToMenu() {
                menuInstance++
                currentScreen = "menu"
            }

            fun cancelSetup() {
                selectedGoalType = ""
                selectedGoalValue = ""
                selectedDifficulty = ""
                goToMenu()
            }

            fun requestExitPrompt() {
                showExitPrompt = true
                vibrateExitPrompt()
                speakAppMessage("Leaving app? Tap once to stay. Double tap to exit.")
            }

            fun stayInApp() {
                showExitPrompt = false
                vibrateExitPrompt()
                speakAppMessage("Staying in app.")
            }

            fun exitApp() {
                showExitPrompt = false
                uiScope.launch {
                    vibrateExitPrompt()
                    speakAppMessage("Leaving app.")
                    delay(if (voiceoverEnabled && isAppTtsReady) 700L else 250L)
                    finish()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (darkModeEnabled) Color.Black else Color.White)
            ) {
                when (currentScreen) {

                    "menu" -> key(menuInstance) {
                        MenuScreen(
                            onStartNewSession = { currentScreen = "goalType" },
                            onProgress = { currentScreen = "progress" },
                            onGuide = {
                                showReopenGuideMessage = false
                                currentScreen = "guide"
                            },
                            onOptions = { currentScreen = "options" },
                            voiceoverEnabled = voiceoverEnabled,
                            vibrationEnabled = vibrationEnabled,
                            darkModeEnabled = darkModeEnabled,
                            onExitAppRequest = { requestExitPrompt() },
                            skipInitialMenuAnnouncement = hasSeenGuide && hasAnnouncedEntryGreeting
                        )
                    }

                    "options" -> OptionsScreen(
                        voiceoverEnabled = voiceoverEnabled,
                        musicEnabled = musicEnabled,
                        vibrationEnabled = vibrationEnabled,
                        darkModeEnabled = darkModeEnabled,
                        onVoiceoverChange = { voiceoverEnabled = it },
                        onMusicChange = { musicEnabled = it },
                        onVibrationChange = { vibrationEnabled = it },
                        onDarkModeChange = { darkModeEnabled = it },
                        onBack = { goToMenu() }
                    )

                    "goalType" -> GoalTypeScreen(
                        voiceoverEnabled = voiceoverEnabled,
                        vibrationEnabled = vibrationEnabled,
                        darkModeEnabled = darkModeEnabled,
                        onNext = { goalType ->
                            selectedGoalType = goalType
                            currentScreen = "goalValue"
                        },
                        onCancel = { cancelSetup() }
                    )

                    "goalValue" -> GoalValueScreen(
                        goalType = selectedGoalType,
                        voiceoverEnabled = voiceoverEnabled,
                        vibrationEnabled = vibrationEnabled,
                        darkModeEnabled = darkModeEnabled,
                        onNext = { goalValue ->
                            selectedGoalValue = goalValue
                            currentScreen = "difficulty"
                        },
                        onBack = { currentScreen = "goalType" },
                        onCancel = { cancelSetup() }
                    )

                    "difficulty" -> DifficultyScreen(
                        voiceoverEnabled = voiceoverEnabled,
                        vibrationEnabled = vibrationEnabled,
                        darkModeEnabled = darkModeEnabled,
                        onNext = { difficulty ->
                            selectedDifficulty = difficulty
                            currentScreen = "summary"
                        },
                        onBack = { currentScreen = "goalValue" },
                        onCancel = { cancelSetup() }
                    )

                    "summary" -> SummaryScreen(
                        goalValue = selectedGoalValue,
                        difficulty = selectedDifficulty,
                        voiceoverEnabled = voiceoverEnabled,
                        vibrationEnabled = vibrationEnabled,
                        darkModeEnabled = darkModeEnabled,
                        onStart = {
                            currentScreen = "training"
                        },
                        onCancel = { cancelSetup() }
                    )

                    "progress" -> ProgressScreen(
                        voiceoverEnabled = voiceoverEnabled,
                        vibrationEnabled = vibrationEnabled,
                        darkModeEnabled = darkModeEnabled,
                        onBack = { goToMenu() }
                    )

                    "guide" -> GuideScreen(
                        voiceoverEnabled = voiceoverEnabled,
                        vibrationEnabled = vibrationEnabled,
                        darkModeEnabled = darkModeEnabled,
                        showReopenGuideMessage = showReopenGuideMessage,
                        onFinish = {
                            prefs.edit()
                                .putBoolean("has_seen_guide", true)
                                .apply()
                            showReopenGuideMessage = false
                            goToMenu()
                        }
                    )

                    "training" -> TrainingSession(
                        goalType = selectedGoalType,
                        goalValue = selectedGoalValue,
                        difficulty = selectedDifficulty,
                        voiceoverEnabled = voiceoverEnabled,
                        musicEnabled = musicEnabled,
                        vibrationEnabled = vibrationEnabled,
                        darkModeEnabled = darkModeEnabled,
                        onFinish = { distanceMeters, time, isNewPersonalBest ->
                            finalDistanceMeters = distanceMeters
                            finalTime = time
                            finalIsNewPersonalBest = isNewPersonalBest
                            currentScreen = "session_summary"
                        },
                        onCancel = { goToMenu() }
                    )


                    "session_summary" -> SessionSummaryScreen(
                        goalType = selectedGoalType,
                        goalValue = selectedGoalValue,
                        difficulty = selectedDifficulty,
                        distanceMeters = finalDistanceMeters,
                        timeSeconds = finalTime,
                        isNewPersonalBest = finalIsNewPersonalBest,
                        voiceoverEnabled = voiceoverEnabled,
                        vibrationEnabled = vibrationEnabled,
                        darkModeEnabled = darkModeEnabled,
                        onBackToMenu = { goToMenu() }
                    )
                }

                if (showExitPrompt) {
                    ExitAppOverlay(
                        darkModeEnabled = darkModeEnabled,
                        onStay = { stayInApp() },
                        onExit = { exitApp() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExitAppOverlay(
    darkModeEnabled: Boolean,
    onStay: () -> Unit,
    onExit: () -> Unit
) {
    val backgroundColor = if (darkModeEnabled) Color.Black else Color.White
    val textColor = if (darkModeEnabled) Color.White else Color.Black
    val accentColor = if (darkModeEnabled) Color(0xFFFFCC00) else Color(0xFFB71C1C)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onStay() },
                    onDoubleTap = { onExit() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "LEAVING APP?",
            color = accentColor,
            fontSize = 50.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Minimal full-screen fallback used by a few simple flows.
 *
 * A long press triggers the provided back action.
 */
@Composable
fun SimpleTextScreen(
    text: String,
    darkModeEnabled: Boolean,
    onBack: () -> Unit
) {
    val backgroundColor = if (darkModeEnabled) Color.Black else Color.White
    val textColor = if (darkModeEnabled) Color.White else Color.Black

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onBack() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = textColor)
    }
}