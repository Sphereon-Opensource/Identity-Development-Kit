/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.identity.idv.oidc

import com.sphereon.attribute.flow.AttributePath
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToHex
import com.sphereon.crypto.resolution.IdentifierService
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwksUrlOpts
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.idv.model.AttributeMapping
import com.sphereon.identity.idv.model.AuthMethodReference
import com.sphereon.identity.idv.model.CallbackComplete
import com.sphereon.identity.idv.model.CallbackFailed
import com.sphereon.identity.idv.model.CallbackOutcome
import com.sphereon.identity.idv.model.CallbackWork
import com.sphereon.identity.idv.model.CancelAcknowledged
import com.sphereon.identity.idv.model.CancelOutcome
import com.sphereon.identity.idv.model.CancelWork
import com.sphereon.identity.idv.model.DispatchOutcome
import com.sphereon.identity.idv.model.DispatchWork
import com.sphereon.identity.idv.model.DriverError
import com.sphereon.identity.idv.model.IdvEvidence
import com.sphereon.identity.idv.model.IdvEvidenceType
import com.sphereon.identity.idv.model.IdvMethodDriver
import com.sphereon.identity.idv.model.IdvMethodType
import com.sphereon.identity.idv.model.IdvNodeResult
import com.sphereon.identity.idv.model.OidcMethodDefinition
import com.sphereon.identity.idv.model.PendingDispatch
import com.sphereon.identity.idv.model.PollAction
import com.sphereon.identity.idv.model.PollFailed
import com.sphereon.identity.idv.model.PollOutcome
import com.sphereon.identity.idv.model.PollPending
import com.sphereon.identity.idv.model.PollWork
import com.sphereon.identity.idv.model.RedirectAction
import com.sphereon.identity.idv.model.ResolvedIdentifier
import com.sphereon.identity.idv.model.SubmitFailed
import com.sphereon.identity.idv.model.SubmitOutcome
import com.sphereon.identity.idv.model.SubmitWork
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.command.CreatePkceCommand
import com.sphereon.oauth2.client.command.ExchangeTokenArgs
import com.sphereon.oauth2.client.command.ExchangeTokenCommand
import com.sphereon.oauth2.client.command.FetchUserInfoArgs
import com.sphereon.oauth2.client.command.FetchUserInfoCommand
import com.sphereon.oauth2.common.model.TokenRequest
import com.sphereon.oauth2.jwt.validation.OidcDiscoveryMetadata
import com.sphereon.oauth2.jwt.validation.OidcDiscoveryService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<IdvMethodDriver>())
class OidcMethodDriver(
    private val principalConfigService: PrincipalConfigService,
    private val createPkceCommand: CreatePkceCommand,
    private val exchangeTokenCommand: ExchangeTokenCommand,
    private val fetchUserInfoCommand: FetchUserInfoCommand,
    private val oidcDiscoveryService: OidcDiscoveryService,
    private val identifierService: IdentifierService,
    private val stateStore: OidcDriverStateStore,
) : IdvMethodDriver {
    override val methodType: IdvMethodType = IdvMethodType.OIDC

    override suspend fun dispatch(work: DispatchWork): IdkResult<DispatchOutcome, com.sphereon.identity.idv.model.IdvError> {
        val definition =
            work.methodDefinition as? OidcMethodDefinition
                ?: return driverError(work, "OIDC driver received unsupported method definition")

        val clientId =
            resolveConfig(definition.clientIdRef.key, definition.clientIdRef.required)
                ?: return driverError(work, "Missing config value '${definition.clientIdRef.key}'")
        val clientSecret = resolveSecret(definition)

        val metadata =
            oidcDiscoveryService.getMetadata(definition.discoveryUrl).getOrElse { error ->
                return driverError(work, "OIDC discovery failed for '${definition.discoveryUrl}': ${error.message}")
            }
        val pkce =
            createPkceCommand.execute(CreatePkceArgs()).getOrElse { error ->
                return driverError(work, "PKCE generation failed: ${error.message.defaultMessage}")
            }

        val now = Clock.System.now()
        val state = randomId()
        val nonce = randomId()
        val callbackRef = work.callbackBaseUrl
        val authorizationEndpoint = definition.authorizationEndpointOverride ?: metadata.authorizationEndpoint
        val tokenEndpoint = definition.tokenEndpointOverride ?: metadata.tokenEndpoint

        if (authorizationEndpoint.isNullOrBlank() || tokenEndpoint.isNullOrBlank()) {
            return driverError(work, "OIDC metadata for '${definition.discoveryUrl}' is missing required endpoints")
        }

        stateStore.put(
            OidcDriverState(
                executionId = work.executionId.value,
                nodeId = work.nodeId.value,
                methodId = definition.id.value,
                state = state,
                nonce = nonce,
                callbackRef = callbackRef,
                redirectUri = callbackRef,
                clientId = clientId,
                clientSecret = clientSecret,
                codeVerifier = pkce.codeVerifier,
                authorizationEndpoint = authorizationEndpoint,
                tokenEndpoint = tokenEndpoint,
                metadata = metadata,
                issuedAt = now,
                expiresAt = now + 10.minutes,
            ),
        )

        val url =
            buildAuthorizationUrl(
                definition = definition,
                authorizationEndpoint = authorizationEndpoint,
                clientId = clientId,
                redirectUri = callbackRef,
                state = state,
                nonce = nonce,
                codeChallenge = pkce.codeChallenge,
                codeChallengeMethod = pkce.codeChallengeMethod.name,
            )

        return Ok(PendingDispatch(RedirectAction(url = url, callbackRef = callbackRef, expiresAt = now + 10.minutes)))
    }

    override suspend fun submit(work: SubmitWork): IdkResult<SubmitOutcome, com.sphereon.identity.idv.model.IdvError> =
        Ok(SubmitFailed(driverErrorValue(work.nodeId.value, work.methodDefinition, "OIDC flow expects an authorization callback, not user input submission")))

    override suspend fun callback(work: CallbackWork): IdkResult<CallbackOutcome, com.sphereon.identity.idv.model.IdvError> {
        val definition =
            work.methodDefinition as? OidcMethodDefinition
                ?: return Ok(CallbackFailed(driverErrorValue(work.nodeId.value, work.methodDefinition, "OIDC driver received unsupported method definition")))
        val state =
            stateStore.get(work.executionId.value, work.nodeId.value)
                ?: return Ok(CallbackFailed(driverErrorValue(work.nodeId.value, definition, "No OIDC session found for callback")))

        val callbackState = work.callbackData["state"]
        if (!callbackState.isNullOrBlank() && callbackState != state.state) {
            return Ok(CallbackFailed(driverErrorValue(work.nodeId.value, definition, "OIDC state mismatch")))
        }
        val code = work.callbackData["code"]
        if (code.isNullOrBlank()) {
            val error = work.callbackData["error"] ?: "missing_authorization_code"
            return Ok(CallbackFailed(driverErrorValue(work.nodeId.value, definition, "OIDC callback failed: $error")))
        }

        val tokenResponse =
            exchangeTokenCommand
                .execute(
                    ExchangeTokenArgs(
                        tokenEndpoint = state.tokenEndpoint,
                        request =
                            TokenRequest(
                                grantType = "authorization_code",
                                code = code,
                                redirectUri = state.redirectUri,
                                codeVerifier = state.codeVerifier,
                                clientId = state.clientId,
                                clientSecret = state.clientSecret,
                                scope = definition.scopes.joinToString(" "),
                            ),
                    ),
                ).getOrElse { error ->
                    return Ok(CallbackFailed(driverErrorValue(work.nodeId.value, definition, "Token exchange failed: ${error.message.defaultMessage}")))
                }

        val idTokenClaims = tokenResponse.idToken?.let(::decodeJwtPayload).orEmpty()
        val userInfoEndpoint = state.metadata.userinfoEndpoint
        val userInfoClaims =
            if (definition.userInfoEnabled && !userInfoEndpoint.isNullOrBlank()) {
                fetchUserInfoCommand
                    .execute(
                        FetchUserInfoArgs(
                            accessToken = tokenResponse.accessToken,
                            userinfoEndpoint = userInfoEndpoint,
                        ),
                    ).getOrNull()
                    ?.claims
                    .orEmpty()
            } else {
                emptyMap()
            }

        val combinedClaims = idTokenClaims + userInfoClaims
        resolveJwks(state.metadata, combinedClaims["kid"]?.jsonPrimitive?.contentOrNull)

        val mappedAttributes = buildMappedAttributes(definition.attributeMappings, combinedClaims)
        val allAttributes =
            if (definition.userInfoEnabled) {
                mappedAttributes + buildMappedAttributes(definition.userInfoAttributeMappings, combinedClaims)
            } else {
                mappedAttributes
            }
        val identifiers = buildIdentifiers(definition.attributeMappings + definition.userInfoAttributeMappings, allAttributes, definition.assurance.maxAssurance)
        val subjectValue = extractMappedValue(definition.subjectBinding.target.attributePath, allAttributes) ?: combinedClaims["sub"]
        val resultClaims =
            if (subjectValue != null) {
                allAttributes + mapOf(definition.subjectBinding.target.attributePath to subjectValue)
            } else {
                allAttributes
            }

        val result =
            IdvNodeResult(
                nodeId = work.nodeId,
                methodId = definition.id,
                identifiers = identifiers,
                attributes = resultClaims,
                assurance = definition.assurance.maxAssurance,
                aal = definition.assurance.maxAal,
                amr = definition.assurance.amrCapabilities + AuthMethodReference.PWD,
                evidence =
                    IdvEvidence(
                        provider = definition.discoveryUrl,
                        method = "oidc",
                        timestamp = Clock.System.now(),
                        metadata =
                            buildMap {
                                put("issuer", JsonPrimitive(state.metadata.issuer))
                                put("jwks_uri", JsonPrimitive(state.metadata.jwksUri))
                                put("token_type", JsonPrimitive(tokenResponse.tokenType))
                                tokenResponse.scope?.let { put("scope", JsonPrimitive(it)) }
                                put("state", JsonPrimitive(state.state))
                            },
                        evidenceType = IdvEvidenceType.ELECTRONIC_RECORD,
                    ),
                trustFramework = definition.compliance.trustFramework,
                evidenceStrength = definition.compliance.evidenceStrength,
                proofingScenario = definition.compliance.proofingScenario,
            )

        stateStore.remove(work.executionId.value, work.nodeId.value)
        return Ok(CallbackComplete(result))
    }

    override suspend fun poll(work: PollWork): IdkResult<PollOutcome, com.sphereon.identity.idv.model.IdvError> {
        val definition =
            work.methodDefinition as? OidcMethodDefinition
                ?: return Ok(PollFailed(driverErrorValue(work.nodeId.value, work.methodDefinition, "OIDC driver received unsupported method definition")))
        stateStore.get(work.executionId.value, work.nodeId.value)
            ?: return Ok(PollFailed(driverErrorValue(work.nodeId.value, definition, "No OIDC session found to poll")))
        return Ok(PollPending(PollAction(nextCheckAt = Clock.System.now() + 15.minutes, pollIntervalMs = 5_000)))
    }

    override suspend fun cancel(work: CancelWork): IdkResult<CancelOutcome, com.sphereon.identity.idv.model.IdvError> {
        stateStore.remove(work.executionId.value, work.nodeId.value)
        return Ok(CancelAcknowledged(cleanedUp = true))
    }

    private fun resolveConfig(
        key: String,
        required: Boolean,
    ): String? {
        val value = principalConfigService.getPropertyAsString(key, null)
        return when {
            !value.isNullOrBlank() -> value
            required -> null
            else -> null
        }
    }

    private fun resolveSecret(definition: OidcMethodDefinition): String? {
        val candidates = listOfNotNull(definition.clientSecretRef.key, definition.clientSecretRef.path)
        return candidates.firstNotNullOfOrNull { principalConfigService.getPropertyAsString(it, null) }
    }

    private suspend fun resolveJwks(
        metadata: OidcDiscoveryMetadata,
        requestedKid: String?,
    ) {
        identifierService.resolve(
            ExternalIdentifierJwksUrlOpts(
                identifier = metadata.jwksUri,
                lookup =
                    com.sphereon.crypto.resolution
                        .AdditionalIdentifierLookup(kid = requestedKid),
            ),
        )
    }

    private fun buildMappedAttributes(
        mappings: List<AttributeMapping>,
        claims: Map<String, JsonElement>,
    ): Map<AttributePath, JsonElement> {
        val result = mutableMapOf<AttributePath, JsonElement>()
        mappings.forEach { mapping ->
            val value = extractValue(mapping.sourceAttribute, claims)
            if (value != null) {
                result[mapping.targetAttribute] = value
            }
        }
        return result
    }

    private fun buildIdentifiers(
        mappings: List<AttributeMapping>,
        attributes: Map<AttributePath, JsonElement>,
        assurance: com.sphereon.identity.idv.model.EidasAssuranceLevel,
    ): List<ResolvedIdentifier> =
        mappings.mapNotNull { mapping ->
            val identifierType = mapping.identifierType ?: return@mapNotNull null
            val value = extractMappedValue(mapping.targetAttribute, attributes)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ResolvedIdentifier(type = identifierType, value = value, verified = true, assurance = assurance)
        }

    private fun extractValue(
        attributePath: AttributePath,
        claims: Map<String, JsonElement>,
    ): JsonElement? {
        val direct = claims[attributePath.value]
        if (direct != null) {
            return direct
        }
        val segments = attributePath.value.split('.').filter { it.isNotBlank() }
        if (segments.isEmpty()) {
            return null
        }
        var current: JsonElement? = claims[segments.first()]
        for (segment in segments.drop(1)) {
            current =
                when (current) {
                    is JsonObject -> current[segment]
                    else -> return null
                }
        }
        return current
    }

    private fun extractMappedValue(
        attributePath: AttributePath,
        attributes: Map<AttributePath, JsonElement>,
    ): JsonElement? = attributes[attributePath]

    private fun buildAuthorizationUrl(
        definition: OidcMethodDefinition,
        authorizationEndpoint: String,
        clientId: String,
        redirectUri: String,
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
                add("scope=${urlEncode(definition.scopes.joinToString(" "))}")
                add("state=${urlEncode(state)}")
                add("nonce=${urlEncode(nonce)}")
                add("code_challenge=${urlEncode(codeChallenge)}")
                add("code_challenge_method=${urlEncode(codeChallengeMethod)}")
                definition.additionalParams.forEach { (key, value) -> add("${urlEncode(key)}=${urlEncode(value)}") }
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

    private fun decodeJwtPayload(jwt: String): Map<String, JsonElement> {
        val parts = jwt.split('.')
        if (parts.size < 2) {
            return emptyMap()
        }
        val payload = base64UrlDecode(parts[1]) ?: return emptyMap()
        return Json.parseToJsonElement(payload).jsonObject
    }

    private fun base64UrlDecode(value: String): String? =
        try {
            value.decodeFromBase64Url().decodeToString()
        } catch (_: Throwable) {
            // Ignored: base64url decoding failed
            null
        }

    private fun driverError(
        work: DispatchWork,
        message: String,
    ): IdkResult<DispatchOutcome, com.sphereon.identity.idv.model.IdvError> = Err(driverErrorValue(work.nodeId.value, work.methodDefinition, message))

    private fun driverErrorValue(
        nodeId: String,
        definition: com.sphereon.identity.idv.model.IdvMethodDefinition,
        message: String,
    ) = DriverError(
        message = message,
        nodeId =
            com.sphereon.identity.idv.model
                .IdvNodeId(nodeId),
        methodId = definition.id,
    )

    @OptIn(ExperimentalUuidApi::class)
    private fun randomId(): String = Uuid.random().toString()
}
