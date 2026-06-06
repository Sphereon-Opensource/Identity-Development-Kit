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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.crypto.resolution.managed.ManagedOptsKid
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption
import com.sphereon.openid.oid4vci.holder.AuthorizationRequestResult
import com.sphereon.openid.oid4vci.holder.BuildAuthorizationRequestArgs
import com.sphereon.openid.oid4vci.holder.BuildAuthorizationRequestCommand
import com.sphereon.openid.oid4vci.holder.CreateCredentialRequestProofArgs
import com.sphereon.openid.oid4vci.holder.CreateCredentialRequestProofCommand
import com.sphereon.openid.oid4vci.holder.CreatedProof
import com.sphereon.openid.oid4vci.holder.ExchangeAuthorizationCodeArgs
import com.sphereon.openid.oid4vci.holder.ExchangeAuthorizationCodeCommand
import com.sphereon.openid.oid4vci.holder.ExchangePreAuthorizedCodeArgs
import com.sphereon.openid.oid4vci.holder.ExchangePreAuthorizedCodeCommand
import com.sphereon.openid.oid4vci.holder.FollowUpIaeArgs
import com.sphereon.openid.oid4vci.holder.FollowUpIaeCommand
import com.sphereon.openid.oid4vci.holder.IaeHolderResult
import com.sphereon.openid.oid4vci.holder.InitiateIaeArgs
import com.sphereon.openid.oid4vci.holder.InitiateIaeCommand
import com.sphereon.openid.oid4vci.holder.Oid4vciHolder
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderAdapter
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.openid.oid4vci.holder.ParseCredentialOfferArgs
import com.sphereon.openid.oid4vci.holder.ParseCredentialOfferCommand
import com.sphereon.openid.oid4vci.holder.RequestCredentialArgs
import com.sphereon.openid.oid4vci.holder.RequestCredentialCommand
import com.sphereon.openid.oid4vci.holder.RequestDeferredCredentialArgs
import com.sphereon.openid.oid4vci.holder.RequestDeferredCredentialCommand
import com.sphereon.openid.oid4vci.holder.RequestNonceArgs
import com.sphereon.openid.oid4vci.holder.RequestNonceCommand
import com.sphereon.openid.oid4vci.holder.ResolveCredentialOfferArgs
import com.sphereon.openid.oid4vci.holder.ResolveCredentialOfferCommand
import com.sphereon.openid.oid4vci.holder.ResolveIssuerMetadataArgs
import com.sphereon.openid.oid4vci.holder.ResolveIssuerMetadataCommand
import com.sphereon.openid.oid4vci.holder.ResolvedAuthorizationServer
import com.sphereon.openid.oid4vci.holder.ResolvedCredentialOffer
import com.sphereon.openid.oid4vci.holder.SelectAuthorizationServerArgs
import com.sphereon.openid.oid4vci.holder.SelectAuthorizationServerCommand
import com.sphereon.openid.oid4vci.holder.SendNotificationArgs
import com.sphereon.openid.oid4vci.holder.SendNotificationCommand
import com.sphereon.openid.oid4vci.holder.TokenResponseWithContext
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonObject

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciHolder>())
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciHolderService>())
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciHolderAdapter>())
class Oid4vciHolderServiceImpl(
    private val parseCredentialOfferCommand: ParseCredentialOfferCommand,
    private val resolveCredentialOfferCommand: ResolveCredentialOfferCommand,
    private val resolveIssuerMetadataCommand: ResolveIssuerMetadataCommand,
    private val selectAuthorizationServerCommand: SelectAuthorizationServerCommand,
    private val requestNonceCommand: RequestNonceCommand,
    private val exchangePreAuthorizedCodeCommand: ExchangePreAuthorizedCodeCommand,
    private val createCredentialRequestProofCommand: CreateCredentialRequestProofCommand,
    private val requestCredentialCommand: RequestCredentialCommand,
    private val requestDeferredCredentialCommand: RequestDeferredCredentialCommand,
    private val sendNotificationCommand: SendNotificationCommand,
    private val followUpIaeCommand: FollowUpIaeCommand,
    private val initiateIaeCommand: InitiateIaeCommand,
    private val buildAuthorizationRequestCommand: BuildAuthorizationRequestCommand,
    private val exchangeAuthorizationCodeCommand: ExchangeAuthorizationCodeCommand,
) : Oid4vciHolder {
    inner class CommandsImpl : Oid4vciHolder.Commands {
        override val parseCredentialOffer = this@Oid4vciHolderServiceImpl.parseCredentialOfferCommand
        override val resolveCredentialOffer = this@Oid4vciHolderServiceImpl.resolveCredentialOfferCommand
        override val resolveIssuerMetadata = this@Oid4vciHolderServiceImpl.resolveIssuerMetadataCommand
        override val selectAuthorizationServer = this@Oid4vciHolderServiceImpl.selectAuthorizationServerCommand
        override val requestNonce = this@Oid4vciHolderServiceImpl.requestNonceCommand
        override val exchangePreAuthorizedCode = this@Oid4vciHolderServiceImpl.exchangePreAuthorizedCodeCommand
        override val createCredentialRequestProof = this@Oid4vciHolderServiceImpl.createCredentialRequestProofCommand
        override val requestCredential = this@Oid4vciHolderServiceImpl.requestCredentialCommand
        override val requestDeferredCredential = this@Oid4vciHolderServiceImpl.requestDeferredCredentialCommand
        override val sendNotification = this@Oid4vciHolderServiceImpl.sendNotificationCommand
        override val followUpIae = this@Oid4vciHolderServiceImpl.followUpIaeCommand
        override val initiateIae = this@Oid4vciHolderServiceImpl.initiateIaeCommand
        override val buildAuthorizationRequest = this@Oid4vciHolderServiceImpl.buildAuthorizationRequestCommand
        override val exchangeAuthorizationCode = this@Oid4vciHolderServiceImpl.exchangeAuthorizationCodeCommand
    }

    override val commands: Oid4vciHolder.Commands = CommandsImpl()

    override suspend fun parseCredentialOffer(rawOffer: String): IdkResult<CredentialOffer, IdkError> = parseCredentialOfferCommand.execute(ParseCredentialOfferArgs(rawOffer = rawOffer))

    override suspend fun resolveCredentialOffer(offer: CredentialOffer): IdkResult<ResolvedCredentialOffer, IdkError> = resolveCredentialOfferCommand.execute(ResolveCredentialOfferArgs(offer = offer))

    override suspend fun resolveIssuerMetadata(issuerUrl: String): IdkResult<CredentialIssuerMetadata, IdkError> =
        resolveIssuerMetadataCommand.execute(ResolveIssuerMetadataArgs(issuerUrl = issuerUrl))

    override suspend fun selectAuthorizationServer(
        issuerMetadata: CredentialIssuerMetadata,
        preferredAuthorizationServer: String?,
    ): IdkResult<ResolvedAuthorizationServer, IdkError> =
        selectAuthorizationServerCommand.execute(
            SelectAuthorizationServerArgs(
                issuerMetadata = issuerMetadata,
                preferredAuthorizationServer = preferredAuthorizationServer,
            ),
        )

    override suspend fun requestNonce(nonceEndpoint: String): IdkResult<NonceResponse, IdkError> = requestNonceCommand.execute(RequestNonceArgs(nonceEndpoint = nonceEndpoint))

    override suspend fun exchangePreAuthorizedCode(
        tokenEndpoint: String,
        preAuthorizedCode: String,
        txCode: String?,
        clientId: String?,
        redirectUri: String?,
    ): IdkResult<TokenResponseWithContext, IdkError> =
        exchangePreAuthorizedCodeCommand.execute(
            ExchangePreAuthorizedCodeArgs(
                tokenEndpoint = tokenEndpoint,
                preAuthorizedCode = preAuthorizedCode,
                txCode = txCode,
                clientId = clientId,
                redirectUri = redirectUri,
            ),
        )

    override suspend fun createCredentialRequestProof(
        issuerUrl: String,
        cNonce: String?,
        signingKeyId: String,
        signingAlgorithm: String,
        clientId: String?,
        count: Int,
        keyInclusionMode: JwsIdentifierMode,
    ): IdkResult<CreatedProof, IdkError> =
        createCredentialRequestProofCommand.execute(
            CreateCredentialRequestProofArgs(
                issuerUrl = issuerUrl,
                cNonce = cNonce,
                signingKeyId = signingKeyId,
                signingAlgorithm = signingAlgorithm,
                clientId = clientId,
                count = count,
                keyInclusionMode = keyInclusionMode,
            ),
        )

    override suspend fun requestCredential(
        credentialEndpoint: String,
        accessToken: String,
        credentialConfigurationId: String?,
        credentialIdentifier: String?,
        proofs: CredentialRequestProofs?,
        credentialResponseEncryption: RequestedCredentialResponseEncryption?,
        requestEncryptionJwk: JsonObject?,
        requestEncryptionAlg: String?,
        requestEncryptionEnc: String?,
        decryptionKeyId: String?,
    ): IdkResult<CredentialResponse, IdkError> =
        requestCredentialCommand.execute(
            RequestCredentialArgs(
                credentialEndpoint = credentialEndpoint,
                accessToken = accessToken,
                credentialConfigurationId = credentialConfigurationId,
                credentialIdentifier = credentialIdentifier,
                proofs = proofs,
                credentialResponseEncryption = credentialResponseEncryption,
                requestEncryptionJwk = requestEncryptionJwk,
                requestEncryptionAlg = requestEncryptionAlg,
                requestEncryptionEnc = requestEncryptionEnc,
                decryptionKey = decryptionKeyId?.let { ManagedOptsKid(identifier = it) },
            ),
        )

    override suspend fun requestDeferredCredential(
        deferredCredentialEndpoint: String,
        accessToken: String,
        transactionId: String,
        credentialResponseEncryption: RequestedCredentialResponseEncryption?,
        requestEncryptionJwk: JsonObject?,
        requestEncryptionAlg: String?,
        requestEncryptionEnc: String?,
        decryptionKeyId: String?,
    ): IdkResult<CredentialResponse, IdkError> =
        requestDeferredCredentialCommand.execute(
            RequestDeferredCredentialArgs(
                deferredCredentialEndpoint = deferredCredentialEndpoint,
                accessToken = accessToken,
                transactionId = transactionId,
                credentialResponseEncryption = credentialResponseEncryption,
                requestEncryptionJwk = requestEncryptionJwk,
                requestEncryptionAlg = requestEncryptionAlg,
                requestEncryptionEnc = requestEncryptionEnc,
                decryptionKey = decryptionKeyId?.let { ManagedOptsKid(identifier = it) },
            ),
        )

    override suspend fun sendNotification(
        notificationEndpoint: String,
        accessToken: String,
        notificationId: String,
        event: CredentialNotificationEvent,
        eventDescription: String?,
    ): IdkResult<Unit, IdkError> =
        sendNotificationCommand.execute(
            SendNotificationArgs(
                notificationEndpoint = notificationEndpoint,
                accessToken = accessToken,
                notificationId = notificationId,
                event = event,
                eventDescription = eventDescription,
            ),
        )

    override suspend fun followUpIae(args: FollowUpIaeArgs): IdkResult<IaeHolderResult, IdkError> = followUpIaeCommand.execute(args)

    override suspend fun initiateIae(args: InitiateIaeArgs): IdkResult<IaeHolderResult, IdkError> = initiateIaeCommand.execute(args)

    override suspend fun buildAuthorizationRequest(
        authorizationEndpoint: String,
        clientId: String,
        redirectUri: String,
        credentialConfigurationIds: List<String>,
        scope: String?,
        issuerState: String?,
        usePar: Boolean,
        parEndpoint: String?,
        credentialIdentifiers: Map<String, List<String>>?,
        locations: List<String>?,
    ): IdkResult<AuthorizationRequestResult, IdkError> =
        buildAuthorizationRequestCommand.execute(
            BuildAuthorizationRequestArgs(
                authorizationEndpoint = authorizationEndpoint,
                clientId = clientId,
                redirectUri = redirectUri,
                credentialConfigurationIds = credentialConfigurationIds,
                scope = scope,
                issuerState = issuerState,
                usePar = usePar,
                parEndpoint = parEndpoint,
                credentialIdentifiers = credentialIdentifiers,
                locations = locations,
            ),
        )

    override suspend fun exchangeAuthorizationCode(
        tokenEndpoint: String,
        code: String,
        codeVerifier: String,
        redirectUri: String,
        clientId: String?,
    ): IdkResult<TokenResponseWithContext, IdkError> =
        exchangeAuthorizationCodeCommand.execute(
            ExchangeAuthorizationCodeArgs(
                tokenEndpoint = tokenEndpoint,
                code = code,
                codeVerifier = codeVerifier,
                redirectUri = redirectUri,
                clientId = clientId,
            ),
        )
}
