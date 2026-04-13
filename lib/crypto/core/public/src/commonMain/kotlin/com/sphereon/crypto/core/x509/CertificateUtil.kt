 /*
 * © 2025 Sphereon International B.V.
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

import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.asn1.Asn1PrimitiveOctetString
import at.asitplus.signum.indispensable.asn1.Asn1String
import at.asitplus.signum.indispensable.asn1.Asn1Time
import at.asitplus.signum.indispensable.asn1.KnownOIDs
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.asn1.encodeToPEM
import at.asitplus.signum.indispensable.pki.AttributeTypeAndValue
import at.asitplus.signum.indispensable.pki.RelativeDistinguishedName
import at.asitplus.signum.indispensable.pki.TbsCertificate
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import at.asitplus.signum.indispensable.toX509SignatureAlgorithm
import com.ionspin.kotlin.bignum.integer.toBigInteger
import com.sphereon.core.api.Encoding
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeTo
import com.sphereon.core.api.encodeToBase64
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.interop.certificateFromSignumX509Certificate
import com.sphereon.crypto.core.interop.signumX509CertificateFromBase64
import com.sphereon.crypto.core.interop.signumX509CertificateFromDer
import com.sphereon.crypto.core.interop.signumX509CertificateFromPem
import com.sphereon.crypto.core.interop.toCertificateDto
import com.sphereon.crypto.core.interop.toSignumAlgorithm
import com.sphereon.crypto.core.interop.toSignumPublicKey
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.CertificateResult
import io.ktor.util.*
import kotlinx.datetime.toDeprecatedInstant
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

const val BEGIN_CERTIFICATE_PEM_HEADER = "-----BEGIN CERTIFICATE-----"
const val END_CERTIFICATE_PEM_FOOTER = "-----END CERTIFICATE-----"

fun x509DerOrPemToPem(cert: String): String {
    if (cert.contains(BEGIN_CERTIFICATE_PEM_HEADER)) {
        return cert.trim()
    }
    return signumX509CertificateFromBase64(cert).encodeToPEM().getOrThrow()
}

fun x509DerOrPemToDer(cert: String): String {
    if (cert.contains(BEGIN_CERTIFICATE_PEM_HEADER)) {
        return certificateFromPem(cert).der.encodeToBase64()
    }
    return certificateFromDer(cert.decodeFrom(Encoding.BASE64)).der.encodeToBase64()
}


/** Wraps a Base64-encoded X.509 certificate string with PEM headers and footers.  */
fun wrapX509CertificatePem(input: String): String {
    // Remove all line breaks and surrounding whitespace
    val noNewlines = input.trim()
        .replace("\r", "")
        .replace("\n", "")
    // Wrap at 64 characters per line
    val body = noNewlines.chunked(64).joinToString("\n")
    return buildString {
        appendLine(BEGIN_CERTIFICATE_PEM_HEADER)
        appendLine(body)
        append(END_CERTIFICATE_PEM_FOOTER)
    }
}


/** Encode a Signum's X509Certificate list into a Base64 string list (no PEM headers). */
fun certificateChainToX5c(chain: Array<Certificate>): Array<String> =
    chain.map { it.derToBase64() }.toTypedArray()


fun certificateFromDer(input: ByteArray): Certificate {
    val x509 = signumX509CertificateFromDer(input)
    return certificateFromSignumX509Certificate(x509, input)
}


fun certificateChainFromDer(ders: Array<ByteArray>): Array<Certificate> =
    ders.map { certificateFromDer(it) }.toTypedArray()

fun certificateFromBase64Der(derBas64: String): Certificate = certificateFromDer(derBas64.decodeFrom(Encoding.BASE64))

fun certificateChainFromX5c(x5c: Array<String>): Array<Certificate> =
    x5c.map { certificateFromBase64Der(it) }.toTypedArray()

fun certificateFromPem(input: String): Certificate {
    val x509 = signumX509CertificateFromPem(input)
    val derBytes = x509.encodeToDerOrNull()
        ?: error("Failed to encode certificate to DER")
    return certificateFromSignumX509Certificate(x509, derBytes)
}

