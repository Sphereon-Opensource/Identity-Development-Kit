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
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.service.ServiceCommand
import kotlin.time.Instant

/**
 * Converts the issuer-supplied absolute epoch-second expiry without allowing an unrepresentable
 * value or a stale boundary to escape as an exception or reach storage.
 */
fun validatePreAuthorizedCodeExpiry(
    expiresAtEpochSeconds: Long,
    now: Instant,
): IdkResult<Instant, IdkError> {
    val expiry =
        try {
            Instant.fromEpochSeconds(expiresAtEpochSeconds)
        } catch (_: IllegalArgumentException) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Pre-authorized code expiry is not representable"))
        }
    if (expiry.epochSeconds != expiresAtEpochSeconds) {
        return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Pre-authorized code expiry is not representable"))
    }
    if (expiry <= now) {
        return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Pre-authorized code expiry must be in the future"))
    }
    return Ok(expiry)
}

/**
 * Args for [RegisterPreAuthorizedCodeCommand]. Carries the basic-auth credentials lifted off the
 * request (validated by the impl against `oauth2.servers.<id>.internal-clients`) and the
 * pre-authorized code metadata supplied by the OID4VCI issuer.
 *
 * No tenant or caller identity is included: the impl resolves tenant from `SessionExecution`
 * per `feedback_session_scope_tenant.md`, and the basic-auth identity authenticates the issuer
 * service rather than an end user.
 */
data class RegisterPreAuthorizedCodeArgs(
    val basicAuthClientId: String,
    val basicAuthClientSecret: String,
    val code: String,
    val sessionId: String,
    val credentialConfigurationIds: List<String>,
    /** Absolute expiry supplied by the issuer; registration must never invent a lifetime. */
    val expiresAtEpochSeconds: Long,
    val txCodeRequired: Boolean = false,
    val txCodeHash: String? = null,
    val issuerIdentifier: String? = null,
    val useCredentialIdentifiers: Boolean = true,
)

/**
 * Outcome wrapper for [RegisterPreAuthorizedCodeCommand]. A typed object keeps the binary
 * transport shape stable; the Status enum lets the adapter render `{"status": "ok"}` without
 * the impl encoding JSON itself.
 */
data class RegisterPreAuthorizedCodeResult(
    val status: String = "ok",
)

/**
 * Internal endpoint command for cross-service pre-authorized code registration, used when the
 * OID4VCI issuer runs in a separate process from the authorization server. The impl validates
 * the supplied basic-auth credentials against `oauth2.servers.<id>.internal-clients` before
 * persisting the code into [com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeStorage].
 */
interface RegisterPreAuthorizedCodeCommand : ServiceCommand<RegisterPreAuthorizedCodeArgs, RegisterPreAuthorizedCodeResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.token.register-pre-authorized-code"
    }
}
