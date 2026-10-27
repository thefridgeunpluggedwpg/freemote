package app.freemote.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey

/** Test vector made by tools/make_pairing_vector.py using androidtvremote2's hash code. */
class PairingSecretTest {
    private fun key(name: String): RSAPublicKey {
        val stream = javaClass.getResourceAsStream("/pairing/$name.pem")!!
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(stream) as X509Certificate
        return cert.publicKey as RSAPublicKey
    }

    private val vector = javaClass.getResourceAsStream("/pairing/vector.json")!!.reader().readText()
    private val code = Regex("\"code\": \"(\\w+)\"").find(vector)!!.groupValues[1]
    private val sha = Regex("\"sha256\": \"(\\w+)\"").find(vector)!!.groupValues[1]

    @Test fun matchesReferenceImplementation() {
        val hash = PairingSecret.compute(key("client"), key("server"), code)
        assertEquals(sha, hash.joinToString("") { "%02x".format(it) })
        assertTrue(PairingSecret.matchesCode(hash, code))
    }

    @Test fun lowercaseCodeGivesSameHash() {
        val hash = PairingSecret.compute(key("client"), key("server"), code.lowercase())
        assertEquals(sha, hash.joinToString("") { "%02x".format(it) })
    }

    @Test fun wrongFirstByteIsRejected() {
        val hash = PairingSecret.compute(key("client"), key("server"), code)
        val wrong = "%02X".format((code.substring(0, 2).toInt(16) + 1) and 0xFF) + code.substring(2)
        assertFalse(PairingSecret.matchesCode(hash, wrong))
    }

    @Test fun swappingKeysChangesHash() {
        val a = PairingSecret.compute(key("client"), key("server"), code)
        val b = PairingSecret.compute(key("server"), key("client"), code)
        assertFalse(a.contentEquals(b))
    }

    @Test fun exponentEncodingMatchesPython() {
        // python: bytes.fromhex(f"0{65537:X}") == b"\x01\x00\x01"
        assertEquals(listOf<Byte>(1, 0, 1), PairingSecret.hexBytes(BigInteger.valueOf(65537)).toList())
    }

    @Test fun codeFormat() {
        assertTrue(PairingSecret.isWellFormed("9E1A2B"))
        assertTrue(PairingSecret.isWellFormed("abcdef"))
        assertFalse(PairingSecret.isWellFormed("12345"))
        assertFalse(PairingSecret.isWellFormed("12345G"))
    }
}
