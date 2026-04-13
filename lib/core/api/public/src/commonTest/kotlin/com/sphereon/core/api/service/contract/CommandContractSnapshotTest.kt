package com.sphereon.core.api.service.contract

import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.session.CommandId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandContractSnapshotTest {
    private val json =
        Json {
            prettyPrint = false
            encodeDefaults = true
        }

    @Test
    fun serializationRoundTrip() {
        val snapshot =
            CommandContractSnapshot(
                commandId = CommandId("kms.keys.generate"),
                actionType = ActionType.CREATE,
                operationType = OperationType.GENERATE,
                summary = "Generate a key",
                description = "Generates a key pair on the specified provider",
                resourceTarget =
                    resourceTarget("kms.key") {
                        parent("kms.provider")
                        identifier("provider_id", required = true)
                        constraint("algorithm")
                    },
                assuranceRequirements =
                    assurance {
                        aal2()
                        requireAmr("mfa")
                        maxAuthAge(3600)
                    },
                executionTraits = executionTraits { mutating() },
                inputSchemaOverlay =
                    schemaOverlay {
                        "providerId" {
                            label("Provider")
                            identifier("provider_id", resource = "kms.provider")
                        }
                    },
                capabilities = setOf(ContractCapability.DISCOVERABLE, ContractCapability.POLICY_READY),
                declaredIntentTargets = setOf(CommandId("kms.keys.get")),
            )

        val serialized = json.encodeToString(snapshot)
        val deserialized = json.decodeFromString<CommandContractSnapshot>(serialized)

        assertEquals(snapshot.commandId, deserialized.commandId)
        assertEquals(snapshot.actionType, deserialized.actionType)
        assertEquals(snapshot.operationType, deserialized.operationType)
        assertEquals(snapshot.summary, deserialized.summary)
        assertEquals(snapshot.description, deserialized.description)
        assertEquals(snapshot.resourceTarget.resourceType, deserialized.resourceTarget.resourceType)
        assertEquals(snapshot.resourceTarget.parentResources, deserialized.resourceTarget.parentResources)
        assertEquals(snapshot.assuranceRequirements.minimumAal, deserialized.assuranceRequirements.minimumAal)
        assertEquals(snapshot.assuranceRequirements.requiredAmr, deserialized.assuranceRequirements.requiredAmr)
        assertEquals(snapshot.executionTraits.isIdempotent, deserialized.executionTraits.isIdempotent)
        assertEquals(snapshot.capabilities, deserialized.capabilities)
        assertEquals(snapshot.declaredIntentTargets, deserialized.declaredIntentTargets)
        assertTrue(deserialized.inputSchemaOverlay.fields.containsKey("providerId"))
    }

    @Test
    fun defaultValuesProduceMinimalSnapshot() {
        val snapshot =
            CommandContractSnapshot(
                commandId = CommandId("health.system.check"),
                actionType = ActionType.READ,
                operationType = OperationType.READ,
            )
        val serialized = json.encodeToString(snapshot)
        val deserialized = json.decodeFromString<CommandContractSnapshot>(serialized)
        assertEquals("health.system.check", deserialized.commandId.value)
        assertEquals(ActionType.READ, deserialized.actionType)
    }
}
