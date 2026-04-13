/*
 * © 2025 Sphereon International B.V.
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

class EventSubsystemBasicTest {

    @Test
    fun eventSubsystemHasValue() {
        val subsystem = EventSubsystem("crypto")
        assertEquals("crypto", subsystem.value)
    }

    @Test
    fun eventSubsystemToStringReturnsValue() {
        val subsystem = EventSubsystem("crypto")
        assertEquals("crypto", subsystem.toString())
    }

    @Test
    fun eventSubsystemEqualsWorks() {
        val subsystem1 = EventSubsystem("crypto")
        val subsystem2 = EventSubsystem("crypto")
        assertEquals(subsystem1, subsystem2)
    }

    @Test
    fun eventSubsystemNotEqualsWorks() {
        val subsystem1 = EventSubsystem("crypto")
        val subsystem2 = EventSubsystem("http")
        assertFalse(subsystem1 == subsystem2)
    }
}

class EventSubsystemMatchesExactTest {

    @Test
    fun matchesExactValueReturnsTrue() {
        val subsystem = EventSubsystem("crypto")
        assertTrue(subsystem.matches("crypto"))
    }

    @Test
    fun matchesDifferentValueReturnsFalse() {
        val subsystem = EventSubsystem("crypto")
        assertFalse(subsystem.matches("http"))
    }
}

class EventSubsystemMatchesWildcardTest {

    @Test
    fun matchesSingleStarReturnsTrue() {
        val subsystem = EventSubsystem("crypto")
        assertTrue(subsystem.matches("*"))
    }

    @Test
    fun matchesDoubleStarReturnsTrue() {
        val subsystem = EventSubsystem("crypto")
        assertTrue(subsystem.matches("**"))
    }

    @Test
    fun matchesPrefixWildcard() {
        val subsystem = EventSubsystem("crypto-manager")
        assertTrue(subsystem.matches("crypto*"))
    }

    @Test
    fun matchesSuffixWildcard() {
        val subsystem = EventSubsystem("crypto-manager")
        assertTrue(subsystem.matches("*manager"))
    }

    @Test
    fun matchesMiddleWildcard() {
        val subsystem = EventSubsystem("crypto-manager")
        assertTrue(subsystem.matches("crypto*manager"))
    }

    @Test
    fun matchesMultipleWildcards() {
        val subsystem = EventSubsystem("crypto-key-manager")
        assertTrue(subsystem.matches("*key*"))
    }
}

class EventSubsystemMatchesPatternTest {

    @Test
    fun matchesPartialPatternReturnsFalse() {
        val subsystem = EventSubsystem("crypto")
        assertFalse(subsystem.matches("crypt"))
    }

    @Test
    fun matchesLongerPatternReturnsFalse() {
        val subsystem = EventSubsystem("crypto")
        assertFalse(subsystem.matches("cryptography"))
    }

    @Test
    fun matchesCaseSensitive() {
        val subsystem = EventSubsystem("crypto")
        assertFalse(subsystem.matches("CRYPTO"))
    }
}

class EventSubsystemsObjectTest {

    @Test
    fun cryptoHasCorrectValue() {
        assertEquals("crypto", EventSubsystems.CRYPTO.value)
    }

    @Test
    fun kmsHasCorrectValue() {
        assertEquals("kms", EventSubsystems.KMS.value)
    }

    @Test
    fun mdocHasCorrectValue() {
        assertEquals("mdoc", EventSubsystems.MDOC.value)
    }

    @Test
    fun sdjwtHasCorrectValue() {
        assertEquals("sdjwt", EventSubsystems.SDJWT.value)
    }

    @Test
    fun oauthHasCorrectValue() {
        assertEquals("oauth", EventSubsystems.OAUTH.value)
    }

    @Test
    fun oid4vpHasCorrectValue() {
        assertEquals("oid4vp", EventSubsystems.OID4VP.value)
    }

    @Test
    fun oid4vciHasCorrectValue() {
        assertEquals("oid4vci", EventSubsystems.OID4VCI.value)
    }

    @Test
    fun httpHasCorrectValue() {
        assertEquals("http", EventSubsystems.HTTP.value)
    }

    @Test
    fun sessionHasCorrectValue() {
        assertEquals("session", EventSubsystems.SESSION.value)
    }

    @Test
    fun eventsHasCorrectValue() {
        assertEquals("events", EventSubsystems.EVENTS.value)
    }

    @Test
    fun customHasCorrectValue() {
        assertEquals("custom", EventSubsystems.CUSTOM.value)
    }
}

class EventSubsystemEdgeCasesTest {

    @Test
    fun matchesEmptyPatternReturnsFalse() {
        val subsystem = EventSubsystem("crypto")
        assertFalse(subsystem.matches(""))
    }

    @Test
    fun matchesEmptySubsystemWithStar() {
        val subsystem = EventSubsystem("")
        assertTrue(subsystem.matches("*"))
    }

    @Test
    fun matchesEmptySubsystemWithDoubleStar() {
        val subsystem = EventSubsystem("")
        assertTrue(subsystem.matches("**"))
    }

    @Test
    fun matchesSubsystemWithSpecialChars() {
        val subsystem = EventSubsystem("my-subsystem")
        assertTrue(subsystem.matches("my-subsystem"))
    }

    @Test
    fun matchesSubsystemWithDots() {
        val subsystem = EventSubsystem("my.subsystem")
        assertTrue(subsystem.matches("my.subsystem"))
    }
}
