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
import com.sphereon.ktor.http.client.provider.withClient
import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import com.sphereon.openid.oid4vci.holder.RequestNonceArgs
import com.sphereon.openid.oid4vci.holder.RequestNonceCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Requests a c_nonce from the issuer's nonce endpoint.
 *
 * Per OID4VCI 1.1 Section 8: HTTP POST to the nonce endpoint with an empty body.
 * No access token is required for this call.
 * Response: {"c_nonce": "...", "c_nonce_expires_in": <seconds>}
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RequestNonceCommand>())
class RequestNonceCommandImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
) : TypedServiceCommandAdapter<RequestNonceArgs, NonceResponse>(
        commandId = RequestNonceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RequestNonceArgs>(),
        outputTypeToken = typeToken<NonceResponse>(),
    ),
    RequestNonceCommand {
    override val commandId: String get() = RequestNonceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is RequestNonceArgs

    override suspend fun doExecute(
        args: RequestNonceArgs,
        applyDuring: (RequestNonceArgs) -> RequestNonceArgs,
    ): IdkResult<NonceResponse, IdkError> {
        val applied = applyDuring(args)
        val nonceEndpoint = applied.nonceEndpoint

        log.debug("Requesting nonce from: $nonceEndpoint")

        return try {
            httpClientFactory.withClient { httpClient ->
                val response = httpClient.post(nonceEndpoint)

                if (!response.status.isSuccess()) {
                    return@withClient Err(
                        IdkError.fromString(
                            message = "Failed to request nonce from $nonceEndpoint: HTTP ${response.status.value}",
                            code = "NONCE_REQUEST_FAILED",
                        ),
                    )
                }

                val body = response.bodyAsText()
                val nonceResponse =
                    try {
                        Oid4vciJson.lenient.decodeFromString(NonceResponse.serializer(), body)
                    } catch (expected: Exception) {
                        return@withClient Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "Failed to parse nonce response JSON: ${expected.message}",
                                throwable = expected,
                            ),
                        )
                    }

                log.debug("Successfully obtained c_nonce from: $nonceEndpoint")
                Ok(nonceResponse)
            }
        } catch (expected: Exception) {
            Err(
                IdkError.fromString(
                    message = "Network error requesting nonce from $nonceEndpoint: ${expected.message}",
                    code = "NONCE_NETWORK_ERROR",
                    exception = expected,
                ),
            )
        }
    }
}
