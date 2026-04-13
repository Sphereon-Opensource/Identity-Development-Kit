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
import com.sphereon.core.api.error.IdkError
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
     * @param supportedAlgorithms If non-null, the JOSE `alg` of the proof MUST be one of
     *   the listed values (OID4VCI §F.1: must match `proof_signing_alg_values_supported`
     *   from the credential configuration). When null no alg-allowlist check is performed.
     * @return Verified proof with holder binding key info.
     */
    suspend fun verify(
        proofValue: JsonElement,
        expectedAudience: String,
        supportedAlgorithms: List<String>? = null,
    ): IdkResult<VerifiedProof, IdkError>
}
