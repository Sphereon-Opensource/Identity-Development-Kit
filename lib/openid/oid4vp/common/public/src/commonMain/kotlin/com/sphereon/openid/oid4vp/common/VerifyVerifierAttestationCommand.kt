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

package com.sphereon.openid.oid4vp.common

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

// =============================================================================
// VerifyVerifierAttestationCommand - Verify verifier attestation JWT
// OpenID4VP 1.0 Final Section 5.9.3 and Section 12
// =============================================================================

/**
 * Arguments for verifying a verifier attestation JWT.
 *
 * @property attestationJwt The verifier attestation JWT string (compact serialization)
 * @property expectedClientId The expected client_id (must match JWT sub claim, without prefix)
 * @property trustedIssuers List of trusted attestation issuer URIs (must include JWT iss claim)
 * @property jarSignerJwk The JWK that signed the JAR (must match attestation cnf.jwk)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyVerifierAttestationArgs", exact = true)
@JsExportCompat
data class VerifyVerifierAttestationArgs(
    val attestationJwt: String,
    val expectedClientId: String,
    val trustedIssuers: List<String>,
    val jarSignerJwk: Jwk? = null,
)

/**
 * Result of verifying a verifier attestation JWT.
 *
 * @property valid Whether the attestation is valid
 * @property issuer The attestation issuer (iss claim)
 * @property subject The attestation subject (sub claim) - should match client_id
 * @property cnfJwk The confirmation JWK from the attestation (cnf.jwk claim)
 * @property expirationTime The expiration time in epoch seconds (exp claim)
 * @property issuedAt The issued at time in epoch seconds (iat claim), if present
 * @property notBefore The not before time in epoch seconds (nbf claim), if present
 * @property errors List of verification errors
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyVerifierAttestationResult", exact = true)
@JsExportCompat
@Serializable
data class VerifyVerifierAttestationResult(
    val valid: Boolean,
    val issuer: String? = null,
    val subject: String? = null,
    @kotlinx.serialization.Transient
    val cnfJwk: Jwk? = null,
    val expirationTime: Long? = null,
    val issuedAt: Long? = null,
    val notBefore: Long? = null,
    val errors: List<VerifierAttestationValidationError> = emptyList(),
)

/**
 * A verifier attestation validation error.
 *
 * @property type The type of validation error
 * @property message Human-readable error message
 * @property details Additional details about the error
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifierAttestationValidationError", exact = true)
@JsExportCompat
@Serializable
data class VerifierAttestationValidationError(
    val type: ClientIdValidationErrorType,
    val message: String,
    val details: String? = null,
)

/**
 * Command to verify a verifier attestation JWT.
 *
 * OpenID4VP 1.0 Final Section 12 defines the Verifier Attestation JWT format:
 *
 * **Header**:
 * - typ: MUST be "verifier-attestation+jwt"
 * - alg: Signing algorithm
 *
 * **Payload**:
 * - iss: REQUIRED - Attestation provider identifier
 * - sub: REQUIRED - Must match client_id (without scheme prefix)
 * - exp: REQUIRED - Expiration time
 * - iat: OPTIONAL - Issued at time
 * - nbf: OPTIONAL - Not before time
 * - cnf: REQUIRED - Confirmation claim containing the verifier's public key
 *   - jwk: REQUIRED within cnf - The public key used to sign the JAR
 *
 * **Verification Steps**:
 * 1. Validate JWT structure and parse header/payload
 * 2. Validate typ header is "verifier-attestation+jwt"
 * 3. Verify JWT signature against the issuer's key (requires key resolution)
 * 4. Validate issuer (iss) is in the list of trusted attestation issuers
 * 5. Validate subject (sub) matches the expected client_id
 * 6. Validate token is not expired (exp) and not used before validity (nbf)
 * 7. Validate required claims are present (iss, sub, exp, cnf, cnf.jwk)
 * 8. Validate cnf.jwk matches the JAR signer key (key binding verification)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyVerifierAttestationCommand", exact = true)
@JsExportCompat
interface VerifyVerifierAttestationCommand : ServiceCommand<VerifyVerifierAttestationArgs, VerifyVerifierAttestationResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vp.common.verifyattestation"
    }
}

/**
 * Service interface for verifying verifier attestation JWTs.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyVerifierAttestationCommandService", exact = true)
interface VerifyVerifierAttestationCommandService {
    /**
     * Verify a verifier attestation JWT.
     *
     * @param args Verification arguments
     * @return Verification result with success/failure and extracted claims
     */
    suspend fun verifyVerifierAttestation(args: VerifyVerifierAttestationArgs): IdkResult<VerifyVerifierAttestationResult, IdkError>
}
