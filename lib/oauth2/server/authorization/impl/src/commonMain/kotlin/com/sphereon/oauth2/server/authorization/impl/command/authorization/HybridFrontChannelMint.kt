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

package com.sphereon.oauth2.server.authorization.impl.command.authorization

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.common.model.ResponseType
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenArgs
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService

/**
 * Front-channel mint output for OIDC Core §3.3 Hybrid Flow. `null` for either field means
 * the requested response_type didn't ask for that token.
 *
 * @property idToken Front-channel id_token, signed and ready to embed in fragment / form_post.
 *   When the auth code is also being returned, this id_token's `c_hash` already binds it to
 *   the code (per OIDC §3.3.2.11). When a front-channel access token is also being returned,
 *   `at_hash` binds it likewise.
 * @property accessToken Front-channel access token (Bearer, by definition — DPoP requires the
 *   proof header which the front-channel redirect can't carry).
 * @property accessTokenExpiresIn Lifetime of the front-channel access token in seconds.
 */
internal data class FrontChannelTokens(
    val idToken: String? = null,
    val accessToken: String? = null,
    val accessTokenExpiresIn: Int? = null,
)

/**
 * Parse a session's stored `responseType` (space-separated string from the original request)
 * back into a typed set. Unknown tokens are dropped — the verifier already rejected those
 * upstream, so we'd only encounter spec-recognised values here.
 */
internal fun AuthorizationSession.responseTypeSet(): Set<ResponseType> =
    responseType
        .split(" ")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { ResponseType.fromValue(it) }
        .toSet()

/**
 * OIDC Core §3.3 — front-channel mint at the /authorize exit. Decisions are driven by the
 * session's response_type:
 *
 *  - `code` only → both fields null; caller emits a plain code response.
 *  - `code id_token` → mint id_token (with `c_hash` binding to the code).
 *  - `code token` → mint access_token only (no id_token).
 *  - `code id_token token` → mint both; id_token also carries `at_hash`.
 *
 * Returned tokens are NOT stored under refresh-token rows (front-channel is short-lived,
 * intentionally). The auth-code grant at /token will mint a SECOND id_token + access_token
 * for back-channel use.
 *
 * @param session Stored session (carries clientId, scope, nonce, response_type).
 * @param code The just-minted authorization code (used for `c_hash` on the id_token).
 * @param subject Resolved subject identifier.
 * @param authTime Unix-second timestamp of the user authentication event.
 * @param acr / [amr] Authentication context class / methods, when known.
 * @param baseUrlOverride Per-request issuer base when `serverConfig.issuer` is unset.
 */
internal suspend fun mintFrontChannelTokens(
    service: AuthorizationServerService,
    session: AuthorizationSession,
    code: String,
    subject: String,
    authTime: Long?,
    acr: String?,
    amr: List<String>?,
    baseUrlOverride: String?,
): IdkResult<FrontChannelTokens, IdkError> {
    val responseTypes = session.responseTypeSet()
    val needsIdToken = ResponseType.ID_TOKEN in responseTypes
    val needsAccessToken = ResponseType.TOKEN in responseTypes
    if (!needsIdToken && !needsAccessToken) {
        return Ok(FrontChannelTokens())
    }

    // Mint the access token first when needed — its value is required for `at_hash` on the
    // id_token (when both are requested). Lifetime is the standard access-token lifetime;
    // CreateAccessTokenArgs.expiresInSeconds defaults to 3600.
    val accessTokenExpiresIn = ACCESS_TOKEN_LIFETIME_SECONDS
    val accessToken: String? =
        if (needsAccessToken) {
            service.commands.createAccessToken
                .execute(
                    CreateAccessTokenArgs(
                        subject = subject,
                        clientId = session.clientId,
                        scope = session.scope,
                        expiresInSeconds = accessTokenExpiresIn,
                        baseUrlOverride = baseUrlOverride,
                    ),
                ).getOrElse { return Err(it) }
                .value
        } else {
            null
        }

    val idToken: String? =
        if (needsIdToken) {
            service.commands.createIdToken
                .execute(
                    CreateIdTokenArgs(
                        subject = subject,
                        clientId = session.clientId,
                        nonce = session.nonce,
                        authTime = authTime,
                        acr = acr,
                        amr = amr,
                        // c_hash binding to the code (OIDC §3.3.2.11). Always set when the
                        // code is being returned alongside (Hybrid Flow always returns code).
                        authorizationCode = code,
                        // at_hash binding to the access token (OIDC §3.3.2.10) when present.
                        accessToken = accessToken,
                        sessionId = session.sessionId,
                        baseUrlOverride = baseUrlOverride,
                    ),
                ).getOrElse { return Err(it) }
                .value
        } else {
            null
        }

    return Ok(
        FrontChannelTokens(
            idToken = idToken,
            accessToken = accessToken,
            accessTokenExpiresIn = if (accessToken != null) accessTokenExpiresIn else null,
        ),
    )
}

private const val ACCESS_TOKEN_LIFETIME_SECONDS = 3600
