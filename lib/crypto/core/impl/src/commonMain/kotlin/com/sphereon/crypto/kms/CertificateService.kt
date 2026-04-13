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

package com.sphereon.crypto.kms

import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.X509SignatureAlgorithm
import at.asitplus.signum.indispensable.asn1.Asn1Decodable
import at.asitplus.signum.indispensable.asn1.Asn1Encodable
import at.asitplus.signum.indispensable.asn1.Asn1Sequence
import at.asitplus.signum.indispensable.asn1.Asn1Set
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.asn1.runRethrowing
import at.asitplus.signum.indispensable.pki.AttributeTypeAndValue
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.signum.indispensable.pki.RelativeDistinguishedName
import at.asitplus.signum.indispensable.pki.TbsCertificationRequest
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.CertificateSigningRequest
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.interop.toSignumPublicKey
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.CertificateResult
import com.sphereon.crypto.core.kms.CertificateService
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.x509.CertificateCreationUtils
import com.sphereon.di.session.SessionScope
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.experimental.ExperimentalObjCName
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
        if (!listOf(JwaKeyType.EC).contains(kty))
            error(
                "Unsupported key type: $kty. Only EC is supported for CSR generation."
            )

        // Build list of DN information to the CSR
        val dn = createDN(distinguishedNameElements)

        // Build the CSR structure
        val tbsCsr = TbsCertificationRequest(
            subjectName = dn.map { RelativeDistinguishedName(it.attrsAndValues) },
            publicKey = jwk.toSignumPublicKey(),
            attributes = listOf()
        )

        // Get the TBS bytes for signing
        val initialTbsBytes = tbsCsr.encodeToDer()

        // Verify that the TBS bytes will match what Pkcs10CertificationRequest will contain
        // There's a known issue where tbsCsr.encodeToDer() may produce different output than what
        // Pkcs10CertificationRequest encodes (e.g., extra characters in string fields)
        // Create a dummy signature with r=1 and s=1 (both must be positive for valid ECDSA signature)
        val dummySigBytes = ByteArray(64) { if (it == 31 || it == 63) 1 else 0 } // r=1, s=1 (last byte of each 32-byte component)
        val dummySig = CryptoSignature.EC.fromRawBytes(dummySigBytes)
        val tempCsr = Pkcs10CertificationRequest(
            tbsCsr = tbsCsr,
            signatureAlgorithm = jwk.toX509SignatureAlgorithm(),
            signature = dummySig
        )
        val tempCsrDer = tempCsr.encodeToDer()
        val parsedTempCsr = Pkcs10CertificationRequest.decodeFromDer(tempCsrDer)
        val actualTbsBytes = parsedTempCsr.tbsCsr.encodeToDer()

        // Use the actual TBS bytes if they differ from the initial encoding
        val tbsBytesToSign = if (!initialTbsBytes.contentEquals(actualTbsBytes)) {
            actualTbsBytes
        } else {
            initialTbsBytes
        }

        // Sign the TBS bytes
        val signature: ByteArray = keyManagerService.createRawSignature(
            keyInfo = subjectKeyInfo,
            input = tbsBytesToSign,
            requireX5Chain = true
        )

        // Build the final CSR with the real signature
        val csr = Pkcs10CertificationRequest(
            tbsCsr = tbsCsr,
            signatureAlgorithm = jwk.toX509SignatureAlgorithm(),
            signature = CryptoSignature.EC.fromRawBytes(signature)
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
                der = csr.encodeToDer()
            )
        }
    }

    // Todo double check the oid's values for correctness
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    private fun createDN(params: X509DistinguishedNameElements): MutableList<RelativeDistinguishedNameIdk> {
        // Delegate to utility function and convert to Idk version
        return CertificateCreationUtils.createDN(params).map { rdn ->
            RelativeDistinguishedNameIdk(rdn.attrsAndValues)
        }.toMutableList()
    }


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
    ): CertificateResult {
        return createCertificate(issuerKeyInfo, issuer, subjectKeyInfo, csr.toX509DistinguishedNameElements(), serialNumber, notBefore, notAfter)
    }

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
    ): CertificateResult {
        return CertificateCreationUtils.createCertificate(
            issuerKeyInfo = issuerKeyInfo,
            issuer = issuer,
            subjectKeyInfo = subjectKeyInfo,
            subject = subject,
            serialNumber = serialNumber,
            notBefore = notBefore,
            notAfter = notAfter,
            signatureFunction = { data -> keyManagerService.createRawSignature(issuerKeyInfo, data, requireX5Chain = true) }
        )
    }

    /**
     * Converts a [Jwk] to an [X509SignatureAlgorithm] for the Indispensable lib.
     *
     * @receiver The [Jwk] to convert.
     * @return The corresponding [X509SignatureAlgorithm]  for the Indispensable lib.
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    private fun Jwk.toX509SignatureAlgorithm(): X509SignatureAlgorithm {
        return when (crv) {
            JwaCurve.P_256 -> X509SignatureAlgorithm.ES256
            JwaCurve.P_384 -> X509SignatureAlgorithm.ES384
            JwaCurve.P_521 -> X509SignatureAlgorithm.ES512
            else -> error("Unsupported curve: $crv. Only P-256, P-384, and P-512 are supported for CSR generation.")
        }
    }

    @ContributesTo(SessionScope::class)
    interface Component {
        val certificateService: CertificateService
    }
}


@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
data class RelativeDistinguishedNameIdk(val attrsAndValues: List<AttributeTypeAndValue>) : Asn1Encodable<Asn1Set> {

    constructor(singleItem: AttributeTypeAndValue) : this(listOf(singleItem))

    override fun encodeToTlv() = runRethrowing {
        Asn1.Set {
            attrsAndValues.forEach { +it }
        }
    }

    companion object : Asn1Decodable<Asn1Set, RelativeDistinguishedNameIdk> {
        override fun doDecode(src: Asn1Set): RelativeDistinguishedNameIdk = runRethrowing {
            RelativeDistinguishedNameIdk(src.children.map { AttributeTypeAndValue.decodeFromTlv(it as Asn1Sequence) })
        }
    }

    override fun toString() = "DistinguishedName(attrsAndValues=${attrsAndValues.joinToString()})"

}
