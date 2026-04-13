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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.Err
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType

/**
 * Extension function that checks if a command supports the given arguments using the
 * supports contract.
 *
 * @param args The arguments to check
 * @param errorMapper The error mapper to use for creating the error
 * @return Ok(Unit) if supported, Err with the mapped error otherwise
 */
suspend fun <Arg : Any, R : Any, E : IdkErrorType> BaseCommand<Arg, R, E>.supportsOrError(
    args: Arg,
    errorMapper: CommandErrorMapper<E>
): IdkResult<Unit, E> = if (supports(args)) {
    Ok(Unit)
} else {
    Err(errorMapper.unsupportedArg(this, args))
}

/**
 * Convenience extension for IdkError commands using supports.
 *
 * @param args The arguments to check
 * @return Ok(Unit) if supported, Err with IdkError otherwise
 */
suspend fun <Arg : Any, R : Any> BaseCommand<Arg, R, IdkError>.supportsOrError(
    args: Arg
): IdkResult<Unit, IdkError> =
    supportsOrError(args = args, errorMapper = IdkErrorCommandErrorMapper)

/**
 * Convenience extension for IdkError commands.
 *
 * @param args The arguments to check
 * @param sessionContext The session context (optional)
 * @return Ok(Unit) if supported, Err with IdkError otherwise
 */
suspend fun <Arg : Any, R : Any> BaseCommand<Arg, R, IdkError>.supportsOrIdkError(
    args: Arg
): IdkResult<Unit, IdkError> = supportsOrError(args = args)
