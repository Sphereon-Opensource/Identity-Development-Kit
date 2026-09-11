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

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.client.OAuth2Client
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.command.federation.AuthorizationUrl
import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationCommand
import com.sphereon.oauth2.server.authorization.config.FederationMetadataResolver
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.FederationFlowConfig
import com.sphereon.oauth2.server.authorization.provider.FederationProviderRuntimeResolver
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRoute
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRouteDecision
import com.sphereon.oauth2.server.authorization.storage.FederationSessionStore
import com.sphereon.oauth2.server.authorization.storage.PendingFederation
import com.sphereon.oauth2.server.authorization.storage.PendingAuthorizationSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock

private const val RANDOM_TOKEN_BYTES = 32

/**
 * Initiate an upstream federated authentication by fetching provider metadata, building the
 * upstream authorization URL, and persisting a [PendingFederation] keyed by the generated `state`.
 *
 * Reads tenant from [SessionExecution] at the persistence call site rather than threading it
 * through [InitiateProviderAuthenticationArgs] or the pending record. A session drift between
 * initiate time and callback time therefore cannot silently proceed against a stale tenant.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<InitiateProviderAuthenticationCommand>())
class InitiateProviderAuthenticationCommandImpl(
    private val sessionExecution: SessionExecution,
    private val oauth2Client: OAuth2Client,
    private val providerResolver: FederationProviderRuntimeResolver,
    private val sessionStore: FederationSessionStore,
    private val secureRandom: SecureRandom,
    private val metadataResolver: FederationMetadataResolver,
    private val flowConfig: FederationFlowConfig,
    private val pendingAuthorizationSessionStore: PendingAuthorizationSessionStore,
    private val clock: Clock,
) : TypedServiceCommandAdapter<InitiateProviderAuthenticationArgs, AuthorizationUrl, AuthenticationError>(
        commandId = InitiateProviderAuthenticationCommand.COMMAND_ID,
        execution = sessionExecution,
        inputTypeToken = typeToken<InitiateProviderAuthenticationArgs>(),
        outputTypeToken = typeToken<AuthorizationUrl>(),
    ),
    InitiateProviderAuthenticationCommand {
    override val commandId: String get() = InitiateProviderAuthenticationCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is InitiateProviderAuthenticationArgs

    override suspend fun doExecute(
        args: InitiateProviderAuthenticationArgs,
        applyDuring: (InitiateProviderAuthenticationArgs) -> InitiateProviderAuthenticationArgs,
    ): IdkResult<AuthorizationUrl, AuthenticationError> {
        val applied = applyDuring(args)
        val downstream = pendingAuthorizationSessionStore.findById(applied.sessionId)
        if (downstream.isErr) return Err(AuthenticationError.Generic(description = downstream.error.message.defaultMessage))
        val downstreamSession = downstream.value
            ?: return Err(AuthenticationError.Generic(description = "Unknown downstream authorization transaction"))
        val route = downstreamSession.authenticationRoute
            ?: return Err(AuthenticationError.Generic(description = "Downstream authorization transaction has no authentication route"))
        val pinnedRoute = pinFederationRouteBinding(route, applied.providerId).getOrElse { return Err(it) }
        val routeBinding = pinnedRoute.eligibleBindings.single { it.bindingId == applied.providerId }
        val providerConfig = providerResolver.resolve(applied.providerId).getOrElse { return Err(it) }

        val metadata =
            metadataResolver
                .resolve(providerConfig)
                .getOrElse {
                    return Err(
                        AuthenticationError.Generic(
                            description = "Failed to fetch upstream metadata for ${applied.providerId}: ${it.message.defaultMessage}",
                        ),
                    )
                }

        if (metadata.issuer != routeBinding.upstreamIssuer || providerConfig.issuerUrl != routeBinding.upstreamIssuer) {
            return Err(AuthenticationError.Generic(description = "Resolved upstream issuer does not match the selected federation binding"))
        }
        federationMetadataPinMismatch(providerConfig, metadata)?.let {
            return Err(AuthenticationError.Generic(description = it))
        }

        val state = secureRandom.newToken(lengthBytes = RANDOM_TOKEN_BYTES, encoding = Encoding.HEX)
        val nonce = secureRandom.newToken(lengthBytes = RANDOM_TOKEN_BYTES, encoding = Encoding.HEX)
        if (state == nonce || state == downstreamSession.state || nonce == downstreamSession.nonce) {
            return Err(AuthenticationError.Generic(description = "Upstream transaction entropy collided with downstream transaction values"))
        }

        val effectiveCallbackPath = applied.callbackPath ?: providerConfig.callbackPath
        // The federation callback URI MUST resolve to the AS base, not to whatever
        // intermediate URL the upstream caller passed as `returnUrl`. The standard
        // authorize flow (`AuthorizeHttpEndpointCommandImpl`) hands us
        // `${baseUrl}/authorize/callback` as the returnUrl so the user-auth provider
        // can hop back into the AS post-authn — but for the federation IdP redirect
        // we need only `${baseUrl}` as the base, otherwise we end up with
        // `${baseUrl}/authorize/callback/federation/callback` which never matches
        // the upstream's registered redirect URI. Strip that intermediate suffix
        // so both standard-authorize and direct-federation entry points produce the
        // same canonical callback URI.
        val asBaseUrl =
            applied.returnUrl
                .substringBefore("?")
                .substringBefore("/authorize/callback")
                .trimEnd('/')
        val callbackRedirectUri = asBaseUrl + effectiveCallbackPath

        val clientAuth = providerResolver.clientAuthentication(applied.providerId, metadata.issuer)
            .getOrElse { return Err(it) }

        val authResult =
            oauth2Client
                .initiateAuthorization(
                    authorizationServerMetadata = metadata,
                    clientId = providerConfig.clientId,
                    redirectUri = callbackRedirectUri,
                    scope = providerConfig.scopes.joinToString(" "),
                    state = state,
                    clientAuthentication = clientAuth,
                    additionalParameters = federationAuthorizationParameters(applied, nonce),
                ).getOrElse {
                    return Err(
                        AuthenticationError.Generic(
                            description = "Failed to initiate upstream authorization for ${applied.providerId}: ${it.message.defaultMessage}",
                        ),
                    )
                }

        val upstreamPkce = authResult.pkceData
            ?: return Err(AuthenticationError.Generic(description = "Upstream authorization did not produce an independent PKCE transaction"))
        if (upstreamPkce.codeVerifier.isBlank() || upstreamPkce.codeChallenge.isBlank() ||
            upstreamPkce.codeChallenge == downstreamSession.codeChallenge
        ) {
            return Err(AuthenticationError.Generic(description = "Upstream authorization produced invalid or reused PKCE state"))
        }

        val now = clock.now()
        val pendingEntry =
            PendingFederation(
                tenantId = sessionExecution.tenantId,
                hostedAuthorizationServerId = route.hostedAuthorizationServerId,
                hostedAuthorizationServerRevision = route.hostedAuthorizationServerRevision,
                federationBindingId = routeBinding.bindingId,
                federationBindingRevision = routeBinding.bindingRevision,
                upstreamAuthorizationServerId = routeBinding.upstreamResourceId,
                upstreamAuthorizationServerRevision = routeBinding.upstreamResourceRevision,
                upstreamIssuer = routeBinding.upstreamIssuer,
                downstreamClientId = downstreamSession.clientId,
                authenticationRoute = pinnedRoute,
                sessionId = applied.sessionId,
                state = state,
                nonce = nonce,
                pkceData = upstreamPkce,
                metadata = metadata,
                returnUrl = applied.returnUrl,
                callbackRedirectUri = callbackRedirectUri,
                completed = false,
                providerId = applied.providerId,
                flowContext = applied.flowContext,
                applicationId = applied.applicationId,
                createdAt = now,
                expiresAt = now + flowConfig.pendingTtl,
            )
        val storeResult = sessionStore.storePendingFederation(pendingEntry, ttl = flowConfig.pendingTtl)
        if (storeResult.isErr) {
            return Err(
                AuthenticationError.Generic(
                    description = "Failed to persist pending federation state: ${storeResult.error.message.defaultMessage}",
                ),
            )
        }

        // The authorization endpoint is the endpoint pinned by the validated discovery
        // snapshot. Runtime string replacement would permit redirect substitution after the
        // binding decision and defeats issuer mix-up protection.
        return Ok(AuthorizationUrl(authResult.authorizationUrl))
    }

}

internal fun federationMetadataPinMismatch(
    providerConfig: FederationProviderConfig,
    metadata: AuthorizationServerMetadata,
): String? = when {
    providerConfig.authorizationEndpointOverride != null &&
        metadata.authorizationEndpoint != providerConfig.authorizationEndpointOverride ->
        "Resolved upstream authorization endpoint does not match the validated federation binding"
    providerConfig.tokenEndpointOverride != null &&
        metadata.tokenEndpoint != providerConfig.tokenEndpointOverride ->
        "Resolved upstream token endpoint does not match the validated federation binding"
    else -> null
}

internal fun pinFederationRouteBinding(
    route: AuthenticationRouteDecision,
    providerId: String,
): IdkResult<AuthenticationRouteDecision, AuthenticationError> {
    val binding = route.eligibleBindings.firstOrNull { it.bindingId == providerId }
        ?: return Err(AuthenticationError.Generic(description = "Federation binding is not eligible for this transaction"))
    if (route.selectedBindingId != null && route.selectedBindingId != providerId) {
        return Err(AuthenticationError.Generic(description = "Federation binding does not match the transaction route"))
    }
    return Ok(
        if (route.selectedBindingId == binding.bindingId) {
            route
        } else {
            route.copy(route = AuthenticationRoute.UPSTREAM_REDIRECT, selectedBindingId = binding.bindingId)
        },
    )
}

internal fun federationAuthorizationParameters(
    args: InitiateProviderAuthenticationArgs,
    nonce: String,
): Map<String, String> =
    buildMap {
        put("nonce", nonce)
        args.hint?.loginHint?.let { put("login_hint", it) }
        args.acrValues.takeIf { it.isNotEmpty() }?.let { put("acr_values", it.joinToString(" ")) }
        if (args.forceReauth) put("prompt", "login")
    }
