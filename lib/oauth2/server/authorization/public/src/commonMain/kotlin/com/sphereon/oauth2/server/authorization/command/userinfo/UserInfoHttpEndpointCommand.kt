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

package com.sphereon.oauth2.server.authorization.command.userinfo

import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.compat.JsExportCompat

/*
 * HTTP endpoint contract for `/userinfo`. OIDC Core 1.0 §5.3 allows both GET and POST, so the
 * impl overrides `supports()` to match both methods on the same path. The descriptor declares
 * GET as the canonical OpenAPI operation.
 */

@JsExportCompat
interface UserInfoHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.userinfo.userinfo-endpoint"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/userinfo",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "userinfo",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("userinfo", "oidc"),
                summary = "OpenID Connect UserInfo endpoint",
            )
    }
}
