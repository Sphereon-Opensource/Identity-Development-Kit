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
import com.sphereon.oauth2.server.authorization.command.device.DeviceVerificationApprovalHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.device.DeviceVerificationApprovalSubmitHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.device.DeviceVerificationEntryHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.device.DeviceVerificationSubmitHttpEndpointCommand
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * RFC 8628 §3.3 user-interaction surface for the device flow. Hosts the four endpoints the user
 * lands on after the device shows them `verification_uri` / `verification_uri_complete`:
 *
 * Routes:
 *  - `GET /device` (entry form)
 *  - `POST /device` (user-code submit)
 *  - `GET /device/approve` (consent prompt)
 *  - `POST /device/approve` (allow / deny submit)
 *
 * The device side's `/device_authorization` (issuance) is mounted by [OAuth2DeviceAuthorizationHttpAdapter];
 * the token-side polling is part of [OAuth2TokenHttpAdapter]'s `/token` route via the `DeviceCode`
 * branch of `HandleTokenRequestCommandImpl`. This adapter is the user-facing half of the flow.
 *
 * Login session reuse is implicit: the submit and approval endpoints read the existing
 * `oidc_login_sid` cookie via `OidcLoginSessionIdProvider` and 302 to `/login` with a `return_url`
 * pointing back at `/device/approve` when no session is active, so the device user is funnelled
 * through the same login surface as `/authorize`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class OAuth2DeviceVerificationHttpAdapter(
    execution: SessionExecution,
    asInstanceResolver: OAuth2ServerInstanceResolver,
    asInstanceIdProvider: MutableOAuth2ServerInstanceIdProvider,
    slugLookup: RoutableSlugLookup,
    tenantIdProvider: MutableResolvedTenantIdProvider,
    private val deviceEntryEndpointCommand: DeviceVerificationEntryHttpEndpointCommand,
    private val deviceSubmitEndpointCommand: DeviceVerificationSubmitHttpEndpointCommand,
    private val deviceApprovalEndpointCommand: DeviceVerificationApprovalHttpEndpointCommand,
    private val deviceApprovalSubmitEndpointCommand: DeviceVerificationApprovalSubmitHttpEndpointCommand,
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
        const val ID: String = "oauth2.as.device-verification"
    }

    override val endpointCommands: List<HttpEndpointCommand> =
        listOf(
            deviceEntryEndpointCommand,
            deviceSubmitEndpointCommand,
            deviceApprovalEndpointCommand,
            deviceApprovalSubmitEndpointCommand,
        )
}
