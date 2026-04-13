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

package com.sphereon.core.api.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandIdTryParseTest {
    @Test
    fun parsesThreeSegmentCommandId() {
        val id = CommandId.tryParse("kms.keys.get")
        assertNotNull(id)
        assertEquals("kms", id.module)
        assertEquals("keys", id.service)
        assertEquals("get", id.command)
    }

    @Test
    fun parsesAnotherThreeSegmentCommandId() {
        val id = CommandId.tryParse("did.manager.resolve")
        assertNotNull(id)
        assertEquals("did", id.module)
        assertEquals("manager", id.service)
        assertEquals("resolve", id.command)
    }

    @Test
    fun parsesPartyCommandId() {
        val id = CommandId.tryParse("party.parties.create")
        assertNotNull(id)
        assertEquals("party", id.module)
        assertEquals("parties", id.service)
        assertEquals("create", id.command)
    }

    @Test
    fun returnsNullForFourSegments() {
        val id = CommandId.tryParse("core.session.session.create")
        assertNull(id)
    }

    @Test
    fun returnsNullForTwoSegments() {
        val id = CommandId.tryParse("core.session")
        assertNull(id)
    }

    @Test
    fun returnsNullForSingleSegment() {
        val id = CommandId.tryParse("core")
        assertNull(id)
    }

    @Test
    fun returnsNullForEmptyString() {
        val id = CommandId.tryParse("")
        assertNull(id)
    }

    @Test
    fun returnsNullForFiveSegments() {
        val id = CommandId.tryParse("openid.oid4vp.verifier.request.create")
        assertNull(id)
    }
}

class CommandIdTryParseResultTest {
    @Test
    fun parsesThreeSegmentCommandIdSuccessfully() {
        val result = CommandId.tryParseResult("kms.keys.get")
        assertTrue(result.isOk)
        assertEquals("kms", result.value.module)
        assertEquals("keys", result.value.service)
        assertEquals("get", result.value.command)
    }

    @Test
    fun parsesDidCommandIdSuccessfully() {
        val result = CommandId.tryParseResult("did.manager.resolve")
        assertTrue(result.isOk)
        assertEquals("did", result.value.module)
        assertEquals("manager", result.value.service)
        assertEquals("resolve", result.value.command)
    }

    @Test
    fun failsForFourSegments() {
        val result = CommandId.tryParseResult("core.session.session.create")
        assertTrue(result.isErr)
        assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
    }

    @Test
    fun failsForTwoSegments() {
        val result = CommandId.tryParseResult("core.session")
        assertTrue(result.isErr)
        assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
    }

    @Test
    fun failsForUppercaseCommandId() {
        val result = CommandId.tryParseResult("Kms.Keys.Get")
        assertTrue(result.isErr)
    }

    @Test
    fun failsForSegmentStartingWithDigit() {
        val result = CommandId.tryParseResult("1kms.keys.get")
        assertTrue(result.isErr)
    }

    @Test
    fun failsForEmptyString() {
        val result = CommandId.tryParseResult("")
        assertTrue(result.isErr)
    }
}

class CommandIdPropertiesTest {
    @Test
    fun toStringFormatsCorrectly() {
        val id = CommandId.of("kms", "keys", "get")
        assertEquals("kms.keys.get", id.toString())
    }

    @Test
    fun toStringWithDifferentParts() {
        val id = CommandId.of("did", "manager", "resolve")
        assertEquals("did.manager.resolve", id.toString())
    }

    @Test
    fun ofCreatesValidCommandId() {
        val id = CommandId.of("party", "parties", "create")
        assertEquals("party.parties.create", id.value)
    }

    @Test
    fun roundTripThroughTryParseAndToString() {
        val original = "party.parties.create"
        val id = CommandId.tryParse(original)
        assertNotNull(id)
        assertEquals(original, id.toString())
    }

    @Test
    fun equalityWorksCorrectly() {
        val id1 = CommandId("kms.keys.get")
        val id2 = CommandId("kms.keys.get")
        assertEquals(id1, id2)
    }
}

class ExtractCommandTest {
    @Test
    fun extractsCommandFromThreeSegmentId() {
        assertEquals("create", extractCommand("party.parties.create"))
    }

    @Test
    fun extractsCommandFromAnotherThreeSegmentId() {
        assertEquals("resolve", extractCommand("did.manager.resolve"))
    }

    @Test
    fun returnsWholeStringIfNoDots() {
        assertEquals("create", extractCommand("create"))
    }

    @Test
    fun extractsLastSegmentRegardlessOfCount() {
        assertEquals("get", extractCommand("kms.keys.get"))
    }
}
