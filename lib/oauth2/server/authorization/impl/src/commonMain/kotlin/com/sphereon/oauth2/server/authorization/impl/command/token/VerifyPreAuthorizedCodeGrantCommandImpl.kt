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
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.VerifiedPreAuthCodeGrant
import com.sphereon.oauth2.server.authorization.command.VerifyPreAuthCodeArgs
import com.sphereon.oauth2.server.authorization.command.VerifyPreAuthorizedCodeGrantCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeStorage
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock

/**
 * Verifies OID4VCI pre-authorized code grants.
 *
 * 1. Load the code without consuming it
 * 2. Check expiry
 * 3. Validate tx_code if required (SHA-256 hash comparison)
 * 4. Validate client binding
 * 5. Atomically consume the validated code from storage
 * 6. Return session, subject, and credential configuration IDs
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<VerifyPreAuthorizedCodeGrantCommand>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyPreAuthorizedCodeGrantCommandImpl", exact = true)
class VerifyPreAuthorizedCodeGrantCommandImpl(
    execution: SessionExecution,
    private val preAuthorizedCodeStorage: PreAuthorizedCodeStorage,
    private val clock: Clock,
) : TypedServiceCommandAdapter<VerifyPreAuthCodeArgs, VerifiedPreAuthCodeGrant, IdkError>(
        commandId = VerifyPreAuthorizedCodeGrantCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifyPreAuthCodeArgs>(),
        outputTypeToken = typeToken<VerifiedPreAuthCodeGrant>(),
    ),
    VerifyPreAuthorizedCodeGrantCommand {
    override val commandId: String get() = VerifyPreAuthorizedCodeGrantCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyPreAuthCodeArgs

    override suspend fun doExecute(
        args: VerifyPreAuthCodeArgs,
        applyDuring: (VerifyPreAuthCodeArgs) -> VerifyPreAuthCodeArgs,
    ): IdkResult<VerifiedPreAuthCodeGrant, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(args: VerifyPreAuthCodeArgs): IdkResult<VerifiedPreAuthCodeGrant, AuthorizationServerError> {
        // 1. Load the pre-authorized code without consuming it. Invalid attempts must not
        // destroy a valid code; the final consume below remains the single-use gate.
        val codeData =
            preAuthorizedCodeStorage
                .findPreAuthorizedCode(args.preAuthorizedCode)
                .getOrElse { error ->
                    return Err(
                        AuthorizationServerError.ServerError(
                            details = "Failed to retrieve pre-authorized code: ${error.details}",
                            exception = null,
                        ),
                    )
                }

        if (codeData == null) {
            execution.log.warn("VDX_PREAUTH_MISS codeHash=${args.preAuthorizedCode.hashCode()}")
            return Err(
                AuthorizationServerError.InvalidGrant(
                    details = "Invalid or already used pre-authorized code",
                    exception = null,
                ),
            )
        }

        // 2. Check expiry
        val now = clock.now()
        if (codeData.expiresAt <= now) {
            return Err(
                AuthorizationServerError.InvalidGrant(
                    details = "Pre-authorized code has expired",
                    exception = null,
                ),
            )
        }

        // 3. Validate tx_code if required
        if (codeData.txCodeRequired) {
            val txCode = args.txCode
            if (txCode.isNullOrBlank()) {
                return Err(
                    AuthorizationServerError.InvalidRequest(
                        details = "Missing required parameter: tx_code",
                        exception = null,
                    ),
                )
            }
            val providedHash = hash(txCode.encodeToByteArray(), DigestAlg.SHA256).encodeToBase64Url()
            if (providedHash != codeData.txCodeHash) {
                return Err(
                    AuthorizationServerError.InvalidGrant(
                        details = "Invalid tx_code",
                        exception = null,
                    ),
                )
            }
        }

        // 4. Validate client binding if the code was bound to a specific client
        if (codeData.clientId != null && codeData.clientId != args.clientId) {
            return Err(
                AuthorizationServerError.InvalidGrant(
                    details = "Pre-authorized code was issued to a different client",
                    exception = null,
                ),
            )
        }

        // 5. Atomically compare-and-consume only after every validation has succeeded. The
        // storage operation also re-checks identity and expiry at commit time, so replacement
        // or expiry during validation cannot mint a grant from stale data.
        val consumedCodeData =
            preAuthorizedCodeStorage
                .consumePreAuthorizedCodeIfValid(
                    code = args.preAuthorizedCode,
                    expectedData = codeData,
                    now = clock.now(),
                )
                .getOrElse { error ->
                    return Err(
                        AuthorizationServerError.ServerError(
                            details = "Failed to consume pre-authorized code: ${error.details}",
                            exception = null,
                        ),
                    )
                }
                ?: return Err(
                    AuthorizationServerError.InvalidGrant(
                        details = "Invalid or already used pre-authorized code",
                        exception = null,
                    ),
                )

        return Ok(
            VerifiedPreAuthCodeGrant(
                sessionId = consumedCodeData.sessionId,
                subject = consumedCodeData.subject,
                credentialConfigurationIds = consumedCodeData.credentialConfigurationIds,
                issuerIdentifier = consumedCodeData.issuerIdentifier,
                useCredentialIdentifiers = consumedCodeData.useCredentialIdentifiers,
            ),
        )
    }
}
