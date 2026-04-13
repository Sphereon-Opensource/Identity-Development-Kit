package com.sphereon.core.api.service.contract

import kotlinx.serialization.Serializable

/**
 * Declares what surfaces a command contract is ready for.
 *
 * EDK can enforce: "only expose to MCP if MCP_READY in capabilities".
 * Commands without declared capabilities are discoverable but not
 * exposed to external surfaces.
 */
@Serializable
enum class ContractCapability {
    /** Command ID, action type, operation type declared */
    DISCOVERABLE,

    /** Resource target + schema overlay sufficient for policy evaluation */
    POLICY_READY,

    /** Schema overlay + descriptions sufficient for workflow step UIs */
    WORKFLOW_READY,

    /** Schema overlay + descriptions sufficient for MCP tool exposure */
    MCP_READY,

    /** Sensitivity declared on all relevant fields; audit redaction rules defined */
    AUDIT_READY,
}

// ========== DSL ==========

fun capabilities(block: CapabilitiesBuilder.() -> Unit): Set<ContractCapability> = CapabilitiesBuilder().apply(block).build()

class CapabilitiesBuilder {
    private val caps = mutableSetOf<ContractCapability>()

    fun discoverable() {
        caps.add(ContractCapability.DISCOVERABLE)
    }

    fun policyReady() {
        caps.add(ContractCapability.POLICY_READY)
    }

    fun workflowReady() {
        caps.add(ContractCapability.WORKFLOW_READY)
    }

    fun mcpReady() {
        caps.add(ContractCapability.MCP_READY)
    }

    fun auditReady() {
        caps.add(ContractCapability.AUDIT_READY)
    }

    fun build(): Set<ContractCapability> = caps.toSet()
}
