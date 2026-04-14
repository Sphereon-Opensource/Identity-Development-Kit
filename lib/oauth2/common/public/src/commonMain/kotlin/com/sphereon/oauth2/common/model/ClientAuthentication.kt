/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.common.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * OAuth 2.0 Client Authentication Methods (RFC 6749, RFC 7521, RFC 7523)
 *
 * Defines the methods a client can use to authenticate to the authorization server:
 * - client_secret_basic: HTTP Basic authentication (RFC 6749 Section 2.3.1)
 * - client_secret_post: Client credentials in request body (RFC 6749 Section 2.3.1)
 * - client_secret_jwt: JWT signed with client secret (RFC 7523)
 * - private_key_jwt: JWT signed with private key (RFC 7523)
 * - none: Public client (no authentication)
 * - attest_jwt_client_auth: Client attestation JWT (draft-ietf-oauth-attestation-based-client-auth)
 */
@JsExportCompat
@Serializable
enum class ClientAuthenticationMethod(
    val value: String,
) {
    CLIENT_SECRET_BASIC("client_secret_basic"),
    CLIENT_SECRET_POST("client_secret_post"),
    CLIENT_SECRET_JWT("client_secret_jwt"),
    PRIVATE_KEY_JWT("private_key_jwt"),
    NONE("none"),
    ATTEST_JWT_CLIENT_AUTH("attest_jwt_client_auth"),
    ;

    companion object {
        fun fromValue(value: String): ClientAuthenticationMethod? = entries.find { it.value == value }
    }
}

/**
 * Client credentials for authentication
 *
 * Used for client_secret_basic and client_secret_post authentication methods
 */
@JsExportCompat
data class ClientCredentials(
    val clientId: String,
    val clientSecret: String,
)

/**
 * Client assertion for JWT-based authentication (RFC 7521, RFC 7523)
 *
 * Used for client_secret_jwt and private_key_jwt authentication methods
 */
@JsExportCompat
data class ClientAssertion(
    val clientId: String,
    // e.g., "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
    val assertionType: String,
    // The JWT
    val assertion: String,
)

/**
 * Client attestation for attestation-based authentication
 *
 * Used for attest_jwt_client_auth authentication method
 * (draft-ietf-oauth-attestation-based-client-auth)
 */
@JsExportCompat
data class ClientAttestation(
    val clientAttestationJwt: String,
    val clientAttestationPopJwt: String,
)

/**
 * Configuration for client authentication
 *
 * Specifies which authentication method to use and the necessary credentials
 */
sealed interface ClientAuthenticationConfig {
    /**
     * HTTP Basic authentication (RFC 6749 Section 2.3.1)
     *
     * Sends credentials in Authorization header: Basic base64(clientId:clientSecret)
     */
    data class Basic(
        val credentials: ClientCredentials,
    ) : ClientAuthenticationConfig

    /**
     * Client credentials in POST body (RFC 6749 Section 2.3.1)
     *
     * Sends client_id and client_secret as form parameters
     */
    data class Post(
        val credentials: ClientCredentials,
    ) : ClientAuthenticationConfig

    /**
     * JWT signed with client secret (RFC 7523)
     *
     * Client authenticates using a JWT signed with the client secret (HMAC)
     */
    data class SecretJwt(
        val assertion: ClientAssertion,
    ) : ClientAuthenticationConfig

    /**
     * JWT signed with private key (RFC 7523)
     *
     * Client authenticates using a JWT signed with a private key (RSA/ECDSA)
     */
    data class PrivateKeyJwt(
        val assertion: ClientAssertion,
    ) : ClientAuthenticationConfig

    /**
     * No authentication - public client
     *
     * Only sends client_id (for public clients)
     */
    data class None(
        val clientId: String,
    ) : ClientAuthenticationConfig

    /**
     * Client attestation JWT (draft-ietf-oauth-attestation-based-client-auth)
     *
     * Uses OAuth-Client-Attestation and OAuth-Client-Attestation-PoP headers
     */
    data class AttestationJwt(
        val attestation: ClientAttestation,
    ) : ClientAuthenticationConfig

    /**
     * Anonymous - no client identification
     *
     * Used for pre-authorized code grant with anonymous access
     */
    data object Anonymous : ClientAuthenticationConfig
}

/**
 * Result of applying client authentication to a request
 *
 * Contains the modified headers and body parameters
 */
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
data class ClientAuthenticationResult(
    val headers: Map<String, String>,
    val bodyParameters: Map<String, String>,
)
