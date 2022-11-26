package io.johnsonlee.mitmproxy.internal.ssl

import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal class MergeTrustManager(trustStore: KeyStore) : X509TrustManager {

    private val addedTm: X509TrustManager = defaultTrustManager(trustStore)
    private val javaTm: X509TrustManager = defaultTrustManager(null)

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        try {
            addedTm.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            javaTm.checkServerTrusted(chain, authType)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return addedTm.acceptedIssuers + javaTm.acceptedIssuers
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        try {
            javaTm.checkClientTrusted(chain, authType)
        } catch (e: CertificateException) {
            addedTm.checkClientTrusted(chain, authType)
        }
    }

    private fun defaultTrustManager(trustStore: KeyStore?): X509TrustManager {
        val tma: String = TrustManagerFactory.getDefaultAlgorithm()
        val tmf: TrustManagerFactory = TrustManagerFactory.getInstance(tma)
        tmf.init(trustStore)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
                ?: throw IllegalStateException("Missed X509TrustManager in " + tmf.trustManagers.contentToString())
    }
}