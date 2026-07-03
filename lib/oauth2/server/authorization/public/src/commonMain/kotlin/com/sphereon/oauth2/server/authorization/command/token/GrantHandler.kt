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

package com.sphereon.oauth2.server.authorization.command.token

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.TokenRequestData
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import com.sphereon.oauth2.server.authorization.wallet.WalletInstanceAttestationEvidence

/**
 * A grant-type-specific token handler. One handler per RFC 6749 / RFC 8693 / RFC 8628 / OID4VCI 1.1
 * grant: `authorization_code`, `refresh_token`, `client_credentials`,
 * `password`,
 * `urn:ietf:params:oauth:grant-type:token-exchange`,
 * `urn:ietf:params:oauth:grant-type:pre-authorized_code`,
 * `urn:ietf:params:oauth:grant-type:device_code`.
 *
 * The token-endpoint orchestrator parses the request, verifies the DPoP proof and client
 * authentication, builds a [GrantContext] carrying the cross-cutting state, then dispatches to the
 * first contributed handler whose [supports] returns true. Adding a new grant is a matter of
 * contributing one more handler to the `Set<GrantHandler>` multibinding: discovery picks it up via
 * [grantType] and dispatch picks it up via [supports].
 */
interface GrantHandler {
    /**
     * Wire string used in the request body's `grant_type` form parameter (RFC 6749 §4) and in
     * the discovery document's `grant_types_supported` (RFC 8414).
     */
    val grantType: String

    /**
     * Returns true when [params] is the [GrantParameters] variant this handler accepts.
     * The orchestrator picks the first contributed handler whose `supports` is true.
     */
    fun supports(params: GrantParameters): Boolean

    /**
     * Mints the [TokenResponse] for [params]. [context] carries cross-cutting inputs that the
     * orchestrator pre-resolves (parsed request, verified client id, DPoP proof thumbprint, mTLS
     * cert thumbprint, server config, request-time clock). Grant-specific verifier commands and
     * storage are pulled from [GrantContext.commands] or injected directly into the handler.
     */
    suspend fun handle(
        params: GrantParameters,
        context: GrantContext,
    ): IdkResult<TokenResponse, IdkError>
}

/**
 * Cross-cutting state for a single token-endpoint invocation, shared by every [GrantHandler].
 *
 * @property tokenRequest the parsed `/token` request, including the `grant_type` enum and the
 *                        per-grant [GrantParameters].
 * @property resolvedClientId the client id confirmed by client authentication; for grants that
 *                            authenticate via mTLS or attestation this is the resolved id, not
 *                            the raw `client_id` form parameter.
 * @property proofJkt JWK thumbprint from the verified DPoP proof (RFC 9449 §4) when the request
 *                    presented a proof; `null` otherwise. Handlers cross-check this against the
 *                    grant-specific binding (auth-code's `dpop_jkt` commitment, refresh-token
 *                    pinned key, subject-token `cnf.jkt`).
 * @property certThumbprintS256 base64url-encoded SHA-256 of the leaf TLS client certificate
 *                              (RFC 8705 §3) when the request arrived over mTLS AND the client or
 *                              server opted in to cert-bound tokens; `null` otherwise. Handlers
 *                              forward this to [com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs].
 * @property applied the [HandleTokenRequestArgs] post-`applyDuring`, used so handlers can read
 *                   the per-request `baseUrlOverride` for issuer resolution behind a proxy.
 * @property commands the [AuthorizationServerService.Commands] bundle exposing the AS sub-commands
 *                    (parse / verify / create access token / create refresh token / create token
 *                    response / create id token).
 * @property serverConfig the resolved server instance config; pre-resolved here so each handler
 *                        does not re-read it from the [com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider].
 * @property walletInstanceAttestation production Wallet Instance Attestation evidence accepted at
 *                                      token client authentication, when present.
 */
data class GrantContext(
    val tokenRequest: TokenRequestData,
    val resolvedClientId: String,
    val proofJkt: String?,
    val certThumbprintS256: String?,
    val applied: HandleTokenRequestArgs,
    val commands: AuthorizationServerService.Commands,
    val serverConfig: OAuth2ServerInstanceConfig,
    val walletInstanceAttestation: WalletInstanceAttestationEvidence? = null,
)
