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
import com.sphereon.openid.oid4vci.issuer.impl.http.command.ApprovePipelineSessionEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.ContributeAttributesEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.ContributeViaCallbackEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.EvaluateCompletenessEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.FailPipelineSourceEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetCredentialOfferEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetSessionAttributesEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.HandleCredentialEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.HandleDeferredCredentialEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.HandleNotificationEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.InitPipelineSessionEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.IssueNonceEndpointCommand
import com.sphereon.openid.oid4vci.issuer.impl.http.command.Oid4vciErrorRenderer
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerProtocolConfig
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
 * - **POST /sessions/{correlationId}/attributes** — Contribute attributes to a pipeline session
 * - **POST /sessions** — Initialise a new issuance pipeline session
 * - **GET /sessions/{correlationId}/completeness** — Evaluate attribute completeness for a pipeline session
 * - **GET /sessions/{correlationId}/attributes** — Read accumulated attributes for a pipeline session
 * - **POST /sessions/{correlationId}/callbacks/{callbackToken}** — Async-callback contribution by capability token
 * - **POST /sessions/{correlationId}/fail** — Mark a pipeline source's contribution as failed
 * - **POST /sessions/{correlationId}/approve** — Apply an approval-gate decision to a pipeline session
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
    private val contributeAttributesCommand: ContributeAttributesEndpointCommand,
    private val initPipelineSessionCommand: InitPipelineSessionEndpointCommand,
    private val evaluateCompletenessCommand: EvaluateCompletenessEndpointCommand,
    private val getSessionAttributesCommand: GetSessionAttributesEndpointCommand,
    private val contributeViaCallbackCommand: ContributeViaCallbackEndpointCommand,
    private val failPipelineSourceCommand: FailPipelineSourceEndpointCommand,
    private val approvePipelineSessionCommand: ApprovePipelineSessionEndpointCommand,
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
            contributeAttributesCommand,
            initPipelineSessionCommand,
            evaluateCompletenessCommand,
            getSessionAttributesCommand,
            contributeViaCallbackCommand,
            failPipelineSourceCommand,
            approvePipelineSessionCommand,
        )

    @ContributesTo(SessionScope::class)
    interface Graph {
        val oid4vciIssuerProtocolHttpAdapter: Oid4vciIssuerProtocolHttpAdapter
    }
}
