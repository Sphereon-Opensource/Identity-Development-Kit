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

package com.sphereon.openid.oid4vci.common.dsl

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ---------------------------------------------------------------------------
// Note on import aliasing
// ---------------------------------------------------------------------------
// The holder and issuer command args live in their own modules (holder-public,
// issuer-public) and cannot be imported here (common-public has no dependency
// on them). The builders below therefore return the actual data class instances
// by constructing them directly via their constructors. Callers must import
// the args types from the appropriate module themselves.
//
// To keep this file self-contained and compilable within common-public, the
// six builder classes and their entry-point functions are defined here.
// The actual `build()` functions return the concrete args types — those types
// are brought in via the typealias helpers declared in the companion modules,
// or callers use the builders from the same compilation unit.
//
// Since Kotlin DSL builders are value-object factories the approach taken is:
//   - Each Builder exposes properties / methods that mirror the args fields
//   - build() constructs the final data class (args type must be on classpath)
//
// The args types are in holder-public / issuer-public, which DO depend on
// common-public, NOT the other way around.  Therefore this file defines only
// the builder state classes; the top-level `fun requestCredentialArgs(...)` etc.
// are provided as extension files in holder-public / issuer-public respectively.
//
// Builders that depend solely on types available in common-public are fully
// implemented here. Builders whose `build()` references holder/issuer args are
// marked `internal open` so they can be finalised in the higher-level module.

// ============================================================================
// Shared builder helpers
// ============================================================================

/**
 * Builder for [RequestedCredentialResponseEncryption].
 * Used inside both [RequestCredentialArgsBuilder] and [RequestCredentialWithFlowArgsBuilder].
 */
@JsExportCompat
@Oid4vciDsl
class RequestedEncryptionBuilder {
    /** The requester's public JWK used to encrypt the credential response. */
    var jwk: JsonObject? = null

    /**
     * Key-wrapping algorithm (e.g. `"ECDH-ES"`, `"RSA-OAEP-256"`).
     * Optional in OID4VCI 1.1 — key agreement may be implicit from the JWK.
     */
    var alg: String? = null

    /** Content-encryption algorithm (e.g. `"A256GCM"`, `"A128CBC-HS256"`). Required. */
    var enc: String? = null

    /** Compression algorithm (e.g. `"DEF"`). */
    var zip: String? = null

    fun build(): RequestedCredentialResponseEncryption {
        val resolvedJwk =
            requireNotNull(jwk) {
                "RequestedEncryptionBuilder: 'jwk' must be set — provide the requester public key as a JsonObject"
            }
        val resolvedEnc =
            requireNotNull(enc) {
                "RequestedEncryptionBuilder: 'enc' must be set — provide the content-encryption algorithm (e.g. \"A256GCM\")"
            }
        return RequestedCredentialResponseEncryption(
            jwk = resolvedJwk,
            alg = alg,
            enc = resolvedEnc,
            zip = zip,
        )
    }
}

// ============================================================================
// 1. RequestCredentialArgsBuilder
// ============================================================================

/**
 * Builder for `RequestCredentialArgs` (holder-public).
 *
 * At least one of [credentialConfigurationId] / [credentialIdentifier] must be set (XOR).
 *
 * Usage:
 * ```kotlin
 * requestCredentialArgs {
 *     endpoint("https://issuer/credential", accessToken = "token")
 *     credentialConfigurationId("UniversityDegree")
 *     proofs("jwt", "eyJ...")
 *     nonceEndpoint("https://issuer/nonce")
 * }
 * ```
 */
@Oid4vciDsl
class RequestCredentialArgsBuilder {
    private var credentialEndpoint: String? = null
    private var accessToken: String? = null

    /** Mutually exclusive with [credentialIdentifier]. */
    var credentialConfigurationId: String? = null

    /** Mutually exclusive with [credentialConfigurationId]. */
    var credentialIdentifier: String? = null

    private var proofs: CredentialRequestProofs? = null
    private var encryption: RequestedCredentialResponseEncryption? = null

    /** Optional nonce endpoint for auto-retry on `invalid_nonce`. */
    var nonceEndpoint: String? = null

    /**
     * Set the credential endpoint URL and bearer access token together.
     */
    fun endpoint(
        url: String,
        accessToken: String,
    ) {
        this.credentialEndpoint = url
        this.accessToken = accessToken
    }

