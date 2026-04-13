package com.sphereon.core.api.service.contract

import kotlinx.serialization.Serializable

/**
 * Result of [ServiceCommandContract.plan] — describes what sub-commands a compound
 * command will delegate to during execution, along with resource hints for
 * pre-authorization.
 *
 * The policy layer uses this to pre-authorize the full execution graph before any
 * work begins, preventing partial execution with committed side effects.
 */
@Serializable
data class CommandPlan(
    val intents: List<CommandIntent>,
    val completeness: PlanningCompleteness = PlanningCompleteness.COMPLETE,
) {
    companion object {
        /** Leaf command — no delegation. */
        fun leaf() = CommandPlan(emptyList())
    }
}

/**
 * A single delegation intent within a [CommandPlan].
 */
@Serializable
data class CommandIntent(
    /** Command ID that will be delegated to */
    val commandId: String,
    /** Resource hints for pre-authorization (partial attributes from args) */
    val resourceHints: List<ResourceInstance> = emptyList(),
    /** True if this delegation may be skipped depending on runtime conditions */
    val optional: Boolean = false,
    /** Human-readable reason for this delegation */
    val reason: String? = null,
)

/**
 * How complete the plan is — determines the pre-authorization strategy.
 */
@Serializable
enum class PlanningCompleteness {
    /** All delegated commands are known upfront. Pre-authorize all before execution. */
    COMPLETE,

    /** Some commands are known, others depend on runtime state. Pre-authorize known, re-authorize later. */
    PARTIAL,

    /** Delegation is fully dynamic. Only pre-authorize the parent command. */
    DYNAMIC,
}

/**
 * Context provided to [ServiceCommandContract.plan] for planning decisions.
 */
@Serializable
data class PlanningContext(
    val tenantId: String? = null,
    val principalId: String? = null,
)
