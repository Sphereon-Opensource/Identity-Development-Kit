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

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationResult

/**
 * Arguments for applying client authentication to HTTP requests
 *
 * @property config The client authentication configuration
 * @property tokenEndpoint The token endpoint URL (used for JWT audience)
 */
data class ApplyClientAuthenticationArgs(
    val config: ClientAuthenticationConfig,
    val tokenEndpoint: String,
)

/**
 * Command for applying client authentication to HTTP requests
 *
 * Implements OAuth 2.0 client authentication methods:
 * - client_secret_basic (RFC 6749 Section 2.3.1)
 * - client_secret_post (RFC 6749 Section 2.3.1)
 * - client_secret_jwt (RFC 7523)
 * - private_key_jwt (RFC 7523)
 * - none (public clients)
 * - attest_jwt_client_auth (draft spec)
 *
 * The command takes a ClientAuthenticationConfig and returns headers and body parameters
 * that should be added to the HTTP request.
 */
interface ApplyClientAuthenticationCommand : ServiceCommand<ApplyClientAuthenticationArgs, ClientAuthenticationResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.clientauth.apply"
    }
}