    /**
     * Shorthand for [credentialConfigurationId].
     */
    fun credentialConfigurationId(id: String) {
        this.credentialConfigurationId = id
    }

    /**
     * Shorthand for [credentialIdentifier].
     */
    fun credentialIdentifier(id: String) {
        this.credentialIdentifier = id
    }

    /**
     * Configure proofs container with string proof values (e.g., JWT compact strings).
     * The string values are wrapped in [JsonPrimitive].
     */
    fun proofs(
        proofType: String,
        vararg proofValues: String,
    ) {
        proofs =
            CredentialRequestProofs(
                proofType = proofType,
                proofValues = proofValues.map { JsonPrimitive(it) },
            )
    }

    /**
     * Configure proofs container with raw [JsonElement] proof values.
     * Use this for proof types like di_vp where values are JSON objects.
     */
    fun proofs(
        proofType: String,
        proofValues: List<JsonElement>,
    ) {
        proofs = CredentialRequestProofs(proofType = proofType, proofValues = proofValues)
    }

    /**
     * Configure requested credential response encryption.
     */
    fun encryption(builder: RequestedEncryptionBuilder.() -> Unit) {
        encryption = RequestedEncryptionBuilder().apply(builder).build()
    }

    /**
     * Validate and produce the [BuilderState] for use by the holder-public entry-point function.
     */
    fun buildState(): RequestCredentialArgsState {
        val endpoint =
            requireNotNull(credentialEndpoint) {
                "RequestCredentialArgsBuilder: 'endpoint' must be called to set the credential endpoint URL"
            }
        val token =
            requireNotNull(accessToken) {
                "RequestCredentialArgsBuilder: 'endpoint' must be called with a non-null accessToken"
            }
        require(credentialConfigurationId != null || credentialIdentifier != null) {
            "RequestCredentialArgsBuilder: set either 'credentialConfigurationId' or 'credentialIdentifier' (mutually exclusive per OID4VCI spec)"
        }
        require(credentialConfigurationId == null || credentialIdentifier == null) {
            "RequestCredentialArgsBuilder: 'credentialConfigurationId' and 'credentialIdentifier' are mutually exclusive — set only one"
        }
        return RequestCredentialArgsState(
            credentialEndpoint = endpoint,
            accessToken = token,
            credentialConfigurationId = credentialConfigurationId,
            credentialIdentifier = credentialIdentifier,
            proofs = proofs,
            credentialResponseEncryption = encryption,
            nonceEndpoint = nonceEndpoint,
        )
    }
}

/** Validated intermediate state produced by [RequestCredentialArgsBuilder]. */
@JsExportCompat
data class RequestCredentialArgsState(
    val credentialEndpoint: String,
    val accessToken: String,
    val credentialConfigurationId: String?,
    val credentialIdentifier: String?,
    val proofs: CredentialRequestProofs?,
    val credentialResponseEncryption: RequestedCredentialResponseEncryption?,
    val nonceEndpoint: String?,
)

// ============================================================================
// 2. CreateCredentialRequestProofArgsBuilder
// ============================================================================

/**
 * Builder for `CreateCredentialRequestProofArgs` (holder-public).
 *
 * Usage:
 * ```kotlin
 * createProofArgs {
 *     issuerUrl("https://issuer.example.com")
 *     signingKey("key-1", algorithm = JwaAlgorithm.ES256)
 *     nonce("c_nonce_value")
 *     clientId("wallet-app")
 *     batch(count = 3)
 *     keyMode(JwsIdentifierMode.JWK)
 * }
 * ```
 */
@Oid4vciDsl
class CreateCredentialRequestProofArgsBuilder {
    private var issuerUrl: String? = null
    private var signingKeyId: String? = null

    /** JWA signing algorithm. Defaults to `"ES256"`. */
    var signingAlgorithm: String = JwaAlgorithm.ES256.value

    /** Optional `c_nonce` from the issuer. */
    var cNonce: String? = null

    /** Optional `client_id` used as JWT `iss`. */
    var clientId: String? = null

    /** Number of proofs to create. Defaults to 1 (singular proof). */
    var count: Int = 1

    /** How to include the public key in the proof JWT header. Defaults to KID. */
    var keyInclusionMode: JwsIdentifierMode = JwsIdentifierMode.KID

