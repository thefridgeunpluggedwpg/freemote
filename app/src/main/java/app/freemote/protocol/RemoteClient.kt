package app.freemote.protocol

import app.freemote.proto.remote.RemoteProto.RemoteAppLinkLaunchRequest
import app.freemote.proto.remote.RemoteProto.RemoteConfigure
import app.freemote.proto.remote.RemoteProto.RemoteDeviceInfo
import app.freemote.proto.remote.RemoteProto.RemoteDirection
import app.freemote.proto.remote.RemoteProto.RemoteEditInfo
import app.freemote.proto.remote.RemoteProto.RemoteImeBatchEdit
import app.freemote.proto.remote.RemoteProto.RemoteImeObject
import app.freemote.proto.remote.RemoteProto.RemoteKeyCode
import app.freemote.proto.remote.RemoteProto.RemoteKeyInject
import app.freemote.proto.remote.RemoteProto.RemoteMessage
import app.freemote.proto.remote.RemoteProto.RemotePingResponse
import app.freemote.proto.remote.RemoteProto.RemoteSetActive
import java.io.Closeable
import java.util.concurrent.Executors
import javax.net.ssl.SSLSocket

/**
 * Remote control over TLS (port 6466), following androidtvremote2's remote.py.
 * [connect] blocks until the TLS handshake is done; a reader thread answers the TV.
 */
class RemoteClient(
    private val identity: Identity,
    private val host: String,
    private val port: Int = 6466,
    private val listener: Listener = object : Listener {},
) : Closeable {
    interface Listener {
        fun onReady() {}
        fun onDisconnected(error: Throwable?) {}
        fun onVolume(level: Int, max: Int, muted: Boolean) {}
    }

    companion object {
        // PING | KEY | IME | POWER | VOLUME | APP_LINK (see Feature in remote.py)
        const val FEATURES = 1 or 2 or 4 or 32 or 64 or 512
    }

    @Volatile private var socket: SSLSocket? = null
    @Volatile var imeCounter = 0; private set
    @Volatile var imeFieldCounter = 0; private set
    /** True once the TV has told us about an active text field. */
    @Volatile var imeActive = false; private set
    @Volatile var ready = false; private set
    private var active = FEATURES
    private val writer = Executors.newSingleThreadExecutor()

    val isConnected get() = socket?.isClosed == false

    fun connect() {
        val s = Tls.connect(identity, host, port)
        s.soTimeout = 0
        socket = s
        Thread({ readLoop(s) }, "freemote-remote-reader").apply { isDaemon = true }.start()
    }

    private fun readLoop(s: SSLSocket) {
        var err: Throwable? = null
        try {
            while (true) {
                val raw = Framing.readFrame(s.inputStream) ?: break
                handle(RemoteMessage.parseFrom(raw))
            }
        } catch (t: Throwable) { err = t }
        ready = false
        runCatching { s.close() }
        listener.onDisconnected(err)
    }

    internal fun handle(m: RemoteMessage) {
        when {
            m.hasRemoteConfigure() -> {
                active = FEATURES and m.remoteConfigure.code1
                send(RemoteMessage.newBuilder().setRemoteConfigure(
                    RemoteConfigure.newBuilder().setCode1(active).setDeviceInfo(
                        RemoteDeviceInfo.newBuilder().setUnknown1(1).setUnknown2("1")
                            .setPackageName("atvremote").setAppVersion("1.0.0"))).build())
            }
            m.hasRemoteSetActive() -> send(RemoteMessage.newBuilder()
                .setRemoteSetActive(RemoteSetActive.newBuilder().setActive(active)).build())
            m.hasRemotePingRequest() -> send(RemoteMessage.newBuilder().setRemotePingResponse(
                RemotePingResponse.newBuilder().setVal1(m.remotePingRequest.val1)).build())
            m.hasRemoteImeBatchEdit() -> {
                imeCounter = m.remoteImeBatchEdit.imeCounter
                imeFieldCounter = m.remoteImeBatchEdit.fieldCounter
                imeActive = true
            }
            m.hasRemoteImeShowRequest() -> imeActive = true
            m.hasRemoteSetVolumeLevel() -> m.remoteSetVolumeLevel.let {
                listener.onVolume(it.volumeLevel, it.volumeMax, it.volumeMuted)
            }
            m.hasRemoteStart() -> if (!ready) { ready = true; listener.onReady() }
        }
    }

    private fun send(m: RemoteMessage) {
        val s = socket ?: return
        runCatching {
            writer.execute {
                runCatching {
                    synchronized(s) {
                        s.outputStream.write(Framing.frame(m.toByteArray()))
                        s.outputStream.flush()
                    }
                }
            }
        }
    }

    fun sendKey(code: RemoteKeyCode, direction: RemoteDirection = RemoteDirection.SHORT) =
        send(keyMessage(code, direction))

    fun sendKey(key: TvKey) = sendKey(key.code)

    /** Types text: IME batch edit when a text box is active, otherwise one key event per character. */
    fun sendText(text: String, forceKeys: Boolean = false) {
        if (text.isEmpty()) return
        if (imeActive && !forceKeys) send(imeMessage(text, imeCounter, imeFieldCounter))
        else text.forEach { c -> CharKeys.keyFor(c)?.let { sendKey(it) } }
    }

    fun launchApp(link: String) = send(RemoteMessage.newBuilder().setRemoteAppLinkLaunchRequest(
        RemoteAppLinkLaunchRequest.newBuilder().setAppLink(link)).build())

    override fun close() {
        runCatching { socket?.close() }
        socket = null
        writer.shutdown()
    }
}

fun keyMessage(code: RemoteKeyCode, direction: RemoteDirection = RemoteDirection.SHORT): RemoteMessage =
    RemoteMessage.newBuilder().setRemoteKeyInject(
        RemoteKeyInject.newBuilder().setKeyCode(code).setDirection(direction)).build()

/** Same shape as androidtvremote2's send_text: start=end=len-1, insert=1. */
fun imeMessage(text: String, imeCounter: Int, fieldCounter: Int): RemoteMessage {
    val p = text.length - 1
    return RemoteMessage.newBuilder().setRemoteImeBatchEdit(
        RemoteImeBatchEdit.newBuilder().setImeCounter(imeCounter).setFieldCounter(fieldCounter)
            .addEditInfo(RemoteEditInfo.newBuilder().setInsert(1).setTextFieldStatus(
                RemoteImeObject.newBuilder().setStart(p).setEnd(p).setValue(text)))).build()
}
