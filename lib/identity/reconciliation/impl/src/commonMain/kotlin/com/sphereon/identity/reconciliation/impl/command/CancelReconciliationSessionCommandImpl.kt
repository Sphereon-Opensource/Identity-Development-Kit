/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.identity.reconciliation.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.reconciliation.command.CancelReconciliationSessionCommand
import com.sphereon.identity.reconciliation.error.ReconciliationError
import com.sphereon.identity.reconciliation.model.CancelReconciliationSessionArgs
import com.sphereon.identity.reconciliation.model.ReconciliationSession
import com.sphereon.identity.reconciliation.model.ReconciliationSessionStatus
import com.sphereon.identity.reconciliation.store.ReconciliationSessionStore
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CancelReconciliationSessionCommandImpl", exact = true)
class CancelReconciliationSessionCommandImpl(
    execution: SessionExecution,
    private val sessionStore: ReconciliationSessionStore
) : TypedServiceCommandAdapter<CancelReconciliationSessionArgs, ReconciliationSession>(
    commandId = CancelReconciliationSessionCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<CancelReconciliationSessionArgs>(),
    outputTypeToken = typeToken<ReconciliationSession>(),
), CancelReconciliationSessionCommand {

    override val commandId: String get() = CancelReconciliationSessionCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CancelReconciliationSessionArgs

    override suspend fun doExecute(
        args: CancelReconciliationSessionArgs,
        applyDuring: (CancelReconciliationSessionArgs) -> CancelReconciliationSessionArgs
    ): IdkResult<ReconciliationSession, IdkError> {
        val applied = applyDuring(args)

        val session = sessionStore.findById(applied.tenantId, applied.sessionId)
            ?: return Err(IdkError.fromDTO(ReconciliationError.SessionNotFound(sessionId = applied.sessionId)))

        val cancelled = session.copy(status = ReconciliationSessionStatus.CANCELLED)
        return Ok(sessionStore.update(cancelled))
    }
}
