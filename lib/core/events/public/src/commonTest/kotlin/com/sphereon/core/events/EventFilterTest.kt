/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events

import com.sphereon.core.api.events.EventCategories
import com.sphereon.core.api.events.EventCategory
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.events.EventType
import com.sphereon.core.api.events.EventTypes
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class EventFilterTest {
    private fun createTestEvent(
        type: EventType = EventTypes.COMMAND_COMPLETED,
        subsystem: EventSubsystem = EventSubsystems.SESSION,
        category: EventCategory = EventCategories.LIFECYCLE,
        origin: String = "test.command",
    ): Event =
        DefaultEvent(
            id = Uuid.random(),
            type = type,
            origin = origin,
            timestamp = Clock.System.now(),
            context = EventContext.EMPTY,
            subsystem = subsystem,
            category = category,
            payload = buildJsonObject { },
            signature = null,
            encryption = null,
        )

    @Test
    fun testAllFilterMatchesEverything() {
        val filter = EventFilter.ALL

        assertTrue(filter.matches(createTestEvent()))
        assertTrue(filter.matches(createTestEvent(type = EventTypes.COMMAND_FAILED)))
        assertTrue(filter.matches(createTestEvent(subsystem = EventSubsystems.CRYPTO)))
        assertTrue(filter.matches(createTestEvent(category = EventCategories.ERROR)))
    }

    @Test
    fun testNoneFilterMatchesNothing() {
        val filter = EventFilter.NONE

        assertFalse(filter.matches(createTestEvent()))
        assertFalse(filter.matches(createTestEvent(type = EventTypes.COMMAND_FAILED)))
        assertFalse(filter.matches(createTestEvent(subsystem = EventSubsystems.CRYPTO)))
    }

    @Test
    fun testFilterByType() {
        val filter = EventFilter.forTypes(listOf(EventTypes.COMMAND_COMPLETED))

        assertTrue(filter.matches(createTestEvent(type = EventTypes.COMMAND_COMPLETED)))
        assertFalse(filter.matches(createTestEvent(type = EventTypes.COMMAND_FAILED)))
        assertFalse(filter.matches(createTestEvent(type = EventTypes.COMMAND_STARTED)))
    }

    @Test
    fun testFilterByMultipleTypes() {
        val filter =
            EventFilter.forTypes(
                listOf(
                    EventTypes.COMMAND_COMPLETED,
                    EventTypes.COMMAND_FAILED,
                ),
            )

        assertTrue(filter.matches(createTestEvent(type = EventTypes.COMMAND_COMPLETED)))
        assertTrue(filter.matches(createTestEvent(type = EventTypes.COMMAND_FAILED)))
        assertFalse(filter.matches(createTestEvent(type = EventTypes.COMMAND_STARTED)))
    }

    @Test
    fun testFilterBySubsystem() {
        val filter = EventFilter.forSubsystems(listOf(EventSubsystems.CRYPTO))

        assertTrue(filter.matches(createTestEvent(subsystem = EventSubsystems.CRYPTO)))
        assertFalse(filter.matches(createTestEvent(subsystem = EventSubsystems.SESSION)))
    }

    @Test
    fun testFilterByCategory() {
        val filter = EventFilter.forCategories(listOf(EventCategories.ERROR))

        assertTrue(filter.matches(createTestEvent(category = EventCategories.ERROR)))
        assertFalse(filter.matches(createTestEvent(category = EventCategories.LIFECYCLE)))
    }

    @Test
    fun testFilterByTypePattern() {
        val filter = EventFilter.forTypePatterns("command.*")

        assertTrue(filter.matches(createTestEvent(type = EventTypes.COMMAND_COMPLETED)))
        assertTrue(filter.matches(createTestEvent(type = EventTypes.COMMAND_FAILED)))
        assertTrue(filter.matches(createTestEvent(type = EventTypes.COMMAND_STARTED)))
        assertFalse(filter.matches(createTestEvent(type = EventTypes.SESSION_CREATED)))
    }

    @Test
    fun testFilterByTypePatternWithDoubleWildcard() {
        val filter = EventFilter.forTypePatterns("**")

        assertTrue(filter.matches(createTestEvent(type = EventTypes.COMMAND_COMPLETED)))
        assertTrue(filter.matches(createTestEvent(type = EventTypes.SESSION_CREATED)))
        assertTrue(filter.matches(createTestEvent(type = EventType.custom("deep.nested.type"))))
    }

    @Test
    fun testDslBuilder() {
        val filter =
            eventFilter {
                type(EventTypes.COMMAND_COMPLETED)
                subsystem(EventSubsystems.SESSION)
                category(EventCategories.LIFECYCLE)
            }

        assertTrue(
            filter.matches(
                createTestEvent(
                    type = EventTypes.COMMAND_COMPLETED,
                    subsystem = EventSubsystems.SESSION,
                    category = EventCategories.LIFECYCLE,
                ),
            ),
        )

        // Fails on type mismatch
        assertFalse(
            filter.matches(
                createTestEvent(
                    type = EventTypes.COMMAND_FAILED,
                    subsystem = EventSubsystems.SESSION,
                    category = EventCategories.LIFECYCLE,
                ),
            ),
        )

        // Fails on subsystem mismatch
        assertFalse(
            filter.matches(
                createTestEvent(
                    type = EventTypes.COMMAND_COMPLETED,
                    subsystem = EventSubsystems.CRYPTO,
                    category = EventCategories.LIFECYCLE,
                ),
            ),
        )

        // Fails on category mismatch
        assertFalse(
            filter.matches(
                createTestEvent(
                    type = EventTypes.COMMAND_COMPLETED,
                    subsystem = EventSubsystems.SESSION,
                    category = EventCategories.ERROR,
                ),
            ),
        )
    }

    @Test
    fun testDslBuilderWithLists() {
        val filter =
            eventFilter {
                types(listOf(EventTypes.COMMAND_COMPLETED, EventTypes.COMMAND_FAILED))
                subsystems(listOf(EventSubsystems.SESSION, EventSubsystems.CRYPTO))
            }

        assertTrue(
            filter.matches(
                createTestEvent(
                    type = EventTypes.COMMAND_COMPLETED,
                    subsystem = EventSubsystems.SESSION,
                ),
            ),
        )
        assertTrue(
            filter.matches(
                createTestEvent(
                    type = EventTypes.COMMAND_FAILED,
                    subsystem = EventSubsystems.CRYPTO,
                ),
            ),
        )
        assertFalse(
            filter.matches(
                createTestEvent(
                    type = EventTypes.COMMAND_STARTED,
                    subsystem = EventSubsystems.SESSION,
                ),
            ),
        )
    }

    @Test
    fun testFilterByOrigin() {
        val filter = EventFilter(origins = setOf("party.create"))

        assertTrue(filter.matches(createTestEvent(origin = "party.create")))
        assertFalse(filter.matches(createTestEvent(origin = "party.update")))
    }

    @Test
    fun testFilterByOriginPattern() {
        val filter = EventFilter(originPatterns = setOf("party.**"))

        assertTrue(filter.matches(createTestEvent(origin = "party.create")))
        assertTrue(filter.matches(createTestEvent(origin = "party.update")))
        assertTrue(filter.matches(createTestEvent(origin = "party.identity.verify")))
        assertFalse(filter.matches(createTestEvent(origin = "resource.create")))
    }

    @Test
    fun testAndCombination() {
        val filter1 = EventFilter.forTypes(listOf(EventTypes.COMMAND_COMPLETED))
        val filter2 = EventFilter.forSubsystems(listOf(EventSubsystems.CRYPTO))
        val combined = filter1.and(filter2)

        assertTrue(
            combined.matches(
                createTestEvent(
                    type = EventTypes.COMMAND_COMPLETED,
                    subsystem = EventSubsystems.CRYPTO,
                ),
            ),
        )
        assertFalse(
            combined.matches(
                createTestEvent(
                    type = EventTypes.COMMAND_COMPLETED,
                    subsystem = EventSubsystems.SESSION,
                ),
            ),
        )
        assertFalse(
            combined.matches(
                createTestEvent(
                    type = EventTypes.COMMAND_FAILED,
                    subsystem = EventSubsystems.CRYPTO,
                ),
            ),
        )
    }

    @Test
    fun testCustomEventType() {
        val customType = EventType.custom("my.domain.operation")
        val filter = EventFilter.forTypes(listOf(customType))

        assertTrue(filter.matches(createTestEvent(type = customType)))
        assertFalse(filter.matches(createTestEvent(type = EventTypes.COMMAND_COMPLETED)))
    }
}
