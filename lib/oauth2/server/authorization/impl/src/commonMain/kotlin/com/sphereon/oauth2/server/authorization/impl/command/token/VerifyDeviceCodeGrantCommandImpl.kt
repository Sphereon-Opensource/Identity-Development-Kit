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

package com.sphereon.oauth2.server.authorization.impl.command.token

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.token.VerifiedDeviceCodeGrant
import com.sphereon.oauth2.server.authorization.command.token.VerifyDeviceCodeGrantArgs
import com.sphereon.oauth2.server.authorization.command.token.VerifyDeviceCodeGrantCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationRecord
import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationState
import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationStorage
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Duration.Companion.seconds

/**
 * Verify an RFC 8628 device-code grant at the token endpoint.
 *
 * State machine per RFC 8628 §3.4 / §3.5:
 *
 * 1. Lookup by `device_code`. Miss yields `invalid_grant`; the spec does not distinguish
 *    "never issued" from "typo" so both fall into the same error.
 * 2. Client mismatch (record was issued to a different `client_id`) yields `invalid_grant`.
 * 3. CONSUMED record yields `invalid_grant` to surface replay against an already-redeemed code.
 * 4. PENDING record past `expires_at` is transitioned to EXPIRED (so subsequent polls observe
 *    the explicit terminal state) and yields `expired_token`. EXPIRED records yield the same.
 * 5. DENIED record yields `access_denied`.
 * 6. PENDING record polled within the per-record `interval` since the last poll yields
 *    `slow_down`; otherwise the poll is recorded and the request yields `authorization_pending`.
 * 7. APPROVED record returns the verified grant. The orchestrator is responsible for minting
 *    tokens and only then calling [DeviceAuthorizationStorage.consume]: keeping verify and
 *    consume separate means a partial token-issuance failure does not strand the user with a
 *    silently-consumed code.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<VerifyDeviceCodeGrantCommand>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyDeviceCodeGrantCommandImpl", exact = true)
class VerifyDeviceCodeGrantCommandImpl(
    execution: SessionExecution,
    private val deviceAuthorizationStorage: DeviceAuthorizationStorage,
) : TypedServiceCommandAdapter<VerifyDeviceCodeGrantArgs, VerifiedDeviceCodeGrant, IdkError>(
        commandId = VerifyDeviceCodeGrantCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifyDeviceCodeGrantArgs>(),
        outputTypeToken = typeToken<VerifiedDeviceCodeGrant>(),
    ),
    VerifyDeviceCodeGrantCommand {
    override val commandId: String get() = VerifyDeviceCodeGrantCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyDeviceCodeGrantArgs

    override suspend fun doExecute(
        args: VerifyDeviceCodeGrantArgs,
        applyDuring: (VerifyDeviceCodeGrantArgs) -> VerifyDeviceCodeGrantArgs,
    ): IdkResult<VerifiedDeviceCodeGrant, IdkError> = executeInternal(applyDuring(args)).mapError { IdkError.fromDTO(it) }

    private suspend fun executeInternal(args: VerifyDeviceCodeGrantArgs): IdkResult<VerifiedDeviceCodeGrant, AuthorizationServerError> {
        val record =
            deviceAuthorizationStorage
                .findByDeviceCode(args.deviceCode)
                .getOrElse { error ->
                    return Err(
                        AuthorizationServerError.ServerError(
                            details = "Failed to retrieve device-authorization record: ${error.details}",
                            exception = null,
                        ),
                    )
                }
                ?: return Err(AuthorizationServerError.InvalidGrant(details = "Unknown device_code"))

        if (record.clientId != args.clientId) {
            return Err(AuthorizationServerError.InvalidGrant(details = "device_code was issued to a different client"))
        }

        return when (record.state) {
            DeviceAuthorizationState.CONSUMED -> {
                Err(AuthorizationServerError.InvalidGrant(details = "device_code has already been redeemed"))
            }

            DeviceAuthorizationState.EXPIRED -> {
                Err(AuthorizationServerError.ExpiredToken())
            }

            DeviceAuthorizationState.DENIED -> {
                Err(AuthorizationServerError.AccessDenied(reason = "End user denied the device authorization request"))
            }

            DeviceAuthorizationState.PENDING -> {
                if (record.expiresAt <= args.now) {
                    transitionToExpired(record)?.let { return Err(it) }
                    return Err(AuthorizationServerError.ExpiredToken())
                }
                val lastPolledAt = record.lastPolledAt
                if (lastPolledAt != null && (args.now - lastPolledAt) < record.intervalSeconds.seconds) {
                    return Err(AuthorizationServerError.SlowDown())
                }
                deviceAuthorizationStorage
                    .recordPolledAt(args.deviceCode, args.now)
                    .getOrElse { error ->
                        return Err(
                            AuthorizationServerError.ServerError(
                                details = "Failed to record device poll: ${error.details}",
                                exception = null,
                            ),
                        )
                    }
                Err(AuthorizationServerError.AuthorizationPending())
            }

            DeviceAuthorizationState.APPROVED -> {
                if (record.expiresAt <= args.now) {
                    // An approved record that aged out before the device polled is still
                    // expired_token per §3.5: the device window has closed and the issued token
                    // would already be past its useful lifetime.
                    transitionToExpired(record)?.let { return Err(it) }
                    return Err(AuthorizationServerError.ExpiredToken())
                }
                val subject =
                    record.approvedSub
                        ?: return Err(
                            AuthorizationServerError.ServerError(
                                details = "APPROVED device-authorization record is missing approved_sub",
                                exception = null,
                            ),
                        )
                Ok(
                    VerifiedDeviceCodeGrant(
                        deviceCode = record.deviceCode,
                        clientId = record.clientId,
                        subject = subject,
                        authTime = record.approvedAuthTime,
                        sessionId = record.approvedSessionId,
                        grantedScope = record.grantedScope ?: record.scope,
                        resource = record.resource,
                        audience = record.audience,
                    ),
                )
            }
        }
    }

    /**
     * Flip a PENDING/APPROVED record to EXPIRED so subsequent polls observe the explicit
     * terminal state instead of re-running the expiry check. Returns a `ServerError` if the
     * storage update itself failed; `null` when the transition succeeded (so the caller proceeds
     * to emit `expired_token`).
     */
    private suspend fun transitionToExpired(record: DeviceAuthorizationRecord): AuthorizationServerError.ServerError? {
        val update =
            deviceAuthorizationStorage.update(
                record.copy(state = DeviceAuthorizationState.EXPIRED),
            )
        return if (update.isErr) {
            AuthorizationServerError.ServerError(
                details = "Failed to transition device-authorization record to EXPIRED: ${update.error.details}",
                exception = null,
            )
        } else {
            null
        }
    }
}
