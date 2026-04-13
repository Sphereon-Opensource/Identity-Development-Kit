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

package com.sphereon.openid.oid4vci.holder.impl.http

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.holder.impl.http.command.BuildAuthRequestEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.CreateSessionEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.ExchangeAuthCodeEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.ExchangePreAuthCodeEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.GetSessionEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.InitiateIaeEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.RequestCredentialEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.RequestDeferredEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.RequestNonceEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.ResolveOfferEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.SendNotificationEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.SessionBuildAuthRequestEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.SessionCredentialEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.SessionDeferredPollEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.SessionExchangeAuthCodeEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.SessionIaeFollowUpEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.SessionIaeInitiateEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.SessionNotifyEndpointCommand
import com.sphereon.openid.oid4vci.holder.impl.http.command.SessionTokenEndpointCommand
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Command-backed HTTP adapter for the OID4VCI holder convenience REST API.
 *
 * Exposes client-side OID4VCI flows (offer resolution, token exchange, nonce,
 * credential request, deferred polling, notification, IAE, auth code) as a
 * local REST API. Callers (e.g., a wallet frontend) send requests to this
 * adapter, which drives the OID4VCI holder service against the remote issuer.
 *
 * Base path: /oid4vci/holder
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class Oid4vciHolderHttpAdapter(
    execution: SessionExecution,
    // Stateless endpoints
    private val resolveOfferCommand: ResolveOfferEndpointCommand,
    private val exchangePreAuthCodeCommand: ExchangePreAuthCodeEndpointCommand,
    private val requestNonceCommand: RequestNonceEndpointCommand,
    private val requestCredentialCommand: RequestCredentialEndpointCommand,
    private val requestDeferredCommand: RequestDeferredEndpointCommand,
    private val sendNotificationCommand: SendNotificationEndpointCommand,
    private val initiateIaeCommand: InitiateIaeEndpointCommand,
    private val buildAuthRequestCommand: BuildAuthRequestEndpointCommand,
    private val exchangeAuthCodeCommand: ExchangeAuthCodeEndpointCommand,
    // Session endpoints
    private val createSessionCommand: CreateSessionEndpointCommand,
    private val getSessionCommand: GetSessionEndpointCommand,
    private val sessionTokenCommand: SessionTokenEndpointCommand,
    private val sessionCredentialCommand: SessionCredentialEndpointCommand,
    private val sessionDeferredPollCommand: SessionDeferredPollEndpointCommand,
    private val sessionNotifyCommand: SessionNotifyEndpointCommand,
    private val sessionIaeInitiateCommand: SessionIaeInitiateEndpointCommand,
    private val sessionIaeFollowUpCommand: SessionIaeFollowUpEndpointCommand,
    private val sessionBuildAuthRequestCommand: SessionBuildAuthRequestEndpointCommand,
    private val sessionExchangeAuthCodeCommand: SessionExchangeAuthCodeEndpointCommand,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = "/oid4vci/holder",
            ),
    ) {
    companion object {
        const val ID: String = "OID4VCI_HOLDER"
    }

    override val endpointCommands: List<HttpEndpointCommand> =
        listOf(
            // Stateless
            resolveOfferCommand,
            exchangePreAuthCodeCommand,
            requestNonceCommand,
            requestCredentialCommand,
            requestDeferredCommand,
            sendNotificationCommand,
            initiateIaeCommand,
            buildAuthRequestCommand,
            exchangeAuthCodeCommand,
            // Session
            createSessionCommand,
            getSessionCommand,
            sessionTokenCommand,
            sessionCredentialCommand,
            sessionDeferredPollCommand,
            sessionNotifyCommand,
            sessionIaeInitiateCommand,
            sessionIaeFollowUpCommand,
            sessionBuildAuthRequestCommand,
            sessionExchangeAuthCodeCommand,
        )

    @ContributesTo(SessionScope::class)
    interface Graph {
        val oid4vciHolderHttpAdapter: Oid4vciHolderHttpAdapter
    }
}
