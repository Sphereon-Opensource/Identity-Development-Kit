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
import com.sphereon.core.api.http.command.RoutableSlugLookup
import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.context.MutableResolvedTenantIdProvider
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.MutableOAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceResolver
import com.sphereon.oauth2.server.authorization.command.userinfo.UserInfoHttpEndpointCommand
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * UserInfo surface for the OAuth2 Authorization Server (OIDC).
 *
 * Routes:
 * - `GET /userinfo` (OpenID Connect Core 1.0 §5.3)
 * - `POST /userinfo` (OpenID Connect Core 1.0 §5.3)
 *
 * Gated by the per-server `oidc` FeaturePolicy: when disabled the underlying endpoint command
 * returns 404.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class OAuth2UserInfoHttpAdapter(
    execution: SessionExecution,
    asInstanceResolver: OAuth2ServerInstanceResolver,
    asInstanceIdProvider: MutableOAuth2ServerInstanceIdProvider,
    slugLookup: RoutableSlugLookup,
    tenantIdProvider: MutableResolvedTenantIdProvider,
    private val userInfoEndpointCommand: UserInfoHttpEndpointCommand,
) : AbstractOAuth2HttpAdapter(
        id = ID,
        execution = execution,
        mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/"),
        asInstanceResolver = asInstanceResolver,
        asInstanceIdProvider = asInstanceIdProvider,
        slugLookup = slugLookup,
        tenantIdProvider = tenantIdProvider,
        tenantPathPolicy = TenantPathPolicy.LeadingSlug(maxDepth = 2),
    ) {
    companion object {
        const val ID: String = "oauth2.as.userinfo"
    }

    override val endpointCommands: List<HttpEndpointCommand> = listOf(userInfoEndpointCommand)
}
