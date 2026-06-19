package com.example.tia_project

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of the latest live progress values received from the watch.
 */
data class WatchProgress(
    val progress: Float = 0f,
    val level: Int = 1,
    val paused: Boolean = false,
    val difficulty: String = "",
    val steps: Int = 0
)

/**
 * Last command emitted by the watch, together with a timestamp so the UI can ignore stale events.
 */
data class WatchCommand(
    val command: String = "",
    val timestamp: Long = 0L
)

/**
 * Final result reported by the watch when a session ends.
 */
data class WatchSessionResult(
    val distanceMeters: Float = 0f,
    val elapsedSeconds: Int = 0,
    val endedEarly: Boolean = false,
    val timestamp: Long = 0L
)

/**
 * In-memory bridge between the wearable listener service and the phone UI.
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

    /**
     * Publishes the latest live session snapshot sent from the watch.
     */
    fun updateProgress(progress: Float, level: Int, paused: Boolean, difficulty: String, steps: Int) {
        _progress.value = WatchProgress(progress, level, paused, difficulty, steps)
    }

    /**
     * Records a new wearable command event such as pause or resume.
     */
    fun updateCommand(command: String, timestamp: Long) {
        _command.value = WatchCommand(command, timestamp)
    }

    /**
     * Stores the session result so the training screen can finish and navigate.
     */
    fun updateResult(distanceMeters: Float, elapsedSeconds: Int, endedEarly: Boolean) {
        _result.value = WatchSessionResult(distanceMeters, elapsedSeconds, endedEarly, System.currentTimeMillis())
    }

    /**
     * Clears transient wearable state when a session starts or finishes.
     */
    fun clearSessionState() {
        _command.value = WatchCommand()
        _progress.value = WatchProgress()
        _level.value = 0
        _result.value = WatchSessionResult()
    }

    /**
     * Updates the current session stage reported by the watch.
     */
    fun updateLevel(level: Int) {
        _level.value = level
    }
}
