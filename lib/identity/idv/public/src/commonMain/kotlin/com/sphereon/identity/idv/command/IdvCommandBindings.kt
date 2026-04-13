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

package com.sphereon.identity.idv.command

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(SessionScope::class)
interface IdvCommandBindings {
    @Provides
    fun resolveIdvUseCase(registry: SessionScopedCommandRegistry): ResolveIdvUseCaseCommand =
        registry.get(ResolveIdvUseCaseCommand.COMMAND_ID) as? ResolveIdvUseCaseCommand
            ?: error("No binding for ${ResolveIdvUseCaseCommand.COMMAND_ID}")

    @Provides
    fun compileIdvGraph(registry: SessionScopedCommandRegistry): CompileIdvGraphCommand =
        registry.get(CompileIdvGraphCommand.COMMAND_ID) as? CompileIdvGraphCommand
            ?: error("No binding for ${CompileIdvGraphCommand.COMMAND_ID}")

    @Provides
    fun startIdvExecution(registry: SessionScopedCommandRegistry): StartIdvExecutionCommand =
        registry.get(StartIdvExecutionCommand.COMMAND_ID) as? StartIdvExecutionCommand
            ?: error("No binding for ${StartIdvExecutionCommand.COMMAND_ID}")

    @Provides
    fun getIdvExecution(registry: SessionScopedCommandRegistry): GetIdvExecutionCommand =
        registry.get(GetIdvExecutionCommand.COMMAND_ID) as? GetIdvExecutionCommand
            ?: error("No binding for ${GetIdvExecutionCommand.COMMAND_ID}")

    @Provides
    fun cancelIdvExecution(registry: SessionScopedCommandRegistry): CancelIdvExecutionCommand =
        registry.get(CancelIdvExecutionCommand.COMMAND_ID) as? CancelIdvExecutionCommand
            ?: error("No binding for ${CancelIdvExecutionCommand.COMMAND_ID}")

    @Provides
    fun resumeIdvExecution(registry: SessionScopedCommandRegistry): ResumeIdvExecutionCommand =
        registry.get(ResumeIdvExecutionCommand.COMMAND_ID) as? ResumeIdvExecutionCommand
            ?: error("No binding for ${ResumeIdvExecutionCommand.COMMAND_ID}")

    @Provides
    fun dispatchIdvNode(registry: SessionScopedCommandRegistry): DispatchIdvNodeCommand =
        registry.get(DispatchIdvNodeCommand.COMMAND_ID) as? DispatchIdvNodeCommand
            ?: error("No binding for ${DispatchIdvNodeCommand.COMMAND_ID}")

    @Provides
    fun submitIdvNode(registry: SessionScopedCommandRegistry): SubmitIdvNodeCommand =
        registry.get(SubmitIdvNodeCommand.COMMAND_ID) as? SubmitIdvNodeCommand
            ?: error("No binding for ${SubmitIdvNodeCommand.COMMAND_ID}")

    @Provides
    fun handleIdvNodeCallback(registry: SessionScopedCommandRegistry): HandleIdvNodeCallbackCommand =
        registry.get(HandleIdvNodeCallbackCommand.COMMAND_ID) as? HandleIdvNodeCallbackCommand
            ?: error("No binding for ${HandleIdvNodeCallbackCommand.COMMAND_ID}")

    @Provides
    fun pollIdvNode(registry: SessionScopedCommandRegistry): PollIdvNodeCommand =
        registry.get(PollIdvNodeCommand.COMMAND_ID) as? PollIdvNodeCommand
            ?: error("No binding for ${PollIdvNodeCommand.COMMAND_ID}")

    @Provides
    fun completeIdvNode(registry: SessionScopedCommandRegistry): CompleteIdvNodeCommand =
        registry.get(CompleteIdvNodeCommand.COMMAND_ID) as? CompleteIdvNodeCommand
            ?: error("No binding for ${CompleteIdvNodeCommand.COMMAND_ID}")

    @Provides
    fun applyIdvMaterialization(registry: SessionScopedCommandRegistry): ApplyIdvMaterializationCommand =
        registry.get(ApplyIdvMaterializationCommand.COMMAND_ID) as? ApplyIdvMaterializationCommand
            ?: error("No binding for ${ApplyIdvMaterializationCommand.COMMAND_ID}")
}
