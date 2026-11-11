package dev.tiltdrive.remote

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Game listens here for the discovery hello and for our control packets. */
const val PORT_HOST = 4444
/** We listen here for the discovery reply and for the game's OutGauge telemetry. */
const val PORT_LOCAL = 4445

/** The game picks its pairing code at random from this range. */
val CODE_RANGE = 10000..99999

object Lights {
    const val FULLBEAM = 2
    const val HANDBRAKE = 4
    const val TC = 16
    const val SIGNAL_L = 32
    const val SIGNAL_R = 64
    const val ABS = 1024
}

data class Telemetry(
    val speedMs: Float,
    val rpm: Float,
    val gear: Int,
    val fuel: Float,
    val engTemp: Float,
    val lights: Int,
) {
    fun speed(mph: Boolean): Float = speedMs * if (mph) 2.23694f else 3.6f
    val gearLabel: String
        get() = when {
            gear <= 0 -> "R"
            gear == 1 -> "N"
            else -> (gear - 1).toString()
        }

    fun lamp(bit: Int): Boolean = (lights and bit) != 0
}

/** Control packet: 4 big-endian floats. Steering is 0..1 on the wire (0 = right, 0.5 = straight, 1 = left). */
fun encodeControl(steerWire: Float, throttle: Float, brake: Float, id: Float): ByteArray =
    ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        .putFloat(steerWire).putFloat(throttle).putFloat(brake).putFloat(id)
        .array()

/** Telemetry is the standard OutGauge struct (96+ bytes), little-endian. */
fun parseTelemetry(buf: ByteArray, len: Int): Telemetry? {
    if (len < 96) return null
    val bb = ByteBuffer.wrap(buf, 0, len).order(ByteOrder.LITTLE_ENDIAN)
    return Telemetry(
        speedMs = bb.getFloat(12),
        rpm = bb.getFloat(16),
        gear = bb.get(10).toInt(),
        fuel = bb.getFloat(28),
        engTemp = bb.getFloat(24),
        lights = bb.getInt(44),
    )
}
