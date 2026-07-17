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

package com.sphereon.openid.oid4vci.holder

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ============================================================================
// ParseCredentialOfferCommand
// ============================================================================

data class ParseCredentialOfferArgs(
    val rawOffer: String,
)

interface ParseCredentialOfferCommand : ServiceCommand<ParseCredentialOfferArgs, CredentialOffer, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.holder.parseoffer"
    }
}

// ============================================================================
// ResolveCredentialOfferCommand
// ============================================================================

data class ResolveCredentialOfferArgs(
    val offer: CredentialOffer,
)

interface ResolveCredentialOfferCommand : ServiceCommand<ResolveCredentialOfferArgs, ResolvedCredentialOffer, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.holder.resolveoffer"
    }
}

// ============================================================================
// ResolveIssuerMetadataCommand
// ============================================================================

data class ResolveIssuerMetadataArgs(
    val issuerUrl: String,
)

interface ResolveIssuerMetadataCommand : ServiceCommand<ResolveIssuerMetadataArgs, CredentialIssuerMetadata, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.holder.metadata"
    }
}

// ============================================================================
// SelectAuthorizationServerCommand
// ============================================================================

data class SelectAuthorizationServerArgs(
    val issuerMetadata: CredentialIssuerMetadata,
    val preferredAuthorizationServer: String? = null,
)

@Serializable
data class ResolvedAuthorizationServer(
    val authorizationServerUrl: String,
    val metadata: AuthorizationServerMetadata,
) {
    val issuer: String get() = metadata.issuer
    val tokenEndpoint: String get() = metadata.tokenEndpoint
    val authorizationEndpoint: String? get() = metadata.authorizationEndpoint
    val pushedAuthorizationRequestEndpoint: String? get() = metadata.pushedAuthorizationRequestEndpoint
    val interactiveAuthorizationEndpoint: String? get() = metadata.interactiveAuthorizationEndpoint
    val jwksUri: String? get() = metadata.jwksUri
}

interface SelectAuthorizationServerCommand : ServiceCommand<SelectAuthorizationServerArgs, ResolvedAuthorizationServer, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.holder.selectas"
    }
}

// ============================================================================
// RequestNonceCommand
// ============================================================================

data class RequestNonceArgs(
    val nonceEndpoint: String,
)

interface RequestNonceCommand : ServiceCommand<RequestNonceArgs, NonceResponse, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.holder.nonce"
    }
}

// ============================================================================
// ExchangePreAuthorizedCodeCommand
// ============================================================================

data class ExchangePreAuthorizedCodeArgs(
    val tokenEndpoint: String,
    val preAuthorizedCode: String,
    val txCode: String? = null,
    val clientId: String? = null,
    val redirectUri: String? = null,
    /** Optional RFC 9449 DPoP proof JWT to send as the `DPoP` token endpoint header. */
    val dpopProofJwt: String? = null,
    /** Optional OAuth attestation-based client-auth JWT for the `OAuth-Client-Attestation` header. */
    val clientAttestationJwt: String? = null,
    /** Optional PoP JWT for the `OAuth-Client-Attestation-PoP` header. */
    val clientAttestationPopJwt: String? = null,
    /** Optional OAuth2 token endpoint client authentication configuration. */
    val clientAuthentication: ClientAuthenticationConfig? = null,
) {
    init {
        require((clientAttestationJwt == null) == (clientAttestationPopJwt == null)) {
            "clientAttestationJwt and clientAttestationPopJwt must be supplied together"
        }
    }
}

@Serializable
data class TokenResponseWithContext(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int? = null,
    @SerialName("authorization_details") val authorizationDetails: List<JsonElement>? = null,
    @SerialName("c_nonce") val cNonce: String? = null,
    @SerialName("c_nonce_expires_in") val cNonceExpiresIn: Int? = null,
    /** OAuth2 refresh token, used later to silently replenish batch-issued credential instances. */
    @SerialName("refresh_token") val refreshToken: String? = null,
    val additionalParameters: Map<String, JsonElement> = emptyMap(),
)

interface ExchangePreAuthorizedCodeCommand : ServiceCommand<ExchangePreAuthorizedCodeArgs, TokenResponseWithContext, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.holder.preauth"
    }
}

// ============================================================================
// CreateCredentialRequestProofCommand
// ============================================================================

