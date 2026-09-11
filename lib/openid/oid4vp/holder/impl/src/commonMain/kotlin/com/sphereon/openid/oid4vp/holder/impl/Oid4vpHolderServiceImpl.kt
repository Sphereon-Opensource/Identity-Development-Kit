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

package com.sphereon.openid.oid4vp.holder.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.holder.CreateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.holder.CreateAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.holder.DigitalCredentialsAuthorizationRequest
import com.sphereon.openid.oid4vp.holder.JarmOptions
import com.sphereon.openid.oid4vp.holder.Oid4vpHolder
import com.sphereon.openid.oid4vp.holder.Oid4vpHolder.Commands
import com.sphereon.openid.oid4vp.holder.Oid4vpHolderAdapter
import com.sphereon.openid.oid4vp.holder.Oid4vpHolderService
import com.sphereon.openid.oid4vp.holder.PreparedPresentation
import com.sphereon.openid.oid4vp.holder.ParseAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.holder.ParseAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.holder.ResolveAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.openid.oid4vp.holder.SubmissionResult
import com.sphereon.openid.oid4vp.holder.SubmitAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.holder.SubmitAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.holder.WalletConfig
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of the OID4VP Holder service.
 *
 * This service handles wallet/holder operations for OpenID4VP:
 * - Parsing and resolving authorization requests
 * - Creating authorization responses with VP tokens
 * - Submitting responses to verifiers
 *
 * Reference: OpenID4VP 1.0
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vpHolder>())
@ContributesBinding(SessionScope::class, binding = binding<Oid4vpHolderService>())
@ContributesBinding(SessionScope::class, binding = binding<Oid4vpHolderAdapter>())
class Oid4vpHolderServiceImpl(
    private val parseAuthorizationRequestCommand: ParseAuthorizationRequestCommand,
    private val resolveAuthorizationRequestCommand: ResolveAuthorizationRequestCommand,
    private val createAuthorizationResponseCommand: CreateAuthorizationResponseCommand,
    private val submitAuthorizationResponseCommand: SubmitAuthorizationResponseCommand,
) : Oid4vpHolder {
    inner class CommandsImpl : Commands {
        override val parseAuthorizationRequest: ParseAuthorizationRequestCommand = this@Oid4vpHolderServiceImpl.parseAuthorizationRequestCommand
        override val resolveAuthorizationRequest: ResolveAuthorizationRequestCommand = this@Oid4vpHolderServiceImpl.resolveAuthorizationRequestCommand
        override val createAuthorizationResponse: CreateAuthorizationResponseCommand = this@Oid4vpHolderServiceImpl.createAuthorizationResponseCommand
        override val submitAuthorizationResponse: SubmitAuthorizationResponseCommand = this@Oid4vpHolderServiceImpl.submitAuthorizationResponseCommand
    }

    override val commands: Commands = CommandsImpl()

    override suspend fun parseAuthorizationRequest(
        requestUri: String,
        walletConfig: WalletConfig?,
    ): IdkResult<AuthorizationRequest, IdkError> = parseAuthorizationRequestCommand.execute(ParseAuthorizationRequestArgs(requestUri, walletConfig))

    override suspend fun parseDigitalCredentialsAuthorizationRequest(
        request: DigitalCredentialsAuthorizationRequest,
        walletConfig: WalletConfig?,
    ): IdkResult<AuthorizationRequest, IdkError> =
        parseAuthorizationRequestCommand.execute(
            ParseAuthorizationRequestArgs(walletConfig = walletConfig, digitalCredentialsRequest = request),
        )

    override suspend fun resolveAuthorizationRequest(request: AuthorizationRequest): IdkResult<ResolvedOid4vpRequest, IdkError> = resolveAuthorizationRequestCommand.execute(request)

    override suspend fun createAuthorizationResponse(
        request: ResolvedOid4vpRequest,
        selectedCredentials: List<SelectedCredential>,
        preparedPresentations: List<PreparedPresentation>,
    ): IdkResult<AuthorizationResponse, IdkError> = createAuthorizationResponseCommand.execute(CreateAuthorizationResponseArgs(request, selectedCredentials, preparedPresentations))

    override suspend fun submitAuthorizationResponse(
        resolvedRequest: ResolvedOid4vpRequest,
        response: AuthorizationResponse,
        responseMode: ResponseMode?,
        jarmOptions: JarmOptions?,
    ): IdkResult<SubmissionResult, IdkError> = submitAuthorizationResponseCommand.execute(SubmitAuthorizationResponseArgs(resolvedRequest, response, responseMode, jarmOptions))
}
