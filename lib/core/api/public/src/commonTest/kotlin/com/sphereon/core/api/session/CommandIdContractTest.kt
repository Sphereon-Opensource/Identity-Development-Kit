/*
 * (c) 2025 Sphereon International B.V.
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract Tests: CommandId `action` -> `command` rename.
 *
 * Validates that the third segment accessor is named `command` (not `action`)
 * per the Unified Authorization, Audit, Telemetry & Shared Signals v4 plan.
 *
 * Genesis code: no backwards compatibility, no aliases.
 */
class CommandIdCommandPropertyTest {

    @Test
    fun commandIdHasCommandPropertyForThirdSegment() {
        // Given a valid command ID
        val id = CommandId("kms.keys.get")

        // When accessing the command property
        // Then it returns the third segment
        assertEquals("get", id.command)
    }

    @Test
    fun commandIdCommandPropertyWorksForVariousIds() {
        // Given various valid command IDs
        // When/Then the command property returns the correct third segment
        assertEquals("resolve", CommandId("did.manager.resolve").command)
        assertEquals("create", CommandId("party.parties.create").command)
        assertEquals("read", CommandId("identity.identities.read").command)
        assertEquals("delete", CommandId("kms.keys.delete").command)
    }

    @Test
    fun commandIdOfUsesCommandParameterName() {
        // Given module, service, and command strings
        // When creating via of()
        val id = CommandId.of("party", "parties", "create")

        // Then the command property reflects the third segment
        assertEquals("create", id.command)
        assertEquals("party.parties.create", id.value)
    }

    @Test
    fun commandIdModuleAndServiceUnchanged() {
        // Given a valid command ID
        val id = CommandId("kms.keys.get")

        // When accessing module and service
        // Then they remain unchanged
        assertEquals("kms", id.module)
        assertEquals("keys", id.service)
    }

    @Test
    fun commandIdSegmentsStillReturnsThreeSegments() {
        // Given a valid command ID
        val id = CommandId("did.manager.resolve")

        // When accessing segments
        // Then all 3 segments are returned
        assertEquals(listOf("did", "manager", "resolve"), id.segments)
    }

    @Test
    fun commandIdTryParseReturnsCommandIdWithCommandProperty() {
        // Given a valid command ID string
        val id = CommandId.tryParse("identity.identities.read")

        // Then a CommandId is returned with working command property
        assertNotNull(id)
        assertEquals("read", id.command)
    }

    @Test
    fun commandIdTryParseReturnsNullForInvalidIds() {
        // Given invalid command ID strings
        // Then tryParse returns null
        assertNull(CommandId.tryParse("invalid"))
        assertNull(CommandId.tryParse("Core.Session.Create"))
        assertNull(CommandId.tryParse("core.session"))
        assertNull(CommandId.tryParse("core.session.session.create"))
        assertNull(CommandId.tryParse(""))
    }

    @Test
    fun commandIdConstructorThrowsForInvalidId() {
        // Given an invalid command ID string
        // When constructing a CommandId
        // Then an exception is thrown
        assertFailsWith<IllegalArgumentException> {
            CommandId("invalid")
        }
    }

    @Test
    fun commandIdConstructorThrowsForFourSegmentId() {
        // Given a 4-segment string
        // When constructing a CommandId
        // Then an exception is thrown
        assertFailsWith<IllegalArgumentException> {
            CommandId("core.session.session.create")
        }
    }

    @Test
    fun commandIdToStringReturnsValue() {
        // Given a valid CommandId
        val id = CommandId("kms.keys.get")

        // Then toString returns the value
        assertEquals("kms.keys.get", id.toString())
    }
}

/**
 * Contract Tests: CommandIdParts `action` -> `command` rename.
 */
class CommandIdPartsCommandPropertyTest {

    @Test
    fun commandIdPartsHasCommandField() {
        // Given CommandIdParts with the command field
        val parts = CommandIdParts(
            module = "kms",
            service = "keys",
            command = "get"
        )

        // Then the command field is accessible
        assertEquals("get", parts.command)
    }

    @Test
    fun commandIdPartsModuleAndServiceUnchanged() {
        // Given CommandIdParts
        val parts = CommandIdParts(
            module = "did",
            service = "manager",
            command = "resolve"
        )

        // Then module and service are accessible
        assertEquals("did", parts.module)
        assertEquals("manager", parts.service)
    }

    @Test
    fun commandIdPartsToCommandIdStringFormatsCorrectly() {
        // Given CommandIdParts
        val parts = CommandIdParts(
            module = "kms",
            service = "keys",
            command = "get"
        )

        // When converting to command ID string
        // Then the 3-segment string is reconstructed
        assertEquals("kms.keys.get", parts.toCommandIdString())
    }

    @Test
    fun commandIdPartsToCommandIdCreatesValidCommandId() {
        // Given CommandIdParts
        val parts = CommandIdParts(
            module = "did",
            service = "manager",
            command = "resolve"
        )

        // When converting to CommandId
        val id = parts.toCommandId()

        // Then a valid CommandId is created
        assertEquals("did.manager.resolve", id.value)
        assertEquals("resolve", id.command)
    }

    @Test
    fun parseCommandIdReturnsPartsWithCommandField() {
        // Given a valid 3-segment command ID
        val parts = parseCommandId("kms.keys.get")

        // Then parts have the command field
        assertNotNull(parts)
        assertEquals("kms", parts.module)
        assertEquals("keys", parts.service)
        assertEquals("get", parts.command)
    }

