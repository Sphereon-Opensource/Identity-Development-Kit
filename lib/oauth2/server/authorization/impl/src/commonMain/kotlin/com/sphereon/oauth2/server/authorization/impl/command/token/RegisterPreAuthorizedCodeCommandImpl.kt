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
import com.sphereon.oauth2.server.authorization.command.token.RegisterPreAuthorizedCodeArgs
import com.sphereon.oauth2.server.authorization.command.token.RegisterPreAuthorizedCodeCommand
import com.sphereon.oauth2.server.authorization.command.token.RegisterPreAuthorizedCodeResult
import com.sphereon.oauth2.server.authorization.command.token.validatePreAuthorizedCodeExpiry
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeData
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeStorage
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock

/**
 * Implementation of [RegisterPreAuthorizedCodeCommand]. The basic-auth parsing happens at the
 * HTTP adapter layer (this command receives the credentials as Args fields); the credential check
 * stays here because it is part of the business operation, not the transport layer.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RegisterPreAuthorizedCodeCommand>())
class RegisterPreAuthorizedCodeCommandImpl(
    execution: SessionExecution,
    private val clientRegistry: ClientRegistry,
    private val preAuthorizedCodeStorage: PreAuthorizedCodeStorage,
    private val clock: Clock = Clock.System,
) : TypedServiceCommandAdapter<RegisterPreAuthorizedCodeArgs, RegisterPreAuthorizedCodeResult, IdkError>(
        commandId = RegisterPreAuthorizedCodeCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RegisterPreAuthorizedCodeArgs>(),
        outputTypeToken = typeToken<RegisterPreAuthorizedCodeResult>(),
    ),
    RegisterPreAuthorizedCodeCommand {
    override val commandId: String get() = RegisterPreAuthorizedCodeCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is RegisterPreAuthorizedCodeArgs

    override suspend fun doExecute(
        args: RegisterPreAuthorizedCodeArgs,
        applyDuring: (RegisterPreAuthorizedCodeArgs) -> RegisterPreAuthorizedCodeArgs,
    ): IdkResult<RegisterPreAuthorizedCodeResult, IdkError> {
        val applied = applyDuring(args)

        // Reject an invalid absolute expiry before any client-registry or storage
        // side effect. This keeps malformed registration requests fail-closed and
        // deterministic, even when credentials are invalid as well.
        val now = clock.now()
        val expiry = validatePreAuthorizedCodeExpiry(applied.expiresAtEpochSeconds, now).getOrElse { return Err(it) }

        val credentialsValid =
            clientRegistry.verifyClientCredentials(
                applied.basicAuthClientId,
                applied.basicAuthClientSecret,
            )
        if (credentialsValid.isErr) {
            return Err(
                IdkError.fromString(
                    code = "server_error",
                    message = "Client credential verification failed",
                ),
            )
        }
        if (!credentialsValid.value) {
            return Err(
                IdkError.fromString(
                    code = "invalid_client",
                    message = "Invalid client credentials",
                ),
            )
        }

        val data =
            PreAuthorizedCodeData(
                sessionId = applied.sessionId,
                credentialConfigurationIds = applied.credentialConfigurationIds,
                txCodeRequired = applied.txCodeRequired,
                txCodeHash = applied.txCodeHash,
                issuerIdentifier = applied.issuerIdentifier,
                useCredentialIdentifiers = applied.useCredentialIdentifiers,
                createdAt = now,
                expiresAt = expiry,
            )

        val stored = preAuthorizedCodeStorage.storePreAuthorizedCode(applied.code, data)
        if (!stored.isOk) {
            return Err(
                IdkError.fromString(
                    code = "server_error",
                    message = "Failed to store code: ${stored.error.details}",
                ),
            )
        }

        return Ok(RegisterPreAuthorizedCodeResult())
    }

}
