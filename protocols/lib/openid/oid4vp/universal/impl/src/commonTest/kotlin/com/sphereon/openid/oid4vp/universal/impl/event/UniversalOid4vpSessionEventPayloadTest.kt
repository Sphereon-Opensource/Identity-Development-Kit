package com.sphereon.openid.oid4vp.universal.impl.event

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class UniversalOid4vpSessionEventPayloadTest {
    @Test
    fun `adds canonical protocol identity and state without replacing existing payload`() {
        val payload =
            buildJsonObject {
                put("correlationId", "corr-1")
                put("status", "AUTHORIZATION_RESPONSE_VERIFIED")
                putSessionEventIdentity(
                    protocolSessionId = "vp-session-1",
                    instanceId = "verifier-a",
                    oldState = "AUTHORIZATION_RESPONSE_RECEIVED",
                    newState = "AUTHORIZATION_RESPONSE_VERIFIED",
                    templateId = "template-v",
                )
            }

        assertEquals("corr-1", payload["correlationId"]?.toString()?.trim('"'))
        assertEquals("AUTHORIZATION_RESPONSE_VERIFIED", payload["status"]?.toString()?.trim('"'))
        assertEquals("vp-session-1", payload["protocolSessionId"]?.toString()?.trim('"'))
        assertEquals("verifier-a", payload["instanceId"]?.toString()?.trim('"'))
        assertEquals("AUTHORIZATION_RESPONSE_RECEIVED", payload["oldState"]?.toString()?.trim('"'))
        assertEquals("AUTHORIZATION_RESPONSE_VERIFIED", payload["newState"]?.toString()?.trim('"'))
        assertEquals("template-v", payload["templateId"]?.toString()?.trim('"'))
        assertFalse(payload.containsKey("statusAfter"))
    }

    @Test
    fun `rejects blank or oversized classified identity`() {
        assertFailsWith<IllegalArgumentException> {
            buildJsonObject { putSessionEventIdentity(" ", "verifier-a") }
        }
        assertFailsWith<IllegalArgumentException> {
            buildJsonObject { putSessionEventIdentity("vp-session-1", "v".repeat(191)) }
        }
    }
}
