/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.client.impl.authorization

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.CreateAuthorizationRequestUrlCommand
import com.sphereon.oauth2.client.command.CreateAuthorizationRequestUrlOptions
import com.sphereon.oauth2.client.command.ParseAuthorizationResponseArgs
import com.sphereon.oauth2.client.command.ParseAuthorizationResponseCommand
import com.sphereon.oauth2.client.command.ParsedAuthorizationResponse
import com.sphereon.oauth2.client.model.AuthorizationRequestUrlResult
import com.sphereon.oauth2.client.service.AuthorizationService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of AuthorizationService that delegates to command implementations
 *
 * This service follows the Command/Service pattern for consistency with other IDK services
 * like PkceService, MetadataService, SdJwtService, and JwtService.
 *
 * @property createAuthorizationRequestUrlCommand Command for creating authorization URLs
 * @property parseAuthorizationResponseCommand Command for parsing authorization responses
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AuthorizationService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthorizationServiceImpl", exact = true)
class AuthorizationServiceImpl(
    private val createAuthorizationRequestUrlCommand: CreateAuthorizationRequestUrlCommand,
    private val parseAuthorizationResponseCommand: ParseAuthorizationResponseCommand,
) : AuthorizationService {
    /**
     * Inner class exposing commands for direct access
     */
    inner class CommandsImpl : AuthorizationService.Commands {
        override val createAuthorizationRequestUrl = this@AuthorizationServiceImpl.createAuthorizationRequestUrlCommand
        override val parseAuthorizationResponse = this@AuthorizationServiceImpl.parseAuthorizationResponseCommand
    }

    override val commands: AuthorizationService.Commands = CommandsImpl()

    /**
     * Create authorization request URL
     *
     * @param options Options for creating the authorization request URL
     * @return IdkResult containing the authorization URL and associated data
     */
    override suspend fun createAuthorizationRequestUrl(options: CreateAuthorizationRequestUrlOptions): IdkResult<AuthorizationRequestUrlResult, IdkError> =
        createAuthorizationRequestUrlCommand.execute(options)

    /**
     * Parse authorization response from redirect URL
     *
     * @param redirectUrl The full redirect URL with query parameters
     * @return IdkResult containing parsed authorization response
     */
    override suspend fun parseAuthorizationResponse(redirectUrl: String): IdkResult<ParsedAuthorizationResponse, IdkError> =
        parseAuthorizationResponseCommand.execute(ParseAuthorizationResponseArgs(redirectUrl))

    @ContributesTo(scope = SessionScope::class)
    interface Graph {
        val authorizationService: AuthorizationService
    }
}
