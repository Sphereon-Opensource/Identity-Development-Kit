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

package com.sphereon.oauth2.server.resource.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.oauth2.server.resource.model.DpopVerificationResult
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Arguments for VerifyDpopProofCommand
 *
 * @property dpopProof The DPoP proof JWT string
 * @property httpMethod The HTTP method (e.g., "GET", "POST")
 * @property httpUrl The HTTP URL (scheme, host, port, path - no query/fragment)
 * @property expectedJkt The expected JWK thumbprint from access token (cnf.jkt)
 */
data class VerifyDpopProofArgs(
    val dpopProof: String,
    val httpMethod: String,
    val httpUrl: String,
    val expectedJkt: String? = null,
)

/**
 * Command: Verify DPoP Proof
 *
 * Verifies a DPoP proof JWT according to RFC 9449 (OAuth 2.0 Demonstrating Proof-of-Possession).
 *
 * **Verification steps**:
 * 1. Parse DPoP proof JWT (header and payload)
 * 2. Validate JWT signature using JWK from header
 * 3. Validate DPoP proof claims:
 *    - typ header MUST be "dpop+jwt"
 *    - htm claim MUST match HTTP method
 *    - htu claim MUST match HTTP URL (without query/fragment)
 *    - iat claim MUST be recent (e.g., within 60 seconds)
 *    - jti claim MUST be unique (replay protection)
 * 4. Compute JWK thumbprint (jkt) from JWK in header
 * 5. Validate jkt matches cnf.jkt claim in access token
 *
 * **Replay protection**:
 * - MUST track jti values to prevent replay attacks
 * - Cache jti values for time window (e.g., iat ± 60 seconds)
 * - SHOULD use distributed cache for multi-instance deployments
 *
 * **Security considerations**:
 * - MUST validate signature before trusting any claims
 * - MUST validate htm and htu to prevent token reuse across requests
 * - MUST validate iat to prevent replay of old proofs
 * - MUST validate jti uniqueness to prevent replay attacks
 * - MUST validate jkt binding to prevent token substitution
 * - Time window for iat validation creates small replay window - use jti tracking
 *
 * @see DpopVerificationResult Output DPoP proof metadata
 */

@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyDpopProofCommand", exact = true)
interface VerifyDpopProofCommand : ServiceCommand<VerifyDpopProofArgs, DpopVerificationResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.resource.verifydpop"
    }
}
