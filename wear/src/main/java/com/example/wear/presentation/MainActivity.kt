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
import com.example.wear.presentation.screens.WatchProgressScreen
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity(), SensorEventListener, DataClient.OnDataChangedListener {

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null

    private var initialSteps: Float? = null

    private var progressState by mutableStateOf(0f)
    private var levelState by mutableStateOf(1)
    private var pausedState by mutableStateOf(false)
    private var difficultyState by mutableStateOf("JUST VIBING")

    private var goalTypeState by mutableStateOf("DISTANCE")
    private var targetSteps by mutableStateOf(1)
    private var sessionStepsState by mutableStateOf(0)

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

        Wearable.getDataClient(this).addListener(this)

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

        Wearable.getDataClient(this).removeListener(this)
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

        sessionStepsState = sessionSteps

        if (goalTypeState == "DISTANCE") {
            progressState = (sessionSteps.toFloat() / targetSteps).coerceIn(0f, 1f)
            levelState = ((progressState * 5).toInt() + 1).coerceIn(1, 5)
        }

        sendWatchProgress()
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
            dataMap.putInt("steps", sessionStepsState)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(this).putDataItem(request)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

                when (path) {
                    "/session_progress" -> {
                        difficultyState = dataMap.getString("difficulty") ?: "JUST VIBING"
                        pausedState = dataMap.getBoolean("paused")
                        vibrationEnabledState = dataMap.getBoolean("vibrationEnabled", true)

                        goalTypeState = dataMap.getString("goalType") ?: "DISTANCE"
                        targetSteps = dataMap.getInt("targetSteps", 1).coerceAtLeast(1)

                        if (goalTypeState == "TIME") {
                            progressState = dataMap.getFloat("progress", progressState)
                            levelState = dataMap.getInt("level", levelState)
                        }
                    }

                    "/watch_vibration" -> {
                        val type = dataMap.getString("type") ?: return@forEach
                        vibrateForEvent(type)
                    }
                }
            }
        }
    }

    private fun vibrateForEvent(type: String) {
        if (!vibrationEnabledState) return

        val pattern = when (type) {
            "halfway" -> longArrayOf(0, 120)
            "level_complete" -> longArrayOf(0, 150, 100, 150)
            "stop_warning" -> longArrayOf(0, 500)
            "session_complete" -> longArrayOf(0, 150, 80, 150, 80, 350)
            else -> longArrayOf(0, 100)
        }

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
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