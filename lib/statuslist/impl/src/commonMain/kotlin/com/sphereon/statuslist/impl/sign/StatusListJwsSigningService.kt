/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.statuslist.impl.sign

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.di.session.SessionScope
import com.sphereon.statuslist.spi.SignStatusListTokenArgs
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonObject

data class StatusListJwsSigningRequest(
    val statusListArgs: SignStatusListTokenArgs,
    val keyName: String,
    val payload: JsonObject,
    val mode: JwsIdentifierMode,
    val opts: CreateJwsOpts,
)

/** Execution seam between status-list envelope construction and key custody. */
interface StatusListJwsSigningService {
    suspend fun createCompactJws(request: StatusListJwsSigningRequest): IdkResult<JwtCompactResult, IdkError>

    suspend fun publicJwk(
        keyName: String,
        keyInstanceId: String?,
    ): Jwk?
}

/** Default IDK implementation for a key held by the current process. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<StatusListJwsSigningService>())
class LocalStatusListJwsSigningService(
    private val jwtService: JwtService,
    private val kms: KeyManagerService,
) : StatusListJwsSigningService {
    override suspend fun createCompactJws(request: StatusListJwsSigningRequest): IdkResult<JwtCompactResult, IdkError> =
        jwtService.createJwsCompact(
            CreateJwsArgs(
                issuer = ManagedOptsAlias(identifier = request.keyName),
                payload = request.payload,
                mode = request.mode,
                opts = request.opts,
            ),
        )

    override suspend fun publicJwk(
        keyName: String,
        keyInstanceId: String?,
    ): Jwk? =
        kms
            .getKeyResult(KeyInfo<Nothing>(alias = keyName))
            .getOrNull()
            ?.key
            ?.key as? Jwk
}
