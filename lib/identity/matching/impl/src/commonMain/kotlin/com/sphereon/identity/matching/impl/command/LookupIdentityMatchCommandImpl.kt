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

package com.sphereon.identity.matching.impl.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.matching.command.LookupIdentityMatchCommand
import com.sphereon.identity.matching.model.LookupIdentityMatchArgs
import com.sphereon.identity.matching.model.MatchResult
import com.sphereon.identity.matching.store.IdentityMatchStore
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("LookupIdentityMatchCommandImpl", exact = true)
class LookupIdentityMatchCommandImpl(
    execution: SessionExecution,
    private val store: IdentityMatchStore,
) : TypedServiceCommandAdapter<LookupIdentityMatchArgs, MatchResult>(
        commandId = LookupIdentityMatchCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<LookupIdentityMatchArgs>(),
        outputTypeToken = typeToken<MatchResult>(),
    ),
    LookupIdentityMatchCommand {
    override val commandId: String get() = LookupIdentityMatchCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is LookupIdentityMatchArgs

    override suspend fun doExecute(
        args: LookupIdentityMatchArgs,
        applyDuring: (LookupIdentityMatchArgs) -> LookupIdentityMatchArgs,
    ): IdkResult<MatchResult, IdkError> {
        val applied = applyDuring(args)
        val match = store.findByIdentifierHash(applied.tenantId, applied.identifierHash, applied.identifierType)
        return Ok(
            if (match != null) {
                MatchResult.Found(match)
            } else {
                MatchResult.NotFound
            },
        )
    }
}
