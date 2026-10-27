package app.freemote.protocol

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date

/** The app's own self-signed client certificate. Created once, stored in app storage. */
class Identity(val privateKey: PrivateKey, val certificate: X509Certificate) {
    val publicKey: RSAPublicKey get() = certificate.publicKey as RSAPublicKey

    fun keyStore(): KeyStore = KeyStore.getInstance("PKCS12").apply {
        load(null, null)
        setKeyEntry(ALIAS, privateKey, PASSWORD, arrayOf(certificate))
    }

    companion object {
        const val ALIAS = "freemote"
        // Protects the file at rest inside the app's private storage only.
        internal val PASSWORD = "freemote-local".toCharArray()
        private const val FILE = "freemote-client.p12"

        fun generate(commonName: String = "FreeMote"): Identity {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048, SecureRandom())
            val kp = kpg.generateKeyPair()
            val name = X500Name("CN=$commonName, O=FreeMote")
            val now = System.currentTimeMillis()
            val builder = JcaX509v3CertificateBuilder(
                name, BigInteger(64, SecureRandom()).abs().add(BigInteger.ONE),
                Date(now - 24L * 3600 * 1000), Date(now + 20L * 365 * 24 * 3600 * 1000),
                name, kp.public,
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
            val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
            return Identity(kp.private, cert)
        }

        /** Loads the saved identity from [dir], or creates and saves a new one. */
        fun loadOrCreate(dir: File): Identity {
            val f = File(dir, FILE)
            if (f.exists()) {
                runCatching {
                    val ks = KeyStore.getInstance("PKCS12")
                    f.inputStream().use { ks.load(it, PASSWORD) }
                    val key = ks.getKey(ALIAS, PASSWORD) as PrivateKey
                    val cert = ks.getCertificate(ALIAS) as X509Certificate
                    return Identity(key, cert)
                }
            }
            val id = generate()
            val tmp = File(dir, "$FILE.tmp")
            tmp.outputStream().use { id.keyStore().store(it, PASSWORD) }
            tmp.renameTo(f)
            return id
        }
    }
}