    /**
     * Set the issuer URL (used as JWT `aud`).
     */
    fun issuerUrl(url: String) {
        this.issuerUrl = url
    }

    /**
     * Set the signing key identifier and algorithm together.
     */
    fun signingKey(
        keyId: String,
        algorithm: JwaAlgorithm = JwaAlgorithm.ES256,
    ) {
        this.signingKeyId = keyId
        this.signingAlgorithm = algorithm.value
    }

    /**
     * Set the signing key identifier and algorithm using a raw algorithm string.
     */
    fun signingKey(
        keyId: String,
        algorithm: String,
    ) {
        this.signingKeyId = keyId
        this.signingAlgorithm = algorithm
    }

    /**
     * Set the `c_nonce` value.
     */
    fun nonce(value: String) {
        this.cNonce = value
    }

    /**
     * Set the client ID.
     */
    fun clientId(id: String) {
        this.clientId = id
    }

    /**
     * Configure batch proof creation — sets [count] to the given value.
     */
    fun batch(count: Int) {
        require(count >= 1) { "CreateCredentialRequestProofArgsBuilder: batch count must be >= 1, got $count" }
        this.count = count
    }

    /**
     * Set the JWS key identifier inclusion mode.
     */
    fun keyMode(mode: JwsIdentifierMode) {
        this.keyInclusionMode = mode
    }

    fun buildState(): CreateCredentialRequestProofArgsState {
        val url =
            requireNotNull(issuerUrl) {
                "CreateCredentialRequestProofArgsBuilder: 'issuerUrl' must be set"
            }
        val keyId =
            requireNotNull(signingKeyId) {
                "CreateCredentialRequestProofArgsBuilder: 'signingKey' must be called to set the signing key identifier"
            }
        return CreateCredentialRequestProofArgsState(
            issuerUrl = url,
            cNonce = cNonce,
            signingKeyId = keyId,
            signingAlgorithm = signingAlgorithm,
            clientId = clientId,
            count = count,
            keyInclusionMode = keyInclusionMode,
        )
    }
}

/** Validated intermediate state produced by [CreateCredentialRequestProofArgsBuilder]. */
@JsExportCompat
data class CreateCredentialRequestProofArgsState(
    val issuerUrl: String,
    val cNonce: String?,
    val signingKeyId: String,
    val signingAlgorithm: String,
    val clientId: String?,
    val count: Int,
    val keyInclusionMode: JwsIdentifierMode,
)

// ============================================================================
// 3. RequestCredentialWithFlowArgsBuilder
// ============================================================================

/**
 * Builder for `RequestCredentialWithFlowArgs` (holder-public).
 *
 * Usage:
 * ```kotlin
 * credentialFlowArgs {
 *     sessionId("sess-1")
 *     endpoint("https://issuer/credential", accessToken = "token")
 *     issuerUrl("https://issuer.example.com")
 *     signingKey("key-1", algorithm = JwaAlgorithm.ES256)
 *     credentialConfigurationId("UniversityDegree")
 *     nonceEndpoint("https://issuer/nonce")
 *     deferredEndpoint("https://issuer/deferred")
 *     notificationEndpoint("https://issuer/notification")
 * }
 * ```
 */
@Oid4vciDsl
class RequestCredentialWithFlowArgsBuilder {
    private var sessionId: String? = null
    private var credentialEndpoint: String? = null
    private var accessToken: String? = null
    private var issuerUrl: String? = null
    private var signingKeyId: String? = null

    /** JWA signing algorithm. Defaults to `"ES256"`. */
    var signingAlgorithm: String = JwaAlgorithm.ES256.value

    /** Mutually exclusive with [credentialIdentifier]. */
    var credentialConfigurationId: String? = null

    /** Mutually exclusive with [credentialConfigurationId]. */
    var credentialIdentifier: String? = null

    /** Optional nonce endpoint for auto-nonce fetch and `invalid_nonce` retry. */
    var nonceEndpoint: String? = null

    /** Optional deferred credential endpoint — required for deferred polling. */
    var deferredCredentialEndpoint: String? = null

    /** Optional notification endpoint — when set a notification is sent on success. */
    var notificationEndpoint: String? = null

    private var encryption: RequestedCredentialResponseEncryption? = null

