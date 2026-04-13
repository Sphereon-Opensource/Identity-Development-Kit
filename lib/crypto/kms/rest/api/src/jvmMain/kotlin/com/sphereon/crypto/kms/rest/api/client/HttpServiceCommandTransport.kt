/*
 * © 2025 Sphereon International B.V.
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
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyInput
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyOutput
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
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

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
    private val principalHeaderName: String = "X-User-ID"
) : KmsCommandTransport {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(CIO) {
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
                outputTypeToken: TypeToken<T>
            ): IdkResult<T, IdkError> {
                return invokeInternal(commandId, input, outputTypeToken, requestExecutor)
            }
        }
    }

    override suspend fun <T : Any> invoke(
        commandId: String,
        input: Any,
        sessionContext: SessionContext,
        outputTypeToken: TypeToken<T>
    ): IdkResult<T, IdkError> {
        val requestExecutor = SessionBoundRequestExecutor(sessionContext)
        return invokeInternal(commandId, input, outputTypeToken, requestExecutor)
    }

    private suspend fun <T : Any> invokeInternal(
        commandId: String,
        input: Any,
        outputTypeToken: TypeToken<T>,
        requestExecutor: SessionBoundRequestExecutor
    ): IdkResult<T, IdkError> {
        val route = commandRoutes[commandId]
            ?: return Err(IdkError.INVALID_STATE(message = "No route registered for command $commandId"))
        val httpMethod = route.first
        val pathTemplate = route.second

        // Build URL with path parameter substitution
        val url = buildUrl(pathTemplate, input)

        try {
            val response = when (httpMethod.uppercase()) {
                "GET" -> requestExecutor.executeGet(url, input)
                "POST" -> requestExecutor.executePost(url, input)
                "PUT" -> requestExecutor.executePut(url, input)
                "DELETE" -> requestExecutor.executeDelete(url, input)
                else -> return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported HTTP method: $httpMethod"))
            }

            val statusCode = response.status.value
            val responseBody = response.bodyAsText()

            if (statusCode >= 400) {
                return parseErrorResponse(statusCode, responseBody)
            }

            if (responseBody.isBlank()) {
                val synthesized = synthesizeEmptyResponse(input, outputTypeToken)
                if (synthesized != null) {
                    return Ok(synthesized)
                }
                return Err(IdkError.INVALID_STATE(message = "Empty response body for command $commandId"))
            }

            @Suppress("UNCHECKED_CAST")
            val serializer = kotlinx.serialization.serializer(outputTypeToken.kType)
            val result = json.decodeFromString(serializer, responseBody) as T
            return Ok(result)

        } catch (e: Exception) {
            return Err(IdkError.UNKNOWN_ERROR(
                message = "HTTP request failed for command $commandId: ${e.message}",
                exception = e
            ))
        }
    }

    /**
     * Builds the full URL by substituting path parameters from the input.
     *
     * Path parameters like {aliasOrKid} are replaced with values from the input object.
     */
    private fun buildUrl(pathTemplate: String, input: Any): String {
        var path = pathTemplate

        // Extract path parameters using reflection or serialization
        val inputJson = encodeToJson(input)
        val inputMap = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(inputJson)

        // Replace path parameters like {aliasOrKid} with actual values
        val pathParamRegex = "\\{([^}]+)\\}".toRegex()
        pathParamRegex.findAll(pathTemplate).forEach { match ->
            val paramName = match.groupValues[1]
            val value = inputMap[paramName]?.let {
                when (it) {
                    is kotlinx.serialization.json.JsonPrimitive -> it.content
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
        private val sessionContext: SessionContext
    ) {
        suspend fun executeGet(
            url: String,
            input: Any
        ): HttpResponse {
            // Extract query parameters from input (fields not in path)
            val queryParams = extractQueryParams(input, url)

            return httpClient.get(url) {
                addAuthHeaders()
                queryParams.forEach { (key, value) ->
                    parameter(key, value)
                }
            }
        }

        suspend fun executePost(
            url: String,
            input: Any
        ): HttpResponse {
            return httpClient.post(url) {
                addAuthHeaders()
                contentType(ContentType.Application.Json)
                setBody(extractRequestBody(input))
            }
        }

        suspend fun executePut(
            url: String,
            input: Any
        ): HttpResponse {
            return httpClient.put(url) {
                addAuthHeaders()
                contentType(ContentType.Application.Json)
                setBody(extractRequestBody(input))
            }
        }

        suspend fun executeDelete(
            url: String,
            input: Any
        ): HttpResponse {
            val queryParams = extractQueryParams(input, url)

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
    private fun extractQueryParams(input: Any, url: String): Map<String, String> {
        val inputJson = encodeToJson(input)
        val inputMap = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(inputJson)

        // Find which params are already in the path
        val pathParams = "\\{([^}]+)\\}".toRegex().findAll(url).map { it.groupValues[1] }.toSet()

        // Return non-path params as query params (only non-null primitives)
        return inputMap.entries
            .filter { it.key !in pathParams }
            .mapNotNull { (key, value) ->
                when (value) {
                    is kotlinx.serialization.json.JsonNull -> null
                    is kotlinx.serialization.json.JsonPrimitive -> key to value.content
                    else -> null // Skip complex objects
                }
            }
            .toMap()
    }

    /**
     * Extracts the request body for POST/PUT requests.
     * For wrapped inputs (like GenerateKeyInput.generateKey), unwraps to the inner object.
     */
    private fun extractRequestBody(input: Any): String {
        val inputJson = encodeToJson(input)
        val inputMap = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(inputJson)

        // Check if input has a single nested object field (wrapper pattern)
        // e.g., GenerateKeyInput { generateKey: GenerateKeyGlobal }
        // In this case, send just the inner object
        if (inputMap.size == 1) {
            val singleValue = inputMap.values.first()
            if (singleValue is kotlinx.serialization.json.JsonObject) {
                return singleValue.toString()
            }
        }

        return inputJson
    }

    private fun <T : Any> parseErrorResponse(statusCode: Int, responseBody: String): IdkResult<T, IdkError> {
        val error = when (statusCode) {
            400 -> IdkError.ILLEGAL_ARGUMENT_ERROR(message = responseBody.ifEmpty { "Bad request" })
            401 -> IdkError.UNAUTHORIZED_ERROR(message = responseBody.ifEmpty { "Unauthorized" })
            403 -> IdkError.FORBIDDEN_ERROR(message = responseBody.ifEmpty { "Forbidden" })
            404 -> IdkError.NOT_FOUND_ERROR(message = responseBody.ifEmpty { "Not found" })
            409 -> IdkError.INVALID_STATE(message = responseBody.ifEmpty { "Conflict" })
            else -> IdkError.UNKNOWN_ERROR(message = "HTTP $statusCode: $responseBody")
        }
        return Err(error)
    }

    /**
     * Closes the HTTP client.
     */
    fun close() {
        httpClient.close()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> synthesizeEmptyResponse(input: Any, outputTypeToken: TypeToken<T>): T? {
        val outputClassifier = outputTypeToken.kType.classifier
        return when {
            outputClassifier == DeleteKeyOutput::class && input is DeleteKeyInput ->
                DeleteKeyOutput(aliasOrKid = input.aliasOrKid, deleted = true) as T
            else -> null
        }
    }

    @OptIn(InternalSerializationApi::class)
    private fun encodeToJson(input: Any): String {
        @Suppress("UNCHECKED_CAST")
        val serializer = input::class.serializer() as kotlinx.serialization.KSerializer<Any>
        return json.encodeToString(serializer, input)
    }
}
