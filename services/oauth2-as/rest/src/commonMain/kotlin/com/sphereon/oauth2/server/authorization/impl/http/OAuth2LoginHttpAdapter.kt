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

package com.sphereon.oauth2.server.authorization.impl.http

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.MutableOAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceResolver
import com.sphereon.oauth2.server.authorization.command.login.LoginAssetHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.login.LoginCancelHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.login.LoginPageHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.login.LoginSubmitHttpEndpointCommand
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Login surface for the OAuth2 Authorization Server's first-party browser-login page.
 *
 * Routes:
 * - `GET /login` (renders the form for a pending authorization session, Group J)
 * - `POST /login` (validates credentials, mints `OidcLoginSession`, sets cookie, redirects back)
 * - `GET /login/assets/{...}` (serves CSS / SVG assets bundled with the login renderer)
 *
 * Resolves the active AS instance through [OAuth2ServerInstanceResolver] like the other
 * per-area adapters in this family so per-instance config (`oauth2.servers.<asId>.session.*`)
 * routes correctly.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class OAuth2LoginHttpAdapter(
    execution: SessionExecution,
    asInstanceResolver: OAuth2ServerInstanceResolver,
    asInstanceIdProvider: MutableOAuth2ServerInstanceIdProvider,
    private val loginPageEndpointCommand: LoginPageHttpEndpointCommand,
    private val loginSubmitEndpointCommand: LoginSubmitHttpEndpointCommand,
    private val loginCancelEndpointCommand: LoginCancelHttpEndpointCommand,
    private val loginAssetEndpointCommand: LoginAssetHttpEndpointCommand,
) : AbstractOAuth2HttpAdapter(
        id = ID,
        execution = execution,
        mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/"),
        asInstanceResolver = asInstanceResolver,
        asInstanceIdProvider = asInstanceIdProvider,
        tenantPathPolicy = TenantPathPolicy.LeadingSlug(maxDepth = 2),
    ) {
    companion object {
        const val ID: String = "OAUTH2_AS_LOGIN"
    }

    override val endpointCommands: List<HttpEndpointCommand> =
        listOf(
            loginPageEndpointCommand,
            loginSubmitEndpointCommand,
            loginCancelEndpointCommand,
            loginAssetEndpointCommand,
        )
}
