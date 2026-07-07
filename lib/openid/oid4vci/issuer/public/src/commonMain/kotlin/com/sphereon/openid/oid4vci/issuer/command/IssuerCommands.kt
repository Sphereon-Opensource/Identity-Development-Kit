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
import com.sphereon.di.session.SessionScope
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
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciIssuanceLifecycleHook
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.OptionalBinding
import kotlinx.serialization.Serializable
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
    /** Optional tx_code (PIN) length to advertise in the offer and generate. Null = unspecified (issuer default). */
    val txCodeLength: Int? = null,
    /** Optional tx_code input mode ("numeric" | "text"). Null = issuer default (numeric). */
    val txCodeInputMode: String? = null,
    val preSeededAttributes: Map<String, JsonElement>? = null,
    /**
     * Opaque lifecycle fields for implementation-specific extensions. IDK does not interpret
     * these as credential subject data; they are passed only to [Oid4vciIssuanceLifecycleHook].
     */
    val initialLifecycleFields: Map<String, JsonElement> = emptyMap(),
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
    /**
     * Controls whether the offer URI is single-use (default) or stays alive across multiple
     * wallet fetches, minting a fresh offer on each GET.
     */
    val uriLifecycle: OfferUriLifecycle = OfferUriLifecycle.SINGLE_USE,
    /**
     * Rate-limit applied to a reusable offer URI. Mandatory when
     * [uriLifecycle] is [OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH]; must be null for
     * [OfferUriLifecycle.SINGLE_USE] (ignored but accepted).
     */
    val rateLimit: OfferRateLimit? = null,
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
    val preferredKeyStorageStatusPeriodSeconds: Int? = null,
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

// ============================================================================
// MintDeferralScopedTokenCommand
// ============================================================================

/**
 * Args for the issuer-side mint of a deferral-scoped access token (§6.5 wallet-auth
 * invariant). Used when refresh tokens are disabled on the issuing AS and the credential
 * is deferred for longer than the wallet's original access-token lifetime.
 *
 * The minted JWT is an RFC 9068 `at+jwt` carrying:
 * - `iss` — the issuer identifier of the AS minting the token
 * - `iat` / `exp` — `exp = iat + [ttlSeconds]`
 * - `scope = "deferred_credential"` — the only operation the token authorizes
 * - `correlation_id` / `transaction_id` — pin the token to the specific deferred entry
 * - `cnf.jkt` — DPoP key thumbprint, when the original wallet token was DPoP-bound
 */
@JsExportCompat
@Serializable
data class MintDeferralScopedTokenArgs(
    /**
     * Pipeline correlation id linking the token to the deferred issuance session so the
     * `/deferred_credential` handler can re-resolve the session without trusting opaque
     * wallet state.
     */
    val correlationId: String,
    /** OID4VCI 1.0 §8.3.4 transaction id the wallet polls with. */
    val transactionId: String,
    /** Token lifetime in seconds — typically `deferralPolicy.maxDeferralSeconds + margin`. */
    val ttlSeconds: Long,
    /**
     * Optional DPoP key thumbprint (RFC 9449 `jkt`). When non-null the minted token is
     * DPoP-bound so the wallet must continue presenting a DPoP proof on subsequent polls;
     * null mints a plain Bearer token (the original access token was unbound).
     */
    val cnfJkt: String? = null,
    /**
     * Optional `aud` claim — typically the deferred-credential endpoint URL. Omitted from
     * the payload when null so the token stays valid for the AS-wide audience.
     */
    val audience: String? = null,
)

/**
 * Result of [MintDeferralScopedTokenCommand]: a signed `at+jwt` and the lifetime the wallet
 * should treat the token as valid for.
 */
@JsExportCompat
@Serializable
data class MintDeferralScopedTokenResult(
    /** The minted compact JWS, ready to be returned to the wallet as a bearer/DPoP token. */
    val accessToken: String,
    /** Same value as `ttlSeconds` on the args, returned for caller convenience. */
    val expiresInSeconds: Long,
)

/**
 * §6.5 wallet-auth invariant: mints a deferral-scoped access token the wallet uses to call
 * `/deferred_credential` after its original access token would expire. Wired into the issuer
 * when the deployment has refresh tokens disabled and the deferral policy exceeds the access
 * token lifetime; the common refresh-token-enabled path does not use this command.
 */
@JsExportCompat
interface MintDeferralScopedTokenCommand : ServiceCommand<MintDeferralScopedTokenArgs, MintDeferralScopedTokenResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vci.issuer.mint-deferral-token"
    }
}

/**
 * Exposes [MintDeferralScopedTokenCommand] as an optional graph accessor so that consumers
 * declaring `MintDeferralScopedTokenCommand? = null` constructor parameters resolve cleanly under
 * the Metro `nullable type key`. The IDK
 * [com.sphereon.openid.oid4vci.issuer.impl.command.MintDeferralScopedTokenCommandImpl] adds a
 * second `@ContributesBinding(SessionScope::class, binding = binding<MintDeferralScopedTokenCommand?>())`
 * so this default `null` body is overridden whenever the issuer-impl module is on the classpath.
 */
@ContributesTo(SessionScope::class)
interface MintDeferralScopedTokenCommandOptionalProvider {
    @OptionalBinding
    val optionalMintDeferralScopedTokenCommand: MintDeferralScopedTokenCommand? get() = null
}
