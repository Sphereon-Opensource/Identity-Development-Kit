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

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.common.model.TokenResponse

/**
 * Args for [HandleTokenRequestCommand]. Carries the form-encoded request body, raw HTTP
 * headers (for client authentication), and the full URL of the `/token` endpoint (for DPoP
 * `htu` binding).
 *
 * [baseUrlOverride] is the per-request base URL (typically `scheme://host`) resolved by the
 * HTTP shell from `Host` + `X-Forwarded-Proto`. It is propagated into the inner
 * `CreateAccessTokenArgs` / `CreateIdTokenArgs` so the issued `iss` claim matches what
 * discovery emits behind a proxy/tunnel when `serverConfig.issuer` is unset.
 */
data class HandleTokenRequestArgs(
    val requestBody: Map<String, List<String>>,
    val requestHeaders: Map<String, String>,
    val httpUrl: String,
    val baseUrlOverride: String? = null,
    /**
     * Leaf TLS client certificate (DER bytes) extracted by the HTTP shell when the token
     * endpoint accepts mTLS (RFC 8705 §2). When the AS terminates TLS itself this is the peer
     * cert from the engine's `ClientCertificatePrincipal`; when the AS sits behind a TLS-
     * terminating proxy it is read from the operator-configured forwarded-cert header. `null`
     * means the request did not arrive over mTLS (the common case for non-mTLS clients).
     *
     * Drives both the RFC 8705 §2 client-auth dispatch (`tls_client_auth` /
     * `self_signed_tls_client_auth`) and the §3 cert-bound access-token (`cnf.x5t#S256`)
     * minting, the latter conditional on
     * [com.sphereon.oauth2.server.authorization.model.ClientRegistration.tlsClientCertificateBoundAccessTokens]
     * or the server-wide
     * [com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig.tlsClientCertificateBoundAccessTokens].
     */
    val clientCertificateDer: ByteArray? = null,
)

/**
 * Orchestration command for `POST /token` (RFC 6749 §3.2: Token Endpoint). Routes the request
 * by grant type (authorization_code, refresh_token, client_credentials, token-exchange,
 * pre-authorized_code) and assembles the resulting [TokenResponse] including any optional
 * id_token (OIDC) and authorization_details (OID4VCI).
 */
interface HandleTokenRequestCommand : ServiceCommand<HandleTokenRequestArgs, TokenResponse, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.token.handle-token-request"
    }
}
