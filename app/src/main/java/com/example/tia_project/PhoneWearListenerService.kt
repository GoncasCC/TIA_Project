package com.example.tia_project

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class PhoneWearListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: com.google.android.gms.wearable.DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

                when (path) {
                    "/watch_command" -> {
                        val command = dataMap.getString("command") ?: return@forEach
                        saveWatchCommand(command)
                    }

                    "/watch_progress" -> {
                        saveWatchProgress(
                            progress = dataMap.getFloat("progress"),
                            level = dataMap.getInt("level"),
                            paused = dataMap.getBoolean("paused"),
                            difficulty = dataMap.getString("difficulty") ?: "JUST VIBING"
                        )
                    }
                }
            }
        }
    }

    private fun saveWatchCommand(command: String) {
        getSharedPreferences("watch_commands", Context.MODE_PRIVATE)
            .edit()
            .putString("command", command)
            .putLong("timestamp", System.currentTimeMillis())
            .apply()
    }

    private fun saveWatchProgress(
        progress: Float,
        level: Int,
        paused: Boolean,
        difficulty: String
    ) {
        getSharedPreferences("watch_progress", Context.MODE_PRIVATE)
            .edit()
            .putFloat("progress", progress)
            .putInt("level", level)
            .putBoolean("paused", paused)
            .putString("difficulty", difficulty)
            .putLong("timestamp", System.currentTimeMillis())
            .apply()
    }
}