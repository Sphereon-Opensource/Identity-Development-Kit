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
import com.sphereon.openid.oid4vci.holder.FollowUpIaeArgs
import com.sphereon.openid.oid4vci.holder.FollowUpIaeCommand
import com.sphereon.openid.oid4vci.holder.IaeHolderResult
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Submits an IAE follow-up request to the AS IAE endpoint.
 *
 * OID4VCI 1.1 Section 6.3 — Follow-up Request
 *
 * POSTs form-encoded parameters to the IAE endpoint and maps the JSON response
 * to [IaeHolderResult].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FollowUpIaeCommand>())
class FollowUpIaeCommandImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
) : TypedServiceCommandAdapter<FollowUpIaeArgs, IaeHolderResult>(
        commandId = FollowUpIaeCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<FollowUpIaeArgs>(),
        outputTypeToken = typeToken<IaeHolderResult>(),
    ),
    FollowUpIaeCommand {
    override val commandId: String get() = FollowUpIaeCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is FollowUpIaeArgs

    override suspend fun doExecute(
        args: FollowUpIaeArgs,
        applyDuring: (FollowUpIaeArgs) -> FollowUpIaeArgs,
    ): IdkResult<IaeHolderResult, IdkError> {
        val applied = applyDuring(args)

        log.debug("Submitting IAE follow-up to: ${applied.iaeEndpoint}")

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
                            append("auth_session", applied.authSession)
                            applied.openid4vpResponse?.let { vp ->
                                append("openid4vp_response", vp.toString())
                            }
                            applied.codeVerifier?.let { append("code_verifier", it) }
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
                    message = "Network error during IAE follow-up at ${applied.iaeEndpoint}: ${expected.message}",
                    code = "IAE_NETWORK_ERROR",
                    exception = expected,
                ),
            )
        } finally {
            httpClient.close()
        }
    }
}
