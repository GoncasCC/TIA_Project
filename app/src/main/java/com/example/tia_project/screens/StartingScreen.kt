package com.example.tia_project.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.tia_project.sensors.ActivityRecognitionManager


@Composable
fun StartingScreen() {
    val context = LocalContext.current

    var started by remember { mutableStateOf(false) }
    var currentActivity by remember { mutableStateOf("—") }
    var permissionDenied by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val activityManager = remember {
        ActivityRecognitionManager(
            context = context,
            onActivityChanged = { label ->
                currentActivity = label
                errorMessage = ""
            },
            onError = { msg ->
                errorMessage = msg
                started = false
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val effectivelyGranted = granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        if (effectivelyGranted) {
            permissionDenied = false
            activityManager.startListening()
            started = true
        } else {
            permissionDenied = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activityManager.stopListening()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Activity Monitor",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Google Activity Recognition API  •  Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (permissionDenied) {
            Text(
                text = "Permissão de reconhecimento de atividade negada.\nAtiva-a nas definições da app.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                },
                enabled = !started
            ) {
                Text("Start")
            }

            Button(
                onClick = {
                    activityManager.stopListening()
                    started = false
                    currentActivity = "—"
                },
                enabled = started
            ) {
                Text("Stop")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = if (started) "Atividade atual:" else "Prima Start para começar",
            style = MaterialTheme.typography.bodyLarge
        )

        if (started) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = currentActivity,
                style = MaterialTheme.typography.headlineLarge
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun PreviewStartingScreen() {
    StartingScreen()
}