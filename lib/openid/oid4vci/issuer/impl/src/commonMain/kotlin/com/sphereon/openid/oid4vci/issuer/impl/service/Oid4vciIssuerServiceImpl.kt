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

package com.sphereon.openid.oid4vci.issuer.impl.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import com.sphereon.openid.oid4vci.issuer.command.BuildIssuerMetadataArgs
import com.sphereon.openid.oid4vci.issuer.command.BuildIssuerMetadataCommand
import com.sphereon.openid.oid4vci.issuer.command.BuildSignedIssuerMetadataArgs
import com.sphereon.openid.oid4vci.issuer.command.BuildSignedIssuerMetadataCommand
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferCommand
import com.sphereon.openid.oid4vci.issuer.command.CreatedCredentialOffer
import com.sphereon.openid.oid4vci.issuer.command.HandleCredentialRequestArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleCredentialRequestCommand
import com.sphereon.openid.oid4vci.issuer.command.HandleDeferredCredentialRequestArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleDeferredCredentialRequestCommand
import com.sphereon.openid.oid4vci.issuer.command.HandleNotificationArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleNotificationCommand
import com.sphereon.openid.oid4vci.issuer.command.IssueNonceArgs
import com.sphereon.openid.oid4vci.issuer.command.IssueNonceCommand
import com.sphereon.openid.oid4vci.issuer.service.Oid4vciIssuerService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciIssuerService>())
class Oid4vciIssuerServiceImpl(
    private val createCredentialOfferCommand: CreateCredentialOfferCommand,
    private val buildIssuerMetadataCommand: BuildIssuerMetadataCommand,
    private val issueNonceCommand: IssueNonceCommand,
    private val handleCredentialRequestCommand: HandleCredentialRequestCommand,
    private val handleDeferredCredentialRequestCommand: HandleDeferredCredentialRequestCommand,
    private val handleNotificationCommand: HandleNotificationCommand,
    private val buildSignedIssuerMetadataCommand: BuildSignedIssuerMetadataCommand,
) : Oid4vciIssuerService {
    inner class CommandsImpl : Oid4vciIssuerService.Commands {
        override val createCredentialOffer = this@Oid4vciIssuerServiceImpl.createCredentialOfferCommand
        override val buildIssuerMetadata = this@Oid4vciIssuerServiceImpl.buildIssuerMetadataCommand
        override val issueNonce = this@Oid4vciIssuerServiceImpl.issueNonceCommand
        override val handleCredentialRequest = this@Oid4vciIssuerServiceImpl.handleCredentialRequestCommand
        override val handleDeferredCredentialRequest = this@Oid4vciIssuerServiceImpl.handleDeferredCredentialRequestCommand
        override val handleNotification = this@Oid4vciIssuerServiceImpl.handleNotificationCommand
        override val buildSignedIssuerMetadata = this@Oid4vciIssuerServiceImpl.buildSignedIssuerMetadataCommand
    }

    override val commands: Oid4vciIssuerService.Commands = CommandsImpl()

    override suspend fun createCredentialOffer(args: CreateCredentialOfferArgs): IdkResult<CreatedCredentialOffer, IdkError> = createCredentialOfferCommand.execute(args)

    override suspend fun buildIssuerMetadata(args: BuildIssuerMetadataArgs): IdkResult<CredentialIssuerMetadata, IdkError> = buildIssuerMetadataCommand.execute(args)

    override suspend fun issueNonce(args: IssueNonceArgs): IdkResult<NonceResponse, IdkError> = issueNonceCommand.execute(args)

    override suspend fun handleCredentialRequest(args: HandleCredentialRequestArgs): IdkResult<CredentialResponse, IdkError> = handleCredentialRequestCommand.execute(args)

    override suspend fun handleDeferredCredentialRequest(args: HandleDeferredCredentialRequestArgs): IdkResult<CredentialResponse, IdkError> = handleDeferredCredentialRequestCommand.execute(args)

    override suspend fun handleNotification(args: HandleNotificationArgs): IdkResult<Unit, IdkError> = handleNotificationCommand.execute(args)

    override suspend fun buildSignedIssuerMetadata(args: BuildSignedIssuerMetadataArgs): IdkResult<JwtCompactResult, IdkError> = buildSignedIssuerMetadataCommand.execute(args)
}
