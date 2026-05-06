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

package com.sphereon.oauth2.server.authorization.command.logout

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.StringResult

/**
 * Inputs to [CreateLogoutTokenCommand]. Carries everything required by OIDC
 * Back-Channel Logout 1.0 §2.4 to mint a `logout_token` JWT for one RP.
 *
 * @property issuer The AS issuer URL (matches discovery `issuer`).
 * @property clientId The RP's `client_id`. Becomes the `aud` claim.
 * @property sub User identifier. Either [sub] or [sid] (or both) MUST be present
 *   per BC §2.4. The IDK orchestrator emits both whenever the participating RP
 *   has [com.sphereon.oauth2.server.authorization.model.ClientRegistration.backchannelLogoutSessionRequired]
 *   = `true`, otherwise just `sub`.
 * @property sid Session id matching the `sid` claim previously emitted in the
 *   id_token. `null` when the RP does not require session-bound logout.
 */
data class CreateLogoutTokenArgs(
    val issuer: String,
    val clientId: String,
    val sub: String,
    val sid: String? = null,
)

/**
 * Mint a signed `logout_token` JWT for one RP per OIDC Back-Channel Logout 1.0 §2.4.
 *
 * The token MUST contain `iss`, `aud`, `iat`, `jti`, `events` (with key
 * `http://schemas.openid.net/event/backchannel-logout`), and at least one of `sub` /
 * `sid`. It MUST NOT contain `nonce` (BC §2.4). The signing key matches the AS's
 * id_token signing key so RPs can verify against the same JWKS.
 *
 * Returns the compact JWS as a [StringResult].
 */
interface CreateLogoutTokenCommand : ServiceCommand<CreateLogoutTokenArgs, StringResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID: String = "oauth2.logout.create-logout-token"
    }
}

/**
 * Inputs to [SendBackChannelLogoutCommand]. Carries the RP's back-channel logout
 * URI plus the signed `logout_token` to deliver to it.
 */
data class SendBackChannelLogoutArgs(
    val backchannelLogoutUri: String,
    val logoutToken: String,
    val clientId: String,
)

/**
 * Deliver one signed `logout_token` to one RP per OIDC Back-Channel Logout 1.0 §2.7.
 * POSTs `application/x-www-form-urlencoded` body `logout_token=<jws>` to the RP's
 * registered `backchannel_logout_uri`.
 *
 * Failures are returned but the IDK orchestrator (RP fan-out) MUST NOT block the
 * RP-Initiated logout flow on a single delivery error; it logs the failure and
 * proceeds. Retry is not implemented in IDK; deployments needing
 * at-least-once delivery contribute a binding that buffers failures to a durable
 * outbox and retries on a schedule.
 */
interface SendBackChannelLogoutCommand : ServiceCommand<SendBackChannelLogoutArgs, Unit, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID: String = "oauth2.logout.send-back-channel-logout"
    }
}