    /**
     * Set the session identifier.
     */
    fun sessionId(id: String) {
        this.sessionId = id
    }

    /**
     * Set the credential endpoint URL and bearer token together.
     */
    fun endpoint(
        url: String,
        accessToken: String,
    ) {
        this.credentialEndpoint = url
        this.accessToken = accessToken
    }

    /**
     * Set the issuer URL used as JWT `aud` in the proof.
     */
    fun issuerUrl(url: String) {
        this.issuerUrl = url
    }

    /**
     * Set the signing key identifier and algorithm together.
     */
    fun signingKey(
        keyId: String,
        algorithm: JwaAlgorithm = JwaAlgorithm.ES256,
    ) {
        this.signingKeyId = keyId
        this.signingAlgorithm = algorithm.value
    }

    /**
     * Set the signing key identifier and algorithm using a raw string.
     */
    fun signingKey(
        keyId: String,
        algorithm: String,
    ) {
        this.signingKeyId = keyId
        this.signingAlgorithm = algorithm
    }

    /**
     * Shorthand for [credentialConfigurationId].
     */
    fun credentialConfigurationId(id: String) {
        this.credentialConfigurationId = id
    }

    /**
     * Shorthand for [credentialIdentifier].
     */
    fun credentialIdentifier(id: String) {
        this.credentialIdentifier = id
    }

    /**
     * Set the nonce endpoint for auto-nonce and invalid_nonce retry.
     */
    fun nonceEndpoint(url: String) {
        this.nonceEndpoint = url
    }

    /**
     * Set the deferred credential endpoint for polling.
     */
    fun deferredEndpoint(url: String) {
        this.deferredCredentialEndpoint = url
    }

    /**
     * Set the notification endpoint.
     */
    fun notificationEndpoint(url: String) {
        this.notificationEndpoint = url
    }

    /**
     * Configure requested credential response encryption.
     */
    fun encryption(builder: RequestedEncryptionBuilder.() -> Unit) {
        encryption = RequestedEncryptionBuilder().apply(builder).build()
    }

    fun buildState(): RequestCredentialWithFlowArgsState {
        val session =
            requireNotNull(sessionId) {
                "RequestCredentialWithFlowArgsBuilder: 'sessionId' must be set"
            }
        val endpoint =
            requireNotNull(credentialEndpoint) {
                "RequestCredentialWithFlowArgsBuilder: 'endpoint' must be called to set the credential endpoint URL"
            }
        val token =
            requireNotNull(accessToken) {
                "RequestCredentialWithFlowArgsBuilder: 'endpoint' must be called with a non-null accessToken"
            }
        val issuer =
            requireNotNull(issuerUrl) {
                "RequestCredentialWithFlowArgsBuilder: 'issuerUrl' must be set"
            }
        val keyId =
            requireNotNull(signingKeyId) {
                "RequestCredentialWithFlowArgsBuilder: 'signingKey' must be called to set the signing key identifier"
            }
        require(credentialConfigurationId != null || credentialIdentifier != null) {
            "RequestCredentialWithFlowArgsBuilder: set either 'credentialConfigurationId' or 'credentialIdentifier' (mutually exclusive per OID4VCI spec)"
        }
        require(credentialConfigurationId == null || credentialIdentifier == null) {
            "RequestCredentialWithFlowArgsBuilder: 'credentialConfigurationId' and 'credentialIdentifier' are mutually exclusive — set only one"
        }
        return RequestCredentialWithFlowArgsState(
            sessionId = session,
            credentialEndpoint = endpoint,
            accessToken = token,
            issuerUrl = issuer,
            signingKeyId = keyId,
            signingAlgorithm = signingAlgorithm,
            credentialConfigurationId = credentialConfigurationId,
            credentialIdentifier = credentialIdentifier,
            nonceEndpoint = nonceEndpoint,
            deferredCredentialEndpoint = deferredCredentialEndpoint,
            notificationEndpoint = notificationEndpoint,
            credentialResponseEncryption = encryption,
        )
    }
}

/** Validated intermediate state produced by [RequestCredentialWithFlowArgsBuilder]. */
@JsExportCompat
data class RequestCredentialWithFlowArgsState(
    val sessionId: String,
    val credentialEndpoint: String,
    val accessToken: String,
    val issuerUrl: String,
    val signingKeyId: String,
    val signingAlgorithm: String,
    val credentialConfigurationId: String?,
    val credentialIdentifier: String?,
    val nonceEndpoint: String?,
    val deferredCredentialEndpoint: String?,
    val notificationEndpoint: String?,
    val credentialResponseEncryption: RequestedCredentialResponseEncryption?,
)

