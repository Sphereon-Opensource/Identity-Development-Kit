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

package com.sphereon.crypto.kms

import at.asitplus.awesn1.Asn1Element
import at.asitplus.awesn1.Asn1Sequence
import at.asitplus.awesn1.crypto.SignatureAlgorithmIdentifier
import at.asitplus.awesn1.crypto.pki.Pkcs10CertificationRequest
import at.asitplus.awesn1.crypto.pki.Pkcs10CertificationRequestInfo
import at.asitplus.awesn1.crypto.pki.RelativeDistinguishedName
import at.asitplus.awesn1.encoding.parse
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.CertificateSigningRequest
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.interop.ecSignatureToAsn1BitString
import com.sphereon.crypto.core.interop.toSignatureAlgorithmIdentifier
import com.sphereon.crypto.core.interop.toSubjectPublicKeyInfo
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.CertificateResult
import com.sphereon.crypto.core.kms.CertificateService
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.x509.CertificateCreationUtils
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.native.ObjCName

/**
 * Service for generating Certificate Signing Requests (CSRs).
 * It uses a Key Manager Service to handle cryptographic operations.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CertificateService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("CertificateServiceImpl", exact = true)
class CertificateServiceImpl(
    private val keyManagerService: KeyManagerService,
) : CertificateService {
    /**
     * Generates a Certificate Signing Request (CSR) for a given key and parameters.
     *
     * @param subjectKeyInfo The (managed) key info for which to generate the CSR.
     * @param distinguishedNameElements The parameters for the CSR, including subject information.
     * @return A [CertificateSigningRequest] containing the CSR in PEM and DER formats.
     * @throws IllegalArgumentException if the key type is not supported.
     */
    @Suppress("MagicNumber")
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    override suspend fun generateCSR(
        subjectKeyInfo: ResolvedKeyInfoType<*>,
        distinguishedNameElements: X509DistinguishedNameElements,
        serialNumber: Int,
    ): CertificateSigningRequest {
        require(serialNumber > 0) { "Serial number must be greater than zero. Got $serialNumber" }

        val jwk = CoseJoseKeyMappingService.toJoseJwk(subjectKeyInfo.key)
        val kty = jwk.kty

        // Only EC is supported for CSR generation
        if (!listOf(JwaKeyType.EC).contains(kty)) {
            error(
                "Unsupported key type: $kty. Only EC is supported for CSR generation.",
            )
        }

        // Build list of DN information to the CSR
        val dn = createDN(distinguishedNameElements)

        // Build the CSR structure
        val tbsCsr =
            Pkcs10CertificationRequestInfo(
                subjectName = dn,
                publicKey = jwk.toSubjectPublicKeyInfo(),
                attributes = listOf(),
            )

        // Get the TBS bytes for signing
        val sigAlg = jwk.toSignatureAlgorithmIdentifier()
        val initialTbsBytes = tbsCsr.encodeToTlv().derEncoded

        // Verify that the TBS bytes will match what Pkcs10CertificationRequest will contain
        // There's a known issue where tbsCsr encoding may produce different output than what
        // Pkcs10CertificationRequest encodes (e.g., extra characters in string fields)
        // Create a dummy signature with r=1 and s=1 (both must be positive for valid ECDSA signature)
        val dummySigBytes =
            ByteArray(64) {
                if (it == 31 || it == 63) {
                    1
                } else {
                    0
                }
            } // r=1, s=1 (last byte of each 32-byte graph)
        val dummySig = ecSignatureToAsn1BitString(dummySigBytes)
        val tempCsr =
            Pkcs10CertificationRequest(
                certificationRequestInfo = tbsCsr,
                signatureAlgorithm = sigAlg,
                signatureValue = dummySig,
            )
        val tempCsrDer = tempCsr.encodeToTlv().derEncoded
        val parsedTempCsr =
            Pkcs10CertificationRequest.decodeFromTlv(
                (Asn1Element.parse(tempCsrDer) as Asn1Sequence),
            )
        val actualTbsBytes = parsedTempCsr.certificationRequestInfo.encodeToTlv().derEncoded

        // Use the actual TBS bytes if they differ from the initial encoding
        val tbsBytesToSign =
            if (!initialTbsBytes.contentEquals(actualTbsBytes)) {
                actualTbsBytes
            } else {
                initialTbsBytes
            }

        // Sign the TBS bytes
        val signature: ByteArray =
            keyManagerService.createRawSignature(
                keyInfo = subjectKeyInfo,
                input = tbsBytesToSign,
                requireX5Chain = true,
            )

        // Build the final CSR with the real signature
        val csr =
            Pkcs10CertificationRequest(
                certificationRequestInfo = tbsCsr,
                signatureAlgorithm = sigAlg,
                signatureValue = ecSignatureToAsn1BitString(signature),
            )

        with(distinguishedNameElements) {
            return CertificateSigningRequest(
                commonName = commonName,
                organization = organizationName,
                organizationalUnit = organizationUnit,
                locality = locality,
                state = state,
                country = country,
                email = email,
                serialNumber = serialNumber,
                der = csr.encodeToTlv().derEncoded,
            )
        }
    }

    // Todo double check the oid's values for correctness
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    private fun createDN(params: X509DistinguishedNameElements): List<RelativeDistinguishedName> = CertificateCreationUtils.createDN(params)

    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    override suspend fun createCertificateFromCSR(
        issuerKeyInfo: KeyInfoType<KeyType>,
        issuer: X509DistinguishedNameElements,
        subjectKeyInfo: ResolvedKeyInfoType<KeyType>,
        csr: CertificateSigningRequest,
        serialNumber: Int,
        notBefore: LocalDateTimeKMP,
        notAfter: LocalDateTimeKMP,
    ): CertificateResult = createCertificate(issuerKeyInfo, issuer, subjectKeyInfo, csr.toX509DistinguishedNameElements(), serialNumber, notBefore, notAfter)

    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    override suspend fun createCertificate(
        issuerKeyInfo: KeyInfoType<KeyType>,
        issuer: X509DistinguishedNameElements,
        subjectKeyInfo: ResolvedKeyInfoType<KeyType>,
        subject: X509DistinguishedNameElements,
        serialNumber: Int,
        notBefore: LocalDateTimeKMP,
        notAfter: LocalDateTimeKMP,
    ): CertificateResult =
        CertificateCreationUtils.createCertificate(
            issuerKeyInfo = issuerKeyInfo,
            issuer = issuer,
            subjectKeyInfo = subjectKeyInfo,
            subject = subject,
            serialNumber = serialNumber,
            notBefore = notBefore,
            notAfter = notAfter,
            signatureFunction = { data -> keyManagerService.createRawSignature(issuerKeyInfo, data, requireX5Chain = true) },
        )

    /**
     * Converts a [Jwk] to a [SignatureAlgorithmIdentifier] based on the key's curve.
     *
     * @receiver The [Jwk] to convert.
     * @return The corresponding [SignatureAlgorithmIdentifier] for the awesn1 lib.
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    private fun Jwk.toSignatureAlgorithmIdentifier(): SignatureAlgorithmIdentifier {
        val sigAlg =
            when (crv) {
                JwaCurve.P_256 -> SignatureAlgorithm.ECDSA_SHA256
                JwaCurve.P_384 -> SignatureAlgorithm.ECDSA_SHA384
                JwaCurve.P_521 -> SignatureAlgorithm.ECDSA_SHA512
                else -> error("Unsupported curve: $crv. Only P-256, P-384, and P-521 are supported for CSR generation.")
            }
        return sigAlg.toSignatureAlgorithmIdentifier()
    }
}