fun certificateChainFromPem(input: String): Array<Certificate> =
    Regex("$BEGIN_CERTIFICATE_PEM_HEADER[\\s\\S]*?$END_CERTIFICATE_PEM_FOOTER")
        .findAll(input)
        .map { certificateFromPem(it.value) }.toList().toTypedArray()



fun pemAndDerToCertificateChain(pemChain: Array<String>?, derChain: Array<ByteArray>?): Array<Certificate> {
    val pemCerts = pemChain?.map { certificateFromPem(it) }
    val derCerts = derChain?.map { certificateFromDer(it) }
    val certs = (pemCerts ?: emptyList()) + (derCerts ?: emptyList())
    return certs.toTypedArray()
}

fun certificateJwkEncode(cert: ByteArray): String {
    return cert.encodeTo(Encoding.BASE64)
}


fun certificateJwkDecode(base64: String): ByteArray {
    return base64.decodeFrom(Encoding.BASE64)
}


/**
 * Utility functions for X.509 certificate creation.
 */
object CertificateCreationUtils {

    /**
     * Creates an X.509 certificate.
     *
     * @param issuerKeyInfo The key info for the issuer (certificate authority).
     * @param issuer The distinguished name elements for the issuer.
     * @param subjectKeyInfo The key info for the subject (entity receiving the certificate).
     * @param subject The distinguished name elements for the subject.
     * @param serialNumber The serial number for the certificate (must be greater than zero).
     * @param notBefore The validity start date.
     * @param notAfter The validity end date.
     * @param signatureFunction A function that takes the data to be signed and returns the signature.
     * @return A [CertificateResult] containing the created certificate and updated key info.
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

        val skiExtension = X509CertificateExtension(
            oid = ObjectIdentifier("2.5.29.14"),
            critical = false,            // SKI is almost always non-critical
            value = extnValue
        )

        val issuerDn = createDN(issuer)
        val subjectDn = createDN(subject)

        val tbsCertificate = TbsCertificate(
            version = 2,
            serialNumber = serialNumber.toLong().toBigInteger().toByteArray(),
            signatureAlgorithm = signatureAlgorithm.toSignumAlgorithm().toX509SignatureAlgorithm().getOrThrow(),
            issuerName = issuerDn,
            subjectName = subjectDn,
            validFrom = Asn1Time(notBefore.toInstant().toDeprecatedInstant()),
            validUntil = Asn1Time(notAfter.toInstant().toDeprecatedInstant()),
            publicKey = jwk.toSignumPublicKey(),
            extensions = listOf(skiExtension)
        )

        val der = tbsCertificate.encodeToTlv().derEncoded
        val signature = signatureFunction(der)
        val x509Certificate = X509Certificate(
            tbsCertificate,
            tbsCertificate.signatureAlgorithm,
            CryptoSignature.EC.fromRawBytes(signature)
        )

        val certificate = x509Certificate.toCertificateDto()

        return CertificateResult(certificate = certificate, keyInfo = certificate.amendJwkKeyInfo(subjectKeyInfo))
    }

    /**
     * Creates a Distinguished Name (DN) from the provided X.509 distinguished name elements.
     *
     * @param params The distinguished name elements.
     * @return A mutable list of RelativeDistinguishedName entries.
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    fun createDN(params: X509DistinguishedNameElements): MutableList<RelativeDistinguishedName> {
        val dn = mutableListOf<RelativeDistinguishedName>()
        dn.add(RelativeDistinguishedName(AttributeTypeAndValue.CommonName(Asn1String.UTF8(params.commonName))))

        addNonNullAttribute(dn, AttributeTypeAndValue::Country, params.country)
        addNonNullAttribute(dn, { value -> AttributeTypeAndValue.Other(KnownOIDs.stateOrProvinceName, value) }, params.state)
        addNonNullAttribute(dn, AttributeTypeAndValue::Organization, params.organizationName)
        addNonNullAttribute(dn, AttributeTypeAndValue::OrganizationalUnit, params.organizationUnit)
        addNonNullAttribute(dn, { value -> AttributeTypeAndValue.Other(KnownOIDs.locality, value) }, params.locality)
        addNonNullAttribute(dn, { value -> AttributeTypeAndValue.Other(KnownOIDs.emailAddress, value) }, params.email)
        return dn
    }

    /**
     * Adds a non-null attribute to the list of RelativeDistinguishedNames.
     *
     * @param dn The mutable list of RelativeDistinguishedNames.
     * @param attributeFactory A lambda function that creates an AttributeTypeAndValue instance.
     *                         It receives a non-null string value as input.
     * @param value The nullable string value to be added as an attribute.
     */
    private fun addNonNullAttribute(
        dn: MutableList<RelativeDistinguishedName>,
        attributeFactory: (Asn1String) -> AttributeTypeAndValue,
        value: String?,
    ) {
        value?.let {
            dn.add(RelativeDistinguishedName(attributeFactory(Asn1String.UTF8(it))))
        }
    }

