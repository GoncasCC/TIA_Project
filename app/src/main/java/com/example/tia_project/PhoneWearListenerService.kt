package com.example.tia_project

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
                        val timestamp = dataMap.getLong("timestamp")
                        WatchDataRepository.updateCommand(command, timestamp)
                    }

                    "/watch_progress" -> {
                        val progress = dataMap.getFloat("progress")
                        val level = dataMap.getInt("level")
                        val paused = dataMap.getBoolean("paused")
                        val difficulty = dataMap.getString("difficulty") ?: ""
                        val steps = dataMap.getInt("steps", 0)
                        WatchDataRepository.updateProgress(progress, level, paused, difficulty, steps)
                    }
                }
            }
        }
    }
}