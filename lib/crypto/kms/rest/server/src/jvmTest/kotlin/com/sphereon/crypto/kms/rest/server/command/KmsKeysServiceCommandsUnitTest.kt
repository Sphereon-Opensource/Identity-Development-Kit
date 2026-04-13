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
 */

package com.sphereon.crypto.kms.rest.server.command

import com.sphereon.crypto.kms.rest.server.createTestApiAppComponent
import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyInput
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GenerateKeyInput
import com.sphereon.crypto.kms.rest.api.command.GenerateKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GetKeyInput
import com.sphereon.crypto.kms.rest.api.command.GetKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ListKeysInput
import com.sphereon.crypto.kms.rest.api.command.ListKeysServiceCommand
import com.sphereon.crypto.kms.rest.api.command.StoreKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.facade.KmsKeysServiceFacade
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyGlobal
import com.sphereon.crypto.kms.rest.api.generated.models.JwkUse
import com.sphereon.crypto.kms.rest.api.generated.models.SignatureAlgorithm as SignatureAlgorithmRest
import com.sphereon.crypto.kms.rest.server.TestApiAppComponent
import com.sphereon.crypto.kms.rest.server.facade.KmsKeysServiceFacadeImpl
import com.sphereon.di.session.SessionContext
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for KMS Keys ServiceCommands using proper DI with in-memory software KMS provider.
 *
 * These tests verify that the ServiceCommand implementations work correctly at the server level,
 * testing the command logic directly without HTTP transport overhead.
 *
 * For full transport E2E tests (client -> HTTP -> server -> command), see [KmsServiceCommandsTransportE2ETest].
 *
 * The KMS manager automatically reads config from properties and injects the configured providers.
 */
class KmsKeysServiceCommandsUnitTest {

    private lateinit var facade: KmsKeysServiceFacade
    private lateinit var sessionContext: SessionContext
    private lateinit var getKeyCommand: GetKeyServiceCommand
    private lateinit var listKeysCommand: ListKeysServiceCommand
    private lateinit var storeKeyCommand: StoreKeyServiceCommand
    private lateinit var generateKeyCommand: GenerateKeyServiceCommand
    private lateinit var deleteKeyCommand: DeleteKeyServiceCommand

    companion object {
        private const val TEST_PROVIDER_ID = "testsoftwarekms"
        // Property prefix pattern: {normalizedAppId}.app.{profile}.kms.providers.{providerId}
        // For appId="kms-commands-app" and profile="profile", prefix is "kms.commands.app.profile"
        private const val PROPERTY_PREFIX = "kms.commands.app.profile.kms.providers"
    }

