/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.credential.issuance.pipeline.callback

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Minted capability token plus the absolute time at which it stops being acceptable.
 *
 * `token` is the compact-serialised JWS string handed to the external attribute source;
 * `expiresAt` is the absolute deadline derived from `clock.now() + ttl` at mint time.
 */
@Serializable
data class CallbackToken(
    val token: String,
    val expiresAt: Instant,
)

/**
 * Mints and validates the short-lived capability tokens carried in a [CallbackCoordinate].
 *
 * The token is a JWS over [CallbackTokenClaims] signed by the issuer's signing key.
 * `mint` produces a token scoped to a `(correlationId, sourceId)` pair with a TTL.
 * `validate` verifies the JWS signature, parses the payload back into claims, and
 * rejects expired tokens. The caller (the callback endpoint) is responsible for the
 * higher-level cross-checks: the decoded `correlationId` and `sourceId` MUST match the
 * pipeline session and source the inbound contribution is targeting.
 */
interface CallbackTokenService {
    /** Mint a fresh capability token scoped to `(correlationId, sourceId)` with the given TTL. */
    suspend fun mint(
        correlationId: String,
        sourceId: String,
        ttl: Duration,
    ): IdkResult<CallbackToken, IdkError>

    /**
     * Verify a token and return its decoded claims. Returns `Err` for tampered, malformed,
     * or expired tokens; the caller does not need to inspect `exp` itself.
     */
    suspend fun validate(token: String): IdkResult<CallbackTokenClaims, IdkError>
}
