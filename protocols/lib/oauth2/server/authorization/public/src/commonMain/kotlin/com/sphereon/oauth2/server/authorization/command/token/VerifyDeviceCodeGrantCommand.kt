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
import kotlin.time.Instant

/**
 * Arguments for verifying an RFC 8628 device-code grant at the token endpoint.
 *
 * @property deviceCode opaque device code presented in the `device_code` request parameter.
 * @property clientId resolved client identifier (from client authentication or the request body
 *                    `client_id`); the verifier matches this against the stored record per
 *                    RFC 8628 §3.4.
 * @property now wall clock at the point of verification, used for expiry and `slow_down`
 *               polling-cadence checks. Threading the instant through args (rather than reading
 *               it inside the impl) keeps the verifier deterministic in tests.
 */
data class VerifyDeviceCodeGrantArgs(
    val deviceCode: String,
    val clientId: String,
    val now: Instant,
)

/**
 * Verified RFC 8628 device-code grant. Returned once the user has approved at the verification
 * URI and the device's poll arrives within the per-record polling cadence. The token-endpoint
 * orchestrator turns this into the access/refresh/id_token triple before calling
 * [com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationStorage.consume] to mark
 * the record consumed.
 *
 * @property deviceCode echoed for downstream `consume` of the storage record.
 * @property clientId resolved client identifier matching the stored record.
 * @property subject end-user subject pinned by the verification UI on approval.
 * @property authTime end-user authentication time, propagated to id_token / refresh-token rows
 *                    when OIDC is enabled.
 * @property sessionId cookie-keyed `oidc_login_sid` from the verification-UI session, used for
 *                     id_token `sid` and Back-Channel Logout recipient set.
 * @property grantedScope scope actually granted by the user; may narrow the requested scope.
 * @property resource RFC 8707 resource indicator(s) carried through from the device-authorization
 *                    request.
 * @property audience token audience(s) carried through from the device-authorization request.
 */
data class VerifiedDeviceCodeGrant(
    val deviceCode: String,
    val clientId: String,
    val subject: String,
    val authTime: Instant?,
    val sessionId: String?,
    val grantedScope: String?,
    val resource: List<String>?,
    val audience: List<String>?,
)

/**
 * Verify a device-code grant at the token endpoint per RFC 8628 §3.4 / §3.5.
 *
 * State-machine outcomes:
 * - `invalid_grant`: unknown device code, client mismatch, or replay against an already-consumed
 *   record.
 * - `expired_token`: record aged past `expires_in` without approval.
 * - `access_denied`: user denied at the verification UI.
 * - `authorization_pending`: user has not yet approved; record is still PENDING.
 * - `slow_down`: device polled before the per-record `interval` elapsed since the last poll.
 * - success: the record is APPROVED; the verifier surfaces the pinned authentication context to
 *   the orchestrator, which then mints tokens and consumes the record.
 *
 * The verifier MAY transition record state (PENDING → EXPIRED, lastPolledAt) but MUST NOT
 * consume an APPROVED record on success: token issuance and consumption stay coupled at the
 * orchestrator so consumption only happens after token minting succeeds.
 */
interface VerifyDeviceCodeGrantCommand : ServiceCommand<VerifyDeviceCodeGrantArgs, VerifiedDeviceCodeGrant, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.devicecode.verify"
    }
}
