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

package com.sphereon.core.api.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequireCommandIdValidTest {

    @Test
    fun validThreeSegmentIdSucceeds() {
        val result = requireCommandId("kms.keys.get")
        assertTrue(result.isOk)
        assertEquals("kms.keys.get", result.value)
    }

    @Test
    fun validDidManagerResolveSucceeds() {
        val result = requireCommandId("did.manager.resolve")
        assertTrue(result.isOk)
        assertEquals("did.manager.resolve", result.value)
    }

    @Test
    fun validPartyPartiesCreateSucceeds() {
        val result = requireCommandId("party.parties.create")
        assertTrue(result.isOk)
        assertEquals("party.parties.create", result.value)
    }

    @Test
    fun validIdWithTrailingDigitsSucceeds() {
        val result = requireCommandId("oid4vp.verifier.create")
        assertTrue(result.isOk)
    }

    @Test
    fun validIdWithNumbersInSegmentsSucceeds() {
        val result = requireCommandId("core1.session2.create3")
        assertTrue(result.isOk)
    }
}

class RequireCommandIdInvalidTest {

    @Test
    fun fourSegmentIdFails() {
        val result = requireCommandId("core.session.session.create")
        assertTrue(result.isErr)
        assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
    }

    @Test
    fun fiveSegmentIdFails() {
        val result = requireCommandId("openid.oid4vp.verifier.request.create")
        assertTrue(result.isErr)
        assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
    }

    @Test
    fun sixSegmentIdFails() {
        val result = requireCommandId("a.b.c.d.e.f")
        assertTrue(result.isErr)
    }

    @Test
    fun twoSegmentIdFails() {
        val result = requireCommandId("core.session")
        assertTrue(result.isErr)
    }

    @Test
    fun singleSegmentIdFails() {
        val result = requireCommandId("core")
        assertTrue(result.isErr)
    }

    @Test
    fun emptyStringFails() {
        val result = requireCommandId("")
        assertTrue(result.isErr)
    }

    @Test
    fun uppercaseIdFails() {
        val result = requireCommandId("Core.Session.Create")
        assertTrue(result.isErr)
    }

    @Test
    fun idWithHyphenSucceeds() {
        // Given a command ID with hyphens in segments
        val result = requireCommandId("core.session-manager.create")
        // Then it should be valid (hyphens are allowed within segments)
        assertTrue(result.isOk)
    }

    @Test
    fun idWithLeadingHyphenFails() {
        // Given a command ID with a segment starting with a hyphen
        val result = requireCommandId("-core.session.create")
        // Then it should be invalid (segments must start with a letter)
        assertTrue(result.isErr)
    }

    @Test
    fun idWithUnderscoreFails() {
        val result = requireCommandId("core.session_manager.create")
        assertTrue(result.isErr)
    }

    @Test
    fun idWithSpacesFails() {
        val result = requireCommandId("core session create")
        assertTrue(result.isErr)
    }

    @Test
    fun idStartingWithDotFails() {
        val result = requireCommandId(".core.session.create")
        assertTrue(result.isErr)
    }

    @Test
    fun idEndingWithDotFails() {
        val result = requireCommandId("core.session.create.")
        assertTrue(result.isErr)
    }

    @Test
    fun idWithConsecutiveDotsFails() {
        val result = requireCommandId("core..create")
        assertTrue(result.isErr)
    }

    @Test
    fun segmentStartingWithDigitFails() {
        val result = requireCommandId("4vp.verifier.create")
        assertTrue(result.isErr)
    }

    @Test
    fun allNumericSegmentsFails() {
        val result = requireCommandId("123.456.789")
        assertTrue(result.isErr)
    }

    @Test
    fun middleSegmentStartingWithDigitFails() {
        val result = requireCommandId("core.2session.create")
        assertTrue(result.isErr)
    }

    @Test
    fun lastSegmentStartingWithDigitFails() {
        val result = requireCommandId("core.session.1create")
        assertTrue(result.isErr)
    }
}

class IsValidCommandIdTest {

    @Test
    fun validThreeSegmentIdReturnsTrue() {
        assertTrue(isValidCommandId("kms.keys.get"))
    }

    @Test
    fun validDidManagerResolveReturnsTrue() {
        assertTrue(isValidCommandId("did.manager.resolve"))
    }

    @Test
    fun validIdWithAlphanumericSegmentsReturnsTrue() {
        assertTrue(isValidCommandId("core1.session2.create3"))
    }

    @Test
    fun fourSegmentIdReturnsFalse() {
        assertFalse(isValidCommandId("core.session.session.create"))
    }

    @Test
    fun fiveSegmentIdReturnsFalse() {
        assertFalse(isValidCommandId("openid.oid4vp.verifier.request.create"))
    }

    @Test
    fun twoSegmentIdReturnsFalse() {
        assertFalse(isValidCommandId("core.session"))
    }

    @Test
    fun invalidUppercaseIdReturnsFalse() {
        assertFalse(isValidCommandId("Core.Session.Create"))
    }

    @Test
    fun emptyStringReturnsFalse() {
        assertFalse(isValidCommandId(""))
    }

    @Test
    fun idWithSpecialCharsReturnsFalse() {
        assertFalse(isValidCommandId("core.session@.create"))
    }

    @Test
    fun allNumericSegmentsReturnsFalse() {
        assertFalse(isValidCommandId("123.456.789"))
    }

    @Test
    fun segmentStartingWithDigitReturnsFalse() {
        assertFalse(isValidCommandId("4vp.verifier.create"))
    }
}
