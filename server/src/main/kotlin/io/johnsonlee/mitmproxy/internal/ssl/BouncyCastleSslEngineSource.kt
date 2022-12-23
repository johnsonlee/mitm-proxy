package io.johnsonlee.mitmproxy.internal.ssl

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import io.johnsonlee.mitmproxy.internal.util.MillisecondsDuration
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.littleshoot.proxy.SslEngineSource
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLParameters
import javax.net.ssl.TrustManager

/**
 * A [SslEngineSource] which creates a key store with a Root Certificate
 * Authority. The certificates are generated lazily if the given key store file
 * doesn't yet exist.
 *
 * The root certificate is exported in PEM format to be used in a browser. The
 * proxy application presents for every host a dynamically created certificate
 * to the browser, signed by this certificate authority.
 *
 * This facilitates the proxy to handle as a "Man In The Middle" to filter the
 * decrypted content in clear text.
 *
 * The hard part was done by mawoki. It's derived from Zed Attack Proxy (ZAP).
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2011 mawoki@ymail.com Licensed under the Apache License, Version 2.0
 */
internal class BouncyCastleSslEngineSource @JvmOverloads constructor(
        private val authority: Authority,
        private val trustAllServers: Boolean,
        private val sendCerts: Boolean,
        private val serverSSLContexts: Cache<String, SSLContext> = initDefaultCertificateCache()
) : SslEngineSource {

    private val sslContext: SSLContext
    private val caCert: Certificate
    private val caPrivKey: PrivateKey

    init {
        val ks = loadKeyStore()
        caCert = ks.getCertificate(authority.alias)
        caPrivKey = ks.getKey(authority.alias, authority.password) as PrivateKey
        val trustManagers: Array<TrustManager> = if (trustAllServers) {
            InsecureTrustManagerFactory.INSTANCE.trustManagers
        } else {
            arrayOf(MergeTrustManager(ks))
        }
        val keyManagers: Array<KeyManager> = if (sendCerts) {
            CertificateHelper.getKeyManagers(ks, authority)
        } else {
            emptyArray()
        }
        sslContext = CertificateHelper.newClientContext(keyManagers, trustManagers)
        val sslEngine = sslContext.createSSLEngine()
        if (!tryHostNameVerificationJava7(sslEngine)) {
            logger.warn("Host Name Verification is not supported, causes insecure HTTPS connection to upstream servers.")
        }
    }

    private fun filterWeakCipherSuites(sslEngine: SSLEngine) {
        val ciphers = sslEngine.enabledCipherSuites.filterNot {
            it == "TLS_DHE_RSA_WITH_AES_128_CBC_SHA" || it == "TLS_DHE_RSA_WITH_AES_256_CBC_SHA"
        }
        sslEngine.enabledCipherSuites = ciphers.toTypedArray()
        if (logger.isDebugEnabled) {
            if (sslEngine.useClientMode) {
                logger.debug(ciphers.joinToString(", ", "Enabled server cipher suites: "))
            } else {
                val host: String = sslEngine.peerHost
                val port: Int = sslEngine.peerPort
                logger.debug("Enabled client {}:{} cipher suites:", host, port)
            }
        }
    }

    override fun newSslEngine(): SSLEngine {
        return sslContext.createSSLEngine().also(::filterWeakCipherSuites)
    }

    override fun newSslEngine(remoteHost: String, remotePort: Int): SSLEngine {
        val sslEngine: SSLEngine = sslContext.createSSLEngine(remoteHost, remotePort)
        sslEngine.useClientMode = true
        if (!tryHostNameVerificationJava7(sslEngine)) {
            logger.debug("Host Name Verification is not supported, causes insecure HTTPS connection")
        }
        filterWeakCipherSuites(sslEngine)
        return sslEngine
    }

    private fun tryHostNameVerificationJava7(sslEngine: SSLEngine): Boolean {
        for (method in SSLParameters::class.java.methods) {
            // method is available since Java 7
            if ("setEndpointIdentificationAlgorithm" == method.name) {
                val sslParams = SSLParameters()
                try {
                    method.invoke(sslParams, "HTTPS")
                } catch (e: IllegalAccessException) {
                    logger.debug("SSLParameters#setEndpointIdentificationAlgorithm", e)
                    return false
                } catch (e: InvocationTargetException) {
                    logger.debug("SSLParameters#setEndpointIdentificationAlgorithm", e)
                    return false
                }
                sslEngine.sslParameters = sslParams
                return true
            }
        }
        return false
    }

    private fun loadKeyStore(): KeyStore {
        return javaClass.getResourceAsStream("$PEM_CERT_PREFIX/${authority.alias}$KEY_STORE_FILE_EXTENSION").use {
            KeyStore.getInstance(KEY_STORE_TYPE).apply {
                load(it, authority.password)
            }
        }
    }

    private fun createServerContext(commonName: String, subjectAlternativeNames: SubjectAlternativeNameHolder): SSLContext {
        val duration = MillisecondsDuration()
        val ks = CertificateHelper.createServerCertificate(commonName, subjectAlternativeNames, authority, caCert, caPrivKey)
        val keyManagers = CertificateHelper.getKeyManagers(ks, authority)
        val result = CertificateHelper.newServerContext(keyManagers)
        logger.info("Impersonated {} in {}ms", commonName, duration)
        return result
    }

    @JvmSynthetic
    internal fun createCertForHost(commonName: String, subjectAlternativeNames: SubjectAlternativeNameHolder): SSLEngine {
        return serverSSLContexts[commonName, Callable {
            createServerContext(commonName, subjectAlternativeNames)
        }].createSSLEngine()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BouncyCastleSslEngineSource::class.java)

        private fun initDefaultCertificateCache(): Cache<String, SSLContext> {
            return CacheBuilder.newBuilder() //
                    .expireAfterAccess(5, TimeUnit.MINUTES) //
                    .concurrencyLevel(16) //
                    .build()
        }


    }
}
