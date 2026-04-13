package com.sphereon.core.api.service.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandPlanTest {
    @Test
    fun leafPlanHasNoIntents() {
        val plan = CommandPlan.leaf()
        assertTrue(plan.intents.isEmpty())
        assertEquals(PlanningCompleteness.COMPLETE, plan.completeness)
    }

    @Test
    fun planWithIntentsPreservesOrder() {
        val plan =
            CommandPlan(
                intents =
                    listOf(
                        CommandIntent("oid4vci.holder.nonce"),
                        CommandIntent("oid4vci.holder.createproof"),
                        CommandIntent("oid4vci.holder.credential"),
                    ),
            )
        assertEquals(3, plan.intents.size)
        assertEquals("oid4vci.holder.nonce", plan.intents[0].commandId)
        assertEquals("oid4vci.holder.credential", plan.intents[2].commandId)
    }

    @Test
    fun optionalIntentsAreMarked() {
        val intent =
            CommandIntent(
                commandId = "oid4vci.holder.deferred",
                optional = true,
                reason = "Only needed for deferred issuance",
            )
        assertTrue(intent.optional)
        assertEquals("Only needed for deferred issuance", intent.reason)
    }

    @Test
    fun intentWithResourceHints() {
        val intent =
            CommandIntent(
                commandId = "oid4vci.holder.createproof",
                resourceHints =
                    resourceInstances {
                        resource("kms.key") {
                            attr("signing_key_id", "key-123")
                        }
                    },
            )
        assertEquals(1, intent.resourceHints.size)
        assertEquals("kms.key", intent.resourceHints[0].resourceType)
    }

    @Test
    fun partialPlanCompleteness() {
        val plan =
            CommandPlan(
                intents = listOf(CommandIntent("cmd.a.known")),
                completeness = PlanningCompleteness.PARTIAL,
            )
        assertEquals(PlanningCompleteness.PARTIAL, plan.completeness)
    }
}
