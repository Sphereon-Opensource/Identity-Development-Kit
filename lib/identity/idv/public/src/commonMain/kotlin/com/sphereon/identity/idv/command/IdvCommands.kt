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

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.identity.idv.model.AttributeBag
import com.sphereon.identity.idv.model.CompiledIdvGraph
import com.sphereon.identity.idv.model.IdvExecution
import com.sphereon.identity.idv.model.IdvExecutionContext
import com.sphereon.identity.idv.model.IdvExecutionId
import com.sphereon.identity.idv.model.IdvMaterializationResult
import com.sphereon.identity.idv.model.IdvMaterializationRule
import com.sphereon.identity.idv.model.IdvNodeDispatchResult
import com.sphereon.identity.idv.model.IdvNodeId
import com.sphereon.identity.idv.model.IdvNodeResult
import com.sphereon.identity.idv.model.IdvUseCaseDefinition
import com.sphereon.identity.idv.model.IdvUseCaseId
import com.sphereon.identity.idv.model.InputFieldId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@JsExportCompat
@Serializable
data class ResolveIdvUseCaseArgs(
    val tenantId: String,
    val useCaseId: IdvUseCaseId? = null,
    val context: IdvExecutionContext? = null,
    @JsExportIgnoreCompat
    val credentialTypes: Set<String>? = null,
    @JsExportIgnoreCompat
    val dcqlQueryIds: Set<String>? = null,
    @JsExportIgnoreCompat
    val credentialSetIds: Set<String>? = null,
    val availableAttributes: AttributeBag? = null,
    @JsExportIgnoreCompat
    val providerIds: Set<String>? = null,
    @JsExportIgnoreCompat
    val formIds: Set<String>? = null,
    @JsExportIgnoreCompat
    val customTags: Set<String>? = null,
)

@JsExportCompat
@Serializable
data class CompileIdvGraphArgs(
    val useCaseDefinition: IdvUseCaseDefinition,
)

@JsExportCompat
@Serializable
data class StartIdvExecutionArgs(
    val useCaseId: IdvUseCaseId,
    val context: IdvExecutionContext,
    val callbackBaseUrl: String? = null,
)

@JsExportCompat
@Serializable
data class GetIdvExecutionArgs(
    val executionId: IdvExecutionId,
    val tenantId: String,
)

@JsExportCompat
@Serializable
data class CancelIdvExecutionArgs(
    val executionId: IdvExecutionId,
    val tenantId: String,
    val reason: String? = null,
    val expectedVersion: Long? = null,
)

@JsExportCompat
@Serializable
data class ResumeIdvExecutionArgs(
    val executionId: IdvExecutionId,
    val tenantId: String,
    val callbackBaseUrl: String? = null,
    val expectedVersion: Long? = null,
)

@JsExportCompat
@Serializable
data class DispatchIdvNodeArgs(
    val executionId: IdvExecutionId,
    val tenantId: String,
    val nodeId: IdvNodeId,
    val callbackBaseUrl: String,
    val expectedVersion: Long? = null,
)

@JsExportCompat
@Serializable
data class SubmitIdvNodeArgs(
    val executionId: IdvExecutionId,
    val tenantId: String,
    val nodeId: IdvNodeId,
    @JsExportIgnoreCompat
    val input: Map<InputFieldId, JsonElement>,
    val expectedVersion: Long? = null,
)

@JsExportCompat
@Serializable
data class HandleIdvNodeCallbackArgs(
    val executionId: IdvExecutionId,
    val tenantId: String,
    val nodeId: IdvNodeId,
    @JsExportIgnoreCompat
    val callbackData: Map<String, String>,
    val expectedVersion: Long? = null,
)

@JsExportCompat
@Serializable
data class PollIdvNodeArgs(
    val executionId: IdvExecutionId,
    val tenantId: String,
    val nodeId: IdvNodeId,
    val expectedVersion: Long? = null,
)

@JsExportCompat
@Serializable
data class CompleteIdvNodeArgs(
    val executionId: IdvExecutionId,
    val tenantId: String,
    val nodeId: IdvNodeId,
    val result: IdvNodeResult,
    val expectedVersion: Long? = null,
)

@JsExportCompat
@Serializable
data class ApplyIdvMaterializationArgs(
    val executionId: IdvExecutionId,
    val tenantId: String,
    val rules: List<IdvMaterializationRule>? = null,
    val expectedVersion: Long? = null,
)

@JsExportCompat
interface ResolveIdvUseCaseCommand : ServiceCommand<ResolveIdvUseCaseArgs, IdvUseCaseDefinition> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.usecase.resolve"
    }
}

@JsExportCompat
interface CompileIdvGraphCommand : ServiceCommand<CompileIdvGraphArgs, CompiledIdvGraph> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.graph.compile"
    }
}

@JsExportCompat
interface StartIdvExecutionCommand : ServiceCommand<StartIdvExecutionArgs, IdvExecution> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.execution.start"
    }
}

@JsExportCompat
interface GetIdvExecutionCommand : ServiceCommand<GetIdvExecutionArgs, IdvExecution> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.execution.get"
    }
}

@JsExportCompat
interface CancelIdvExecutionCommand : ServiceCommand<CancelIdvExecutionArgs, IdvExecution> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.execution.cancel"
    }
}

@JsExportCompat
interface ResumeIdvExecutionCommand : ServiceCommand<ResumeIdvExecutionArgs, IdvExecution> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.execution.resume"
    }
}

@JsExportCompat
interface DispatchIdvNodeCommand : ServiceCommand<DispatchIdvNodeArgs, IdvNodeDispatchResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.node.dispatch"
    }
}

@JsExportCompat
interface SubmitIdvNodeCommand : ServiceCommand<SubmitIdvNodeArgs, IdvExecution> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.node.submit"
    }
}

@JsExportCompat
interface HandleIdvNodeCallbackCommand : ServiceCommand<HandleIdvNodeCallbackArgs, IdvExecution> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.node.callback"
    }
}

@JsExportCompat
interface PollIdvNodeCommand : ServiceCommand<PollIdvNodeArgs, IdvExecution> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.node.poll"
    }
}

@JsExportCompat
interface CompleteIdvNodeCommand : ServiceCommand<CompleteIdvNodeArgs, IdvExecution> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.node.complete"
    }
}

@JsExportCompat
interface ApplyIdvMaterializationCommand : ServiceCommand<ApplyIdvMaterializationArgs, IdvMaterializationResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.materialization.apply"
    }
}
