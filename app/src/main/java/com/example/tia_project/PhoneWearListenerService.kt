package com.example.tia_project

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

class PhoneWearListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        try {
            val json = JSONObject(String(messageEvent.data, Charsets.UTF_8))
            when (path) {
                "/watch_command" -> {
                    val command = json.getString("command").replace("\"", "").trim()
                    WatchDataRepository.updateCommand(command, System.currentTimeMillis())
                }
                "/level_changed" -> {
                    val level = json.optInt("level", 1)
                    WatchDataRepository.updateLevel(level)
                }
                "/session_result" -> {
                    val distanceMeters  = json.optDouble("distanceMeters", 0.0).toFloat()
                    val elapsedSeconds  = json.optInt("elapsedSeconds", 0)
                    val endedEarly      = json.optBoolean("endedEarly", false)
                    val isNewPersonalBest = json.optBoolean("isNewPersonalBest", false)
                    WatchDataRepository.updateResult(
                        distanceMeters = distanceMeters,
                        elapsedSeconds = elapsedSeconds,
                        endedEarly = endedEarly,
                        isNewPersonalBest = isNewPersonalBest
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WearDebug", "Erro no PhoneWearListenerService ($path): ${e.message}")
        }
    }
}
