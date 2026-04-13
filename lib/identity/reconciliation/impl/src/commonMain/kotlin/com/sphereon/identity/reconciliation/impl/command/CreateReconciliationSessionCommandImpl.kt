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

package com.sphereon.identity.reconciliation.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToHex
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.reconciliation.api.OidcConnectionResolver
import com.sphereon.identity.reconciliation.command.CreateReconciliationSessionCommand
import com.sphereon.identity.reconciliation.error.ReconciliationError
import com.sphereon.identity.reconciliation.model.CreateReconciliationSessionArgs
import com.sphereon.identity.reconciliation.model.CreateReconciliationSessionResult
import com.sphereon.identity.reconciliation.model.ReconciliationSession
import com.sphereon.identity.reconciliation.model.ReconciliationSessionStatus
import com.sphereon.identity.reconciliation.store.ReconciliationProviderStore
import com.sphereon.identity.reconciliation.store.ReconciliationSessionStore
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.command.CreatePkceCommand
import com.sphereon.oauth2.jwt.validation.OidcDiscoveryService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateReconciliationSessionCommandImpl", exact = true)
class CreateReconciliationSessionCommandImpl(
    execution: SessionExecution,
    private val sessionStore: ReconciliationSessionStore,
    private val providerStore: ReconciliationProviderStore,
    private val oidcConnectionResolver: OidcConnectionResolver,
    private val createPkceCommand: CreatePkceCommand,
    private val oidcDiscoveryService: OidcDiscoveryService,
) : TypedServiceCommandAdapter<CreateReconciliationSessionArgs, CreateReconciliationSessionResult>(
        commandId = CreateReconciliationSessionCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateReconciliationSessionArgs>(),
        outputTypeToken = typeToken<CreateReconciliationSessionResult>(),
    ),
    CreateReconciliationSessionCommand {
    override val commandId: String get() = CreateReconciliationSessionCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateReconciliationSessionArgs

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun doExecute(
        args: CreateReconciliationSessionArgs,
        applyDuring: (CreateReconciliationSessionArgs) -> CreateReconciliationSessionArgs,
    ): IdkResult<CreateReconciliationSessionResult, IdkError> {
        val applied = applyDuring(args)

        val provider =
            providerStore.findById(applied.providerId)
                ?: return Err(IdkError.fromDTO(ReconciliationError.ProviderNotFound(providerId = applied.providerId)))

        // Resolve OIDC connection details from OidcClientConfig references
        val oidcConnection =
            oidcConnectionResolver.resolve(provider.oidcClientId)
                ?: return Err(IdkError.fromDTO(ReconciliationError.ProviderNotFound(providerId = "oidc-client:${provider.oidcClientId}")))

        val state = Uuid.random().toString()
        val nonce = Uuid.random().toString()

        // Generate proper PKCE challenge/verifier pair (RFC 7636)
        val pkceData =
            createPkceCommand.execute(CreatePkceArgs()).getOrElse { error ->
                return Err(
                    IdkError.fromDTO(
                        ReconciliationError.OidcFlowFailed(
                            reason = "PKCE generation failed: ${error.message.defaultMessage ?: "Unknown error"}",
                        ),
                    ),
                )
            }

        // Discover OIDC metadata
        val discoveryIssuer = oidcConnection.discoveryUrl.removeSuffix("/.well-known/openid-configuration")
        val metadata = oidcDiscoveryService.getMetadata(discoveryIssuer).getOrNull()

        // Resolve endpoints: client override -> discovery -> convention fallback
        val authorizationEndpoint =
            oidcConnection.authorizationEndpointOverride
                ?: metadata?.authorizationEndpoint
                ?: (discoveryIssuer.trimEnd('/') + "/authorize")
        val tokenEndpoint =
            oidcConnection.tokenEndpointOverride
                ?: metadata?.tokenEndpoint
                ?: (discoveryIssuer.trimEnd('/') + "/token")

        // Build OIDC authorization URL
        val authorizationUrl =
            buildAuthorizationUrl(
                authorizationEndpoint = authorizationEndpoint,
                clientId = oidcConnection.clientId,
                redirectUri = applied.redirectUri,
                scopes = oidcConnection.scopes,
                state = state,
                nonce = nonce,
                codeChallenge = pkceData.codeChallenge,
                codeChallengeMethod = pkceData.codeChallengeMethod.name,
            )

        val now = Clock.System.now()
        val configTtl =
            conf
                .conf(com.sphereon.core.api.conf.ConfigLevel.PRINCIPAL)
                .getPropertyAsString("identity.reconciliation.session-ttl-seconds", null)
                ?.toLongOrNull()
        val ttlSeconds = configTtl ?: applied.sessionTtlSeconds
        val session =
            ReconciliationSession(
                id = Uuid.random().toString(),
                tenantId = applied.tenantId,
                status = ReconciliationSessionStatus.CREATED,
                identifierHash = applied.identifierHash,
                identifierType = applied.identifierType,
                providerId = applied.providerId,
                authorizationUrl = authorizationUrl,
                state = state,
                nonce = nonce,
                codeVerifier = pkceData.codeVerifier,
                redirectUri = applied.redirectUri,
                tokenEndpoint = tokenEndpoint,
                createdAt = now,
                expiresAt = now + ttlSeconds.seconds,
            )

        val stored = sessionStore.create(session)
        return Ok(CreateReconciliationSessionResult(session = stored, authorizationUrl = authorizationUrl))
    }

    private fun buildAuthorizationUrl(
        authorizationEndpoint: String,
        clientId: String,
        redirectUri: String,
        scopes: List<String>,
        state: String,
        nonce: String,
        codeChallenge: String,
        codeChallengeMethod: String,
    ): String {
        val params =
            buildList {
                add("response_type=code")
                add("client_id=${urlEncode(clientId)}")
                add("redirect_uri=${urlEncode(redirectUri)}")
                add("scope=${scopes.joinToString("+")}")
                add("state=${urlEncode(state)}")
                add("nonce=${urlEncode(nonce)}")
                add("code_challenge=${urlEncode(codeChallenge)}")
                add("code_challenge_method=$codeChallengeMethod")
            }
        return "$authorizationEndpoint?${params.joinToString("&")}"
    }

    private fun urlEncode(value: String): String {
        val sb = StringBuilder()
        for (char in value) {
            when {
                char.isLetterOrDigit() || char in "-._~" -> {
                    sb.append(char)
                }

                else -> {
                    val bytes = char.toString().encodeToByteArray()
                    val hex = bytes.encodeToHex().uppercase()
                    for (i in hex.indices step 2) {
                        sb.append('%')
                        sb.append(hex[i])
                        sb.append(hex[i + 1])
                    }
                }
            }
        }
        return sb.toString()
    }
}
