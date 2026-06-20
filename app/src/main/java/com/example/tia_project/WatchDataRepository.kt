package com.example.tia_project

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live progress snapshot pushed from the watch during an active session. */
data class WatchProgress(
    val progress: Float = 0f,
    val level: Int = 1,
    val paused: Boolean = false,
    val difficulty: String = "",
    val steps: Int = 0
)

/** Small command channel used to mirror pause/resume actions from the watch. */
data class WatchCommand(
    val command: String = "",
    val timestamp: Long = 0L
)

/** Final session payload sent back by the watch when a workout ends. */
data class WatchSessionResult(
    val distanceMeters: Float = 0f,
    val elapsedSeconds: Int = 0,
    val endedEarly: Boolean = false,
    val isNewPersonalBest: Boolean = false,
    val timestamp: Long = 0L
)

/**
 * In-memory bridge between the wearable listener service and the phone UI.
 *
 * Screens collect these flows so they can react to watch events without
 * talking to the Wear APIs directly.
 */
object WatchDataRepository {
    private val _progress = MutableStateFlow(WatchProgress())
    val progress: StateFlow<WatchProgress> = _progress.asStateFlow()

    private val _command = MutableStateFlow(WatchCommand())
    val command: StateFlow<WatchCommand> = _command.asStateFlow()

    private val _result = MutableStateFlow(WatchSessionResult())
    val result: StateFlow<WatchSessionResult> = _result.asStateFlow()

    private val _level = MutableStateFlow(0)
    val level: StateFlow<Int> = _level.asStateFlow()

    fun updateProgress(progress: Float, level: Int, paused: Boolean, difficulty: String, steps: Int) {
        _progress.value = WatchProgress(progress, level, paused, difficulty, steps)
    }

    fun updateCommand(command: String, timestamp: Long) {
        _command.value = WatchCommand(command, timestamp)
    }

    fun updateResult(
        distanceMeters: Float,
        elapsedSeconds: Int,
        endedEarly: Boolean,
        isNewPersonalBest: Boolean
    ) {
        _result.value = WatchSessionResult(
            distanceMeters = distanceMeters,
            elapsedSeconds = elapsedSeconds,
            endedEarly = endedEarly,
            isNewPersonalBest = isNewPersonalBest,
            timestamp = System.currentTimeMillis()
        )
    }

    fun clearSessionState() {
        _command.value = WatchCommand()
        _progress.value = WatchProgress()
        _level.value = 0
        _result.value = WatchSessionResult()
    }

    fun updateLevel(level: Int) {
        _level.value = level
    }
}
