/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

@file:OptIn(ExperimentalStdlibApi::class)

package com.sphereon.crypto.core.x509

import at.asitplus.awesn1.Asn1PrimitiveOctetString
import at.asitplus.awesn1.Asn1String
import at.asitplus.awesn1.Asn1Time
import at.asitplus.awesn1.ObjectIdentifier
import at.asitplus.awesn1.crypto.pki.AttributeTypeAndValue
import at.asitplus.awesn1.crypto.pki.RelativeDistinguishedName
import at.asitplus.awesn1.crypto.pki.TbsCertificate
import at.asitplus.awesn1.crypto.pki.X509Certificate
import at.asitplus.awesn1.crypto.pki.X509CertificateExtension
import at.asitplus.awesn1.encodeToPem
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeTo
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.interop.certificateFromX509Certificate
import com.sphereon.crypto.core.interop.ecSignatureToAsn1BitString
import com.sphereon.crypto.core.interop.toCertificateDto
import com.sphereon.crypto.core.interop.toSignatureAlgorithmIdentifier
import com.sphereon.crypto.core.interop.toSubjectPublicKeyInfo
import com.sphereon.crypto.core.interop.x509CertificateFromBase64
import com.sphereon.crypto.core.interop.x509CertificateFromDer
import com.sphereon.crypto.core.interop.x509CertificateFromPem
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.CertificateResult
import io.ktor.util.sha1
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

const val BEGIN_CERTIFICATE_PEM_HEADER = "-----BEGIN CERTIFICATE-----"
const val END_CERTIFICATE_PEM_FOOTER = "-----END CERTIFICATE-----"

fun x509DerOrPemToPem(cert: String): String {
    if (cert.contains(BEGIN_CERTIFICATE_PEM_HEADER)) {
        return cert.trim()
    }
    return x509CertificateFromBase64(cert).encodeToPem()
}

fun x509DerOrPemToDer(cert: String): String {
    if (cert.contains(BEGIN_CERTIFICATE_PEM_HEADER)) {
        return certificateFromPem(cert).der.encodeToBase64()
    }
    return certificateFromDer(cert.decodeFrom(Encoding.BASE64)).der.encodeToBase64()
}

/** Wraps a Base64-encoded X.509 certificate string with PEM headers and footers.  */
fun wrapX509CertificatePem(input: String): String {
    val noNewlines =
        input
            .trim()
            .replace("\r", "")
            .replace("\n", "")
    val body = noNewlines.chunked(64).joinToString("\n")
    return buildString {
        appendLine(BEGIN_CERTIFICATE_PEM_HEADER)
        appendLine(body)
        append(END_CERTIFICATE_PEM_FOOTER)
    }
}

fun certificateChainToX5c(chain: Array<Certificate>): Array<String> = chain.map { it.derToBase64() }.toTypedArray()

fun certificateFromDer(input: ByteArray): Certificate {
    val x509 = x509CertificateFromDer(input)
    return certificateFromX509Certificate(x509, input)
}

fun certificateChainFromDer(ders: Array<ByteArray>): Array<Certificate> = ders.map { certificateFromDer(it) }.toTypedArray()

fun certificateFromBase64Der(derBas64: String): Certificate = certificateFromDer(derBas64.decodeFrom(Encoding.BASE64))

fun certificateChainFromX5c(x5c: Array<String>): Array<Certificate> = x5c.map { certificateFromBase64Der(it) }.toTypedArray()

fun certificateFromPem(input: String): Certificate {
    val x509 = x509CertificateFromPem(input)
    val derBytes = x509.encodeToTlv().derEncoded
    return certificateFromX509Certificate(x509, derBytes)
}

fun certificateChainFromPem(input: String): Array<Certificate> =
    Regex("$BEGIN_CERTIFICATE_PEM_HEADER[\\s\\S]*?$END_CERTIFICATE_PEM_FOOTER")
        .findAll(input)
        .map { certificateFromPem(it.value) }
        .toList()
        .toTypedArray()

fun pemAndDerToCertificateChain(
    pemChain: Array<String>?,
    derChain: Array<ByteArray>?,
): Array<Certificate> {
    val pemCerts = pemChain?.map { certificateFromPem(it) }
    val derCerts = derChain?.map { certificateFromDer(it) }
    val certs = (pemCerts ?: emptyList()) + (derCerts ?: emptyList())
    return certs.toTypedArray()
}

fun certificateJwkEncode(cert: ByteArray): String = cert.encodeTo(Encoding.BASE64)

fun certificateJwkDecode(base64: String): ByteArray = base64.decodeFrom(Encoding.BASE64)

/**
 * Utility functions for X.509 certificate creation.
 */
