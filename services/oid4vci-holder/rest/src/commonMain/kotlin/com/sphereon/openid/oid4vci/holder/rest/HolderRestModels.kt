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

package com.sphereon.openid.oid4vci.holder.rest

import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Request body for POST /oid4vci/holder/offers/resolve.
 *
 * [rawOffer] may be a credential_offer URI (openid-credential-offer://...), a URI with
 * a `credential_offer_uri` query parameter, or an inline JSON credential offer string.
 */
@Serializable
data class ResolveOfferRequest(
    @SerialName("raw_offer") val rawOffer: String,
)

/**
 * Request body for POST /oid4vci/holder/token/preauth.
 *
 * Contains the token endpoint URL and the pre-authorized code to exchange, along with
 * optional tx_code (transaction code from user), client_id, and redirect_uri.
 */
@Serializable
data class ExchangePreAuthorizedCodeRequest(
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("pre_authorized_code") val preAuthorizedCode: String,
    @SerialName("tx_code") val txCode: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("redirect_uri") val redirectUri: String? = null,
)

/**
 * Request body for POST /oid4vci/holder/nonce.
 *
 * Contains the nonce endpoint URL as advertised in the issuer metadata.
 */
@Serializable
data class NonceEndpointRequest(
    @SerialName("nonce_endpoint") val nonceEndpoint: String,
)

/**
 * Request body for POST /oid4vci/holder/credential.
 *
 * Drives a credential request from the wallet to the issuer credential endpoint.
 * [accessToken] is the Bearer token obtained from the token endpoint.
 * Either [credentialConfigurationId] or [credentialIdentifier] must be provided.
 */
@Serializable
data class CredentialRequestBody(
    @SerialName("credential_endpoint") val credentialEndpoint: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("credential_configuration_id") val credentialConfigurationId: String? = null,
    @SerialName("credential_identifier") val credentialIdentifier: String? = null,
    val proofs: CredentialRequestProofs? = null,
    @SerialName("credential_response_encryption") val credentialResponseEncryption: RequestedCredentialResponseEncryption? = null,
    @SerialName("request_encryption_jwk") val requestEncryptionJwk: JsonObject? = null,
    @SerialName("request_encryption_alg") val requestEncryptionAlg: String? = null,
    @SerialName("request_encryption_enc") val requestEncryptionEnc: String? = null,
    @SerialName("decryption_key_id") val decryptionKeyId: String? = null,
)

/**
 * Request body for POST /oid4vci/holder/deferred.
 *
 * Used to poll the issuer's deferred credential endpoint for a pending credential.
 * [transactionId] was returned in the initial CredentialResponse when issuance was deferred.
 */
@Serializable
data class DeferredCredentialRequestBody(
    @SerialName("deferred_credential_endpoint") val deferredCredentialEndpoint: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("credential_response_encryption") val credentialResponseEncryption: RequestedCredentialResponseEncryption? = null,
    @SerialName("request_encryption_jwk") val requestEncryptionJwk: JsonObject? = null,
    @SerialName("request_encryption_alg") val requestEncryptionAlg: String? = null,
    @SerialName("request_encryption_enc") val requestEncryptionEnc: String? = null,
    @SerialName("decryption_key_id") val decryptionKeyId: String? = null,
)

/**
 * Request body for POST /oid4vci/holder/notification.
 *
 * Sends a lifecycle notification back to the issuer.
 * [event] is one of: credential_accepted, credential_failure, credential_deleted.
 */
@Serializable
data class NotificationRequestBody(
    @SerialName("notification_endpoint") val notificationEndpoint: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("notification_id") val notificationId: String,
    val event: CredentialNotificationEvent,
    @SerialName("event_description") val eventDescription: String? = null,
)

// ============================================================================
// Session-based endpoint request bodies
// ============================================================================

/**
 * Request body for POST /oid4vci/holder/sessions.
 *
 * Creates a new client session by parsing and resolving the credential offer URI.
 */
@Serializable
data class CreateSessionRequest(
    @SerialName("offer_uri") val offerUri: String,
    @SerialName("client_id") val clientId: String? = null,
)

/**
 * Request body for POST /oid4vci/holder/sessions/{id}/token.
 *
 * Exchanges a pre-authorized code for an access token and stores the result in the session.
 */
@Serializable
data class SessionTokenRequest(
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("pre_authorized_code") val preAuthorizedCode: String,
    @SerialName("tx_code") val txCode: String? = null,
)

/**
 * Request body for POST /oid4vci/holder/sessions/{id}/credentials.
 *
 * Requests a credential from the issuer using the session's access token.
 * Creates a key-bound proof automatically via [signingKeyId].
 */
@Serializable
data class SessionCredentialRequest(
    @SerialName("credential_endpoint") val credentialEndpoint: String,
    @SerialName("credential_configuration_id") val credentialConfigurationId: String? = null,
    @SerialName("credential_identifier") val credentialIdentifier: String? = null,
    @SerialName("signing_key_id") val signingKeyId: String,
    @SerialName("signing_algorithm") val signingAlgorithm: String = "ES256",
    @SerialName("nonce_endpoint") val nonceEndpoint: String? = null,
    @SerialName("deferred_credential_endpoint") val deferredCredentialEndpoint: String? = null,
    @SerialName("notification_endpoint") val notificationEndpoint: String? = null,
    @SerialName("credential_response_encryption") val credentialResponseEncryption: RequestedCredentialResponseEncryption? = null,
)

/**
 * Request body for POST /oid4vci/holder/sessions/{id}/deferred/poll.
 *
 * Polls the issuer's deferred credential endpoint using the session's access token.
 */
@Serializable
data class SessionDeferredPollRequest(
    @SerialName("deferred_credential_endpoint") val deferredCredentialEndpoint: String,
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("credential_response_encryption") val credentialResponseEncryption: RequestedCredentialResponseEncryption? = null,
)

/**
 * Request body for POST /oid4vci/holder/sessions/{id}/notify.
 *
 * Sends a notification to the issuer using the session's access token.
 */
@Serializable
data class SessionNotifyRequest(
    @SerialName("notification_endpoint") val notificationEndpoint: String,
    @SerialName("notification_id") val notificationId: String,
    val event: CredentialNotificationEvent,
    @SerialName("event_description") val eventDescription: String? = null,
)

/**
 * Request body for POST /oid4vci/holder/sessions/{id}/iae/followUp.
 *
 * Submits an OID4VCI 1.1 IAE follow-up request using the session context.
 */
@Serializable
data class SessionIaeFollowUpRequest(
    @SerialName("iae_endpoint") val iaeEndpoint: String,
    @SerialName("auth_session") val authSession: String,
    @SerialName("openid4vp_response") val openid4vpResponse: JsonObject? = null,
    @SerialName("code_verifier") val codeVerifier: String? = null,
)

// ============================================================================
// Auth-code flow and IAE stateless request/response bodies
// ============================================================================

/**
 * Request body for POST /oid4vci/holder/iae/initiate.
 *
 * Initiates an OID4VCI 1.1 Interactive Authorization Endpoint (IAE) flow.
 * [credentialConfigurationIds] are converted to authorization_details entries.
 */
@Serializable
data class InitiateIaeRequest(
    @SerialName("iae_endpoint") val iaeEndpoint: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("redirect_uri") val redirectUri: String,
    @SerialName("credential_configuration_ids") val credentialConfigurationIds: List<String>,
    @SerialName("interaction_types_supported") val interactionTypesSupported: List<String>,
    val scope: String? = null,
)

/**
 * Request body for POST /oid4vci/holder/auth/request.
 *
 * Builds an OID4VCI Authorization Code Flow authorization request URL with PKCE.
 */
@Serializable
data class BuildAuthorizationRequestRequest(
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("redirect_uri") val redirectUri: String,
    @SerialName("credential_configuration_ids") val credentialConfigurationIds: List<String>,
    val scope: String? = null,
    @SerialName("issuer_state") val issuerState: String? = null,
    @SerialName("use_par") val usePar: Boolean = false,
    @SerialName("par_endpoint") val parEndpoint: String? = null,
    @SerialName("credential_identifiers") val credentialIdentifiers: Map<String, List<String>>? = null,
    val locations: List<String>? = null,
)

/**
 * Response body for POST /oid4vci/holder/auth/request.
 *
 * Contains the authorization URL to redirect the user to, the PKCE code verifier
 * to store for token exchange, and the state parameter for CSRF protection.
 */
@Serializable
data class AuthorizationRequestResponse(
    @SerialName("authorization_url") val authorizationUrl: String,
    @SerialName("code_verifier") val codeVerifier: String,
    val state: String,
)

/**
 * Request body for POST /oid4vci/holder/auth/exchange.
 *
 * Exchanges an authorization code for tokens at the given token endpoint using PKCE.
 */
@Serializable
data class ExchangeAuthorizationCodeRequest(
    @SerialName("token_endpoint") val tokenEndpoint: String,
    val code: String,
    @SerialName("code_verifier") val codeVerifier: String,
    @SerialName("redirect_uri") val redirectUri: String,
    @SerialName("client_id") val clientId: String? = null,
)

// ============================================================================
// Session-based auth-code flow and IAE request bodies
// ============================================================================

/**
 * Request body for POST /oid4vci/holder/sessions/{id}/auth/request.
 *
 * Builds an authorization request URL using the session's [credentialConfigurationIds]
 * and [issuerState]. Only endpoint URLs and client details are required from the caller.
 */
@Serializable
data class SessionBuildAuthorizationRequestRequest(
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("redirect_uri") val redirectUri: String,
    val scope: String? = null,
    @SerialName("use_par") val usePar: Boolean = false,
    @SerialName("par_endpoint") val parEndpoint: String? = null,
    @SerialName("credential_identifiers") val credentialIdentifiers: Map<String, List<String>>? = null,
    val locations: List<String>? = null,
)

/**
 * Request body for POST /oid4vci/holder/sessions/{id}/auth/exchange.
 *
 * Exchanges an authorization code for tokens and updates the session with
 * the obtained access token and credential identifiers.
 */
@Serializable
data class SessionExchangeAuthorizationCodeRequest(
    @SerialName("token_endpoint") val tokenEndpoint: String,
    val code: String,
    @SerialName("code_verifier") val codeVerifier: String,
    @SerialName("redirect_uri") val redirectUri: String,
)

/**
 * Request body for POST /oid4vci/holder/sessions/{id}/iae/initiate.
 *
 * Initiates an IAE flow using the session's [credentialConfigurationIds].
 * Only endpoint URLs, client details, and interaction types are required from the caller.
 */
@Serializable
data class SessionInitiateIaeRequest(
    @SerialName("iae_endpoint") val iaeEndpoint: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("redirect_uri") val redirectUri: String,
    @SerialName("interaction_types_supported") val interactionTypesSupported: List<String>,
    val scope: String? = null,
)

// ============================================================================
// Session credential flow response
// ============================================================================

/**
 * Serializable response for POST /oid4vci/holder/sessions/{id}/credentials.
 *
 * Wraps the [CredentialFlowResult] sealed class (which is not itself serializable)
 * into a flat, wire-safe envelope that the REST layer can encode as JSON.
 *
 * [outcome] is one of: "immediate", "deferred_completed", "deferred_exhausted".
 */
@Serializable
data class SessionCredentialFlowResponse(
    val outcome: String,
    val credential: CredentialResponse? = null,
    @SerialName("poll_attempts") val pollAttempts: Int? = null,
    @SerialName("notification_sent") val notificationSent: Boolean? = null,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("attempts_made") val attemptsMade: Int? = null,
    @SerialName("last_interval") val lastInterval: Int? = null,
)