// ============================================================================
// 4. PollDeferredCredentialArgsBuilder
// ============================================================================

/**
 * Builder for `PollDeferredCredentialArgs` (holder-public).
 *
 * Usage:
 * ```kotlin
 * pollDeferredArgs {
 *     endpoint("https://issuer/deferred", accessToken = "token")
 *     transactionId("tx-abc")
 *     sessionId("sess-1")
 *     polling(interval = 10, maxAttempts = 60)
 * }
 * ```
 */
@JsExportCompat
@Oid4vciDsl
class PollDeferredCredentialArgsBuilder {
    private var deferredCredentialEndpoint: String? = null
    private var accessToken: String? = null
    private var transactionId: String? = null
    private var sessionId: String? = null

    /** Polling interval in seconds. `null` falls back to config default. */
    var interval: Int? = null

    /** Maximum number of poll attempts. `null` falls back to config default. */
    var maxAttempts: Int? = null

    private var encryption: RequestedCredentialResponseEncryption? = null

    /**
     * Set the deferred credential endpoint URL and bearer token together.
     */
    fun endpoint(
        url: String,
        accessToken: String,
    ) {
        this.deferredCredentialEndpoint = url
        this.accessToken = accessToken
    }

    /**
     * Set the transaction ID from the initial deferred credential response.
     */
    fun transactionId(id: String) {
        this.transactionId = id
    }

    /**
     * Set the session identifier.
     */
    fun sessionId(id: String) {
        this.sessionId = id
    }

    /**
     * Configure polling parameters.
     *
     * @param interval Polling interval in seconds.
     * @param maxAttempts Maximum number of poll attempts.
     */
    fun polling(
        interval: Int? = null,
        maxAttempts: Int? = null,
    ) {
        if (interval != null) {
            require(interval >= 1) { "PollDeferredCredentialArgsBuilder: polling interval must be >= 1, got $interval" }
            this.interval = interval
        }
        if (maxAttempts != null) {
            require(maxAttempts >= 1) { "PollDeferredCredentialArgsBuilder: maxAttempts must be >= 1, got $maxAttempts" }
            this.maxAttempts = maxAttempts
        }
    }

    /**
     * Configure optional credential response encryption.
     */
    fun encryption(builder: RequestedEncryptionBuilder.() -> Unit) {
        encryption = RequestedEncryptionBuilder().apply(builder).build()
    }

    fun buildState(): PollDeferredCredentialArgsState {
        val endpoint =
            requireNotNull(deferredCredentialEndpoint) {
                "PollDeferredCredentialArgsBuilder: 'endpoint' must be called to set the deferred credential endpoint URL"
            }
        val token =
            requireNotNull(accessToken) {
                "PollDeferredCredentialArgsBuilder: 'endpoint' must be called with a non-null accessToken"
            }
        val txId =
            requireNotNull(transactionId) {
                "PollDeferredCredentialArgsBuilder: 'transactionId' must be set"
            }
        val session =
            requireNotNull(sessionId) {
                "PollDeferredCredentialArgsBuilder: 'sessionId' must be set"
            }
        return PollDeferredCredentialArgsState(
            deferredCredentialEndpoint = endpoint,
            accessToken = token,
            transactionId = txId,
            sessionId = session,
            interval = interval,
            maxAttempts = maxAttempts,
            credentialResponseEncryption = encryption,
        )
    }
}

/** Validated intermediate state produced by [PollDeferredCredentialArgsBuilder]. */
@JsExportCompat
data class PollDeferredCredentialArgsState(
    val deferredCredentialEndpoint: String,
    val accessToken: String,
    val transactionId: String,
    val sessionId: String,
    val interval: Int?,
    val maxAttempts: Int?,
    val credentialResponseEncryption: RequestedCredentialResponseEncryption?,
)

// ============================================================================
// 5. CreateCredentialOfferArgsBuilder
// ============================================================================

