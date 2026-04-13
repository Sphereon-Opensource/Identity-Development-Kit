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

package com.sphereon.openid.oid4vci.holder.impl.http.describe

import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.openid.oid4vci.holder.impl.http.Oid4vciHolderHttpAdapter
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
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * AppScope descriptor provider for [Oid4vciHolderHttpAdapter].
 *
 * Provides metadata about the holder REST endpoints so the
 * HttpAdapterCatalog can route incoming requests to this adapter.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class Oid4vciHolderDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = Oid4vciHolderHttpAdapter.ID

    private val basePath = "/oid4vci/holder"

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = basePath,
                ),
            endpoints =
                listOf(
                    // Stateless
                    ResolveOfferEndpointCommand.ENDPOINT,
                    ExchangePreAuthCodeEndpointCommand.ENDPOINT,
                    RequestNonceEndpointCommand.ENDPOINT,
                    RequestCredentialEndpointCommand.ENDPOINT,
                    RequestDeferredEndpointCommand.ENDPOINT,
                    SendNotificationEndpointCommand.ENDPOINT,
                    InitiateIaeEndpointCommand.ENDPOINT,
                    BuildAuthRequestEndpointCommand.ENDPOINT,
                    ExchangeAuthCodeEndpointCommand.ENDPOINT,
                    // Session
                    CreateSessionEndpointCommand.ENDPOINT,
                    GetSessionEndpointCommand.ENDPOINT,
                    SessionTokenEndpointCommand.ENDPOINT,
                    SessionCredentialEndpointCommand.ENDPOINT,
                    SessionDeferredPollEndpointCommand.ENDPOINT,
                    SessionNotifyEndpointCommand.ENDPOINT,
                    SessionIaeInitiateEndpointCommand.ENDPOINT,
                    SessionIaeFollowUpEndpointCommand.ENDPOINT,
                    SessionBuildAuthRequestEndpointCommand.ENDPOINT,
                    SessionExchangeAuthCodeEndpointCommand.ENDPOINT,
                ).map { endpoint ->
                    endpoint.copy(pathPattern = basePath + endpoint.pathPattern)
                },
        )
}
