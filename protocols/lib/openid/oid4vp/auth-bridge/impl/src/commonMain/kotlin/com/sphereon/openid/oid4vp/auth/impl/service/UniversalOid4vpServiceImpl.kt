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

package com.sphereon.openid.oid4vp.auth.impl.service

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.HttpJson
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.openid.oid4vp.auth.config.Oid4vpAuthBridgeConfigProvider
import com.sphereon.openid.oid4vp.auth.service.UniversalOid4vpService
import com.sphereon.openid.oid4vp.universal.CreateAuthorizationRequestInput
import com.sphereon.openid.oid4vp.universal.CreateAuthorizationRequestOutput
import com.sphereon.openid.oid4vp.universal.GetAuthorizationRequestStatusOutput
import com.sphereon.openid.oid4vp.universal.UniversalOid4vpErrorResponse
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

private const val ERROR_BODY_TRUNCATE_LENGTH = 500
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_CONFLICT = 409

/**
 * HTTP-based implementation of [UniversalOid4vpServiceImpl] that calls a remote Universal OID4VP REST API.
 *
 * This service makes HTTP calls to the Universal OID4VP REST API endpoints:
 * - POST /backend/auth/requests - Create authorization request
 * - GET /backend/auth/requests/{correlation_id} - Get status
 * - DELETE /backend/auth/requests/{correlation_id} - Delete request
 *
 * ## Configuration
 *
 * The base URL and timeouts are configured via [Oid4vpAuthBridgeConfig].
 *
 * ## Usage
 *
 * This service is used when the Universal OID4VP verifier runs as a separate microservice,
 * rather than in the same process. It replaces [InternalUniversalOid4vpClient] for
 * deployments where the verifier is externalized.
 *
 * ## Thread Safety
 *
 * This implementation is thread-safe as it uses Ktor's thread-safe HttpClient.
 *
 * @property configProvider Provider for the REST API connection configuration
 * @property httpClientFactory Factory for creating HTTP clients
 * @property execution Session execution context for logging
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UniversalOid4vpService>())
class UniversalOid4vpServiceImpl(
    private val configProvider: Oid4vpAuthBridgeConfigProvider,
    private val httpClientFactory: HttpClientFactory,
    private val execution: SessionExecution,
) : UniversalOid4vpService {
    private val log = execution.log
    private val config by lazy { configProvider.getConfig() }

    private val httpClientOptions = HttpClientOptions.createDefault(LoggerConfig.Default)

    override suspend fun createAuthorizationRequest(input: CreateAuthorizationRequestInput): IdkResult<CreateAuthorizationRequestOutput, IdkError> {
        log.debug("UniversalOid4vpService: Creating authorization request with queryId=${input.queryId}")

        val httpClient =
            try {
                httpClientFactory.createClient(httpClientOptions)
            } catch (e: Exception) {
                return Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to create HTTP client: ${e.message}",
                        exception = e,
                    ),
                )
            }

        return try {
            val url = "${config.universalApiBaseUrl}/backend/auth/requests"
            val body = HttpJson.restApi.encodeToString(CreateAuthorizationRequestInput.serializer(), input)
            val response: HttpResponse =
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

            if (response.status.isSuccess()) {
                val output: CreateAuthorizationRequestOutput = response.body()
                log.info("UniversalOid4vpService: Created authorization request: correlationId=${output.correlationId}")
                Ok(output)
            } else {
                handleErrorResponse(response, "Failed to create authorization request")
            }
        } catch (e: Exception) {
            log.error("UniversalOid4vpService: Create authorization request failed: ${e.message}", e)
            Err(
                IdkError.UNKNOWN_ERROR(
                    message = "HTTP request failed: ${e.message}",
                    exception = e,
                ),
            )
        } finally {
            httpClient.close()
        }
    }

    override suspend fun getAuthorizationRequestStatus(correlationId: String): IdkResult<GetAuthorizationRequestStatusOutput, IdkError> {
        log.debug("UniversalOid4vpService: Getting authorization request status: correlationId=$correlationId")

        val httpClient =
            try {
                httpClientFactory.createClient(httpClientOptions)
            } catch (e: Exception) {
                return Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to create HTTP client: ${e.message}",
                        exception = e,
                    ),
                )
            }

        return try {
            val response: HttpResponse =
                httpClient.get(
                    "${config.universalApiBaseUrl}/backend/auth/requests/$correlationId",
                )

            if (response.status.isSuccess()) {
                val output: GetAuthorizationRequestStatusOutput = response.body()
                log.debug("UniversalOid4vpService: Status for $correlationId: ${output.status}")
                Ok(output)
            } else {
                handleErrorResponse(response, "Failed to get authorization request status")
            }
        } catch (e: Exception) {
            log.error("UniversalOid4vpService: Get status failed: ${e.message}", e)
            Err(
                IdkError.UNKNOWN_ERROR(
                    message = "HTTP request failed: ${e.message}",
                    exception = e,
                ),
            )
        } finally {
            httpClient.close()
        }
    }

    override suspend fun deleteAuthorizationRequest(correlationId: String): IdkResult<Unit, IdkError> {
        log.debug("UniversalOid4vpService: Deleting authorization request: correlationId=$correlationId")

        val httpClient =
            try {
                httpClientFactory.createClient(httpClientOptions)
            } catch (e: Exception) {
                return Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to create HTTP client: ${e.message}",
                        exception = e,
                    ),
                )
            }

        return try {
            val response: HttpResponse =
                httpClient.delete(
                    "${config.universalApiBaseUrl}/backend/auth/requests/$correlationId",
                )

            if (response.status.isSuccess()) {
                log.info("UniversalOid4vpService: Deleted authorization request: correlationId=$correlationId")
                Ok(Unit)
            } else {
                handleErrorResponse(response, "Failed to delete authorization request")
            }
        } catch (e: Exception) {
            log.error("UniversalOid4vpService: Delete failed: ${e.message}", e)
            Err(
                IdkError.UNKNOWN_ERROR(
                    message = "HTTP request failed: ${e.message}",
                    exception = e,
                ),
            )
        } finally {
            httpClient.close()
        }
    }

    private suspend fun <T> handleErrorResponse(
        response: HttpResponse,
        message: String,
    ): IdkResult<T, IdkError> {
        val statusCode = response.status.value
        val errorBody =
            try {
                response.body<UniversalOid4vpErrorResponse>()
            } catch (e: Exception) {
                null
            }

        val errorMessage = errorBody?.message ?: response.bodyAsText().take(ERROR_BODY_MAX_LENGTH)
        log.error("UniversalOid4vpService: $message: HTTP $statusCode - $errorMessage")

        val errorCode =
            when (statusCode) {
                HTTP_NOT_FOUND -> "NOT_FOUND"
                HTTP_BAD_REQUEST -> "BAD_REQUEST"
                HTTP_UNAUTHORIZED -> "UNAUTHORIZED"
                HTTP_FORBIDDEN -> "FORBIDDEN"
                HTTP_CONFLICT -> "CONFLICT"
                else -> "HTTP_ERROR"
            }

        return Err(
            IdkError.fromString(
                message = "$message: HTTP $statusCode - $errorMessage",
                code = errorCode,
            ),
        )
    }

    private companion object {
        private const val ERROR_BODY_MAX_LENGTH = 500
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_CONFLICT = 409
    }
}
