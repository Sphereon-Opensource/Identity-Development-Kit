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
 *
 */

package com.sphereon.crypto.resolution.managed

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.session.Command
import com.sphereon.core.api.session.ExecutionScopedCommandAdapter
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.resolution.IIdentifierMethod
import com.sphereon.di.Order

abstract class ManagedIdentifierServiceAdapter<ResultType : ManagedIdentifierResult<KeyType>>(
    override val supportedIdentifierMethods: List<IIdentifierMethod>,
    execution: SessionExecution,
    commandId: String,
) : ExecutionScopedCommandAdapter<ManagedIdentifierOptsOrResult, ResultType, IdkErrorType>(id = commandId, execution = execution),
    Command<ManagedIdentifierOptsOrResult, ResultType, IdkErrorType>,
    ManagedIdentifierService {
    override val order: Int get() = Order.MEDIUM.orderValue

    override suspend fun isSupportedIdentifierMethod(identifierMethod: IIdentifierMethod): Boolean = supportedIdentifierMethods.contains(identifierMethod)

    override suspend fun isSupportedOpts(opts: ManagedIdentifierOptsOrResult): Boolean = supports(opts)

    override suspend fun asSupportedOpts(opts: ManagedIdentifierOptsOrResult): IdkResult<ManagedIdentifierOpts, IdkErrorType> =
        if (isSupportedOpts(opts)) {
            (opts as ManagedIdentifierOpts).asOkResult()
        } else {
            IdkError.Companion.COMMAND_ARG_NOT_SUPPORTED_ERROR().asErrorResult()
        }
}
