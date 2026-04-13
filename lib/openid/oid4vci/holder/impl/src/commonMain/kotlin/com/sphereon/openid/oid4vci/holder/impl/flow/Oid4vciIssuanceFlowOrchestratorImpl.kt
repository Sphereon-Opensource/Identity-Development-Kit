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

package com.sphereon.openid.oid4vci.holder.impl.flow

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.holder.CredentialFlowResult
import com.sphereon.openid.oid4vci.holder.Oid4vciIssuanceFlowAdapter
import com.sphereon.openid.oid4vci.holder.Oid4vciIssuanceFlowOrchestrator
import com.sphereon.openid.oid4vci.holder.Oid4vciIssuanceFlowService
import com.sphereon.openid.oid4vci.holder.PollDeferredCredentialArgs
import com.sphereon.openid.oid4vci.holder.PollDeferredCredentialCommand
import com.sphereon.openid.oid4vci.holder.PollDeferredCredentialResult
import com.sphereon.openid.oid4vci.holder.RequestCredentialWithFlowArgs
import com.sphereon.openid.oid4vci.holder.RequestCredentialWithFlowCommand
import com.sphereon.openid.oid4vci.holder.SendNotificationWithRetryArgs
import com.sphereon.openid.oid4vci.holder.SendNotificationWithRetryCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of [Oid4vciIssuanceFlowOrchestrator].
 *
 * Binds as all three tiers ([Oid4vciIssuanceFlowOrchestrator], [Oid4vciIssuanceFlowAdapter],
 * [Oid4vciIssuanceFlowService]) so dependent code can inject at its preferred abstraction level.
 *
 * Each service method delegates directly to the corresponding command's [execute] function,
 * keeping orchestration logic inside the command implementations.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciIssuanceFlowOrchestrator>())
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciIssuanceFlowAdapter>())
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciIssuanceFlowService>())
class Oid4vciIssuanceFlowOrchestratorImpl(
    private val pollDeferredCredentialCommand: PollDeferredCredentialCommand,
    private val sendNotificationWithRetryCommand: SendNotificationWithRetryCommand,
    private val requestCredentialWithFlowCommand: RequestCredentialWithFlowCommand,
) : Oid4vciIssuanceFlowOrchestrator {
    inner class CommandsImpl : Oid4vciIssuanceFlowOrchestrator.Commands {
        override val pollDeferredCredential = this@Oid4vciIssuanceFlowOrchestratorImpl.pollDeferredCredentialCommand
        override val sendNotificationWithRetry = this@Oid4vciIssuanceFlowOrchestratorImpl.sendNotificationWithRetryCommand
        override val requestCredentialWithFlow = this@Oid4vciIssuanceFlowOrchestratorImpl.requestCredentialWithFlowCommand
    }

    override val commands: Oid4vciIssuanceFlowOrchestrator.Commands = CommandsImpl()

    override suspend fun pollDeferredCredential(args: PollDeferredCredentialArgs): IdkResult<PollDeferredCredentialResult, IdkError> = pollDeferredCredentialCommand.execute(args)

    override suspend fun sendNotificationWithRetry(args: SendNotificationWithRetryArgs): IdkResult<Unit, IdkError> = sendNotificationWithRetryCommand.execute(args)

    override suspend fun requestCredentialWithFlow(args: RequestCredentialWithFlowArgs): IdkResult<CredentialFlowResult, IdkError> = requestCredentialWithFlowCommand.execute(args)
}
