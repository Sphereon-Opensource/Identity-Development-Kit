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

package com.sphereon.crypto.kms.rest.api.client

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.error.RestErrorBody
import com.sphereon.core.api.http.error.RestErrorDetail
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyInput
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyOutput
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GenerateKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GetKeyInput
import com.sphereon.crypto.kms.rest.api.command.GetKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ImportKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ListKeysInput
import com.sphereon.crypto.kms.rest.api.command.ListKeysServiceCommand
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyGlobal
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.GetKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ImportKey
import com.sphereon.crypto.kms.rest.api.generated.models.ImportKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ListKeysResponse
import com.sphereon.di.session.SessionContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.seconds

/**
 * HTTP-based implementation of [ServiceCommandTransport] for IDK's KMS REST client.
 *
 * This transport is used by IDK-only consumers to talk to a KMS REST API.
 * HTTP method and path are resolved from the command's [PublicApiCommand] interface
 * via a command metadata registry.
 *
 * EDK consumers should NOT use this transport — use EDK's routed transport instead.
 */
class HttpServiceCommandTransport(
    private val baseUrl: String,
    private val commandRoutes: Map<String, Pair<String, String>> = emptyMap(),
    private val tenantHeaderName: String = "X-Tenant-ID",
    private val principalHeaderName: String = "X-User-ID",
) : KmsCommandTransport {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val httpClient =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json)
            }
        }

    override fun bindSession(runtimeSessionContext: SessionContext): SessionBoundKmsCommandTransport {
        val requestExecutor = SessionBoundRequestExecutor(runtimeSessionContext)
        return object : SessionBoundKmsCommandTransport {
            override suspend fun <T : Any> invoke(
                commandId: String,
                input: Any,
                outputTypeToken: TypeToken<T>,
            ): IdkResult<T, IdkError> = invokeInternal(commandId, input, outputTypeToken, requestExecutor)
        }
    }

    override suspend fun <T : Any> invoke(
        commandId: String,
        input: Any,
        sessionContext: SessionContext,
        outputTypeToken: TypeToken<T>,
    ): IdkResult<T, IdkError> {
        val requestExecutor = SessionBoundRequestExecutor(sessionContext)
        return invokeInternal(commandId, input, outputTypeToken, requestExecutor)
    }

    private suspend fun <T : Any> invokeInternal(
        commandId: String,
        input: Any,
        outputTypeToken: TypeToken<T>,
        requestExecutor: SessionBoundRequestExecutor,
    ): IdkResult<T, IdkError> {
        val route =
            commandRoutes[commandId]
                ?: return Err(IdkError.INVALID_STATE(message = "No route registered for command $commandId"))
        val httpMethod = route.first
        val pathTemplate = route.second

        // Build URL with path parameter substitution
        val url = buildUrl(commandId, pathTemplate, input)

        try {
            val response =
                when (httpMethod.uppercase()) {
                    "GET" -> requestExecutor.executeGet(commandId, url, pathTemplate, input)
                    "POST" -> requestExecutor.executePost(commandId, url, input)
                    "PUT" -> requestExecutor.executePut(commandId, url, input)
                    "DELETE" -> requestExecutor.executeDelete(commandId, url, pathTemplate, input)
                    else -> return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported HTTP method: $httpMethod"))
                }

            val statusCode = response.status.value
            val responseBody = response.bodyAsText()

            if (statusCode >= 400) {
                return parseErrorResponse(statusCode, responseBody, response.headers[HttpHeaders.RetryAfter])
            }

            if (responseBody.isBlank()) {
                val synthesized = synthesizeEmptyResponse(input, outputTypeToken)
                if (synthesized != null) {
                    return Ok(synthesized)
                }
                return Err(IdkError.INVALID_STATE(message = "Empty response body for command $commandId"))
            }

            return Ok(decodeResponse(commandId, responseBody))
        } catch (expected: Exception) {
            return Err(
                IdkError.UNKNOWN_ERROR(
                    message = "HTTP request failed for command $commandId: ${expected.message}",
                    exception = expected,
                ),
            )
        }
    }

    /**
     * Builds the full URL by substituting path parameters from the input.
     *
     * Path parameters like {aliasOrKid} are replaced with values from the input object.
     */
    private fun buildUrl(
        commandId: String,
        pathTemplate: String,
        input: Any,
    ): String {
        var path = pathTemplate

        // Extract path parameters using reflection or serialization
        val inputJson = encodeToJson(commandId, input)
        val inputMap = json.decodeFromString<Map<String, JsonElement>>(inputJson)

        // Replace path parameters like {aliasOrKid} with actual values
        val pathParamRegex = "\\{([^}]+)\\}".toRegex()
        pathParamRegex.findAll(pathTemplate).forEach { match ->
            val paramName = match.groupValues[1]
            val value =
                inputMap[paramName]?.let {
                    when (it) {
                        is JsonPrimitive -> it.content
                        else -> it.toString()
                    }
                }
            if (value != null) {
                path = path.replace("{$paramName}", value)
            }
        }

        return "$baseUrl$path"
    }

    private inner class SessionBoundRequestExecutor(
        private val sessionContext: SessionContext,
    ) {
        suspend fun executeGet(
            commandId: String,
            url: String,
            pathTemplate: String,
            input: Any,
        ): HttpResponse {
            // Extract query parameters from input (fields not in path)
            val queryParams = extractQueryParams(commandId, input, pathTemplate)

            return httpClient.get(url) {
                addAuthHeaders()
                queryParams.forEach { (key, value) ->
                    parameter(key, value)
                }
            }
        }

        suspend fun executePost(
            commandId: String,
            url: String,
            input: Any,
        ): HttpResponse =
            httpClient.post(url) {
                addAuthHeaders()
                contentType(ContentType.Application.Json)
                setBody(extractRequestBody(commandId, input))
            }

        suspend fun executePut(
            commandId: String,
            url: String,
            input: Any,
        ): HttpResponse =
            httpClient.put(url) {
                addAuthHeaders()
                contentType(ContentType.Application.Json)
                setBody(extractRequestBody(commandId, input))
            }

        suspend fun executeDelete(
            commandId: String,
            url: String,
            pathTemplate: String,
            input: Any,
        ): HttpResponse {
            val queryParams = extractQueryParams(commandId, input, pathTemplate)

            return httpClient.delete(url) {
                addAuthHeaders()
                queryParams.forEach { (key, value) ->
                    parameter(key, value)
                }
            }
        }

        private fun io.ktor.client.request.HttpRequestBuilder.addAuthHeaders() {
            val tenantId = sessionContext.context.tenant.tenantId
            if (tenantId.isNotBlank() && tenantId != "<anonymous>") {
                header(tenantHeaderName, tenantId)
            }

            val principal = sessionContext.context.principal?.toString()
            if (!principal.isNullOrBlank() && principal != "<anonymous>") {
                header(principalHeaderName, principal)
            }

            // Add JWT token if available from secure details
            sessionContext.context.secureDetails?.jwt?.let { jwt ->
                if (jwt.isNotBlank()) {
                    header("Authorization", "Bearer $jwt")
                }
            }
        }
    }

    /**
     * Extracts query parameters from input, excluding path parameters.
     */
    private fun extractQueryParams(
        commandId: String,
        input: Any,
        pathTemplate: String,
    ): Map<String, String> {
        val inputJson = encodeToJson(commandId, input)
        val inputMap = json.decodeFromString<Map<String, JsonElement>>(inputJson)

        // Find which params are already in the path
        val pathParams = "\\{([^}]+)\\}".toRegex().findAll(pathTemplate).map { it.groupValues[1] }.toSet()

        // Return non-path params as query params (only non-null primitives)
        return inputMap.entries
            .filter { it.key !in pathParams }
            .mapNotNull { (key, value) ->
                when (value) {
                    is JsonNull -> null
                    is JsonPrimitive -> key to value.content
                    else -> null // Skip complex objects
                }
            }.toMap()
    }

    /**
     * Extracts the request body for POST/PUT requests.
     *
     * KMS command inputs are the wire bodies directly; they are not wrapped in
     * command envelopes.
     */
    private fun extractRequestBody(
        commandId: String,
        input: Any,
    ): String = encodeToJson(commandId, input)

    private fun <T : Any> parseErrorResponse(
        statusCode: Int,
        responseBody: String,
        retryAfterHeader: String? = null,
    ): IdkResult<T, IdkError> {
        val restError = parseRestError(responseBody)
        val message = restError?.message ?: responseBody.ifEmpty { defaultMessageForStatus(statusCode) }
        val error =
            when (statusCode) {
                400 -> IdkError.ILLEGAL_ARGUMENT_ERROR(message = message)
                401 -> IdkError.UNAUTHORIZED_ERROR(message = message)
                403 -> IdkError.FORBIDDEN_ERROR(message = message)
                404 -> IdkError.NOT_FOUND_ERROR(message = message)
                409 -> IdkError.INVALID_STATE(message = message)
                429 -> IdkError.QUOTA_EXCEEDED_ERROR(message = message)
                503 -> IdkError.SERVICE_UNAVAILABLE_ERROR(message = message, retryAfter = retryAfterHeader.retryAfterSeconds())
                504 -> IdkError.TIMEOUT_ERROR(message = message)
                else -> IdkError.UNKNOWN_ERROR(message = "HTTP $statusCode: $responseBody")
            }
        return Err(error)
    }

    private fun parseRestError(responseBody: String): RestErrorDetail? =
        runCatching { json.decodeFromString(RestErrorBody.serializer(), responseBody).error }.getOrNull()

    private fun String?.retryAfterSeconds() = this?.toLongOrNull()?.coerceAtLeast(1)?.seconds

    private fun defaultMessageForStatus(statusCode: Int): String =
        when (statusCode) {
            400 -> "Bad request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not found"
            409 -> "Conflict"
            429 -> "Too many requests"
            503 -> "Service temporarily unavailable"
            504 -> "Gateway timeout"
            else -> "HTTP $statusCode"
        }

    /**
     * Closes the HTTP client.
     */
    fun close() {
        httpClient.close()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> synthesizeEmptyResponse(
        input: Any,
        outputTypeToken: TypeToken<T>,
    ): T? {
        val outputClassifier = outputTypeToken.kType.classifier
        return when {
            outputClassifier == DeleteKeyOutput::class && input is DeleteKeyInput -> {
                DeleteKeyOutput(aliasOrKid = input.aliasOrKid, deleted = true) as T
            }

            else -> {
                null
            }
        }
    }

    private fun encodeToJson(
        commandId: String,
        input: Any,
    ): String =
        when (commandId) {
            GenerateKeyServiceCommand.COMMAND_ID -> json.encodeToString(input as GenerateKeyGlobal)
            GetKeyServiceCommand.COMMAND_ID -> json.encodeToString(input as GetKeyInput)
            ListKeysServiceCommand.COMMAND_ID -> json.encodeToString(input as ListKeysInput)
            DeleteKeyServiceCommand.COMMAND_ID -> json.encodeToString(input as DeleteKeyInput)
            ImportKeyServiceCommand.COMMAND_ID -> json.encodeToString(input as ImportKey)
            else -> {
                throw IllegalStateException(
                    "No compile-time serializer registered for command $commandId input",
                )
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> decodeResponse(
        commandId: String,
        responseBody: String,
    ): T =
        when (commandId) {
            GenerateKeyServiceCommand.COMMAND_ID -> json.decodeFromString<GenerateKeyResponse>(responseBody) as T
            GetKeyServiceCommand.COMMAND_ID -> json.decodeFromString<GetKeyResponse>(responseBody) as T
            ListKeysServiceCommand.COMMAND_ID -> json.decodeFromString<ListKeysResponse>(responseBody) as T
            DeleteKeyServiceCommand.COMMAND_ID -> json.decodeFromString<DeleteKeyOutput>(responseBody) as T
            ImportKeyServiceCommand.COMMAND_ID -> json.decodeFromString<ImportKeyResponse>(responseBody) as T
            else -> {
                throw IllegalStateException(
                    "No compile-time serializer registered for command $commandId response",
                )
            }
        }
}
