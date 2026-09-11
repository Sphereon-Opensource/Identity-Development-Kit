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

package com.sphereon.oauth2.server.authorization.command.authorization

import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.compat.JsExportCompat

/*
 * HTTP endpoint contracts for the authorization-endpoint family. Authorize dispatches across a
 * `Set<HandleAuthorizeRequestCommand>` (standard vs wallet via `supports()`); the callback runs
 * the [HandleAuthorizeCallbackCommand] pipeline; IAE branches by the presence of `auth_session`
 * in the form body to either [com.sphereon.oauth2.server.authorization.command.HandleIaeInitialRequestCommand]
 * or [com.sphereon.oauth2.server.authorization.command.HandleIaeFollowUpCommand]. The branching
 * is HTTP-layer parsing (which form params are present), not business logic.
 */

/**
 * `/authorize` endpoint. Per OIDC Core 1.0 §3.1.2.1 both `GET` (parameters in the query string)
 * and `POST` (parameters in an `application/x-www-form-urlencoded` body) are accepted. The impl
 * overrides `supports()` to match both methods on the same path; both code paths converge on the
 * same `HandleAuthorizeRequestArgs` dispatch. The companion [ENDPOINT] declares the `GET` form;
 * the `POST` form is advertised separately through the AppScope descriptor provider.
 */
@JsExportCompat
interface AuthorizeHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.authorization.authorize-endpoint"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/authorize",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "authorize",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("authorization"),
                summary = "RFC 6749 OAuth 2.0 Authorization Endpoint",
            )
    }
}

@JsExportCompat
interface AuthorizeCallbackHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.authorization.authorize-callback-endpoint"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/authorize/callback",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "authorizeCallback",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("authorization"),
                summary = "Resume the authorization flow after upstream user authentication",
            )
    }
}

@JsExportCompat
interface IaeHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.iae.iae-endpoint"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/iae",
                consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "interactiveAuthorization",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("iae", "authorization"),
                summary = "OID4VCI Interactive Authorization Endpoint",
            )
    }
}
