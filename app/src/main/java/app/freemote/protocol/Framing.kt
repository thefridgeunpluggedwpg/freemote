package app.freemote.protocol

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * Android TV Remote v2 messages are protobuf messages, each prefixed with its
 * length as a protobuf varint (same as protobuf's writeDelimitedTo).
 */
object Framing {
    const val MAX_MESSAGE = 1 shl 20

    fun encodeVarint(value: Int): ByteArray {
        require(value >= 0) { "negative length" }
        val out = ByteArrayOutputStream()
        var v = value
        while (v >= 0x80) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v)
        return out.toByteArray()
    }

    fun frame(payload: ByteArray): ByteArray = encodeVarint(payload.size) + payload

    /** Reads one framed message, or returns null on a clean end of stream. */
    fun readFrame(input: InputStream): ByteArray? {
        var result = 0
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) {
                if (shift == 0) return null
                throw EOFException("stream ended inside a length prefix")
            }
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            if (shift > 28) throw IOException("length prefix too long")
        }
        if (result > MAX_MESSAGE) throw IOException("message too large: $result")
        val buf = ByteArray(result)
        var off = 0
        while (off < result) {
            val n = input.read(buf, off, result - off)
            if (n < 0) throw EOFException("stream ended inside a message")
            off += n
        }
        return buf
    }
}
