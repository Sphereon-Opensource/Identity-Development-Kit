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

package com.sphereon.oauth2.server.authorization.command.device

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand

/**
 * Arguments for the RFC 8628 §3.1 `/device_authorization` request.
 *
 * @property clientId the requesting client identifier (resolved from client auth or the request
 *                    body `client_id`); validated against the registry plus the `device_code`
 *                    grant-type allow-list before issuance.
 * @property scope optional space-separated scope set the device wants the user to grant. Stored
 *                 verbatim on the record; the verification UI may narrow it on approval.
 * @property resource optional RFC 8707 resource indicator(s) carried through to the eventual
 *                    access token's `aud`/`resource` binding.
 * @property audience optional explicit audience(s) carried through to the eventual access
 *                    token; `null` defers to default audience derivation from `resource`.
 */
data class IssueDeviceAuthorizationArgs(
    val clientId: String,
    val scope: String? = null,
    val resource: List<String>? = null,
    val audience: List<String>? = null,
    /**
     * Per-request base URL resolved by the HTTP layer from `Host` + `X-Forwarded-Proto`. Used to
     * construct `verification_uri` / `verification_uri_complete` when the AS config has no
     * explicit `issuer` set, mirroring the discovery and access-token issuance fallback so the
     * URLs the device receives match what discovery advertises behind a proxy.
     */
    val baseUrlOverride: String? = null,
)

/**
 * RFC 8628 §3.2 `/device_authorization` response payload, ready for the HTTP layer to serialise
 * onto the wire.
 *
 * @property deviceCode opaque, ~256-bit base64url-encoded device code the device polls with at
 *                      `/token`.
 * @property userCode human-typeable code (8 chars from the unambiguous alphabet, formatted
 *                    `XXXX-XXXX`) the user enters at the verification URI.
 * @property verificationUri stable absolute URL of the verification page (`{baseUrl}/device`).
 * @property verificationUriComplete pre-populated form variant (`{baseUrl}/device?user_code=...`)
 *                                   so the device can render a QR or deep-link the user to a UI
 *                                   that already has the code filled in.
 * @property expiresIn lifetime of the issued record in seconds, sourced from the AS config.
 * @property intervalSeconds initial baseline polling cadence per RFC 8628 §3.2; the device MUST
 *                           wait at least this long between polls. The AS may bump effective
 *                           cadence at runtime via `slow_down`.
 */
data class IssuedDeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String,
    val expiresIn: Int,
    val intervalSeconds: Int,
)

/**
 * Issue a device-authorization record per RFC 8628 §3.1: validate the client, generate
 * `device_code` + `user_code`, persist a PENDING [com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationRecord],
 * and return the response payload that the HTTP layer renders.
 *
 * Errors:
 * - `invalid_client`: client unknown to the registry.
 * - `unauthorized_client`: registered client is not authorised for the
 *   `urn:ietf:params:oauth:grant-type:device_code` grant.
 * - `server_error`: code-generation collision exhausted the retry budget (CSPRNG fault) or the
 *   issuer/baseUrl could not be resolved.
 */
interface IssueDeviceAuthorizationCommand : ServiceCommand<IssueDeviceAuthorizationArgs, IssuedDeviceAuthorization, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.device.issue"
    }
}
