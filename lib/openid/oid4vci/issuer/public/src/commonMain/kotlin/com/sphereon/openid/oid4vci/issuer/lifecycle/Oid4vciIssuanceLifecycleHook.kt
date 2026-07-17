/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.lifecycle

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.OptionalBinding
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration

@JsExportCompat
@Serializable
enum class Oid4vciIssuancePhase {
    START,
    AUTHORIZATION,
    PRE_AUTHORIZED,
    TOKEN,
    CREDENTIAL_REQUEST,
    DEFERRED,
    PRE_ISSUE,
    POST_ISSUANCE,
    NOTIFICATION_RECEIPT,
}

@JsExportCompat
@Serializable
data class Oid4vciOfferLifecycleArgs(
    val issuerId: String,
    val credentialConfigurationIds: List<String>,
    val preAuthorizedCodeGrant: Boolean,
    val authorizationCodeGrant: Boolean,
    val txCodeRequired: Boolean,
    @JsExportIgnoreCompat
    val initialFields: Map<String, JsonElement> = emptyMap(),
    val boundUsageToken: String? = null,
)

@JsExportCompat
@Serializable
data class Oid4vciOfferLifecycleResult(
    /**
     * Opaque correlation handle owned by the lifecycle implementation. EDK uses this as the
     * connector-pipeline session correlation id; IDK treats it only as an extension handle.
     */
    val correlationId: String? = null,
)

@JsExportCompat
@Serializable
data class Oid4vciPhaseLifecycleArgs(
    val correlationId: String?,
    val protocolSessionId: String,
    val phase: Oid4vciIssuancePhase,
    val credentialConfigurationId: String? = null,
    @JsExportIgnoreCompat
    val fields: Map<String, JsonElement> = emptyMap(),
)

@JsExportCompat
@Serializable
data class Oid4vciPhaseLifecycleResult(
    @JsExportIgnoreCompat
    val attributes: Map<String, JsonElement> = emptyMap(),
    @JsExportIgnoreCompat
    val pendingAsyncCallbackContributors: Set<String> = emptySet(),
    val syncWaitWindow: Duration = Duration.ZERO,
)

@JsExportCompat
@Serializable
data class Oid4vciCompletenessLifecycleArgs(
    val correlationId: String,
)

@JsExportCompat
@Serializable
data class Oid4vciCompletenessLifecycleResult(
    val shouldDefer: Boolean = false,
    val awaitingApproval: Boolean = false,
    @JsExportIgnoreCompat
    val missingRequiredClaims: List<String> = emptyList(),
)

/**
 * IDK-owned OID4VCI lifecycle extension seam.
 *
 * The simple issuer calls this seam at protocol lifecycle points. EDK binds a connector-backed
 * implementation. VDX may make that implementation durable and registrable, but those concerns do
 * not enter IDK.
 */
@JsExportCompat
interface Oid4vciIssuanceLifecycleHook {
    @JsExportIgnoreCompat
    suspend fun initializeOffer(args: Oid4vciOfferLifecycleArgs): IdkResult<Oid4vciOfferLifecycleResult, IdkError> = Ok(Oid4vciOfferLifecycleResult())

    @JsExportIgnoreCompat
    suspend fun recordPhase(args: Oid4vciPhaseLifecycleArgs): IdkResult<Oid4vciPhaseLifecycleResult, IdkError> = Ok(Oid4vciPhaseLifecycleResult())

    @JsExportIgnoreCompat
    suspend fun evaluateCompleteness(args: Oid4vciCompletenessLifecycleArgs): IdkResult<Oid4vciCompletenessLifecycleResult, IdkError> = Ok(Oid4vciCompletenessLifecycleResult())
}

@ContributesTo(SessionScope::class)
interface Oid4vciIssuanceLifecycleHookOptionalProvider {
    @OptionalBinding
    val optionalOid4vciIssuanceLifecycleHook: Oid4vciIssuanceLifecycleHook? get() = null
}
