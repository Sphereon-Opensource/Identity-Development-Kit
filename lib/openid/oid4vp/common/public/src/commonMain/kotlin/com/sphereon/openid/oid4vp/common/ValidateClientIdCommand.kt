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
 */

package com.sphereon.openid.oid4vp.common

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.x509.Certificate
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlin.native.ObjCName

// =============================================================================
// ValidateClientIdCommand - Validate client_id per scheme (OpenID4VP 1.0 Section 5.2)
// =============================================================================

/**
 * Arguments for validating a client_id.
 *
 * @property parsedClientId The parsed client_id with scheme and identifier
 * @property jarUsed Whether a JAR (JWT Authorization Request) was used
 * @property jarSignerMethod The signer method detected from the JAR (if JAR was used)
 * @property jarTypHeader The typ header from the JAR JWT (for validation)
 * @property jarSignerCertificates X.509 certificate chain from JAR x5c header (for x509_* schemes)
 * @property jarSignerKid The key ID (kid) from the JAR header (for DID/custom validation)
 * @property jarSignerJwk The JWK used to sign the JAR (for verifier_attestation cnf binding verification)
 * @property jarAttestationJwt The attestation JWT from JAR jwt header (for verifier_attestation scheme)
 * @property trustedAttestationIssuers List of trusted attestation issuer URIs (for verifier_attestation scheme)
 * @property redirectUri The redirect_uri from the request (for redirect_uri scheme validation)
 * @property responseUri The response_uri from the request (for redirect_uri scheme validation)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ValidateClientIdArgs", exact = true)
@JsExportCompat
data class ValidateClientIdArgs(
    val parsedClientId: ParsedClientId,
    val jarUsed: Boolean = false,
    val jarSignerMethod: JarSignerMethod? = null,
    val jarTypHeader: String? = null,
    val jarSignerCertificates: List<Certificate>? = null,
    val jarSignerKid: String? = null,
    val jarSignerJwk: com.sphereon.crypto.core.jose.Jwk? = null,
    val jarAttestationJwt: String? = null,
    val trustedAttestationIssuers: List<String>? = null,
    val redirectUri: String? = null,
    val responseUri: String? = null
)

/**
 * Result of validating a client_id.
 *
 * @property valid Whether the client_id is valid for the scheme
 * @property scheme The client_id scheme that was validated
 * @property clientId The client_id value (without scheme prefix)
 * @property verifiedCertificate The leaf certificate that was verified (for x509_* schemes)
 * @property resolvedSignerJwk The resolved public key JWK for signature verification (for decentralized_identifier scheme)
 * @property resolvedVerificationMethodId The resolved verification method ID (for decentralized_identifier scheme)
 * @property errors List of validation errors
 * @property warnings List of validation warnings (non-fatal issues)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ValidateClientIdResult", exact = true)
@JsExportCompat
@Serializable
data class ValidateClientIdResult(
    val valid: Boolean,
    val scheme: ClientIdScheme,
    val clientId: String,
    val verifiedCertificate: Certificate? = null,
    val resolvedSignerJwk: Jwk? = null,
    val resolvedVerificationMethodId: String? = null,
    val errors: List<ClientIdValidationError> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * Validation error types for client_id validation.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ClientIdValidationErrorType", exact = true)
@JsExportCompat
@Serializable
enum class ClientIdValidationErrorType {
    /** JAR was used but should not be for this scheme (e.g., redirect_uri) */
    JAR_NOT_ALLOWED,
    
    /** JAR is required for this scheme but was not used */
    JAR_REQUIRED,
    
    /** JAR signer method is not allowed for this scheme */
    JAR_SIGNER_METHOD_NOT_ALLOWED,
    
    /** JAR typ header is invalid (expected oauth-authz-req+jwt) */
    JAR_TYP_HEADER_INVALID,
    
    /** JAR signer DID does not match client_id (for decentralized_identifier scheme) */
    JAR_SIGNER_DID_MISMATCH,
    
    /** X.509 certificate chain is missing for x509_* scheme */
    CERTIFICATE_MISSING,
    
    /** X.509 certificate chain validation failed */
    CERTIFICATE_VALIDATION_FAILED,
    
    /** SAN DNS name does not match client_id */
    SAN_DNS_MISMATCH,
    
    /** SAN URI does not match client_id */
    SAN_URI_MISMATCH,
    
    /** Certificate hash does not match client_id */
    CERTIFICATE_HASH_MISMATCH,
    
    /** redirect_uri/response_uri does not match client_id */
    REDIRECT_URI_MISMATCH,
    
    /** The scheme is not supported */
    SCHEME_NOT_SUPPORTED,
    
    /** Verifier attestation JWT is missing from JAR jwt header */
    ATTESTATION_JWT_MISSING,
    
    /** Verifier attestation JWT signature verification failed */
    ATTESTATION_SIGNATURE_INVALID,
    
    /** Verifier attestation JWT issuer is not trusted */
    ATTESTATION_ISSUER_NOT_TRUSTED,
    
    /** Verifier attestation JWT subject does not match client_id */
    ATTESTATION_SUBJECT_MISMATCH,
    
    /** Verifier attestation JWT is expired */
    ATTESTATION_EXPIRED,
    
    /** Verifier attestation JWT is missing required claims */
    ATTESTATION_CLAIMS_MISSING,
    
    /** Verifier attestation JWT cnf.jwk does not match JAR signer */
    ATTESTATION_CNF_MISMATCH,
    
    /** JAR signer JWK is missing for verifier attestation cnf binding verification */
    JAR_SIGNER_JWK_MISSING,

    /** DID resolution failed */
    DID_RESOLUTION_FAILED,

    /** DID document was not found */
    DID_DOCUMENT_NOT_FOUND,

    /** DID method is not supported */
    DID_METHOD_NOT_SUPPORTED,

    /** Verification method was not found in DID document */
    DID_VERIFICATION_METHOD_NOT_FOUND,

    /** Verification method has no key material */
    DID_NO_KEY_MATERIAL,

    /** Invalid DID format */
    DID_INVALID_FORMAT,

    /** Generic validation error */
    VALIDATION_ERROR
}