object CertificateCreationUtils {
    /**
     * Creates an X.509 certificate.
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    suspend fun createCertificate(
        issuerKeyInfo: KeyInfoType<KeyType>,
        issuer: X509DistinguishedNameElements,
        subjectKeyInfo: ResolvedKeyInfoType<KeyType>,
        subject: X509DistinguishedNameElements,
        serialNumber: Int,
        notBefore: LocalDateTimeKMP,
        notAfter: LocalDateTimeKMP,
        signatureFunction: suspend (ByteArray) -> ByteArray,
    ): CertificateResult {
        val signatureAlgorithm = issuerKeyInfo.signatureAlgorithm ?: SignatureAlgorithm.ECDSA_SHA256
        val jwk = CoseJoseKeyMappingService.toJoseJwk(subjectKeyInfo.key)
        val sha1Fingerprint = computeSubjectKeyIdentifier(jwk)
        val keyIdElement = Asn1PrimitiveOctetString(sha1Fingerprint)
        val derKeyId = keyIdElement.derEncoded
        val extnValue = Asn1PrimitiveOctetString(derKeyId)

        val skiExtension =
            X509CertificateExtension(
                oid = ObjectIdentifier("2.5.29.14"),
                critical = false,
                value = extnValue,
            )

        val issuerDn = createDN(issuer)
        val subjectDn = createDN(subject)

        val tbsCertificate =
            TbsCertificate(
                version = 2,
                serialNumber =
                    at.asitplus.awesn1
                        .Asn1Integer(serialNumber)
                        .twosComplement(),
                signatureAlgorithm = signatureAlgorithm.toSignatureAlgorithmIdentifier(),
                issuerName = issuerDn,
                subjectName = subjectDn,
                validFrom = Asn1Time(notBefore.toInstant().let { kotlin.time.Instant.fromEpochSeconds(it.epochSeconds, it.nanosecondsOfSecond) }),
                validUntil = Asn1Time(notAfter.toInstant().let { kotlin.time.Instant.fromEpochSeconds(it.epochSeconds, it.nanosecondsOfSecond) }),
                subjectPublicKeyInfo = jwk.toSubjectPublicKeyInfo(),
                extensions = listOf(skiExtension),
            )

        val der = tbsCertificate.encodeToTlv().derEncoded
        val signature = signatureFunction(der)
        val x509Certificate =
            X509Certificate(
                tbsCertificate,
                tbsCertificate.signatureAlgorithm,
                ecSignatureToAsn1BitString(signature),
            )

        val certificate = x509Certificate.toCertificateDto()

        return CertificateResult(certificate = certificate, keyInfo = certificate.amendJwkKeyInfo(subjectKeyInfo))
    }

    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    fun createDN(params: X509DistinguishedNameElements): MutableList<RelativeDistinguishedName> {
        val dn = mutableListOf<RelativeDistinguishedName>()
        dn.add(RelativeDistinguishedName(AttributeTypeAndValue.CommonName(Asn1String.UTF8(params.commonName))))

        addNonNullAttribute(dn, AttributeTypeAndValue::Country, params.country)
        addNonNullAttribute(dn, { value -> AttributeTypeAndValue.Other(ObjectIdentifier(X500AttributeTypeOids.ST), value) }, params.state)
        addNonNullAttribute(dn, AttributeTypeAndValue::Organization, params.organizationName)
        addNonNullAttribute(dn, AttributeTypeAndValue::OrganizationalUnit, params.organizationUnit)
        addNonNullAttribute(dn, { value -> AttributeTypeAndValue.Other(ObjectIdentifier(X500AttributeTypeOids.L), value) }, params.locality)
        addNonNullAttribute(dn, { value -> AttributeTypeAndValue.Other(ObjectIdentifier(X500AttributeTypeOids.EMAIL_ADDRESS), value) }, params.email)
        return dn
    }

    private fun addNonNullAttribute(
        dn: MutableList<RelativeDistinguishedName>,
        attributeFactory: (Asn1String) -> AttributeTypeAndValue,
        value: String?,
    ) {
        value?.let {
            dn.add(RelativeDistinguishedName(attributeFactory(Asn1String.UTF8(it))))
        }
    }

    private fun computeECSubjectKeyIdentifier(jwk: Jwk): ByteArray {
        val x = jwk.x?.decodeFrom(Encoding.BASE64URL)
        val y = jwk.y?.decodeFrom(Encoding.BASE64URL)
        requireNotNull(x) { "x coordinate must not be null" }
        requireNotNull(y) { "y coordinate must not be null" }
        val publicPoint =
            ByteArray(1 + x.size + y.size).apply {
                this[0] = 0x04
                x.copyInto(this, 1)
                y.copyInto(this, 1 + x.size)
            }
        return sha1(publicPoint)
    }

    private fun computeRSASubjectKeyIdentifier(jwk: Jwk): ByteArray {
        val derEncoded = jwk.toSubjectPublicKeyInfo().encodeToTlv().derEncoded
        return sha1(derEncoded)
    }

    private fun computeSubjectKeyIdentifier(jwk: Jwk): ByteArray =
        when (jwk.kty) {
            JwaKeyType.EC -> {
                computeECSubjectKeyIdentifier(jwk)
            }

            JwaKeyType.RSA -> {
                computeRSASubjectKeyIdentifier(jwk)
            }

            else -> {
                when (jwk.alg) {
                    JwaAlgorithm.ES256, JwaAlgorithm.ES384, JwaAlgorithm.ES512 -> computeECSubjectKeyIdentifier(jwk)
                    JwaAlgorithm.PS256, JwaAlgorithm.PS384, JwaAlgorithm.PS512 -> computeRSASubjectKeyIdentifier(jwk)
                    else -> throw IllegalArgumentException("Unsupported key type: ${jwk.kty}, alg ${jwk.alg}. Only EC and RSA are supported to compute the Subject Key Identifier for certificates.")
                }
            }
        }
}
