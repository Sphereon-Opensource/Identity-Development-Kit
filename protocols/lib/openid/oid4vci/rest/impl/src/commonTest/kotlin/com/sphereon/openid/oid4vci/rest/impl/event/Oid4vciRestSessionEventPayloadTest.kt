package com.sphereon.openid.oid4vci.rest.impl.event

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class Oid4vciRestSessionEventPayloadTest {
    @Test
    fun `adds canonical protocol identity and state without replacing existing payload`() {
        val payload =
            buildJsonObject {
                put("correlationId", "corr-1")
                putSessionEventIdentity(
                    protocolSessionId = "issuance-1",
                    instanceId = "issuer-a",
                    oldState = "CREATING",
                    newState = "CREDENTIAL_OFFER_CREATED",
                    templateId = "template-a",
                )
            }

        assertEquals("corr-1", payload["correlationId"]?.toString()?.trim('"'))
        assertEquals("issuance-1", payload["protocolSessionId"]?.toString()?.trim('"'))
        assertEquals("issuer-a", payload["instanceId"]?.toString()?.trim('"'))
        assertEquals("CREATING", payload["oldState"]?.toString()?.trim('"'))
        assertEquals("CREDENTIAL_OFFER_CREATED", payload["newState"]?.toString()?.trim('"'))
        assertEquals("template-a", payload["templateId"]?.toString()?.trim('"'))
        assertFalse(payload.containsKey("statusAfter"))
    }

    @Test
    fun `rejects blank mandatory identity values`() {
        assertFailsWith<IllegalArgumentException> {
            buildJsonObject {
                putSessionEventIdentity(
                    protocolSessionId = "",
                    instanceId = "issuer-instance-event-validation",
                )
            }
        }
        assertFailsWith<IllegalArgumentException> {
            buildJsonObject {
                putSessionEventIdentity(
                    protocolSessionId = "issuance-session-event-validation",
                    instanceId = "",
                )
            }
        }
    }
}
