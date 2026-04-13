/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.command

import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.contract.AttributeKind
import com.sphereon.core.api.service.contract.CommandContractSnapshot
import com.sphereon.core.api.service.contract.ContractCapability
import com.sphereon.core.api.service.contract.ContractValidator
import com.sphereon.core.api.service.contract.OperationType
import com.sphereon.core.api.service.contract.PlanningCompleteness
import com.sphereon.core.api.service.contract.PlanningContext
import com.sphereon.core.api.service.contract.ServiceCommandContract
import com.sphereon.core.api.service.contract.ValidationSeverity
import com.sphereon.core.api.session.CommandId
import com.sphereon.trust.core.model.TrustContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrustCommandContractTest {
    private val validateTrust = ValidateTrustContract()
    private val getTrustAnchors = GetTrustAnchorsContract()
    private val refreshTrustAnchors = RefreshTrustAnchorsContract()
    private val checkRevocation = CheckRevocationContract()
    private val discoverEntityInfo = DiscoverEntityInfoContract()

    private val allContracts = listOf(validateTrust, getTrustAnchors, refreshTrustAnchors, checkRevocation, discoverEntityInfo)
    private val json =
        Json {
            prettyPrint = false
            encodeDefaults = true
        }

    // ========== Identity ==========

    @Test
    fun validateTrustContractIdentity() {
        assertEquals("trust.validation.validate", validateTrust.commandId.value)
        assertEquals(ActionType.EXECUTE, validateTrust.actionType)
        assertEquals(OperationType.VALIDATE, validateTrust.operationType)
    }

    @Test
    fun getTrustAnchorsContractIdentity() {
        assertEquals("trust.anchors.list", getTrustAnchors.commandId.value)
        assertEquals(ActionType.LIST, getTrustAnchors.actionType)
        assertEquals(OperationType.LIST, getTrustAnchors.operationType)
    }

    @Test
    fun refreshTrustAnchorsContractIdentity() {
        assertEquals("trust.anchors.refresh", refreshTrustAnchors.commandId.value)
        assertEquals(ActionType.EXECUTE, refreshTrustAnchors.actionType)
        assertEquals(OperationType.EXECUTE, refreshTrustAnchors.operationType)
    }

    @Test
    fun checkRevocationContractIdentity() {
        assertEquals("trust.revocation.check", checkRevocation.commandId.value)
        assertEquals(ActionType.EXECUTE, checkRevocation.actionType)
        assertEquals(OperationType.VERIFY, checkRevocation.operationType)
    }

    @Test
    fun discoverEntityInfoContractIdentity() {
        assertEquals("trust.discovery.entityinfo", discoverEntityInfo.commandId.value)
        assertEquals(ActionType.EXECUTE, discoverEntityInfo.actionType)
        assertEquals(OperationType.RESOLVE, discoverEntityInfo.operationType)
    }

    // ========== Resource Targets ==========

    @Test
    fun validateTrustResourceTarget() {
        val target = validateTrust.resourceTarget
        assertEquals("trust.validation", target.resourceType)
        assertTrue(target.attributeSchema.any { it.name == "context_type" && it.required && it.kind == AttributeKind.IDENTIFIER })
        assertTrue(target.attributeSchema.any { it.name == "framework" && it.kind == AttributeKind.IDENTIFIER })
        assertTrue(target.attributeSchema.any { it.name == "check_revocation" && it.kind == AttributeKind.CONSTRAINT })
    }

    @Test
    fun getTrustAnchorsResourceTarget() {
        val target = getTrustAnchors.resourceTarget
        assertEquals("trust.anchor.collection", target.resourceType)
        assertTrue(target.attributeSchema.any { it.name == "anchor_type" })
        assertTrue(target.attributeSchema.any { it.name == "context_type" })
    }

    @Test
    fun checkRevocationResourceTarget() {
        val target = checkRevocation.resourceTarget
        assertEquals("trust.revocation", target.resourceType)
        assertTrue(target.attributeSchema.all { it.kind == AttributeKind.CONSTRAINT })
        assertTrue(target.attributeSchema.any { it.name == "check_ocsp" })
        assertTrue(target.attributeSchema.any { it.name == "timeout_ms" })
    }

    @Test
    fun discoverEntityInfoResourceTarget() {
        val target = discoverEntityInfo.resourceTarget
        assertEquals("trust.entity", target.resourceType)
        assertTrue(target.attributeSchema.any { it.name == "context_type" && it.required })
        assertTrue(target.attributeSchema.any { it.name == "entity_identifier" && it.required })
    }

    // ========== Schema Overlays ==========

    @Test
    fun validateTrustOverlayFieldsMatchInputProperties() {
        val overlay = validateTrust.inputSchemaOverlay
        assertTrue(overlay.fields.containsKey("contextType"))
        assertTrue(overlay.fields.containsKey("framework"))
        assertTrue(overlay.fields.containsKey("identifierJson"))
        assertTrue(overlay.fields.containsKey("checkRevocation"))
    }

    @Test
    fun checkRevocationOverlayMarksCertificatesAsRestricted() {
        val overlay = checkRevocation.inputSchemaOverlay
        val certField = overlay.fields["certificateDer"]
        assertNotNull(certField)
        assertEquals(
            com.sphereon.core.api.service.contract.SensitivityClassification.RESTRICTED,
            certField.sensitivity,
        )
    }

    // ========== Compound Command Planning ==========

    @Test
    fun validateTrustDeclaredIntentTargets() {
        val targets = validateTrust.declaredIntentTargets.map { it.value }.toSet()
        assertTrue("trust.etsi.validate" in targets)
        assertTrue("trust.x509.validate" in targets)
        assertTrue("trust.did.validate" in targets)
        assertTrue("trust.oidfed.validate" in targets)
    }

    @Test
    fun validateTrustPlanRoutesToEtsiForEtsiContext() =
        runTest {
            val args =
                ValidateTrustArgs(
                    contextType = TrustContext.TYPE_ETSI_TSL,
                    identifierJson = "{}",
                )
            val plan = validateTrust.plan(args, PlanningContext())
            assertEquals(1, plan.intents.size)
            assertEquals("trust.etsi.validate", plan.intents[0].commandId)
            assertEquals(PlanningCompleteness.PARTIAL, plan.completeness)
        }

    @Test
    fun validateTrustPlanRoutesToX509ForX509Context() =
        runTest {
            val args =
                ValidateTrustArgs(
                    contextType = TrustContext.TYPE_X509,
                    identifierJson = "{}",
                )
            val plan = validateTrust.plan(args, PlanningContext())
            assertEquals("trust.x509.validate", plan.intents[0].commandId)
        }

    @Test
    fun validateTrustPlanRoutesToX509ForCaBundleContext() =
        runTest {
            val args =
                ValidateTrustArgs(
                    contextType = TrustContext.TYPE_CA_BUNDLE,
                    identifierJson = "{}",
                )
            val plan = validateTrust.plan(args, PlanningContext())
            assertEquals("trust.x509.validate", plan.intents[0].commandId)
        }

    @Test
    fun validateTrustPlanRoutesToDidForDidContext() =
        runTest {
            val args =
                ValidateTrustArgs(
                    contextType = TrustContext.TYPE_DID,
                    identifierJson = "{}",
                )
            val plan = validateTrust.plan(args, PlanningContext())
            assertEquals("trust.did.validate", plan.intents[0].commandId)
        }

    @Test
    fun validateTrustPlanRoutesToOidfedForFederationContext() =
        runTest {
            val args =
                ValidateTrustArgs(
                    contextType = TrustContext.TYPE_OPENID_FEDERATION,
                    identifierJson = "{}",
                )
            val plan = validateTrust.plan(args, PlanningContext())
            assertEquals("trust.oidfed.validate", plan.intents[0].commandId)
        }

    @Test
    fun validateTrustPlanReturnsLeafForUnknownContext() =
        runTest {
            val args =
                ValidateTrustArgs(
                    contextType = "custom_unknown",
                    identifierJson = "{}",
                )
            val plan = validateTrust.plan(args, PlanningContext())
            assertTrue(plan.intents.isEmpty())
        }

    // ========== Execution Traits ==========

    @Test
    fun readOnlyCommandsAreIdempotent() {
        assertEquals(true, getTrustAnchors.executionTraits.isIdempotent)
        assertEquals(true, checkRevocation.executionTraits.isIdempotent)
        assertEquals(true, discoverEntityInfo.executionTraits.isIdempotent)
    }

    @Test
    fun refreshIsNotIdempotent() {
        assertEquals(false, refreshTrustAnchors.executionTraits.isIdempotent)
    }

    // ========== Capabilities ==========

    @Test
    fun allContractsAreDiscoverableAndPolicyReady() {
        for (contract in allContracts) {
            assertTrue(
                ContractCapability.DISCOVERABLE in contract.capabilities,
                "${contract.commandId.value} should be DISCOVERABLE",
            )
            assertTrue(
                ContractCapability.POLICY_READY in contract.capabilities,
                "${contract.commandId.value} should be POLICY_READY",
            )
        }
    }

    // ========== Snapshot Serialization ==========

    @Test
    fun allSnapshotsSerializeAndDeserialize() {
        for (contract in allContracts) {
            val snapshot = contract.toSnapshot()
            val serialized = json.encodeToString(snapshot)
            val deserialized = json.decodeFromString<CommandContractSnapshot>(serialized)

            assertEquals(contract.commandId, deserialized.commandId)
            assertEquals(contract.actionType, deserialized.actionType)
            assertEquals(contract.operationType, deserialized.operationType)
            assertEquals(contract.summary, deserialized.summary)
            assertEquals(contract.resourceTarget.resourceType, deserialized.resourceTarget.resourceType)
        }
    }

    // ========== Contract Validation ==========

    @Test
    fun coreOnlyContractsProduceMissingTargetErrorsForExternalModules() {
        // ValidateTrust declares 4 intent targets in external modules (etsi, x509, did, oidfed)
        // which are not in allContracts — this correctly produces errors for missing targets
        val findings = ContractValidator.validate(allContracts)
        val missingTargetErrors =
            findings.filter {
                it.severity == ValidationSeverity.ERROR && it.message.contains("unregistered")
            }
        assertEquals(4, missingTargetErrors.size)

        // No other errors besides missing targets
        val otherErrors =
            findings.filter {
                it.severity == ValidationSeverity.ERROR && !it.message.contains("unregistered")
            }
        assertTrue(otherErrors.isEmpty(), "Unexpected errors: $otherErrors")
    }

    @Test
    fun allContractsPassValidationWhenIntentTargetsAreRegistered() {
        val stubs =
            listOf("trust.etsi.validate", "trust.x509.validate", "trust.did.validate", "trust.oidfed.validate")
                .map { stubContract(it) }

        val allWithStubs = allContracts + stubs
        val findings = ContractValidator.validate(allWithStubs)
        val errors = findings.filter { it.severity == ValidationSeverity.ERROR }
        assertTrue(errors.isEmpty(), "Expected no errors with all targets registered but got: $errors")
    }

    private fun stubContract(id: String): ServiceCommandContract<Unit, Unit> =
        object : ServiceCommandContract<Unit, Unit> {
            override val commandId = CommandId(id)
            override val inputTypeToken: TypeToken<Unit> get() = TypeToken.UNIT
            override val outputTypeToken: TypeToken<Unit> get() = TypeToken.UNIT
        }
}
