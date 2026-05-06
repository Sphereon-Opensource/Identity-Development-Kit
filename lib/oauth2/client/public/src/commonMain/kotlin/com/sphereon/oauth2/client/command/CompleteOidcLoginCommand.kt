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

package com.sphereon.oauth2.client.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.IdTokenPayload
import com.sphereon.oauth2.common.model.TokenResponse
import kotlin.jvm.JvmOverloads

/**
 * Arguments for the full "complete OIDC login" flow that replaces ad-hoc
 * parse-exchange-validate chains on the callback side.
 *
 * The command takes the callback either as a full URL ([AuthorizationResponseSource.QUERY]) or
 * as a redirect URI + form-encoded body ([AuthorizationResponseSource.FORM_POST]). The stored
 * transaction is looked up by `state` from the [com.sphereon.oauth2.client.transaction.OidcLoginTransactionStore]
 * using `tenantId` as the partition key.
 *
 * @property clientId registered OAuth2 client identifier — used as expected aud/azp on the ID token.
 * @property clientAuthentication client authentication config for the token exchange.
 * @property callbackUrl either the full redirect URL (QUERY) or the redirect URI (FORM_POST).
 * @property callbackFormBody the `application/x-www-form-urlencoded` body — required when
 *   [responseSource] is FORM_POST.
 * @property responseSource where the authorization response parameters live.
 * @property tenantId optional multi-tenant partition key matching the `initiateOidcLogin` call.
 */
@JsExportCompat
data class CompleteOidcLoginArgs
    @JvmOverloads
    constructor(
        val clientId: String,
        val clientAuthentication: ClientAuthenticationConfig,
        val callbackUrl: String,
        val callbackFormBody: String? = null,
        val responseSource: AuthorizationResponseSource = AuthorizationResponseSource.QUERY,
        val tenantId: String? = null,
    )

/**
 * Typed result returned by [CompleteOidcLoginCommand].
 *
 * @property accessToken the access token the RP uses to call the resource server / UserInfo.
 * @property refreshToken the refresh token, if the AS issued one.
 * @property idToken the raw compact JWS ID token — useful for logging / debugging.
 * @property idTokenClaims the decoded and validated ID token payload.
 * @property tokenResponse the full token endpoint response (scope, token_type, expires_in, etc.).
 */
@JsExportCompat
data class OidcLoginResult
    @JvmOverloads
    constructor(
        val accessToken: String,
        val refreshToken: String?,
        val idToken: String,
        val idTokenClaims: IdTokenPayload,
        val tokenResponse: TokenResponse,
    )

/**
 * Full OIDC login-callback command: parse response → consume transaction state → exchange code →
 * validate ID token (JWKS-bound when possible) → return typed result.
 *
 * This is the recommended entry point for the callback side of the OIDC authorization-code flow;
 * callers that only need the legacy OAuth2-only token exchange should continue using
 * [ExchangeTokenCommand].
 */
@JsExportCompat
interface CompleteOidcLoginCommand : ServiceCommand<CompleteOidcLoginArgs, OidcLoginResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.oidclogin.complete"
    }
}
