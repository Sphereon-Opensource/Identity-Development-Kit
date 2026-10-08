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

package com.sphereon.oauth2.server.authorization.command.device

import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.compat.JsExportCompat

/**
 * HTTP shell for the RFC 8628 §3.1 `/device_authorization` endpoint. Parses the
 * `application/x-www-form-urlencoded` request body, dispatches to
 * [IssueDeviceAuthorizationCommand], and renders the RFC 8628 §3.2 JSON response. Gated on the
 * per-server `deviceFlow` feature policy at the impl layer so a 404 surfaces when the deployment
 * has not opted in, mirroring how `/par` is gated by `par`.
 */
@JsExportCompat
interface DeviceAuthorizationHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.device.authorization-endpoint"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/device_authorization",
                consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "deviceAuthorization",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("device"),
                summary = "RFC 8628 OAuth 2.0 Device Authorization Endpoint",
            )
    }
}
