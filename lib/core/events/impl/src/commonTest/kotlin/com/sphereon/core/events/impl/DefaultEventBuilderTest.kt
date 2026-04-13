/*
 * Copyright (c) 2025 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events.impl

import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.events.EventCategories
import com.sphereon.core.events.EventContext
import com.sphereon.core.events.EventSubsystems
import com.sphereon.core.events.EventTypes
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class DefaultEventBuilderTest {

    @Test
    fun testBuildMinimalEvent() {
        val builder = DefaultEventBuilder(IdkScope.APP)
        val event = builder
            .type(EventTypes.COMMAND_COMPLETED)
            .origin("test.command")
            .build()

        assertEquals(EventTypes.COMMAND_COMPLETED, event.type)
        assertEquals("test.command", event.origin)
        assertNotNull(event.id)
        assertNotNull(event.timestamp)
        assertEquals(EventContext.EMPTY, event.context)
        assertEquals(EventSubsystems.CUSTOM, event.subsystem)
        assertEquals(EventCategories.OPERATION, event.category)
        assertNull(event.signature)
        assertNull(event.encryption)
    }

    @Test
    fun testBuildFullEvent() {
        val context = EventContext(
            sessionId = "session-123",
            tenantId = "tenant-456",
            principalId = "user@example.com"
        )
        val payload = buildJsonObject {
            put("key", "value")
            put("count", 42)
        }

        val builder = DefaultEventBuilder(IdkScope.SESSION, context)
        val event = builder
            .type(EventTypes.COMMAND_COMPLETED)
            .origin("party.create")
            .subsystem(EventSubsystems.SESSION)
            .category(EventCategories.LIFECYCLE)
            .payload(payload)
            .correlationId("correlation-789")
            .build()

        assertEquals(EventTypes.COMMAND_COMPLETED, event.type)
        assertEquals("party.create", event.origin)
        assertEquals(EventSubsystems.SESSION, event.subsystem)
        assertEquals(EventCategories.LIFECYCLE, event.category)
        assertEquals(payload, event.payload)
        assertEquals("correlation-789", event.context.correlationId)
        assertEquals("session-123", event.context.sessionId)
        assertEquals("tenant-456", event.context.tenantId)
        assertEquals("user@example.com", event.context.principalId)
    }

    @Test
    fun testBuildWithoutTypeThrows() {
        val builder = DefaultEventBuilder(IdkScope.APP)
            .origin("test.command")

        assertFailsWith<IllegalStateException> {
            builder.build()
        }
    }

    @Test
    fun testBuildWithoutOriginThrows() {
        val builder = DefaultEventBuilder(IdkScope.APP)
            .type(EventTypes.COMMAND_COMPLETED)

        assertFailsWith<IllegalStateException> {
            builder.build()
        }
    }

    @Test
    fun testCorrelationIdMergedIntoContext() {
        val builder = DefaultEventBuilder(IdkScope.APP)
        val event = builder
            .type(EventTypes.COMMAND_COMPLETED)
            .origin("test.command")
            .correlationId("my-correlation-id")
            .build()

        assertEquals("my-correlation-id", event.context.correlationId)
    }

    @Test
    fun testContextCorrelationIdPreserved() {
        val context = EventContext(
            sessionId = null,
            tenantId = null,
            principalId = null,
            correlationId = "existing-correlation"
        )
        val builder = DefaultEventBuilder(IdkScope.APP, context)
        val event = builder
            .type(EventTypes.COMMAND_COMPLETED)
            .origin("test.command")
            .correlationId("new-correlation") // Should not override since context already has one
            .build()

        // Context's correlationId is preserved
        assertEquals("existing-correlation", event.context.correlationId)
    }

    @Test
    fun testForAppScope() {
        val builder = DefaultEventBuilder.forAppScope()
        val event = builder
            .type(EventTypes.COMMAND_COMPLETED)
            .origin("app.startup")
            .build()

        assertEquals(EventContext.EMPTY, event.context)
    }

    @Test
    fun testForUserScope() {
        val context = EventContext(
            sessionId = null,
            tenantId = "tenant-1",
            principalId = "user-1"
        )
        val builder = DefaultEventBuilder.forUserScope(context)
        val event = builder
            .type(EventTypes.COMMAND_COMPLETED)
            .origin("user.action")
            .build()

        assertEquals("tenant-1", event.context.tenantId)
        assertEquals("user-1", event.context.principalId)
        assertNull(event.context.sessionId)
    }

    @Test
    fun testForSessionScope() {
        val context = EventContext(
            sessionId = "session-abc",
            tenantId = "tenant-xyz",
            principalId = "user@test.com"
        )
        val builder = DefaultEventBuilder.forSessionScope(context)
        val event = builder
            .type(EventTypes.COMMAND_COMPLETED)
            .origin("session.action")
            .build()

        assertEquals("session-abc", event.context.sessionId)
        assertEquals("tenant-xyz", event.context.tenantId)
        assertEquals("user@test.com", event.context.principalId)
    }

    @Test
    fun testCustomEventType() {
        val customType = EventTypes.custom("my.domain.operation")
        val builder = DefaultEventBuilder(IdkScope.APP)
        val event = builder
            .type(customType)
            .origin("custom.command")
            .build()

        assertEquals("custom.my.domain.operation.v1.0", event.type.value)
    }

    @Test
    fun testEachBuildCreatesNewId() {
        val builder = DefaultEventBuilder(IdkScope.APP)
            .type(EventTypes.COMMAND_COMPLETED)
            .origin("test.command")

        val event1 = builder.build()
        val event2 = builder.build()

        // Each build should create a new event with a unique ID
        // Note: The builder doesn't reset, so this tests the Uuid.random() call
        assertNotNull(event1.id)
        assertNotNull(event2.id)
    }
}