/**
 * A client_id validation error.
 *
 * @property type The type of validation error
 * @property message Human-readable error message
 * @property details Additional details about the error
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ClientIdValidationError", exact = true)
@JsExportCompat
@Serializable
data class ClientIdValidationError(
    val type: ClientIdValidationErrorType,
    val message: String,
    val details: String? = null
)

/**
 * Command to validate client_id according to its scheme.
 *
 * OpenID4VP 1.0 Final Section 5.2 defines validation requirements per scheme:
 *
 * - **pre-registered**: Trust is pre-configured, no additional validation required
 * - **redirect_uri**: JAR MUST NOT be used, client_id must match redirect_uri/response_uri
 * - **x509_san_dns**: JAR required with x5c, SAN DNS name must match client_id
 * - **x509_san_uri**: JAR required with x5c, SAN URI must match client_id
 * - **x509_hash**: JAR required with x5c, certificate hash must match client_id
 * - **verifier_attestation**: JAR required, attestation validation (future)
 * - **openid_federation**: Trust via federation (out of scope)
 * - **decentralized_identifier**: DID resolution required (out of scope)
 *
 * This command integrates with the identifier resolution service for X.509 validation.
 */
interface ValidateClientIdCommand : ServiceCommand<ValidateClientIdArgs, ValidateClientIdResult> {
    companion object {
        const val COMMAND_ID = "oid4vp.client.validate"
    }

    override val commandId: String get() = COMMAND_ID
}

/**
 * Service interface for client_id validation.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ValidateClientIdCommandService", exact = true)
@JsExportIgnoreCompat
interface ValidateClientIdCommandService {
    /**
     * Validate client_id according to its scheme.
     *
     * @param args Validation arguments including parsed client_id and JAR info
     * @return Validation result with success/failure and details
     */
    suspend fun validateClientId(args: ValidateClientIdArgs): IdkResult<ValidateClientIdResult, IdkError>
}
