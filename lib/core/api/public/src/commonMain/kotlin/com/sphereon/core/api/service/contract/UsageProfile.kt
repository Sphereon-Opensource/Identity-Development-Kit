package com.sphereon.core.api.service.contract

import kotlinx.serialization.Serializable

/**
 * Classifies a command's usage characteristics for metering, feature gating,
 * and rate limiting.
 *
 * IDK declares the profile as neutral metadata. EDK/VDX interpret it for:
 * - License enforcement: [featureGates] checked against the active license
 * - Usage metering: [weight] aggregated for usage reporting
 * - Quota management: [weight] determines how much of a quota a single invocation consumes
 *
 * [weight] = 0 means the command is exempt from metering (health checks, discovery).
 * [weight] >= 1 means the command is metered, with higher values consuming more quota.
 * [featureGates] are orthogonal to weight — they control license-level access.
 *
 * All values are defaults — EDK config can override per commandId pattern and per tenant.
 */
@Serializable
data class UsageProfile(
    /** Relative weight for usage aggregation. 0 = exempt (not tracked). 1 = baseline. Higher = heavier.
     *  EDK maps this to concrete units in the metering layer. */
    val weight: Int = 1,
    /** Feature gate identifiers. EDK license service checks that ALL gates
     *  are satisfied by the active license before allowing execution.
     *  Empty = no feature gating (available in all license tiers). */
    val featureGates: Set<String> = emptySet(),
) {
    companion object {
        val EXEMPT = UsageProfile(weight = 0)
        val STANDARD = UsageProfile()
    }
}

// ========== DSL ==========

fun usageProfile(block: UsageProfileBuilder.() -> Unit): UsageProfile = UsageProfileBuilder().apply(block).build()

class UsageProfileBuilder {
    private var weight: Int = 1
    private val gates = mutableSetOf<String>()

    fun exempt() {
        weight = 0
    }

    fun weight(w: Int) {
        weight = w
    }

    fun featureGate(vararg gate: String) {
        gates.addAll(gate)
    }

    fun build() = UsageProfile(weight, gates.toSet())
}
