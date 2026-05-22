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
import kotlinx.coroutines.delay
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null

    private var initialSteps: Float? = null

    private var progressState by mutableStateOf(0f)
    private var levelState by mutableStateOf(1)
    private var pausedState by mutableStateOf(false)
    private var difficultyState by mutableStateOf("JUST VIBING")

    private val targetSteps = 100

    private var vibrationEnabledState by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                1001
            )
        }

        setContent {
            /////////simulate steps (delete for a real interaction)
            LaunchedEffect(Unit) {

                while (true) {
                    delay(1000)

                    if (!pausedState && progressState < 1f) {
                        progressState += 0.02f
                        levelState =
                            ((progressState * 5).toInt() + 1).coerceAtMost(5)

                        sendWatchProgress()
                    }
                }
            }
            WatchProgressScreen(
                progress = progressState,
                level = levelState,
                paused = pausedState,
                difficulty = difficultyState,
                onPauseToggle = {
                    pausedState = !pausedState
                    sendWatchCommand(if (pausedState) "pause" else "resume")
                    sendWatchProgress()
                },
                onEndSession = {
                    pausedState = true
                    sendWatchProgress()
                    sendWatchCommand("end_session")
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()

        stepCounterSensor?.also { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (pausedState) return
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return

        val totalStepsFromDevice = event.values[0]

        if (initialSteps == null) {
            initialSteps = totalStepsFromDevice
        }

        val sessionSteps =
            (totalStepsFromDevice - (initialSteps ?: totalStepsFromDevice)).toInt()

        progressState = (sessionSteps.toFloat() / targetSteps).coerceIn(0f, 1f)
        levelState = (sessionSteps / 100) + 1
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun sendWatchCommand(command: String) {
        val request = PutDataMapRequest.create("/watch_command").apply {
            dataMap.putString("command", command)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(this).putDataItem(request)
    }
    private fun sendWatchProgress() {
        val request = PutDataMapRequest.create("/watch_progress").apply {
            dataMap.putFloat("progress", progressState)
            dataMap.putInt("level", levelState)
            dataMap.putBoolean("paused", pausedState)
            dataMap.putString("difficulty", difficultyState)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(this).putDataItem(request)
    }
}

