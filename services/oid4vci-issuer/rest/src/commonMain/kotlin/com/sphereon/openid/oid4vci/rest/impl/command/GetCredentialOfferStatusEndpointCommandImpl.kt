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

package com.sphereon.openid.oid4vci.rest.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.rest.GetCredentialOfferStatusEndpointCommand
import com.sphereon.openid.oid4vci.rest.GetCredentialOfferStatusInput
import com.sphereon.openid.oid4vci.rest.GetCredentialOfferStatusOutput
import com.sphereon.openid.oid4vci.rest.GetCredentialOfferStatusServiceCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

/**
 * HTTP endpoint for getting credential offer status.
 *
 * GET /api/oid4vci/v1/backend/credential/offers/{correlation_id}
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetCredentialOfferStatusEndpointCommand>())
class GetCredentialOfferStatusEndpointCommandImpl(
    execution: SessionExecution,
    private val getCredentialOfferStatusServiceCommand: GetCredentialOfferStatusServiceCommand,
) : HttpEndpointCommandAdapter(
        id = GetCredentialOfferStatusEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetCredentialOfferStatusEndpointCommand.ENDPOINT,
    ),
    GetCredentialOfferStatusEndpointCommand {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val correlationId = request.requirePathParam("correlation_id").getOrElse { return Err(it) }

        val output =
            getCredentialOfferStatusServiceCommand
                .execute(
                    GetCredentialOfferStatusInput(correlationId = correlationId),
                ).getOrElse { error ->
                    return Err(error)
                }

        return Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers =
                    mapOf(
                        "Content-Type" to "application/json",
                        "Cache-Control" to "no-store",
                    ),
                body = json.encodeToString(GetCredentialOfferStatusOutput.serializer(), output),
            ),
        )
    }
}
