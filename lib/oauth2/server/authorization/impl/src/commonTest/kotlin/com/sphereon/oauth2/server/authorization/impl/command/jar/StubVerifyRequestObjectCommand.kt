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

package com.sphereon.oauth2.server.authorization.impl.command.jar

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.oauth2.server.authorization.command.jar.VerifiedRequestObject
import com.sphereon.oauth2.server.authorization.command.jar.VerifyRequestObjectArgs
import com.sphereon.oauth2.server.authorization.command.jar.VerifyRequestObjectCommand

/**
 * Test stub for [VerifyRequestObjectCommand]. Returns the front-channel parameters unchanged with
 * a fixed `clientId` and `signingAlg`. Suites that exercise JAR semantics use a real verifier
 * instead, see the OIDF JAR conformance harness.
 */
class StubVerifyRequestObjectCommand(
    execution: SessionExecution,
    private val clientId: String = "stub-client",
    private val alg: String = "RS256",
) : TypedServiceCommandAdapter<VerifyRequestObjectArgs, VerifiedRequestObject, IdkError>(
        commandId = VerifyRequestObjectCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifyRequestObjectArgs>(),
        outputTypeToken = typeToken<VerifiedRequestObject>(),
    ),
    VerifyRequestObjectCommand {
    override val commandId: String get() = VerifyRequestObjectCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyRequestObjectArgs

    override suspend fun doExecute(
        args: VerifyRequestObjectArgs,
        applyDuring: (VerifyRequestObjectArgs) -> VerifyRequestObjectArgs,
    ): IdkResult<VerifiedRequestObject, IdkError> {
        val applied = applyDuring(args)
        return Ok(
            VerifiedRequestObject(
                mergedParameters = applied.queryParameters,
                clientId = clientId,
                signingAlg = alg,
            ),
        )
    }
}
