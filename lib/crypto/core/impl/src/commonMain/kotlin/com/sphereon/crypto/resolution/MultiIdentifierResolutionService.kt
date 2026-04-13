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

package com.sphereon.crypto.resolution

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.session.Command
import com.sphereon.core.api.session.ExecutionScopedCommandAdapter
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.MultiExternalIdentifierService
import com.sphereon.crypto.resolution.managed.MultiManagedIdentifierService
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import software.amazon.app.platform.scope.Scoped
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding


@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<MultiIdentifierResolutionService>())
@ContributesBinding(SessionScope::class, binding = binding<IdentifierService>())
class MultiIdentifierResolutionServiceImpl(execution: SessionExecution, val managedMulti: MultiManagedIdentifierService, val externalMulti: MultiExternalIdentifierService) :
    ExecutionScopedCommandAdapter<IdentifierOptsOrResult, IdentifierOptsOrResult, IdkErrorType>(
        id = COMMAND_ID,
        execution = execution
    ),
    Command<IdentifierOptsOrResult, IdentifierOptsOrResult, IdkErrorType>, MultiIdentifierResolutionService, Scoped {
    override suspend fun doExecute(
        args: IdentifierOptsOrResult,
        applyDuring: (IdentifierOptsOrResult) -> IdentifierOptsOrResult
    ): IdkResult<IdentifierOptsOrResult, IdkErrorType> {
        log.debug("Resolving identifier: ${args.identifier.toString().take(100)}...")
        // Note: supports() validation is already performed by parent CommandAdapter.execute()

        return when (args) {
            is ManagedIdentifierOptsOrResult -> managedMulti.resolve(args)
            is ExternalIdentifierOptsOrResult -> externalMulti.resolve(args)
            else -> IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(message = "Identifier opts expected. Type ${args.method ?: args.identifier} not supported")
                .asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean {
        return when (args) {
            is IdentifierOptsOrResult -> isSupportedOpts(args)
            is IIdentifierMethod -> isSupportedIdentifierMethod(args)
            else -> isSupportedIdentifier(args)
        }
    }

    override val supportedIdentifierMethods: List<IIdentifierMethod>
        get() = (managedMulti.supportedIdentifierMethods + externalMulti.supportedIdentifierMethods).toSet().toList()

    override suspend fun isSupportedIdentifier(identifier: Any): Boolean {
        return managedMulti.isSupportedIdentifier(identifier) || externalMulti.isSupportedIdentifier(identifier)
    }

    override suspend fun isSupportedIdentifierMethod(identifierMethod: IIdentifierMethod): Boolean {
        return supportedIdentifierMethods.contains(identifierMethod)
    }

    override suspend fun isSupportedOpts(opts: IdentifierOptsOrResult): Boolean {
        return when (opts) {
            is ManagedIdentifierOptsOrResult -> managedMulti.isSupportedOpts(opts)
            is ExternalIdentifierOptsOrResult -> externalMulti.isSupportedOpts(opts)
            else -> false
        }
    }

    override suspend fun resolve(opts: IdentifierOptsOrResult): IdkResult<IdentifierOptsOrResult, IdkErrorType> {
        return execute(opts)
    }

    override suspend fun asSupportedOpts(opts: IdentifierOptsOrResult): IdkResult<IdentifierOptsOrResult, IdkErrorType> {
        return when (opts) {
            is ManagedIdentifierOptsOrResult -> {
                managedMulti.asSupportedOpts(opts)
            }

            is ExternalIdentifierOptsOrResult -> {
                externalMulti.asSupportedOpts(opts)
            }

            else -> {
                IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR().asErrorResult()
            }
        }
    }


    @ContributesTo(SessionScope::class)
    interface Component {
        val identifierService: IdentifierService
    }

    companion object {
        const val COMMAND_ID = "crypto.resolution.multi"
    }
}
