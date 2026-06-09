package com.example.tia_project

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WatchProgress(
    val progress: Float = 0f,
    val level: Int = 1,
    val paused: Boolean = false,
    val difficulty: String = "",
    val steps: Int = 0
)

data class WatchCommand(
    val command: String = "",
    val timestamp: Long = 0L
)

data class WatchSessionResult(
    val distanceMeters: Float = 0f,
    val elapsedSeconds: Int = 0,
    val endedEarly: Boolean = false,
    val timestamp: Long = 0L
)

object WatchDataRepository {
    private val _progress = MutableStateFlow(WatchProgress())
    val progress: StateFlow<WatchProgress> = _progress.asStateFlow()

    private val _command = MutableStateFlow(WatchCommand())
    val command: StateFlow<WatchCommand> = _command.asStateFlow()

    private val _result = MutableStateFlow(WatchSessionResult())
    val result: StateFlow<WatchSessionResult> = _result.asStateFlow()

    // Nível atual enviado pelo watch (para trocar música)
    private val _level = MutableStateFlow(0)
    val level: StateFlow<Int> = _level.asStateFlow()

    fun updateProgress(progress: Float, level: Int, paused: Boolean, difficulty: String, steps: Int) {
        _progress.value = WatchProgress(progress, level, paused, difficulty, steps)
    }

    fun updateCommand(command: String, timestamp: Long) {
        _command.value = WatchCommand(command, timestamp)
    }

    fun updateResult(distanceMeters: Float, elapsedSeconds: Int, endedEarly: Boolean) {
        _result.value = WatchSessionResult(distanceMeters, elapsedSeconds, endedEarly, System.currentTimeMillis())
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