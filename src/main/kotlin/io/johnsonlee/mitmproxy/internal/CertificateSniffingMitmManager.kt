package io.johnsonlee.mitmproxy.internal

import io.netty.handler.codec.http.HttpRequest
import org.littleshoot.proxy.MitmManager
import org.slf4j.LoggerFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession

internal class CertificateSniffingMitmManager @JvmOverloads constructor(
        authority: Authority = Authority()
) : MitmManager {

    private val sslEngineSource: BouncyCastleSslEngineSource = try {
        BouncyCastleSslEngineSource(authority = authority, trustAllServers = true, sendCerts = true)
    } catch (e: Exception) {
        throw RootCertificateException("Errors during assembling root CA.", e)
    }

    override fun serverSslEngine(peerHost: String, peerPort: Int): SSLEngine {
        return sslEngineSource.newSslEngine(peerHost, peerPort)
    }

    override fun serverSslEngine(): SSLEngine {
        return sslEngineSource.newSslEngine()
    }

    override fun clientSslEngineFor(httpRequest: HttpRequest, serverSslSession: SSLSession): SSLEngine {
        return try {
            val upstreamCert = getCertificateFromSession(serverSslSession)
            val commonName = getCommonName(upstreamCert)
            val san = SubjectAlternativeNameHolder().apply {
                addAll(upstreamCert.subjectAlternativeNames)
            }
            sslEngineSource.createCertForHost(commonName, san)
        } catch (e: Exception) {
            throw FakeCertificateException("Creation dynamic certificate failed", e)
        }
    }

    private fun getCertificateFromSession(sslSession: SSLSession): X509Certificate {
        return sslSession.peerCertificates.firstOrNull {
            it is X509Certificate
        } as? X509Certificate
                ?: throw IllegalStateException("Required java.security.cert.X509Certificate, found: ${sslSession.peerCertificates.contentToString()}")
    }

    private fun getCommonName(c: X509Certificate): String {
        logger.debug("Subject DN principal name: {}", c.subjectDN.name)

        return c.subjectDN.name.split(",\\s*").firstOrNull {
            it.startsWith("CN=")
        }?.substring(3) ?: throw IllegalStateException("Missed CN in Subject DN: ${c.subjectDN}")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CertificateSniffingMitmManager::class.java)
    }
}