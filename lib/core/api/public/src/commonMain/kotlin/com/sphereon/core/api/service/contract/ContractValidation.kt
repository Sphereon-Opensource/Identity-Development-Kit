package com.sphereon.core.api.service.contract

import com.sphereon.core.api.service.PublicApiCommand
import com.sphereon.core.api.session.CommandId

/**
 * Severity levels for contract validation findings.
 */
enum class ValidationSeverity { ERROR, WARNING, INFO }

/**
 * A single contract validation finding.
 */
data class ContractValidationFinding(
    val severity: ValidationSeverity,
    val commandId: String,
    val message: String,
)

/**
 * IDK-level startup validation for command contracts.
 *
 * Validates static metadata that can be checked without serializer resolution.
 * EDK adds additional validation (overlay vs SerialDescriptor, schema completeness).
 */
object ContractValidator {
    /**
     * Validate all registered contracts and return findings.
     *
     * @param contracts all registered contracts
     * @return list of validation findings (empty = all valid)
     */
    fun validate(contracts: Collection<ServiceCommandContract<*, *>>): List<ContractValidationFinding> {
        val findings = mutableListOf<ContractValidationFinding>()
        val registeredIds = contracts.map { it.commandId.value }.toSet()

        for (contract in contracts) {
            val cmdId = contract.commandId.value
            // Note: CommandId format is enforced by CommandId's init block.
            // No need to re-validate here.

            // DeclaredIntentTargets reference registered commands
            for (target in contract.declaredIntentTargets) {
                if (target.value !in registeredIds) {
                    findings.add(
                        ContractValidationFinding(
                            ValidationSeverity.ERROR,
                            cmdId,
                            "declaredIntentTargets references unregistered command: '${target.value}'",
                        ),
                    )
                }
            }

            // Assurance consistency
            val assurance = contract.assuranceRequirements
            if (assurance.requiresDualControl && assurance.minimumAal != null && assurance.minimumAal != AuthAssuranceLevel.AAL3) {
                findings.add(
                    ContractValidationFinding(
                        ValidationSeverity.WARNING,
                        cmdId,
                        "requiresDualControl=true but minimumAal=${assurance.minimumAal} (expected AAL3 for dual control)",
                    ),
                )
            }

            // No capabilities declared
            if (contract.capabilities.isEmpty()) {
                findings.add(
                    ContractValidationFinding(
                        ValidationSeverity.INFO,
                        cmdId,
                        "No capabilities declared. Command will not be exposed to workflow/MCP/policy surfaces.",
                    ),
                )
            }

            // Error contract: discoverable commands should declare possibleErrors
            if (contract.possibleErrors.isEmpty() && contract.capabilities.contains(ContractCapability.DISCOVERABLE)) {
                findings.add(
                    ContractValidationFinding(
                        ValidationSeverity.WARNING,
                        cmdId,
                        "DISCOVERABLE command has no possibleErrors declared. Declare error definitions for contract completeness.",
                    ),
                )
            }

            // Error contract: duplicate error codes within a single contract
            val errorCodes = contract.possibleErrors.map { it.code }
            val duplicateCodes = errorCodes.groupBy { it }.filter { it.value.size > 1 }.keys
            for (code in duplicateCodes) {
                findings.add(
                    ContractValidationFinding(
                        ValidationSeverity.ERROR,
                        cmdId,
                        "Duplicate error code '$code' in possibleErrors",
                    ),
                )
            }
        }

        // Error contract: inconsistent category usage across contracts (same error code, different categories)
        val codeToCategories = mutableMapOf<String, MutableSet<Pair<String, String>>>()
        for (contract in contracts) {
            for (errorDef in contract.possibleErrors) {
                codeToCategories
                    .getOrPut(errorDef.code) { mutableSetOf() }
                    .add(contract.commandId.value to errorDef.category.name)
            }
        }
        for ((code, usages) in codeToCategories) {
            val categories = usages.map { it.second }.toSet()
            if (categories.size > 1) {
                val details = usages.joinToString(", ") { "${it.first}=${it.second}" }
                findings.add(
                    ContractValidationFinding(
                        ValidationSeverity.WARNING,
                        code,
                        "Error code '$code' used with inconsistent categories: $details",
                    ),
                )
            }
        }

        // Cycle detection in declaredIntentTargets graph
        findings.addAll(detectCycles(contracts))

        return findings
    }

    private fun detectCycles(contracts: Collection<ServiceCommandContract<*, *>>): List<ContractValidationFinding> {
        val findings = mutableListOf<ContractValidationFinding>()
        val graph = contracts.associate { it.commandId.value to it.declaredIntentTargets.map { t -> t.value }.toSet() }

        val visited = mutableSetOf<String>()
        val inStack = mutableSetOf<String>()

        fun dfs(
            node: String,
            path: List<String>,
        ) {
            if (node in inStack) {
                val cycleStart = path.indexOf(node)
                val cycle = path.subList(cycleStart, path.size) + node
                findings.add(
                    ContractValidationFinding(
                        ValidationSeverity.ERROR,
                        node,
                        "Cycle detected in declaredIntentTargets: ${cycle.joinToString(" -> ")}",
                    ),
                )
                return
            }
            if (node in visited) {
                return
            }

            visited.add(node)
            inStack.add(node)
            for (neighbor in graph[node] ?: emptySet()) {
                dfs(neighbor, path + node)
            }
            inStack.remove(node)
        }

        for (node in graph.keys) {
            dfs(node, emptyList())
        }

        return findings
    }
}
