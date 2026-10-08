/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.command.discovery

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.jose.jws.command.CreateJwsCompactCommand
import com.sphereon.crypto.jose.jws.createJwsArgs
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.command.BuildSignedAuthorizationServerMetadataArgs
import com.sphereon.oauth2.server.authorization.command.BuildSignedAuthorizationServerMetadataCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock

/**
 * Default implementation of [BuildSignedAuthorizationServerMetadataCommand]. Wraps the
 * unsigned metadata document in a JWS Compact Serialisation with the AS's active
 * signing key, with the registered RFC 8414 §2 claims (`sub`, `iat`) overlaid on top
 * of every metadata parameter as a top-level claim.
 *
 * Mirrors the OID4VCI shape (`BuildSignedIssuerMetadataCommandImpl`) so the two signed
 * metadata flows have one canonical pattern. The `typ` header is `oauth-as-metadata+jwt`
 * to keep the AS variant distinguishable from the OID4VCI issuer variant.
 *
 * RPs verify the JWS against the AS's JWKS (the `kid` header references the active
 * signing key, which JWKS already publishes per P0-K4); a successful verify proves the
 * metadata document was emitted by the AS and not tampered with in transit.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<BuildSignedAuthorizationServerMetadataCommand>())
class BuildSignedAuthorizationServerMetadataCommandImpl(
    execution: SessionExecution,
    private val createJwsCompactCommand: CreateJwsCompactCommand,
) : TypedServiceCommandAdapter<BuildSignedAuthorizationServerMetadataArgs, JwtCompactResult, IdkError>(
        commandId = BuildSignedAuthorizationServerMetadataCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<BuildSignedAuthorizationServerMetadataArgs>(),
        outputTypeToken = typeToken<JwtCompactResult>(),
    ),
    BuildSignedAuthorizationServerMetadataCommand {
    override val commandId: String get() = BuildSignedAuthorizationServerMetadataCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is BuildSignedAuthorizationServerMetadataArgs

    override suspend fun doExecute(
        args: BuildSignedAuthorizationServerMetadataArgs,
        applyDuring: (BuildSignedAuthorizationServerMetadataArgs) -> BuildSignedAuthorizationServerMetadataArgs,
    ): IdkResult<JwtCompactResult, IdkError> {
        val applied = applyDuring(args)

        // RFC 8414 §2 mandates: every metadata parameter from the unsigned document
        // appears as a top-level JWT claim; in addition the JWS payload SHOULD carry
        // the registered claims `iss` (= issuer), `sub` (= issuer), and `iat`. We elide
        // the `signed_metadata` field itself before signing so the signed copy never
        // contains itself recursively.
        val unsignedJson =
            METADATA_JSON
                .encodeToJsonElement(
                    AuthorizationServerMetadata.serializer(),
                    applied.metadata.copy(signedMetadata = null),
                ).jsonObject

        val payload =
            buildJsonObject {
                unsignedJson.forEach { (key, value) -> put(key, value) }
                // RFC 8414 §2 — `iss` is recommended; we also set `sub` to the same value
                // for symmetry with the OID4VCI signed-metadata convention. Both equal the
                // canonical issuer identifier from the metadata.
                put("iss", JsonPrimitive(applied.metadata.issuer))
                put("sub", JsonPrimitive(applied.metadata.issuer))
                put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
            }

        val jwsArgs =
            createJwsArgs {
                issuer(applied.signingKey)
                payload(payload)
                mode(applied.identifierMode)
                options {
                    protectedHeader {
                        typ(BuildSignedAuthorizationServerMetadataCommand.JWT_TYP)
                    }
                    // The AS already populates `iss` from the metadata directly above; the
                    // JWS layer should not overwrite it.
                    noIssPayloadUpdate()
                }
            }

        return createJwsCompactCommand.execute(jwsArgs)
    }

    companion object {
        // Serialiser tuned for compact JSON (no indentation, drop nulls so absent
        // optional metadata members don't bloat the JWT payload).
        private val METADATA_JSON =
            Json {
                encodeDefaults = false
                ignoreUnknownKeys = true
            }
    }
}
