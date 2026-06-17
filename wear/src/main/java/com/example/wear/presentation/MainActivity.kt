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
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.wear.presentation.screens.WaitingScreen
import com.example.wear.presentation.screens.WatchProgressScreen
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import java.util.Locale

class MainActivity : ComponentActivity(), SensorEventListener, MessageClient.OnMessageReceivedListener {

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var initialSteps: Float? = null
    private var lastDetectedStepMs: Long = 0L
    private var accelMovementMs: Long = 0L

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private var lastLevel = 1
    private var halfwayAnnounced = false
    private var levelEndAnnounced = false
    private var stopWarningSent = false
    private var lastStepsForStopCheck = 0
    private var personalBestAnnounced = false
    private var lastMotivationMs = 0L
    private var sessionSteps = 0

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stopCheckJob: Job? = null
    private var timerJob: Job? = null
    private var timerStartMs: Long = 0L
    private var timerPausedMs: Long = 0L
    private var timerElapsedBeforePause: Long = 0L

    private var sessionStartMs: Long = 0L
    private var sessionPausedAccumulatedMs: Long = 0L
    private var sessionPauseStartMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isTtsReady = true
            }
        }

        sensorManager       = getSystemService(SENSOR_SERVICE) as SensorManager
        stepCounterSensor   = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

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
                if (resetSignal > 0L) {
                    initialSteps = null
                    sessionSteps = 0
                    lastLevel = 1
                    halfwayAnnounced = false
                    levelEndAnnounced = false
                    stopWarningSent = false
                    lastStepsForStopCheck = 0
                    personalBestAnnounced = false
                    lastMotivationMs = 0L
                }
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
                    needsSpeedUp = session.needsSpeedUp,
                    vibrationEnabled = session.vibrationEnabled,
                    onPauseToggle = {
                        val newPaused = !session.paused
                        WearSessionRepository.update(session.copy(paused = newPaused))
                        sendCommandToPhone(if (newPaused) "pause" else "resume")
                    },
                    onResume = {
                        WearSessionRepository.update(session.copy(paused = false))
                        sendCommandToPhone("resume")
                        speak("Returning to session.")
                    },
                    onEndSession = {
                        speak("Finishing session.")
                        sendSessionResult(
                            distanceMeters = sessionSteps * 0.7f,
                            elapsedSeconds = if (WearSessionRepository.session.value.goalType == "TIME") {
                                val p = session.progress
                                val targetSecs = session.goalValue.extractNumber() * 60
                                (p * targetSecs).toInt()
                            } else {
                                realElapsedSeconds()
                            },
                            endedEarly = true
                        )
                        WearSessionRepository.setSessionActive(false)
                    },
                    onSpeakRequest = { text -> speak(text) },
                    onStopSpeaking = { tts?.stop() }
                )
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
        stepCounterSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometerSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(this)
        sensorManager.unregisterListener(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        try {
            val json = org.json.JSONObject(String(messageEvent.data, Charsets.UTF_8))
            when (messageEvent.path) {
                "/session_start" -> {
                    val newGoalType = json.optString("goalType", "DISTANCE")
                    val current = WearSessionRepository.session.value
                    if (newGoalType != current.goalType) WearSessionRepository.triggerStepReset()
                    WearSessionRepository.triggerStepReset()
                    WearSessionRepository.update(SessionData(
                        goalType                = newGoalType,
                        goalValue               = json.optString("goalValue", "1 KILOMETER"),
                        difficulty              = json.optString("difficulty", "JUST VIBING"),
                        targetSteps             = json.optInt("targetSteps", 1).coerceAtLeast(1),
                        vibrationEnabled        = json.optBoolean("vibrationEnabled", true),
                        voiceoverEnabled        = json.optBoolean("voiceoverEnabled", true),
                        personalBestDistanceKm  = json.optDouble("personalBestDistanceKm", 0.0).toFloat(),
                        personalBestTimeSeconds = json.optInt("personalBestTimeSeconds", 0),
                        activity                = json.optString("activity", "RUNNING")
                    ))
                    WearSessionRepository.setSessionActive(true)
                    resetSessionState()
                    sessionStartMs = System.currentTimeMillis()
                    sessionPausedAccumulatedMs = 0L
                    sessionPauseStartMs = 0L
                    startStopCheckLoop()
                    if (newGoalType == "TIME") {
                        startTimeGoalTimer(json.optString("goalValue", "1 MINUTE"))
                    }
                }
                "/watch_command" -> {
                    val cmd = json.optString("command", "")
                    when (cmd) {
                        "pause"  -> {
                            WearSessionRepository.update(WearSessionRepository.session.value.copy(paused = true))
                            timerElapsedBeforePause += System.currentTimeMillis() - timerStartMs
                            sessionPauseStartMs = System.currentTimeMillis()
                        }
                        "resume" -> {
                            WearSessionRepository.update(WearSessionRepository.session.value.copy(paused = false))
                            timerStartMs = System.currentTimeMillis()
                            if (sessionPauseStartMs > 0L) {
                                sessionPausedAccumulatedMs += System.currentTimeMillis() - sessionPauseStartMs
                                sessionPauseStartMs = 0L
                            }
                        }
                    }
                }
                "/session_end" -> {
                    stopCheckJob?.cancel()
                    timerJob?.cancel()
                    WearSessionRepository.setSessionActive(false)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WearDebug", "Erro onMessageReceived MainActivity: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val session = WearSessionRepository.session.value
        if (!WearSessionRepository.sessionActive.value) return
        if (session.paused) return

        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            if (magnitude > 12.0f) {
                lastDetectedStepMs = System.currentTimeMillis()
            }
            return
        }

        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return

        val totalSteps = event.values[0]
        if (initialSteps == null) initialSteps = totalSteps
        sessionSteps = (totalSteps - (initialSteps ?: totalSteps)).toInt()

        val progress: Float
        val level: Int
        val levelProgress: Float

        if (session.goalType == "DISTANCE") {
            progress = (sessionSteps.toFloat() / session.targetSteps).coerceIn(0f, 1f)
            val targetDistanceMeters = session.goalValue.extractNumber() * 1000
            val metersPerCheckpoint = 200
            val totalLevels = (targetDistanceMeters / metersPerCheckpoint).coerceAtLeast(1)
            val stepsPerLevel = session.targetSteps / totalLevels
            level = if (stepsPerLevel > 0) (sessionSteps / stepsPerLevel + 1).coerceIn(1, totalLevels) else 1
            levelProgress = if (stepsPerLevel > 0) ((sessionSteps % stepsPerLevel).toFloat() / stepsPerLevel).coerceIn(0f, 1f) else 0f
        } else {
            progress = session.progress
            level = session.level
            levelProgress = 0f
        }

        WearSessionRepository.update(session.copy(progress = progress, level = level))

        if (level != lastLevel) {
            lastLevel = level
            halfwayAnnounced = false
            levelEndAnnounced = false
            sendLevelChanged(level)
        }

        if (session.goalType == "DISTANCE" && session.difficulty != "JUST VIBING") {
            handleLevelFeedback(levelProgress, level, session)
        }

        if (session.difficulty == "PUSHING LIMITS") {
            handlePersonalBestFeedback(sessionSteps, session)
        }

        if (progress >= 1f) {
            onSessionComplete(session)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun handleLevelFeedback(levelProgress: Float, level: Int, session: SessionData) {
        if (!halfwayAnnounced && levelProgress >= 0.5f) {
            halfwayAnnounced = true
            vibrate("halfway")
            speak("Halfway through level $level.")
        }
        if (!levelEndAnnounced && levelProgress >= 0.98f) {
            levelEndAnnounced = true
            vibrate("level_complete")
            speak("End of level $level.")
        }
    }

    private fun handlePersonalBestFeedback(steps: Int, session: SessionData) {
        val pbDistanceMeters = session.personalBestDistanceKm * 1000f
        val currentDistanceMeters = steps * 0.7f

        if (!personalBestAnnounced && pbDistanceMeters > 0f && currentDistanceMeters > pbDistanceMeters) {
            personalBestAnnounced = true
            WearSessionRepository.update(session.copy(needsSpeedUp = false))
            speak("New personal best! ${String.format(Locale.US, "%.2f", currentDistanceMeters / 1000f)} kilometers.")
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastMotivationMs < 15_000L) return
        lastMotivationMs = now

        val pbSteps = (pbDistanceMeters / 0.7f).toInt()
        val remainingPbSteps = pbSteps - steps
        val remainingPbDistance = remainingPbSteps * 0.7f

        when {
            currentDistanceMeters >= pbDistanceMeters -> {
                WearSessionRepository.update(session.copy(needsSpeedUp = false))
                speak("You are beating your personal best. Keep going.")
            }
            remainingPbSteps <= 0 -> {
                WearSessionRepository.update(session.copy(needsSpeedUp = false))
                speak("Keep going. Focus on finishing strong.")
            }
            else -> {
                val remainingSessionSteps = session.targetSteps - steps
                if (remainingSessionSteps <= 0) return
                if (remainingPbDistance <= remainingSessionSteps * 0.7f) {
                    WearSessionRepository.update(session.copy(needsSpeedUp = false))
                    speak("You can still beat your personal best. Keep this pace.")
                } else {
                    WearSessionRepository.update(session.copy(needsSpeedUp = true))
                    speak("You can still beat your personal best if you speed up a little.")
                }
            }
        }
    }

    private fun startStopCheckLoop() {
        stopCheckJob?.cancel()
        stopCheckJob = scope.launch {
            var everStarted = false
            var lastNudgeMs = 0L
            var movementStreak = 0
            var stoppedSinceMs = 0L
            stopWarningSent = false
            lastDetectedStepMs = 0L

            delay(5_000L)

            while (isActive) {
                val session = WearSessionRepository.session.value
                if (!WearSessionRepository.sessionActive.value) break
                if (session.paused || session.difficulty == "JUST VIBING") {
                    if (session.isStopped) {
                        WearSessionRepository.update(session.copy(isStopped = false))
                    }
                    stoppedSinceMs = 0L
                    delay(1_000L)
                    continue
                }

                val recentStepDetected = lastDetectedStepMs > 0L &&
                        (System.currentTimeMillis() - lastDetectedStepMs) < 2_000L
                val stepThreshold = if (session.activity.uppercase() == "WALK") 2 else 5
                val hasMoved = recentStepDetected || sessionSteps > lastStepsForStopCheck + stepThreshold

                if (hasMoved) {
                    movementStreak++
                    if (!everStarted && movementStreak >= 1) everStarted = true
                } else {
                    movementStreak = 0
                }

                if (hasMoved && everStarted) {
                    stopWarningSent = false
                    stoppedSinceMs = 0L
                    if (session.isStopped) {
                        WearSessionRepository.update(session.copy(isStopped = false))
                    }
                } else if (!everStarted) {
                    WearSessionRepository.update(session.copy(isStopped = true))
                    val now = System.currentTimeMillis()
                    if (now - lastNudgeMs >= 5_000L) {
                        lastNudgeMs = now
                        vibrate("nudge")
                        val prompt = when (session.activity.uppercase()) {
                            "WALK" -> "Let's start walking."
                            else   -> "Let's start running."
                        }
                        speak(prompt)
                    }
                } else {
                    val now = System.currentTimeMillis()
                    if (stoppedSinceMs == 0L) stoppedSinceMs = now

                    if (!session.isStopped) {
                        WearSessionRepository.update(session.copy(isStopped = true))
                    }

                    if (!stopWarningSent && (now - stoppedSinceMs) >= 5_000L) {
                        vibrate("stop_warning")
                        speak("You stopped. Let's keep going.")
                        stopWarningSent = true
                    }
                }

                lastStepsForStopCheck = sessionSteps

                if (session.difficulty == "PUSHING LIMITS" && everStarted) {
                    handlePersonalBestFeedback(sessionSteps, session)
                }

                delay(1_500L)
            }
        }
    }

    private fun realElapsedSeconds(): Int {
        val pausedNow = if (sessionPauseStartMs > 0L)
            System.currentTimeMillis() - sessionPauseStartMs else 0L
        val activeMs = (System.currentTimeMillis() - sessionStartMs) -
                sessionPausedAccumulatedMs - pausedNow
        return (activeMs / 1000L).coerceAtLeast(0L).toInt()
    }

    private fun onSessionComplete(session: SessionData) {
        stopCheckJob?.cancel()
        vibrate("session_complete")
        speak("Workout complete.")
        val distanceMeters = sessionSteps * 0.7f
        val elapsedSeconds = if (session.goalType == "TIME") {
            session.goalValue.extractNumber() * 60
        } else {
            realElapsedSeconds()
        }
        sendSessionResult(distanceMeters, elapsedSeconds, endedEarly = false)
        WearSessionRepository.setSessionActive(false)
    }

    private fun sendCommandToPhone(command: String) {
        val payload = """{"command":"$command"}""".toByteArray(Charsets.UTF_8)
        sendToPhone("/watch_command", payload)
    }

    private fun sendLevelChanged(level: Int) {
        val payload = """{"level":$level}""".toByteArray(Charsets.UTF_8)
        sendToPhone("/level_changed", payload)
    }

    private fun sendSessionResult(distanceMeters: Float, elapsedSeconds: Int, endedEarly: Boolean) {
        val payload = """{"distanceMeters":$distanceMeters,"elapsedSeconds":$elapsedSeconds,"endedEarly":$endedEarly}"""
            .toByteArray(Charsets.UTF_8)
        sendToPhone("/session_result", payload)
    }

    private fun sendToPhone(path: String, payload: ByteArray) {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                nodes.forEach { node ->
                    Wearable.getMessageClient(this).sendMessage(node.id, path, payload)
                        .addOnSuccessListener { android.util.Log.d("WearDebug", "✓ $path enviado") }
                        .addOnFailureListener { android.util.Log.e("WearDebug", "✗ $path falhou: ${it.message}") }
                }
            }
    }

    private fun speak(text: String) {
        if (isTtsReady && WearSessionRepository.session.value.voiceoverEnabled) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
        }
    }

    private fun vibrate(type: String) {
        if (!WearSessionRepository.session.value.vibrationEnabled) return
        val pattern = when (type) {
            "nudge"            -> longArrayOf(0, 80)
            "halfway"          -> longArrayOf(0, 120)
            "level_complete"   -> longArrayOf(0, 150, 100, 150)
            "stop_warning"     -> longArrayOf(0, 400, 100, 400, 100, 400)
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

    private fun resetSessionState() {
        lastLevel = 1
        halfwayAnnounced = false
        levelEndAnnounced = false
        stopWarningSent = false
        lastStepsForStopCheck = 0
        personalBestAnnounced = false
        lastMotivationMs = 0L
        sessionSteps = 0
        initialSteps = null
        timerJob?.cancel()
        timerJob = null
        timerStartMs = 0L
        timerPausedMs = 0L
        timerElapsedBeforePause = 0L
        sessionStartMs = 0L
        sessionPausedAccumulatedMs = 0L
        sessionPauseStartMs = 0L
    }

    private fun startTimeGoalTimer(goalValue: String) {
        timerJob?.cancel()
        timerStartMs = System.currentTimeMillis()
        timerPausedMs = 0L
        timerElapsedBeforePause = 0L

        val totalMinutes = goalValue.substringBefore(" ").toIntOrNull() ?: 1
        val totalSeconds = totalMinutes * 60L
        val totalLevels = totalMinutes.coerceAtLeast(1).toLong()
        val secondsPerLevel = totalSeconds / totalLevels

        timerJob = scope.launch {
            while (isActive && WearSessionRepository.sessionActive.value) {
                val session = WearSessionRepository.session.value

                if (session.paused) {
                    delay(200L)
                    continue
                }

                val elapsedMs = timerElapsedBeforePause + (System.currentTimeMillis() - timerStartMs)
                val elapsedSeconds = (elapsedMs / 1000L).coerceAtMost(totalSeconds)
                val progress = (elapsedSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
                val level = ((elapsedSeconds / secondsPerLevel) + 1)
                    .coerceIn(1, totalLevels).toInt()

                WearSessionRepository.update(session.copy(progress = progress, level = level))

                if (level != lastLevel) {
                    lastLevel = level
                    halfwayAnnounced = false
                    levelEndAnnounced = false
                    sendLevelChanged(level)
                }

                if (session.difficulty != "JUST VIBING") {
                    val secondsIntoLevel = elapsedSeconds % secondsPerLevel
                    val levelProgress = (secondsIntoLevel.toFloat() / secondsPerLevel).coerceIn(0f, 1f)
                    handleLevelFeedback(levelProgress, level, session)
                }

                if (progress >= 1f) {
                    onSessionComplete(session)
                    break
                }

                delay(1000L)
            }
        }
    }
}