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

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.data.store.party.model.PartyType
import com.sphereon.identity.matching.model.IdentityMatch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

@JsExportCompat
@Serializable
data class IdvSubjectRef(
    val ownerPartyId: String? = null,
    val identityId: String? = null,
    val targetPartyType: PartyType,
    val relationshipContext: PartyRelationshipRef? = null,
)

@JsExportCompat
@Serializable
data class PartyRelationshipRef(
    val organizationPartyId: String,
    val relationshipType: String,
)

@JsExportCompat
@Serializable
data class IdvExecutionContext(
    val tenantId: String,
    val subject: IdvSubjectRef,
    val availableAttributes: AttributeBag = AttributeBag.empty(),
    val entryPointType: IdvEntryPointType? = null,
    val triggerType: IdvTriggerType? = null,
    val workflowId: String? = null,
    val correlationId: String? = null,
)

@JsExportCompat
@Serializable
data class AttributeBag(
    @JsExportIgnoreCompat
    val attributes: Map<AttributePath, JsonElement> = emptyMap(),
    @JsExportIgnoreCompat
    val provenance: Map<AttributePath, IdvNodeId> = emptyMap(),
) {
    companion object {
        fun empty(): AttributeBag = AttributeBag()
    }

    operator fun get(key: AttributePath): JsonElement? = attributes[key]

    fun with(
        key: AttributePath,
        value: JsonElement,
        source: IdvNodeId? = null,
    ): AttributeBag =
        AttributeBag(
            attributes = attributes + (key to value),
            provenance =
                if (source != null) {
                    provenance + (key to source)
                } else {
                    provenance
                },
        )

    @JsExportIgnoreCompat
    fun withAll(
        values: Map<AttributePath, JsonElement>,
        source: IdvNodeId? = null,
    ): AttributeBag =
        AttributeBag(
            attributes = attributes + values,
            provenance =
                if (source != null) {
                    provenance + values.keys.associateWith { source }
                } else {
                    provenance
                },
        )
}

@JsExportCompat
@Serializable
sealed interface IdvError {
    val message: String
    val nodeId: IdvNodeId?
}

@Serializable
data class DriverError(
    override val message: String,
    override val nodeId: IdvNodeId,
    val methodId: IdvMethodId,
    val providerErrorCode: String? = null,
    val retryable: Boolean = false,
) : IdvError

@Serializable
data class GraphEvaluationError(
    override val message: String,
    override val nodeId: IdvNodeId,
    val failedChildren: List<IdvNodeId> = emptyList(),
) : IdvError

@Serializable
data class AssuranceError(
    override val message: String,
    override val nodeId: IdvNodeId? = null,
    val required: EidasAssuranceLevel,
    val achieved: EidasAssuranceLevel,
) : IdvError

@Serializable
data class ComplianceError(
    override val message: String,
    override val nodeId: IdvNodeId? = null,
    val regulation: TrustFrameworkType? = null,
    val violation: String,
) : IdvError

@Serializable
data class LifecycleError(
    override val message: String,
    override val nodeId: IdvNodeId? = null,
    val status: IdvExecutionStatus,
) : IdvError

@Serializable
data class MaterializationError(
    override val message: String,
    override val nodeId: IdvNodeId? = null,
    val failedRules: List<String> = emptyList(),
) : IdvError

@Serializable
data class RateLimitError(
    override val message: String,
    override val nodeId: IdvNodeId? = null,
    val retryAfterMs: Long? = null,
) : IdvError

@Serializable
data class ConcurrencyError(
    override val message: String,
    override val nodeId: IdvNodeId? = null,
    val expectedVersion: Long,
    val actualVersion: Long,
) : IdvError

@JsExportCompat
@Serializable
data class IdvUseCaseDefinition(
    val id: IdvUseCaseId,
    val tenantId: String,
    val name: String,
    val description: String? = null,
    val enabled: Boolean = true,
    val target: IdvTargetPolicy,
    val triggers: List<IdvUseCaseTrigger>,
    val graph: IdvNode,
    val policy: IdvExecutionPolicy,
    val materializations: List<IdvMaterializationRule>,
    val userMessage: String? = null,
    val completionMessage: String? = null,
)

