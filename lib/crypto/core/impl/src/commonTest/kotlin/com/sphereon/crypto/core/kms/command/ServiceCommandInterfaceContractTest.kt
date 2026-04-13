/*
 * Copyright (c) 2025 Sphereon International B.V.
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
 */

package com.sphereon.crypto.core.kms.command

import com.sphereon.crypto.jose.jwe.CreateJweCompactCommand
import com.sphereon.crypto.jose.jwe.CreateJweJsonFlattenedCommand
import com.sphereon.crypto.jose.jwe.CreateJweJsonGeneralCommand
import com.sphereon.crypto.jose.jwe.DecryptJweCommand
import com.sphereon.crypto.jose.jwe.PrepareJweCommand
import com.sphereon.crypto.jose.jws.CreateJwsCompactCommand
import com.sphereon.crypto.jose.jws.CreateJwsJsonFlattenedCommand
import com.sphereon.crypto.jose.jws.CreateJwsJsonGeneralCommand
import com.sphereon.crypto.jose.jws.PrepareJwsCommand
import com.sphereon.crypto.jose.jws.VerifyJwsCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validates structural invariants of the ServiceCommand interface contract for all commands.
 *
 * These tests catch real migration bugs:
 * - Missing or empty COMMAND_ID in companion objects
 * - Malformed command IDs that break serviceId/operationName derivation
 * - Duplicate command IDs causing routing collisions
 * - Wrong domain grouping causing commands to be routed to the wrong microservice
 */
class ServiceCommandInterfaceContractTest {

    /**
     * Registry of all migrated commands with their companion metadata.
     * Adding a new command here ensures it's covered by all structural tests.
     */
    private data class CommandSpec(
        val name: String,
        val commandId: String,
        val expectedDomain: String,
        val expectedServiceId: String,
        val expectedOperation: String
    )

    private val allCommands: List<CommandSpec> = listOf(
        // KMS Key Management
        CommandSpec("GenerateKeyCommand", GenerateKeyCommand.COMMAND_ID, "kms", "kms.key", "generate"),
        CommandSpec("ListKeysCommand", ListKeysCommand.COMMAND_ID, "kms", "kms.key", "list"),
        CommandSpec("GetKeyCommand", GetKeyCommand.COMMAND_ID, "kms", "kms.key", "get"),
        CommandSpec("StoreKeyCommand", StoreKeyCommand.COMMAND_ID, "kms", "kms.key", "store"),
        CommandSpec("DeleteKeyCommand", DeleteKeyCommand.COMMAND_ID, "kms", "kms.key", "delete"),
        // KMS Key Resolution
        CommandSpec("ResolvePublicKeyCommand", ResolvePublicKeyCommand.COMMAND_ID, "kms", "kms.key", "resolve"),
        // KMS Signatures
        CommandSpec("CreateRawSignatureCommand", CreateRawSignatureCommand.COMMAND_ID, "kms", "kms.signature", "create"),
        CommandSpec("VerifyRawSignatureCommand", VerifyRawSignatureCommand.COMMAND_ID, "kms", "kms.signature", "verify"),
        // KMS Encryption
        CommandSpec("EncryptCommand", EncryptCommand.COMMAND_ID, "kms", "kms.encryption", "encrypt"),
        CommandSpec("DecryptCommand", DecryptCommand.COMMAND_ID, "kms", "kms.encryption", "decrypt"),
        CommandSpec("WrapKeyCommand", WrapKeyCommand.COMMAND_ID, "kms", "kms.encryption", "wrap"),
        CommandSpec("UnwrapKeyCommand", UnwrapKeyCommand.COMMAND_ID, "kms", "kms.encryption", "unwrap"),
        CommandSpec("PerformKeyAgreementCommand", PerformKeyAgreementCommand.COMMAND_ID, "kms", "kms.encryption", "agree"),
        // JWS
        CommandSpec("PrepareJwsCommand", PrepareJwsCommand.COMMAND_ID, "crypto", "crypto.jws", "prepare"),
        CommandSpec("CreateJwsCompactCommand", CreateJwsCompactCommand.COMMAND_ID, "crypto", "crypto.jws", "compact"),
        CommandSpec("CreateJwsJsonFlattenedCommand", CreateJwsJsonFlattenedCommand.COMMAND_ID, "crypto", "crypto.jws", "flattened"),
        CommandSpec("CreateJwsJsonGeneralCommand", CreateJwsJsonGeneralCommand.COMMAND_ID, "crypto", "crypto.jws", "general"),
        CommandSpec("VerifyJwsCommand", VerifyJwsCommand.COMMAND_ID, "crypto", "crypto.jws", "verify"),
        // JWE
        CommandSpec("PrepareJweCommand", PrepareJweCommand.COMMAND_ID, "crypto", "crypto.jwe", "prepare"),
        CommandSpec("CreateJweCompactCommand", CreateJweCompactCommand.COMMAND_ID, "crypto", "crypto.jwe", "compact"),
        CommandSpec("CreateJweJsonFlattenedCommand", CreateJweJsonFlattenedCommand.COMMAND_ID, "crypto", "crypto.jwe", "flattened"),
        CommandSpec("CreateJweJsonGeneralCommand", CreateJweJsonGeneralCommand.COMMAND_ID, "crypto", "crypto.jwe", "general"),
        CommandSpec("DecryptJweCommand", DecryptJweCommand.COMMAND_ID, "crypto", "crypto.jwe", "decrypt"),
    )

    // ========================================================================
    // Structural invariant: command IDs are non-empty
    // ========================================================================

    @Test
    fun allCommandIdsAreNonEmpty() {
        // Given: all migrated commands
        // Then: every COMMAND_ID is non-blank
        for (cmd in allCommands) {
            assertTrue(
                cmd.commandId.isNotBlank(),
                "${cmd.name}.COMMAND_ID should not be blank"
            )
        }
    }

