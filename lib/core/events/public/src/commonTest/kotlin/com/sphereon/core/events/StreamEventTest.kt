@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.sphereon.core.events

import com.sphereon.core.api.events.EventCategories
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlin.uuid.Uuid

class StreamEventTest {
    @Test
    fun streamEventRoundTripsThroughTheExistingIdkEventEnvelope() {
        val event = event()
        val encoded = Json.encodeToString(DefaultStreamEvent.serializer(), event)
        val decoded = Json.decodeFromString(DefaultStreamEvent.serializer(), encoded)

        assertEquals(event, decoded)
        assertEquals("tenant-1", decoded.context.tenantId)
        assertEquals("actor-1", decoded.authority.actorId)
        assertEquals("model-1", decoded.stream.modelId)
        assertEquals("0.1.0", decoded.version)
    }

    @Test
    fun streamMetadataRejectsBlankInvalidAndCallerDriftedValues() {
        assertFailsWith<IllegalArgumentException> { EventDigest("", "digest") }
        assertFailsWith<IllegalArgumentException> { EventDigest("sha-256", "") }
        assertFailsWith<IllegalArgumentException> { stream().copy(sequence = 0) }
        assertFailsWith<IllegalArgumentException> { stream().copy(expectedPriorVersion = -1) }
        assertFailsWith<IllegalArgumentException> { stream().copy(sequence = 1, expectedPriorVersion = 1) }
        assertFailsWith<IllegalArgumentException> { stream().copy(commandId = "invalid.command") }
        assertFailsWith<IllegalArgumentException> { stream().copy(causationId = "") }
        assertFailsWith<IllegalArgumentException> { EventAuthority("", "USER") }
        assertFailsWith<IllegalArgumentException> { EventAuthority("actor", "") }
        assertFailsWith<IllegalArgumentException> { EventAuthority("actor", "USER", "") }
        assertFailsWith<IllegalArgumentException> { EventEffectiveInterval(NOW, NOW) }
        assertFailsWith<IllegalArgumentException> { EventRevisionRef("", "1") }
        assertFailsWith<IllegalArgumentException> { EventRevisionRef("id", "") }
        assertFailsWith<IllegalArgumentException> { EventSourceProvenance(protectedRefs = listOf("")) }
        assertFailsWith<IllegalArgumentException> { event().copy(origin = "organization.other.command") }
        assertFailsWith<IllegalArgumentException> {
            event().copy(authority = EventAuthority("different-actor", "USER"))
        }
        assertFailsWith<IllegalArgumentException> {
            event().copy(context = event().context.copy(tenantId = null))
        }
    }

    private fun event() = DefaultStreamEvent(
        id = Uuid.parse("018f0a65-7f0c-7a23-8c11-3f63582fd300"),
        type = EventType("organization.model.initialized"),
        version = "0.1.0",
        origin = "organization.model.initialize",
        timestamp = NOW,
        context = EventContext(
            sessionId = "session-1",
            tenantId = "tenant-1",
            principalId = "actor-1",
            correlationId = "correlation-1",
        ),
        subsystem = EventSubsystem("enterprise.organization"),
        category = EventCategories.OPERATION,
        payload = buildJsonObject { put("type", "model-initialized") },
        stream = stream(),
        authority = EventAuthority("actor-1", "USER"),
        effectiveInterval = EventEffectiveInterval(NOW),
        provenance = EventSourceProvenance(
            semanticRefs = listOf(EventRevisionRef("model-1", "1")),
            protectedRefs = listOf("protected-store:object-1"),
        ),
    )

    private fun stream() = EventStreamContext(
        modelId = "model-1",
        streamId = "model-1",
        sequence = 1,
        expectedPriorVersion = 0,
        commandId = "organization.model.initialize",
        idempotencyKey = "initialize-1",
        commandDigest = EventDigest("sha-256", "digest"),
    )

    private companion object {
        val NOW = Instant.parse("2026-08-10T12:00:00Z")
    }
}
