package dev.tiltdrive.remote

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer

enum class LinkState { Idle, Searching, Connected, Failed }

/** UDP link to the game: discovery -> control stream out + telemetry stream in. */
class BeamLink(private val ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var rx: DatagramSocket? = null

    val state = MutableStateFlow(LinkState.Idle)
    val error = MutableStateFlow<String?>(null)
    val telemetry = MutableStateFlow<Telemetry?>(null)
    val latencyMs = MutableStateFlow(0)
    val stale = MutableStateFlow(false)

    /** Live control values, written by the UI, read by the sender loop. */
    @Volatile var steerWire = 0.5f
    @Volatile var throttle = 0f
    @Volatile var brake = 0f

    @Volatile private var pingId = 1
    @Volatile private var pingSince = 0L

    fun connect(code: String, manualHost: String?) {
        disconnect()
        error.value = null
        telemetry.value = null
        stale.value = false
        state.value = LinkState.Searching
        job = scope.launch { runLink(code.trim(), manualHost?.trim()?.ifBlank { null }) }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        rx?.close()
        rx = null
        throttle = 0f
        brake = 0f
        state.value = LinkState.Idle
    }

    private suspend fun runLink(code: String, manualHost: String?) = coroutineScope {
        var rxSock: DatagramSocket? = null
        var txSock: DatagramSocket? = null
        try {
            val rxs = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(PORT_LOCAL))
                soTimeout = 250
            }
            rxSock = rxs
            rx = rxs
            val host = discover(rxs, code, manualHost)
            if (host == null) {
                if (isActive) {
                    error.value = "Game not found. Check that the PC and phone are on the same Wi-Fi " +
                        "and that the remote controller is enabled in the game."
                    state.value = LinkState.Failed
                }
                return@coroutineScope
            }
            val tx = DatagramSocket()
            txSock = tx
            pingId = 1
            pingSince = System.currentTimeMillis()
            state.value = LinkState.Connected
            val sender = launch { sendLoop(tx, host) }
            receiveLoop(rxs)
            sender.cancel()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isActive) {
                error.value = e.message ?: e.toString()
                state.value = LinkState.Failed
            }
        } finally {
            rxSock?.close()
            txSock?.close()
        }
        Unit
    }

    private fun CoroutineScope.discover(rx: DatagramSocket, code: String, manualHost: String?): InetAddress? {
        val targets = broadcastTargets().toMutableList()
        if (manualHost != null) {
            runCatching { targets.add(0, InetAddress.getByName(manualHost)) }
        }
        val msg = "beamng|${Build.MODEL}|$code".toByteArray()
        val expected = "beamng|$code"
        DatagramSocket().use { tx ->
            tx.broadcast = true
            val buf = ByteArray(64)
            repeat(12) {
                if (!isActive) return null
                for (t in targets) runCatching { tx.send(DatagramPacket(msg, msg.size, t, PORT_HOST)) }
                val until = System.currentTimeMillis() + 1000
                while (isActive && System.currentTimeMillis() < until) {
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        rx.receive(p)
                        if (String(p.data, 0, p.length).trim() == expected) return p.address
                    } catch (_: SocketTimeoutException) {
                    }
                }
            }
        }
        return null
    }

    private suspend fun CoroutineScope.sendLoop(tx: DatagramSocket, host: InetAddress) {
        while (isActive) {
            val data = encodeControl(steerWire, throttle, brake, pingId.toFloat())
            runCatching { tx.send(DatagramPacket(data, data.size, host, PORT_HOST)) }
            delay(25)
        }
    }

    private fun CoroutineScope.receiveLoop(rx: DatagramSocket) {
        val buf = ByteArray(128)
        var last = System.currentTimeMillis()
        while (isActive) {
            try {
                val p = DatagramPacket(buf, buf.size)
                rx.receive(p)
                val t = parseTelemetry(buf, p.length) ?: continue
                val now = System.currentTimeMillis()
                last = now
                stale.value = false
                telemetry.value = t
                if (t.id == pingId) {
                    latencyMs.value = (latencyMs.value + (now - pingSince).toInt()) / 2
                    pingSince = now
                    pingId = if (pingId + 1 >= 128) 0 else pingId + 1
                }
            } catch (_: SocketTimeoutException) {
                if (System.currentTimeMillis() - last > 2500) stale.value = true
            }
        }
    }

    private fun broadcastTargets(): List<InetAddress> {
        val out = mutableListOf<InetAddress>()
        runCatching {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val lp = cm.getLinkProperties(cm.activeNetwork)
            lp?.linkAddresses?.forEach { la ->
                val a = la.address
                if (a is Inet4Address) {
                    val prefix = la.prefixLength
                    val mask = if (prefix == 0) 0 else -1 shl (32 - prefix)
                    val ip = ByteBuffer.wrap(a.address).int
                    val b = ip or mask.inv()
                    out += InetAddress.getByAddress(ByteBuffer.allocate(4).putInt(b).array())
                }
            }
        }
        out += InetAddress.getByName("255.255.255.255")
        return out
    }
}
