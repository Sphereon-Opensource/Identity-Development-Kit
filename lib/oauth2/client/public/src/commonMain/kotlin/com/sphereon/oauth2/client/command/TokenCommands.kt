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

package com.sphereon.oauth2.client.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.oauth2.common.model.TokenRequest
import com.sphereon.oauth2.common.model.TokenResponse

/**
 * Arguments for exchanging a token at the token endpoint
 *
 * @property tokenEndpoint The token endpoint URL (HTTPS URL)
 * @property request The token request with grant-specific parameters
 */
@JsExportCompat
data class ExchangeTokenArgs(
    val tokenEndpoint: String,
    val request: TokenRequest,
)

/**
 * Command for exchanging tokens at the token endpoint
 *
 * Implements RFC 6749 OAuth 2.0 token endpoint with support for:
 * - authorization_code grant (RFC 6749 Section 4.1.3)
 * - refresh_token grant (RFC 6749 Section 6)
 * - client_credentials grant (RFC 6749 Section 4.4.2)
 * - pre-authorized_code grant (OpenID4VCI)
 *
 * Includes support for:
 * - PKCE verification (RFC 7636)
 * - Resource Indicators (RFC 8707)
 * - DPoP (RFC 9449)
 */
@JsExportCompat
interface ExchangeTokenCommand : ServiceCommand<ExchangeTokenArgs, TokenResponse> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.token.exchange"
    }
}
