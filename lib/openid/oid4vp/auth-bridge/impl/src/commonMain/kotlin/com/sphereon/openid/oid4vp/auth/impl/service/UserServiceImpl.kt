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
import com.sphereon.core.api.encodeToHex
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.HttpJson
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.openid.oid4vp.auth.config.Oid4vpAuthBridgeConfigProvider
import com.sphereon.openid.oid4vp.auth.input.CreateUserInput
import com.sphereon.openid.oid4vp.auth.model.ResolvedUser
import com.sphereon.openid.oid4vp.auth.service.UserService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * HTTP-based implementation of [UserService] that calls the VDX User Microservice REST API.
 *
 * This service makes HTTP calls to the VDX User Microservice endpoints:
 * - GET /users?username={identifier} - Lookup user by username
 * - POST /users - Create a new user
 *
 * ## Configuration
 *
 * The base URL and timeouts are configured via [Oid4vpAuthBridgeConfig]:
 * - `user-api.base-url` - Base URL of the User API (e.g., http://localhost:8080/api/users/v1)
 * - `user-api.connection-timeout-ms` - Connection timeout in milliseconds
 * - `user-api.request-timeout-ms` - Request timeout in milliseconds
 *
 * ## Thread Safety
 *
 * This implementation is thread-safe as it uses Ktor's thread-safe HttpClient.
 *
 * @property configProvider Provider for the REST API connection configuration.
 * @property httpClientFactory Factory for creating HTTP clients.
 * @property execution Session execution context for logging.
 */
@Inject
@SingleIn(SessionScope::class)
class UserServiceImpl(
    private val configProvider: Oid4vpAuthBridgeConfigProvider,
    private val httpClientFactory: HttpClientFactory,
    private val execution: SessionExecution,
) : UserService {
    private val log = execution.log
    private val config by lazy { configProvider.getConfig() }

    private val httpClientOptions = HttpClientOptions.createDefault(LoggerConfig.Default)

    override suspend fun lookupUser(identifier: String): IdkResult<ResolvedUser?, IdkError> {
        log.debug("UserService: Looking up user with identifier=$identifier")

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
            // URL encode the identifier for the query parameter
            val encodedIdentifier = identifier.encodeURLQueryComponent()
            val response: HttpResponse =
                httpClient.get(
                    "${config.userApiBaseUrl}/users?username=$encodedIdentifier",
                )

            if (response.status.isSuccess()) {
                val listResponse: UserListResponse = response.body()
                val user = listResponse.items.firstOrNull()

                if (user != null) {
                    log.debug("UserService: Found user: partyId=${user.partyId}")
                } else {
                    log.debug("UserService: No user found with identifier=$identifier")
                }

                Ok(user)
            } else {
                handleErrorResponse(response, "Failed to lookup user")
            }
        } catch (e: Exception) {
            log.error("UserService: Lookup user failed: ${e.message}", e)
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

    override suspend fun createUser(input: CreateUserInput): IdkResult<ResolvedUser, IdkError> {
        log.debug("UserService: Creating user with username=${input.username}")

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
                httpClient.post("${config.userApiBaseUrl}/users") {
                    contentType(ContentType.Application.Json)
                    setBody(HttpJson.restApi.encodeToString(CreateUserInput.serializer(), input))
                }

            if (response.status.isSuccess()) {
                val detailResponse: UserDetailResponse = response.body()
                val user = detailResponse.user

                log.info("UserService: Created user: partyId=${user.partyId}, username=${user.username}")
                Ok(user)
            } else {
                handleErrorResponse(response, "Failed to create user")
            }
        } catch (e: Exception) {
            log.error("UserService: Create user failed: ${e.message}", e)
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
        val errorBody = response.bodyAsText().take(ERROR_BODY_MAX_LENGTH)
        log.error("UserService: $message: HTTP $statusCode - $errorBody")

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
                message = "$message: HTTP $statusCode - $errorBody",
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

/**
 * URL encoding for query parameters.
 */
private fun String.encodeURLQueryComponent(): String {
    val sb = StringBuilder()
    for (char in this) {
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

/**
 * Response model for user list endpoint.
 * This mirrors the VDX UserListResponse but is local to avoid VDX dependency.
 */
@Serializable
private data class UserListResponse(
    val items: List<ResolvedUser> = emptyList(),
)

/**
 * Response model for user detail endpoint (create/get single user).
 * This mirrors the VDX UserDetail but is local to avoid VDX dependency.
 */
@Serializable
private data class UserDetailResponse(
    val user: ResolvedUser,
)
