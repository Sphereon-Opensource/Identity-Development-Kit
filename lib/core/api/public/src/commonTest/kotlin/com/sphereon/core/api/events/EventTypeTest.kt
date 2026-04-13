/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.core.api.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventTypeBasicTest {
    @Test
    fun eventTypeHasValue() {
        val type = EventType("command.started")
        assertEquals("command.started", type.value)
    }

    @Test
    fun eventTypeToStringReturnsValue() {
        val type = EventType("command.started")
        assertEquals("command.started", type.toString())
    }

    @Test
    fun eventTypeEqualsWorks() {
        val type1 = EventType("command.started")
        val type2 = EventType("command.started")
        assertEquals(type1, type2)
    }

    @Test
    fun eventTypeNotEqualsWorks() {
        val type1 = EventType("command.started")
        val type2 = EventType("command.completed")
        assertFalse(type1 == type2)
    }
}

class EventTypeCustomTest {
    @Test
    fun customCreatesCorrectFormat() {
        val type = EventType.custom("my.event")
        assertEquals("custom.my.event.v1.0", type.value)
    }

    @Test
    fun customWithVersionCreatesCorrectFormat() {
        val type = EventType.custom("my.event", "2.5")
        assertEquals("custom.my.event.v2.5", type.value)
    }

    @Test
    fun customFromCompanionWorks() {
        val type = EventTypes.custom("test.event")
        assertEquals("custom.test.event.v1.0", type.value)
    }

    @Test
    fun customFromCompanionWithVersionWorks() {
        val type = EventTypes.custom("test.event", "3.0")
        assertEquals("custom.test.event.v3.0", type.value)
    }
}

class EventTypeMatchesExactTest {
    @Test
    fun matchesExactValueReturnsTrue() {
        val type = EventType("command.started")
        assertTrue(type.matches("command.started"))
    }

    @Test
    fun matchesDifferentValueReturnsFalse() {
        val type = EventType("command.started")
        assertFalse(type.matches("command.completed"))
    }

    @Test
    fun matchesDoubleStarReturnsTrueForAnything() {
        val type = EventType("some.deeply.nested.event.type")
        assertTrue(type.matches("**"))
    }
}

class EventTypeMatchesSingleWildcardTest {
    @Test
    fun matchesSingleWildcardInMiddle() {
        val type = EventType("command.started")
        assertTrue(type.matches("command.*"))
    }

    @Test
    fun matchesSingleWildcardAtStart() {
        val type = EventType("command.started")
        assertTrue(type.matches("*.started"))
    }

    @Test
    fun matchesSingleWildcardDoesNotCrossDots() {
        val type = EventType("command.lifecycle.started")
        assertFalse(type.matches("command.*"))
    }

    @Test
    fun matchesSingleWildcardMatchesSingleSegment() {
        val type = EventType("command.lifecycle.started")
        assertTrue(type.matches("command.*.started"))
    }
}

class EventTypeMatchesDoubleWildcardTest {
    @Test
    fun matchesStandaloneDoubleWildcardMatchesAnything() {
        // The standalone "**" pattern matches everything
        val type = EventType("command.lifecycle.started")
        assertTrue(type.matches("**"))
    }

    @Test
    fun matchesStandaloneDoubleWildcardMatchesSimpleValue() {
        val type = EventType("simple")
        assertTrue(type.matches("**"))
    }

    @Test
    fun matchesStandaloneDoubleWildcardMatchesDeeplyNested() {
        val type = EventType("a.b.c.d.e.f")
        assertTrue(type.matches("**"))
    }
}

class EventTypeMatchesMixedWildcardsTest {
    @Test
    fun matchesMixedWildcards() {
        val type = EventType("session.user.login.started")
        assertTrue(type.matches("session.*.login.*"))
    }

    @Test
    fun matchesSingleWildcardInMultiplePositions() {
        val type = EventType("a.b.c.d")
        assertTrue(type.matches("*.*.*.d"))
    }
}

class EventTypesObjectTest {
    @Test
    fun commandStartedHasCorrectValue() {
        assertEquals("command.started", EventTypes.COMMAND_STARTED.value)
    }

    @Test
    fun commandCompletedHasCorrectValue() {
        assertEquals("command.completed", EventTypes.COMMAND_COMPLETED.value)
    }

    @Test
    fun commandFailedHasCorrectValue() {
        assertEquals("command.failed", EventTypes.COMMAND_FAILED.value)
    }

    @Test
    fun sessionCreatedHasCorrectValue() {
        assertEquals("session.created", EventTypes.SESSION_CREATED.value)
    }

    @Test
    fun sessionClosedHasCorrectValue() {
        assertEquals("session.closed", EventTypes.SESSION_CLOSED.value)
    }

    @Test
    fun userContextCreatedHasCorrectValue() {
        assertEquals("user-context.created", EventTypes.USER_CONTEXT_CREATED.value)
    }

    @Test
    fun userContextClosedHasCorrectValue() {
        assertEquals("user-context.closed", EventTypes.USER_CONTEXT_CLOSED.value)
    }

    @Test
    fun customHasCorrectValue() {
        assertEquals("custom", EventTypes.CUSTOM.value)
    }
}

class EventTypePatternMatchingEdgeCasesTest {
    @Test
    fun matchesEmptyPatternReturnsFalse() {
        val type = EventType("command.started")
        assertFalse(type.matches(""))
    }

    @Test
    fun matchesSingleDotPattern() {
        val type = EventType("a.b")
        assertTrue(type.matches("a.b"))
    }

    @Test
    fun matchesPartialPrefixReturnsFalse() {
        val type = EventType("command.started")
        assertFalse(type.matches("command"))
    }

    @Test
    fun matchesPartialSuffixReturnsFalse() {
        val type = EventType("command.started")
        assertFalse(type.matches("started"))
    }

    @Test
    fun matchesExtraSegmentReturnsFalse() {
        val type = EventType("command.started")
        assertFalse(type.matches("command.started.extra"))
    }

    @Test
    fun matchesMissingSegmentReturnsFalse() {
        val type = EventType("command.lifecycle.started")
        assertFalse(type.matches("command.started"))
    }
}
