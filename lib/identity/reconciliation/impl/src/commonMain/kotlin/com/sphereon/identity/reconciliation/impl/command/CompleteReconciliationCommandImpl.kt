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
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.matching.command.CreateIdentityMatchCommand
import com.sphereon.identity.matching.crypto.ReconciliationCryptoService
import com.sphereon.identity.matching.model.CreateIdentityMatchArgs
import com.sphereon.identity.matching.store.IdentityMatchStore
import com.sphereon.identity.reconciliation.api.OidcConnectionResolver
import com.sphereon.identity.reconciliation.command.CompleteReconciliationCommand
import com.sphereon.identity.reconciliation.error.ReconciliationError
import com.sphereon.identity.reconciliation.model.CompleteReconciliationArgs
import com.sphereon.identity.reconciliation.model.CompleteReconciliationResult
import com.sphereon.identity.reconciliation.model.ReconciliationAttributeMapping
import com.sphereon.identity.reconciliation.model.ReconciliationSessionStatus
import com.sphereon.identity.reconciliation.model.ResolvedIdentity
import com.sphereon.identity.reconciliation.store.ReconciliationProviderStore
import com.sphereon.identity.reconciliation.store.ReconciliationSessionStore
import com.sphereon.oauth2.client.command.ExchangeTokenArgs
import com.sphereon.oauth2.client.command.ExchangeTokenCommand
import com.sphereon.oauth2.client.command.FetchUserInfoArgs
import com.sphereon.oauth2.client.command.FetchUserInfoCommand
import com.sphereon.oauth2.common.model.TokenRequest
import com.sphereon.oauth2.common.token.OidcTokenClaimExtractor
import com.sphereon.oauth2.jwt.validation.OidcDiscoveryService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CompleteReconciliationCommandImpl", exact = true)
class CompleteReconciliationCommandImpl(
    execution: SessionExecution,
    private val sessionStore: ReconciliationSessionStore,
    private val providerStore: ReconciliationProviderStore,
    private val oidcConnectionResolver: OidcConnectionResolver,
    private val reconciliationCryptoService: ReconciliationCryptoService,
    private val exchangeTokenCommand: ExchangeTokenCommand,
    private val fetchUserInfoCommand: FetchUserInfoCommand,
    private val createMatchCommand: CreateIdentityMatchCommand,
    private val identityMatchStore: IdentityMatchStore,
    private val oidcDiscoveryService: OidcDiscoveryService,
    private val tokenClaimExtractor: OidcTokenClaimExtractor,
) : TypedServiceCommandAdapter<CompleteReconciliationArgs, CompleteReconciliationResult, IdkError>(
        commandId = CompleteReconciliationCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CompleteReconciliationArgs>(),
        outputTypeToken = typeToken<CompleteReconciliationResult>(),
    ),
    CompleteReconciliationCommand {
    override val commandId: String get() = CompleteReconciliationCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CompleteReconciliationArgs

    override suspend fun doExecute(
        args: CompleteReconciliationArgs,
        applyDuring: (CompleteReconciliationArgs) -> CompleteReconciliationArgs,
    ): IdkResult<CompleteReconciliationResult, IdkError> {
        val applied = applyDuring(args)

        // Retrieve session
        val session =
            sessionStore.findById(applied.tenantId, applied.sessionId)
                ?: return Err(IdkError.fromDTO(ReconciliationError.SessionNotFound(sessionId = applied.sessionId)))

        // Verify session state
        if (session.status != ReconciliationSessionStatus.CREATED && session.status != ReconciliationSessionStatus.REDIRECTED) {
            return Err(
                IdkError.fromDTO(
                    ReconciliationError.SessionInvalidState(
                        sessionId = applied.sessionId,
                        currentStatus = session.status.name,
                        expectedStatus = "CREATED or REDIRECTED",
                    ),
                ),
            )
        }

        // Check expiry
        if (session.expiresAt < Clock.System.now()) {
            sessionStore.update(session.copy(status = ReconciliationSessionStatus.EXPIRED))
            return Err(IdkError.fromDTO(ReconciliationError.SessionExpired(sessionId = applied.sessionId)))
        }

        // Verify state parameter
        if (session.state != applied.state) {
            return Err(
                IdkError.fromDTO(
                    ReconciliationError.StateMismatch(
                        expected = session.state ?: "",
                        actual = applied.state,
                    ),
                ),
            )
        }

        // Look up provider
        val provider =
            providerStore.findById(session.providerId)
                ?: return Err(IdkError.fromDTO(ReconciliationError.ProviderNotFound(providerId = session.providerId)))

        // Resolve OIDC connection details
        val oidcConnection =
            oidcConnectionResolver.resolve(provider.oidcClientId)
                ?: return Err(IdkError.fromDTO(ReconciliationError.ProviderNotFound(providerId = "oidc-client:${provider.oidcClientId}")))

        // Resolve token endpoint: client override -> session cache -> OIDC discovery -> convention fallback
        val discoveryIssuer = oidcConnection.discoveryUrl.removeSuffix("/.well-known/openid-configuration")
        val tokenEndpoint =
            oidcConnection.tokenEndpointOverride
                ?: session.tokenEndpoint
                ?: oidcDiscoveryService.getMetadata(discoveryIssuer).getOrNull()?.tokenEndpoint
                ?: (discoveryIssuer.trimEnd('/') + "/token")

        val tokenRequest =
            TokenRequest(
                grantType = "authorization_code",
                code = applied.authorizationCode,
                redirectUri = session.redirectUri,
                codeVerifier = session.codeVerifier,
                clientId = oidcConnection.clientId,
                clientSecret = oidcConnection.clientSecret,
            )

        val tokenResponse =
            exchangeTokenCommand
                .execute(
                    ExchangeTokenArgs(tokenEndpoint = tokenEndpoint, request = tokenRequest),
                ).getOrElse { error ->
                    sessionStore.update(session.copy(status = ReconciliationSessionStatus.ERROR, errorMessage = "Token exchange failed"))
                    return Err(
                        IdkError.fromDTO(
                            ReconciliationError.TokenExchangeFailed(
                                reason = error.message.defaultMessage ?: "Unknown error",
                            ),
                        ),
                    )
                }

        // Decode ID token claims (safe without signature verification per OIDC Core 3.1.3.7)
        val idTokenClaims: Map<String, JsonElement> =
            tokenResponse.idToken?.let { idToken ->
                tokenClaimExtractor.extractAllClaims(idToken).getOrNull()
            } ?: emptyMap()

        // Fetch userinfo claims if enabled for this OIDC client
        val userInfoClaims: Map<String, JsonElement> =
            if (oidcConnection.userInfoEnabled) {
                val userinfoEndpoint =
                    oidcDiscoveryService.getMetadata(discoveryIssuer).getOrNull()?.userinfoEndpoint
                        ?: (discoveryIssuer.trimEnd('/') + "/userinfo")
                val userInfoResult =
                    fetchUserInfoCommand.execute(
                        FetchUserInfoArgs(
                            accessToken = tokenResponse.accessToken,
                            userinfoEndpoint = userinfoEndpoint,
                        ),
                    )
                userInfoResult.getOrNull()?.let { result ->
                    buildMap {
                        put("sub", JsonPrimitive(result.sub))
                        putAll(result.claims)
                    }
                } ?: emptyMap()
            } else {
                emptyMap()
            }

        // Merge claims: start with ID token claims, overlay userinfo claims (userinfo takes precedence)
        val mergedClaims =
            buildMap {
                putAll(idTokenClaims)
                putAll(userInfoClaims)
            }

        // Apply provider attribute mappings
        val mappedClaimsResult = applyAttributeMappings(mergedClaims, provider.attributeMappings)
        val mappedClaims =
            mappedClaimsResult.getOrElse { error ->
                sessionStore.update(session.copy(status = ReconciliationSessionStatus.ERROR, errorMessage = error.message.defaultMessage))
                return Err(error)
            }

        // Apply userinfo-specific attribute mappings on top (additive)
        val finalClaimsResult =
            if (provider.userInfoAttributeMappings.isNotEmpty()) {
                applyAttributeMappings(mappedClaims, provider.userInfoAttributeMappings)
            } else {
                Ok(mappedClaims)
            }

        val finalClaims =
            finalClaimsResult.getOrElse { error ->
                sessionStore.update(session.copy(status = ReconciliationSessionStatus.ERROR, errorMessage = error.message.defaultMessage))
                return Err(error)
            }

        val externalSubject =
            finalClaims[provider.identifierAttributeName]?.jsonPrimitive?.content
                ?: return Err(
                    IdkError.fromDTO(
                        ReconciliationError.IdentifierAttributeMissing(
                            attributeName = provider.identifierAttributeName,
                            providerId = provider.id,
                        ),
                    ),
                )

        // Build resolved identity from merged and mapped claims
        val resolvedIdentity =
            ResolvedIdentity(
                externalSubject = externalSubject,
                externalIssuer = finalClaims["iss"]?.jsonPrimitive?.content ?: discoveryIssuer,
                claims = finalClaims,
                internalIdentityId = applied.internalIdentityId,
            )

        // Create identity match (idempotent: if match already exists, use it)
        val matchResult =
            createMatchCommand.execute(
                CreateIdentityMatchArgs(
                    identifierHash = session.identifierHash,
                    identifierType = session.identifierType,
                    internalIdentityId = applied.internalIdentityId,
                    tenantId = applied.tenantId,
                    hashKeyVersion = applied.hashKeyVersion,
                ),
            )

        val match =
            matchResult.getOrElse { error ->
                val existing =
                    identityMatchStore.findByIdentifierHash(
                        applied.tenantId,
                        session.identifierHash,
                        session.identifierType,
                    )
                if (existing != null) {
                    existing
                } else {
                    return Err(
                        IdkError.fromDTO(
                            ReconciliationError.MatchCreationFailed(
                                reason = error.message.defaultMessage ?: "Unknown error",
                            ),
                        ),
                    )
                }
            }

        // Encrypt resolved identity before storing in session
        val identityJson = Json.encodeToString(resolvedIdentity)
        val encryptedIdentity =
            try {
                reconciliationCryptoService.encrypt(identityJson)
            } catch (expected: Exception) {
                return Err(
                    IdkError.fromDTO(
                        ReconciliationError.CryptoFailed(
                            operation = "encrypt",
                            reason = expected.message ?: "Unknown encryption error",
                        ),
                    ),
                )
            }

        // Update session to completed
        val updatedSession =
            sessionStore.update(
                session.copy(
                    status = ReconciliationSessionStatus.COMPLETED,
                    encryptedIdentity = encryptedIdentity,
                ),
            )

        return Ok(CompleteReconciliationResult(session = updatedSession, match = match))
    }

    private fun applyAttributeMappings(
        claims: Map<String, JsonElement>,
        mappings: List<ReconciliationAttributeMapping>,
    ): IdkResult<Map<String, JsonElement>, IdkError> {
        if (mappings.isEmpty()) {
            return Ok(claims)
        }

        val missingRequired = mutableListOf<String>()
        val result =
            buildMap {
                putAll(claims)
                for (mapping in mappings) {
                    val value = claims[mapping.source]
                    if (value != null) {
                        put(mapping.target, value)
                    } else if (mapping.required) {
                        missingRequired.add("${mapping.source} -> ${mapping.target}")
                    }
                }
            }

        if (missingRequired.isNotEmpty()) {
            return Err(
                IdkError.fromDTO(
                    ReconciliationError.RequiredAttributesMissing(
                        missingAttributes = missingRequired,
                    ),
                ),
            )
        }

        return Ok(result)
    }
}
