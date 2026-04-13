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

package com.sphereon.openid.oid4vci.issuer.impl.http.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.CredentialNotification
import com.sphereon.openid.oid4vci.issuer.command.HandleNotificationArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleNotificationCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Endpoint command for handling credential notifications.
 *
 * POST /notification
 */
interface HandleNotificationEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.protocol.notification"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/notification",
                consumes = setOf(MediaType.ApplicationJson),
                operationId = "handleNotification",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer"),
                summary = "Handle a credential notification",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleNotificationEndpointCommand>())
class HandleNotificationEndpointCommandImpl(
    execution: SessionExecution,
    private val handleNotificationCommand: HandleNotificationCommand,
) : HttpEndpointCommandAdapter(
        id = HandleNotificationEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = HandleNotificationEndpointCommand.ENDPOINT,
    ),
    HandleNotificationEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        val accessToken =
            extractBearerToken(request)
                ?: return Err(IdkError.UNAUTHORIZED_ERROR(message = "Missing or invalid Authorization header"))

        val notification =
            try {
                protocolJson.decodeFromString<CredentialNotification>(request.body ?: "{}")
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed notification request: ${expected.message}"))
            }

        return handleNotificationCommand
            .execute(
                HandleNotificationArgs(
                    accessToken = accessToken,
                    notification = notification,
                ),
            ).map { GenericHttpResponse(statusCode = 204, headers = emptyMap(), body = null) }
    }
}
