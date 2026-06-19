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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.wear.presentation.screens.WaitingScreen
import com.example.wear.presentation.screens.WatchProgressScreen
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity(), SensorEventListener, MessageClient.OnMessageReceivedListener {

    companion object {
        private const val WALK_STRIDE_METERS = 0.7f
        private const val RUN_STRIDE_METERS = 1.26f
        private const val CADENCE_WINDOW_MS = 2_000L
        private const val RUN_ON_CADENCE_SPM = 100f
        private const val RUN_OFF_CADENCE_SPM = 95f
        private const val PERSONAL_BEST_WARMUP_MS = 20_000L
        private const val PERSONAL_BEST_REMINDER_MS = 60_000L
        private const val COACHING_GAP_MS = 4_000L
    }

    private enum class PersonalBestFeedbackState {
        ON_TRACK,
        NEEDS_SPEED_UP
    }

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var initialSteps: Float? = null
    private var lastDetectedStepMs: Long = 0L

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private var lastLevel = 1
    private var halfwayAnnounced = false
    private var levelEndAnnounced = false
    private var stopWarningSent = false
    private var lastStepsForStopCheck = 0
    private var sessionSteps = 0
    private var personalBestReachedAnnounced = false
    private var lastPersonalBestFeedbackState: PersonalBestFeedbackState? = null
    private var lastPersonalBestFeedbackMs = 0L
    private var lastCoachingSpeechMs = 0L

    private var accumulatedDistanceMeters = 0f
    private var lastDistanceStepCount = 0
    private var cadenceWindowStartSteps = 0
    private var cadenceWindowStartMs = 0L
    private var smoothedCadenceSpm = 0f
    private var currentStrideMultiplier = WALK_STRIDE_METERS

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stopCheckJob: Job? = null
    private var timerJob: Job? = null
    private var timerStartMs = 0L
    private var timerElapsedBeforePause = 0L

    private var sessionStartMs = 0L
    private var sessionPausedAccumulatedMs = 0L
    private var sessionPauseStartMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isTtsReady = true
            }
        }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                1001
            )
        }

        setContent {
            val session by WearSessionRepository.session.collectAsState()
            val sessionActive by WearSessionRepository.sessionActive.collectAsState()
            val resetSignal by WearSessionRepository.resetSteps.collectAsState()

            androidx.compose.runtime.LaunchedEffect(resetSignal) {
                if (resetSignal > 0L) {
                    resetSessionState()
                }
            }

            if (!sessionActive) {
                WaitingScreen()
            } else {
                WatchProgressScreen(
                    progress = session.progress,
                    level = session.level,
                    sessionStarted = session.sessionStarted,
                    paused = session.paused,
                    isStopped = session.isStopped,
                    difficulty = session.difficulty,
                    needsSpeedUp = session.needsSpeedUp,
                    introTitle = session.introTitle,
                    introValue = session.introValue,
                    vibrationEnabled = session.vibrationEnabled,
                    onPauseToggle = {
                        if (session.sessionStarted) {
                            val newPaused = !session.paused
                            WearSessionRepository.update(session.copy(paused = newPaused))
                            sendCommandToPhone(if (newPaused) "pause" else "resume")
                        }
                    },
                    onResume = {
                        if (session.sessionStarted) {
                            WearSessionRepository.update(session.copy(paused = false))
                            sendCommandToPhone("resume")
                            speakDirect("Returning to session.")
                        }
                    },
                    onEndSession = {
                        speakDirect("Finishing session.")
                        sendSessionResult(
                            distanceMeters = accumulatedDistanceMeters,
                            elapsedSeconds = elapsedSecondsForResult(session),
                            endedEarly = true,
                            isNewPersonalBest = isNewPersonalBestAtCompletion(
                                session = session,
                                distanceMeters = accumulatedDistanceMeters,
                                elapsedSeconds = elapsedSecondsForResult(session),
                                endedEarly = true
                            )
                        )
                        WearSessionRepository.setSessionActive(false)
                    },
                    onSpeakRequest = { text -> speakDirect(text) },
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
                    val newSession = SessionData(
                        goalType = newGoalType,
                        goalValue = json.optString("goalValue", "1 KILOMETER"),
                        difficulty = json.optString("difficulty", "JUST VIBING"),
                        targetSteps = json.optInt("targetSteps", 1).coerceAtLeast(1),
                        vibrationEnabled = json.optBoolean("vibrationEnabled", true),
                        voiceoverEnabled = json.optBoolean("voiceoverEnabled", true),
                        personalBestDistanceKm = json.optDouble("personalBestDistanceKm", 0.0).toFloat(),
                        personalBestTimeSeconds = json.optInt("personalBestTimeSeconds", 0),
                        introTitle = json.optString("introTitle", ""),
                        introValue = json.optString("introValue", "")
                    )
                    WearSessionRepository.triggerStepReset()
                    WearSessionRepository.update(newSession)
                    WearSessionRepository.setSessionActive(true)
                    resetSessionState()
                }

                "/session_go" -> {
                    val current = WearSessionRepository.session.value
                    if (current.sessionStarted) return

                    WearSessionRepository.update(
                        current.copy(
                            sessionStarted = true,
                            introTitle = "",
                            introValue = ""
                        )
                    )

                    sessionStartMs = System.currentTimeMillis()
                    sessionPausedAccumulatedMs = 0L
                    sessionPauseStartMs = 0L
                    startStopCheckLoop()

                    if (current.goalType == "TIME") {
                        startTimeGoalTimer(current.goalValue)
                    }
                }

                "/watch_command" -> {
                    val session = WearSessionRepository.session.value
                    if (!session.sessionStarted) return

                    when (json.optString("command", "")) {
                        "pause" -> {
                            WearSessionRepository.update(session.copy(paused = true))
                            if (timerStartMs > 0L) {
                                timerElapsedBeforePause += System.currentTimeMillis() - timerStartMs
                            }
                            sessionPauseStartMs = System.currentTimeMillis()
                        }

                        "resume" -> {
                            WearSessionRepository.update(session.copy(paused = false))
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
        if (!session.sessionStarted || session.paused) return

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
        updateDistanceAndCadence(System.currentTimeMillis())

        val progress: Float
        val level: Int
        val levelProgress: Float

        if (session.goalType == "DISTANCE") {
            val targetDistanceMeters = goalValueToDistanceMeters(session.goalValue).toFloat()
            val metersPerCheckpoint = 200f
            val totalLevels = (targetDistanceMeters / metersPerCheckpoint).toInt().coerceAtLeast(1)
            progress = (accumulatedDistanceMeters / targetDistanceMeters).coerceIn(0f, 1f)
            level = (accumulatedDistanceMeters / metersPerCheckpoint).toInt()
                .plus(1)
                .coerceIn(1, totalLevels)
            levelProgress = ((accumulatedDistanceMeters % metersPerCheckpoint) / metersPerCheckpoint)
                .coerceIn(0f, 1f)
        } else {
            progress = session.progress
            level = session.level
            levelProgress = 0f
        }

        val updatedSession = session.copy(progress = progress, level = level)
        WearSessionRepository.update(updatedSession)

        if (level != lastLevel) {
            lastLevel = level
            halfwayAnnounced = false
            levelEndAnnounced = false
            sendLevelChanged(level)
        }

        if (updatedSession.goalType == "DISTANCE" && updatedSession.difficulty != "JUST VIBING") {
            handleLevelFeedback(levelProgress, level)
        }

        if (updatedSession.difficulty == "PUSHING LIMITS") {
            handlePersonalBestFeedback(updatedSession)
        }

        if (progress >= 1f) {
            onSessionComplete(updatedSession)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateDistanceAndCadence(now: Long) {
        if (cadenceWindowStartMs == 0L) {
            cadenceWindowStartMs = now
            cadenceWindowStartSteps = sessionSteps
            lastDistanceStepCount = sessionSteps
            return
        }

        val deltaSteps = sessionSteps - lastDistanceStepCount
        if (deltaSteps > 0) {
            accumulatedDistanceMeters += deltaSteps * currentStrideMultiplier
            lastDistanceStepCount = sessionSteps
        }

        val windowMs = now - cadenceWindowStartMs
        if (windowMs >= CADENCE_WINDOW_MS) {
            val windowSteps = sessionSteps - cadenceWindowStartSteps
            val instantSpm = windowSteps * 60_000f / windowMs
            smoothedCadenceSpm =
                if (smoothedCadenceSpm == 0f) instantSpm
                else smoothedCadenceSpm * 0.5f + instantSpm * 0.5f

            currentStrideMultiplier = when {
                smoothedCadenceSpm >= RUN_ON_CADENCE_SPM -> RUN_STRIDE_METERS
                smoothedCadenceSpm <= RUN_OFF_CADENCE_SPM -> WALK_STRIDE_METERS
                else -> currentStrideMultiplier
            }

            cadenceWindowStartMs = now
            cadenceWindowStartSteps = sessionSteps
        }
    }

    private fun handleLevelFeedback(levelProgress: Float, level: Int) {
        if (!halfwayAnnounced && levelProgress >= 0.5f) {
            halfwayAnnounced = true
            announceCoaching("Halfway through stage $level.", "halfway")
        }
        if (!levelEndAnnounced && levelProgress >= 0.98f) {
            levelEndAnnounced = true
            announceCoaching("End of stage $level.", "level_complete")
        }
    }

    private fun handlePersonalBestFeedback(session: SessionData) {
        val pbDistanceMeters = session.personalBestDistanceKm * 1000f
        val currentDistanceMeters = accumulatedDistanceMeters

        if (
            session.goalType == "TIME" &&
            !personalBestReachedAnnounced &&
            pbDistanceMeters > 0f &&
            currentDistanceMeters > pbDistanceMeters
        ) {
            personalBestReachedAnnounced = true
            updateNeedsSpeedUp(session, false)
            announceCoaching(
                text = "New personal best! ${currentDistanceMeters.roundToInt()} meters.",
                vibrationType = "new_personal_best",
                force = true
            )
            return
        }

        val state = evaluatePersonalBestFeedback(session) ?: run {
            updateNeedsSpeedUp(session, false)
            return
        }

        updateNeedsSpeedUp(session, state == PersonalBestFeedbackState.NEEDS_SPEED_UP)

        val now = System.currentTimeMillis()
        val shouldAnnounce =
            state != lastPersonalBestFeedbackState ||
                    now - lastPersonalBestFeedbackMs >= PERSONAL_BEST_REMINDER_MS

        if (!shouldAnnounce) return

        lastPersonalBestFeedbackState = state
        lastPersonalBestFeedbackMs = now

        when (state) {
            PersonalBestFeedbackState.ON_TRACK -> {
                announceCoaching("You are on pace to beat your personal best. Keep it up.")
            }

            PersonalBestFeedbackState.NEEDS_SPEED_UP -> {
                announceCoaching(
                    text = "You can still beat your personal best if you speed up a little.",
                    vibrationType = "pace_warning"
                )
            }
        }
    }

    private fun evaluatePersonalBestFeedback(session: SessionData): PersonalBestFeedbackState? {
        val elapsedSeconds = realElapsedSeconds()
        if (elapsedSeconds <= 0) return null
        if (elapsedSeconds * 1000L < PERSONAL_BEST_WARMUP_MS) return null

        return if (session.goalType == "DISTANCE") {
            val personalBestSeconds = session.personalBestTimeSeconds
            val targetDistanceMeters = goalValueToDistanceMeters(session.goalValue).toFloat()
            if (personalBestSeconds <= 0 || targetDistanceMeters <= 0f || accumulatedDistanceMeters <= 0f) {
                null
            } else {
                val projectedFinishSeconds =
                    elapsedSeconds * (targetDistanceMeters / accumulatedDistanceMeters)
                if (projectedFinishSeconds <= personalBestSeconds) {
                    PersonalBestFeedbackState.ON_TRACK
                } else {
                    PersonalBestFeedbackState.NEEDS_SPEED_UP
                }
            }
        } else {
            val personalBestDistanceMeters = session.personalBestDistanceKm * 1000f
            val totalGoalSeconds = session.goalValue.extractNumber() * 60
            if (personalBestDistanceMeters <= 0f || totalGoalSeconds <= 0 || accumulatedDistanceMeters <= 0f) {
                null
            } else {
                val projectedFinishDistanceMeters =
                    (accumulatedDistanceMeters / elapsedSeconds.toFloat()) * totalGoalSeconds
                if (projectedFinishDistanceMeters >= personalBestDistanceMeters) {
                    PersonalBestFeedbackState.ON_TRACK
                } else {
                    PersonalBestFeedbackState.NEEDS_SPEED_UP
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
                if (!WearSessionRepository.sessionActive.value || !session.sessionStarted) break

                if (session.paused || session.difficulty == "JUST VIBING") {
                    if (session.isStopped) {
                        WearSessionRepository.update(session.copy(isStopped = false))
                    }
                    stoppedSinceMs = 0L
                    delay(1_000L)
                    continue
                }

                val recentStepDetected =
                    lastDetectedStepMs > 0L && (System.currentTimeMillis() - lastDetectedStepMs) < 2_000L
                val stepThreshold = 3
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
                        announceCoaching("Let's get moving.", "nudge")
                    }
                } else {
                    val now = System.currentTimeMillis()
                    if (stoppedSinceMs == 0L) stoppedSinceMs = now

                    if (!session.isStopped) {
                        WearSessionRepository.update(session.copy(isStopped = true))
                    }

                    if (!stopWarningSent && (now - stoppedSinceMs) >= 5_000L) {
                        announceCoaching("You stopped. Let's keep going.", "stop_warning")
                        stopWarningSent = true
                    }
                }

                lastStepsForStopCheck = sessionSteps
                delay(1_500L)
            }
        }
    }

    private fun announceCoaching(
        text: String,
        vibrationType: String? = null,
        force: Boolean = false
    ) {
        val shouldSpeak = WearSessionRepository.session.value.voiceoverEnabled && isTtsReady
        if (!force && shouldSpeak && !canSpeakCoaching()) return

        if (vibrationType != null) {
            vibrate(vibrationType)
        }

        if (!shouldSpeak) return

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
        lastCoachingSpeechMs = System.currentTimeMillis()
    }

    private fun canSpeakCoaching(): Boolean {
        val now = System.currentTimeMillis()
        return tts?.isSpeaking != true && now - lastCoachingSpeechMs >= COACHING_GAP_MS
    }

    private fun updateNeedsSpeedUp(session: SessionData, needsSpeedUp: Boolean) {
        if (session.needsSpeedUp != needsSpeedUp) {
            WearSessionRepository.update(session.copy(needsSpeedUp = needsSpeedUp))
        }
    }

    private fun realElapsedSeconds(): Int {
        if (sessionStartMs == 0L) return 0
        val pausedNow =
            if (sessionPauseStartMs > 0L) System.currentTimeMillis() - sessionPauseStartMs else 0L
        val activeMs =
            (System.currentTimeMillis() - sessionStartMs) - sessionPausedAccumulatedMs - pausedNow
        return (activeMs / 1000L).coerceAtLeast(0L).toInt()
    }

    private fun elapsedSecondsForResult(session: SessionData): Int {
        return if (session.goalType == "TIME") {
            val targetSeconds = session.goalValue.extractNumber() * 60
            if (session.sessionStarted) {
                (session.progress * targetSeconds).toInt()
            } else {
                0
            }
        } else {
            realElapsedSeconds()
        }
    }

    private fun isNewPersonalBestAtCompletion(
        session: SessionData,
        distanceMeters: Float,
        elapsedSeconds: Int,
        endedEarly: Boolean
    ): Boolean {
        if (endedEarly) return false

        return if (session.goalType == "DISTANCE") {
            session.personalBestTimeSeconds == 0 || elapsedSeconds < session.personalBestTimeSeconds
        } else {
            val personalBestDistanceMeters = session.personalBestDistanceKm * 1000f
            personalBestDistanceMeters == 0f || distanceMeters > personalBestDistanceMeters
        }
    }

    private fun onSessionComplete(session: SessionData) {
        stopCheckJob?.cancel()
        val distanceMeters = accumulatedDistanceMeters
        val elapsedSeconds = if (session.goalType == "TIME") {
            session.goalValue.extractNumber() * 60
        } else {
            realElapsedSeconds()
        }
        val isNewPersonalBest =
            isNewPersonalBestAtCompletion(session, distanceMeters, elapsedSeconds, endedEarly = false)

        vibrate("session_complete")
        speakDirect("Workout complete.")
        sendSessionResult(
            distanceMeters = distanceMeters,
            elapsedSeconds = elapsedSeconds,
            endedEarly = false,
            isNewPersonalBest = isNewPersonalBest
        )
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

    private fun sendSessionResult(
        distanceMeters: Float,
        elapsedSeconds: Int,
        endedEarly: Boolean,
        isNewPersonalBest: Boolean
    ) {
        val payload =
            """{"distanceMeters":$distanceMeters,"elapsedSeconds":$elapsedSeconds,"endedEarly":$endedEarly,"isNewPersonalBest":$isNewPersonalBest}"""
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

    private fun speakDirect(text: String) {
        if (isTtsReady && WearSessionRepository.session.value.voiceoverEnabled) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
        }
    }

    private fun vibrate(type: String) {
        if (!WearSessionRepository.session.value.vibrationEnabled) return

        val pattern = when (type) {
            "nudge" -> longArrayOf(0, 80)
            "halfway" -> longArrayOf(0, 120)
            "level_complete" -> longArrayOf(0, 150, 100, 150)
            "stop_warning" -> longArrayOf(0, 400, 100, 400, 100, 400)
            "session_complete" -> longArrayOf(0, 150, 80, 150, 80, 350)
            "pace_warning" -> longArrayOf(0, 120, 80, 120)
            "new_personal_best" -> longArrayOf(0, 140, 70, 140, 70, 260)
            else -> longArrayOf(0, 100)
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
        sessionSteps = 0
        personalBestReachedAnnounced = false
        lastPersonalBestFeedbackState = null
        lastPersonalBestFeedbackMs = 0L
        lastCoachingSpeechMs = 0L
        initialSteps = null
        accumulatedDistanceMeters = 0f
        lastDistanceStepCount = 0
        cadenceWindowStartSteps = 0
        cadenceWindowStartMs = 0L
        smoothedCadenceSpm = 0f
        currentStrideMultiplier = WALK_STRIDE_METERS
        stopCheckJob?.cancel()
        timerJob?.cancel()
        timerJob = null
        timerStartMs = 0L
        timerElapsedBeforePause = 0L
        sessionStartMs = 0L
        sessionPausedAccumulatedMs = 0L
        sessionPauseStartMs = 0L
    }

    private fun startTimeGoalTimer(goalValue: String) {
        timerJob?.cancel()
        timerStartMs = System.currentTimeMillis()
        timerElapsedBeforePause = 0L

        val totalMinutes = goalValue.substringBefore(" ").toIntOrNull() ?: 1
        val totalSeconds = totalMinutes * 60L
        val totalLevels = totalMinutes.coerceAtLeast(1).toLong()
        val secondsPerLevel = totalSeconds / totalLevels

        timerJob = scope.launch {
            while (isActive && WearSessionRepository.sessionActive.value) {
                val session = WearSessionRepository.session.value

                if (!session.sessionStarted) {
                    delay(200L)
                    continue
                }

                if (session.paused) {
                    delay(200L)
                    continue
                }

                val elapsedMs = timerElapsedBeforePause + (System.currentTimeMillis() - timerStartMs)
                val elapsedSeconds = (elapsedMs / 1000L).coerceAtMost(totalSeconds)
                val progress = (elapsedSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
                val level = ((elapsedSeconds / secondsPerLevel) + 1)
                    .coerceIn(1, totalLevels)
                    .toInt()

                val updatedSession = session.copy(progress = progress, level = level)
                WearSessionRepository.update(updatedSession)

                if (level != lastLevel) {
                    lastLevel = level
                    halfwayAnnounced = false
                    levelEndAnnounced = false
                    sendLevelChanged(level)
                }

                if (updatedSession.difficulty != "JUST VIBING") {
                    val secondsIntoLevel = elapsedSeconds % secondsPerLevel
                    val levelProgress =
                        (secondsIntoLevel.toFloat() / secondsPerLevel).coerceIn(0f, 1f)
                    handleLevelFeedback(levelProgress, level)
                }

                if (updatedSession.difficulty == "PUSHING LIMITS") {
                    handlePersonalBestFeedback(updatedSession)
                }

                if (progress >= 1f) {
                    onSessionComplete(updatedSession)
                    break
                }

                delay(1_000L)
            }
        }
    }

    private fun goalValueToDistanceMeters(goalValue: String): Int {
        return when {
            goalValue.contains("KILOMETER", ignoreCase = true) -> goalValue.extractNumber() * 1000
            goalValue.contains("METER", ignoreCase = true) -> goalValue.extractNumber()
            else -> goalValue.extractNumber() * 1000
        }
    }
}
