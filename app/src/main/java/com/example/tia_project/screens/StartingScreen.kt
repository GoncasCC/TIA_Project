package com.example.tia_project.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.tia_project.sensors.AccelerometerManager


@Composable
fun StartingScreen() {
    val context = LocalContext.current

    var started by remember { mutableStateOf(false) }
    var sensorText by remember { mutableStateOf("Accelerometer values will appear here") }

    val accelerometerManager = remember {
        AccelerometerManager(context) { x, y, z ->
            sensorText = "X: %.2f\nY: %.2f\nZ: %.2f".format(x, y, z)
        }
    }

    DisposableEffect(started) {
        if (started) {
            if (accelerometerManager.isSensorAvailable()) {
                accelerometerManager.startListening()
            } else {
                sensorText = "This device does not have an accelerometer."
            }
        }

        onDispose {
            accelerometerManager.stopListening()
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

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    started = true
                    sensorText = "Starting accelerometer..."
                },
                enabled = !started
            ) {
                Text("Start")
            }

            Button(
                onClick = {
                    started = false
                    sensorText = "Accelerometer stopped."
                },
                enabled = started
            ) {
                Text("Stop")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = sensorText,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}



@Preview(showBackground = true)
@Composable
fun PreviewStartingScreen() {
    StartingScreen()
}