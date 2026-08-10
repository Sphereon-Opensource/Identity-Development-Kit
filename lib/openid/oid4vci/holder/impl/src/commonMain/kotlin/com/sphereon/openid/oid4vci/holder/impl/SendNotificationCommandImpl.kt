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
import com.sphereon.openid.oid4vci.common.model.CredentialNotification
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrorResponse
import com.sphereon.openid.oid4vci.holder.SendNotificationArgs
import com.sphereon.openid.oid4vci.holder.SendNotificationCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Sends a credential event notification to the issuer.
 *
 * Per OID4VCI 1.1 Section 12.1: HTTP POST to notification endpoint with access-token auth.
 * Successful response: HTTP 2xx (204 No Content expected).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SendNotificationCommand>())
class SendNotificationCommandImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
) : TypedServiceCommandAdapter<SendNotificationArgs, Unit, IdkError>(
        commandId = SendNotificationCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<SendNotificationArgs>(),
        outputTypeToken = typeToken<Unit>(),
    ),
    SendNotificationCommand {
    override val commandId: String get() = SendNotificationCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is SendNotificationArgs

    override suspend fun doExecute(
        args: SendNotificationArgs,
        applyDuring: (SendNotificationArgs) -> SendNotificationArgs,
    ): IdkResult<Unit, IdkError> {
        val applied = applyDuring(args)

        log.debug("Sending notification '${applied.event}' to: ${applied.notificationEndpoint}")

        return try {
            httpClientFactory.withClient { httpClient ->
                val notification =
                    CredentialNotification(
                        notificationId = applied.notificationId,
                        event = applied.event,
                        eventDescription = applied.eventDescription,
                    )

                val requestBody = Oid4vciJson.lenient.encodeToString(CredentialNotification.serializer(), notification)

                val response =
                    httpClient.post(applied.notificationEndpoint) {
                        contentType(ContentType.Application.Json)
                        headers {
                            val scheme = if (applied.dpopProofJwt != null) "DPoP" else "Bearer"
                            append("Authorization", "$scheme ${applied.accessToken}")
                            applied.dpopProofJwt?.let { append("DPoP", it) }
                        }
                        setBody(requestBody)
                    }

                if (!response.status.isSuccess()) {
                    val errorBody =
                        try {
                            response.bodyAsText()
                        } catch (expected: Exception) {
                            log.debug("Failed to read notification error response body: ${expected.message}")
                            ""
                        }
                    val errorResponse =
                        try {
                            Oid4vciJson.lenient.decodeFromString(Oid4vciErrorResponse.serializer(), errorBody)
                        } catch (expected: Exception) {
                            log.debug("Failed to parse notification error response JSON: ${expected.message}")
                            null
                        }
                    val dpopNonce = response.headers["DPoP-Nonce"]
                    val wwwAuthenticate = response.headers["WWW-Authenticate"].orEmpty()
                    if (dpopNonce != null && (errorResponse?.error == "use_dpop_nonce" || wwwAuthenticate.contains("use_dpop_nonce"))) {
                        return@withClient Err(
                            IdkError(
                                code = "use_dpop_nonce",
                                message =
                                    IdkError.Message(
                                        i18nKey = "use_dpop_nonce",
                                        defaultMessage = "Notification endpoint requires nonce in DPoP proof",
                                    ),
                                meta = mapOf("dpop_nonce" to dpopNonce),
                            ),
                        )
                    }
                    return@withClient Err(
                        IdkError.fromString(
                            message = "Notification endpoint returned HTTP ${response.status.value}: $errorBody",
                            code = "NOTIFICATION_FAILED",
                        ),
                    )
                }

                log.debug("Successfully sent notification '${applied.event}' to: ${applied.notificationEndpoint}")
                Ok(Unit)
            }
        } catch (expected: Exception) {
            Err(
                IdkError.fromString(
                    message = "Network error sending notification to ${applied.notificationEndpoint}: ${expected.message}",
                    code = "NOTIFICATION_NETWORK_ERROR",
                    exception = expected,
                ),
            )
        }
    }
}
