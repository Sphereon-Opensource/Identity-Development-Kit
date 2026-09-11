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

package com.sphereon.openid.oid4vp.dcql.store.rest.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpJson
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.dcql.store.command.CreateDcqlQueryArgs
import com.sphereon.openid.oid4vp.dcql.store.command.CreateDcqlQueryServiceCommand
import com.sphereon.openid.oid4vp.dcql.store.command.DeleteDcqlQueryArgs
import com.sphereon.openid.oid4vp.dcql.store.command.DeleteDcqlQueryServiceCommand
import com.sphereon.openid.oid4vp.dcql.store.command.GetDcqlQueryArgs
import com.sphereon.openid.oid4vp.dcql.store.command.GetDcqlQueryServiceCommand
import com.sphereon.openid.oid4vp.dcql.store.command.ListDcqlQueriesArgs
import com.sphereon.openid.oid4vp.dcql.store.command.ListDcqlQueriesServiceCommand
import com.sphereon.openid.oid4vp.dcql.store.command.UpdateDcqlQueryArgs
import com.sphereon.openid.oid4vp.dcql.store.command.UpdateDcqlQueryServiceCommand
import com.sphereon.openid.oid4vp.dcql.store.http.CreateDcqlQueryEndpointCommand
import com.sphereon.openid.oid4vp.dcql.store.http.DeleteDcqlQueryEndpointCommand
import com.sphereon.openid.oid4vp.dcql.store.http.GetDcqlQueryEndpointCommand
import com.sphereon.openid.oid4vp.dcql.store.http.ListDcqlQueriesEndpointCommand
import com.sphereon.openid.oid4vp.dcql.store.http.PatchDcqlQueryEndpointCommand
import com.sphereon.openid.oid4vp.dcql.store.http.ReplaceDcqlQueryEndpointCommand
import com.sphereon.openid.oid4vp.dcql.store.http.model.UpdateDcqlQueryRequest
import com.sphereon.openid.oid4vp.dcql.store.model.DcqlQueryConfiguration
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.builtins.ListSerializer

private val JSON_HEADERS =
    mapOf(
        "Content-Type" to "application/json",
        "Cache-Control" to "no-store",
    )

private fun jsonResponse(
    statusCode: Int,
    body: String,
): GenericHttpResponse =
    GenericHttpResponse(
        statusCode = statusCode,
        headers = JSON_HEADERS,
        body = body,
    )

private fun parseBody(
    body: String?,
    onMissing: () -> IdkError,
): IdkResult<String, IdkError> =
    if (body.isNullOrBlank()) {
        Err(onMissing())
    } else {
        Ok(body)
    }

/**
 * `GET /api/dcql/v1/queries` — list all DCQL query configurations for the current tenant.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(ListDcqlQueriesEndpointCommand.COMMAND_ID)
class ListDcqlQueriesEndpointCommandImpl(
    execution: SessionExecution,
    private val listCommand: ListDcqlQueriesServiceCommand,
) : HttpEndpointCommandAdapter(
        id = ListDcqlQueriesEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ListDcqlQueriesEndpointCommand.ENDPOINT,
    ),
    ListDcqlQueriesEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        applyDuring(args)
        val result = listCommand.execute(ListDcqlQueriesArgs()).getOrElse { return Err(it) }
        return Ok(
            jsonResponse(
                statusCode = 200,
                body = HttpJson.restApi.encodeToString(ListSerializer(DcqlQueryConfiguration.serializer()), result),
            ),
        )
    }
}

/**
 * `POST /api/dcql/v1/queries` — create a new DCQL query configuration.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(CreateDcqlQueryEndpointCommand.COMMAND_ID)
class CreateDcqlQueryEndpointCommandImpl(
    execution: SessionExecution,
    private val createCommand: CreateDcqlQueryServiceCommand,
) : HttpEndpointCommandAdapter(
        id = CreateDcqlQueryEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = CreateDcqlQueryEndpointCommand.ENDPOINT,
    ),
    CreateDcqlQueryEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val body =
            parseBody(request.body) {
                IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Request body is required")
            }.getOrElse { return Err(it) }
        val createArgs =
            try {
                HttpJson.restApi.decodeFromString(CreateDcqlQueryArgs.serializer(), body)
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed request body: ${expected.message}"))
            }
        val result = createCommand.execute(createArgs).getOrElse { return Err(it) }
        return Ok(
            jsonResponse(
                statusCode = 201,
                body = HttpJson.restApi.encodeToString(DcqlQueryConfiguration.serializer(), result),
            ),
        )
    }
}

/**
 * `GET /api/dcql/v1/queries/{queryId}` — read a single DCQL query configuration.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(GetDcqlQueryEndpointCommand.COMMAND_ID)
class GetDcqlQueryEndpointCommandImpl(
    execution: SessionExecution,
    private val getCommand: GetDcqlQueryServiceCommand,
) : HttpEndpointCommandAdapter(
        id = GetDcqlQueryEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetDcqlQueryEndpointCommand.ENDPOINT,
    ),
    GetDcqlQueryEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val queryId = request.requirePathParam("queryId").getOrElse { return Err(it) }
        val result = getCommand.execute(GetDcqlQueryArgs(queryId)).getOrElse { return Err(it) }
        return Ok(
            jsonResponse(
                statusCode = 200,
                body = HttpJson.restApi.encodeToString(DcqlQueryConfiguration.serializer(), result),
            ),
        )
    }
}

/**
 * Shared `PUT`/`PATCH` body handling: extracts the path `queryId`, parses the
 * [UpdateDcqlQueryRequest] body, and invokes the update service command.
 */
