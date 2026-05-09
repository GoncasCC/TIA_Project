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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val prefs = remember {
                getSharedPreferences("app_preferences", MODE_PRIVATE)
            }

            val hasSeenGuide = remember {
                prefs.getBoolean("has_seen_guide", false)
            }

            var currentScreen by remember {
                mutableStateOf(if (hasSeenGuide) "menu" else "guide")
            }

            var showReopenGuideMessage by remember {
                mutableStateOf(!hasSeenGuide)
            }

            var menuInstance by remember { mutableStateOf(0) }

            var selectedActivity by remember { mutableStateOf("") }
            var selectedGoalType by remember { mutableStateOf("") }
            var selectedGoalValue by remember { mutableStateOf("") }

            var voiceoverEnabled by remember { mutableStateOf(true) }
            var musicEnabled by remember { mutableStateOf(true) }
            var vibrationEnabled by remember { mutableStateOf(true) }
            var darkModeEnabled by remember { mutableStateOf(true) }

            fun goToMenu() {
                menuInstance++
                currentScreen = "menu"
            }

            fun cancelSetup() {
                selectedActivity = ""
                selectedGoalType = ""
                selectedGoalValue = ""
                goToMenu()
            }

            when (currentScreen) {

                "menu" -> key(menuInstance) {
                    MenuScreen(
                        onStartNewSession = {
                            currentScreen = "activity"
                        },
                        onProgress = {
                            currentScreen = "progress"
                        },
                        onGuide = {
                            showReopenGuideMessage = false
                            currentScreen = "guide"
                        },
                        onOptions = {
                            currentScreen = "options"
                        },
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
                    onBack = {
                        goToMenu()
                    }
                )

                "activity" -> ActivityScreen(
                    voiceoverEnabled = voiceoverEnabled,
                    vibrationEnabled = vibrationEnabled,
                    darkModeEnabled = darkModeEnabled,
                    onNext = { activity ->
                        selectedActivity = activity
                        currentScreen = "goalType"
                    },
                    onCancel = {
                        cancelSetup()
                    }
                )

                "goalType" -> GoalTypeScreen(
                    voiceoverEnabled = voiceoverEnabled,
                    vibrationEnabled = vibrationEnabled,
                    darkModeEnabled = darkModeEnabled,
                    onNext = { goalType ->
                        selectedGoalType = goalType
                        currentScreen = "goalValue"
                    },
                    onBack = {
                        currentScreen = "activity"
                    },
                    onCancel = {
                        cancelSetup()
                    }
                )

                "goalValue" -> GoalValueScreen(
                    goalType = selectedGoalType,
                    voiceoverEnabled = voiceoverEnabled,
                    vibrationEnabled = vibrationEnabled,
                    darkModeEnabled = darkModeEnabled,
                    onNext = { goalValue ->
                        selectedGoalValue = goalValue
                        currentScreen = "summary"
                    },
                    onBack = {
                        currentScreen = "goalType"
                    },
                    onCancel = {
                        cancelSetup()
                    }
                )

                "summary" -> SummaryScreen(
                    activity = selectedActivity,
                    goalValue = selectedGoalValue,
                    voiceoverEnabled = voiceoverEnabled,
                    vibrationEnabled = vibrationEnabled,
                    darkModeEnabled = darkModeEnabled,
                    onStart = {
                        // TODO: ir para o ecrã da sessão depois
                    },
                    onCancel = {
                        cancelSetup()
                    }
                )

                "progress" -> SimpleTextScreen(
                    text = "Progress",
                    darkModeEnabled = darkModeEnabled,
                    onBack = {
                        goToMenu()
                    }
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
            }
        }
    }
}

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
                    onLongPress = {
                        onBack()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor
        )
    }
}