    @Test
    fun parseCommandIdReturnsNullForInvalidIds() {
        // Given invalid command ID strings
        // When parsing
        // Then null is returned
        assertNull(parseCommandId("invalid"))
        assertNull(parseCommandId("did.resolve"))
        assertNull(parseCommandId("core.session.session.create"))
        assertNull(parseCommandId(""))
    }

    @Test
    fun parseCommandIdResultReturnsOkWithCommandField() {
        // Given a valid command ID
        val result = parseCommandIdResult("party.parties.create")

        // Then Ok is returned with correct command field
        assertTrue(result.isOk)
        assertEquals("party", result.value.module)
        assertEquals("parties", result.value.service)
        assertEquals("create", result.value.command)
    }

    @Test
    fun parseCommandIdResultReturnsErrForInvalidId() {
        // Given an invalid command ID
        val result = parseCommandIdResult("invalid")

        // Then Err is returned
        assertTrue(result.isErr)
    }

    @Test
    fun commandIdPartsCopyWorksWithCommandField() {
        // Given CommandIdParts
        val original = CommandIdParts(
            module = "kms",
            service = "keys",
            command = "get"
        )

        // When copying with a new command
        val copy = original.copy(command = "delete")

        // Then original is unchanged and copy has new value
        assertEquals("get", original.command)
        assertEquals("delete", copy.command)
    }

    @Test
    fun commandIdPartsEquality() {
        // Given two identical CommandIdParts
        val parts1 = CommandIdParts(module = "kms", service = "keys", command = "get")
        val parts2 = CommandIdParts(module = "kms", service = "keys", command = "get")

        // Then they are equal
        assertEquals(parts1, parts2)
        assertEquals(parts1.hashCode(), parts2.hashCode())
    }

    @Test
    fun extractCommandHelperExtractsLastSegment() {
        // Given valid command ID strings
        // When extracting the command (last segment)
        // Then the last segment is returned
        assertEquals("get", extractCommand("kms.keys.get"))
        assertEquals("resolve", extractCommand("did.manager.resolve"))
        assertEquals("create", extractCommand("party.parties.create"))
    }

    @Test
    fun roundTripThroughParseAndToString() {
        // Given a valid command ID string
        val original = "party.parties.create"

        // When parsing and converting back
        val parts = parseCommandId(original)
        assertNotNull(parts)

        // Then the round-trip preserves the value
        assertEquals(original, parts.toCommandIdString())
    }
}

/**
 * Contract Tests: CommandId pattern matching still works after rename.
 */
class CommandIdPatternMatchingTest {

    @Test
    fun exactMatchStillWorks() {
        // Given a command ID
        val id = CommandId("kms.keys.get")

        // When matching against exact same pattern
        // Then it matches
        assertTrue(id.matches("kms.keys.get"))
        assertFalse(id.matches("kms.keys.create"))
    }

    @Test
    fun singleWildcardStillWorks() {
        // Given a command ID
        val id = CommandId("did.manager.resolve")

        // When matching with * wildcard
        // Then it matches single segments
        assertTrue(id.matches("did.*.resolve"))
        assertFalse(id.matches("did.*.create"))
    }

    @Test
    fun doubleWildcardStillWorks() {
        // Given a command ID
        val id = CommandId("kms.keys.get")

        // When matching with **
        // Then it matches any remaining segments
        assertTrue(id.matches("kms.**"))
        assertTrue(id.matches("kms.keys.**"))
        assertFalse(id.matches("did.**"))
    }

    @Test
    fun alternativesStillWork() {
        // Given a command ID
        val id = CommandId("party.parties.create")

        // When matching with {alternatives}
        // Then it matches one of the listed values
        assertTrue(id.matches("party.{parties,resources}.create"))
        assertFalse(id.matches("party.{users,resources}.create"))
    }

    @Test
    fun combinedPatternsStillWork() {
        // Given a command ID
        val id = CommandId("party.parties.read")

        // When matching with combined patterns
        // Then it matches
        assertTrue(id.matches("*.{parties,resources}.*"))

        // And non-matching IDs are rejected
        val other = CommandId("party.users.read")
        assertFalse(other.matches("*.{parties,resources}.*"))
    }
}

/**
 * Contract Tests: CommandId validation error message uses "command" not "action".
 */
class CommandIdValidationMessageTest {

    @Test
    fun invalidCommandIdErrorMentionsModuleServiceCommand() {
        // Given an invalid command ID
        val result = requireCommandId("invalid")

        // Then the error message references module.service.command format
        assertTrue(result.isErr)
        val message = result.error.message.defaultMessage
        assertTrue(
            message.contains("module.service.command") || message.contains("Invalid command ID format"),
            "Error message should reference the canonical format. Got: $message"
        )
    }

    @Test
    fun commandErrorsInvalidCommandIdMentionsModuleServiceCommand() {
        // Given the CommandErrors.invalidCommandId factory
        val error = CommandErrors.invalidCommandId("bad-id")

        // Then the error message references the canonical format
        val message = error.message.defaultMessage
        assertTrue(
            message.contains("module.service.command") || message.contains("Invalid command ID format"),
            "Error message should reference the canonical format. Got: $message"
        )
    }
}
