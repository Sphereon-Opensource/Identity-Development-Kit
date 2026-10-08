/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsCompactCommand
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.signing.AsServerSigningIdentifierResolver
import com.sphereon.openid.oid4vci.issuer.command.MintDeferralScopedTokenArgs
import com.sphereon.openid.oid4vci.issuer.command.MintDeferralScopedTokenCommand
import com.sphereon.openid.oid4vci.issuer.command.MintDeferralScopedTokenResult
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/**
 * Implementation of [MintDeferralScopedTokenCommand]: signs an RFC 9068 `at+jwt` carrying
 * `scope = "deferred_credential"` plus the correlation / transaction ids that bind it to a
 * specific deferred issuance entry.
 *
 * Signing reuses the AS [AsServerSigningIdentifierResolver] so the deferral-scoped token is signed
 * by the same key as the wallet's original access token. That keeps the token verifiable by the
 * same AS-side introspection logic with no extra key plumbing. The resolver is injected optionally
 * (the issuer may be deployed against an external AS that owns signing): when it is absent the
 * command returns a clear error instead of failing DI graph construction.
 *
 * The audience claim is populated from [MintDeferralScopedTokenArgs.audience] when supplied
 * by the caller, otherwise the [Oid4vciIssuerConfigProvider.issuerIdentifier] is used as a
 * fallback so the token always carries an `aud` claim.
 *
 * JWS signing goes through [CreateJwsCompactCommand] (the bound IDK Command) rather than the
 * narrower [com.sphereon.crypto.jose.jws.command.CreateJwsCompactCommandService] surface so
 * the consumer matches the IDK pattern used by `BuildSignedIssuerMetadataCommandImpl` and
 * `BuildSignedAuthorizationServerMetadataCommandImpl`. The service surface is exposed only
 * via [com.sphereon.crypto.jose.jws.JwtService] and is not separately bound in the issuer-rest
 * Metro graph, so binding to the Command keeps composition robust across consumer graphs.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<MintDeferralScopedTokenCommand>())
@ContributesBinding(SessionScope::class, binding = binding<MintDeferralScopedTokenCommand?>())
class MintDeferralScopedTokenCommandImpl(
    execution: SessionExecution,
    private val createJwsCompactCommand: CreateJwsCompactCommand,
    private val signingIdentifierResolverProvider: Provider<AsServerSigningIdentifierResolver>? = null,
    private val issuerConfigProvider: Oid4vciIssuerConfigProvider,
    private val clock: Clock,
) : TypedServiceCommandAdapter<MintDeferralScopedTokenArgs, MintDeferralScopedTokenResult, IdkError>(
        commandId = MintDeferralScopedTokenCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<MintDeferralScopedTokenArgs>(),
        outputTypeToken = typeToken<MintDeferralScopedTokenResult>(),
    ),
    MintDeferralScopedTokenCommand {
    override val commandId: String get() = MintDeferralScopedTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is MintDeferralScopedTokenArgs

    override suspend fun doExecute(
        args: MintDeferralScopedTokenArgs,
        applyDuring: (MintDeferralScopedTokenArgs) -> MintDeferralScopedTokenArgs,
    ): IdkResult<MintDeferralScopedTokenResult, IdkError> {
        val applied = applyDuring(args)

        val serverIdentifier = signingIdentifierResolverProvider?.invoke()?.resolveSigningIdentifier()
        val signer =
            serverIdentifier ?: return Err(
                IdkError.INVALID_STATE(
                    message =
                        "Cannot mint deferral-scoped access token: no OAuth2 server signing identifier is available " +
                            "(the AsServerSigningIdentifierResolver binding is absent or resolved null). Configure the " +
                            "server signing key or enable refresh tokens on the AS so the wallet never needs a " +
                            "deferral-scoped fallback.",
                ),
            )

        val now = clock.now().epochSeconds
        val expiresAt = now + applied.ttlSeconds
        val audience = applied.audience ?: issuerConfigProvider.issuerIdentifier

        val payload =
            buildJsonObject {
                put("iss", issuerConfigProvider.issuerIdentifier)
                put("aud", audience)
                put("iat", now)
                put("exp", expiresAt)
                put("scope", DEFERRED_CREDENTIAL_SCOPE)
                put("correlation_id", applied.correlationId)
                put("transaction_id", applied.transactionId)
                val jkt = applied.cnfJkt
                if (jkt != null) {
                    put(
                        "cnf",
                        buildJsonObject {
                            put("jkt", jkt)
                        },
                    )
                }
            }

        val protectedHeader =
            buildJsonObject {
                put("typ", AT_JWT_TYP)
            }

        val jwsArgs =
            CreateJwsArgs(
                issuer = signer,
                payload = payload.toString(),
                opts =
                    CreateJwsOpts(
                        protectedHeader = protectedHeader,
                        noIssPayloadUpdate = true,
                    ),
            )

        val signed =
            createJwsCompactCommand
                .execute(jwsArgs)
                .getOrElse { return Err(it) }

        return Ok(
            MintDeferralScopedTokenResult(
                accessToken = signed.jwt,
                expiresInSeconds = applied.ttlSeconds,
            ),
        )
    }

    private companion object {
        const val DEFERRED_CREDENTIAL_SCOPE = "deferred_credential"
        const val AT_JWT_TYP = "at+jwt"
    }
}
