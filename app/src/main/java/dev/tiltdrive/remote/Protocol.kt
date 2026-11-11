package dev.tiltdrive.remote

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Game listens here for discovery and for our control packets. */
const val PORT_HOST = 4444
/** We listen here for the discovery reply and the telemetry stream. */
const val PORT_LOCAL = 4445

object Lights {
    const val FULLBEAM = 2
    const val HANDBRAKE = 4
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
    val flags: Int,
    val lights: Int,
    val id: Int,
) {
    val usesKmh: Boolean get() = (flags and 16384) != 0
    val speedDisplay: Float get() = if (usesKmh) speedMs * 3.6f else speedMs * 2.23694f
    val gearLabel: String
        get() = when {
            gear <= 0 -> "R"
            gear == 1 -> "N"
            else -> (gear - 1).toString()
        }

    fun lamp(bit: Int): Boolean = (lights and bit) != 0
}

/** Control packet: 4 big-endian floats. Steering is 0..1 on the wire, 0.5 = straight. */
fun encodeControl(steerWire: Float, throttle: Float, brake: Float, id: Float): ByteArray =
    ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        .putFloat(steerWire).putFloat(throttle).putFloat(brake).putFloat(id)
        .array()

/** Telemetry is an OutGauge-style struct, little-endian. */
fun parseTelemetry(buf: ByteArray, len: Int): Telemetry? {
    if (len < 96) return null
    val bb = ByteBuffer.wrap(buf, 0, len).order(ByteOrder.LITTLE_ENDIAN)
    return Telemetry(
        speedMs = bb.getFloat(12),
        rpm = bb.getFloat(16),
        gear = bb.get(10).toInt(),
        fuel = bb.getFloat(28),
        engTemp = bb.getFloat(24),
        flags = bb.getShort(8).toInt() and 0xFFFF,
        lights = bb.getInt(44),
        id = bb.get(92).toInt() and 0xFF,
    )
}

/** QR text from the game looks like "something#securityCode". */
fun parseQr(text: String): String? {
    val parts = text.trim().split("#")
    return if (parts.size == 2 && parts[1].isNotBlank()) parts[1] else null
}
