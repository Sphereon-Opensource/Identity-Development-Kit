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

package com.sphereon.oauth2.server.authorization.impl.command.attestation

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.AttestationChallengeResponse
import com.sphereon.oauth2.server.authorization.command.CreateAttestationChallengeArgs
import com.sphereon.oauth2.server.authorization.command.CreateAttestationChallengeCommand
import com.sphereon.oauth2.server.authorization.storage.AttestationChallengeStorage
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateAttestationChallengeCommandImpl", exact = true)
class CreateAttestationChallengeCommandImpl(
    execution: SessionExecution,
    private val challengeStorage: AttestationChallengeStorage,
) : TypedServiceCommandAdapter<CreateAttestationChallengeArgs, AttestationChallengeResponse, IdkError>(
        commandId = CreateAttestationChallengeCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateAttestationChallengeArgs>(),
        outputTypeToken = typeToken<AttestationChallengeResponse>(),
    ),
    CreateAttestationChallengeCommand {
    override val commandId: String get() = CreateAttestationChallengeCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateAttestationChallengeArgs

    override suspend fun doExecute(
        args: CreateAttestationChallengeArgs,
        applyDuring: (CreateAttestationChallengeArgs) -> CreateAttestationChallengeArgs,
    ): IdkResult<AttestationChallengeResponse, IdkError> {
        applyDuring(args)

        val challenge =
            challengeStorage
                .generateChallenge()
                .getOrElse { error ->
                    return com.sphereon.core.api
                        .Err(IdkError.fromDTO(error))
                }

        return Ok(AttestationChallengeResponse(attestationChallenge = challenge))
    }
}
