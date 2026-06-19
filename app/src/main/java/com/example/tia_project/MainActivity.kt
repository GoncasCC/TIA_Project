package com.example.tia_project

import android.os.Bundle
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
import com.example.tia_project.screens.*

/**
 * Main phone entry point.
 * It keeps the app's screen state in Compose and wires together setup,
 * training, guide, progress, and summary flows.
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


            var finalDistance by remember { mutableStateOf(0f) }
            var finalTime by remember { mutableStateOf(0) }

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
                        darkModeEnabled = darkModeEnabled
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
                    onFinish = { distance, time ->

                        finalDistance = distance
                        finalTime = time
                        currentScreen = "session_summary"
                    },
                    onCancel = { goToMenu() }
                )


                "session_summary" -> SessionSummaryScreen(
                    distanceKm = finalDistance,
                    timeSeconds = finalTime,
                    voiceoverEnabled = voiceoverEnabled,
                    vibrationEnabled = vibrationEnabled,
                    darkModeEnabled = darkModeEnabled,
                    onBackToMenu = { goToMenu() }
                )
            }
        }
    }
}

/**
 * Minimal fallback screen used for simple text-only states with a long-press back gesture.
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
