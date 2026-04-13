package com.sphereon.oauth2.client.impl.authorization

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.ParseAuthorizationResponseArgs
import com.sphereon.oauth2.client.command.ParseAuthorizationResponseCommand
import com.sphereon.oauth2.client.command.ParsedAuthorizationResponse
import com.sphereon.oauth2.common.error.Oauth2Error
import com.sphereon.oauth2.common.model.AuthorizationErrorResponse
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.oauth2.client.util.extractQueryParameters
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

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
) : TypedServiceCommandAdapter<ParseAuthorizationResponseArgs, ParsedAuthorizationResponse>(
    commandId = ParseAuthorizationResponseCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<ParseAuthorizationResponseArgs>(),
    outputTypeToken = typeToken<ParsedAuthorizationResponse>(),
), ParseAuthorizationResponseCommand {

    override val commandId: String get() = ParseAuthorizationResponseCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ParseAuthorizationResponseArgs

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun doExecute(
        args: ParseAuthorizationResponseArgs,
        applyDuring: (ParseAuthorizationResponseArgs) -> ParseAuthorizationResponseArgs
    ): IdkResult<ParsedAuthorizationResponse, IdkError> {
        val applied = applyDuring(args)
        return parseAuthorizationResponseInternal(applied.redirectUrl).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun parseAuthorizationResponseInternal(redirectUrl: String): IdkResult<ParsedAuthorizationResponse, Oauth2Error> {
        // Extract query parameters from URL
        val params = try {
            extractQueryParameters(redirectUrl)
        } catch (e: Exception) {
            return Err(
                Oauth2Error.InvalidRequest(
                    details = listOf(
                        com.sphereon.oauth2.common.validation.ValidationErrorDetail(
                            path = "redirectUrl",
                            message = "Invalid redirect URL format: ${e.message}"
                        )
                    )
                )
            )
        }

        // Check if this is an error response (has 'error' parameter)
        return if (params.containsKey("error")) {
            parseErrorResponse(params)
        } else {
            parseSuccessResponse(params)
        }
    }

    private fun parseSuccessResponse(params: Map<String, String>): IdkResult<ParsedAuthorizationResponse, Oauth2Error> {
        val code = params["code"]
        if (code.isNullOrBlank()) {
            return Err(
                Oauth2Error.InvalidRequest(
                    details = listOf(
                        com.sphereon.oauth2.common.validation.ValidationErrorDetail(
                            path = "code",
                            message = "Authorization response must contain 'code' parameter"
                        )
                    )
                )
            )
        }

        val response = AuthorizationResponse(
            code = code,
            state = params["state"]
        )

        return Ok(ParsedAuthorizationResponse.Success(response))
    }

    private fun parseErrorResponse(params: Map<String, String>): IdkResult<ParsedAuthorizationResponse, Oauth2Error> {
        val error = params["error"]
        if (error.isNullOrBlank()) {
            return Err(
                Oauth2Error.InvalidRequest(
                    details = listOf(
                        com.sphereon.oauth2.common.validation.ValidationErrorDetail(
                            path = "error",
                            message = "Error response must contain non-empty 'error' parameter"
                        )
                    )
                )
            )
        }

        val response = AuthorizationErrorResponse(
            error = error,
            errorDescription = params["error_description"],
            errorUri = params["error_uri"],
            state = params["state"]
        )

        return Ok(ParsedAuthorizationResponse.Error(response))
    }
}
