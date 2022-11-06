package io.johnsonlee.mitmproxy.internal

import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.bc.BcX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileWriter
import java.math.BigInteger
import java.security.Key
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Date
import java.util.Random
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory

/**
 * The P12 format has to be implemented by every vendor. Oracles proprietary
 * JKS type is not available in Android.
 */
internal const val KEY_STORE_TYPE = "PKCS12"
internal const val KEY_STORE_FILE_EXTENSION = ".p12"

/**
 * Generate by openssl:
 * ```bash
 * openssl x509 -inform PEM -subject_hash_old -in src/main/resources/certs/mitmproxy.pem | head -1
 * ```
 */
internal const val PEM_CERT_PREFIX = "/certs"
internal const val PEM_CERT_FILE_PATH = "${PEM_CERT_PREFIX}/b44475dc.0"
internal const val PEM_CERT_FILE_EXTENSION = ".pem"

private const val KEYGEN_ALGORITHM = "RSA"
private const val SECURE_RANDOM_ALGORITHM = "SHA1PRNG"

private const val ROOT_KEYSIZE = 2048
private const val FAKE_KEYSIZE = 1024

/** The milliseconds of a day  */
private const val ONE_DAY = 86400000L

/**
 * Enforce TLS 1.2 if available, since it's not default up to Java 8.
 *
 *
 * Java 7 disables TLS 1.1 and 1.2 for clients. From [Java Cryptography Architecture Oracle Providers Documentation:](http://docs.oracle.com/javase/7/docs/technotes/guides/security/SunProviders.html)
 * Although SunJSSE in the Java SE 7 release supports TLS 1.1 and TLS 1.2,
 * neither version is enabled by default for client connections. Some
 * servers do not implement forward compatibility correctly and refuse to
 * talk to TLS 1.1 or TLS 1.2 clients. For interoperability, SunJSSE does
 * not enable TLS 1.1 or TLS 1.2 by default for client connections.
 */
private const val SSL_CONTEXT_PROTOCOL = "TLSv1.2"

/**
 * [SSLContext]: Every implementation of the Java platform is required
 * to support the following standard SSLContext protocol: TLSv1
 */
private const val SSL_CONTEXT_FALLBACK_PROTOCOL = "TLSv1"

/**
 * The signature algorithm starting with the message digest to use when
 * signing certificates. On 64-bit systems this should be set to SHA512, on
 * 32-bit systems this is SHA256. On 64-bit systems, SHA512 generally
 * performs better than SHA256; see this question for details:
 * http://crypto.stackexchange.com/questions/26336/sha512-faster-than-sha256
 */
private val SIGNATURE_ALGORITHM = (if (is32BitJvm()) "SHA256" else "SHA512") + "WithRSAEncryption"

/**
 * Current time minus 1 year, just in case software clock goes back due to
 * time synchronization
 */
private val NOT_BEFORE: Date = Date(System.currentTimeMillis() - ONE_DAY * 365)

/**
 * The maximum possible value in X.509 specification: 9999-12-31 23:59:59,
 * new Date(253402300799000L), but Apple iOS 8 fails with a certificate
 * expiration date grater than Mon, 24 Jan 6084 02:07:59 GMT (issue #6).
 *
 * Hundred years in the future from starting the proxy should be enough.
 */
private val NOT_AFTER: Date = Date(System.currentTimeMillis() + ONE_DAY * 365 * 100)

internal object CertificateHelper {

    private val logger = LoggerFactory.getLogger(CertificateHelper::class.java)

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    private fun generateKeyPair(keySize: Int): KeyPair {
        val generator = KeyPairGenerator.getInstance(KEYGEN_ALGORITHM)
        val secureRandom = SecureRandom.getInstance(SECURE_RANDOM_ALGORITHM)
        generator.initialize(keySize, secureRandom)
        return generator.generateKeyPair()
    }


