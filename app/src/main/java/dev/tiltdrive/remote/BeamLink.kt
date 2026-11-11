package dev.tiltdrive.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

/**
 * UDP link to the game.
 * 1. Pairing: broadcast "beamng|<device>|<code>" to :4444. The game answers "beamng|<code>" to :4445
 *    only when the code is right. Auto-connect tries every 5-digit code, the reply reveals the right one.
 * 2. Control: 4 floats to :4444 every ~15 ms (also keeps the device registered; the game drops idle ones).
 * 3. Telemetry: the game's OutGauge stream arrives on :4445 once the user points it at the phone.
 */
class BeamLink(private val ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private var job: Job? = null
    @Volatile private var rx: DatagramSocket? = null

    val state = MutableStateFlow(LinkState.Idle)
    val error = MutableStateFlow<String?>(null)
    val progress = MutableStateFlow(0f)
    val telemetry = MutableStateFlow<Telemetry?>(null)
    val stale = MutableStateFlow(false)
    @Volatile var onCodeFound: ((String) -> Unit)? = null

    /** Live control values, written by the UI, read by the sender loop. */
    @Volatile var steerWire = 0.5f
    @Volatile var throttle = 0f
    @Volatile var brake = 0f

    /** [manualCode] = connect with this code; null = auto (tries [hintCode] first, then every code). */
    fun connect(manualCode: String?, hintCode: String?, manualHost: String?) {
        disconnect()
        error.value = null
        telemetry.value = null
        stale.value = false
        progress.value = 0f
        state.value = LinkState.Searching
        job = scope.launch {
            runLink(manualCode?.trim(), hintCode?.trim()?.takeIf { it.length == 5 }, manualHost?.trim()?.ifBlank { null })
        }
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

    // ───────────── Wi-Fi helpers ─────────────

    /** Pinning sockets to Wi-Fi keeps discovery working when mobile data is on too. */
    @Suppress("DEPRECATION")
    private fun wifiNetwork(): Network? = runCatching {
        cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }.getOrNull()

    fun phoneIp(): String? = runCatching {
        val net = wifiNetwork() ?: return null
        cm.getLinkProperties(net)?.linkAddresses
            ?.map { it.address }?.filterIsInstance<Inet4Address>()?.firstOrNull()?.hostAddress
    }.getOrNull()

    private fun pin(net: Network?, s: DatagramSocket) {
        if (net != null) runCatching { net.bindSocket(s) }
    }

    private fun subnetBroadcast(net: Network?): InetAddress? = runCatching {
        val la = cm.getLinkProperties(net ?: return null)?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address } ?: return null
        val prefix = la.prefixLength
        val mask = if (prefix == 0) 0 else -1 shl (32 - prefix)
        val ip = ByteBuffer.wrap(la.address.address).int
        InetAddress.getByAddress(ByteBuffer.allocate(4).putInt(ip or mask.inv()).array())
    }.getOrNull()

    private fun targets(net: Network?, manualHost: String?): List<InetAddress> {
        val out = mutableListOf<InetAddress>()
        if (manualHost != null) runCatching { out += InetAddress.getByName(manualHost) }
        out += subnetBroadcast(net) ?: InetAddress.getByName("255.255.255.255")
        return out
    }

    private fun deviceName(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}".replace(Regex("[|#\\n\\r]"), "_")

    private fun hello(code: String) = "beamng|${deviceName()}|$code".toByteArray()

    // ───────────── Session ─────────────

    private suspend fun runLink(manualCode: String?, hint: String?, manualHost: String?) = coroutineScope {
        var rxSock: DatagramSocket? = null
        var txSock: DatagramSocket? = null
        try {
            val net = wifiNetwork()
            val rxs = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(PORT_LOCAL)) // wildcard: catch the reply whichever IP the game saw
                soTimeout = 20
            }
            rxSock = rxs
            rx = rxs
            pin(net, rxs)
            val targets = targets(net, manualHost)

            var found: Pair<InetAddress, String>? = null
            if (manualCode != null) {
                found = handshake(rxs, net, targets, manualCode, 10)
            } else {
                if (hint != null) found = handshake(rxs, net, targets, hint, 2)
                if (found == null) found = sweep(rxs, net, targets)
            }
            if (found == null) {
                if (isActive) {
                    error.value = "Game not found. Same Wi-Fi? In the game set Options → Controls → Hardware → " +
                        "“Use your phone or tablet” to Yes, and allow BeamNG.drive in the Windows firewall."
                    state.value = LinkState.Failed
                }
                return@coroutineScope
            }
            val host = found.first
            val code = found.second
            onCodeFound?.invoke(code)

            val tx = DatagramSocket()
            txSock = tx
            pin(net, tx)
            rxs.soTimeout = 250
            state.value = LinkState.Connected
            val sender = launch { sendLoop(tx, host, hello(code)) }
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

    /** Send the hello with a known code and wait for the game's echo. */
    private suspend fun handshake(
        rx: DatagramSocket, net: Network?, targets: List<InetAddress>, code: String, tries: Int,
    ): Pair<InetAddress, String>? {
        val msg = hello(code)
        val expected = "beamng|$code"
        val buf = ByteArray(64)
        val p = DatagramPacket(buf, buf.size)
        DatagramSocket().use { tx ->
            tx.broadcast = true
            pin(net, tx)
            repeat(tries) {
                currentCoroutineContext().ensureActive()
                for (t in targets) runCatching { tx.send(DatagramPacket(msg, msg.size, t, PORT_HOST)) }
                val until = System.currentTimeMillis() + 1000
                while (System.currentTimeMillis() < until) {
                    currentCoroutineContext().ensureActive()
                    try {
                        p.length = buf.size
                        rx.receive(p)
                        if (String(buf, 0, p.length).trim() == expected) return p.address to code
                    } catch (_: SocketTimeoutException) {
                    }
                }
            }
        }
        return null
    }

    /** Camera-free pairing: try every code; the game's reply "beamng|<code>" reveals the right one. */
    private suspend fun sweep(
        rx: DatagramSocket, net: Network?, targets: List<InetAddress>,
    ): Pair<InetAddress, String>? {
        val name = deviceName()
        val reply = Regex("^beamng\\|(\\d{5})$")
        val buf = ByteArray(32)
        val p = DatagramPacket(buf, buf.size)
        val total = (CODE_RANGE.last - CODE_RANGE.first + 1).toFloat()

        fun drain(): Pair<InetAddress, String>? {
            while (true) {
                try {
                    p.length = buf.size
                    rx.receive(p)
                } catch (_: SocketTimeoutException) {
                    return null
                }
                val m = reply.find(String(buf, 0, p.length).trim())?.groupValues?.get(1)
                if (m != null) return p.address to m
            }
        }

        DatagramSocket().use { tx ->
            tx.broadcast = true
            pin(net, tx)
            repeat(2) {
                var tried = 0
                for (code in CODE_RANGE) {
                    val payload = "beamng|$name|$code".toByteArray()
                    for (t in targets) runCatching { tx.send(DatagramPacket(payload, payload.size, t, PORT_HOST)) }
                    tried++
                    if (tried % 300 == 0) {
                        currentCoroutineContext().ensureActive()
                        drain()?.let { return it }
                        progress.value = tried / total
                        delay(40)
                    }
                }
                repeat(50) { // grace window for stragglers
                    currentCoroutineContext().ensureActive()
                    drain()?.let { return it }
                    delay(20)
                }
            }
        }
        return null
    }

    private suspend fun CoroutineScope.sendLoop(tx: DatagramSocket, host: InetAddress, hello: ByteArray) {
        var tick = 0
        while (isActive) {
            // Re-register now and then: the game forgets devices that stay quiet for ~10 s.
            if (tick++ % 130 == 0) runCatching { tx.send(DatagramPacket(hello, hello.size, host, PORT_HOST)) }
            val d = encodeControl(steerWire, throttle, brake, 0f)
            runCatching { tx.send(DatagramPacket(d, d.size, host, PORT_HOST)) }
            delay(15)
        }
    }

    private fun CoroutineScope.receiveLoop(rx: DatagramSocket) {
        val buf = ByteArray(128)
        val p = DatagramPacket(buf, buf.size)
        var last = System.currentTimeMillis()
        while (isActive) {
            try {
                p.length = buf.size
                rx.receive(p)
                val t = parseTelemetry(buf, p.length) ?: continue // short packets = pairing echoes
                last = System.currentTimeMillis()
                stale.value = false
                telemetry.value = t
            } catch (_: SocketTimeoutException) {
                if (telemetry.value != null && System.currentTimeMillis() - last > 2500) stale.value = true
            }
        }
    }
}
