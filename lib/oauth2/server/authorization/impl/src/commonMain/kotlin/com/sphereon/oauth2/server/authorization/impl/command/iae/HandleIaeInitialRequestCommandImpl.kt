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

package com.sphereon.oauth2.server.authorization.impl.command.iae

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.ParseJarArgs
import com.sphereon.oauth2.client.command.ParseJarCommand
import com.sphereon.oauth2.server.authorization.command.HandleIaeInitialRequestArgs
import com.sphereon.oauth2.server.authorization.command.HandleIaeInitialRequestCommand
import com.sphereon.oauth2.server.authorization.command.IaeResult
import com.sphereon.oauth2.server.authorization.impl.store.KvIaeSessionStore
import com.sphereon.oauth2.server.authorization.model.IaeErrorResponse
import com.sphereon.oauth2.server.authorization.model.IaeErrors
import com.sphereon.oauth2.server.authorization.model.IaeInteractionRequiredResponse
import com.sphereon.oauth2.server.authorization.model.IaeInteractionTypes
import com.sphereon.oauth2.server.authorization.model.IaeSession
import com.sphereon.oauth2.server.authorization.model.IaeSessionStatus
import com.sphereon.oauth2.server.authorization.storage.IaeSessionStore
import com.sphereon.openid.oid4vci.issuer.config.CredentialIssuancePolicyResolver
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.verifier.Oid4vpVerifierService
import dev.whyoleg.cryptography.random.CryptographyRandom
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of [HandleIaeInitialRequestCommand].
 *
 * Processes the initial IAE authorization request (OID4VCI 1.1 Section 6.2):
 * 1. Validates response_type == "code"
 * 2. Validates interactionTypesSupported is non-empty
 * 3. Determines the required interaction type (issuer policy; defaults to OPENID4VP_PRESENTATION)
 * 4. Checks wallet supports the required type
 * 5. Generates authSession token and vpNonce
 * 6. Creates and stores [IaeSession]
 * 7. Builds and returns [IaeResult.InteractionRequired]
 *
 * Registered via [com.sphereon.oauth2.server.authorization.impl.command.OAuth2AuthServerCommandDescriptors].
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("HandleIaeInitialRequestCommandImpl", exact = true)
class HandleIaeInitialRequestCommandImpl(
    execution: SessionExecution,
    private val iaeSessionStore: IaeSessionStore,
    private val parseJarCommand: ParseJarCommand? = null,
    private val verifierService: Oid4vpVerifierService? = null,
    private val policyResolver: CredentialIssuancePolicyResolver? = null,
) : TypedServiceCommandAdapter<HandleIaeInitialRequestArgs, IaeResult, IdkError>(
        commandId = HandleIaeInitialRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleIaeInitialRequestArgs>(),
        outputTypeToken = typeToken<IaeResult>(),
    ),
    HandleIaeInitialRequestCommand {
    override val commandId: String get() = HandleIaeInitialRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is HandleIaeInitialRequestArgs

    override suspend fun doExecute(
        args: HandleIaeInitialRequestArgs,
        applyDuring: (HandleIaeInitialRequestArgs) -> HandleIaeInitialRequestArgs,
    ): IdkResult<IaeResult, IdkError> {
        val applied = applyDuring(args)

        // RFC 9101: if a `request` JWT is present, parse it and let its claims override form params.
        val jarToken = applied.request
        val resolved =
            if (jarToken != null) {
                val jarArgs =
                    ParseJarArgs(
                        jarToken = jarToken,
                        issuer = applied.clientId,
                        // Audience validation is delegated to ParseJarCommand; pass empty string when no
                        // issuer identifier is available at this layer so the command can handle it.
                        audience = "",
                    )
                val jarCommand =
                    parseJarCommand
                        ?: return Ok(
                            IaeResult.Error(
                                IaeErrorResponse(
                                    error = IaeErrors.INVALID_REQUEST,
                                    errorDescription = "JAR (request parameter) is not supported by this server",
                                ),
                            ),
                        )

                val parseResult = jarCommand.execute(jarArgs)
                if (parseResult.isErr) {
                    return Ok(
                        IaeResult.Error(
                            IaeErrorResponse(
                                error = IaeErrors.INVALID_REQUEST,
                                errorDescription = "Invalid request object: ${parseResult.error.message.defaultMessage}",
                            ),
                        ),
                    )
                }

                val parsed = parseResult.value
                val authReq = parsed.authorizationRequest

                // Extract interaction_types_supported from additional parameters in the JWT.
                // It may be a comma-separated string or a JSON array stored as an additional claim.
                val jarInteractionTypes: List<String>? =
                    authReq.additionalParameters["interaction_types_supported"]
                        ?.let { element ->
                            when (element) {
                                is JsonArray -> {
                                    element.map { it.jsonPrimitive.content }.filter { it.isNotBlank() }
                                }

                                is JsonPrimitive -> {
                                    element.content
                                        .split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                }

                                else -> {
                                    null
                                }
                            }
                        }
                        ?: parsed.claims["interaction_types_supported"]?.let { raw ->
                            when (raw) {
                                is String -> raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                is List<*> -> raw.filterIsInstance<String>().filter { it.isNotBlank() }
                                else -> null
                            }
                        }

                // JAR params override individually supplied form params (RFC 9101 Section 4).
                applied.copy(
                    clientId = authReq.clientId,
                    responseType = authReq.responseType.takeIf { it.isNotBlank() } ?: applied.responseType,
                    redirectUri = authReq.redirectUri ?: applied.redirectUri,
                    scope = authReq.scope ?: applied.scope,
                    codeChallenge = authReq.codeChallenge ?: applied.codeChallenge,
                    codeChallengeMethod = authReq.codeChallengeMethod ?: applied.codeChallengeMethod,
                    issuerState = authReq.issuerState ?: applied.issuerState,
                    interactionTypesSupported = jarInteractionTypes ?: applied.interactionTypesSupported,
                )
            } else {
                applied
            }

        return executeInternal(resolved)
    }

    private suspend fun executeInternal(args: HandleIaeInitialRequestArgs): IdkResult<IaeResult, IdkError> {
        // Step 1: Validate response_type
        if (args.responseType != "code") {
            return Ok(
                IaeResult.Error(
                    IaeErrorResponse(
                        error = IaeErrors.INVALID_REQUEST,
                        errorDescription = "response_type must be 'code', got '${args.responseType}'",
                    ),
                ),
            )
        }

        // Step 2: Validate interactionTypesSupported is non-empty
        if (args.interactionTypesSupported.isEmpty()) {
            return Ok(
                IaeResult.Error(
                    IaeErrorResponse(
                        error = IaeErrors.MISSING_INTERACTION_TYPE,
                        errorDescription = "interaction_types_supported must not be empty",
                    ),
                ),
            )
        }

        // Step 3: Determine required interaction type from per-credential policy (default OPENID4VP_PRESENTATION)
        val requiredInteractionType = determineRequiredInteractionType(args)

        // Step 3b: Reject VP interaction when OID4VP verifier is not wired
        if (requiredInteractionType == IaeInteractionTypes.OPENID4VP_PRESENTATION && verifierService == null) {
            return Ok(
                IaeResult.Error(
                    IaeErrorResponse(
                        error = IaeErrors.INVALID_REQUEST,
                        errorDescription =
                            "IAE interaction type openid4vp_presentation requires a configured OID4VP verifier service, but none is available. " +
                                "Either configure a verifier service or change the IAE interaction type.",
                    ),
                ),
            )
        }

        // Step 4: Check wallet supports the required type
        if (!args.interactionTypesSupported.contains(requiredInteractionType)) {
            return Ok(
                IaeResult.Error(
                    IaeErrorResponse(
                        error = IaeErrors.MISSING_INTERACTION_TYPE,
                        errorDescription = "Wallet does not support required interaction type: $requiredInteractionType",
                    ),
                ),
            )
        }

        // Step 5: Generate unique authSession token and vpNonce
        val authSession = generateToken("iae")
        val vpNonce = generateToken("nonce")
        val sessionId = generateToken("session")

        // Step 6: Build interaction-specific data before persisting the session,
        // so that verifier session IDs can be captured up front.
        data class VpRequestData(
            val openid4vpRequest: JsonObject,
            val vpSessionId: String?,
        )

        val dcqlQueryId = resolveDcqlQueryId(args)
        val vpRequestData: VpRequestData? =
            when (requiredInteractionType) {
                IaeInteractionTypes.OPENID4VP_PRESENTATION -> {
                    val pair =
                        buildOpenid4vpRequest(
                            nonce = vpNonce,
                            clientId = args.clientId,
                            responseUri = args.redirectUri,
                            dcqlQueryId = dcqlQueryId,
                        )
                    if (pair == null) {
                        return Ok(
                            IaeResult.Error(
                                IaeErrorResponse(
                                    error = IaeErrors.INVALID_REQUEST,
                                    errorDescription = "Failed to build OpenID4VP authorization request",
                                ),
                            ),
                        )
                    }
                    VpRequestData(openid4vpRequest = pair.first, vpSessionId = pair.second)
                }

                else -> {
                    null
                }
            }

        // Step 7: Create IaeSession and store it
        val now = Clock.System.now()
        val expiresAt = now + KvIaeSessionStore.SESSION_TTL.seconds
        val session =
            IaeSession(
                sessionId = sessionId,
                authSession = authSession,
                clientId = args.clientId,
                redirectUri = args.redirectUri,
                responseType = args.responseType,
                scope = args.scope,
                authorizationDetails = args.authorizationDetails,
                codeChallenge = args.codeChallenge,
                codeChallengeMethod = args.codeChallengeMethod,
                interactionTypesSupported = args.interactionTypesSupported,
                status = IaeSessionStatus.INTERACTION_REQUIRED,
                currentInteractionType = requiredInteractionType,
                vpNonce = vpNonce,
                vpSessionId = vpRequestData?.vpSessionId,
                createdAt = now.epochSeconds,
                expiresAt = expiresAt.epochSeconds,
            )

        val storeResult = iaeSessionStore.create(session)
        if (storeResult.isErr) {
            return Err(storeResult.error)
        }

        // Step 8: Build wire response based on interaction type
        val response =
            when (requiredInteractionType) {
                IaeInteractionTypes.OPENID4VP_PRESENTATION -> {
                    IaeInteractionRequiredResponse(
                        type = requiredInteractionType,
                        authSession = authSession,
                        openid4vpRequest = vpRequestData!!.openid4vpRequest,
                    )
                }

                IaeInteractionTypes.REDIRECT_TO_WEB -> {
                    val requestUri = "urn:ietf:params:oauth:request_uri:${generateToken("req")}"
                    IaeInteractionRequiredResponse(
                        type = requiredInteractionType,
                        authSession = authSession,
                        requestUri = requestUri,
                        expiresIn = REDIRECT_URI_EXPIRES_IN_SECONDS,
                    )
                }

                else -> {
                    IaeInteractionRequiredResponse(
                        type = requiredInteractionType,
                        authSession = authSession,
                    )
                }
            }

        return Ok(IaeResult.InteractionRequired(response))
    }

    /**
     * Extracts the first `credential_configuration_id` from [args].authorizationDetails and
     * uses [policyResolver] to determine the required IAE interaction type.
     *
     * Falls back to [IaeInteractionTypes.OPENID4VP_PRESENTATION] when:
     * - [policyResolver] is not injected, or
     * - no `credential_configuration_id` can be extracted from the authorization_details, or
     * - IAE is not enabled in the resolved policy.
     */
    private suspend fun determineRequiredInteractionType(args: HandleIaeInitialRequestArgs): String {
        if (policyResolver == null) {
            return IaeInteractionTypes.OPENID4VP_PRESENTATION
        }

        val configId = extractFirstCredentialConfigurationId(args) ?: return IaeInteractionTypes.OPENID4VP_PRESENTATION
        val policy = policyResolver.resolve(configId)

        return if (policy.iaeEnabled) {
            policy.iaeInteractionType
        } else {
            IaeInteractionTypes.OPENID4VP_PRESENTATION
        }
    }

    /**
     * Resolves the DCQL query ID from per-credential policy for the first credential
     * configuration ID found in [args].authorizationDetails. Returns `null` when no
     * policy resolver is present or no ID is configured.
     */
    private suspend fun resolveDcqlQueryId(args: HandleIaeInitialRequestArgs): String? {
        if (policyResolver == null) {
            return null
        }
        val configId = extractFirstCredentialConfigurationId(args) ?: return null
        return policyResolver.resolve(configId).iaeDcqlQueryId
    }

    /**
     * Extracts the `credential_configuration_id` from the first element of
     * [HandleIaeInitialRequestArgs.authorizationDetails] that is a JSON object containing that key.
     */
    private fun extractFirstCredentialConfigurationId(args: HandleIaeInitialRequestArgs): String? =
        args.authorizationDetails
            ?.asSequence()
            ?.mapNotNull { element ->
                runCatching { element.jsonObject["credential_configuration_id"]?.jsonPrimitive?.contentOrNull }
                    .getOrNull()
            }?.firstOrNull()

    /**
     * Builds the OpenID4VP authorization request to embed in the IAE interaction response.
     *
     * When a [verifierService] is injected, delegates to
     * [Oid4vpVerifierService.createAuthorizationRequest] so that the verifier creates a proper
     * session with DCQL query and stores it for later validation.  The resulting
     * [AuthorizationRequest] is serialized to a [JsonObject] so it can be sent to the wallet.
     *
     * When no verifier is available, falls back to a minimal hardcoded JsonObject that contains
     * only the mandatory fields (`response_type`, `response_mode`, `nonce`).
     *
     * @param nonce The `vpNonce` bound to this IAE session — MUST appear in the VP response.
     * @param clientId The AS client_id, used as `client_id` in the VP request.
     * @param responseUri The URI where the wallet should POST the VP response.
     * @param dcqlQueryId Optional DCQL query ID resolved from per-credential policy.
     *   When non-null and a [verifierService] is available, the query ID is used as the credential
     *   query ID so the verifier can look up a named query definition.
     *   When null, a minimal "accept any credential" query is built inline.
     * @return A pair of the serialized request [JsonObject] and the verifier session ID, or
     *   `null` if the verifier service returns an error.
     */
    private suspend fun buildOpenid4vpRequest(
        nonce: String,
        clientId: String,
        responseUri: String,
        dcqlQueryId: String? = null,
    ): Pair<JsonObject, String?>? {
        if (verifierService != null) {
            // Build a DCQL query for the IAE round-trip.
            // When a dcqlQueryId is configured in policy, prefer a named credential query so that
            // the verifier can resolve a pre-configured query definition. Otherwise, fall back to
            // a minimal "accept any credential" query for generic IAE support.
            val dcqlQuery =
                DcqlQuery(
                    credentials =
                        listOf(
                            DcqlCredentialQuery(id = dcqlQueryId ?: "iae_credential"),
                        ),
                )

            val vpRequestResult =
                verifierService.createAuthorizationRequest(
                    CreateAuthorizationRequestArgs(
                        dcqlQuery = dcqlQuery,
                        clientId = clientId,
                        responseUri = responseUri,
                        responseMode = ResponseMode.IAE_POST,
                        nonce = nonce,
                    ),
                )

            if (vpRequestResult.isErr) {
                return null
            }

            val created = vpRequestResult.value
            val authRequest = created.request

            // Serialize AuthorizationRequest to JsonObject for the wallet.
            val jsonObject =
                json.encodeToJsonElement(authRequest).let {
                    it as? JsonObject ?: JsonObject(emptyMap())
                }

            return Pair(jsonObject, created.sessionId)
        }

        // Fallback: minimal VP request without verifier session (no real validation will occur).
        val fallback =
            JsonObject(
                mapOf(
                    "response_type" to JsonPrimitive("vp_token"),
                    "response_mode" to JsonPrimitive(ResponseMode.IAE_POST.value),
                    "nonce" to JsonPrimitive(nonce),
                ),
            )
        return Pair(fallback, null)
    }

    /**
     * Generates a short opaque token using timestamp + random bytes encoded as base64url.
     */
    private fun generateToken(prefix: String): String {
        val randomBytes = CryptographyRandom.nextBytes(TOKEN_RANDOM_BYTES)
        return "${prefix}_${randomBytes.encodeToBase64Url()}"
    }

    companion object {
        private const val TOKEN_RANDOM_BYTES = 24
        private const val REDIRECT_URI_EXPIRES_IN_SECONDS = 60

        private val json =
            kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            }
    }
}
