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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParseCommandIdTest {

    @Test
    fun parsesThreeSegmentCommandId() {
        val parts = parseCommandId("kms.keys.get")
        assertNotNull(parts)
        assertEquals("kms", parts.module)
        assertEquals("keys", parts.service)
        assertEquals("get", parts.command)
    }

    @Test
    fun parsesAnotherThreeSegmentCommandId() {
        val parts = parseCommandId("did.manager.resolve")
        assertNotNull(parts)
        assertEquals("did", parts.module)
        assertEquals("manager", parts.service)
        assertEquals("resolve", parts.command)
    }

    @Test
    fun parsesPartyCommandId() {
        val parts = parseCommandId("party.parties.create")
        assertNotNull(parts)
        assertEquals("party", parts.module)
        assertEquals("parties", parts.service)
        assertEquals("create", parts.command)
    }

    @Test
    fun returnsNullForFourSegments() {
        val parts = parseCommandId("core.session.session.create")
        assertNull(parts)
    }

    @Test
    fun returnsNullForTwoSegments() {
        val parts = parseCommandId("core.session")
        assertNull(parts)
    }

    @Test
    fun returnsNullForSingleSegment() {
        val parts = parseCommandId("core")
        assertNull(parts)
    }

    @Test
    fun returnsNullForEmptyString() {
        val parts = parseCommandId("")
        assertNull(parts)
    }

    @Test
    fun returnsNullForFiveSegments() {
        val parts = parseCommandId("openid.oid4vp.verifier.request.create")
        assertNull(parts)
    }
}

class ParseCommandIdResultTest {

    @Test
    fun parsesThreeSegmentCommandIdSuccessfully() {
        val result = parseCommandIdResult("kms.keys.get")
        assertTrue(result.isOk)
        assertEquals("kms", result.value.module)
        assertEquals("keys", result.value.service)
        assertEquals("get", result.value.command)
    }

    @Test
    fun parsesDidCommandIdSuccessfully() {
        val result = parseCommandIdResult("did.manager.resolve")
        assertTrue(result.isOk)
        assertEquals("did", result.value.module)
        assertEquals("manager", result.value.service)
        assertEquals("resolve", result.value.command)
    }

    @Test
    fun failsForFourSegments() {
        val result = parseCommandIdResult("core.session.session.create")
        assertTrue(result.isErr)
        assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
    }

    @Test
    fun failsForTwoSegments() {
        val result = parseCommandIdResult("core.session")
        assertTrue(result.isErr)
        assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
    }

    @Test
    fun failsForUppercaseCommandId() {
        val result = parseCommandIdResult("Kms.Keys.Get")
        assertTrue(result.isErr)
    }

    @Test
    fun failsForSegmentStartingWithDigit() {
        val result = parseCommandIdResult("1kms.keys.get")
        assertTrue(result.isErr)
    }

    @Test
    fun failsForEmptyString() {
        val result = parseCommandIdResult("")
        assertTrue(result.isErr)
    }
}

class CommandIdPartsDataClassTest {

    @Test
    fun toCommandIdStringFormatsCorrectly() {
        val parts = CommandIdParts(
            module = "kms",
            service = "keys",
            command = "get"
        )
        assertEquals("kms.keys.get", parts.toCommandIdString())
    }

    @Test
    fun toCommandIdStringWithDifferentParts() {
        val parts = CommandIdParts(
            module = "did",
            service = "manager",
            command = "resolve"
        )
        assertEquals("did.manager.resolve", parts.toCommandIdString())
    }

    @Test
    fun toCommandIdReturnsCommandId() {
        val parts = CommandIdParts(
            module = "party",
            service = "parties",
            command = "create"
        )
        val commandId = parts.toCommandId()
        assertEquals("party.parties.create", commandId.value)
    }

    @Test
    fun copyCreatesNewParts() {
        val original = CommandIdParts(
            module = "kms",
            service = "keys",
            command = "get"
        )
        val copy = original.copy(command = "delete")
        assertEquals("get", original.command)
        assertEquals("delete", copy.command)
    }

    @Test
    fun equalsWorksCorrectly() {
        val parts1 = CommandIdParts(
            module = "kms",
            service = "keys",
            command = "get"
        )
        val parts2 = CommandIdParts(
            module = "kms",
            service = "keys",
            command = "get"
        )
        assertEquals(parts1, parts2)
    }

    @Test
    fun hashCodeWorksCorrectly() {
        val parts1 = CommandIdParts(
            module = "kms",
            service = "keys",
            command = "get"
        )
        val parts2 = CommandIdParts(
            module = "kms",
            service = "keys",
            command = "get"
        )
        assertEquals(parts1.hashCode(), parts2.hashCode())
    }

    @Test
    fun roundTripThroughParseAndToString() {
        val original = "party.parties.create"
        val parts = parseCommandId(original)
        assertNotNull(parts)
        assertEquals(original, parts.toCommandIdString())
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
