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

import com.sphereon.attribute.flow.AttributeRecord
import com.sphereon.attribute.pipeline.LookupKey
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
import com.sphereon.credential.issuance.pipeline.PipelineConfiguration
import com.sphereon.credential.issuance.pipeline.command.InitPipelineSessionArgs
import com.sphereon.credential.issuance.pipeline.command.InitPipelineSessionCommand
import com.sphereon.credential.issuance.pipeline.command.InitPipelineSessionResult
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Endpoint command for initialising a new issuance pipeline session.
 *
 * POST /sessions
 *
 * Lets a backend system allocate an [InitPipelineSessionResult] for a given
 * [PipelineConfiguration], optionally seeding it with initial attributes and lookup keys.
 * The session identifier and resolved correlation handle are returned in the response body.
 */
interface InitPipelineSessionEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.protocol.init-pipeline-session"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/backend/sessions",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "initPipelineSession",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer"),
                summary = "Initialise a new issuance pipeline session",
            )
    }
}

@Serializable
internal data class InitPipelineSessionRequestBody(
    @SerialName("pipeline_configuration")
    val pipelineConfiguration: PipelineConfiguration,
    @SerialName("correlation_id")
    val correlationId: String? = null,
    @SerialName("initial_attributes")
    val initialAttributes: List<AttributeRecord> = emptyList(),
    @SerialName("initial_lookup_keys")
    val initialLookupKeys: List<LookupKey> = emptyList(),
    @SerialName("ttl_seconds")
    val ttlSeconds: Long? = null,
)

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<InitPipelineSessionEndpointCommand>())
class InitPipelineSessionEndpointCommandImpl(
    execution: SessionExecution,
    private val initPipelineSessionCommand: InitPipelineSessionCommand? = null,
) : HttpEndpointCommandAdapter(
        id = InitPipelineSessionEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = InitPipelineSessionEndpointCommand.ENDPOINT,
    ),
    InitPipelineSessionEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        if (initPipelineSessionCommand == null) {
            return Err(
                IdkError.COMMAND_DISABLED_ERROR(
                    commandId = InitPipelineSessionCommand.COMMAND_ID,
                ),
            )
        }

        val request = applyDuring(args)

        val body =
            try {
                protocolJson.decodeFromString<InitPipelineSessionRequestBody>(request.body ?: "{}")
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed init-pipeline-session request: ${expected.message}"))
            }

        return initPipelineSessionCommand
            .execute(
                InitPipelineSessionArgs(
                    pipelineConfiguration = body.pipelineConfiguration,
                    correlationId = body.correlationId,
                    initialAttributes = body.initialAttributes,
                    initialLookupKeys = body.initialLookupKeys,
                    ttlSeconds = body.ttlSeconds,
                ),
            ).map { result ->
                GenericHttpResponse(
                    statusCode = 200,
                    headers = JSON_HEADERS,
                    body = protocolJson.encodeToString(InitPipelineSessionResult.serializer(), result),
                )
            }
    }
}
