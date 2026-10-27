package app.freemote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.freemote.data.Tv
import app.freemote.data.TvDiscovery
import app.freemote.data.TvStore
import app.freemote.protocol.Identity
import app.freemote.protocol.PairingClient
import app.freemote.protocol.RemoteClient
import app.freemote.protocol.TvApp
import app.freemote.protocol.TvKey
import app.freemote.protocol.WrongCodeException
import app.freemote.ui.ConnStatus
import app.freemote.ui.PairState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object List : Screen
    data class Pair(val tv: Tv) : Screen
    data class Remote(val tv: Tv) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = TvStore(app)
    val discovery = TvDiscovery(app)
    val saved = MutableStateFlow(store.load())
    val screen = MutableStateFlow<Screen>(Screen.List)
    val pairState = MutableStateFlow<PairState>(PairState.Starting)
    val conn = MutableStateFlow<ConnStatus>(ConnStatus.Connecting)

    private var identity: Identity? = null
    private var pairing: PairingClient? = null
    private var remote: RemoteClient? = null

    private suspend fun id(): Identity = withContext(Dispatchers.IO) {
        identity ?: Identity.loadOrCreate(getApplication<Application>().filesDir).also { identity = it }
    }

    fun scan() = discovery.start()

    fun pick(tv: Tv) {
        discovery.stop()
        val known = saved.value.firstOrNull { it.host == tv.host }
        if (known?.paired == true) openRemote(known) else startPairing(tv)
    }

    fun forget(tv: Tv) { store.forget(tv.host); saved.value = store.load() }

    fun startPairing(tv: Tv) {
        screen.value = Screen.Pair(tv)
        pairState.value = PairState.Starting
        viewModelScope.launch {
            pairState.value = withContext(Dispatchers.IO) {
                try {
                    pairing?.close()
                    val p = PairingClient(id(), tv.host)
                    pairing = p
                    p.start()
                    PairState.EnterCode(null)
                } catch (e: Exception) {
                    PairState.Failed("Couldn't reach the TV. Is it on and on the same Wi-Fi? (${e.message})")
                }
            }
        }
    }

    fun submitCode(tv: Tv, code: String) {
        val p = pairing ?: return startPairing(tv)
        pairState.value = PairState.Checking
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try { p.finish(code); null } catch (e: WrongCodeException) { e } catch (e: Exception) { e }
            }
            when (result) {
                null -> {
                    p.close(); pairing = null
                    val paired = tv.copy(paired = true)
                    store.save(paired); saved.value = store.load()
                    openRemote(paired)
                }
                is WrongCodeException -> pairState.value = PairState.EnterCode(result.message)
                else -> pairState.value = PairState.Failed("Pairing failed: ${result.message}")
            }
        }
    }

    fun openRemote(tv: Tv) {
        screen.value = Screen.Remote(tv)
        connect(tv)
    }

    fun connect(tv: Tv) {
        conn.value = ConnStatus.Connecting
        viewModelScope.launch {
            conn.value = withContext(Dispatchers.IO) {
                try {
                    remote?.close()
                    remote = null
                    var self: RemoteClient? = null
                    val r = RemoteClient(id(), tv.host, listener = object : RemoteClient.Listener {
                        override fun onDisconnected(error: Throwable?) {
                            if (remote === self) {
                                remote = null
                                conn.value = ConnStatus.Lost("Connection to the TV was lost.")
                            }
                        }
                    })
                    self = r
                    r.connect()
                    remote = r
                    ConnStatus.Connected
                } catch (e: javax.net.ssl.SSLException) {
                    ConnStatus.NeedsPairing
                } catch (e: Exception) {
                    ConnStatus.Lost("Couldn't connect: ${e.message}")
                }
            }
        }
    }

    fun key(k: TvKey) { remote?.sendKey(k) ?: reconnectIfIdle() }
    fun app(a: TvApp) { remote?.launchApp(a.link) ?: reconnectIfIdle() }
    fun text(t: String, keysOnly: Boolean) { remote?.sendText(t, keysOnly) }

    private fun reconnectIfIdle() {
        (screen.value as? Screen.Remote)?.let { if (conn.value !is ConnStatus.Connecting) connect(it.tv) }
    }

    fun back() {
        remote?.close(); remote = null
        pairing?.close(); pairing = null
        screen.value = Screen.List
        scan()
    }

    override fun onCleared() { remote?.close(); pairing?.close(); discovery.stop() }
}
