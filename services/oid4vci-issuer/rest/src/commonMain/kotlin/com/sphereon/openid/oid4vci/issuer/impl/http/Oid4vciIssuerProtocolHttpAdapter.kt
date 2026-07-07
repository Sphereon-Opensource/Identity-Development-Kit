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

package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.RoutableSlugLookup
import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.context.MutableResolvedTenantIdProvider
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerProtocolConfig
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetCredentialOfferEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.HandleCredentialEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.HandleDeferredCredentialEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.HandleNotificationEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.IssueNonceEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.Oid4vciErrorRenderer
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Command-backed HTTP adapter for OID4VCI issuer protocol endpoints.
 *
 * - **GET /credentials/offers/{offerId}** — Retrieve credential offer
 * - **POST /nonce** — Issue nonce for credential request proof
 * - **POST /credential** — Handle credential request
 * - **POST /deferredCredential** — Handle deferred credential request
 * - **POST /notification** — Handle credential notification
 *
 * Framework-agnostic. Authentication is delegated to the platform server.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class Oid4vciIssuerProtocolHttpAdapter(
    execution: SessionExecution,
    slugLookup: RoutableSlugLookup,
    tenantIdProvider: MutableResolvedTenantIdProvider,
    appConfig: AppConfigService,
    private val credentialOfferCommand: GetCredentialOfferEndpointCommand,
    private val nonceCommand: IssueNonceEndpointCommand,
    private val credentialCommand: HandleCredentialEndpointCommand,
    private val deferredCredentialCommand: HandleDeferredCredentialEndpointCommand,
    private val notificationCommand: HandleNotificationEndpointCommand,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = resolveBasePath(appConfig),
            ),
        tenantPathPolicy = TenantPathPolicy.LeadingSlug(maxDepth = 2),
        errorRenderer = Oid4vciErrorRenderer(),
    ) {
    override val routableSlugLookup: RoutableSlugLookup = slugLookup
    override val resolvedTenantIdProvider: MutableResolvedTenantIdProvider = tenantIdProvider

    companion object {
        const val ID: String = "OID4VCI_ISSUER"

        const val BASE_PATH_KEY: String = Oid4vciIssuerProtocolConfig.BASE_PATH_KEY

        /** Normalised protocol base path from config: "" (root) or a leading-slash path with no trailing slash. */
        fun resolveBasePath(appConfig: AppConfigService): String = Oid4vciIssuerProtocolConfig.resolveBasePath(appConfig)
    }

    override val endpointCommands: List<HttpEndpointCommand> =
        listOf(
            credentialOfferCommand,
            nonceCommand,
            credentialCommand,
            deferredCredentialCommand,
            notificationCommand,
        )

    @ContributesTo(SessionScope::class)
    interface Graph {
        val oid4vciIssuerProtocolHttpAdapter: Oid4vciIssuerProtocolHttpAdapter
    }
}
