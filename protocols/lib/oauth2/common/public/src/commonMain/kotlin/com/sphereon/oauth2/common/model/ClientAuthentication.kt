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
 * - tls_client_auth: PKI mutual-TLS (RFC 8705 Â§2.1)
 * - self_signed_tls_client_auth: Self-signed mutual-TLS bound to a registered JWK (RFC 8705 Â§2.2)
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
    TLS_CLIENT_AUTH("tls_client_auth"),
    SELF_SIGNED_TLS_CLIENT_AUTH("self_signed_tls_client_auth"),
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
class ClientCredentials(
    val clientId: String,
    val clientSecret: String,
) {
    override fun toString(): String = "ClientCredentials(<redacted>)"
}

/**
 * Client assertion for JWT-based authentication (RFC 7521, RFC 7523)
 *
 * Used for client_secret_jwt and private_key_jwt authentication methods
 */
@JsExportCompat
class ClientAssertion(
    val clientId: String,
    // e.g., "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
    val assertionType: String,
    // The JWT
    val assertion: String,
) {
    override fun toString(): String = "ClientAssertion(<redacted>)"
}

/**
 * Client attestation for attestation-based authentication
 *
 * Used for attest_jwt_client_auth authentication method
 * (draft-ietf-oauth-attestation-based-client-auth)
 */
@JsExportCompat
class ClientAttestation(
    val clientAttestationJwt: String,
    val clientAttestationPopJwt: String,
) {
    override fun toString(): String = "ClientAttestation(<redacted>)"

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = REDACTED_HASH_CODE
}

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
    class Basic(
        val credentials: ClientCredentials,
    ) : ClientAuthenticationConfig {
        override fun toString(): String = "ClientAuthenticationConfig.Basic(<redacted>)"
    }

    /**
     * Client credentials in POST body (RFC 6749 Section 2.3.1)
     *
     * Sends client_id and client_secret as form parameters
     */
    class Post(
        val credentials: ClientCredentials,
    ) : ClientAuthenticationConfig {
        override fun toString(): String = "ClientAuthenticationConfig.Post(<redacted>)"
    }

    /**
     * JWT signed with client secret (RFC 7523)
     *
     * Client authenticates using a JWT signed with the client secret (HMAC)
     */
    class SecretJwt(
        val assertion: ClientAssertion,
    ) : ClientAuthenticationConfig {
        override fun toString(): String = "ClientAuthenticationConfig.SecretJwt(<redacted>)"
    }

    /**
     * JWT signed with private key (RFC 7523)
     *
     * Client authenticates using a JWT signed with a private key (RSA/ECDSA)
     */
    class PrivateKeyJwt(
        val assertion: ClientAssertion,
    ) : ClientAuthenticationConfig {
        override fun toString(): String = "ClientAuthenticationConfig.PrivateKeyJwt(<redacted>)"
    }

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

    /**
     * Mutual-TLS client authentication (RFC 8705 Â§2).
     *
     * The governed HTTP request context owns the endpoint-bound KMS client identity used by the
     * TLS handshake. The actual verification mode (PKI subject/SAN match versus self-signed JWK
     * match) is selected per-client by the registered `token_endpoint_auth_method`. Governed
     * outbound clients construct this with only the `client_id`; the optional byte array remains
     * solely for server-side extraction of the certificate presented at an inbound AS edge.
     */
    class MutualTls(
        val clientId: String,
        /** Presented server-side certificate bytes. Governed outbound clients leave this empty. */
        val clientCertificateDer: ByteArray = byteArrayOf(),
    ) : ClientAuthenticationConfig {
        override fun toString(): String = "ClientAuthenticationConfig.MutualTls(<redacted>)"
    }
}

/**
 * Result of applying client authentication to a request
 *
 * Contains the modified headers and body parameters
 */
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
class ClientAuthenticationResult(
    val headers: Map<String, String>,
    val bodyParameters: Map<String, String>,
) {
    override fun toString(): String = "ClientAuthenticationResult(<redacted>)"

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = REDACTED_HASH_CODE
}

private const val REDACTED_HASH_CODE: Int = 0


