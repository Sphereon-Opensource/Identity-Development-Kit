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
import com.sphereon.openid.oid4vci.issuer.command.IssueNonceArgs
import com.sphereon.openid.oid4vci.issuer.command.IssueNonceCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString

/**
 * Endpoint command for issuing a nonce.
 *
 * POST /nonce
 *
 * OID4VCI 1.1 Section 8.2: response MUST include Cache-Control: no-store.
 */
interface IssueNonceEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.protocol.nonce"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/nonce",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "issueNonce",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer"),
                summary = "Issue a nonce for credential request proof",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<IssueNonceEndpointCommand>())
class IssueNonceEndpointCommandImpl(
    execution: SessionExecution,
    private val issueNonceCommand: IssueNonceCommand,
) : HttpEndpointCommandAdapter(
        id = IssueNonceEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = IssueNonceEndpointCommand.ENDPOINT,
    ),
    IssueNonceEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        applyDuring(args)

        return issueNonceCommand.execute(IssueNonceArgs()).map { nonce ->
            GenericHttpResponse(
                statusCode = 200,
                headers = JSON_HEADERS + mapOf("Cache-Control" to "no-store"),
                body = protocolJson.encodeToString(nonce),
            )
        }
    }
}
