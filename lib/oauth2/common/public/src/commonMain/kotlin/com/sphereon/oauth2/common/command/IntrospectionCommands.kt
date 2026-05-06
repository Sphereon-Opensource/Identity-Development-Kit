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

package com.sphereon.oauth2.common.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse

/**
 * Arguments for introspecting an OAuth 2.0 token
 *
 * @property authorizationServerMetadata The authorization server metadata
 * @property token The token to introspect
 * @property clientAuthentication The client authentication configuration
 * @property tokenTypeHint Optional hint about the type of token
 * @property additionalParameters Additional parameters to include in the request
 */
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
data class IntrospectTokenArgs(
    val authorizationServerMetadata: AuthorizationServerMetadata,
    val token: String,
    val clientAuthentication: ClientAuthenticationConfig,
    val tokenTypeHint: String? = null,
    val additionalParameters: Map<String, String> = emptyMap(),
)

/**
 * Command to introspect an OAuth 2.0 token.
 * RFC 7662 - OAuth 2.0 Token Introspection
 *
 * The introspection endpoint allows a resource server to query the
 * authorization server about the state and metadata of a token.
 */
@JsExportCompat
interface IntrospectTokenCommand : ServiceCommand<IntrospectTokenArgs, TokenIntrospectionResponse, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.token.introspect"
    }
}
