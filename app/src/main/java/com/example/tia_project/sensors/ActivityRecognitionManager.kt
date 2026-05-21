package com.example.tia_project.sensors

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityRecognitionManager(
    private val context: Context,
    private val onActivityChanged: (String) -> Unit,
    private val onError: (String) -> Unit = {}
) {

    companion object {
        private const val ACTION_ACTIVITY_UPDATE =
            "com.example.tia_project.ACTIVITY_UPDATE"
        private const val DETECTION_INTERVAL_MS = 1000L
        private const val MIN_CONFIDENCE = 30
    }

    private val client = ActivityRecognition.getClient(context)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(ACTION_ACTIVITY_UPDATE)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (!ActivityRecognitionResult.hasResult(intent)) return

            val result = ActivityRecognitionResult.extractResult(intent) ?: return
            val activity = result.mostProbableActivity

            if (activity.confidence >= MIN_CONFIDENCE) {
                onActivityChanged(activityLabel(activity.type))
            }
        }
    }

    private fun activityLabel(type: Int): String {
        return when (type) {
            DetectedActivity.STILL -> "Parado"
            DetectedActivity.ON_FOOT -> "A andar"
            DetectedActivity.WALKING -> "A andar"
            DetectedActivity.RUNNING -> "A correr"
            DetectedActivity.ON_BICYCLE -> "De bicicleta"
            DetectedActivity.IN_VEHICLE -> "Em veículo"
            DetectedActivity.TILTING -> "A inclinar"
            else -> "Desconhecido"
        }
    }

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    fun startListening() {
        val filter = IntentFilter(ACTION_ACTIVITY_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        client.requestActivityUpdates(DETECTION_INTERVAL_MS, pendingIntent)
            .addOnSuccessListener {
                onActivityChanged("A detetar...")


            }
            .addOnFailureListener { e ->
                onError("Erro ao iniciar: ${e.message}")
            }
    }

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    fun stopListening() {
        client.removeActivityUpdates(pendingIntent)
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
    }
}
