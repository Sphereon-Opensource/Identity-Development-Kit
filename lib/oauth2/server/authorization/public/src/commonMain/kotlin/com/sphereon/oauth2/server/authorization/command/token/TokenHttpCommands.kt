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

package com.sphereon.oauth2.server.authorization.command.token

import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.compat.JsExportCompat

/*
 * HTTP endpoint contracts for the token-endpoint family. Each command is a thin HTTP shell over
 * its underlying ServiceCommand: parse the form/body, dispatch, render the response. The
 * register-pre-authorized-code endpoint additionally lifts Basic-auth credentials from the
 * Authorization header in the HTTP layer; credential validation lives in
 * [RegisterPreAuthorizedCodeCommand].
 */

@JsExportCompat
interface TokenHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.token.token-endpoint"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/token",
                consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "token",
                commandId = COMMAND_ID,
                tags = setOf("token"),
                summary = "RFC 6749 OAuth 2.0 Token Endpoint",
            )
    }
}

@JsExportCompat
interface RegisterPreAuthorizedCodeHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.token.register-pre-authorized-code-endpoint"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/internal/preauth/register",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "registerPreAuthCode",
                commandId = COMMAND_ID,
                tags = setOf("token", "internal"),
                summary = "Internal endpoint for cross-service pre-authorized code registration",
            )
    }
}
