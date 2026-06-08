package com.example.wear.presentation

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionData(
    val progress: Float = 0f,
    val level: Int = 1,
    val paused: Boolean = false,
    val isStopped: Boolean = false,
    val difficulty: String = "JUST VIBING",
    val goalType: String = "DISTANCE",
    val targetSteps: Int = 1,
    val vibrationEnabled: Boolean = true
)

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

class WearListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        android.util.Log.d("WearDebug", "onDataChanged chamado com ${dataEvents.count} eventos")

        dataEvents.forEach { event ->
            android.util.Log.d("WearDebug", "evento: type=${event.type} path=${event.dataItem.uri.path}")

            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val path = event.dataItem.uri.path
            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

            when (path) {
                "/session_start" -> {
                    android.util.Log.d("WearDebug", "✓ /session_start recebido no relógio")
                    WearSessionRepository.triggerStepReset()
                    WearSessionRepository.setSessionActive(true)
                }
                "/session_progress" -> {
                    android.util.Log.d("WearDebug", "✓ /session_progress recebido no relógio")
                    WearSessionRepository.setSessionActive(true)
                    val current = WearSessionRepository.session.value
                    val newGoalType = dataMap.getString("goalType") ?: "DISTANCE"
                    if (newGoalType != current.goalType) {
                        WearSessionRepository.triggerStepReset()
                    }
                    WearSessionRepository.update(
                        SessionData(
                            progress = dataMap.getFloat("progress", current.progress),
                            level = dataMap.getInt("level", current.level),
                            paused = dataMap.getBoolean("paused"),
                            difficulty = dataMap.getString("difficulty") ?: current.difficulty,
                            goalType = newGoalType,
                            targetSteps = dataMap.getInt("targetSteps", 1).coerceAtLeast(1),
                            vibrationEnabled = dataMap.getBoolean("vibrationEnabled", true)
                        )
                    )
                }
                "/watch_vibration" -> {
                    val type = dataMap.getString("type") ?: return@forEach
                    val enabled = WearSessionRepository.session.value.vibrationEnabled
                    if (enabled) vibrateForEvent(type)
                }
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