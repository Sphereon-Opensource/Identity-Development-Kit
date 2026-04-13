package com.sphereon.core.api.service.contract

import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.session.CommandId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContractValidationTest {
    private fun stubContract(
        commandId: String,
        declaredTargets: Set<CommandId> = emptySet(),
        assurance: AssuranceRequirements = AssuranceRequirements.UNSPECIFIED,
        capabilities: Set<ContractCapability> = setOf(ContractCapability.DISCOVERABLE),
    ): ServiceCommandContract<Unit, Unit> =
        object : ServiceCommandContract<Unit, Unit> {
            override val commandId = CommandId(commandId)
            override val actionType = ActionType.EXECUTE
            override val inputTypeToken: TypeToken<Unit> get() = TypeToken.UNIT
            override val outputTypeToken: TypeToken<Unit> get() = TypeToken.UNIT
            override val declaredIntentTargets = declaredTargets
            override val assuranceRequirements = assurance
            override val capabilities = capabilities
        }

    @Test
    fun validContractsProduceNoErrors() {
        val contracts =
            listOf(
                stubContract("kms.keys.generate"),
                stubContract("kms.keys.get"),
            )
        val findings = ContractValidator.validate(contracts)
        val errors = findings.filter { it.severity == ValidationSeverity.ERROR }
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun missingIntentTargetProducesError() {
        val contracts =
            listOf(
                stubContract("kms.keys.generate", declaredTargets = setOf(CommandId("kms.keys.nonexistent"))),
            )
        val findings = ContractValidator.validate(contracts)
        val errors = findings.filter { it.severity == ValidationSeverity.ERROR }
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("unregistered command"))
    }

    @Test
    fun validIntentTargetProducesNoError() {
        val contracts =
            listOf(
                stubContract("kms.keys.generate", declaredTargets = setOf(CommandId("kms.keys.get"))),
                stubContract("kms.keys.get"),
            )
        val findings = ContractValidator.validate(contracts)
        val errors = findings.filter { it.severity == ValidationSeverity.ERROR }
        assertTrue(errors.isEmpty())
    }

    @Test
    fun cycleInIntentTargetsProducesError() {
        val contracts =
            listOf(
                stubContract("cmd.a.first", declaredTargets = setOf(CommandId("cmd.b.second"))),
                stubContract("cmd.b.second", declaredTargets = setOf(CommandId("cmd.a.first"))),
            )
        val findings = ContractValidator.validate(contracts)
        val cycleErrors = findings.filter { it.severity == ValidationSeverity.ERROR && it.message.contains("Cycle") }
        assertTrue(cycleErrors.isNotEmpty(), "Expected cycle detection error")
    }

    @Test
    fun selfReferenceIsCycle() {
        val contracts =
            listOf(
                stubContract("cmd.a.self", declaredTargets = setOf(CommandId("cmd.a.self"))),
            )
        val findings = ContractValidator.validate(contracts)
        val cycleErrors = findings.filter { it.severity == ValidationSeverity.ERROR && it.message.contains("Cycle") }
        assertTrue(cycleErrors.isNotEmpty(), "Expected cycle detection for self-reference")
    }

    @Test
    fun dualControlWithoutAal3ProducesWarning() {
        val contracts =
            listOf(
                stubContract(
                    "kms.keys.delete",
                    assurance =
                        AssuranceRequirements(
                            minimumAal = AuthAssuranceLevel.AAL2,
                            requiresDualControl = true,
                        ),
                ),
            )
        val findings = ContractValidator.validate(contracts)
        val dualControlWarnings = findings.filter { it.severity == ValidationSeverity.WARNING && it.message.contains("requiresDualControl") }
        assertEquals(1, dualControlWarnings.size)
    }

    @Test
    fun dualControlWithAal3ProducesNoWarning() {
        val contracts =
            listOf(
                stubContract(
                    "kms.keys.delete",
                    assurance =
                        AssuranceRequirements(
                            minimumAal = AuthAssuranceLevel.AAL3,
                            requiresDualControl = true,
                        ),
                ),
            )
        val findings = ContractValidator.validate(contracts)
        val dualControlWarnings = findings.filter { it.severity == ValidationSeverity.WARNING && it.message.contains("requiresDualControl") }
        assertTrue(dualControlWarnings.isEmpty())
    }

    @Test
    fun noCapabilitiesProducesInfo() {
        val contracts =
            listOf(
                stubContract("kms.keys.get", capabilities = emptySet()),
            )
        val findings = ContractValidator.validate(contracts)
        val infos = findings.filter { it.severity == ValidationSeverity.INFO }
        assertEquals(1, infos.size)
        assertTrue(infos[0].message.contains("No capabilities"))
    }
}
