package app.freemote.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.ArrayDeque

/** Finds TVs on the Wi-Fi with mDNS service `_androidtvremote2._tcp`. */
class TvDiscovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var lock: WifiManager.MulticastLock? = null
    private val _found = MutableStateFlow<List<Tv>>(emptyList())
    val found: StateFlow<List<Tv>> = _found
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    // Older Android can only resolve one service at a time, so queue them.
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var listener: NsdManager.DiscoveryListener? = null

    @Synchronized
    fun start() {
        stop()
        _found.value = emptyList()
        lock = wifi.createMulticastLock("freemote").apply { setReferenceCounted(false); acquire() }
        val l = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(t: String?, e: Int) { _scanning.value = false }
            override fun onStopDiscoveryFailed(t: String?, e: Int) {}
            override fun onDiscoveryStarted(t: String?) { _scanning.value = true }
            override fun onDiscoveryStopped(t: String?) { _scanning.value = false }
            override fun onServiceFound(info: NsdServiceInfo) { enqueue(info) }
            override fun onServiceLost(info: NsdServiceInfo) {
                _found.value = _found.value.filterNot { it.name == info.serviceName }
            }
        }
        listener = l
        runCatching { nsd.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    @Synchronized
    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        runCatching { lock?.release() }
        lock = null
        _scanning.value = false
    }

    @Synchronized
    private fun enqueue(info: NsdServiceInfo) {
        queue.add(info)
        next()
    }

    @Synchronized
    private fun next() {
        if (resolving) return
        val info = queue.poll() ?: return
        resolving = true
        @Suppress("DEPRECATION")
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(i: NsdServiceInfo?, e: Int) = done()
            override fun onServiceResolved(i: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                val host = i.host?.hostAddress
                if (host != null) {
                    _found.value = _found.value.filterNot { it.host == host } + Tv(i.serviceName, host)
                }
                done()
            }
        })
    }

    @Synchronized
    private fun done() { resolving = false; next() }

    companion object { const val SERVICE = "_androidtvremote2._tcp." }
}
