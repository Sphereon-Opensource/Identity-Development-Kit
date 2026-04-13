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

package com.sphereon.openid.oid4vci.holder.impl

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.holder.BuildAuthorizationRequestCommand
import com.sphereon.openid.oid4vci.holder.CreateCredentialRequestProofCommand
import com.sphereon.openid.oid4vci.holder.ExchangeAuthorizationCodeCommand
import com.sphereon.openid.oid4vci.holder.ExchangePreAuthorizedCodeCommand
import com.sphereon.openid.oid4vci.holder.FollowUpIaeCommand
import com.sphereon.openid.oid4vci.holder.InitiateIaeCommand
import com.sphereon.openid.oid4vci.holder.ParseCredentialOfferCommand
import com.sphereon.openid.oid4vci.holder.PollDeferredCredentialCommand
import com.sphereon.openid.oid4vci.holder.RequestCredentialCommand
import com.sphereon.openid.oid4vci.holder.RequestCredentialWithFlowCommand
import com.sphereon.openid.oid4vci.holder.RequestDeferredCredentialCommand
import com.sphereon.openid.oid4vci.holder.RequestNonceCommand
import com.sphereon.openid.oid4vci.holder.ResolveCredentialOfferCommand
import com.sphereon.openid.oid4vci.holder.ResolveIssuerMetadataCommand
import com.sphereon.openid.oid4vci.holder.SelectAuthorizationServerCommand
import com.sphereon.openid.oid4vci.holder.SendNotificationCommand
import com.sphereon.openid.oid4vci.holder.SendNotificationWithRetryCommand
import com.sphereon.openid.oid4vci.holder.impl.flow.PollDeferredCredentialCommandImpl
import com.sphereon.openid.oid4vci.holder.impl.flow.RequestCredentialWithFlowCommandImpl
import com.sphereon.openid.oid4vci.holder.impl.flow.SendNotificationWithRetryCommandImpl
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface Oid4vciHolderCommandDescriptors {
    @Provides @IntoMap
    @StringKey(ParseCredentialOfferCommand.COMMAND_ID)
    fun parseCredentialOffer(impl: ParseCredentialOfferCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(ResolveIssuerMetadataCommand.COMMAND_ID)
    fun resolveIssuerMetadata(impl: ResolveIssuerMetadataCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(ResolveCredentialOfferCommand.COMMAND_ID)
    fun resolveCredentialOffer(impl: ResolveCredentialOfferCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(SelectAuthorizationServerCommand.COMMAND_ID)
    fun selectAuthorizationServer(impl: SelectAuthorizationServerCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(RequestNonceCommand.COMMAND_ID)
    fun requestNonce(impl: RequestNonceCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(ExchangePreAuthorizedCodeCommand.COMMAND_ID)
    fun exchangePreAuthorizedCode(impl: ExchangePreAuthorizedCodeCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CreateCredentialRequestProofCommand.COMMAND_ID)
    fun createCredentialRequestProof(impl: CreateCredentialRequestProofCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(RequestCredentialCommand.COMMAND_ID)
    fun requestCredential(impl: RequestCredentialCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(RequestDeferredCredentialCommand.COMMAND_ID)
    fun requestDeferredCredential(impl: RequestDeferredCredentialCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(SendNotificationCommand.COMMAND_ID)
    fun sendNotification(impl: SendNotificationCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(FollowUpIaeCommand.COMMAND_ID)
    fun followUpIae(impl: FollowUpIaeCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(InitiateIaeCommand.COMMAND_ID)
    fun initiateIae(impl: InitiateIaeCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(BuildAuthorizationRequestCommand.COMMAND_ID)
    fun buildAuthorizationRequest(impl: BuildAuthorizationRequestCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(ExchangeAuthorizationCodeCommand.COMMAND_ID)
    fun exchangeAuthorizationCode(impl: ExchangeAuthorizationCodeCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(PollDeferredCredentialCommand.COMMAND_ID)
    fun pollDeferredCredential(impl: PollDeferredCredentialCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(SendNotificationWithRetryCommand.COMMAND_ID)
    fun sendNotificationWithRetry(impl: SendNotificationWithRetryCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(RequestCredentialWithFlowCommand.COMMAND_ID)
    fun requestCredentialWithFlow(impl: RequestCredentialWithFlowCommandImpl): ServiceCommand<*, *> = impl
}
