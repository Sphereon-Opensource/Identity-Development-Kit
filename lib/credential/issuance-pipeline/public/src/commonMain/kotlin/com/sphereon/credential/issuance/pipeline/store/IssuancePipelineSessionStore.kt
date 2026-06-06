/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.credential.issuance.pipeline.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.credential.issuance.pipeline.IssuancePipelineSession

/**
 * Persistence for [IssuancePipelineSession]. Mirrors the issuer-core stores' `IdkResult`-returning
 * `create / get / update / delete` shape.
 *
 * The EDK default is KV-backed; VDX overrides it with a Postgres-backed, encrypted-blob-at-rest
 * implementation. Implementations are responsible for encrypting the session payload per
 * [IssuancePipelineSession.encryptionMode] — the store is the single at-rest boundary.
 */
@JsExportCompat
interface IssuancePipelineSessionStore {
    /** Persist a new session. */
    suspend fun create(session: IssuancePipelineSession): IdkResult<IssuancePipelineSession, IdkError>

    /** Load a session by its primary [IssuancePipelineSession.sessionId]. */
    suspend fun get(sessionId: String): IdkResult<IssuancePipelineSession?, IdkError>

    /**
     * Load a session by the HMAC blind index of its `correlationId` — the lookup path used by
     * every ingress that knows only the `correlationId` (callbacks, inbound pushes). The caller
     * computes the HMAC; the store never sees the raw `correlationId`.
     */
    suspend fun getByCorrelationIdHmac(correlationIdHmac: ByteArray): IdkResult<IssuancePipelineSession?, IdkError>

    /** Persist changes to an existing session. */
    suspend fun update(session: IssuancePipelineSession): IdkResult<IssuancePipelineSession, IdkError>

    /** Remove a session. Returns true if a session was removed. */
    suspend fun delete(sessionId: String): IdkResult<Boolean, IdkError>
}
