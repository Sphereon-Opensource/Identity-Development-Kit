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
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientCredentials
import com.sphereon.oauth2.server.authorization.command.federation.AuthorizationUrl
import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationCommand
import com.sphereon.oauth2.server.authorization.config.FederationMetadataResolver
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.FederationFlowConfig
import com.sphereon.oauth2.server.authorization.provider.FederationProviderRegistry
import com.sphereon.oauth2.server.authorization.storage.FederationSessionStore
import com.sphereon.oauth2.server.authorization.storage.PendingFederation
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

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
    execution: SessionExecution,
    private val oauth2Client: OAuth2Client,
    private val providerRegistry: FederationProviderRegistry,
    private val sessionStore: FederationSessionStore,
    private val secureRandom: SecureRandom,
    private val metadataResolver: FederationMetadataResolver,
    private val flowConfig: FederationFlowConfig,
) : TypedServiceCommandAdapter<InitiateProviderAuthenticationArgs, AuthorizationUrl, AuthenticationError>(
        commandId = InitiateProviderAuthenticationCommand.COMMAND_ID,
        execution = execution,
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
        val providerConfig =
            resolveProvider(applied.providerId)
                ?: return Err(
                    AuthenticationError.Generic(
                        description = "Unknown or disabled federation provider: ${applied.providerId}",
                    ),
                )

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

        val state = secureRandom.newToken(lengthBytes = RANDOM_TOKEN_BYTES, encoding = Encoding.HEX)
        val nonce = secureRandom.newToken(lengthBytes = RANDOM_TOKEN_BYTES, encoding = Encoding.HEX)

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

        val clientAuth =
            providerConfig.clientSecret?.let { secret ->
                ClientAuthenticationConfig.Post(
                    credentials =
                        ClientCredentials(
                            clientId = providerConfig.clientId,
                            clientSecret = secret,
                        ),
                )
            }

        val authResult =
            oauth2Client
                .initiateAuthorization(
                    authorizationServerMetadata = metadata,
                    clientId = providerConfig.clientId,
                    redirectUri = callbackRedirectUri,
                    scope = providerConfig.scopes.joinToString(" "),
                    state = state,
                    clientAuthentication = clientAuth,
                    additionalParameters =
                        buildMap {
                            put("nonce", nonce)
                            applied.hint?.loginHint?.let { put("login_hint", it) }
                        },
                ).getOrElse {
                    return Err(
                        AuthenticationError.Generic(
                            description = "Failed to initiate upstream authorization for ${applied.providerId}: ${it.message.defaultMessage}",
                        ),
                    )
                }

        val pendingEntry =
            PendingFederation(
                sessionId = applied.sessionId,
                state = state,
                nonce = nonce,
                pkceData = authResult.pkceData,
                metadata = metadata,
                returnUrl = applied.returnUrl,
                callbackRedirectUri = callbackRedirectUri,
                completed = false,
                providerId = applied.providerId,
                flowContext = applied.flowContext,
                applicationId = applied.applicationId,
            )
        val storeResult = sessionStore.storePendingFederation(pendingEntry, ttl = flowConfig.pendingTtl)
        if (storeResult.isErr) {
            return Err(
                AuthenticationError.Generic(
                    description = "Failed to persist pending federation state: ${storeResult.error.message.defaultMessage}",
                ),
            )
        }

        val authUrl =
            if (providerConfig.authorizationEndpointOverride != null && metadata.authorizationEndpoint != null) {
                authResult.authorizationUrl.replace(metadata.authorizationEndpoint!!, providerConfig.authorizationEndpointOverride!!)
            } else {
                authResult.authorizationUrl
            }

        return Ok(AuthorizationUrl(authUrl))
    }

    private fun resolveProvider(providerId: String?): FederationProviderConfig? {
        val id = providerId ?: providerRegistry.defaultProviderId() ?: return null
        return providerRegistry.findById(id)?.takeIf { it.enabled }
    }
}