    /**
     * Computes the Subject Key Identifier (SKI) for an EC public key.
     * The SKI is computed as the SHA-1 hash of the uncompressed public key point.
     *
     * @param jwk The JWK containing the EC public key coordinates.
     * @return The SHA-1 hash of the uncompressed public key point.
     */
    private fun computeECSubjectKeyIdentifier(jwk: Jwk): ByteArray {
        val x = jwk.x?.decodeFrom(Encoding.BASE64URL)
        val y = jwk.y?.decodeFrom(Encoding.BASE64URL)
        requireNotNull(x) { "x coordinate must not be null" }
        requireNotNull(y) { "y coordinate must not be null" }
        // build uncompressed point: 0x04 || X || Y
        val publicPoint = ByteArray(1 + x.size + y.size).apply {
            this[0] = 0x04
            x.copyInto(this, 1)
            y.copyInto(this, 1 + x.size)
        }
        return sha1(publicPoint)
    }

    /**
     * Computes the Subject Key Identifier (SKI) for an RSA public key.
     * The SKI is computed as the SHA-1 hash of the DER-encoded public key.
     *
     * @param jwk The JWK containing the RSA public key parameters (n and e).
     * @return The SHA-1 hash of the DER-encoded RSA public key.
     */
    private fun computeRSASubjectKeyIdentifier(jwk: Jwk): ByteArray {
        val n = jwk.n?.decodeFrom(Encoding.BASE64URL)
        val e = jwk.e?.decodeFrom(Encoding.BASE64URL)
        requireNotNull(n) { "n (modulus) must not be null" }
        requireNotNull(e) { "e (exponent) must not be null" }

        // Convert to Signum public key and get DER encoding
        val publicKey = jwk.toSignumPublicKey()
        val derEncoded = publicKey.encodeToDer()

        return sha1(derEncoded)
    }

    /**
     * Computes the Subject Key Identifier (SKI) for a public key.
     * Automatically determines whether the key is EC or RSA based on the JWK key type.
     *
     * @param jwk The JWK containing the public key.
     * @return The SHA-1 hash of the public key.
     * @throws IllegalArgumentException if the key type is not EC or RSA.
     */
    private fun computeSubjectKeyIdentifier(jwk: Jwk): ByteArray {
        return when (jwk.kty) {
            JwaKeyType.EC -> computeECSubjectKeyIdentifier(jwk)
            JwaKeyType.RSA -> computeRSASubjectKeyIdentifier(jwk)
            else -> when (jwk.alg) {
                JwaAlgorithm.ES256, JwaAlgorithm.ES384, JwaAlgorithm.ES512 -> computeECSubjectKeyIdentifier(jwk)
                JwaAlgorithm.PS256, JwaAlgorithm.PS384, JwaAlgorithm.PS512 -> computeRSASubjectKeyIdentifier(jwk)

                else -> throw IllegalArgumentException("Unsupported key type: ${jwk.kty}, alg ${jwk.alg}. Only EC and RSA are supported to compute the Subject Key Identifier for certificates.")
            }

        }
    }
}
