/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

@file:OptIn(ExperimentalTime::class)

package com.sphereon.oauth2.server.authorization.provider

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Bridge between a completed federated upstream login and the local
 * identity + session persistence layer. Optional: when a
 * [FederatedUserAuthenticationProvider] has no linker wired, federation
 * still works but the session is not recorded in the local
 * `AuthenticationSessionStore` (so OIDC Back-Channel Logout for that
 * session falls back to "notify every registered RP" rather than the
 * precise RP list).
 *
 * The SPI lives in IDK so the provider can reference it without a hard
 * dependency on EDK's party / identity-upsert modules. EDK ships a
 * default implementation that delegates to `IdvIdentityUpserter` +
 * `AuthenticationSessionStore`.
 */
interface FederatedIdentityLinker {
    /**
     * Link a completed upstream authentication to a local
     * [LinkedFederatedSession]. Implementations should:
     *  1. Upsert a local `Identity` keyed by (`upstreamIssuer`, `upstreamSub`)
     *     so re-logins map to the same local identity.
     *  2. Write an `AuthenticationSessionRecord` keyed by [sessionId] with
     *     the local identity id, [authenticatedAt], [expiresAt], [acr], [amr].
     *
     * Returns the **local identity id** as a string so the caller can use
     * it as the `userId` propagated through `AuthenticatedUser`.
     */
    suspend fun linkFederatedSession(request: LinkFederatedSessionRequest,): IdkResult<LinkedFederatedSession, IdkError>
}

/** Inputs to [FederatedIdentityLinker.linkFederatedSession]. */
data class LinkFederatedSessionRequest(
    val tenantId: String,
    val sessionId: String,
    val upstreamIssuer: String,
    val upstreamSub: String,
    /**
     * Name of the claim in [claims] that carries the stable upstream
     * identifier for this identity (typically `"sub"`; some providers use
     * `"preferred_username"` or `"email"`).
     */
    val identifierClaimName: String = "sub",
    val claims: Map<String, Any>,
    val acr: String?,
    val amr: List<String>?,
    val returnUrl: String?,
    val remoteIp: String?,
    val authenticatedAt: Instant,
    val expiresAt: Instant,
    /**
     * Upstream OIDC `sid` claim (OIDC Core §2 session identifier). Supplied when
     * the upstream id_token carries `sid`. Stored on the written session record
     * so the inbound Back-Channel Logout receiver can terminate the precise
     * local session bound to the upstream one.
     */
    val upstreamSid: String? = null,
    /**
     * Opaque application / login-surface id captured at federation initiate time
     * (`PendingFederation.applicationId`, originally `AuthenticationContext.applicationId`).
     * When present, implementations scope the identity link to that application:
     * the local identity is bound to it for login and the written session record
     * carries it. Null = application-agnostic federation login.
     */
    val applicationId: String? = null,
)

/** Output of a successful link. */
data class LinkedFederatedSession(
    /** String-ified local identity id (Uuid in EDK's default impl). */
    val localIdentityId: String,
)
