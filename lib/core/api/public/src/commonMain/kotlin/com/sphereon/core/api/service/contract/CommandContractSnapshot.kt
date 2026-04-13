package com.sphereon.core.api.service.contract

import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.ErrorDefinitionType
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.session.CommandId
import kotlinx.serialization.Serializable

/**
 * Serializable snapshot of an error definition from a command contract.
 */
@Serializable
data class ErrorContractEntry(
    val code: String,
    val category: ErrorCategory,
    val defaultMessage: String,
    val severity: IdkError.Severity = IdkError.Severity.ERROR,
) {
    companion object {
        fun fromDefinition(definition: ErrorDefinitionType) =
            ErrorContractEntry(
                code = definition.code,
                category = definition.category,
                defaultMessage = definition.defaultMessage,
                severity = definition.severity,
            )
    }
}

/**
 * Serializable snapshot of a [ServiceCommandContract]'s static metadata.
 *
 * Metadata-only — does NOT include resolved structural schema (field types,
 * nullability, nested structures). For the full resolved schema, use EDK's
 * `CommandSchemaService` which produces `ResolvedCommandContractSnapshot`.
 *
 * Use cases:
 * - Persistence: store contract metadata in database for admin UIs
 * - REST APIs: return command metadata from discovery endpoints
 * - Cross-service: share contract metadata between microservices
 */
@Serializable
data class CommandContractSnapshot(
    val commandId: CommandId,
    val actionType: ActionType,
    val operationType: OperationType,
    val summary: String? = null,
    val description: String? = null,
    val resourceTarget: ResourceTargetDescriptor = ResourceTargetDescriptor.UNSPECIFIED,
    val assuranceRequirements: AssuranceRequirements = AssuranceRequirements.UNSPECIFIED,
    val executionTraits: ExecutionTraits = ExecutionTraits.UNSPECIFIED,
    val usageProfile: UsageProfile = UsageProfile.STANDARD,
    val complianceProfile: ComplianceProfile = ComplianceProfile.NONE,
    val inputSchemaOverlay: SchemaOverlay = SchemaOverlay.EMPTY,
    val outputSchemaOverlay: SchemaOverlay = SchemaOverlay.EMPTY,
    val capabilities: Set<ContractCapability> = emptySet(),
    val declaredIntentTargets: Set<CommandId> = emptySet(),
    val possibleErrors: Set<ErrorContractEntry> = emptySet(),
)
