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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.dcql.store.DcqlQueryConfigurationStore
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriArgs
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriCommand
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.verifier.CreateSignedAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.verifier.CreateSignedAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.verifier.CreatedAuthorizationRequest
import com.sphereon.openid.oid4vp.verifier.DirectPostHandledResponse
import com.sphereon.openid.oid4vp.verifier.HandleDirectPostResponseArgs
import com.sphereon.openid.oid4vp.verifier.HandleDirectPostResponseCommand
import com.sphereon.openid.oid4vp.verifier.HolderBindingResult
import com.sphereon.openid.oid4vp.verifier.Oid4vpVerifierService
import com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.ParsedAuthorizationResponse
import com.sphereon.openid.oid4vp.verifier.RetrieveAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.RetrieveAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.RetrievedAuthorizationResponse
import com.sphereon.openid.oid4vp.verifier.SignedAuthorizationRequestResult
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.ValidationResult
import com.sphereon.openid.oid4vp.verifier.VerifyHolderBindingArgs
import com.sphereon.openid.oid4vp.verifier.VerifyHolderBindingCommand
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestUriHandler
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import com.sphereon.openid.oid4vp.verifier.store.ClientMetadataConfigurationStore
import com.sphereon.openid.oid4vp.verifier.store.ResponseCodeStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of Oid4vpRpService for OpenID4VP Relying Party (Verifier) operations.
 *
 * This service aggregates all RP commands and provides a unified interface for:
 * - Creating authorization requests with DCQL queries
 * - Parsing authorization responses with VP tokens
 * - Validating responses against original requests
 * - Verifying holder binding
 * - Building authorization request URIs
 *
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vpVerifierService>())
class Oid4VpVerifierServiceImpl(
    private val createAuthorizationRequestCommand: CreateAuthorizationRequestCommand,
    private val parseAuthorizationResponseCommand: ParseAuthorizationResponseCommand,
    private val validateAuthorizationResponseCommand: ValidateAuthorizationResponseCommand,
    private val verifyHolderBindingCommand: VerifyHolderBindingCommand,
    private val buildAuthorizationRequestUriCommand: BuildAuthorizationRequestUriCommand,
    private val createSignedAuthorizationRequestCommand: CreateSignedAuthorizationRequestCommand,
    private val handleDirectPostResponseCommand: HandleDirectPostResponseCommand,
    private val retrieveAuthorizationResponseCommand: RetrieveAuthorizationResponseCommand,
    override val responseCodeStore: ResponseCodeStore,
    override val authorizationSessionStore: AuthorizationSessionStore,
    override val dcqlQueryConfigurationStore: DcqlQueryConfigurationStore,
    override val clientMetadataConfigurationStore: ClientMetadataConfigurationStore,
    override val requestUriHandler: RequestUriHandler,
    execution: SessionExecution,
) : Oid4vpVerifierService {
    /**
     * Inner class exposing all commands for advanced use cases
     */
    inner class CommandsImpl : Oid4vpVerifierService.Commands {
        override val createAuthorizationRequest: CreateAuthorizationRequestCommand
            get() = this@Oid4VpVerifierServiceImpl.createAuthorizationRequestCommand
        override val parseAuthorizationResponse: ParseAuthorizationResponseCommand
            get() = this@Oid4VpVerifierServiceImpl.parseAuthorizationResponseCommand
        override val validateAuthorizationResponse: ValidateAuthorizationResponseCommand
            get() = this@Oid4VpVerifierServiceImpl.validateAuthorizationResponseCommand
        override val verifyHolderBinding: VerifyHolderBindingCommand
            get() = this@Oid4VpVerifierServiceImpl.verifyHolderBindingCommand
        override val buildAuthorizationRequestUri: BuildAuthorizationRequestUriCommand
            get() = this@Oid4VpVerifierServiceImpl.buildAuthorizationRequestUriCommand
        override val createSignedAuthorizationRequest: CreateSignedAuthorizationRequestCommand
            get() = this@Oid4VpVerifierServiceImpl.createSignedAuthorizationRequestCommand
        override val handleDirectPostResponse: HandleDirectPostResponseCommand
            get() = this@Oid4VpVerifierServiceImpl.handleDirectPostResponseCommand
        override val retrieveAuthorizationResponse: RetrieveAuthorizationResponseCommand
            get() = this@Oid4VpVerifierServiceImpl.retrieveAuthorizationResponseCommand
    }

    override val commands: Oid4vpVerifierService.Commands = CommandsImpl()

    // ========================================================================
    // Command Service Delegations
    // ========================================================================

    override suspend fun createAuthorizationRequest(args: CreateAuthorizationRequestArgs): IdkResult<CreatedAuthorizationRequest, IdkError> = createAuthorizationRequestCommand.execute(args)

    override suspend fun parseAuthorizationResponse(args: ParseAuthorizationResponseArgs): IdkResult<ParsedAuthorizationResponse, IdkError> = parseAuthorizationResponseCommand.execute(args)

    override suspend fun validateAuthorizationResponse(args: ValidateAuthorizationResponseArgs): IdkResult<ValidationResult, IdkError> = validateAuthorizationResponseCommand.execute(args)

    override suspend fun verifyHolderBinding(args: VerifyHolderBindingArgs): IdkResult<HolderBindingResult, IdkError> = verifyHolderBindingCommand.execute(args)

    override suspend fun buildAuthorizationRequestUri(args: BuildAuthorizationRequestUriArgs): IdkResult<StringResult, IdkError> = buildAuthorizationRequestUriCommand.execute(args)

    override suspend fun createSignedAuthorizationRequest(args: CreateSignedAuthorizationRequestArgs): IdkResult<SignedAuthorizationRequestResult, IdkError> =
        createSignedAuthorizationRequestCommand.execute(args)

    // ========================================================================
    // Response Code Protection Commands - OpenID4VP 1.0 Section 14.3.3
    // ========================================================================

    override suspend fun handleDirectPostResponse(args: HandleDirectPostResponseArgs): IdkResult<DirectPostHandledResponse, IdkError> = handleDirectPostResponseCommand.execute(args)

    override suspend fun retrieveAuthorizationResponse(args: RetrieveAuthorizationResponseArgs): IdkResult<RetrievedAuthorizationResponse, IdkError> =
        retrieveAuthorizationResponseCommand.execute(args)

    @ContributesTo(SessionScope::class)
    interface Graph {
        val oid4vpVerifierService: Oid4vpVerifierService
    }
}
