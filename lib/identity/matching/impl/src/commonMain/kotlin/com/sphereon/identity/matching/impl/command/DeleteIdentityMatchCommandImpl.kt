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
import com.sphereon.identity.matching.command.DeleteIdentityMatchCommand
import com.sphereon.identity.matching.model.DeleteIdentityMatchArgs
import com.sphereon.identity.matching.store.IdentityMatchStore
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteIdentityMatchCommandImpl", exact = true)
class DeleteIdentityMatchCommandImpl(
    execution: SessionExecution,
    private val store: IdentityMatchStore,
) : TypedServiceCommandAdapter<DeleteIdentityMatchArgs, Boolean>(
        commandId = DeleteIdentityMatchCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DeleteIdentityMatchArgs>(),
        outputTypeToken = typeToken<Boolean>(),
    ),
    DeleteIdentityMatchCommand {
    override val commandId: String get() = DeleteIdentityMatchCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is DeleteIdentityMatchArgs

    override suspend fun doExecute(
        args: DeleteIdentityMatchArgs,
        applyDuring: (DeleteIdentityMatchArgs) -> DeleteIdentityMatchArgs,
    ): IdkResult<Boolean, IdkError> {
        val applied = applyDuring(args)
        return Ok(store.delete(applied.tenantId, applied.matchId))
    }
}
