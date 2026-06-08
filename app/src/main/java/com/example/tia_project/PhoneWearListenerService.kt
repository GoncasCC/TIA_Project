package com.example.tia_project

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

class PhoneWearListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        if (path == "/watch_command") {
            try {
                val jsonString = String(messageEvent.data, Charsets.UTF_8)
                val json = JSONObject(jsonString)
                val command = json.getString("command")
                // Limpa espaços e aspas extra
                val cleanCommand = command.replace("\"", "").trim()
                WatchDataRepository.updateCommand(cleanCommand, System.currentTimeMillis())
            } catch (e: Exception) {
                // Caso não seja JSON, lê como texto puro
                val raw = String(messageEvent.data, Charsets.UTF_8)
                val cleanCommand = raw.replace("\"", "").trim()
                WatchDataRepository.updateCommand(cleanCommand, System.currentTimeMillis())
            }
        }
    }

    override fun onDataChanged(dataEvents: com.google.android.gms.wearable.DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

                when (path) {
                    "/watch_progress" -> {
                        val progress = dataMap.getFloat("progress")
                        val level = dataMap.getInt("level")
                        val paused = dataMap.getBoolean("paused")
                        val difficulty = dataMap.getString("difficulty") ?: ""
                        val steps = dataMap.getInt("steps", 0)
                        WatchDataRepository.updateProgress(progress, level, paused, difficulty, steps)
                    }
                    "/watch_command" -> {
                        val command = dataMap.getString("command") ?: return@forEach
                        val cleanCommand = command.replace("\"", "").trim()
                        val timestamp = dataMap.getLong("timestamp", System.currentTimeMillis())
                        WatchDataRepository.updateCommand(cleanCommand, timestamp)
                    }
                }
            }
        }
    }
}