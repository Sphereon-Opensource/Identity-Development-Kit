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
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.holder.IaeHolderResult
import com.sphereon.openid.oid4vci.holder.InitiateIaeArgs
import com.sphereon.openid.oid4vci.holder.InitiateIaeCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Submits the initial IAE request to the AS IAE endpoint.
 *
 * OID4VCI 1.1 Section 6.1.1 — Initial Request
 *
 * POSTs form-encoded parameters to the IAE endpoint and maps the JSON response
 * to [IaeHolderResult].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<InitiateIaeCommand>())
class InitiateIaeCommandImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
) : TypedServiceCommandAdapter<InitiateIaeArgs, IaeHolderResult, IdkError>(
        commandId = InitiateIaeCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<InitiateIaeArgs>(),
        outputTypeToken = typeToken<IaeHolderResult>(),
    ),
    InitiateIaeCommand {
    override val commandId: String get() = InitiateIaeCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is InitiateIaeArgs

    override suspend fun doExecute(
        args: InitiateIaeArgs,
        applyDuring: (InitiateIaeArgs) -> InitiateIaeArgs,
    ): IdkResult<IaeHolderResult, IdkError> {
        val applied = applyDuring(args)

        log.debug("Submitting IAE initial request to: ${applied.iaeEndpoint}")

        val httpClient =
            try {
                httpClientFactory.createClient(HttpClientOptions.createDefault())
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to create HTTP client: ${expected.message}", throwable = expected))
            }

        return try {
            val response =
                httpClient.submitForm(
                    url = applied.iaeEndpoint,
                    formParameters =
                        Parameters.build {
                            append("response_type", "code")
                            append("client_id", applied.clientId)
                            append("redirect_uri", applied.redirectUri)
                            append("interaction_types_supported", applied.interactionTypesSupported.joinToString(","))
                            applied.authorizationDetails?.let { details ->
                                append("authorization_details", "[${details.joinToString(",")}]")
                            }
                            applied.scope?.let { append("scope", it) }
                            applied.codeChallenge?.let { append("code_challenge", it) }
                            applied.codeChallengeMethod?.let { append("code_challenge_method", it) }
                        },
                )

            val responseText =
                try {
                    response.bodyAsText()
                } catch (expected: Exception) {
                    return Err(IdkError.fromString(message = "Failed to read IAE response body: ${expected.message}", code = "IAE_RESPONSE_READ_ERROR", exception = expected))
                }

            val body =
                try {
                    Oid4vciJson.lenient.parseToJsonElement(responseText).jsonObject
                } catch (expected: Exception) {
                    return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "IAE response is not valid JSON: ${expected.message}", throwable = expected))
                }

            // Check for error field first (error response)
            val errorField = body["error"]?.jsonPrimitive?.contentOrNull
            if (errorField != null) {
                val errorDescription = body["error_description"]?.jsonPrimitive?.contentOrNull
                return Ok(IaeHolderResult.Error(error = errorField, errorDescription = errorDescription))
            }

            // Determine result type from status field
            val status = body["status"]?.jsonPrimitive?.contentOrNull
            when (status) {
                "ok" -> {
                    val code =
                        body["code"]?.jsonPrimitive?.contentOrNull
                            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "IAE authorization code response missing 'code' field"))
                    Ok(IaeHolderResult.AuthorizationCode(code = code))
                }

                "require_interaction" -> {
                    val type =
                        body["type"]?.jsonPrimitive?.contentOrNull
                            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "IAE interaction required response missing 'type' field"))
                    val authSession =
                        body["auth_session"]?.jsonPrimitive?.contentOrNull
                            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "IAE interaction required response missing 'auth_session' field"))
                    val openid4vpRequest = body["openid4vp_request"]?.jsonObject
                    val requestUri = body["request_uri"]?.jsonPrimitive?.contentOrNull
                    val expiresIn = body["expires_in"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    Ok(
                        IaeHolderResult.InteractionRequired(
                            type = type,
                            authSession = authSession,
                            openid4vpRequest = openid4vpRequest,
                            requestUri = requestUri,
                            expiresIn = expiresIn,
                        ),
                    )
                }

                else -> {
                    Err(IdkError.fromString(message = "Unexpected IAE response status: $status", code = "IAE_UNEXPECTED_STATUS"))
                }
            }
        } catch (expected: Exception) {
            Err(
                IdkError.fromString(
                    message = "Network error during IAE initial request at ${applied.iaeEndpoint}: ${expected.message}",
                    code = "IAE_NETWORK_ERROR",
                    exception = expected,
                ),
            )
        } finally {
            httpClient.close()
        }
    }
}
