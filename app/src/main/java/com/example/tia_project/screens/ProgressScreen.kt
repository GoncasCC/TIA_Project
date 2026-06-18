package com.example.tia_project.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

sealed class Page {
    data class PBMode(val label: String, val value: String, val speech: String) : Page()
    data class StatsPeriod(val period: String) : Page()
}

@Composable
fun ProgressScreen(
    voiceoverEnabled: Boolean,
    vibrationEnabled: Boolean,
    darkModeEnabled: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val backgroundColor = if (darkModeEnabled) Color.Black else Color.White
    val textColor = if (darkModeEnabled) Color.White else Color.Black
    val accentColor = if (darkModeEnabled) Color(0xFFFFCC00) else Color(0xFFB71C1C)

    val sessions = remember { loadSavedSessions(context) }

    val pb1Min = remember(sessions) {
        sessions.filter { it.mode == "1 MIN" }.maxByOrNull { it.distanceKm }
    }
    val pb5Min = remember(sessions) {
        sessions.filter { it.mode == "5 MIN" }.maxByOrNull { it.distanceKm }
    }
    val pb1Km = remember(sessions) {
        sessions.filter { it.mode == "1 KM" && it.timeSeconds > 0 }.minByOrNull { it.timeSeconds }
    }

    val pages: List<Page> = listOf(
        Page.PBMode(
            label = "1 MIN MODE",
            value = if (pb1Min != null) pb1Min.distanceKm.toSignificantKm() else "---",
            speech = if (pb1Min != null) "Personal best, 1 minute mode: ${pb1Min.distanceKm.toSignificantKmSpeech()} kilometers"
            else "Personal best, 1 minute mode: no record yet"
        ),
        Page.PBMode(
            label = "5 MIN MODE",
            value = if (pb5Min != null) pb5Min.distanceKm.toSignificantKm() else "---",
            speech = if (pb5Min != null) "Personal best, 5 minutes mode: ${pb5Min.distanceKm.toSignificantKmSpeech()} kilometers"
            else "Personal best, 5 minutes mode: no record yet"
        ),
        Page.PBMode(
            label = "1 KM MODE",
            value = if (pb1Km != null) pb1Km.timeSeconds.toReadableDuration() else "---",
            speech = if (pb1Km != null) {
                val m = pb1Km.timeSeconds / 60
                val s = pb1Km.timeSeconds % 60
                "Personal best, 1 kilometer mode: $m minutes and $s seconds"
            } else "Personal best, 1 kilometer mode: no record yet"
        ),
        Page.StatsPeriod("TODAY"),
        Page.StatsPeriod("YESTERDAY"),
        Page.StatsPeriod("THIS WEEK"),
        Page.StatsPeriod("THIS MONTH"),
        Page.StatsPeriod("ALL TIME")
    )

    var pageIndex by remember { mutableStateOf(0) }
    val currentPage = pages[pageIndex]

    val progressData = remember(pageIndex, sessions) {
        if (currentPage is Page.StatsPeriod) calculateProgressForPeriod(currentPage.period, sessions)
        else ProgressData(0f, 0, 0)
    }

    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun vibrateNormal() {
        if (!vibrationEnabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(120)
        }
    }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var isGoingBack by remember { mutableStateOf(false) }
    var isTwoFingerGesture by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
            }
        }

        tts = textToSpeech
        textToSpeech.language = Locale.US

        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    LaunchedEffect(pageIndex, isTtsReady, voiceoverEnabled) {
        if (isGoingBack || !isTtsReady || !voiceoverEnabled) return@LaunchedEffect
        when (val page = currentPage) {
            is Page.PBMode -> tts?.speak(page.speech, TextToSpeech.QUEUE_FLUSH, null, "page_$pageIndex")
            is Page.StatsPeriod -> {
                val periodText = when (page.period) {
                    "TODAY" -> "Today"
                    "YESTERDAY" -> "Yesterday"
                    "THIS WEEK" -> "This week"
                    "THIS MONTH" -> "This month"
                    "ALL TIME" -> "All time"
                    else -> page.period.lowercase()
                }
                val speech = "$periodText, you completed ${progressData.totalDistanceKm.toSignificantKmSpeech()} kilometers " +
                        "in ${progressData.totalTimeSeconds.toReadableDurationForSpeech()}. " +
                        "Total sessions: ${progressData.totalSessions}."
                tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "page_$pageIndex")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(pageIndex, vibrationEnabled, voiceoverEnabled) {
                coroutineScope {
                    launch (start = CoroutineStart.UNDISPATCHED) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)

                            isTwoFingerGesture = false
                            var totalDragX = 0f

                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }

                                if (pressed.size >= 2) {
                                    isTwoFingerGesture = true
                                }

                                val dragX = event.changes.sumOf { it.positionChange().x.toDouble() }.toFloat()
                                totalDragX += dragX

                                if (isTwoFingerGesture) {
                                    if (!isGoingBack && (totalDragX > 80f || totalDragX < -80f)) {
                                        isGoingBack = true
                                        event.changes.forEach { it.consume() }
                                        vibrateNormal()
                                        tts?.speak("Going back to menu.", TextToSpeech.QUEUE_FLUSH, null, "progress_back_menu")

                                        launch {
                                            while (tts?.isSpeaking == true) {
                                                delay(100)
                                            }
                                            delay(200)
                                            onBack()
                                        }
                                    }
                                } else {
                                    if (!isGoingBack) {
                                        if (totalDragX > 25f) {
                                            pageIndex = if (pageIndex == 0) pages.lastIndex else pageIndex - 1
                                            vibrateNormal()
                                            totalDragX = 0f
                                            event.changes.forEach { it.consume() }
                                        } else if (totalDragX < -25f) {
                                            pageIndex = if (pageIndex == pages.lastIndex) 0 else pageIndex + 1
                                            vibrateNormal()
                                            totalDragX = 0f
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            isTwoFingerGesture = false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 40.dp)
        ) {
            Text(
                text = "PROGRESS",
                color = textColor,
                fontSize = 66.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val page = currentPage) {
                    is Page.PBMode -> {
                        Text(
                            text = "PERSONAL BEST",
                            color = accentColor,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = page.label,
                            color = textColor,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = page.value,
                            color = textColor,
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is Page.StatsPeriod -> {
                        Text(
                            text = page.period,
                            color = accentColor,
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(70.dp))

                        Text(
                            text = progressData.totalDistanceKm.toSignificantKm(),
                            color = textColor,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = progressData.totalTimeSeconds.toReadableDuration(),
                            color = textColor,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "SESSIONS: ${progressData.totalSessions}",
                            color = textColor,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

data class SavedSession(
    val date: String,
    val distanceKm: Float,
    val timeSeconds: Int,
    val mode: String = ""
)

data class ProgressData(
    val totalDistanceKm: Float,
    val totalTimeSeconds: Int,
    val totalSessions: Int
)

private fun loadSavedSessions(context: Context): List<SavedSession> {
    val prefs = context.getSharedPreferences("training_sessions", Context.MODE_PRIVATE)
    val rawSessions = prefs.getStringSet("sessions", emptySet()) ?: emptySet()

    return rawSessions.mapNotNull { raw ->
        val parts = raw.split("|")
        when (parts.size) {
            4 -> SavedSession(
                date = parts[0],
                distanceKm = parts[1].toFloatOrNull() ?: 0f,
                timeSeconds = parts[2].toIntOrNull() ?: 0,
                mode = parts[3]
            )
            3 -> SavedSession(
                date = parts[0],
                distanceKm = parts[1].toFloatOrNull() ?: 0f,
                timeSeconds = parts[2].toIntOrNull() ?: 0
            )
            else -> null
        }
    }
}

private fun calculateProgressForPeriod(
    period: String,
    sessions: List<SavedSession>
): ProgressData {
    val cal = java.util.Calendar.getInstance()

    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    val startOfToday = cal.timeInMillis

    val startOfYesterday = startOfToday - 24 * 60 * 60 * 1000L

    cal.set(java.util.Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
    val startOfWeek = cal.timeInMillis

    cal.timeInMillis = startOfToday
    cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
    val startOfMonth = cal.timeInMillis

    val filteredSessions = sessions.filter { session ->
        val sessionTime = session.date.toLongOrNull() ?: return@filter false

        when (period) {
            "TODAY"      -> sessionTime >= startOfToday
            "YESTERDAY"  -> sessionTime in startOfYesterday until startOfToday
            "THIS WEEK"  -> sessionTime >= startOfWeek
            "THIS MONTH" -> sessionTime >= startOfMonth
            "ALL TIME"   -> true
            else         -> false
        }
    }

    return ProgressData(
        totalDistanceKm = filteredSessions.sumOf { it.distanceKm.toDouble() }.toFloat(),
        totalTimeSeconds = filteredSessions.sumOf { it.timeSeconds },
        totalSessions = filteredSessions.size
    )
}

private fun Float.toSignificantKm(): String {
    return when {
        this >= 100f -> String.format(Locale.US, "%.0f KM", this)
        this >= 10f  -> String.format(Locale.US, "%.1f KM", this)
        else         -> String.format(Locale.US, "%.3f KM", this)
    }
}

private fun Float.toSignificantKmSpeech(): String {
    return when {
        this >= 100f -> String.format(Locale.US, "%.0f", this)
        this >= 10f  -> String.format(Locale.US, "%.1f", this)
        else         -> String.format(Locale.US, "%.3f", this)
    }
}

private fun Int.toReadableDuration(): String {
    val hours = this / 3600
    val minutes = this / 60

    return when {
        hours > 0 -> String.format(Locale.US, "%dH %02dM", hours, minutes % 60)
        else -> String.format(Locale.US, "%d MIN", minutes)
    }
}

private fun Int.toReadableDurationForSpeech(): String {
    val hours = this / 3600
    val minutes = this / 60

    return when {
        hours > 0 && minutes % 60 > 0 -> "$hours hour${if (hours == 1) "" else "s"} and ${minutes % 60} minute${if (minutes % 60 == 1) "" else "s"}"
        hours > 0 -> "$hours hour${if (hours == 1) "" else "s"}"
        minutes > 0 -> "$minutes minute${if (minutes == 1) "" else "s"}"
        else -> "0 minutes"
    }
}