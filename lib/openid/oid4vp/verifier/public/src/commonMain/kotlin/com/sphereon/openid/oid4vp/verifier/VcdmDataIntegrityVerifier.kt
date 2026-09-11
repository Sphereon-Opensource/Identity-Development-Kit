package com.sphereon.openid.oid4vp.verifier

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolutionPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

/**
 * Verification constraints for a JSON VCDM document secured with Data Integrity.
 *
 * For an OID4VP-bound Verifiable Presentation, [expectedDomain] is the OID4VP
 * `client_id` and [expectedChallenge] is the request `nonce`. They map to the
 * Data Integrity proof `domain` and `challenge` properties respectively.
 */
@Serializable
data class VcdmDataIntegrityVerificationArgs(
    val document: JsonObject,
    val expectedProofPurpose: ProofPurpose,
    val expectedController: String? = null,
    val expectedDomain: String? = null,
    val expectedChallenge: String? = null,
    val requireDomainAndChallenge: Boolean = false,
    /**
     * Verifier-owned, exact-reference trust policy for non-DID verification
     * methods. DID relationships are still derived from the authenticated DID
     * document. This policy must never be assembled from the proof itself.
     */
    @Transient
    val verificationMethodResolutionPolicy: VerificationMethodResolutionPolicy =
        VerificationMethodResolutionPolicy.empty(),
)

/** The verified unsecured document, proofs, and authenticated verification-method controllers. */
@Serializable
data class VcdmDataIntegrityVerificationResult(
    val verifiedDocument: JsonObject,
    val proofCount: Int,
    /** Distinct controllers established by successful verification-method resolution. */
    val authenticatedControllers: Set<String> = emptySet(),
)

/**
 * Narrow VCDM adapter for the currently supported Data Integrity profile.
 * Implementations MUST fail closed: a structural, DID-resolution, proof-purpose,
 * binding, or cryptographic error is not a successful verification.
 */
interface VcdmDataIntegrityVerifier {
    suspend fun verify(
        args: VcdmDataIntegrityVerificationArgs,
    ): IdkResult<VcdmDataIntegrityVerificationResult, IdkError>
}
