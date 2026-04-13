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
 *
 */

package com.sphereon.core.api.session

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.NotFoundException
import com.sphereon.di.context.UserScope
import com.sphereon.di.session.SessionContextManager
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionCommandExecutorImpl", exact = true)
class SessionCommandExecutorImpl(
    private val sessionContextManager: SessionContextManager,
    private val errorMapper: CommandErrorMapper<IdkErrorType> = IdkErrorTypeCommandErrorMapper
) : ISessionCommandExecutor {
    override suspend fun <Arg : Any, SuccessResult : Any> execute(
        commandId: String,
        args: Arg
    ): IdkResult<SuccessResult, IdkErrorType> {
        val validCommandId = requireCommandId(commandId)
        if (validCommandId.isErr) {
            return IdkResult.err(validCommandId.error)
        }

        val activeSession = sessionContextManager.getActive()
        val service = try {
            activeSession.getService<Command<Arg, SuccessResult, IdkErrorType>>(commandId)
        } catch (e: NotFoundException) {
            return IdkResult.err(errorMapper.commandNotFound(commandId))
        } catch (e: Exception) {
            return IdkResult.err(
                errorMapper.unknown(
                    message = "Unexpected error while resolving command '$commandId'",
                    cause = e
                )
            )
        }

        try {
            if (!service.isEnabled) {
                return IdkResult.err(errorMapper.commandDisabled(commandId = commandId))
            }
            if (!service.supports(args)) {
                return IdkResult.err(errorMapper.unsupportedArg(command = service, arg = args))
            }
            return service.execute(args)
        } catch (e: Exception) {
            return IdkResult.err(
                errorMapper.unknown(
                    message = "Unexpected error while executing command '$commandId'",
                    cause = e
                )
            )
        }
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("ISessionCommandExecutor", exact = true)
interface ISessionCommandExecutor {
    suspend fun <Arg : Any, SuccessResult : Any> execute(
        commandId: String,
        args: Arg
    ): IdkResult<SuccessResult, IdkErrorType>
}
