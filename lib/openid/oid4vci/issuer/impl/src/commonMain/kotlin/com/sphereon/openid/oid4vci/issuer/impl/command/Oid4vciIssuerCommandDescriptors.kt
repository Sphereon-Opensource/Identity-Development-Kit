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

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.command.BuildIssuerMetadataCommand
import com.sphereon.openid.oid4vci.issuer.command.BuildSignedIssuerMetadataCommand
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferCommand
import com.sphereon.openid.oid4vci.issuer.command.HandleCredentialRequestCommand
import com.sphereon.openid.oid4vci.issuer.command.HandleDeferredCredentialRequestCommand
import com.sphereon.openid.oid4vci.issuer.command.HandleNotificationCommand
import com.sphereon.openid.oid4vci.issuer.command.IssueNonceCommand
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface Oid4vciIssuerCommandDescriptors {
    @Provides @IntoMap
    @StringKey(CreateCredentialOfferCommand.COMMAND_ID)
    fun createCredentialOffer(impl: CreateCredentialOfferCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(BuildIssuerMetadataCommand.COMMAND_ID)
    fun buildIssuerMetadata(impl: BuildIssuerMetadataCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(IssueNonceCommand.COMMAND_ID)
    fun issueNonce(impl: IssueNonceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(HandleCredentialRequestCommand.COMMAND_ID)
    fun handleCredentialRequest(impl: HandleCredentialRequestCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(HandleDeferredCredentialRequestCommand.COMMAND_ID)
    fun handleDeferredCredentialRequest(impl: HandleDeferredCredentialRequestCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(HandleNotificationCommand.COMMAND_ID)
    fun handleNotification(impl: HandleNotificationCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(BuildSignedIssuerMetadataCommand.COMMAND_ID)
    fun buildSignedIssuerMetadata(impl: BuildSignedIssuerMetadataCommandImpl): ServiceCommand<*, *, *> = impl
}