/**
 * Builder for `CreateCredentialOfferArgs` (issuer-public).
 *
 * Usage:
 * ```kotlin
 * createOfferArgs {
 *     issuerId("https://issuer.example.com")
 *     credentials("UniversityDegree", "MembershipCard")
 *     preAuthorizedCodeGrant(txCodeRequired = true)
 *     attributes {
 *         put("name", JsonPrimitive("John"))
 *     }
 * }
 * ```
 */
@Oid4vciDsl
class CreateCredentialOfferArgsBuilder {
    private var issuerId: String? = null
    private val credentialConfigurationIds = mutableListOf<String>()

    /** Whether to include a pre-authorized code grant in the offer. */
    var preAuthorizedCodeGrant: Boolean = false

    /** Whether to include an authorization code grant in the offer. */
    var authorizationCodeGrant: Boolean = false

    /** Whether a transaction code (PIN) is required for pre-authorized code grant. */
    var txCodeRequired: Boolean = false

    /** Optional tx_code length to advertise in the offer and generate (null = issuer default). */
    var txCodeLength: Int? = null

    /** Optional tx_code input mode ("numeric" | "text"; null = numeric). */
    var txCodeInputMode: String? = null

    /** Offer TTL in seconds. Defaults to 600 (10 minutes). */
    var offerTtlSeconds: Long = DEFAULT_OFFER_TTL_SECONDS

    private val preSeededAttributes = mutableMapOf<String, JsonElement>()

    /**
     * Set the issuer identifier URL.
     */
    fun issuerId(id: String) {
        this.issuerId = id
    }

    /**
     * Add one or more credential configuration IDs to include in the offer.
     */
    fun credentials(vararg ids: String) {
        credentialConfigurationIds.addAll(ids.toList())
    }

    /**
     * Add a single credential configuration ID to include in the offer.
     */
    fun credential(id: String) {
        credentialConfigurationIds.add(id)
    }

    /**
     * Enable pre-authorized code grant and optionally require a transaction code.
     */
    fun preAuthorizedCodeGrant(
        txCodeRequired: Boolean = false,
        txCodeLength: Int? = null,
        txCodeInputMode: String? = null,
    ) {
        this.preAuthorizedCodeGrant = true
        this.txCodeRequired = txCodeRequired
        this.txCodeLength = txCodeLength
        this.txCodeInputMode = txCodeInputMode
    }

    /**
     * Enable authorization code grant.
     */
    fun authorizationCodeGrant() {
        this.authorizationCodeGrant = true
    }

    /**
     * Configure pre-seeded attributes to embed in the offer session.
     *
     * @param builder Map builder — use [MutableMap.put] to add key/value pairs.
     */
    @JsExportIgnoreCompat
    fun attributes(builder: MutableMap<String, JsonElement>.() -> Unit) {
        preSeededAttributes.builder()
    }

    fun buildState(): CreateCredentialOfferArgsState {
        val issuer =
            requireNotNull(issuerId) {
                "CreateCredentialOfferArgsBuilder: 'issuerId' must be set"
            }
        require(credentialConfigurationIds.isNotEmpty()) {
            "CreateCredentialOfferArgsBuilder: at least one credential must be added via 'credentials(...)' or 'credential(...)'"
        }
        require(preAuthorizedCodeGrant || authorizationCodeGrant) {
            "CreateCredentialOfferArgsBuilder: at least one grant type must be enabled — call 'preAuthorizedCodeGrant()' or 'authorizationCodeGrant()'"
        }
        return CreateCredentialOfferArgsState(
            issuerId = issuer,
            credentialConfigurationIds = credentialConfigurationIds.toList(),
            preAuthorizedCodeGrant = preAuthorizedCodeGrant,
            authorizationCodeGrant = authorizationCodeGrant,
            txCodeRequired = txCodeRequired,
            txCodeLength = txCodeLength,
            txCodeInputMode = txCodeInputMode,
            preSeededAttributes = preSeededAttributes.toMap().takeIf { it.isNotEmpty() },
            offerTtlSeconds = offerTtlSeconds,
        )
    }

    companion object {
        private const val DEFAULT_OFFER_TTL_SECONDS = 600L
    }
}

