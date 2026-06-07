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

object WatchDataRepository {
    private val _progress = MutableStateFlow(WatchProgress())
    val progress: StateFlow<WatchProgress> = _progress.asStateFlow()

    private val _command = MutableStateFlow(WatchCommand())
    val command: StateFlow<WatchCommand> = _command.asStateFlow()

    fun updateProgress(progress: Float, level: Int, paused: Boolean, difficulty: String, steps: Int) {
        _progress.value = WatchProgress(progress, level, paused, difficulty, steps)
    }

    fun updateCommand(command: String, timestamp: Long) {
        _command.value = WatchCommand(command, timestamp)
    }
}