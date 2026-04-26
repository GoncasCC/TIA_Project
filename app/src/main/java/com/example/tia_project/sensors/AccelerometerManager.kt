package com.example.tia_project.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

data class MotionData(
    val x: Float,
    val y: Float,
    val z: Float,
    val magnitude: Float,
    val linearMagnitude: Float,
    val state: String
)

class AccelerometerManager(
    context: Context,
    private val onMotionChanged: (MotionData) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gravity = FloatArray(3)

    fun isSensorAvailable(): Boolean {
        return accelerometer != null
    }

    fun startListening() {
        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return


        //Documentation from androidStudio ---  https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion?hl=pt-br (Podem ver isto - basicamente tirar a gravidade do acelerometro)
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val alpha = 0.8f

        gravity[0] = alpha * gravity[0] + (1 - alpha) * x
        gravity[1] = alpha * gravity[1] + (1 - alpha) * y
        gravity[2] = alpha * gravity[2] + (1 - alpha) * z

        val linearX = x - gravity[0]
        val linearY = y - gravity[1]
        val linearZ = z - gravity[2]


        val magnitude = sqrt(x * x + y * y + z * z)
        val linearMagnitude = sqrt(
            linearX * linearX +
                    linearY * linearY +
                    linearZ * linearZ
        )


        //Here is where the forces are tested. If values need to change its here.
        val state = when {
            linearMagnitude < 0.8f -> "Still"
            linearMagnitude < 3.0f -> "Walking / Light movement"
            else -> "Running / Strong movement"
        }

        onMotionChanged(
            MotionData(
                x = x,
                y = y,
                z = z,
                magnitude = magnitude,
                linearMagnitude = linearMagnitude,
                state = state
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}
