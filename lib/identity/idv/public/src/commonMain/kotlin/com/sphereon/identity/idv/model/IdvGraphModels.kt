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

package com.sphereon.identity.idv.model

import com.sphereon.attribute.flow.AttributeBinding
import com.sphereon.attribute.flow.AttributePath
import com.sphereon.attribute.flow.InputFieldId
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

@JsExportCompat
@Serializable
sealed interface IdvNode {
    val nodeId: IdvNodeId
}

@Serializable
data class MethodNode(
    override val nodeId: IdvNodeId,
    val methodId: IdvMethodId,
    val inputBindings: List<AttributeBinding> = emptyList(),
    val configOverrides: JsonObject? = null,
    val skipIfAttributePresent: AttributePath? = null,
    val displayOverride: IdvDisplay? = null,
) : IdvNode

@Serializable
data class SequenceNode(
    override val nodeId: IdvNodeId,
    val children: List<IdvNode>,
) : IdvNode

@Serializable
data class ParallelNode(
    override val nodeId: IdvNodeId,
    val children: List<IdvNode>,
    val joinPolicy: JoinPolicy = JoinPolicy.All,
    val failurePolicy: FailurePolicy = FailurePolicy.FailFast,
) : IdvNode

@Serializable
data class ChoiceNode(
    override val nodeId: IdvNodeId,
    val children: List<IdvNode>,
    val selectionPolicy: SelectionPolicy = SelectionPolicy.UserSelect,
) : IdvNode

@Serializable
data class ThresholdNode(
    override val nodeId: IdvNodeId,
    val children: List<IdvNode>,
    val minimumSuccesses: Int,
    val failurePolicy: FailurePolicy = FailurePolicy.WaitForAll,
) : IdvNode

@JsExportCompat
@Serializable
enum class JoinPolicy {
    All,
    Any,
    BestEffort,
}

@JsExportCompat
@Serializable
enum class SelectionPolicy {
    UserSelect,
    HighestAssurance,
    LowestCost,
    FirstAvailable,
}

@JsExportCompat
@Serializable
enum class FailurePolicy {
    FailFast,
    WaitForAll,
    ContinueOnFailure,
}

@JsExportCompat
@Serializable
data class IdvDisplay(
    val name: String,
    val description: String? = null,
    val iconUri: String? = null,
)

@JsExportCompat
@Serializable
sealed interface IdvNodeState {
    val nodeId: IdvNodeId
    val status: IdvNodeStatus
    val version: Long
}

@Serializable
data class PendingNodeState(
    override val nodeId: IdvNodeId,
    override val status: IdvNodeStatus = IdvNodeStatus.PENDING,
    override val version: Long = 0,
) : IdvNodeState

@Serializable
data class DispatchedNodeState(
    override val nodeId: IdvNodeId,
    override val status: IdvNodeStatus = IdvNodeStatus.DISPATCHED,
    override val version: Long,
    val pendingAction: IdvPendingAction,
    val driverState: JsonObject? = null,
    val dispatchedAt: Instant,
) : IdvNodeState

@Serializable
data class CompletedNodeState(
    override val nodeId: IdvNodeId,
    override val status: IdvNodeStatus = IdvNodeStatus.COMPLETED,
    override val version: Long,
    val result: IdvNodeResult,
    val completedAt: Instant,
) : IdvNodeState

@Serializable
data class FailedNodeState(
    override val nodeId: IdvNodeId,
    override val status: IdvNodeStatus = IdvNodeStatus.FAILED,
    override val version: Long,
    val error: IdvError,
    val failedAt: Instant,
) : IdvNodeState

@Serializable
data class SkippedNodeState(
    override val nodeId: IdvNodeId,
    override val status: IdvNodeStatus = IdvNodeStatus.SKIPPED,
    override val version: Long,
    val reason: String,
) : IdvNodeState

@Serializable
data class CancelledNodeState(
    override val nodeId: IdvNodeId,
    override val status: IdvNodeStatus = IdvNodeStatus.CANCELLED,
    override val version: Long,
    val cancelledAt: Instant,
    val reason: String? = null,
) : IdvNodeState

@JsExportCompat
@Serializable
sealed interface IdvPendingAction

@Serializable
data class RedirectAction(
    val url: String,
    val callbackRef: String,
    val expiresAt: Instant? = null,
) : IdvPendingAction

@Serializable
data class UserInputAction(
    val form: InputFormDescriptor,
) : IdvPendingAction

@Serializable
data class PollAction(
    val nextCheckAt: Instant,
    val pollIntervalMs: Long = 5000,
) : IdvPendingAction

@Serializable
data class WaitForCallbackAction(
    val callbackRef: String,
    val expiresAt: Instant,
) : IdvPendingAction

@Serializable
data class CompletedAction(
    val result: IdvNodeResult,
) : IdvPendingAction

@JsExportCompat
@Serializable
data class InputFormDescriptor(
    val fields: List<InputField>,
    val submitLabel: String = "Submit",
)

@JsExportCompat
@Serializable
data class InputField(
    val id: InputFieldId,
    val label: String,
    val type: InputFieldType,
    val required: Boolean = true,
    val options: List<InputOption>? = null,
)

@JsExportCompat
@Serializable
enum class InputFieldType {
    TEXT,
    CODE,
    SELECT,
    HIDDEN,

    /**
     * Browser renders as `<input type="password">` so the value is masked on
     * screen and excluded from password-manager autofill on hostile origins.
     * The transport itself does not impose a different validation contract;
     * the driver is responsible for any server-side strength / breach checks.
     */
    PASSWORD,
}

@JsExportCompat
@Serializable
data class InputOption(
    val value: String,
    val label: String,
    val description: String? = null,
    val iconUri: String? = null,
)
