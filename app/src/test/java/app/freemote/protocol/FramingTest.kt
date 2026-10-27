package app.freemote.protocol

import app.freemote.proto.remote.RemoteProto.RemoteKeyCode
import app.freemote.proto.remote.RemoteProto.RemoteMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException

class FramingTest {
    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    @Test fun varints() {
        assertArrayEquals(hex("00"), Framing.encodeVarint(0))
        assertArrayEquals(hex("7f"), Framing.encodeVarint(127))
        assertArrayEquals(hex("80 01"), Framing.encodeVarint(128))
        assertArrayEquals(hex("ac 02"), Framing.encodeVarint(300))
    }

    @Test fun keyPressHasExpectedWireBytes() {
        // RemoteMessage{remote_key_inject{key_code: DPAD_UP(19), direction: SHORT(3)}}
        // field 10 (0x52) len 4: [08 13] [10 03]; frame prefix = 6
        val framed = Framing.frame(keyMessage(RemoteKeyCode.KEYCODE_DPAD_UP).toByteArray())
        assertArrayEquals(hex("06 52 04 08 13 10 03"), framed)
    }

    @Test fun roundTripsSeveralMessagesInOneStream() {
        val a = keyMessage(RemoteKeyCode.KEYCODE_HOME)
        val b = imeMessage("hello", 3, 7)
        val big = ByteArray(300) { 1 }
        val stream = ByteArrayInputStream(Framing.frame(a.toByteArray()) + Framing.frame(b.toByteArray()) + Framing.frame(big))
        assertEquals(a, RemoteMessage.parseFrom(Framing.readFrame(stream)))
        assertEquals(b, RemoteMessage.parseFrom(Framing.readFrame(stream)))
        assertArrayEquals(big, Framing.readFrame(stream))
        assertNull(Framing.readFrame(stream))
    }

    @Test(expected = EOFException::class)
    fun truncatedMessageFailsLoudly() {
        Framing.readFrame(ByteArrayInputStream(hex("05 01 02")))
    }

    @Test fun imeMessageMatchesReferenceShape() {
        val m = imeMessage("abc", 4, 9).remoteImeBatchEdit
        assertEquals(4, m.imeCounter)
        assertEquals(9, m.fieldCounter)
        val e = m.getEditInfo(0)
        assertEquals(1, e.insert)
        assertEquals(2, e.textFieldStatus.start)
        assertEquals(2, e.textFieldStatus.end)
        assertEquals("abc", e.textFieldStatus.value)
    }
}