@JsExportCompat
@Serializable
data class IdvTargetPolicy(
    val associationType: IdentityAssociationType,
    val allowExistingIdentity: Boolean = true,
    val autoCreateParty: Boolean = true,
)

@JsExportCompat
@Serializable
data class IdvExecutionPolicy(
    val minimumAssurance: EidasAssuranceLevel,
    val minimumAal: AuthAssuranceLevel?,
    val sessionTtlSeconds: Int = 3600,
    val allowPartialCompletion: Boolean = false,
    val rateLimitPolicy: RateLimitPolicy? = null,
    val regulatoryContext: RegulatoryContext? = null,
    val evidenceRetentionPolicy: RetentionPolicy? = null,
    @JsExportIgnoreCompat
    val requiredConsents: Set<ConsentType> = emptySet(),
    val territoryId: String? = null,
)

@JsExportCompat
@Serializable
data class RateLimitPolicy(
    val maxExecutionsPerHour: Int? = null,
    val maxExecutionsPerDay: Int? = null,
    val maxConcurrentExecutions: Int? = null,
    val perSubjectLimit: Boolean = true,
)

@JsExportCompat
@Serializable
data class RegulatoryContext(
    val framework: TrustFrameworkType,
    val cddLevel: CddLevel? = null,
    val reviewCycleDays: Int? = null,
    val etsiLoip: EtsiLoip? = null,
)

@JsExportCompat
@Serializable
data class RetentionPolicy(
    val evidenceRetentionDays: Int,
    val identifierRetentionDays: Int? = null,
    val legalBasis: LegalBasis,
)

@JsExportCompat
@Serializable
data class IdvUseCaseTrigger(
    val entryPointType: IdvEntryPointType? = null,
    @JsExportIgnoreCompat
    val credentialTypes: Set<String>? = null,
    @JsExportIgnoreCompat
    val dcqlQueryIds: Set<String>? = null,
    @JsExportIgnoreCompat
    val credentialSetIds: Set<String>? = null,
    val attributePredicates: List<AttributePredicate>? = null,
    @JsExportIgnoreCompat
    val providerIds: Set<String>? = null,
    @JsExportIgnoreCompat
    val formIds: Set<String>? = null,
    val triggerType: IdvTriggerType? = null,
    val priority: Int = 0,
    @JsExportIgnoreCompat
    val customTags: Set<String>? = null,
)

@JsExportCompat
@Serializable
sealed interface IdvMaterializationRule

@Serializable
data class CreateIdentifiersMaterialization(
    val fromNodes: Set<IdvNodeId>? = null,
    val identifierFilter: Set<IdentifierType>? = null,
) : IdvMaterializationRule

@Serializable
data class CreateIdentityMatchMaterialization(
    val fromNodes: Set<IdvNodeId>? = null,
    val hashAlgorithm: JwaAlgorithm = JwaAlgorithm.HS256,
) : IdvMaterializationRule

@Serializable
data class CreateRelationshipMaterialization(
    val relationshipType: String,
    val sourceNodeId: IdvNodeId,
) : IdvMaterializationRule

@Serializable
data class CreateRegistrationMaterialization(
    val registrationType: String,
    val sourceNodeId: IdvNodeId,
    val jurisdictionCountry: String? = null,
) : IdvMaterializationRule

@Serializable
data class AttachEvidenceMaterialization(
    val fromNodes: Set<IdvNodeId>? = null,
) : IdvMaterializationRule

@Serializable
data class MarkVerifiedMaterialization(
    val role: String? = null,
    val forUseCaseId: IdvUseCaseId? = null,
) : IdvMaterializationRule

