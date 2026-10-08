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

import com.sphereon.attribute.flow.AttributeBag
import com.sphereon.attribute.flow.InputFieldId
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.identity.idv.model.CompiledIdvGraph
import com.sphereon.identity.idv.model.IdvExecution
import com.sphereon.identity.idv.model.IdvExecutionContext
import com.sphereon.identity.idv.model.IdvExecutionId
import com.sphereon.identity.idv.model.IdvMaterializationResult
import com.sphereon.identity.idv.model.IdvMaterializationRule
import com.sphereon.identity.idv.model.IdvNode
import com.sphereon.identity.idv.model.IdvNodeDispatchResult
import com.sphereon.identity.idv.model.IdvNodeId
import com.sphereon.identity.idv.model.IdvNodeResult
import com.sphereon.identity.idv.model.IdvUseCaseDefinition
import com.sphereon.identity.idv.model.IdvUseCaseId
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

/**
 * Args for [StartAdhocIdvExecutionCommand]. The caller supplies the [graph] directly
 * — no use-case lookup happens — so the AS's required-actions orchestrator can drive
 * IDV with a graph composed on the fly from
 * [com.sphereon.oauth2.server.authorization.requiredaction.RequiredActionGraphProvider]s.
 *
 * **Why a sibling command, not a flag on [StartIdvExecutionArgs]:** a stored
 * use-case execution carries the use case's `policy` (assurance floor, TTL,
 * retention, regulatory context) — none of which apply to a synthetic
 * required-actions graph. Splitting the commands keeps the stored-use-case path
 * purely declarative while letting the ad-hoc path supply only what it actually
 * has.
 */
@JsExportCompat
@Serializable
data class StartAdhocIdvExecutionArgs(
    val context: IdvExecutionContext,
    /** The graph to execute. Typically a [com.sphereon.identity.idv.model.SequenceNode] or [com.sphereon.identity.idv.model.MethodNode]. */
    val graph: IdvNode,
    val callbackBaseUrl: String? = null,
    /**
     * Human-readable label surfaced on telemetry / audit events for the synthetic
     * use case. Required-actions orchestration uses "required-actions" so logs are
     * filterable; defaults work for tests.
     */
    val syntheticUseCaseName: String = "adhoc",
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
interface ResolveIdvUseCaseCommand : ServiceCommand<ResolveIdvUseCaseArgs, IdvUseCaseDefinition, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.usecase.resolve"
    }
}

@JsExportCompat
interface CompileIdvGraphCommand : ServiceCommand<CompileIdvGraphArgs, CompiledIdvGraph, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.graph.compile"
    }
}

@JsExportCompat
interface StartIdvExecutionCommand : ServiceCommand<StartIdvExecutionArgs, IdvExecution, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.execution.start"
    }
}

/**
 * Sibling of [StartIdvExecutionCommand] for ad-hoc graphs. Drives an [IdvExecution]
 * from a caller-supplied [com.sphereon.identity.idv.model.IdvNode] without
 * consulting the [com.sphereon.identity.idv.store.IdvUseCaseDefinitionStore]. Used
 * by the AS required-actions orchestrator's `ADHOC` strategy; tenants that prefer
 * a stored, curated graph still call [StartIdvExecutionCommand] with a use-case id.
 */
@JsExportCompat
interface StartAdhocIdvExecutionCommand : ServiceCommand<StartAdhocIdvExecutionArgs, IdvExecution, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.execution.start-adhoc"
    }
}

@JsExportCompat
interface GetIdvExecutionCommand : ServiceCommand<GetIdvExecutionArgs, IdvExecution, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.execution.get"
    }
}

@JsExportCompat
interface CancelIdvExecutionCommand : ServiceCommand<CancelIdvExecutionArgs, IdvExecution, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.execution.cancel"
    }
}

@JsExportCompat
interface ResumeIdvExecutionCommand : ServiceCommand<ResumeIdvExecutionArgs, IdvExecution, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.execution.resume"
    }
}

@JsExportCompat
interface DispatchIdvNodeCommand : ServiceCommand<DispatchIdvNodeArgs, IdvNodeDispatchResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.node.dispatch"
    }
}

@JsExportCompat
interface SubmitIdvNodeCommand : ServiceCommand<SubmitIdvNodeArgs, IdvExecution, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.node.submit"
    }
}

@JsExportCompat
interface HandleIdvNodeCallbackCommand : ServiceCommand<HandleIdvNodeCallbackArgs, IdvExecution, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.node.callback"
    }
}

@JsExportCompat
interface PollIdvNodeCommand : ServiceCommand<PollIdvNodeArgs, IdvExecution, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.node.poll"
    }
}

@JsExportCompat
interface CompleteIdvNodeCommand : ServiceCommand<CompleteIdvNodeArgs, IdvExecution, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.node.complete"
    }
}

@JsExportCompat
interface ApplyIdvMaterializationCommand : ServiceCommand<ApplyIdvMaterializationArgs, IdvMaterializationResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "idv.materialization.apply"
    }
}