    // ========================================================================
    // Structural invariant: command IDs follow dotted notation
    // ========================================================================

    @Test
    fun allCommandIdsHaveAtLeastThreeSegments() {
        // Given: all migrated commands
        // Then: every COMMAND_ID has at least 3 dot-separated segments (domain.service.operation)
        for (cmd in allCommands) {
            val segments = cmd.commandId.split('.')
            assertTrue(
                segments.size >= 3,
                "${cmd.name}.COMMAND_ID = '${cmd.commandId}' has ${segments.size} segment(s), expected >= 3"
            )
        }
    }

    @Test
    fun allCommandIdsContainNoEmptySegments() {
        // Given: all migrated commands
        // Then: no segment is blank (catches "kms..generate" or "kms.key." typos)
        for (cmd in allCommands) {
            val segments = cmd.commandId.split('.')
            for ((i, segment) in segments.withIndex()) {
                assertTrue(
                    segment.isNotBlank(),
                    "${cmd.name}.COMMAND_ID = '${cmd.commandId}' has blank segment at index $i"
                )
            }
        }
    }

    // ========================================================================
    // Structural invariant: serviceId/operationName/domain derivation
    // ========================================================================

    @Test
    fun serviceIdDerivationMatchesDomainGrouping() {
        // Given: all migrated commands with their expected domain grouping
        // Then: substringBeforeLast('.') yields the expected serviceId
        for (cmd in allCommands) {
            val derivedServiceId = cmd.commandId.substringBeforeLast('.')
            assertEquals(
                cmd.expectedServiceId, derivedServiceId,
                "${cmd.name}: serviceId derivation mismatch — indicates wrong commandId structure"
            )
        }
    }

    @Test
    fun operationNameDerivationIsNonEmpty() {
        // Given: all migrated commands
        // Then: substringAfterLast('.') yields a non-blank operation name
        for (cmd in allCommands) {
            val derivedOp = cmd.commandId.substringAfterLast('.')
            assertEquals(
                cmd.expectedOperation, derivedOp,
                "${cmd.name}: operationName derivation mismatch"
            )
            assertTrue(
                derivedOp.isNotBlank(),
                "${cmd.name}: operationName is blank"
            )
        }
    }

    @Test
    fun domainDerivationMatchesExpected() {
        // Given: all migrated commands
        // Then: substringBefore('.') yields the expected domain (first segment)
        for (cmd in allCommands) {
            val derivedDomain = cmd.commandId.substringBefore('.')
            assertEquals(
                cmd.expectedDomain, derivedDomain,
                "${cmd.name}: serviceDomain derivation mismatch — would route to wrong microservice"
            )
        }
    }

    // ========================================================================
    // Structural invariant: uniqueness (prevents routing collisions)
    // ========================================================================

    @Test
    fun commandIdsAreUniqueAcrossAllDomains() {
        // Given: all migrated command IDs
        val allIds = allCommands.map { it.commandId }

        // Then: no duplicates (duplicates would cause routing collisions in the transport layer)
        val duplicates = allIds.groupBy { it }.filter { it.value.size > 1 }.keys
        assertEquals(
            allIds.size, allIds.toSet().size,
            "Duplicate command IDs found (would cause routing collisions): $duplicates"
        )
    }

    // ========================================================================
    // Structural invariant: domain grouping consistency
    // ========================================================================

    @Test
    fun allKmsCommandsShareKmsDomain() {
        // Given: commands expected to be in the KMS domain
        val kmsCommands = allCommands.filter { it.expectedDomain == "kms" }

        // Then: there are exactly 13 KMS commands (5 key mgmt + 1 resolution + 2 sig + 5 enc)
        assertEquals(13, kmsCommands.size, "Expected 13 KMS commands")

        // And: they all actually derive "kms" as their domain
        for (cmd in kmsCommands) {
            assertEquals("kms", cmd.commandId.substringBefore('.'),
                "${cmd.name} should be in 'kms' domain but commandId is '${cmd.commandId}'")
        }
    }

    @Test
    fun allJwsCommandsShareSameServiceId() {
        // Given: JWS commands
        val jwsCommands = allCommands.filter { it.expectedServiceId == "crypto.jws" }

        // Then: there are exactly 5 JWS commands
        assertEquals(5, jwsCommands.size, "Expected 5 JWS commands")

        // And: they all derive the same serviceId (prevents one being accidentally mis-grouped)
        val serviceIds = jwsCommands.map { it.commandId.substringBeforeLast('.') }.toSet()
        assertEquals(1, serviceIds.size,
            "All JWS commands should share one serviceId, found: $serviceIds")
    }

    @Test
    fun allJweCommandsShareSameServiceId() {
        // Given: JWE commands
        val jweCommands = allCommands.filter { it.expectedServiceId == "crypto.jwe" }

        // Then: there are exactly 5 JWE commands
        assertEquals(5, jweCommands.size, "Expected 5 JWE commands")

        // And: they all derive the same serviceId
        val serviceIds = jweCommands.map { it.commandId.substringBeforeLast('.') }.toSet()
        assertEquals(1, serviceIds.size,
            "All JWE commands should share one serviceId, found: $serviceIds")
    }

    // ========================================================================
    // Structural invariant: total command count (catch missing/extra commands)
    // ========================================================================

    @Test
    fun totalMigratedCommandCountMatchesExpected() {
        // Given: Covers Crypto Core (13 KMS), JWS (5), JWE (5) = 23 commands
        assertEquals(23, allCommands.size,
            "Should have exactly 23 migrated commands. If you add a new command, add it to allCommands list.")
    }
}
