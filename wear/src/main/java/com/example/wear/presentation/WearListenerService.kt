package com.example.wear.presentation

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.wearable.MessageEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Canonical wearable session state shared between the listener service, activity, and UI.
 */
data class SessionData(
    val progress: Float = 0f,
    val level: Int = 1,
    val paused: Boolean = false,
    val isStopped: Boolean = false,
    val difficulty: String = "JUST VIBING",
    val goalType: String = "DISTANCE",
    val goalValue: String = "1 KILOMETER",
    val targetSteps: Int = 1,
    val vibrationEnabled: Boolean = true,
    val voiceoverEnabled: Boolean = true,
    val personalBestDistanceKm: Float = 0f,
    val personalBestTimeSeconds: Int = 0,
    val needsSpeedUp: Boolean = false
)

/**
 * State holder for the active workout running on the watch.
 */
object WearSessionRepository {
    private val _session = MutableStateFlow(SessionData())
    val session: StateFlow<SessionData> = _session.asStateFlow()

    private val _resetSteps = MutableStateFlow(0L)
    val resetSteps: StateFlow<Long> = _resetSteps.asStateFlow()

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    fun update(data: SessionData) { _session.value = data }
    fun triggerStepReset() { _resetSteps.value = System.currentTimeMillis() }
    fun setSessionActive(active: Boolean) { _sessionActive.value = active }
}

/**
 * Background wearable listener that accepts session commands from the phone and updates
 * the shared watch session state.
 */
class WearListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        android.util.Log.d("WearDebug", "✓ Mensagem recebida no serviço: $path")

        try {
            val json = org.json.JSONObject(String(messageEvent.data, Charsets.UTF_8))

            when (path) {
                "/session_start" -> {
                    val newGoalType = json.optString("goalType", "DISTANCE")
                    val current = WearSessionRepository.session.value
                    if (newGoalType != current.goalType) WearSessionRepository.triggerStepReset()
                    WearSessionRepository.triggerStepReset()
                    WearSessionRepository.update(SessionData(
                        goalType = newGoalType,
                        goalValue = json.optString("goalValue", "5 KILOMETERS"),
                        difficulty = json.optString("difficulty", "JUST VIBING"),
                        targetSteps = json.optInt("targetSteps", 1).coerceAtLeast(1),
                        vibrationEnabled = json.optBoolean("vibrationEnabled", true),
                        voiceoverEnabled = json.optBoolean("voiceoverEnabled", true),
                        personalBestDistanceKm = json.optDouble("personalBestDistanceKm", 0.0).toFloat(),
                        personalBestTimeSeconds = json.optInt("personalBestTimeSeconds", 0)
                    ))
                    WearSessionRepository.setSessionActive(true)
                }
                "/watch_command" -> {
                    val cmd = json.optString("command", "")
                    when (cmd) {
                        "pause" -> WearSessionRepository.update(
                            WearSessionRepository.session.value.copy(paused = true)
                        )
                        "resume" -> WearSessionRepository.update(
                            WearSessionRepository.session.value.copy(paused = false)
                        )
                    }
                }
                "/session_end" -> {
                    WearSessionRepository.setSessionActive(false)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WearDebug", "Erro ao processar mensagem: ${e.message}")
            if (path == "/session_start") {
                WearSessionRepository.triggerStepReset()
                WearSessionRepository.setSessionActive(true)
            }
        }
    }

    private fun vibrateForEvent(type: String) {
        val pattern = when (type) {
            "halfway"          -> longArrayOf(0, 120)
            "level_complete"   -> longArrayOf(0, 150, 100, 150)
            "stop_warning"     -> longArrayOf(0, 500)
            "session_complete" -> longArrayOf(0, 150, 80, 150, 80, 350)
            else               -> longArrayOf(0, 100)
        }
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}
internal fun String.extractNumber(): Int = substringBefore(" ").toIntOrNull() ?: 1