data class CreateCredentialRequestProofArgs(
    val issuerUrl: String,
    val cNonce: String? = null,
    val signingKeyIds: List<String>,
    val signingAlgorithm: String = "ES256",
    val clientId: String? = null,
    val keyInclusionMode: JwsIdentifierMode = JwsIdentifierMode.KID,
    /** Optional OID4VCI key-attestation JWT to place in the PoP JWT header or send as an attestation proof. */
    val keyAttestationJwt: String? = null,
    /** The proof type to create (e.g., "jwt", "cwt"). Defaults to "jwt". */
    val proofType: String = "jwt",
) {
    init {
        require(signingKeyIds.isNotEmpty()) { "CreateCredentialRequestProofArgs.signingKeyIds must not be empty" }
        require(signingKeyIds.all { it.isNotBlank() }) { "CreateCredentialRequestProofArgs.signingKeyIds must not contain blank entries" }
    }
}

@Serializable
data class CreatedProof(
    val proofs: CredentialRequestProofs,
)

interface CreateCredentialRequestProofCommand : ServiceCommand<CreateCredentialRequestProofArgs, CreatedProof, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.holder.createproof"
    }
}

// ============================================================================
// RequestCredentialCommand
// ============================================================================

data class RequestCredentialArgs(
    val credentialEndpoint: String,
    val accessToken: String,
    val dpopProofJwt: String? = null,
    val credentialConfigurationId: String? = null,
    val credentialIdentifier: String? = null,
    val proofs: CredentialRequestProofs? = null,
    val credentialResponseEncryption: RequestedCredentialResponseEncryption? = null,
    /**
     * Optional nonce endpoint URL. When set and the issuer returns `invalid_nonce`,
     * a fresh nonce is fetched and returned in the error so the caller can retry
     * with an updated proof.
     */
    val nonceEndpoint: String? = null,
    /**
     * Optional decryption key for JWE credential responses. When [credentialResponseEncryption]
     * is set and the issuer returns an encrypted response (Content-Type: application/jwt),
     * this key is used to decrypt the JWE before parsing the CredentialResponse.
     */
    val decryptionKey: ManagedIdentifierOptsOrResult? = null,
    /**
     * Optional single JWK from issuer metadata for encrypting the request body.
     * When set along with [requestEncryptionAlg] and [requestEncryptionEnc],
     * the credential request JSON is encrypted as a JWE compact serialization
     * and sent with Content-Type: application/jwt.
     *
     * This is a single JWK object (not a JWKS / JWK Set).
     */
    val requestEncryptionJwk: JsonObject? = null,
    /**
     * Key encryption algorithm for request encryption (e.g. "RSA-OAEP", "ECDH-ES+A256KW").
     */
    val requestEncryptionAlg: String? = null,
    /**
     * Content encryption algorithm for request encryption (e.g. "A256GCM").
     */
    val requestEncryptionEnc: String? = null,
)

interface RequestCredentialCommand : ServiceCommand<RequestCredentialArgs, CredentialResponse, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.holder.credential"
    }
}

// ============================================================================
// RequestDeferredCredentialCommand
// ============================================================================

data class RequestDeferredCredentialArgs(
    val deferredCredentialEndpoint: String,
    val accessToken: String,
    val transactionId: String,
    val credentialResponseEncryption: RequestedCredentialResponseEncryption? = null,
    /**
     * Optional decryption key for JWE credential responses. When [credentialResponseEncryption]
     * is set and the issuer returns an encrypted response (Content-Type: application/jwt),
     * this key is used to decrypt the JWE before parsing the CredentialResponse.
     */
    val decryptionKey: ManagedIdentifierOptsOrResult? = null,
    /**
     * Optional single JWK from issuer metadata for encrypting the request body.
     * When set along with [requestEncryptionAlg] and [requestEncryptionEnc],
     * the deferred credential request JSON is encrypted as a JWE compact serialization
     * and sent with Content-Type: application/jwt.
     *
     * This is a single JWK object (not a JWKS / JWK Set).
     */
    val requestEncryptionJwk: JsonObject? = null,
    /**
     * Key encryption algorithm for request encryption (e.g. "RSA-OAEP", "ECDH-ES+A256KW").
     */
    val requestEncryptionAlg: String? = null,
    /**
     * Content encryption algorithm for request encryption (e.g. "A256GCM").
     */
    val requestEncryptionEnc: String? = null,
)

interface RequestDeferredCredentialCommand : ServiceCommand<RequestDeferredCredentialArgs, CredentialResponse, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.holder.deferred"
    }
}

// ============================================================================
// SendNotificationCommand
// ============================================================================

data class SendNotificationArgs(
    val notificationEndpoint: String,
    val accessToken: String,
    val notificationId: String,
    val event: CredentialNotificationEvent,
    val eventDescription: String? = null,
)

interface SendNotificationCommand : ServiceCommand<SendNotificationArgs, Unit, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.holder.notification"
    }
}
