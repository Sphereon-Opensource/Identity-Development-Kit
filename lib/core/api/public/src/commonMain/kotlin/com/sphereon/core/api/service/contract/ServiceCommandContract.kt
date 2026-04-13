package com.sphereon.core.api.service.contract

import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.error.ErrorDefinitionType
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.session.CommandId

/**
 * App-scoped contract declaring all static metadata for a service command.
 *
 * Discoverable without [SessionExecution] — registered at AppScope via
 * `@IntoMap @StringKey(COMMAND_ID)` multibinding. Used by:
 * - Policy: resource targeting, assurance requirements, planning
 * - Workflow: step discovery, input/output schema, descriptions
 * - MCP: tool exposure with parameter descriptions
 * - Audit: sensitivity classification, capability declarations
 *
 * Session-scoped [ServiceCommand] references this via [ServiceCommand.contract].
 *
 * i18n keys are derived by convention:
 * - `cmd.{commandId.value}.summary`
 * - `cmd.{commandId.value}.description`
 * - `cmd.{commandId.value}.field.{fieldName}.label`
 * - `cmd.{commandId.value}.field.{fieldName}.description`
 */
interface ServiceCommandContract<TIn : Any, TOut : Any> {
    // ========== Identity ==========

    val commandId: CommandId
    val actionType: ActionType get() = ActionType.EXECUTE
    val operationType: OperationType get() = OperationType.fromActionType(actionType)

    // ========== Human-readable ==========

    val summary: String? get() = null
    val description: String? get() = null

    // ========== Schema ==========

    /** Type token for the input type. Carries KType; EDK resolves to KSerializer for structural schema. */
    val inputTypeToken: TypeToken<TIn>

    /** Type token for the output type. */
    val outputTypeToken: TypeToken<TOut>

    /** Metadata overlay for input fields (labels, sensitivity, policy mapping). */
    val inputSchemaOverlay: SchemaOverlay get() = SchemaOverlay.EMPTY

    /** Metadata overlay for output fields. */
    val outputSchemaOverlay: SchemaOverlay get() = SchemaOverlay.EMPTY

    // ========== Policy ==========

    /** Resource type and attribute schema this command targets. */
    val resourceTarget: ResourceTargetDescriptor get() = ResourceTargetDescriptor.UNSPECIFIED

    /** Authentication assurance requirements (defaults, config-overrideable). */
    val assuranceRequirements: AssuranceRequirements get() = AssuranceRequirements.UNSPECIFIED

    // ========== Execution ==========

    /** Execution characteristics (idempotency, deferral, scheduling, duration). */
    val executionTraits: ExecutionTraits get() = ExecutionTraits.UNSPECIFIED

    // ========== Usage ==========

    /** Usage classification for metering, feature gating, and rate limiting. */
    val usageProfile: UsageProfile get() = UsageProfile.STANDARD

    // ========== Compliance ==========

    /** Regulatory and compliance context. Frameworks, lawful basis, consent, retention, DPIA. */
    val complianceProfile: ComplianceProfile get() = ComplianceProfile.NONE

    // ========== Compound command planning ==========

    /**
     * Static declaration of possible delegation targets for startup linting and
     * cycle detection. Unlike [plan], this does not depend on runtime input.
     */
    val declaredIntentTargets: Set<CommandId> get() = emptySet()

    // ========== Error contract ==========

    /**
     * Declared error set for this operation. Finite set of [ErrorDefinitionType]
     * that this command may return. Empty means unspecified (legacy commands).
     *
     * Used for:
     * - Contract validation at startup
     * - OpenAPI error response generation
     * - Typed error handling alignment with Kotlin Rich Errors direction
     */
    val possibleErrors: Set<ErrorDefinitionType> get() = emptySet()

    // ========== Capabilities ==========

    /** Declares what surfaces this command is ready for. */
    val capabilities: Set<ContractCapability> get() = emptySet()

    /**
     * Produces a plan describing which sub-commands this command will delegate to.
     * The plan can depend on the actual input args and runtime context.
     *
     * Default: leaf command (no delegation).
     */
    suspend fun plan(
        args: TIn,
        context: PlanningContext,
    ): CommandPlan = CommandPlan.leaf()

    // ========== Snapshot ==========

    /**
     * Serializable metadata snapshot. Does NOT include resolved structural schema
     * (that requires EDK's CommandSchemaService).
     */
    fun toSnapshot(): CommandContractSnapshot =
        CommandContractSnapshot(
            commandId = commandId,
            actionType = actionType,
            operationType = operationType,
            summary = summary,
            description = description,
            resourceTarget = resourceTarget,
            assuranceRequirements = assuranceRequirements,
            executionTraits = executionTraits,
            usageProfile = usageProfile,
            complianceProfile = complianceProfile,
            inputSchemaOverlay = inputSchemaOverlay,
            outputSchemaOverlay = outputSchemaOverlay,
            capabilities = capabilities,
            declaredIntentTargets = declaredIntentTargets,
            possibleErrors = possibleErrors.map { ErrorContractEntry.fromDefinition(it) }.toSet(),
        )
}
