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
 */

package com.sphereon.crypto.kms.rest.server.command

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyInput
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GenerateKeyInput
import com.sphereon.crypto.kms.rest.api.command.GenerateKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GetKeyInput
import com.sphereon.crypto.kms.rest.api.command.GetKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ListKeysInput
import com.sphereon.crypto.kms.rest.api.command.ListKeysServiceCommand
import com.sphereon.crypto.kms.rest.api.command.StoreKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyGlobal
import com.sphereon.crypto.kms.rest.api.generated.models.JwkUse
import com.sphereon.crypto.kms.rest.server.TestApiAppGraph
import com.sphereon.crypto.kms.rest.server.createTestApiAppGraph
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.sphereon.crypto.kms.rest.api.generated.models.SignatureAlgorithm as SignatureAlgorithmRest

/**
 * Integration test verifying that KMS command implementations are discoverable through
 * map multibinding and resolvable via SessionScopedCommandRegistry.
 *
 * This validates the LOCAL transport path used by EDK's routing system:
 *   Routed command -> ServiceCommandTransportFactory -> LocalServiceCommandTransport
 *   -> SessionScopedCommandRegistry.get(commandId) -> execute()
 *
 * The registry discovers commands through `Map<String, ServiceCommand<*, *>>` multibinding.
 * This test proves that real DI-injected KMS commands are in that map and executable
 * through the commandId-based lookup + execute path.
 */
class KmsCommandRegistryIntegrationTest {
    /**
     * Graph interface to access the command registry from the session graph.
     * KSP merges this into the test session graph.
     *
     * Note: only exposes the registry, not the underlying command maps.
     */
    @ContributesTo(SessionScope::class)
    interface TestRegistryGraph {
        val sessionScopedCommandRegistry: SessionScopedCommandRegistry
    }

    private lateinit var registry: SessionScopedCommandRegistry
    private lateinit var commandMap: Map<String, ServiceCommand<Any, Any>>