    private fun createRootCertificate(authority: Authority, keyStoreType: String): KeyStore {
        val keyPair = generateKeyPair(ROOT_KEYSIZE)
        val issuer = X500NameBuilder(BCStyle.INSTANCE).apply {
            addRDN(BCStyle.CN, authority.commonName)
            addRDN(BCStyle.O, authority.organization)
            addRDN(BCStyle.OU, authority.organizationalUnitName)
        }.build()
        val serial = BigInteger.valueOf(initRandomSerial())
        val pubKey = keyPair.public
        val generator = JcaX509v3CertificateBuilder(issuer, serial, NOT_BEFORE, NOT_AFTER, issuer, pubKey).apply {
            addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyIdentifier(pubKey))
            addExtension(Extension.basicConstraints, true, BasicConstraints(true))
            addExtension(Extension.keyUsage, false, KeyUsage(KeyUsage.keyCertSign or KeyUsage.digitalSignature or KeyUsage.keyEncipherment or KeyUsage.dataEncipherment or KeyUsage.cRLSign))
            addExtension(Extension.extendedKeyUsage, false, DERSequence(ASN1EncodableVector().apply {
                add(KeyPurposeId.id_kp_serverAuth)
                add(KeyPurposeId.id_kp_clientAuth)
                add(KeyPurposeId.anyExtendedKeyUsage)
            }))
        }
        val cert = signCertificate(generator, keyPair.private)
        return KeyStore.getInstance(keyStoreType).apply {
            load(null, null)
            setKeyEntry(authority.alias, keyPair.private, authority.password, arrayOf<Certificate>(cert))
        }
    }

    private fun createSubjectKeyIdentifier(key: Key): SubjectKeyIdentifier {
        val seq = ByteArrayInputStream(key.encoded).use {
            ASN1InputStream(it).readObject()
        }
        val info = SubjectPublicKeyInfo.getInstance(seq)
        return BcX509ExtensionUtils().createSubjectKeyIdentifier(info)
    }


    private fun signCertificate(
            certificateBuilder: X509v3CertificateBuilder,
            signedWithPrivateKey: PrivateKey
    ): X509Certificate {
        val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(BouncyCastleProvider.PROVIDER_NAME).build(signedWithPrivateKey)
        return JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(certificateBuilder.build(signer))
    }

    @JvmSynthetic
    fun createServerCertificate(
            commonName: String,
            subjectAlternativeNames: SubjectAlternativeNameHolder,
            authority: Authority,
            caCert: Certificate,
            caPrivKey: PrivateKey
    ): KeyStore {
        val keyPair = generateKeyPair(FAKE_KEYSIZE)
        val issuer = X509CertificateHolder(caCert.encoded).subject
        val serial = BigInteger.valueOf(initRandomSerial())
        val subject = X500NameBuilder(BCStyle.INSTANCE).apply {
            addRDN(BCStyle.CN, commonName)
            addRDN(BCStyle.O, authority.certOrganization)
            addRDN(BCStyle.OU, authority.certOrganizationalUnitName)
        }.build()
        val builder = JcaX509v3CertificateBuilder(
                issuer, serial, NOT_BEFORE, Date(System.currentTimeMillis() + ONE_DAY), subject, keyPair.public
        ).apply {
            addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyIdentifier(keyPair.public))
            addExtension(Extension.basicConstraints, false, BasicConstraints(false))
        }
        subjectAlternativeNames.fillInto(builder)
        val cert = signCertificate(builder, caPrivKey).apply {
            checkValidity(Date())
            verify(caCert.publicKey)
        }
        return KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry(authority.alias, keyPair.private, authority.password, arrayOf(cert, caCert))
        }
    }

    @JvmSynthetic
    fun getTrustManagers(keyStore: KeyStore): Array<TrustManager> {
        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }.trustManagers
    }

    @JvmSynthetic
    fun getKeyManagers(keyStore: KeyStore, authority: Authority): Array<KeyManager> {
        return KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, authority.password)
        }.keyManagers
    }

    @JvmSynthetic
    fun newClientContext(keyManagers: Array<KeyManager>, trustManagers: Array<TrustManager>): SSLContext {
        return newSSLContext().apply {
            init(keyManagers, trustManagers, null)
        }
    }

    @JvmSynthetic
    fun newServerContext(keyManagers: Array<KeyManager>): SSLContext {
        return newSSLContext().apply {
            init(keyManagers, null, SecureRandom().apply {
                setSeed(System.currentTimeMillis())
            })
        }
    }

    private fun newSSLContext(): SSLContext = try {
        SSLContext.getInstance(SSL_CONTEXT_PROTOCOL)
    } catch (e: NoSuchAlgorithmException) {
        SSLContext.getInstance(SSL_CONTEXT_FALLBACK_PROTOCOL)
    }


    @JvmSynthetic
    internal fun initializeKeyStore(authority: Authority, dir: File) {
        val duration = MillisecondsDuration()
        val keystore = createRootCertificate(authority, KEY_STORE_TYPE)
        logger.info("Created root certificate authority key store in {}ms", duration)

        val cert = File(dir, "${authority.alias}${KEY_STORE_FILE_EXTENSION}").outputStream().use {
            keystore.store(it, authority.password)
            keystore.getCertificate(authority.alias)
        }
        exportPem(File(dir, "${authority.alias}${PEM_CERT_FILE_EXTENSION}"), cert)
    }

    @JvmSynthetic
    internal fun exportPem(exportFile: File, vararg certs: Any) {
        FileWriter(exportFile).use { sw ->
            JcaPEMWriter(sw).use { pw ->
                certs.forEach { cert ->
                    pw.writeObject(cert)
                    pw.flush()
                }
            }
        }
    }
}

private fun initRandomSerial(): Long {
    val rnd = Random(System.currentTimeMillis())
    // prevent browser certificate caches, cause of doubled serial numbers using 48bit random number
    val sl = rnd.nextInt().toLong() shl 32 or ((rnd.nextInt().toUInt() and UInt.MAX_VALUE).toLong())
    // let reserve of 16 bit for increasing, serials have to be positive
    return sl and 0x0000FFFFFFFFFFFFL
}

/**
 * Uses the non-portable system property sun.arch.data.model to help
 * determine if we are running on a 32-bit JVM. Since the majority of modern
 * systems are 64 bits, this method "assumes" 64 bits and only returns true
 * if sun.arch.data.model explicitly indicates a 32-bit JVM.
 *
 * @return true if we can determine definitively that this is a 32-bit JVM,
 * otherwise false
 */
private fun is32BitJvm(): Boolean {
    val bits = Integer.getInteger("sun.arch.data.model")
    return bits != null && bits == 32
}

fun main() {
    val pwd = System.getProperty("user.dir")
    val certs = File(pwd, listOf("src", "main", "resources", "certs").joinToString(File.separator))
    CertificateHelper.initializeKeyStore(Authority(), certs)
}