@JsExportCompat
@Serializable
data class IdvExecution(
    val executionId: IdvExecutionId,
    val useCaseId: IdvUseCaseId,
    val tenantId: String,
    val subject: IdvSubjectRef,
    val status: IdvExecutionStatus,
    @JsExportIgnoreCompat
    val nodeStates: Map<IdvNodeId, IdvNodeState>,
    val currentPendingActions: List<NodePendingAction>,
    val accumulatedAttributes: AttributeBag,
    val results: List<IdvNodeResult>,
    val errors: List<IdvError>,
    val pendingMaterializations: List<IdvMaterializationRule>,
    val version: Long,
    val createdAt: Instant,
    val expiresAt: Instant,
    val workflowId: String? = null,
    val correlationId: String? = null,
)

@JsExportCompat
@Serializable
data class NodePendingAction(
    val nodeId: IdvNodeId,
    val methodId: IdvMethodId,
    val action: IdvPendingAction,
)

@JsExportCompat
@Serializable
data class IdvNodeResult(
    val nodeId: IdvNodeId,
    val methodId: IdvMethodId,
    val identifiers: List<ResolvedIdentifier>,
    @JsExportIgnoreCompat
    val attributes: Map<AttributePath, JsonElement>,
    val assurance: EidasAssuranceLevel,
    val aal: AuthAssuranceLevel,
    @JsExportIgnoreCompat
    val amr: Set<AuthMethodReference>,
    val evidence: IdvEvidence,
    val trustFramework: TrustFrameworkType? = null,
    val evidenceStrength: EvidenceStrength? = null,
    val proofingScenario: ProofingScenario? = null,
    val verifiedAttributesMetadata: VerifiedAttributesMetadata? = null,
)

@JsExportCompat
@Serializable
data class ResolvedIdentifier(
    val type: IdentifierType,
    val value: String,
    val verified: Boolean,
    val assurance: EidasAssuranceLevel,
)

@JsExportCompat
@Serializable
data class IdvEvidence(
    val provider: String,
    val method: String,
    val timestamp: Instant,
    @JsExportIgnoreCompat
    val metadata: Map<String, JsonElement> = emptyMap(),
    val responseHash: String? = null,
    val evidenceType: IdvEvidenceType? = null,
)

@JsExportCompat
@Serializable
data class VerifiedAttributesMetadata(
    val trustFramework: TrustFrameworkType,
    val assuranceLevel: EidasAssuranceLevel,
    val evidenceType: IdvEvidenceType,
    val verificationMethod: String,
)

@JsExportCompat
@Serializable
data class IdvExecutionResult(
    val executionId: IdvExecutionId,
    val status: IdvExecutionStatus,
    val overallAssurance: EidasAssuranceLevel,
    val overallAal: AuthAssuranceLevel,
    @JsExportIgnoreCompat
    val combinedAmr: Set<AuthMethodReference>,
    val allIdentifiers: List<ResolvedIdentifier>,
    val allAttributes: AttributeBag,
    val meetsUseCaseRequirements: Boolean,
    val errors: List<IdvError>,
    val materializationStatus: IdvMaterializationResult? = null,
)

@JsExportCompat
@Serializable
data class IdvMaterializationResult(
    val createdIdentifiers: List<String>,
    val createdMatches: List<String>,
    val createdRelationships: List<String>,
    val createdRegistrations: List<String>,
    val errors: List<MaterializationError> = emptyList(),
)

@JsExportCompat
@Serializable
data class CompiledIdvGraph(
    val rootNode: IdvNode,
    @JsExportIgnoreCompat
    val nodesById: Map<IdvNodeId, IdvNode>,
    val executionOrder: List<IdvNodeId>,
)

@JsExportCompat
@Serializable
data class IdvNodeDispatchResult(
    val execution: IdvExecution,
    val outcome: DispatchOutcome,
)

@JsExportCompat
@Serializable
data class IdvMaterializationProjection(
    val execution: IdvExecution,
    val materializationResult: IdvMaterializationResult,
    val createdMatches: List<IdentityMatch> = emptyList(),
)
