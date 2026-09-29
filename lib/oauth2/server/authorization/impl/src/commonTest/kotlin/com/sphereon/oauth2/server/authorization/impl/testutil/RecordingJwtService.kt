/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.testutil

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwsJsonFlattened
import com.sphereon.crypto.jose.jws.JwsJsonGeneral
import com.sphereon.crypto.jose.jws.JwsJsonGeneralWithIdentifiers
import com.sphereon.crypto.jose.jws.JwsValidationResult
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.PreparedJwsObject
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Records verification requests and accepts every signature, so tests can assert which anchored
 * keys the token-exchange steps selected. With [mintedKid] it also produces compact JWTs.
 */
internal class RecordingJwtService(
    private val mintedKid: String? = null,
) : JwtService {
    val verifyArgs = mutableListOf<VerifyJwsArgs>()

    private val notImplemented =
        IdkError(
            code = "not_implemented",
            message = IdkError.Message(i18nKey = "", defaultMessage = "Not implemented"),
        )

    override suspend fun prepareJws(args: CreateJwsJsonArgs): IdkResult<PreparedJwsObject, IdkError> = Err(notImplemented)

    override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> {
        val kid = mintedKid ?: return Err(notImplemented)
        val payload = args.payload as? String ?: return Err(notImplemented)
        val header =
            buildJsonObject {
                put("alg", "ES256")
                put("typ", "at+jwt")
                put("kid", kid)
            }.toString()
        val compact =
            listOf(header, payload, "test-signature")
                .joinToString(".") { it.encodeToByteArray().encodeToBase64Url() }
        return Ok(JwtCompactResult(jwt = compact))
    }

    override suspend fun createJwsJsonFlattened(args: CreateJwsJsonArgs): IdkResult<JwsJsonFlattened, IdkError> = Err(notImplemented)

    override suspend fun createJwsJsonGeneral(args: CreateJwsJsonArgs): IdkResult<JwsJsonGeneral, IdkError> = Err(notImplemented)

    override suspend fun verifyJws(args: VerifyJwsArgs): IdkResult<JwsValidationResult, IdkError> {
        verifyArgs += args
        return Ok(
            JwsValidationResult(
                jws = JwsJsonGeneralWithIdentifiers(payload = "", signatures = emptyList()),
                isValid = true,
                parsedPayload = JsonObject(emptyMap()),
            ),
        )
    }

    override fun assembleJwsGeneral(
        prepared: PreparedJwsObject,
        signatureBytes: ByteArray,
    ): JwsJsonGeneral = throw NotImplementedError()

    override fun assembleJwsFlattened(
        prepared: PreparedJwsObject,
        signatureBytes: ByteArray,
    ): JwsJsonFlattened = throw NotImplementedError()

    override fun assembleJwsCompact(
        prepared: PreparedJwsObject,
        signatureBytes: ByteArray,
    ): JwtCompactResult = throw NotImplementedError()

    override val commands: JwtService.Commands
        get() = throw NotImplementedError()
}
