package io.johnsonlee.mitmproxy.internal

import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.cert.X509v3CertificateBuilder
import java.util.regex.Pattern

internal class SubjectAlternativeNameHolder {

    private val sans = mutableListOf<ASN1Encodable>()

    @JvmSynthetic
    fun addIpAddress(ipAddress: String) {
        sans.add(GeneralName(GeneralName.iPAddress, ipAddress))
    }

    @JvmSynthetic
    fun addDomainName(subjectAlternativeName: String) {
        sans.add(GeneralName(GeneralName.dNSName, subjectAlternativeName))
    }

    @JvmSynthetic
    fun fillInto(certGen: X509v3CertificateBuilder) {
        if (sans.isNotEmpty()) {
            certGen.addExtension(Extension.subjectAlternativeName, false, DERSequence(sans.toTypedArray()))
        }
    }

    @JvmSynthetic
    fun addAll(subjectAlternativeNames: Collection<List<*>>) {
        subjectAlternativeNames.filter(::isValidNameEntry).forEach { each ->
            val tag = Integer.valueOf(each[0].toString())
            val name = each[1].toString()
            sans.add(GeneralName(tag, name))
        }
    }

    private fun isValidNameEntry(nameEntry: List<*>): Boolean {
        if (nameEntry.size != 2) {
            return false
        }
        val tag = nameEntry.first().toString()
        return TAGS_PATTERN.matcher(tag).matches()
    }

}

/**
 * @see org.bouncycastle.asn1.x509.GeneralName
 *
 * @see [RFC 5280, § 4.2.1.6. Subject Alternative Name](https://tools.ietf.org/html/rfc5280.section-4.2.1.6)
 */
private val TAGS_PATTERN = Pattern.compile("[012345678]")
