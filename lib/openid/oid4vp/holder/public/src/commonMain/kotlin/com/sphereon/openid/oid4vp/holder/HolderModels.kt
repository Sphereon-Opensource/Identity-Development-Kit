package com.sphereon.openid.oid4vp.holder

import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.ClientIdValidationError
import com.sphereon.openid.oid4vp.common.ClientMetadata
import com.sphereon.openid.oid4vp.common.ParsedTransactionDataEntry
import com.sphereon.openid.oid4vp.common.VerifierAttestation
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Resolved OID4VP authorization request with all metadata and validation complete.
 *
 * This is the result of resolving an authorization request, which includes:
 * - Original request parameters
 * - Parsed DCQL query (if present)
 * - Client metadata (fetched or embedded)
 * - Verified client identity information
 * - Parsed transaction data (if present)
 * - Parsed verifier attestations (if present)
 *
 * Reference: OpenID4VP 1.0 Section 5
 *
 * @property request Original authorization request
 * @property dcqlQuery Parsed DCQL query (null if presentation_definition used)
 * @property clientMetadata Client metadata for the verifier
 * @property verifierInfo Information about the verified client identity
 * @property transactionData Parsed transaction data entries (per OpenID4VP 1.0 Section 5.1.2)
 * @property verifierAttestations Parsed verifier attestations from the verifier_info parameter (per OpenID4VP 1.0 Section 5.1.1)
 */
@Serializable
data class ResolvedOid4vpRequest(
    val request: AuthorizationRequest,
    val dcqlQuery: DcqlQuery? = null,
    val clientMetadata: ClientMetadata? = null,
    val verifierInfo: VerifierInfo,
    val transactionData: List<ParsedTransactionDataEntry>? = null,
    val verifierAttestations: List<VerifierAttestation>? = null
)

/**
 * Information about a verified client (verifier) identity.
 *
 * Client identity is established based on client_id_scheme:
 * - `pre-registered`: Client ID from pre-registration
 * - `redirect_uri`: Client ID matches redirect URI
 * - `entity_id`: Client ID is an Entity ID (OpenID Federation)
 * - `did`: Client ID is a DID
 * - `x509_san_dns`: Client ID from X.509 certificate SAN
 * - `x509_san_uri`: Client ID from X.509 certificate SAN URI
 * - `verifier_attestation`: Client verified via attestation JWT
 *
 * Reference: OpenID4VP 1.0 Section 10 - Client Identifier Schemes
 *
 * @property clientId The verified client identifier
 * @property clientIdScheme The scheme used to verify the client
 * @property clientIdValid Whether the client_id was validated according to its scheme
 * @property clientIdValidationErrors Validation errors if client_id validation failed
 * @property displayName Optional display name for the verifier
 * @property logoUri Optional logo URI for the verifier
 * @property trustRoot Optional trust root (for federation/PKI schemes)
 */
@Serializable
data class VerifierInfo(
    val clientId: String,
    val clientIdScheme: ClientIdScheme,
    val clientIdValid: Boolean = true,
    val clientIdValidationErrors: List<ClientIdValidationError> = emptyList(),
    val displayName: String? = null,
    val logoUri: String? = null,
    val trustRoot: String? = null
)

/**
 * A credential selected by the holder to include in the authorization response.
 *
 * Each selected credential must include:
 * - Credential query ID (from DCQL query)
 * - Identifier (local wallet ID)
 * - Presentation format (JWT, SD-JWT, mdoc)
 * - Presentation payload (signed/serialized credential)
 * - Optional disclosed claims (for selective disclosure formats)
 *
 * Reference: OpenID4VP 1.0 Section 6.1 - VP Token (DCQL Format)
 *
 * @property credentialQueryId The credential query ID from the DCQL query this presentation satisfies.
 *                             This is used as the key in the vp_token object.
 * @property credentialId Local identifier for the credential (wallet-specific)
 * @property presentation Serialized presentation (JWT, SD-JWT, or mdoc CBOR)
 * @property format Format identifier (e.g., "dc+sd-jwt", "mso_mdoc", "jwt_vp")
 * @property disclosedClaims Optional map of disclosed claims (for SD-JWT)
 */
@Serializable
data class SelectedCredential(
    val credentialQueryId: String,
    val credentialId: String,
    val presentation: String,
    val format: String,
    val disclosedClaims: Map<String, String>? = null
)

/**
 * Result of submitting an authorization response to the verifier.
 *
 * Submission results vary by response_mode:
 * - `direct_post`: HTTP response from verifier
 * - `fragment`/`query`: Redirect URI (success assumed)
 *
 * Reference: OpenID4VP 1.0 Section 8.3 - Response Mode: direct_post
 *
 * @see SubmissionResult.Success
 * @see SubmissionResult.Error
 * @see SubmissionResult.Redirect
 */
sealed interface SubmissionResult {
    /**
     * Successful submission with verifier acknowledgment.
     *
     * @property redirectUri Optional redirect URI from verifier (for direct_post)
     * @property responseBody Optional response body from verifier
     */
    @Serializable
    data class Success(
        val redirectUri: String? = null,
        val responseBody: JsonObject? = null
    ) : SubmissionResult

    /**
     * Submission failed with error.
     *
     * Reference: OpenID4VP 1.0 Section 8.3.2 - Error Response
     *
     * @property error Error code (e.g., "invalid_request", "invalid_client")
     * @property errorDescription Human-readable error description
     */
    @Serializable
    data class Error(
        val error: String,
        val errorDescription: String? = null
    ) : SubmissionResult

    /**
     * Redirect-based submission (fragment or query mode).
     *
     * For redirect modes, the response is embedded in the redirect URI.
     * The holder should redirect the user to this URI.
     *
     * @property redirectUri Complete redirect URI with response parameters
     */
    @Serializable
    data class Redirect(
        val redirectUri: String
    ) : SubmissionResult
}

/**
 * Exception thrown by Holder operations.
 *
 * @property message Error message
 * @property cause Optional underlying cause
 */
class Oid4vpHolderException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
