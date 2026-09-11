/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.oauth2.server.authorization.impl.command.federation

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.federation.FederationCompleteOutcome
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationOutcomeArgs
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationOutcomeCommand
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.FederatedClaimMapper
import com.sphereon.oauth2.server.authorization.provider.FederatedIdentityLinker
import com.sphereon.oauth2.server.authorization.provider.FederationFlowConfig
import com.sphereon.oauth2.server.authorization.provider.LinkFederatedSessionRequest
import com.sphereon.oauth2.server.authorization.storage.CachedUserInfo
import com.sphereon.oauth2.server.authorization.storage.FederationSessionStore
import com.sphereon.oauth2.server.authorization.model.NormalizedAuthenticationEvidence
import com.sphereon.oauth2.server.authorization.model.ProvenancedAuthenticationClaim
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlin.time.Clock

/**
 * Normal federation login completion: project upstream claims via [FederatedClaimMapper], link
 * the federated identity to a local Identity via [FederatedIdentityLinker], then atomically
 * flip the pending record's `completed` flag and write the claims cache via
 * [FederationSessionStore.completePendingFederation].
 *
 * Tenant is read from [SessionExecution] at the linker-call site (A1-R1): the pending record
 * intentionally does not carry tenant state, so a stale capture at initiate time cannot override
 * the live session at callback time.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleFederationOutcomeCommand>())
class HandleFederationOutcomeCommandImpl(
    execution: SessionExecution,
    private val claimMapper: FederatedClaimMapper,
    private val federatedIdentityLinker: FederatedIdentityLinker,
    private val sessionStore: FederationSessionStore,
    private val flowConfig: FederationFlowConfig,
    private val clock: Clock,
) : TypedServiceCommandAdapter<HandleFederationOutcomeArgs, FederationCompleteOutcome, AuthenticationError>(
        commandId = HandleFederationOutcomeCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleFederationOutcomeArgs>(),
        outputTypeToken = typeToken<FederationCompleteOutcome>(),
    ),
    HandleFederationOutcomeCommand {
    override val commandId: String get() = HandleFederationOutcomeCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is HandleFederationOutcomeArgs

    override suspend fun doExecute(
        args: HandleFederationOutcomeArgs,
        applyDuring: (HandleFederationOutcomeArgs) -> HandleFederationOutcomeArgs,
    ): IdkResult<FederationCompleteOutcome, AuthenticationError> {
        val applied = applyDuring(args)
        val exchange = applied.exchange
        val pending = applied.pending
        val providerConfig = applied.providerConfig
        val state = applied.state
        val log = execution.log

        val rawClaims = exchange.claims
        log.debug("Raw claims from upstream (${rawClaims.size} total, keys=${rawClaims.keys})")

        val mergedClaims =
            try {
                claimMapper.map(rawClaims)
            } catch (e: IllegalArgumentException) {
                return Err(
                    AuthenticationError.Generic(
                        description = "Upstream claims rejected: ${e.message ?: "invalid claim mapping"}",
                    ),
                )
            } catch (e: IllegalStateException) {
                return Err(
                    AuthenticationError.Generic(
                        description = "Upstream claims rejected: ${e.message ?: "required claims missing"}",
                    ),
                )
            }

        log.debug("Projected claims (${mergedClaims.size} total, keys=${mergedClaims.keys})")
        val upstreamSub = exchange.upstreamSubject

        val tenant = execution.tenantId
        val now = clock.now()
        val linked =
            federatedIdentityLinker
                .linkFederatedSession(
                    LinkFederatedSessionRequest(
                        tenantId = tenant,
                        sessionId = pending.sessionId,
                        upstreamIssuer = pending.upstreamIssuer,
                        upstreamSub = upstreamSub,
                        identifierClaimName = providerConfig.identifierClaimName,
                        claims = mergedClaims,
                        acr = exchange.upstreamAcr,
                        amr = exchange.upstreamAmr,
                        returnUrl = pending.returnUrl,
                        remoteIp = null,
                        authenticatedAt = now,
                        expiresAt = now + flowConfig.sessionTtl,
                        upstreamSid = exchange.upstreamSid,
                        applicationId = pending.applicationId,
                    ),
                ).getOrElse {
                    return Err(
                        AuthenticationError.Generic(
                            exception = it.exception,
                            description = it.message.defaultMessage,
                        ),
                    )
                }
        val userId: String = linked.localIdentityId

        val claimsAsJson: Map<String, JsonElement> = mergedClaims.mapValues { (_, v) -> v.toJsonElement() }

        // Synthetic upstream-identity claims injected so they flow through
        // CreateAccessTokenCommandImpl.additionalClaims into the minted access token and are
        // readable on ValidatedTokenContext (upstream_sub, upstream_iss, upstream_acr, upstream_amr).
        val upstreamClaims =
            buildMap<String, JsonElement> {
                put("upstream_sub", JsonPrimitive(upstreamSub))
                put("upstream_iss", JsonPrimitive(pending.upstreamIssuer))
                exchange.upstreamAcr?.let { put("upstream_acr", JsonPrimitive(it)) }
                exchange.upstreamAmr?.let { amr -> put("upstream_amr", buildJsonArray { amr.forEach { add(JsonPrimitive(it)) } }) }
            }

        val cached =
            CachedUserInfo(
                userId = userId,
                claims = claimsAsJson + upstreamClaims,
                cachedAt = clock.now(),
            )
        val selectedRouteBinding = pending.authenticationRoute.eligibleBindings.single { it.bindingId == pending.federationBindingId }
        if (selectedRouteBinding.claimsMapping.values.toSet().size != selectedRouteBinding.claimsMapping.size) {
            return Err(
                AuthenticationError.Generic(
                    description = "Federation claim mapping contains duplicate governed targets",
                ),
            )
        }
        val governedClaims = selectedRouteBinding.claimsMapping.mapNotNull { (source, target) ->
            rawClaims[source]?.let { value ->
                target to ProvenancedAuthenticationClaim(
                    value = value.toJsonElement(),
                    sourceClaim = source,
                    issuer = pending.upstreamIssuer,
                )
            }
        }.toMap()
        val evidence = NormalizedAuthenticationEvidence(
            hostedAuthorizationServerId = pending.hostedAuthorizationServerId,
            federationBindingId = pending.federationBindingId,
            upstreamIssuer = pending.upstreamIssuer,
            upstreamSubject = upstreamSub,
            localSubject = userId,
            acr = exchange.upstreamAcr,
            amr = exchange.upstreamAmr.orEmpty(),
            authTime = exchange.upstreamAuthTime ?: exchange.validatedAt,
            governedClaims = governedClaims,
            downstreamTransactionId = pending.sessionId,
            upstreamTransactionId = pending.state,
            validatedAt = exchange.validatedAt,
            hostedAuthorizationServerRevision = pending.hostedAuthorizationServerRevision,
            federationBindingRevision = pending.federationBindingRevision,
            upstreamResourceRevision = pending.upstreamAuthorizationServerRevision,
        )
        val completeResult =
            sessionStore.completePendingFederation(
                state = state,
                userId = userId,
                claims = cached,
                claimsTtl = flowConfig.claimsCacheTtl,
                upstreamAcr = exchange.upstreamAcr,
                upstreamAmr = exchange.upstreamAmr,
                evidence = evidence,
            )
        if (completeResult.isErr) {
            return Err(
                AuthenticationError.Generic(
                    description = "Failed to complete federation session: ${completeResult.error.message.defaultMessage}",
                ),
            )
        }

        return Ok(FederationCompleteOutcome(sessionId = pending.sessionId))
    }

}

private fun Any?.toJsonElement(): JsonElement =
    when (this) {
        null -> {
            JsonNull
        }

        is JsonElement -> {
            this
        }

        is Boolean -> {
            JsonPrimitive(this)
        }

        is Number -> {
            JsonPrimitive(this)
        }

        is String -> {
            JsonPrimitive(this)
        }

        is Map<*, *> -> {
            JsonObject(
                this.entries.associate { (k, v) -> k.toString() to v.toJsonElement() },
            )
        }

        is Iterable<*> -> {
            JsonArray(this.map { it.toJsonElement() })
        }

        else -> {
            JsonPrimitive(toString())
        }
    }
