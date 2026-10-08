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

package com.sphereon.oauth2.client.impl.authorization

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.api.validation.ValidationErrorDetail
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.AuthorizationResponseSource
import com.sphereon.oauth2.client.command.ParseAuthorizationResponseArgs
import com.sphereon.oauth2.client.command.ParseAuthorizationResponseCommand
import com.sphereon.oauth2.client.command.ParsedAuthorizationResponse
import com.sphereon.oauth2.client.util.decodeQueryParameters
import com.sphereon.oauth2.client.util.extractQueryParameters
import com.sphereon.oauth2.common.error.Oauth2Error
import com.sphereon.oauth2.common.model.AuthorizationErrorResponse
import com.sphereon.oauth2.common.model.AuthorizationResponse
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonPrimitive

/**
 * Implementation of ParseAuthorizationResponseCommand
 *
 * Parses authorization response from redirect URL query parameters.
 * Handles both success responses (with code) and error responses.
 */
@Inject
@SingleIn(SessionScope::class)
class ParseAuthorizationResponseCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<ParseAuthorizationResponseArgs, ParsedAuthorizationResponse, IdkError>(
        commandId = ParseAuthorizationResponseCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ParseAuthorizationResponseArgs>(),
        outputTypeToken = typeToken<ParsedAuthorizationResponse>(),
    ),
    ParseAuthorizationResponseCommand {
    override val commandId: String get() = ParseAuthorizationResponseCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ParseAuthorizationResponseArgs

    override suspend fun doExecute(
        args: ParseAuthorizationResponseArgs,
        applyDuring: (ParseAuthorizationResponseArgs) -> ParseAuthorizationResponseArgs,
    ): IdkResult<ParsedAuthorizationResponse, IdkError> {
        val applied = applyDuring(args)
        return parseAuthorizationResponseInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun parseAuthorizationResponseInternal(args: ParseAuthorizationResponseArgs,): IdkResult<ParsedAuthorizationResponse, Oauth2Error> {
        val params: Map<String, String> =
            when (args.source) {
                AuthorizationResponseSource.QUERY -> {
                    try {
                        extractQueryParameters(args.redirectUrl)
                    } catch (expected: Exception) {
                        return Err(invalidRequest("redirectUrl", "Invalid redirect URL format: ${expected.message}"))
                    }
                }

                AuthorizationResponseSource.FORM_POST -> {
                    val body =
                        args.formBody
                            ?: return Err(invalidRequest("formBody", "formBody is required when source=FORM_POST"))
                    try {
                        decodeQueryParameters(body)
                    } catch (expected: Exception) {
                        return Err(invalidRequest("formBody", "Invalid form-encoded body: ${expected.message}"))
                    }
                }

                AuthorizationResponseSource.FRAGMENT -> {
                    // Implicit/hybrid flows are out of scope for the first OIDF pass. Reject
                    // explicitly so callers know this isn't silently falling back to query mode.
                    return Err(
                        invalidRequest(
                            path = "source",
                            message = "Fragment response mode is not yet supported (tracked as WP5 follow-up)",
                        ),
                    )
                }
            }

        return if (params.containsKey("error")) {
            parseErrorResponse(params, args)
        } else {
            parseSuccessResponse(params, args)
        }
    }

    private fun parseSuccessResponse(
        params: Map<String, String>,
        args: ParseAuthorizationResponseArgs,
    ): IdkResult<ParsedAuthorizationResponse, Oauth2Error> {
        val code = params["code"]
        if (code.isNullOrBlank()) {
            return Err(invalidRequest("code", "Authorization response must contain 'code' parameter"))
        }

        validateBindings(params, args)?.let { return Err(it) }

        val response =
            AuthorizationResponse(
                code = code,
                state = params["state"],
                additionalParameters = params.toAdditionalParameters(SUCCESS_PARAMETERS),
            )

        return Ok(ParsedAuthorizationResponse.Success(response))
    }

    private fun parseErrorResponse(
        params: Map<String, String>,
        args: ParseAuthorizationResponseArgs,
    ): IdkResult<ParsedAuthorizationResponse, Oauth2Error> {
        val error = params["error"]
        if (error.isNullOrBlank()) {
            return Err(invalidRequest("error", "Error response must contain non-empty 'error' parameter"))
        }

        validateBindings(params, args)?.let { return Err(it) }

        val response =
            AuthorizationErrorResponse(
                error = error,
                errorDescription = params["error_description"],
                errorUri = params["error_uri"],
                state = params["state"],
                additionalParameters = params.toAdditionalParameters(ERROR_PARAMETERS),
            )

        return Ok(ParsedAuthorizationResponse.Error(response))
    }

    private fun validateBindings(
        params: Map<String, String>,
        args: ParseAuthorizationResponseArgs,
    ): Oauth2Error.InvalidGrant? {
        args.expectedState?.let { expected ->
            if (params["state"] != expected) {
                return Oauth2Error.InvalidGrant(reason = "Authorization response state mismatch")
            }
        }

        val issuer = params["iss"]?.takeIf(String::isNotBlank)
        if (args.requireIssuer && issuer == null) {
            return Oauth2Error.InvalidGrant(reason = "Authorization response missing RFC 9207 issuer")
        }
        args.expectedIssuer?.let { expected ->
            if (issuer != null && issuer != expected) {
                return Oauth2Error.InvalidGrant(reason = "Authorization response issuer mismatch")
            }
        }
        return null
    }

    private fun Map<String, String>.toAdditionalParameters(standardParameters: Set<String>) =
        filterKeys { it !in standardParameters }.mapValues { JsonPrimitive(it.value) }

    private fun invalidRequest(
        path: String,
        message: String,
    ): Oauth2Error.InvalidRequest =
        Oauth2Error.InvalidRequest(
            details = listOf(ValidationErrorDetail(path = path, message = message)),
        )

    private companion object {
        val SUCCESS_PARAMETERS = setOf("code", "state")
        val ERROR_PARAMETERS = setOf("error", "error_description", "error_uri", "state")
    }
}
