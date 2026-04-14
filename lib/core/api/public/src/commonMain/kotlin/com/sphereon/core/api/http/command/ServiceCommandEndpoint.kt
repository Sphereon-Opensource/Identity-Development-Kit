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

package com.sphereon.core.api.http.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.errorResponse
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat

private const val HTTP_NOT_IMPLEMENTED = 501

/**
 * Wraps a [ServiceCommand] as an [HttpEndpointCommand] for metadata-driven routing.
 *
 * In VDX binary transport mode, this wrapper is used only for metadata (endpoint descriptor).
 * The actual request handling goes through [BinaryCommandAdapter] which deserializes the
 * binary request and invokes the [ServiceCommand] directly.
 *
 * This class provides the bridge between the two command systems:
 * - [HttpEndpointCommand] (GenericHttpRequest -> GenericHttpResponse) for HTTP adapter routing
 * - [ServiceCommand] (typed I/O) for actual business logic
 *
 * @param serviceCommand The service command to wrap
 * @param endpoint The HTTP endpoint descriptor with method, path, etc.
 * @param execution The session execution context
 */
@JsExportCompat
class ServiceCommandEndpoint(
    private val serviceCommand: ServiceCommand<*, *>,
    override val endpoint: HttpEndpointDescriptor,
    execution: SessionExecution,
) : HttpEndpointCommandAdapter(
        id = serviceCommand.commandId,
        execution = execution,
        endpoint = endpoint,
    ) {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        // In VDX mode, this endpoint is metadata-only; BinaryCommandAdapter handles
        // actual request dispatch. If invoked directly (e.g., IDK universal adapter mode),
        // return a 501 indicating the command should be accessed through the binary transport.
        return Ok(
            errorResponse(
                HTTP_NOT_IMPLEMENTED,
                "ServiceCommand '${serviceCommand.commandId}' should be invoked through binary transport, not direct HTTP adapter dispatch",
            ),
        )
    }
}
