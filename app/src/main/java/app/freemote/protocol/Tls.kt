package app.freemote.protocol

import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object Tls {
    /** TVs use self-signed certificates, so (like the official remote) we accept the TV's cert. */
    private object TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    fun context(identity: Identity): SSLContext {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(identity.keyStore(), Identity.PASSWORD)
        return SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, arrayOf<TrustManager>(TrustAll), null)
        }
    }

    fun connect(identity: Identity, host: String, port: Int, timeoutMs: Int = 6000): SSLSocket {
        val s = context(identity).socketFactory.createSocket() as SSLSocket
        s.connect(InetSocketAddress(host, port), timeoutMs)
        s.soTimeout = timeoutMs
        s.startHandshake()
        return s
    }

    fun serverKey(s: SSLSocket): RSAPublicKey =
        (s.session.peerCertificates[0] as X509Certificate).publicKey as RSAPublicKey
}
