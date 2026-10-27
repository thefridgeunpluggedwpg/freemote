package app.freemote.protocol

import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

/**
 * Pairing secret, as in androidtvremote2 (pairing.py) and Google's polo
 * implementation: SHA-256 over client modulus, client exponent, server modulus,
 * server exponent and the last 4 hex digits of the code shown on the TV.
 * The first byte of the hash must equal the first 2 hex digits of the code.
 */
object PairingSecret {
    private val HEX = Regex("^[0-9A-Fa-f]{6}$")

    fun isWellFormed(code: String) = HEX.matches(code)

    /** Big-endian bytes of the number's hex form (left-padded to even length, no sign byte). */
    fun hexBytes(v: BigInteger): ByteArray {
        var s = v.toString(16)
        if (s.length % 2 == 1) s = "0$s"
        return ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    fun compute(client: RSAPublicKey, server: RSAPublicKey, code: String): ByteArray {
        require(isWellFormed(code)) { "Code must be 6 characters (0-9, A-F)" }
        val md = MessageDigest.getInstance("SHA-256")
        md.update(hexBytes(client.modulus))
        md.update(hexBytes(client.publicExponent))
        md.update(hexBytes(server.modulus))
        md.update(hexBytes(server.publicExponent))
        md.update(hexBytes(BigInteger(code.substring(2), 16)).let { pad(it, 2) })
        return md.digest()
    }

    fun matchesCode(hash: ByteArray, code: String): Boolean =
        isWellFormed(code) && (hash[0].toInt() and 0xFF) == code.substring(0, 2).toInt(16)

    private fun pad(b: ByteArray, len: Int): ByteArray =
        if (b.size >= len) b else ByteArray(len - b.size) + b
}
