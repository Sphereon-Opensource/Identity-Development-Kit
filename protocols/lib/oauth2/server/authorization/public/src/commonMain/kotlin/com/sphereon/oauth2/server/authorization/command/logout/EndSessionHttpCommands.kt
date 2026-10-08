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

package com.sphereon.oauth2.server.authorization.command.logout

import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.compat.JsExportCompat

// HTTP endpoint contracts for the OIDC RP-Initiated Logout 1.0 end-session endpoint and the
// downstream Front-Channel logout HTML response. The end-session endpoint MUST accept both
// GET and POST per RP-Initiated Logout §2.

/**
 * `GET /logout`: OIDC RP-Initiated Logout 1.0 end-session endpoint reading parameters from
 * the query string. The handler invalidates the OIDC login session bound to the request's
 * `oidc_login_sid` cookie (and / or to the `id_token_hint` `sid` claim), fans out
 * Front-Channel and Back-Channel logout requests to RPs that participated in the session,
 * clears the cookie, and either redirects to a registered `post_logout_redirect_uri` (with
 * `state` echoed) or renders a logged-out confirmation page.
 */
@JsExportCompat
interface EndSessionGetHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID: String = "oauth2.logout.end-session-get"

        val ENDPOINT: HttpEndpointDescriptor =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/logout",
                operationId = "endSessionGet",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("logout"),
                summary = "OIDC RP-Initiated Logout 1.0 end-session endpoint (GET)",
            )
    }
}

/**
 * `POST /logout`: same end-session semantics as [EndSessionGetHttpEndpointCommand] but reads
 * the parameters from a `application/x-www-form-urlencoded` body. RP-Initiated Logout 1.0 §2
 * requires both GET and POST forms.
 */
@JsExportCompat
interface EndSessionPostHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID: String = "oauth2.logout.end-session-post"

        val ENDPOINT: HttpEndpointDescriptor =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/logout",
                consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                operationId = "endSessionPost",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("logout"),
                summary = "OIDC RP-Initiated Logout 1.0 end-session endpoint (POST)",
            )
    }
}
