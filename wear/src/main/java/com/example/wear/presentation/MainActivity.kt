package com.example.wear.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.wear.presentation.screens.WaitingScreen
import com.example.wear.presentation.screens.WatchProgressScreen
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import android.speech.tts.TextToSpeech
import java.util.Locale

class MainActivity : ComponentActivity(), SensorEventListener, MessageClient.OnMessageReceivedListener {

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var initialSteps: Float? = null


    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }


        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 1001)
        }


        setContent {
            val session by WearSessionRepository.session.collectAsState()
            val sessionActive by WearSessionRepository.sessionActive.collectAsState()
            val resetSignal by WearSessionRepository.resetSteps.collectAsState()

            LaunchedEffect(resetSignal) {
                if (resetSignal > 0L) initialSteps = null
            }

            if (!sessionActive) {
                WaitingScreen()
            } else {
                WatchProgressScreen(
                    progress = session.progress,
                    level = session.level,
                    paused = session.paused,
                    isStopped = session.isStopped,
                    difficulty = session.difficulty,
                    onPauseToggle = {
                        val newPaused = !session.paused
                        sendWatchCommand(if (newPaused) "pause" else "resume")
                        sendWatchProgress(session.progress, session.level, newPaused, session.difficulty, 0)
                    },
                    onResume = {
                        sendWatchCommand("resume")
                        sendWatchProgress(session.progress, session.level, false, session.difficulty, 0)
                    },
                    onEndSession = {
                        WearSessionRepository.setSessionActive(false)
                        sendWatchCommand("end_session")
                    },
                    onSpeakRequest = { text ->

                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "end_confirm")
                    }
                )
            }
        }
    }


    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
        stepCounterSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(this)
        sensorManager.unregisterListener(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        android.util.Log.d("WearDebug", "✓ Mensagem recebida: $path")

        try {
            val json = JSONObject(String(messageEvent.data, Charsets.UTF_8))

            when (path) {
                "/session_start" -> {
                    android.util.Log.d("WearDebug", "✓ /session_start recebido")
                    WearSessionRepository.triggerStepReset()
                    WearSessionRepository.setSessionActive(true)
                }
                "/session_progress" -> {
                    android.util.Log.d("WearDebug", "✓ /session_progress recebido")
                    WearSessionRepository.setSessionActive(true)
                    val current = WearSessionRepository.session.value
                    val newGoalType = json.optString("goalType", "DISTANCE")
                    if (newGoalType != current.goalType) WearSessionRepository.triggerStepReset()

                    WearSessionRepository.update(SessionData(
                        progress = json.optDouble("progress", current.progress.toDouble()).toFloat(),
                        level = json.optInt("level", current.level),
                        paused = json.optBoolean("paused", false),
                        isStopped = json.optBoolean("isStopped", false),
                        difficulty = json.optString("difficulty", current.difficulty),
                        goalType = newGoalType,
                        targetSteps = json.optInt("targetSteps", 1).coerceAtLeast(1),
                        vibrationEnabled = json.optBoolean("vibrationEnabled", true)
                    ))
                }
                "/watch_vibration" -> {
                    val type = json.optString("type", "")
                    val enabled = WearSessionRepository.session.value.vibrationEnabled
                    if (enabled && type.isNotEmpty()) vibrateForEvent(type)
                }
            }
        } catch (e: Exception) {
            when (path) {
                "/session_start" -> {
                    WearSessionRepository.triggerStepReset()
                    WearSessionRepository.setSessionActive(true)
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val session = WearSessionRepository.session.value
        if (session.paused) return
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return

        val totalSteps = event.values[0]
        if (initialSteps == null) initialSteps = totalSteps
        val sessionSteps = (totalSteps - (initialSteps ?: totalSteps)).toInt()

        if (session.goalType == "DISTANCE") {
            val progress = (sessionSteps.toFloat() / session.targetSteps).coerceIn(0f, 1f)
            val level = ((progress * 5).toInt() + 1).coerceIn(1, 5)
            sendWatchProgress(progress, level, session.paused, session.difficulty, sessionSteps)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun sendWatchCommand(command: String) {
        val payload = "{\"command\":\"$command\"}".toByteArray(Charsets.UTF_8)
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                nodes.forEach { node ->
                    Wearable.getMessageClient(this).sendMessage(node.id, "/watch_command", payload)
                }
            }
    }

    private fun sendWatchProgress(progress: Float, level: Int, paused: Boolean, difficulty: String, steps: Int) {
        val payload = """{"progress":$progress,"level":$level,"paused":$paused,"difficulty":"$difficulty","steps":$steps}"""
            .toByteArray(Charsets.UTF_8)
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                nodes.forEach { node ->
                    Wearable.getMessageClient(this).sendMessage(node.id, "/watch_progress", payload)
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