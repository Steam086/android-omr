package com.answercard.grader.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class DeviceStabilityMonitor(
    context: Context,
    private val evaluator: StabilityEvaluator = StabilityEvaluator(),
    private val onStabilityChanged: (Boolean) -> Unit = {},
) : SensorEventListener {
    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // Gravity-compensated acceleration, so a phone at rest reads ~0. Catches the pure
    // translation (sliding) that the gyroscope is blind to.
    private val linearAcceleration: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private var lastReported: Boolean? = null

    val hasGyroscope: Boolean get() = gyroscope != null
    val hasLinearAcceleration: Boolean get() = linearAcceleration != null
    private val hasMotionSensor: Boolean get() = gyroscope != null || linearAcceleration != null

    fun start() {
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        linearAcceleration?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        if (hasMotionSensor) sensorManager.unregisterListener(this)
    }

    fun isStable(nowMs: Long = System.currentTimeMillis()): Boolean =
        !hasMotionSensor || evaluator.isStable(nowMs)

    override fun onSensorChanged(event: SensorEvent) {
        val magnitude = sqrt(
            event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2],
        )
        val nowMs = System.currentTimeMillis()
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> evaluator.onGyroscopeSample(nowMs, magnitude)
            Sensor.TYPE_LINEAR_ACCELERATION -> evaluator.onLinearAccelerationSample(nowMs, magnitude)
            else -> return
        }
        val stable = isStable(nowMs)
        if (stable != lastReported) {
            lastReported = stable
            onStabilityChanged(stable)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
