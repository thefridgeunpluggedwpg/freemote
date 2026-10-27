package app.freemote.protocol

import com.google.polo.wire.protobuf.PoloProto.Configuration
import com.google.polo.wire.protobuf.PoloProto.Options
import com.google.polo.wire.protobuf.PoloProto.OuterMessage
import com.google.polo.wire.protobuf.PoloProto.PairingRequest
import com.google.polo.wire.protobuf.PoloProto.Secret
import com.google.protobuf.ByteString
import java.io.Closeable
import java.io.IOException
import javax.net.ssl.SSLSocket

class PairingException(message: String) : IOException(message)
class WrongCodeException : IOException("That code doesn't match. Check the code on the TV and try again.")

/**
 * Pairing over TLS (port 6467), following androidtvremote2's pairing.py:
 * request -> ack -> options -> configuration -> configuration_ack (TV shows code)
 * -> secret -> secret_ack.  Blocking: call from a background thread.
 */
class PairingClient(
    private val identity: Identity,
    private val host: String,
    private val port: Int = 6467,
    private val clientName: String = "FreeMote",
) : Closeable {
    private var socket: SSLSocket? = null

    private fun base(): OuterMessage.Builder = OuterMessage.newBuilder()
        .setProtocolVersion(2).setStatus(OuterMessage.Status.STATUS_OK)

    private fun send(m: OuterMessage) {
        val out = socket!!.outputStream
        out.write(Framing.frame(m.toByteArray()))
        out.flush()
    }

    private fun receive(): OuterMessage {
        val raw = Framing.readFrame(socket!!.inputStream) ?: throw PairingException("The TV closed the connection")
        val m = OuterMessage.parseFrom(raw)
        if (m.status != OuterMessage.Status.STATUS_OK) {
            if (m.status == OuterMessage.Status.STATUS_BAD_SECRET) throw WrongCodeException()
            throw PairingException("The TV said: ${m.status}")
        }
        return m
    }

    private val hexEncoding = Options.Encoding.newBuilder()
        .setType(Options.Encoding.EncodingType.ENCODING_TYPE_HEXADECIMAL).setSymbolLength(6).build()

    /** Connects and asks the TV to show a pairing code. */
    fun start() {
        socket = Tls.connect(identity, host, port, timeoutMs = 10_000)
        send(base().setPairingRequest(
            PairingRequest.newBuilder().setClientName(clientName).setServiceName("atvremote")).build())
        while (true) {
            val m = receive()
            when {
                m.hasPairingRequestAck() -> send(base().setOptions(
                    Options.newBuilder().setPreferredRole(Options.RoleType.ROLE_TYPE_INPUT)
                        .addInputEncodings(hexEncoding)).build())
                m.hasOptions() -> send(base().setConfiguration(
                    Configuration.newBuilder().setClientRole(Options.RoleType.ROLE_TYPE_INPUT)
                        .setEncoding(hexEncoding)).build())
                m.hasConfigurationAck() -> return
                else -> throw PairingException("Unexpected reply from the TV")
            }
        }
    }

    /** Sends the code typed by the user. Throws [WrongCodeException] if it can't be right. */
    fun finish(code: String) {
        val c = code.trim().uppercase()
        if (!PairingSecret.isWellFormed(c)) throw WrongCodeException()
        val s = socket ?: throw PairingException("Not connected")
        val hash = PairingSecret.compute(identity.publicKey, Tls.serverKey(s), c)
        if (!PairingSecret.matchesCode(hash, c)) throw WrongCodeException()
        send(base().setSecret(Secret.newBuilder().setSecret(ByteString.copyFrom(hash))).build())
        val m = receive()
        if (!m.hasSecretAck()) throw PairingException("The TV didn't accept the pairing")
    }

    override fun close() { runCatching { socket?.close() }; socket = null }
}