private suspend fun runUpdate(
    request: GenericHttpRequest,
    pathPattern: String,
    updateCommand: UpdateDcqlQueryServiceCommand,
): IdkResult<GenericHttpResponse, IdkError> {
    val enriched = request.withExtractedParams(pathPattern)
    val queryId = enriched.requirePathParam("queryId").getOrElse { return Err(it) }
    val body =
        parseBody(enriched.body) {
            IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Request body is required")
        }.getOrElse { return Err(it) }
    val updateRequest =
        try {
            HttpJson.restApi.decodeFromString(UpdateDcqlQueryRequest.serializer(), body)
        } catch (expected: Exception) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed request body: ${expected.message}"))
        }
    val updateArgs =
        UpdateDcqlQueryArgs(
            queryId = queryId,
            name = updateRequest.name,
            description = updateRequest.description,
            dcqlQuery = updateRequest.dcqlQuery,
            enabled = updateRequest.enabled,
        )
    val result = updateCommand.execute(updateArgs).getOrElse { return Err(it) }
    return Ok(
        jsonResponse(
            statusCode = 200,
            body = HttpJson.restApi.encodeToString(DcqlQueryConfiguration.serializer(), result),
        ),
    )
}

/**
 * `PUT /api/dcql/v1/queries/{queryId}` — replace a DCQL query configuration in full.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(ReplaceDcqlQueryEndpointCommand.COMMAND_ID)
class ReplaceDcqlQueryEndpointCommandImpl(
    execution: SessionExecution,
    private val updateCommand: UpdateDcqlQueryServiceCommand,
) : HttpEndpointCommandAdapter(
        id = ReplaceDcqlQueryEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ReplaceDcqlQueryEndpointCommand.ENDPOINT,
    ),
    ReplaceDcqlQueryEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> = runUpdate(applyDuring(args), endpoint.pathPattern, updateCommand)
}

/**
 * `PATCH /api/dcql/v1/queries/{queryId}` — partially update a DCQL query configuration.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(PatchDcqlQueryEndpointCommand.COMMAND_ID)
class PatchDcqlQueryEndpointCommandImpl(
    execution: SessionExecution,
    private val updateCommand: UpdateDcqlQueryServiceCommand,
) : HttpEndpointCommandAdapter(
        id = PatchDcqlQueryEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = PatchDcqlQueryEndpointCommand.ENDPOINT,
    ),
    PatchDcqlQueryEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> = runUpdate(applyDuring(args), endpoint.pathPattern, updateCommand)
}

/**
 * `DELETE /api/dcql/v1/queries/{queryId}` — delete a DCQL query configuration.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(DeleteDcqlQueryEndpointCommand.COMMAND_ID)
class DeleteDcqlQueryEndpointCommandImpl(
    execution: SessionExecution,
    private val deleteCommand: DeleteDcqlQueryServiceCommand,
) : HttpEndpointCommandAdapter(
        id = DeleteDcqlQueryEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = DeleteDcqlQueryEndpointCommand.ENDPOINT,
    ),
    DeleteDcqlQueryEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args).withExtractedParams(endpoint.pathPattern)
        val queryId = request.requirePathParam("queryId").getOrElse { return Err(it) }
        val deleted = deleteCommand.execute(DeleteDcqlQueryArgs(queryId)).getOrElse { return Err(it) }
        if (!deleted) {
            return Err(IdkError.NOT_FOUND_ERROR(message = "DCQL query configuration not found: $queryId"))
        }
        return Ok(GenericHttpResponse(statusCode = 204, headers = JSON_HEADERS, body = null))
    }
}
