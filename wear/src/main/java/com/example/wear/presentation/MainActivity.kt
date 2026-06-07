package com.example.wear.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.wear.presentation.screens.WatchProgressScreen
import com.example.wear.presentation.screens.WaitingScreen
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var initialSteps: Float? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                    difficulty = session.difficulty,
                    onPauseToggle = {
                        val newPaused = !session.paused
                        sendWatchCommand(if (newPaused) "pause" else "resume")
                        sendWatchProgress(
                            progress = session.progress,
                            level = session.level,
                            paused = newPaused,
                            difficulty = session.difficulty,
                            steps = 0
                        )
                    },
                    onEndSession = {
                        WearSessionRepository.setSessionActive(false)
                        sendWatchCommand("end_session")
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        stepCounterSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
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
        val request = PutDataMapRequest.create("/watch_command").apply {
            dataMap.putString("command", command)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(this).putDataItem(request)
    }

    private fun sendWatchProgress(
        progress: Float, level: Int, paused: Boolean,
        difficulty: String, steps: Int
    ) {
        val request = PutDataMapRequest.create("/watch_progress").apply {
            dataMap.putFloat("progress", progress)
            dataMap.putInt("level", level)
            dataMap.putBoolean("paused", paused)
            dataMap.putString("difficulty", difficulty)
            dataMap.putInt("steps", steps)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(this).putDataItem(request)
    }
}