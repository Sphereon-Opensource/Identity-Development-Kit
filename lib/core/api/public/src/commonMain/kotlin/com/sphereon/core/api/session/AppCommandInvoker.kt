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

package com.sphereon.core.api.session

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.asCoreApiContextGraph
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.context.PrincipalInput
import com.sphereon.di.context.TenantInput
import com.sphereon.di.context.UserContextManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * App-scoped command executor that supports cross-tenant execution.
 *
 * Extends [CommandInvoker] with an overload that accepts explicit tenant/principal context.
 * The base [CommandInvoker] methods delegate via a background service context.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AppCommandInvoker", exact = true)
interface AppCommandInvoker : CommandInvoker {
    /**
     * Execute a command with explicit tenant/principal context.
     *
     * Creates or gets the user context for the given tenant/principal,
     * then delegates to the session-scoped executor within that context.
     */
    suspend fun <TInput : Any, TOutput : Any> execute(
        tenantInput: TenantInput,
        principalInput: PrincipalInput,
        command: ServiceCommand<TInput, TOutput>,
        input: TInput,
    ): IdkResult<TOutput, IdkError>
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<AppCommandInvoker>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("AppCommandInvokerImpl", exact = true)
class AppCommandInvokerImpl(
    val userContextManager: UserContextManager,
) : AppCommandInvoker {
    private fun backgroundExecutor(): CommandInvoker = userContextManager.getBackgroundService().asCoreApiContextGraph().commandInvoker

    override fun resolve(commandId: String): ServiceCommand<*, *>? = backgroundExecutor().resolve(commandId)

    override suspend fun <TInput : Any, TOutput : Any> execute(
        command: ServiceCommand<TInput, TOutput>,
        input: TInput,
    ): IdkResult<TOutput, IdkError> = backgroundExecutor().execute(command, input)

    override fun has(commandId: String): Boolean = backgroundExecutor().has(commandId)

    override fun listCommandIds(): List<String> = backgroundExecutor().listCommandIds()

    override suspend fun <TInput : Any, TOutput : Any> execute(
        tenantInput: TenantInput,
        principalInput: PrincipalInput,
        command: ServiceCommand<TInput, TOutput>,
        input: TInput,
    ): IdkResult<TOutput, IdkError> {
        val activeContext = userContextManager.createOrGetFromInputs(tenantInput, principalInput)
        val executor = activeContext.asCoreApiContextGraph().commandInvoker
        return executor.execute(command, input)
    }
}