    @BeforeTest
    fun setUp() {
        // Given - configure in-memory KMS provider
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "$PROPERTY_PREFIX.$TEST_PROVIDER_ID.type" to "software",
                "$PROPERTY_PREFIX.$TEST_PROVIDER_ID.id" to TEST_PROVIDER_ID,
                "$PROPERTY_PREFIX.$TEST_PROVIDER_ID.persistKeysDuringGeneration" to "true",
                "$PROPERTY_PREFIX.$TEST_PROVIDER_ID.exposePrivateKeysDuringGeneration" to "true",
                "$PROPERTY_PREFIX.$TEST_PROVIDER_ID.keyStore.type" to "memory",
                "$PROPERTY_PREFIX.$TEST_PROVIDER_ID.keyStore.id" to "test-registry-keystore",
                "$PROPERTY_PREFIX.$TEST_PROVIDER_ID.keyStore.keyVisibility" to "private",
                "$PROPERTY_PREFIX.$TEST_PROVIDER_ID.keyStore.overwriteAlias" to "true",
            ),
        )

        val app =
            createTestApiAppGraph(
                application = this,
                appId = "kms-registry-app",
                profile = "profile",
                version = "1.0.0-test",
            )

        val context = app.userContextManager.getAnonymous()
        val session = context.sessionContextManager.createOrGetFromId("registry-integration-test")

        // Access the command registry from the session graph via the @ContributesTo interface
        registry = (session.graph as TestRegistryGraph).sessionScopedCommandRegistry

        // Build command map from registry (which lazily creates commands from descriptors)
        val kmsCommandIds =
            listOf(
                GetKeyServiceCommand.COMMAND_ID,
                ListKeysServiceCommand.COMMAND_ID,
                StoreKeyServiceCommand.COMMAND_ID,
                GenerateKeyServiceCommand.COMMAND_ID,
                DeleteKeyServiceCommand.COMMAND_ID,
            )
        @Suppress("UNCHECKED_CAST")
        commandMap =
            kmsCommandIds
                .mapNotNull { id -> registry.get(id)?.let { id to it as ServiceCommand<Any, Any> } }
                .toMap()
    }

    // ==================== Descriptor Discovery Tests ====================

    @Test
    fun allFiveKmsCommandsAreRegistered() {
        // Then - all 5 KMS commands should be resolvable via the registry
        val kmsCommandIds =
            listOf(
                GetKeyServiceCommand.COMMAND_ID,
                ListKeysServiceCommand.COMMAND_ID,
                StoreKeyServiceCommand.COMMAND_ID,
                GenerateKeyServiceCommand.COMMAND_ID,
                DeleteKeyServiceCommand.COMMAND_ID,
            )

        for (commandId in kmsCommandIds) {
            assertNotNull(
                registry.get(commandId),
                "Command '$commandId' should be resolvable via the registry",
            )
        }
    }

    @Test
    fun commandsAreResolvableViaRegistry() {
        // Then - each command can be resolved via the registry
        assertNotNull(registry.get(GetKeyServiceCommand.COMMAND_ID), "GetKey should be resolvable via registry")
        assertNotNull(registry.get(ListKeysServiceCommand.COMMAND_ID), "ListKeys should be resolvable via registry")
        assertNotNull(registry.get(StoreKeyServiceCommand.COMMAND_ID), "StoreKey should be resolvable via registry")
        assertNotNull(registry.get(GenerateKeyServiceCommand.COMMAND_ID), "GenerateKey should be resolvable via registry")
        assertNotNull(registry.get(DeleteKeyServiceCommand.COMMAND_ID), "DeleteKey should be resolvable via registry")
    }

    // ==================== Execute Tests (registry execution path) ====================

    @Test
    fun generateKeyViaExecute() =
        runTest {
            // Given - look up the command by ID (same path as LocalServiceCommandTransport)
            val command = commandMap[GenerateKeyServiceCommand.COMMAND_ID]
            assertNotNull(command, "GenerateKey must be resolvable via registry")

            val input =
                GenerateKeyInput(
                    generateKey =
                        GenerateKeyGlobal(
                            alias = "registry-generate-${System.currentTimeMillis()}",
                            providerId = TEST_PROVIDER_ID,
                            use = JwkUse.sig,
                            alg = SignatureAlgorithmRest.ECDSA_SHA256,
                        ),
                )

            // When - execute through the registry path (same as LocalServiceCommandTransport uses)
            val result = command.execute(input)

            // Then
            assertTrue(
                result.isOk,
                "GenerateKey via execute should succeed: ${if (result.isErr) {
                    result.error
                } else {
                    ""
                }}"
            )
        }

    @Test
    fun getKeyViaExecute() =
        runTest {
            // Given - first generate a key
            val alias = "registry-get-${System.currentTimeMillis()}"
            val generateCommand = commandMap.getValue(GenerateKeyServiceCommand.COMMAND_ID)
            generateCommand.execute(
                GenerateKeyInput(
                    generateKey = GenerateKeyGlobal(alias = alias, providerId = TEST_PROVIDER_ID),
                ),
            )

            // When - get the key via registry path
            val getCommand = commandMap.getValue(GetKeyServiceCommand.COMMAND_ID)
            val result =
                getCommand.execute(
                    GetKeyInput(aliasOrKid = alias, providerId = TEST_PROVIDER_ID),
                )

            // Then
            assertTrue(
                result.isOk,
                "GetKey via execute should succeed: ${if (result.isErr) {
                    result.error
                } else {
                    ""
                }}"
            )
        }

    @Test
    fun listKeysViaExecute() =
        runTest {
            // Given - generate a key first
            val generateCommand = commandMap.getValue(GenerateKeyServiceCommand.COMMAND_ID)
            generateCommand.execute(
                GenerateKeyInput(
                    generateKey =
                        GenerateKeyGlobal(
                            alias = "registry-list-${System.currentTimeMillis()}",
                            providerId = TEST_PROVIDER_ID,
                        ),
                ),
            )

            // When - list keys via registry path
            val listCommand = commandMap.getValue(ListKeysServiceCommand.COMMAND_ID)
            val result =
                listCommand.execute(
                    ListKeysInput(providerId = TEST_PROVIDER_ID),
                )

            // Then
            assertTrue(
                result.isOk,
                "ListKeys via execute should succeed: ${if (result.isErr) {
                    result.error
                } else {
                    ""
                }}"
            )
        }

    @Test
    fun deleteKeyViaExecute() =
        runTest {
            // Given - generate a key first
            val alias = "registry-delete-${System.currentTimeMillis()}"
            val generateCommand = commandMap.getValue(GenerateKeyServiceCommand.COMMAND_ID)
            generateCommand.execute(
                GenerateKeyInput(
                    generateKey = GenerateKeyGlobal(alias = alias, providerId = TEST_PROVIDER_ID),
                ),
            )

            // When - delete via registry path
            val deleteCommand = commandMap.getValue(DeleteKeyServiceCommand.COMMAND_ID)
            val result =
                deleteCommand.execute(
                    DeleteKeyInput(aliasOrKid = alias, providerId = TEST_PROVIDER_ID),
                )

            // Then
            assertTrue(
                result.isOk,
                "DeleteKey via execute should succeed: ${if (result.isErr) {
                    result.error
                } else {
                    ""
                }}"
            )
        }

    @Test
    fun getNonExistentKeyViaExecuteReturnsError() =
        runTest {
            // Given
            val getCommand = commandMap.getValue(GetKeyServiceCommand.COMMAND_ID)

            // When
            val result =
                getCommand.execute(
                    GetKeyInput(aliasOrKid = "nonexistent-${System.currentTimeMillis()}", providerId = TEST_PROVIDER_ID),
                )

            // Then
            assertTrue(result.isErr, "GetKey for nonexistent key should return error")
        }

    // ==================== Command identity verification ====================

    @Test
    fun registeredCommandsHaveCorrectCommandIds() {
        // Then - commands resolved via registry have the expected commandId values
        assertEquals("kms.keys.get", commandMap[GetKeyServiceCommand.COMMAND_ID]?.commandId)
        assertEquals("kms.keys.list", commandMap[ListKeysServiceCommand.COMMAND_ID]?.commandId)
        assertEquals("kms.keys.store", commandMap[StoreKeyServiceCommand.COMMAND_ID]?.commandId)
        assertEquals("kms.keys.generate", commandMap[GenerateKeyServiceCommand.COMMAND_ID]?.commandId)
        assertEquals("kms.keys.delete", commandMap[DeleteKeyServiceCommand.COMMAND_ID]?.commandId)
    }

    companion object {
        private const val TEST_PROVIDER_ID = "testsoftwarekms"
        private const val PROPERTY_PREFIX = "kms.providers"
    }
}