/** Validated intermediate state produced by [CreateCredentialOfferArgsBuilder]. */
@JsExportCompat
data class CreateCredentialOfferArgsState(
    val issuerId: String,
    val credentialConfigurationIds: List<String>,
    val preAuthorizedCodeGrant: Boolean,
    val authorizationCodeGrant: Boolean,
    val txCodeRequired: Boolean,
    val txCodeLength: Int? = null,
    val txCodeInputMode: String? = null,
    val preSeededAttributes: Map<String, JsonElement>?,
    val offerTtlSeconds: Long,
)

// ============================================================================
// 6. SendNotificationWithRetryArgsBuilder
// ============================================================================

/**
 * Builder for `SendNotificationWithRetryArgs` (holder-public).
 *
 * Usage:
 * ```kotlin
 * notifyWithRetryArgs {
 *     endpoint("https://issuer/notification", accessToken = "token")
 *     notificationId("notif-id")
 *     event(CredentialNotificationEvent.CREDENTIAL_ACCEPTED)
 *     retryPolicy(maxRetries = 5, initialBackoffMs = 500)
 * }
 * ```
 */
@JsExportCompat
@Oid4vciDsl
class SendNotificationWithRetryArgsBuilder {
    private var notificationEndpoint: String? = null
    private var accessToken: String? = null
    private var notificationId: String? = null
    private var event: CredentialNotificationEvent? = null

    /** Optional human-readable description of the event. */
    var eventDescription: String? = null

    /** Maximum number of retry attempts after the initial try. Defaults to 3. */
    var maxRetries: Int = DEFAULT_MAX_RETRIES

    /** Initial backoff delay in milliseconds before the first retry. Defaults to 1000. */
    var initialBackoffMs: Long = DEFAULT_INITIAL_BACKOFF_MS

    /**
     * Set the notification endpoint URL and bearer token together.
     */
    fun endpoint(
        url: String,
        accessToken: String,
    ) {
        this.notificationEndpoint = url
        this.accessToken = accessToken
    }

    /**
     * Set the notification ID from the credential response.
     */
    fun notificationId(id: String) {
        this.notificationId = id
    }

    /**
     * Set the credential notification event type.
     */
    fun event(event: CredentialNotificationEvent) {
        this.event = event
    }

    /**
     * Configure retry parameters.
     *
     * @param maxRetries Maximum retries after the initial attempt.
     * @param initialBackoffMs Initial backoff in milliseconds.
     */
    fun retryPolicy(
        maxRetries: Int = 3,
        initialBackoffMs: Long = 1000L,
    ) {
        require(maxRetries >= 0) { "SendNotificationWithRetryArgsBuilder: maxRetries must be >= 0, got $maxRetries" }
        require(initialBackoffMs >= 0) { "SendNotificationWithRetryArgsBuilder: initialBackoffMs must be >= 0, got $initialBackoffMs" }
        this.maxRetries = maxRetries
        this.initialBackoffMs = initialBackoffMs
    }

    fun buildState(): SendNotificationWithRetryArgsState {
        val endpoint =
            requireNotNull(notificationEndpoint) {
                "SendNotificationWithRetryArgsBuilder: 'endpoint' must be called to set the notification endpoint URL"
            }
        val token =
            requireNotNull(accessToken) {
                "SendNotificationWithRetryArgsBuilder: 'endpoint' must be called with a non-null accessToken"
            }
        val notifId =
            requireNotNull(notificationId) {
                "SendNotificationWithRetryArgsBuilder: 'notificationId' must be set"
            }
        val notifEvent =
            requireNotNull(event) {
                "SendNotificationWithRetryArgsBuilder: 'event' must be set — provide a CredentialNotificationEvent value"
            }
        return SendNotificationWithRetryArgsState(
            notificationEndpoint = endpoint,
            accessToken = token,
            notificationId = notifId,
            event = notifEvent,
            eventDescription = eventDescription,
            maxRetries = maxRetries,
            initialBackoffMs = initialBackoffMs,
        )
    }

    companion object {
        private const val DEFAULT_MAX_RETRIES = 3
        private const val DEFAULT_INITIAL_BACKOFF_MS = 1000L
    }
}

/** Validated intermediate state produced by [SendNotificationWithRetryArgsBuilder]. */
@JsExportCompat
data class SendNotificationWithRetryArgsState(
    val notificationEndpoint: String,
    val accessToken: String,
    val notificationId: String,
    val event: CredentialNotificationEvent,
    val eventDescription: String?,
    val maxRetries: Int,
    val initialBackoffMs: Long,
)
