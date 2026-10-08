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

package com.sphereon.oauth2.server.authorization.impl.command.logout

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.query.percentEncodeQueryComponent
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.server.authorization.command.logout.SendBackChannelLogoutArgs
import com.sphereon.oauth2.server.authorization.command.logout.SendBackChannelLogoutCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

/**
 * IDK [SendBackChannelLogoutCommand] implementation. Fire-and-forget: the AS
 * RP-initiated logout flow MUST NOT block on a slow / failing RP. Logs network
 * errors and non-2xx response codes through the session logger but always
 * returns `Ok(Unit)` so the orchestrator continues delivering to other RPs.
 *
 * BC §2.7 specifies the AS SHOULD attempt redelivery on failure; durable retry
 * lives outside IDK because it requires a backing outbox the open-core layer
 * does not ship.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SendBackChannelLogoutCommand>())
class SendBackChannelLogoutCommandImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
) : TypedServiceCommandAdapter<SendBackChannelLogoutArgs, Unit, IdkError>(
        commandId = SendBackChannelLogoutCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<SendBackChannelLogoutArgs>(),
        outputTypeToken = typeToken<Unit>(),
    ),
    SendBackChannelLogoutCommand {
    override val commandId: String get() = SendBackChannelLogoutCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is SendBackChannelLogoutArgs

    override suspend fun doExecute(
        args: SendBackChannelLogoutArgs,
        applyDuring: (SendBackChannelLogoutArgs) -> SendBackChannelLogoutArgs,
    ): IdkResult<Unit, IdkError> {
        val applied = applyDuring(args)
        return try {
            val client =
                httpClientFactory.createClient(
                    HttpClientOptions(
                        engine = null,
                        enableContentNegotiation = false,
                    ),
                )
            val body = "logout_token=${percentEncodeQueryComponent(applied.logoutToken)}"
            val response: HttpResponse =
                client.post(applied.backchannelLogoutUri) {
                    contentType(ContentType.Application.FormUrlEncoded)
                    headers {
                        // BC §2.5: RPs MUST receive `Cache-Control: no-store` and `Pragma: no-cache`.
                        // RFC 7234 §5.2.1 caches the request, but caching the response side is the
                        // RP's concern; setting these on the request is a polite hint.
                        append(HttpHeaders.CacheControl, "no-store")
                        append(HttpHeaders.Pragma, "no-cache")
                    }
                    setBody(body)
                }
            val status = response.status.value
            if (status !in 200..299) {
                execution.log.warn(
                    "Back-Channel Logout delivery to ${applied.clientId} at ${applied.backchannelLogoutUri} " +
                        "returned HTTP $status; not retrying (IDK fire-and-forget). The RP may have stale sessions.",
                )
            }
            Ok(Unit)
        } catch (expected: Exception) {
            execution.log.warn(
                "Back-Channel Logout delivery to ${applied.clientId} at ${applied.backchannelLogoutUri} " +
                    "failed: ${expected.message}; not retrying (IDK fire-and-forget). The RP may have stale sessions.",
            )
            Ok(Unit)
        }
    }
}
