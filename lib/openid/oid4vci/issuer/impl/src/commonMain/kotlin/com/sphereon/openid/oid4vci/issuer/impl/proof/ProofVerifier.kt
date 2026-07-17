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

package com.sphereon.openid.oid4vci.issuer.impl.proof

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.common.model.ProofTypeSupported
import com.sphereon.openid.oid4vci.issuer.proof.VerifiedProof
import kotlinx.serialization.json.JsonElement

/**
 * Interface for OID4VCI proof verification.
 * Each implementation handles a specific proof type (jwt, cwt, attestation, di_vp).
 */
interface ProofVerifier {
    /** The proof type this verifier handles (e.g., "jwt", "cwt", "di_vp"). */
    val supportedProofType: String

    /**
     * Verify a proof value.
     *
     * @param proofValue The proof as a [JsonElement]. For JWT/CWT/attestation this is a
     *   [JsonPrimitive] string; for di_vp this is a [JsonObject].
     * @param expectedAudience The expected audience (Credential Issuer Identifier).
     * @param expectedClientId The client_id the access token was issued to. When the proof
     *   carries an `iss` claim it MUST equal this value (OID4VCI 1.0 §7.2.1.2: "The value of
     *   this claim MUST be the client_id of the Client making the Credential Request"). Pass
     *   `null` for flows where no client_id is bound to the access token (e.g. Pre-Authorized
     *   Code Flow without a public client identifier).
     * @param credentialConfigId The credential configuration ID for which this proof is
     *   being verified. Verifiers use it to resolve per-credential trust (e.g. pinned
     *   key-attester JWKs) without re-deriving it from the request.
     * @param proofTypeSupported The full `proof_types_supported.<type>` metadata block for
     *   this proof carrier (when the credential configuration declares one). Carries
     *   `proof_signing_alg_values_supported` (§F.1 alg-allowlist) and
     *   `key_attestations_required` (§11.2.3 attestation policy). When null, no allowlist
     *   check is performed and no attestation is required.
     * @return Verified proof with holder binding key info.
     */
    suspend fun verify(
        proofValue: JsonElement,
        expectedAudience: String,
        expectedClientId: String?,
        credentialConfigId: String,
        proofTypeSupported: ProofTypeSupported? = null,
        expectedNonce: String? = null,
        consumeNonce: Boolean = true,
    ): IdkResult<VerifiedProof, IdkError>

    /**
     * Extract the credential request nonce from an unverified proof envelope.
     *
     * Batch credential requests carry multiple proofs in one request. The issuer consumes the
     * request nonce once after all proofs verify, so the command needs the nonce value before
     * invoking [verify] with [consumeNonce] set to false.
     */
    fun extractNonce(proofValue: JsonElement): IdkResult<String?, IdkError> = Ok(null)
}
