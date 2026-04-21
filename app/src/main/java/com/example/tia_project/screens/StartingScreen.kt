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
import com.example.tia_project.sensors.MotionData


@Composable
fun StartingScreen() {
    val context = LocalContext.current

    var started by remember { mutableStateOf(false) }
    var motionData by remember {
        mutableStateOf(
            MotionData(
                x = 0f,
                y = 0f,
                z = 0f,
                magnitude = 0f,
                linearMagnitude = 0f,
                state = "Not started"
            )
        )
    }

    var statusText by remember { mutableStateOf("Accelerometer values will appear here") }

    val accelerometerManager = remember {
        AccelerometerManager(context) { newData ->
            motionData = newData
        }
    }

    DisposableEffect(started) {
        if (started) {
            if (accelerometerManager.isSensorAvailable()) {
                statusText = "Accelerometer started"
                accelerometerManager.startListening()
            } else {
                statusText = "This device does not have an accelerometer."
            }
        } else {
            accelerometerManager.stopListening()
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
                onClick = { started = true },
                enabled = !started
            ) {
                Text("Start")
            }

            Button(
                onClick = {
                    started = false
                    statusText = "Accelerometer stopped"
                },
                enabled = started
            ) {
                Text("Stop")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!started) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            Text(
                text = """
                    X: %.2f
                    Y: %.2f
                    Z: %.2f
                    
                    Magnitude: %.2f
                    Linear movement: %.2f
                    
                    State: %s
                """.trimIndent().format(
                    motionData.x,
                    motionData.y,
                    motionData.z,
                    motionData.magnitude,
                    motionData.linearMagnitude,
                    motionData.state
                ),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}



@Preview(showBackground = true)
@Composable
fun PreviewStartingScreen() {
    StartingScreen()
}