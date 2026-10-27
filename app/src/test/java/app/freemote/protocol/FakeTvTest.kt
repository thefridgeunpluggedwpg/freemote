package app.freemote.protocol

import app.freemote.proto.remote.RemoteProto.*
import com.google.polo.wire.protobuf.PoloProto.*
import com.google.protobuf.ByteString
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

/**
 * A tiny fake TV (TLS server) that plays the TV side of the pairing and
 * remote protocols, so the real client code is exercised end-to-end over TLS.
 */
class FakeTvTest {
    private val tvIdentity = Identity.generate("FakeTV")
    private val phone = Identity.generate("Phone")
    private val servers = mutableListOf<SSLServerSocket>()

    private fun server(): SSLServerSocket {
        val ss = Tls.context(tvIdentity).serverSocketFactory.createServerSocket(0) as SSLServerSocket
        ss.needClientAuth = true
        servers += ss
        return ss
    }

    @After fun tearDown() = servers.forEach { it.close() }

    private fun SSLSocket.sendPolo(m: OuterMessage) { outputStream.write(Framing.frame(m.toByteArray())); outputStream.flush() }
    private fun SSLSocket.readPolo() = OuterMessage.parseFrom(Framing.readFrame(inputStream))
    private fun ok() = OuterMessage.newBuilder().setProtocolVersion(2).setStatus(OuterMessage.Status.STATUS_OK)

    /** Fake TV pairing. Shows (returns) the code via [codeOut] and records the secret it got. */
    private fun fakePairingTv(ss: SSLServerSocket, codeOut: LinkedBlockingQueue<String>, result: LinkedBlockingQueue<Boolean>) = thread {
        ss.accept().use { s0 ->
            val s = s0 as SSLSocket
            assertTrue(s.readPolo().hasPairingRequest()); s.sendPolo(ok().setPairingRequestAck(PairingRequestAck.getDefaultInstance()).build())
            val opts = s.readPolo(); assertTrue(opts.hasOptions()); s.sendPolo(ok().setOptions(opts.options).build())
            assertTrue(s.readPolo().hasConfiguration()); s.sendPolo(ok().setConfigurationAck(ConfigurationAck.getDefaultInstance()).build())
            val clientKey = (s.session.peerCertificates[0] as X509Certificate).publicKey as RSAPublicKey
            val probe = PairingSecret.compute(clientKey, tvIdentity.publicKey, "00C0DE")
            val code = "%02X".format(probe[0]) + "C0DE"
            codeOut.put(code)
            val secret = runCatching { s.readPolo() }.getOrNull()
            val good = secret != null && secret.hasSecret() &&
                secret.secret.secret == ByteString.copyFrom(PairingSecret.compute(clientKey, tvIdentity.publicKey, code))
            result.put(good)
            if (good) s.sendPolo(ok().setSecretAck(SecretAck.newBuilder().setSecret(secret!!.secret.secret)).build())
        }
    }

    @Test fun pairsWithCorrectCode() {
        val ss = server(); val codes = LinkedBlockingQueue<String>(); val res = LinkedBlockingQueue<Boolean>()
        fakePairingTv(ss, codes, res)
        PairingClient(phone, "127.0.0.1", ss.localPort).use { c ->
            c.start()
            c.finish(codes.poll(5, TimeUnit.SECONDS)!!)
        }
        assertEquals(true, res.poll(5, TimeUnit.SECONDS))
    }

    @Test fun rejectsWrongCodeBeforeSending() {
        val ss = server(); val codes = LinkedBlockingQueue<String>(); val res = LinkedBlockingQueue<Boolean>()
        fakePairingTv(ss, codes, res)
        PairingClient(phone, "127.0.0.1", ss.localPort).use { c ->
            c.start()
            val real = codes.poll(5, TimeUnit.SECONDS)!!
            val wrong = "%02X".format((real.substring(0, 2).toInt(16) + 1) and 0xFF) + real.substring(2)
            assertThrows(WrongCodeException::class.java) { c.finish(wrong) }
        }
    }

    @Test fun identityIsSavedAndReused() {
        val dir = createTempDirFile()
        val a = Identity.loadOrCreate(dir)
        val b = Identity.loadOrCreate(dir)
        assertEquals(a.certificate, b.certificate)
        assertEquals(2048, a.publicKey.modulus.bitLength())
    }

    private fun createTempDirFile(): File = kotlin.io.path.createTempDirectory("freemote").toFile()

    @Test fun remoteSessionHandshakeKeysTextAndApps() {
        val ss = server()
        val got = LinkedBlockingQueue<RemoteMessage>()
        val ready = CountDownLatch(1)
        fun SSLSocket.send(m: RemoteMessage) { outputStream.write(Framing.frame(m.toByteArray())); outputStream.flush() }
        fun SSLSocket.read() = RemoteMessage.parseFrom(Framing.readFrame(inputStream))
        thread {
            val s = ss.accept() as SSLSocket
            s.send(RemoteMessage.newBuilder().setRemoteConfigure(RemoteConfigure.newBuilder().setCode1(622)
                .setDeviceInfo(RemoteDeviceInfo.newBuilder().setModel("FakeTV").setVendor("Test"))).build())
            got.put(s.read())
            s.send(RemoteMessage.newBuilder().setRemoteSetActive(RemoteSetActive.getDefaultInstance()).build())
            got.put(s.read())
            s.send(RemoteMessage.newBuilder().setRemoteStart(RemoteStart.newBuilder().setStarted(true)).build())
            s.send(RemoteMessage.newBuilder().setRemotePingRequest(RemotePingRequest.newBuilder().setVal1(42)).build())
            got.put(s.read())
            s.send(RemoteMessage.newBuilder().setRemoteImeBatchEdit(RemoteImeBatchEdit.newBuilder().setImeCounter(5).setFieldCounter(6)).build())
            ready.countDown()
            repeat(4) { got.put(s.read()) }
            s.close()
        }
        val client = RemoteClient(phone, "127.0.0.1", ss.localPort)
        client.connect()
        val cfg = got.poll(5, TimeUnit.SECONDS)!!
        assertEquals(RemoteClient.FEATURES and 622, cfg.remoteConfigure.code1)
        assertEquals("atvremote", cfg.remoteConfigure.deviceInfo.packageName)
        assertTrue(got.poll(5, TimeUnit.SECONDS)!!.hasRemoteSetActive())
        assertEquals(42, got.poll(5, TimeUnit.SECONDS)!!.remotePingResponse.val1)
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        // wait until the client has processed the IME message
        val deadline = System.currentTimeMillis() + 5000
        while (!client.imeActive && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(client.ready)

        client.sendKey(TvKey.UP)
        client.sendText("hi")
        client.launchApp(TvApp.YOUTUBE.link)
        client.sendText("a", forceKeys = true)

        assertEquals(RemoteKeyCode.KEYCODE_DPAD_UP, got.poll(5, TimeUnit.SECONDS)!!.remoteKeyInject.keyCode)
        val ime = got.poll(5, TimeUnit.SECONDS)!!.remoteImeBatchEdit
        assertEquals(5, ime.imeCounter); assertEquals(6, ime.fieldCounter); assertEquals("hi", ime.getEditInfo(0).textFieldStatus.value)
        assertEquals("https://www.youtube.com", got.poll(5, TimeUnit.SECONDS)!!.remoteAppLinkLaunchRequest.appLink)
        assertEquals(RemoteKeyCode.KEYCODE_A, got.poll(5, TimeUnit.SECONDS)!!.remoteKeyInject.keyCode)
        client.close()
    }
}
