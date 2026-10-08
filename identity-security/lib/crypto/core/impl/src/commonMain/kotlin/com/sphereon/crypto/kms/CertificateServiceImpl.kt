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

import at.asitplus.awesn1.crypto.pki.Pkcs10CertificationRequest
import at.asitplus.awesn1.crypto.pki.Pkcs10CertificationRequestInfo
import at.asitplus.awesn1.crypto.pki.Pkcs10CsrAttribute
import at.asitplus.awesn1.crypto.pki.X500RelativeDistinguishedName
import at.asitplus.awesn1.serialization.DER
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.CertificateSigningRequest
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.interop.ecSignatureToX509SignatureValue
import com.sphereon.crypto.core.interop.resolveEcdsaKmpCurve
import com.sphereon.crypto.core.interop.resolveEcdsaKmpDigest
import com.sphereon.crypto.core.interop.toEcdsaPublicKey
import com.sphereon.crypto.core.interop.toSignatureAlgorithmIdentifier
import com.sphereon.crypto.core.interop.toSubjectPublicKeyInfo
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.generateJwkThumbprint
import com.sphereon.crypto.core.kms.CertificateResult
import com.sphereon.crypto.core.kms.CertificateService
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.X509CertificateExtensionSpec
import com.sphereon.crypto.core.x509.CertificateCreationUtils
import com.sphereon.crypto.core.sign.requireSigningKeyCompatible
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.ECDSA
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
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
        attributes: List<Pkcs10CsrAttribute>,
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
                attributes = attributes,
            )

        // Get the TBS bytes for signing
        val signingAlgorithm = jwk.csrSignatureAlgorithm()
        subjectKeyInfo.requireSigningKeyCompatible(signingAlgorithm)
        val sigAlg = signingAlgorithm.toSignatureAlgorithmIdentifier()
        val provider = keyManagerService.getProvider(subjectKeyInfo.providerId, signingAlgorithm)
        val signingKeyInfo = if (jwk.d == null && subjectKeyInfo is ManagedKeyInfoType<*>) {
            require(subjectKeyInfo.providerId.isNotBlank() && subjectKeyInfo.alias.isNotBlank()) {
                "Managed CSR signing requires a provider and alias"
            }
            require(subjectKeyInfo.kid == null || jwk.kid == null || subjectKeyInfo.kid == jwk.kid) {
                "CSR subject key id conflicts with supplied key material"
            }
            // Resolve by the managed coordinates alone: caller material/kid must not influence
            // the canonical lookup. Public material is evidence to compare, not a private-key fallback.
            val canonical = provider.getKey(KeyInfo<KeyType>(
                providerId = subjectKeyInfo.providerId, alias = subjectKeyInfo.alias,
                keyVisibility = KeyVisibility.PUBLIC,
            ))
            val canonicalJwk = CoseJoseKeyMappingService.toJoseJwk(canonical.key)
            require(generateJwkThumbprint(jwk) == generateJwkThumbprint(canonicalJwk)) {
                "Managed CSR signing key does not match the supplied subject public key"
            }
            require(listOfNotNull(subjectKeyInfo.kid, jwk.kid, canonical.kid, canonicalJwk.kid).distinct().size <= 1) {
                "Managed CSR signing key id does not match the supplied subject"
            }
            canonical.requireSigningKeyCompatible(signingAlgorithm)
            KeyInfo<KeyType>(
                providerId = subjectKeyInfo.providerId, alias = subjectKeyInfo.alias,
                kid = subjectKeyInfo.kid ?: jwk.kid, signatureAlgorithm = signingAlgorithm,
            )
        } else subjectKeyInfo
        val initialTbsBytes = DER.encodeToByteArray(tbsCsr)

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
        val dummySig = ecSignatureToX509SignatureValue(dummySigBytes)
        val tempCsr =
            Pkcs10CertificationRequest(
                certificationRequestInfo = tbsCsr,
                signatureAlgorithm = sigAlg,
                signatureValue = dummySig,
            )
        val tempCsrDer = DER.encodeToByteArray(tempCsr)
        val parsedTempCsr = DER.decodeFromByteArray<Pkcs10CertificationRequest>(tempCsrDer)
        val actualTbsBytes = DER.encodeToByteArray(parsedTempCsr.certificationRequestInfo)

        // Use the actual TBS bytes if they differ from the initial encoding
        val tbsBytesToSign =
            if (!initialTbsBytes.contentEquals(actualTbsBytes)) {
                actualTbsBytes
            } else {
                initialTbsBytes
            }

        // Sign the TBS bytes
        val signature = provider.createRawSignature(signingKeyInfo, tbsBytesToSign, requireX5Chain = true)

        // Verify the exact CSR subject and bytes, including after a managed alias race. This is
        // a mathematical consistency check, not another authorization operation: SIGN-only key
        // policy was enforced above and must not be rewritten to grant VERIFY.
        val publicKey = jwk.toEcdsaPublicKey(
            provider = CryptographyProvider.Default,
            curve = resolveEcdsaKmpCurve(Curve.fromJose(requireNotNull(jwk.crv))),
        )
        require(publicKey.signatureVerifier(
            digest = resolveEcdsaKmpDigest(signingAlgorithm), format = ECDSA.SignatureFormat.RAW,
        ).tryVerifySignature(tbsBytesToSign, signature)) {
            "CSR signature does not verify against its subject public key"
        }

        // Build the final CSR with the real signature
        val csr =
            Pkcs10CertificationRequest(
                certificationRequestInfo = tbsCsr,
                signatureAlgorithm = sigAlg,
                signatureValue = ecSignatureToX509SignatureValue(signature),
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
                der = DER.encodeToByteArray(csr),
            )
        }
    }

    // Todo double check the oid's values for correctness
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    private fun createDN(params: X509DistinguishedNameElements): List<X500RelativeDistinguishedName> = CertificateCreationUtils.createDN(params)

    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    override suspend fun createCertificateFromCSR(
        issuerKeyInfo: KeyInfoType<KeyType>,
        issuer: X509DistinguishedNameElements,
        subjectKeyInfo: ResolvedKeyInfoType<KeyType>,
        csr: CertificateSigningRequest,
        serialNumber: Int,
        extensions: List<X509CertificateExtensionSpec>,
        notBefore: LocalDateTimeKMP,
        notAfter: LocalDateTimeKMP,
    ): CertificateResult =
        createCertificate(
            issuerKeyInfo = issuerKeyInfo,
            issuer = issuer,
            subjectKeyInfo = subjectKeyInfo,
            subject = csr.toX509DistinguishedNameElements(),
            serialNumber = serialNumber,
            extensions = extensions,
            notBefore = notBefore,
            notAfter = notAfter,
        )

    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    override suspend fun createCertificate(
        issuerKeyInfo: KeyInfoType<KeyType>,
        issuer: X509DistinguishedNameElements,
        subjectKeyInfo: ResolvedKeyInfoType<KeyType>,
        subject: X509DistinguishedNameElements,
        serialNumber: Int,
        extensions: List<X509CertificateExtensionSpec>,
        notBefore: LocalDateTimeKMP,
        notAfter: LocalDateTimeKMP,
    ): CertificateResult =
        CertificateCreationUtils.createCertificate(
            issuerKeyInfo = issuerKeyInfo,
            issuer = issuer,
            subjectKeyInfo = subjectKeyInfo,
            subject = subject,
            serialNumber = serialNumber,
            extensions = extensions,
            notBefore = notBefore,
            notAfter = notAfter,
            signatureFunction = { data ->
                keyManagerService
                    .getProvider(issuerKeyInfo.providerId, issuerKeyInfo.signatureAlgorithm)
                    .createRawSignature(issuerKeyInfo, data, requireX5Chain = true)
            },
        )

    /**
     * Selects the CSR signature algorithm from the subject key's curve.
     *
     * @receiver The [Jwk] to convert.
     * @return The corresponding [SignatureAlgorithm].
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    private fun Jwk.csrSignatureAlgorithm(): SignatureAlgorithm =
        when (crv) {
            JwaCurve.P_256 -> SignatureAlgorithm.ECDSA_SHA256
            JwaCurve.P_384 -> SignatureAlgorithm.ECDSA_SHA384
            JwaCurve.P_521 -> SignatureAlgorithm.ECDSA_SHA512
            else -> error("Unsupported curve: $crv. Only P-256, P-384, and P-521 are supported for CSR generation.")
        }
}
