/*
 * (c) 2026 Sphereon International B.V.
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

class CommandIdValidationTest {
    @Test
    fun validCommandIdWithThreeSegmentsIsAccepted() {
        // Given a valid 3-segment command ID
        val id = "kms.keys.get"

        // When validated
        val result = requireCommandId(id)

        // Then it is accepted
        assertTrue(result.isOk)
        assertEquals("kms.keys.get", result.value)
    }

    @Test
    fun multipleValidThreeSegmentIdsAreAccepted() {
        // Given various valid 3-segment command IDs
        // When/Then each is accepted
        assertTrue(requireCommandId("did.manager.resolve").isOk)
        assertTrue(requireCommandId("party.parties.create").isOk)
        assertTrue(requireCommandId("identity.identities.read").isOk)
    }

    @Test
    fun commandIdWithSingleSegmentIsRejected() {
        // Given a single-segment string
        val result = requireCommandId("invalid")

        // Then it is rejected
        assertTrue(result.isErr)
        assertTrue(
            result.error.message.defaultMessage
                .contains("Invalid command ID format"),
        )
    }

    @Test
    fun commandIdWithTwoSegmentsIsRejected() {
        // Given a 2-segment string
        val result = requireCommandId("core.session")

        // Then it is rejected
        assertTrue(result.isErr)
        assertTrue(
            result.error.message.defaultMessage
                .contains("Invalid command ID format"),
        )
    }

    @Test
    fun commandIdWithFourSegmentsIsRejected() {
        // Given a 4-segment string (old format)
        val result = requireCommandId("core.session.session.create")

        // Then it is rejected
        assertTrue(result.isErr)
    }

    @Test
    fun commandIdWithFiveSegmentsIsRejected() {
        // Given a 5-segment string (old format with variant)
        val result = requireCommandId("openid.oid4vp.verifier.request.create")

        // Then it is rejected
        assertTrue(result.isErr)
    }

    @Test
    fun commandIdWithUppercaseIsRejected() {
        // Given a command ID with uppercase characters
        val result = requireCommandId("Core.Session.Create")

        // Then it is rejected
        assertTrue(result.isErr)
    }

    @Test
    fun commandIdWithEmptySegmentIsRejected() {
        // Given a command ID with an empty segment
        val result = requireCommandId("kms..get")

        // Then it is rejected
        assertTrue(result.isErr)
    }

    @Test
    fun commandIdWithSegmentStartingWithDigitIsRejected() {
        // Given a command ID where a segment starts with a digit
        val result = requireCommandId("kms.1keys.get")

        // Then it is rejected
        assertTrue(result.isErr)
    }

    @Test
    fun isValidCommandIdReturnsTrueForValidIds() {
        // Given/When/Then valid 3-segment IDs are recognized
        assertTrue(isValidCommandId("kms.keys.get"))
        assertTrue(isValidCommandId("did.manager.resolve"))
        assertTrue(isValidCommandId("party.parties.create"))
        assertTrue(isValidCommandId("identity.identities.read"))
    }

    @Test
    fun isValidCommandIdReturnsFalseForInvalidIds() {
        // Given/When/Then invalid IDs are rejected
        assertFalse(isValidCommandId("invalid"))
        assertFalse(isValidCommandId("core.session"))
        assertFalse(isValidCommandId("core.session.session.create"))
        assertFalse(isValidCommandId("Core.Session.Create"))
        assertFalse(isValidCommandId(""))
    }

    @Test
    fun commandIdWithNumbersInSegmentsIsAccepted() {
        // Given command IDs with numbers (not at segment start)
        assertTrue(isValidCommandId("oid4vp.verifier.create"))
        assertTrue(isValidCommandId("api.v2.get"))
    }
}

class CommandIdValueClassTest {
    @Test
    fun commandIdExtractsModuleCorrectly() {
        // Given a valid command ID
        val id = CommandId("kms.keys.get")

        // When accessing the module
        // Then the first segment is returned
        assertEquals("kms", id.module)
    }

    @Test
    fun commandIdExtractsServiceCorrectly() {
        // Given a valid command ID
        val id = CommandId("kms.keys.get")

        // When accessing the service
        // Then the second segment is returned
        assertEquals("keys", id.service)
    }

    @Test
    fun commandIdExtractsCommandCorrectly() {
        // Given a valid command ID
        val id = CommandId("kms.keys.get")

        // When accessing the command
        // Then the third segment is returned
        assertEquals("get", id.command)
    }

    @Test
    fun commandIdSegmentsReturnsAllThreeSegments() {
        // Given a valid command ID
        val id = CommandId("did.manager.resolve")

        // When accessing segments
        // Then all 3 segments are returned
        assertEquals(listOf("did", "manager", "resolve"), id.segments)
    }

    @Test
    fun commandIdOfCreatesValidIdFromThreeArgs() {
        // Given module, service, and command strings
        // When creating via of()
        val id = CommandId.of("party", "parties", "create")

        // Then the composite string is correct
        assertEquals("party.parties.create", id.value)
    }

    @Test
    fun commandIdOfValidatesTheFinalCanonicalId() {
        assertFailsWith<IllegalArgumentException> {
            CommandId.of("party", "parties-", "create")
        }
        assertFailsWith<IllegalArgumentException> {
            CommandId.of("party", "parties", "create--record")
        }
        assertFailsWith<IllegalArgumentException> {
            CommandId.of("party.parties", "records", "create")
        }
    }

    @Test
    fun commandIdTryParseReturnsCommandIdForValidId() {
        // Given a valid command ID string
        val id = CommandId.tryParse("identity.identities.read")

        // Then a CommandId is returned
        assertNotNull(id)
        assertEquals("identity.identities.read", id.value)
    }

    @Test
    fun commandIdTryParseReturnsNullForInvalidId() {
        // Given invalid command ID strings
        // Then tryParse returns null
        assertNull(CommandId.tryParse("invalid"))
        assertNull(CommandId.tryParse("Core.Session.Create"))
        assertNull(CommandId.tryParse("core.session"))
        assertNull(CommandId.tryParse("core.session.session.create"))
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
        // Given a 4-segment string (old format)
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

    @Test
    fun commandIdValuePropertyReturnsFullString() {
        // Given a CommandId created via of()
        val id = CommandId.of("did", "manager", "resolve")

        // Then the value property holds the full string
        assertEquals("did.manager.resolve", id.value)
    }
}

class CommandIdTryParseInlineTest {
    @Test
    fun tryParseReturnsNullForInvalidId() {
        // Given invalid command ID strings
        // When parsing
        // Then null is returned
        assertNull(CommandId.tryParse("invalid"))
        assertNull(CommandId.tryParse("did.resolve"))
        assertNull(CommandId.tryParse("core.session.session.create"))
    }

    @Test
    fun tryParseReturnsCommandIdForThreeSegmentId() {
        // Given a valid 3-segment command ID
        val id = CommandId.tryParse("kms.keys.get")

        // Then parts are correctly extracted
        assertNotNull(id)
        assertEquals("kms", id.module)
        assertEquals("keys", id.service)
        assertEquals("get", id.command)
    }

    @Test
    fun tryParseReturnsCommandIdForVariousIds() {
        // Given another valid command ID
        val id = CommandId.tryParse("did.manager.resolve")

        // Then parts are correctly extracted
        assertNotNull(id)
        assertEquals("did", id.module)
        assertEquals("manager", id.service)
        assertEquals("resolve", id.command)
    }

    @Test
    fun tryParseResultReturnsOkForValidId() {
        // Given a valid command ID
        val result = CommandId.tryParseResult("party.parties.create")

        // Then Ok is returned with correct parts
        assertTrue(result.isOk)
        assertEquals("party", result.value.module)
        assertEquals("parties", result.value.service)
        assertEquals("create", result.value.command)
    }

    @Test
    fun tryParseResultReturnsErrForInvalidId() {
        // Given an invalid command ID
        val result = CommandId.tryParseResult("invalid")

        // Then Err is returned
        assertTrue(result.isErr)
    }

    @Test
    fun tryParseResultReturnsErrForFourSegmentId() {
        // Given a 4-segment command ID (old format)
        val result = CommandId.tryParseResult("core.session.session.create")

        // Then Err is returned
        assertTrue(result.isErr)
    }

    @Test
    fun extractCommandReturnsLastSegment() {
        // Given valid command ID strings
        // When extracting the command
        // Then the last segment is returned
        assertEquals("get", extractCommand("kms.keys.get"))
        assertEquals("resolve", extractCommand("did.manager.resolve"))
        assertEquals("create", extractCommand("party.parties.create"))
    }

    @Test
    fun commandIdOfReconstructs() {
        // Given CommandId created from parts
        val id = CommandId.of("kms", "keys", "get")

        // When converting to string
        // Then the 3-segment string is reconstructed
        assertEquals("kms.keys.get", id.toString())
    }

    @Test
    fun commandIdOfCreatesValidCommandId() {
        // Given CommandId created from parts
        val id = CommandId.of("did", "manager", "resolve")

        // Then a valid CommandId is created
        assertEquals("did.manager.resolve", id.value)
    }

    @Test
    fun commandIdFieldsAreAccessible() {
        // Given a CommandId
        val id = CommandId("identity.identities.read")

        // Then all fields are accessible
        assertEquals("identity", id.module)
        assertEquals("identities", id.service)
        assertEquals("read", id.command)
    }
}

class CommandPatternMatchingTest {
    @Test
    fun matchesAdvancedPatternExactMatch() {
        // Given exact match scenarios
        // When/Then exact matches work correctly
        assertTrue(matchesAdvancedPattern("kms.keys.get", "kms.keys.get"))
        assertFalse(matchesAdvancedPattern("kms.keys.get", "kms.keys.create"))
    }

    @Test
    fun matchesAdvancedPatternSingleWildcard() {
        // Given single wildcard patterns
        // When/Then single segment wildcard matches one segment
        assertTrue(matchesAdvancedPattern("did.*.resolve", "did.manager.resolve"))
        assertTrue(matchesAdvancedPattern("did.*.resolve", "did.resolver.resolve"))
        assertFalse(matchesAdvancedPattern("did.*.resolve", "did.manager.create"))
    }

    @Test
    fun matchesAdvancedPatternDoubleWildcard() {
        // Given double wildcard patterns
        // When/Then ** matches any remaining segments
        assertTrue(matchesAdvancedPattern("kms.**", "kms.keys.get"))
        assertTrue(matchesAdvancedPattern("did.**", "did.manager.resolve"))
        assertFalse(matchesAdvancedPattern("kms.**", "did.manager.resolve"))
    }

    @Test
    fun matchesAdvancedPatternAlternatives() {
        // Given alternative patterns
        // When/Then alternatives match listed values only
        assertTrue(matchesAdvancedPattern("party.{parties,resources}.create", "party.parties.create"))
        assertTrue(matchesAdvancedPattern("party.{parties,resources}.create", "party.resources.create"))
        assertFalse(matchesAdvancedPattern("party.{parties,resources}.create", "party.users.create"))
    }

    @Test
    fun matchesAdvancedPatternCombined() {
        // Given combined patterns
        // When/Then combined wildcards and alternatives work together
        assertTrue(matchesAdvancedPattern("*.{parties,resources}.*", "party.parties.read"))
        assertTrue(matchesAdvancedPattern("*.{parties,resources}.*", "data.resources.write"))
        assertFalse(matchesAdvancedPattern("*.{parties,resources}.*", "data.users.read"))
    }

    @Test
    fun matchesSimplePatternExact() {
        // Given exact simple patterns
        // When/Then exact matching works
        assertTrue(matchesSimplePattern("kms.keys.get", "kms.keys.get"))
        assertFalse(matchesSimplePattern("kms.keys.get", "kms.keys.create"))
    }

    @Test
    fun matchesSimplePatternSingleStar() {
        // Given single star suffix patterns
        // When/Then it matches exactly one segment
        assertTrue(matchesSimplePattern("kms.keys.*", "kms.keys.get"))
        assertTrue(matchesSimplePattern("kms.keys.*", "kms.keys.create"))
        assertFalse(matchesSimplePattern("kms.*", "kms.keys.get"))
    }

    @Test
    fun matchesSimplePatternDoubleStar() {
        // Given double star suffix patterns
        // When/Then it matches any remaining depth
        assertTrue(matchesSimplePattern("kms.**", "kms.keys.get"))
        assertTrue(matchesSimplePattern("kms.**", "kms.anything.deeply"))
    }
}

class CommandRegistryTest {
    @Test
    fun registryRegistersAndListsIds() {
        // Given an empty registry
        val registry = InMemoryCommandRegistry()

        // When registering command IDs
        registry.register("kms.keys.get", "kms")
        registry.register("did.manager.resolve", "did")

        // Then both are listed
        val ids = registry.listIds()
        assertEquals(2, ids.size)
        assertTrue("kms.keys.get" in ids)
        assertTrue("did.manager.resolve" in ids)
    }

    @Test
    fun registryThrowsForInvalidCommandId() {
        // Given an empty registry
        val registry = InMemoryCommandRegistry()

        // When registering an invalid command ID
        // Then an exception is thrown
        assertFailsWith<IllegalArgumentException> {
            registry.register("invalid", "test")
        }
    }

    @Test
    fun registryThrowsForFourSegmentCommandId() {
        // Given an empty registry
        val registry = InMemoryCommandRegistry()

        // When registering a 4-segment command ID (old format)
        // Then an exception is thrown
        assertFailsWith<IllegalArgumentException> {
            registry.register("core.session.session.create", "session")
        }
    }

    @Test
    fun registryFindsBySubsystem() {
        // Given a registry with commands from different subsystems
        val registry = InMemoryCommandRegistry()
        registry.register("kms.keys.get", "kms")
        registry.register("kms.keys.create", "kms")
        registry.register("did.manager.resolve", "did")

        // When finding by subsystem
        val kmsCommands = registry.findBySubsystem("kms")

        // Then only commands in that subsystem are returned
        assertEquals(2, kmsCommands.size)
        assertTrue("kms.keys.get" in kmsCommands)
        assertTrue("kms.keys.create" in kmsCommands)
    }

    @Test
    fun registryFindsByTag() {
        // Given a registry with tagged commands
        val registry = InMemoryCommandRegistry()
        registry.register("kms.keys.create", "kms", setOf("write", "public"))
        registry.register("kms.keys.get", "kms", setOf("read", "public"))
        registry.register("did.manager.resolve", "did", setOf("read"))

        // When finding by tag
        val writeCommands = registry.findByTag("write")
        val publicCommands = registry.findByTag("public")

        // Then the correct commands are returned
        assertEquals(1, writeCommands.size)
        assertTrue("kms.keys.create" in writeCommands)
        assertEquals(2, publicCommands.size)
    }

    @Test
    fun registryFindsByPattern() {
        // Given a registry with commands
        val registry = InMemoryCommandRegistry()
        registry.register("kms.keys.get", "kms")
        registry.register("kms.keys.create", "kms")
        registry.register("did.manager.resolve", "did")

        // When finding by pattern
        val kmsCommands = registry.findByPattern("kms.**")
        val allCommands = registry.findByPattern("**")

        // Then patterns match correctly
        assertEquals(2, kmsCommands.size)
        assertEquals(3, allCommands.size)
    }

    @Test
    fun registryIsRegisteredReturnsCorrectly() {
        // Given a registry with one command
        val registry = InMemoryCommandRegistry()
        registry.register("kms.keys.get", "kms")

        // When checking registration
        // Then registered IDs return true, unregistered return false
        assertTrue(registry.isRegistered("kms.keys.get"))
        assertFalse(registry.isRegistered("kms.keys.create"))
    }
}
