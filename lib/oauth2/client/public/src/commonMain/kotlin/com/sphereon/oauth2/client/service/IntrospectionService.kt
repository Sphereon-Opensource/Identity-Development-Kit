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

package com.sphereon.oauth2.client.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.oauth2.common.command.IntrospectTokenArgs
import com.sphereon.oauth2.common.command.IntrospectTokenCommand
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsName
import kotlin.native.ObjCName

/**
 * Service for OAuth 2.0 token introspection operations.
 * RFC 7662 - OAuth 2.0 Token Introspection
 *
 * Provides access to:
 * - Token introspection for access tokens and refresh tokens
 * - Token metadata and validity checking
 *
 * This service follows the Command/Service pattern, delegating to command implementations
 * for testability and consistency with other IDK services.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("IntrospectionService", exact = true)
@JsExportCompat
interface IntrospectionService {
    suspend fun introspectToken(args: IntrospectTokenArgs): IdkResult<TokenIntrospectionResponse, IdkError>

    /**
     * Provides access to the underlying commands for advanced usage scenarios
     */
    val commands: Commands

    /**
     * Container for all introspection commands
     */
    @JsExportIgnoreCompat
    interface Commands {
        val introspectToken: IntrospectTokenCommand
    }

    /**
     * Convenience method for introspecting a token with individual parameters.
     */
    @JsName("introspectTokenWithParams")
    suspend fun introspectToken(
        authorizationServerMetadata: AuthorizationServerMetadata,
        token: String,
        clientAuthentication: ClientAuthenticationConfig,
        tokenTypeHint: String? = null,
        additionalParameters: Map<String, String> = emptyMap(),
    ): IdkResult<TokenIntrospectionResponse, IdkError> =
        introspectToken(
            IntrospectTokenArgs(
                authorizationServerMetadata = authorizationServerMetadata,
                token = token,
                clientAuthentication = clientAuthentication,
                tokenTypeHint = tokenTypeHint,
                additionalParameters = additionalParameters,
            ),
        )
}
