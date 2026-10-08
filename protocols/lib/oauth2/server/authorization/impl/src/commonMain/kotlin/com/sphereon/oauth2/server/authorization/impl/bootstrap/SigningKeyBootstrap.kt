/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.bootstrap

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.AppConsoleLogServiceImpl
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.session.AppCommandInvoker
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.di.context.PrincipalInput
import com.sphereon.di.context.TenantInput
import com.sphereon.oauth2.server.authorization.command.RotateSigningKeyArgs
import com.sphereon.oauth2.server.authorization.command.RotateSigningKeyCommand
import com.sphereon.oauth2.server.authorization.storage.RotationResult
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore

/**
 * First-boot signing-key provisioner for any service that embeds the
 * OAuth2 AS. Mirrors the "ensure the store has an ACTIVE key before any
 * mint can race" pattern; idempotent so subsequent boots no-op.
 *
 * Without this, [com.sphereon.oauth2.server.authorization.impl.config.DefaultOAuth2ConfigModule]
 * produces a null `serverIdentifier`, the AS sign path silently falls
 * back to opaque tokens at mint time, and any downstream JWT validator
 * (e.g. a blob-rest endpoint) rejects the bearer because it isn't a
 * parseable JWS. Service bootstraps MUST run this on the boot thread
 * before the request path opens.
 *
 * Caller-supplied [tenantInput] / [principalInput] identify the session
 * the rotate command runs under. Service implementations pass whatever
 * inputs match the system-account convention they already use — the
 * Temporal-side `TemporalTenantInput` / `TemporalPrincipalInput`, a pure
 * REST service's own [TenantInput] / [PrincipalInput] impls, etc. The
 * substrate itself stays decoupled from any specific runtime's
 * system-account model.
 *
 * Persistence: whichever [SigningKeyStore] the classpath wires in
 * (InMemoryStore by default, PostgresSigningKeyStore when the EDK
 * store-postgres module is present). In-memory mode regenerates a key
 * on every boot — workable for dev, accepts that tokens in flight at
 * restart fail until they retry.
 *
 * @param appCommandInvoker invoker to dispatch the session-scoped
 *   [RotateSigningKeyCommand] from app-scope bootstrap code.
 * @param signingKeyStore AS signing-key metadata store. Actual key
 *   bytes live in the KMS provider this store points at.
 * @param tenantInput tenant the command runs under (typically the
 *   system / platform tenant).
 * @param principalInput principal the command runs under (typically
 *   the system account).
 * @param tenantId AS signing-key tenant id. Defaults to `"default"`,
 *   matching [DefaultOAuth2ConfigModule]'s `DEFAULT_SIGNING_KEY_TENANT`.
 * @param algorithm JWS algorithm. Defaults to `ECDSA_SHA256` (ES256).
 * @param logService log sink (defaults to console).
 * @return Ok(kid of the active key, existing or newly minted) on
 *   success; Err(IdkError) when the rotate command is unregistered or
 *   the KMS rejects key generation.
 */
suspend fun ensureActiveSigningKey(
    appCommandInvoker: AppCommandInvoker,
    signingKeyStore: SigningKeyStore,
    tenantInput: TenantInput,
    principalInput: PrincipalInput,
    tenantId: String = "default",
    algorithm: SignatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
    logService: LogService = AppConsoleLogServiceImpl(),
): IdkResult<String, IdkError> {
    val activeResult = signingKeyStore.getActive(tenantId)
    if (activeResult.isErr) {
        return Err(
            IdkError.fromString(
                code = "OAUTH2.SIGNING_KEY_LOOKUP_FAILED",
                message = "Failed to query SigningKeyStore for active key: ${activeResult.error}",
            ),
        )
    }
    val existing = activeResult.value
    if (existing != null) {
        val kid = existing.keyInfo.kid ?: "<unknown>"
        logService.info(
            "OAuth2 AS active signing key already provisioned; skipping bootstrap",
            metadata = mapOf("tenantId" to tenantId, "kid" to kid),
        )
        return Ok(kid)
    }

    logService.info(
        "OAuth2 AS has no ACTIVE signing key; bootstrapping a fresh one",
        metadata = mapOf("tenantId" to tenantId, "algorithm" to (algorithm::class.simpleName ?: "?")),
    )
    val rotate =
        appCommandInvoker.resolve(RotateSigningKeyCommand.COMMAND_ID) as? RotateSigningKeyCommand
            ?: return Err(
                IdkError.fromString(
                    code = "OAUTH2.ROTATE_SIGNING_KEY_NOT_REGISTERED",
                    message = "RotateSigningKeyCommand not registered; add lib-oauth2-server-authorization-impl to the service",
                ),
            )

    val result: IdkResult<RotationResult, IdkError> =
        appCommandInvoker.execute(
            tenantInput = tenantInput,
            principalInput = principalInput,
            command = rotate,
            input =
                RotateSigningKeyArgs(
                    tenantId = tenantId,
                    algorithm = algorithm,
                ),
            correlationId = null,
        )

    if (result.isErr) {
        return Err(result.error)
    }
    val rotation = result.value
    val newKid = rotation.newActive.keyInfo.kid ?: "<unknown>"
    logService.info(
        "OAuth2 AS signing key bootstrapped",
        metadata =
            mapOf(
                "tenantId" to tenantId,
                "newKid" to newKid,
                "alias" to (rotation.newActive.keyInfo.alias ?: "<unknown>"),
            ),
    )
    return Ok(newKid)
}
