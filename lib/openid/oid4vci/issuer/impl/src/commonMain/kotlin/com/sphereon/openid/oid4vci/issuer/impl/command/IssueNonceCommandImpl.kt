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

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import com.sphereon.openid.oid4vci.issuer.command.IssueNonceArgs
import com.sphereon.openid.oid4vci.issuer.command.IssueNonceCommand
import com.sphereon.openid.oid4vci.issuer.impl.nonce.NonceManager
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<IssueNonceCommand>())
class IssueNonceCommandImpl(
    execution: SessionExecution,
    private val nonceManager: NonceManager,
) : TypedServiceCommandAdapter<IssueNonceArgs, NonceResponse, IdkError>(
        commandId = IssueNonceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<IssueNonceArgs>(),
        outputTypeToken = typeToken<NonceResponse>(),
    ),
    IssueNonceCommand {
    override val commandId: String get() = IssueNonceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is IssueNonceArgs

    override suspend fun doExecute(
        args: IssueNonceArgs,
        applyDuring: (IssueNonceArgs) -> IssueNonceArgs,
    ): IdkResult<NonceResponse, IdkError> {
        val applied = applyDuring(args)
        return nonceManager.issue(applied.ttlSeconds)
    }
}
