package dev.tiltdrive.remote

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Wheel angle in degrees from the gravity vector, for a phone held like a steering wheel
 * (screen facing you, landscape). 0 = upright, clockwise = positive.
 */
class TiltSensor(ctx: Context, private val rotation: () -> Int) : SensorEventListener {
    private val sm = ctx.getSystemService(SensorManager::class.java)
    @Volatile var angleDeg = 0f
        private set
    private var fx = 0f
    private var fy = 0f
    private var primed = false

    fun start() {
        val s = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (s != null) sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sm.unregisterListener(this)
        primed = false
    }

    override fun onSensorChanged(e: SensorEvent) {
        val vx = e.values[0]
        val vy = e.values[1]
        // Map device axes to screen axes for the current display rotation.
        val (sx, sy) = when (rotation()) {
            Surface.ROTATION_90 -> vy to -vx
            Surface.ROTATION_180 -> -vx to -vy
            Surface.ROTATION_270 -> -vy to vx
            else -> vx to vy
        }
        if (hypot(sx, sy) < 2.5f) return // lying flat: angle is meaningless
        if (!primed) { fx = sx; fy = sy; primed = true }
        val a = 0.25f
        fx += a * (sx - fx)
        fy += a * (sy - fy)
        angleDeg = Math.toDegrees(atan2(-fx, fy).toDouble()).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
