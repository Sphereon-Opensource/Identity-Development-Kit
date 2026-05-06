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

package com.sphereon.openid.oid4vci.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.command.CreatePkceCommand
import com.sphereon.oauth2.client.util.buildUrl
import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.holder.AuthorizationRequestResult
import com.sphereon.openid.oid4vci.holder.BuildAuthorizationRequestArgs
import com.sphereon.openid.oid4vci.holder.BuildAuthorizationRequestCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlin.random.Random

/**
 * Builds an OID4VCI Authorization Code Flow authorization request URL.
 *
 * Supports both direct redirect and PAR (Pushed Authorization Requests, RFC 9126).
 * Always generates PKCE S256 challenge/verifier pair.
 *
 * Reference: OID4VCI 1.0 Section 5 — Authorization Code Flow
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<BuildAuthorizationRequestCommand>())
class BuildAuthorizationRequestCommandImpl(
    execution: SessionExecution,
    private val createPkceCommand: CreatePkceCommand,
    private val httpClientFactory: HttpClientFactory,
) : TypedServiceCommandAdapter<BuildAuthorizationRequestArgs, AuthorizationRequestResult, IdkError>(
        commandId = BuildAuthorizationRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<BuildAuthorizationRequestArgs>(),
        outputTypeToken = typeToken<AuthorizationRequestResult>(),
    ),
    BuildAuthorizationRequestCommand {
    override val commandId: String get() = BuildAuthorizationRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is BuildAuthorizationRequestArgs

    override suspend fun doExecute(
        args: BuildAuthorizationRequestArgs,
        applyDuring: (BuildAuthorizationRequestArgs) -> BuildAuthorizationRequestArgs,
    ): IdkResult<AuthorizationRequestResult, IdkError> {
        val applied = applyDuring(args)

        // Generate PKCE
        val pkceResult = createPkceCommand.execute(CreatePkceArgs())
        if (pkceResult.isErr) {
            return Err(pkceResult.error)
        }
        val pkce = pkceResult.value

        // Generate random state
        val state = generateState()

        // Build authorization_details JSON array per RFC 9396 + OID4VCI spec
        val authorizationDetails = buildAuthorizationDetails(applied)

        val requestParams: Map<String, String?> =
            buildMap {
                put("response_type", "code")
                put("client_id", applied.clientId)
                put("redirect_uri", applied.redirectUri)
                put("state", state)
                put("code_challenge", pkce.codeChallenge)
                put("code_challenge_method", pkce.codeChallengeMethod.value)
                put("authorization_details", authorizationDetails)
                applied.scope?.let { put("scope", it) }
                applied.issuerState?.let { put("issuer_state", it) }
            }

        return if (applied.usePar) {
            val parEndpoint =
                applied.parEndpoint
                    ?: return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(message = "PAR endpoint must be provided when usePar=true"),
                    )
            buildParUrl(parEndpoint, applied.authorizationEndpoint, applied.clientId, requestParams, pkce.codeVerifier, state)
        } else {
            val authorizationUrl = buildUrl(applied.authorizationEndpoint, requestParams)
            Ok(
                AuthorizationRequestResult(
                    authorizationUrl = authorizationUrl,
                    codeVerifier = pkce.codeVerifier,
                    state = state,
                ),
            )
        }
    }

    /**
     * Builds authorization_details JSON string for the given args.
     *
     * Per OID4VCI 1.0 Section 5.1.1 and RFC 9396:
     * [{"type": "openid_credential", "credential_configuration_id": "<id>", ...}, ...]
     *
     * Includes optional `credential_identifiers` and `locations` when present in args.
     */
    private fun buildAuthorizationDetails(args: BuildAuthorizationRequestArgs): String {
        val array =
            buildJsonArray {
                for (configId in args.credentialConfigurationIds) {
                    add(
                        buildJsonObject {
                            put("type", JsonPrimitive("openid_credential"))
                            put("credential_configuration_id", JsonPrimitive(configId))
                            args.credentialIdentifiers?.get(configId)?.let { ids ->
                                putJsonArray("credential_identifiers") { ids.forEach { add(JsonPrimitive(it)) } }
                            }
                            args.locations?.let { locs ->
                                putJsonArray("locations") { locs.forEach { add(JsonPrimitive(it)) } }
                            }
                        },
                    )
                }
            }
        return Oid4vciJson.lenient.encodeToString(
            kotlinx.serialization.json.JsonArray
                .serializer(),
            array,
        )
    }

    /**
     * Generates a cryptographically random state parameter (43 chars, base64url-safe).
     */
    private fun generateState(): String {
        val randomBytes = Random.Default.nextBytes(STATE_RANDOM_BYTES)
        return randomBytes.encodeToBase64Url()
    }

    private companion object {
        private const val STATE_RANDOM_BYTES = 32
    }

    /**
     * Pushes the authorization request to the PAR endpoint and returns the redirect URL
     * with the resulting request_uri.
     */
    private suspend fun buildParUrl(
        parEndpoint: String,
        authorizationEndpoint: String,
        clientId: String,
        requestParams: Map<String, String?>,
        codeVerifier: String,
        state: String,
    ): IdkResult<AuthorizationRequestResult, IdkError> {
        val httpClient =
            try {
                httpClientFactory.createClient(HttpClientOptions.createDefault())
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to create HTTP client: ${expected.message}", throwable = expected))
            }

        return try {
            val formParameters =
                Parameters.build {
                    requestParams.forEach { (key, value) ->
                        if (value != null) {
                            append(key, value)
                        }
                    }
                }

            val response =
                httpClient.submitForm(
                    url = parEndpoint,
                    formParameters = formParameters,
                )

            if (!response.status.isSuccess()) {
                val errorBody =
                    try {
                        response.bodyAsText()
                    } catch (expected: Exception) {
                        log.debug("Failed to read PAR error response body: ${expected.message}")
                        ""
                    }
                return Err(
                    IdkError.fromString(
                        message = "PAR endpoint returned HTTP ${response.status.value}: $errorBody",
                        code = "PAR_REQUEST_FAILED",
                    ),
                )
            }

            val body = response.bodyAsText()
            val parResponse =
                try {
                    Oid4vciJson.lenient.parseToJsonElement(body) as? JsonObject
                        ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "PAR response is not a JSON object"))
                } catch (expected: Exception) {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Failed to parse PAR response JSON: ${expected.message}",
                            throwable = expected,
                        ),
                    )
                }

            val requestUri =
                parResponse["request_uri"]?.jsonPrimitive?.content
                    ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "PAR response missing 'request_uri'"))

            val authorizationUrl =
                buildUrl(
                    authorizationEndpoint,
                    mapOf("request_uri" to requestUri, "client_id" to clientId),
                )

            Ok(
                AuthorizationRequestResult(
                    authorizationUrl = authorizationUrl,
                    codeVerifier = codeVerifier,
                    state = state,
                ),
            )
        } catch (expected: Exception) {
            Err(
                IdkError.fromString(
                    message = "Network error during PAR request at $parEndpoint: ${expected.message}",
                    code = "PAR_NETWORK_ERROR",
                    exception = expected,
                ),
            )
        } finally {
            httpClient.close()
        }
    }
}
