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

package com.sphereon.openid.oid4vci.issuer.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.openid.oid4vci.common.model.BatchCredentialIssuance
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialNotification
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.DeferredCredentialRequest
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialRequestEncryption
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialResponseEncryption
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import kotlinx.serialization.json.JsonElement

// ============================================================================
// CreateCredentialOfferCommand
// ============================================================================

@JsExportCompat
data class CreateCredentialOfferArgs(
    val issuerId: String,
    val credentialConfigurationIds: List<String>,
    val preAuthorizedCodeGrant: Boolean = false,
    val authorizationCodeGrant: Boolean = false,
    val txCodeRequired: Boolean = false,
    val preSeededAttributes: Map<String, JsonElement>? = null,
    val offerTtlSeconds: Long = 600,
    /**
     * Opaque usage-token the calling flow wants bound to this issuance.
     * Carried forward onto the created [IssuanceSession.boundUsageToken] so
     * post-issuance hooks (webhooks, redemption consume, etc.) can correlate
     * the signed credential back to the invitation / ticket / workflow
     * instance it belongs to. Null for flows that don't need correlation.
     */
    val boundUsageToken: String? = null,
    /**
     * Optional per-offer allow-list of post-issuance hook command IDs. When
     * non-null the issuer intersects the deployment-level resolved hook set
     * with this list so the caller (e.g. a redemption batch) can scope
     * hook fan-out. Null = no narrowing (default).
     */
    val postIssuanceHookAllowList: List<String>? = null,
    /**
     * Outer deeplink prefix for `offerUri` (OID4VCI 1.0 §4.1.1). Examples:
     * `"openid-credential-offer://"` (default), `"haip://"`, or a full HTTPS URL such as
     * `"https://wallet.example.com/credential_offer"` for universal-link / app-link wallets.
     * The created `offerUri` is `${scheme}{?|&}credential_offer_uri=…` — i.e. the scheme is
     * prefixed and `credential_offer_uri` is appended with `?` (or `&` when the scheme
     * already carries a query). Null falls back to `openid-credential-offer://`.
     */
    val scheme: String? = null,
)

@JsExportCompat
data class CreatedCredentialOffer(
    val offerId: String,
    val sessionId: String,
    val offer: CredentialOffer,
    val offerUri: String,
    val txCode: String? = null,
)

@JsExportCompat
interface CreateCredentialOfferCommand : ServiceCommand<CreateCredentialOfferArgs, CreatedCredentialOffer, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.issuer.createoffer"
    }
}

// ============================================================================
// BuildIssuerMetadataCommand
// ============================================================================

@JsExportCompat
data class BuildIssuerMetadataArgs(
    val issuerIdentifier: String,
    val baseUrl: String,
    val authorizationServers: List<String>? = null,
    val credentialConfigurations: Map<String, CredentialConfigurationSupported>,
    val display: List<com.sphereon.openid.oid4vc.common.DisplayProperties>? = null,
    val credentialResponseEncryption: MetadataCredentialResponseEncryption? = null,
    val credentialRequestEncryption: MetadataCredentialRequestEncryption? = null,
    val batchCredentialIssuance: BatchCredentialIssuance? = null,
)

@JsExportCompat
interface BuildIssuerMetadataCommand : ServiceCommand<BuildIssuerMetadataArgs, CredentialIssuerMetadata, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.issuer.metadata"
    }
}

// ============================================================================
// IssueNonceCommand
// ============================================================================

@JsExportCompat
data class IssueNonceArgs(
    val ttlSeconds: Long = 300,
)

@JsExportCompat
interface IssueNonceCommand : ServiceCommand<IssueNonceArgs, NonceResponse, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.issuer.nonce"
    }
}

// ============================================================================
// HandleCredentialRequestCommand
// ============================================================================

@JsExportCompat
data class HandleCredentialRequestArgs(
    val accessToken: String,
    val dpopProof: String? = null,
    val credentialRequest: CredentialRequest,
    val issuerIdentifier: String? = null,
    val credentialConfigurations: Map<String, CredentialConfigurationSupported> = emptyMap(),
    /**
     * Full request URL the credential endpoint received (with scheme + host but stripped of
     * query/fragment). Forwarded to the AS bridge so DPoP `htu` can be verified against the
     * actual incoming request — RFC 9449 §7.1 requires this for any DPoP-bound access token.
     */
    val httpUrl: String? = null,
    /**
     * HTTP method the credential endpoint was invoked with (always `POST` per OID4VCI 1.0 §8).
     * Forwarded to the AS bridge for DPoP `htm` verification.
     */
    val httpMethod: String? = null,
)

@JsExportCompat
interface HandleCredentialRequestCommand : ServiceCommand<HandleCredentialRequestArgs, CredentialResponse, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.issuer.credential"
    }
}

// ============================================================================
// HandleDeferredCredentialRequestCommand
// ============================================================================

@JsExportCompat
data class HandleDeferredCredentialRequestArgs(
    val accessToken: String,
    val dpopProof: String? = null,
    val deferredRequest: DeferredCredentialRequest,
    /** Public-facing request URL — required for DPoP `htu` verification (RFC 9449 §7.1). */
    val httpUrl: String? = null,
    /** HTTP method — required for DPoP `htm` verification. */
    val httpMethod: String? = null,
)

@JsExportCompat
interface HandleDeferredCredentialRequestCommand : ServiceCommand<HandleDeferredCredentialRequestArgs, CredentialResponse, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.issuer.deferred"
    }
}

// ============================================================================
// HandleNotificationCommand
// ============================================================================

@JsExportCompat
data class HandleNotificationArgs(
    val accessToken: String,
    val notification: CredentialNotification,
    /** Public-facing request URL — required for DPoP `htu` verification (RFC 9449 §7.1). */
    val httpUrl: String? = null,
    /** HTTP method — required for DPoP `htm` verification. */
    val httpMethod: String? = null,
    /** DPoP proof header — required for DPoP-bound access tokens. */
    val dpopProof: String? = null,
)

@JsExportCompat
interface HandleNotificationCommand : ServiceCommand<HandleNotificationArgs, Unit, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.issuer.notification"
    }
}

// ============================================================================
// BuildSignedIssuerMetadataCommand
// ============================================================================

@JsExportCompat
data class BuildSignedIssuerMetadataArgs(
    val metadata: CredentialIssuerMetadata,
    val signingKey: ManagedIdentifierOptsOrResult,
    val identifierMode: JwsIdentifierMode = JwsIdentifierMode.AUTO,
)

@JsExportCompat
interface BuildSignedIssuerMetadataCommand : ServiceCommand<BuildSignedIssuerMetadataArgs, JwtCompactResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.issuer.signedmetadata"
    }
}
