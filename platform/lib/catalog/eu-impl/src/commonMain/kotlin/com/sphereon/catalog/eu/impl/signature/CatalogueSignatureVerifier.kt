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
 */

package com.sphereon.catalog.eu.impl.signature

import com.sphereon.di.session.SessionScope
import com.sphereon.trust.etsi.signature.xades.XAdESValidationOptions
import com.sphereon.trust.etsi.signature.xades.XAdESValidator
import com.sphereon.trust.etsi.signature.xmldsig.EnvelopedSignatureCoverage
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Instant

/**
 * @property valid the signature, its references and the signing certificate digest are valid and the signer is one of the authorised certificates.
 * @property cryptographicallyValid the signature value and every reference digest check out.
 * @property signerAuthorised the signing certificate is byte-identical to one of the authorised certificates.
 */
class CatalogueSignatureResult(
    val valid: Boolean,
    val signaturePresent: Boolean,
    val cryptographicallyValid: Boolean,
    val signerAuthorised: Boolean,
    val signerCertificate: ByteArray?,
    val signingTime: Instant?,
    val errors: List<String> = emptyList(),
    val reasonCodes: List<String> = emptyList(),
)

/**
 * Verifies the enveloped XAdES signature of a LoC, CoA or CoS against the certificates that are authorised to sign it.
 */
interface CatalogueSignatureVerifier {
    /**
     * @param authorisedSigners DER certificates. The signature must have been made with exactly one of these; an
     * issuer of the signer is not enough.
     */
    suspend fun verify(
        xml: ByteArray,
        authorisedSigners: List<ByteArray>,
        requireXAdES: Boolean = true,
    ): CatalogueSignatureResult
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CatalogueSignatureVerifier>())
class XAdESCatalogueSignatureVerifier(
    private val xadesValidator: XAdESValidator,
) : CatalogueSignatureVerifier {
    override suspend fun verify(
        xml: ByteArray,
        authorisedSigners: List<ByteArray>,
        requireXAdES: Boolean,
    ): CatalogueSignatureResult {
        if (authorisedSigners.isEmpty()) {
            return CatalogueSignatureResult(false, false, false, false, null, null, listOf("No authorised signer certificates were provided"))
        }
        val result =
            xadesValidator.validate(
                xml,
                XAdESValidationOptions(
                    validateReferences = true,
                    validateSigningCertificate = true,
                    validateCertificateChain = true,
                    requireXAdESProperties = requireXAdES,
                    trustedCertificates = authorisedSigners,
                ),
            )
        val signer = result.signingCertificate
        val authorised = signer != null && authorisedSigners.any { it.contentEquals(signer) }
        // Signature wrapping defence: a valid signature is not enough, it must be the only one, sit directly under
        // the root and cover the whole document through an enveloped Reference with URI="".
        val structureErrors = mutableListOf<String>()
        if (result.signaturePresent) {
            structureErrors.addAll(
                EnvelopedSignatureCoverage.violations(
                    coverage =
                        EnvelopedSignatureCoverage.Coverage(
                            signatureCount = result.signatureCount,
                            signatureIsRootChild = result.signatureIsRootChild,
                            documentReferenceValid = result.documentReferenceValid,
                            duplicateIds = result.duplicateIds,
                            signedPropertiesCovered = result.signedPropertiesCovered,
                        ),
                    requireSignedPropertiesCovered = requireXAdES,
                    subject = "catalogue",
                ),
            )
            if (requireXAdES && !result.signingCertificateV2Present) {
                structureErrors.add("The xades:SigningCertificateV2 is required so the signer evidence is signed")
            }
        }
        val covered = result.signaturePresent && structureErrors.isEmpty()
        return CatalogueSignatureResult(
            valid = result.valid && result.signaturePresent && authorised && covered,
            signaturePresent = result.signaturePresent,
            cryptographicallyValid = result.signatureValid && result.referencesValid && (covered || !result.signaturePresent),
            signerAuthorised = authorised,
            signerCertificate = signer,
            signingTime = result.signingTime,
            errors = result.errors + structureErrors,
            reasonCodes = result.reasonCodes,
        )
    }
}