    @BeforeTest
    fun setUp() {
        // Configure in-memory software KMS provider via properties
        // The KMS manager automatically reads this config and injects the providers
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "$PROPERTY_PREFIX.$TEST_PROVIDER_ID.type" to "software",
                "$PROPERTY_PREFIX.$TEST_PROVIDER_ID.id" to TEST_PROVIDER_ID,
                "$PROPERTY_PREFIX.$TEST_PROVIDER_ID.persistKeysDuringGeneration" to "true",
                "$PROPERTY_PREFIX.$TEST_PROVIDER_ID.exposePrivateKeysDuringGeneration" to "true",
                "$PROPERTY_PREFIX.$TEST_PROVIDER_ID.keyStore.type" to "memory",
                "$PROPERTY_PREFIX.$TEST_PROVIDER_ID.keyStore.id" to "test-memory-keystore",
                "$PROPERTY_PREFIX.$TEST_PROVIDER_ID.keyStore.keyVisibility" to "private",
                "$PROPERTY_PREFIX.$TEST_PROVIDER_ID.keyStore.overwriteAlias" to "true"
            )
        )

        // Initialize app component with DI
        // appId determines the property prefix (normalized: "kms-commands-app" -> "kms.commands")
        val app = createTestApiAppComponent(
            application = this,
            appId = "kms-commands-app",
            profile = "profile",
            version = "1.0.0-test"
        )

        // Get user context and session
        val context = app.userContextManager.getAnonymous()
        val session = context.sessionContextManager.createOrGetFromId("service-commands-e2e-test")
        sessionContext = session.sessionContext

        // Get the facade and commands from session component via ContributesTo interface
        val sessionComponent = session.component as KmsKeysServiceFacadeImpl.Component
        facade = sessionComponent.kmsKeysServiceFacade

        // Get individual commands via SessionScopedCommandRegistry lookup.
        // KmsCommandDescriptors registers them as RegistrableServiceCommandDescriptor multibinding entries.
        val registry = (session.component as KmsCommandRegistryIntegrationTest.TestRegistryComponent).sessionScopedCommandRegistry
        getKeyCommand = registry.get(GetKeyServiceCommand.COMMAND_ID) as GetKeyServiceCommand
        listKeysCommand = registry.get(ListKeysServiceCommand.COMMAND_ID) as ListKeysServiceCommand
        storeKeyCommand = registry.get(StoreKeyServiceCommand.COMMAND_ID) as StoreKeyServiceCommand
        generateKeyCommand = registry.get(GenerateKeyServiceCommand.COMMAND_ID) as GenerateKeyServiceCommand
        deleteKeyCommand = registry.get(DeleteKeyServiceCommand.COMMAND_ID) as DeleteKeyServiceCommand
    }

    // ==================== Facade Tests ====================

    @Test
    fun testGenerateKeyViaFacade() = runTest {
        val result = facade.generateKey(
            GenerateKeyGlobal(
                alias = "facade-test-key-${System.currentTimeMillis()}",
                providerId = TEST_PROVIDER_ID,
                use = JwkUse.sig,
                alg = SignatureAlgorithmRest.ECDSA_SHA256
            )
        )

        assertTrue(result.isOk, "Generate key should succeed, but got: ${if (result.isErr) result.error else "N/A"}")
        val response = result.value
        assertNotNull(response.keyPair, "Key pair should not be null")
        assertNotNull(response.keyPair.jose, "Public key should not be null")
    }

    @Test
    fun testListKeysViaFacade() = runTest {
        // First generate a key
        val generateResult = facade.generateKey(
            GenerateKeyGlobal(
                alias = "list-test-key-${System.currentTimeMillis()}",
                providerId = TEST_PROVIDER_ID
            )
        )
        assertTrue(generateResult.isOk, "Generate should succeed first")

        // Then list keys
        val listResult = facade.listKeys(providerId = TEST_PROVIDER_ID)

        assertTrue(listResult.isOk, "List keys should succeed, but got: ${if (listResult.isErr) listResult.error else "N/A"}")
        val response = listResult.value
        assertNotNull(response.keyInfos, "Key infos should not be null")
        assertTrue(response.keyInfos.isNotEmpty(), "Should have at least one key")
    }

    @Test
    fun testGetKeyViaFacade() = runTest {
        // First generate a key with known alias
        val alias = "get-test-key-${System.currentTimeMillis()}"
        val generateResult = facade.generateKey(
            GenerateKeyGlobal(
                alias = alias,
                providerId = TEST_PROVIDER_ID
            )
        )
        assertTrue(generateResult.isOk, "Generate should succeed first")

        // Then get the key
        val getResult = facade.getKey(aliasOrKid = alias, providerId = TEST_PROVIDER_ID)

        assertTrue(getResult.isOk, "Get key should succeed, but got: ${if (getResult.isErr) getResult.error else "N/A"}")
        val response = getResult.value
        assertNotNull(response.keyInfo, "Key info should not be null")
    }

    @Test
    fun testDeleteKeyViaFacade() = runTest {
        // First generate a key
        val alias = "delete-test-key-${System.currentTimeMillis()}"
        val generateResult = facade.generateKey(
            GenerateKeyGlobal(
                alias = alias,
                providerId = TEST_PROVIDER_ID
            )
        )
        assertTrue(generateResult.isOk, "Generate should succeed first")

        // Then delete it
        val deleteResult = facade.deleteKey(aliasOrKid = alias, providerId = TEST_PROVIDER_ID)

        assertTrue(deleteResult.isOk, "Delete key should succeed, but got: ${if (deleteResult.isErr) deleteResult.error else "N/A"}")
        val response = deleteResult.value
        assertTrue(response.deleted, "Key should be marked as deleted")
        assertEquals(alias, response.aliasOrKid, "Alias should match")
    }

    // ==================== Direct ServiceCommand Tests ====================

    @Test
    fun testGenerateKeyServiceCommandDirectly() = runTest {
        val input = GenerateKeyInput(
            generateKey = GenerateKeyGlobal(
                alias = "direct-generate-test-${System.currentTimeMillis()}",
                providerId = TEST_PROVIDER_ID,
                use = JwkUse.sig,
                alg = SignatureAlgorithmRest.ECDSA_SHA256
            )
        )

        val result = generateKeyCommand.execute(input)

        assertTrue(result.isOk, "Direct generate command should succeed")
        assertNotNull(result.value.keyPair)
    }

    @Test
    fun testListKeysServiceCommandDirectly() = runTest {
        // Generate a key first to ensure there's something to list
        facade.generateKey(
            GenerateKeyGlobal(
                alias = "direct-list-test-${System.currentTimeMillis()}",
                providerId = TEST_PROVIDER_ID
            )
        )

        val input = ListKeysInput(providerId = TEST_PROVIDER_ID)
        val result = listKeysCommand.execute(input)

        assertTrue(result.isOk, "Direct list command should succeed")
        assertTrue(result.value.keyInfos.isNotEmpty(), "Should have at least one key")
    }

    @Test
    fun testGetKeyServiceCommandDirectly() = runTest {
        // Generate a key first
        val alias = "direct-get-test-${System.currentTimeMillis()}"
        facade.generateKey(
            GenerateKeyGlobal(
                alias = alias,
                providerId = TEST_PROVIDER_ID
            )
        )

        val input = GetKeyInput(aliasOrKid = alias, providerId = TEST_PROVIDER_ID)
        val result = getKeyCommand.execute(input)

        assertTrue(result.isOk, "Direct get command should succeed")
        assertNotNull(result.value.keyInfo)
    }

    @Test
    fun testDeleteKeyServiceCommandDirectly() = runTest {
        // Generate a key first
        val alias = "direct-delete-test-${System.currentTimeMillis()}"
        facade.generateKey(
            GenerateKeyGlobal(
                alias = alias,
                providerId = TEST_PROVIDER_ID
            )
        )

        val input = DeleteKeyInput(aliasOrKid = alias, providerId = TEST_PROVIDER_ID)
        val result = deleteKeyCommand.execute(input)

        assertTrue(result.isOk, "Direct delete command should succeed")
        assertTrue(result.value.deleted)
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun testGetNonExistentKey() = runTest {
        val result = facade.getKey(
            aliasOrKid = "non-existent-key-${System.currentTimeMillis()}",
            providerId = TEST_PROVIDER_ID
        )

        assertTrue(result.isErr, "Getting non-existent key should fail")
    }

    @Test
    fun testDeleteNonExistentKey() = runTest {
        val result = facade.deleteKey(
            aliasOrKid = "non-existent-key-${System.currentTimeMillis()}",
            providerId = TEST_PROVIDER_ID
        )

        assertTrue(result.isErr, "Deleting non-existent key should fail")
    }

    // ==================== ServiceCommand Metadata Tests ====================

    @Test
    fun testGetKeyCommandMetadata() {
        assertEquals(GetKeyServiceCommand.COMMAND_ID, getKeyCommand.commandId)
        // PublicApiCommand httpEndpoint declares the public REST bindings
        assertEquals(HttpMethod.GET, getKeyCommand.httpEndpoint.method)
        assertEquals("/keys/{aliasOrKid}", getKeyCommand.httpEndpoint.pathPattern)
    }

    @Test
    fun testListKeysCommandMetadata() {
        assertEquals(ListKeysServiceCommand.COMMAND_ID, listKeysCommand.commandId)
        assertEquals(HttpMethod.GET, listKeysCommand.httpEndpoint.method)
        assertEquals("/keys", listKeysCommand.httpEndpoint.pathPattern)
    }

    @Test
    fun testGenerateKeyCommandMetadata() {
        assertEquals(GenerateKeyServiceCommand.COMMAND_ID, generateKeyCommand.commandId)
        assertEquals(HttpMethod.POST, generateKeyCommand.httpEndpoint.method)
        assertEquals("/keys/generate", generateKeyCommand.httpEndpoint.pathPattern)
    }

    @Test
    fun testDeleteKeyCommandMetadata() {
        assertEquals(DeleteKeyServiceCommand.COMMAND_ID, deleteKeyCommand.commandId)
        assertEquals(HttpMethod.DELETE, deleteKeyCommand.httpEndpoint.method)
        assertEquals("/keys/{aliasOrKid}", deleteKeyCommand.httpEndpoint.pathPattern)
    }
}
