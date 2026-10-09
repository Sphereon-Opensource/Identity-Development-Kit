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
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.signing.AsSigningRequirement
import com.sphereon.oauth2.server.authorization.signing.AsServerSigningIdentifierResolver
import com.sphereon.oauth2.server.authorization.signing.CapturedAsServerConfig
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationPolicySnapshot
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationServerDeployment
import com.sphereon.openid.oid4vci.issuer.command.MintDeferralScopedTokenArgs
import com.sphereon.openid.oid4vci.issuer.command.MintDeferralScopedTokenCommand
import com.sphereon.openid.oid4vci.issuer.command.MintDeferralScopedTokenResult
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerInstanceIdProvider
import com.sphereon.openid.oid4vci.issuer.store.CredentialIssuanceSessionStore
import com.sphereon.openid.oid4vci.issuer.store.CredentialRequestIdentityStore
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialStatus
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialStore
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
 * The persisted transaction and authorization-policy snapshot select the exact hosted AS.
 * Signing uses an ACTIVE tenant descriptor from that AS's current selection; key rotation may
 * choose a different kid from the original access token. External-AS and unbacked transactions
 * fail before the JWS command is called.
 *
 * The JWT issuer is the persisted AS issuer. Audience defaults to the credential issuer and
 * remains distinct from issuer; a nonblank explicit audience may narrow the request.
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
    private val oauth2ConfigProvider: OAuth2ServersConfigProvider? = null,
    private val deferredStore: DeferredCredentialStore,
    private val sessionStore: CredentialIssuanceSessionStore,
    private val requestIdentityStore: CredentialRequestIdentityStore,
    private val issuerInstanceIdProvider: Oid4vciIssuerInstanceIdProvider,
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
        val deferred = deferredStore.get(applied.transactionId).getOrElse { return Err(it) }
            ?: return Err(IdkError.INVALID_STATE(message = "Deferral transaction has no persisted issuance binding"))
        if (deferred.status != DeferredCredentialStatus.PENDING || deferred.expiresAt <= clock.now().toEpochMilliseconds()) {
            return Err(IdkError.INVALID_STATE(message = "Deferral transaction is not pending and current"))
        }
        val session = sessionStore.get(deferred.issuanceSessionId).getOrElse { return Err(it) }
        val snapshot: Oid4vciAuthorizationPolicySnapshot
        val expectedCorrelation: String
        val issuerInstanceId: String
        if (session != null) {
            snapshot = session.authorizationPolicySnapshot
                ?: return Err(IdkError.INVALID_STATE(message = "Issuance session has no authorization-server snapshot"))
            expectedCorrelation = session.lifecycleCorrelationId ?: session.sessionId
            issuerInstanceId = session.instanceId
        } else {
            val identity = requestIdentityStore.get(deferred.issuanceSessionId).getOrElse { return Err(it) }
                ?: return Err(IdkError.INVALID_STATE(message = "Deferral transaction has no persisted issuer identity"))
            snapshot = identity.authorizationPolicySnapshot
            expectedCorrelation = identity.protocolSessionId
            issuerInstanceId = identity.instanceId
        }
        if (deferred.instanceId != issuerInstanceId || snapshot.issuerId.toString() != issuerInstanceId ||
            applied.correlationId != expectedCorrelation || issuerInstanceIdProvider.currentInstanceId() != issuerInstanceId
        ) {
            return Err(IdkError.INVALID_STATE(message = "Deferral transaction does not match its persisted issuer binding"))
        }
        if (snapshot.authorizationServerDeployment != Oid4vciAuthorizationServerDeployment.HOSTED) {
            return Err(IdkError.INVALID_STATE(message = "Deferral authorization server is not hosted"))
        }
        val exactKey = snapshot.authorizationServerRuntimeKey
            ?: return Err(IdkError.INVALID_STATE(message = "Deferral authorization server has no runtime key"))
        val configProvider = oauth2ConfigProvider
            ?: return Err(IdkError.INVALID_STATE(message = "Deferral authorization-server configuration is unavailable"))
        val resolver = signingIdentifierResolverProvider?.invoke()
            ?: return Err(IdkError.INVALID_STATE(message = "Deferral authorization-server signer is unavailable"))
        val captured = try {
            CapturedAsServerConfig.select(configProvider.getConfig(), exactKey)
        } catch (error: IllegalArgumentException) {
            return Err(IdkError.INVALID_STATE(message = error.message ?: "Deferral authorization-server selection failed"))
        } catch (error: IllegalStateException) {
            return Err(IdkError.INVALID_STATE(message = error.message ?: "Deferral authorization-server selection failed"))
        }
        if (captured.server?.issuer?.let { it != snapshot.authorizationServerIssuer } == true ||
            snapshot.authorizationServerIssuer.isBlank()
        ) {
            return Err(IdkError.INVALID_STATE(message = "Deferral authorization-server issuer differs from persisted selection"))
        }
        val signer = try {
            resolver.selectSigning(captured, AsSigningRequirement.REQUIRED).identifier
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (expected: Exception) {
            return Err(IdkError.INVALID_STATE(message = expected.message ?: "Deferral authorization-server signing selection failed"))
        } ?: return Err(IdkError.INVALID_STATE(message = "Deferral authorization-server signer is unavailable"))

        val now = clock.now().epochSeconds
        val expiresAt = now + applied.ttlSeconds
        val audience = applied.audience ?: issuerConfigProvider.issuerIdentifier
        if (audience.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Deferral-token audience must not be blank"))
        }

        val payload =
            buildJsonObject {
                put("iss", snapshot.authorizationServerIssuer)
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
