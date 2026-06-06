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

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Where an asynchronous attribute source posts its contribution back to.
 *
 * `url` is the issuer endpoint the external system calls (the path includes the issuer
 * base, the session correlation id, and the token segment). `token` is the opaque
 * capability artefact carried in the URL: when the external system later sends a
 * `POST {url}` the issuer parses and validates `token` to bind the contribution to the
 * pipeline session and the originating source. `expiresAt` is the absolute deadline at
 * which the token stops being acceptable, so external systems can decide whether to
 * re-request a fresh coordinate before posting.
 */
@Serializable
data class CallbackCoordinate(
    val url: String,
    val token: String,
    val expiresAt: Instant,
)

/**
 * Decoded payload of a callback capability token.
 *
 * `correlationId` is the pipeline session this token is scoped to; the callback endpoint
 * compares it against the path correlationId and rejects on mismatch. `sourceId` is the
 * `AttributeSource` id within the pipeline this token is scoped to, so a token minted
 * for one external source cannot be replayed to contribute on behalf of another. `exp`
 * carries the standard JWS `exp` claim semantics (epoch-seconds expiry); validators
 * compare it against the current clock time.
 */
@Serializable
data class CallbackTokenClaims(
    val correlationId: String,
    val sourceId: String,
    val exp: Long,
)
