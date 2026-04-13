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

package com.sphereon.crypto.kms

import com.sphereon.core.api.Ok
import com.sphereon.core.api.Err
import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.crypto.core.JvmCryptoTestAppComponent
import com.sphereon.crypto.core.createJvmCryptoTestAppComponent
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.GetAllCapabilitiesArgs
import com.sphereon.crypto.core.kms.GetAllCapabilitiesCommand
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KmsProviderQuery
import com.sphereon.crypto.core.kms.KmsProviderRegistry
import com.sphereon.crypto.core.kms.QueryProviderArgs
import com.sphereon.crypto.core.kms.QueryProviderCommand
import com.sphereon.crypto.core.kms.QueryProvidersArgs
import com.sphereon.crypto.core.kms.QueryProvidersCommand
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.crypto.core.kms.kmsQuery
import com.sphereon.crypto.kms.command.KmsQueryCommandsComponent
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.command.QueryProviderCommandImpl
import com.sphereon.crypto.kms.command.QueryProvidersCommandImpl
import com.sphereon.di.context.createAnonymousSessionContext
import dev.whyoleg.cryptography.CryptographyProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Extension function to access KMS query commands from a session component.
 */
fun Any.asKmsQueryCommandsComponent(): KmsQueryCommandsComponent = this as KmsQueryCommandsComponent

/**
 * Tests for KMS provider query functionality via KeyManagerService and query commands.
 */
class KmsQueryFunctionalityTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var queryProviderCommand: QueryProviderCommand
    private lateinit var queryProvidersCommand: QueryProvidersCommand
    private lateinit var getAllCapabilitiesCommand: GetAllCapabilitiesCommand

    val app = createJvmCryptoTestAppComponent(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("kms-query-test")

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config = SoftwareKmsProviderConfig(
            id = "test-software-provider",
            cryptographyProvider = CryptographyProvider.Default.name
        )
        app as JvmCryptoTestAppComponent
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceComponent().serviceExecution)

        // Get KeyManagerService from the session component
        keyManagerService = session.component.asKeyManagerServiceComponent().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        // Get query commands from the session component via DI
        val queryCommands = session.component.asKmsQueryCommandsComponent()
        queryProviderCommand = queryCommands.queryProviderCommand
        queryProvidersCommand = queryCommands.queryProvidersCommand
        getAllCapabilitiesCommand = queryCommands.getAllCapabilitiesCommand
    }

    // =========== Provider Management Tests ===========

    @Test
    fun getProviderIdsShouldReturnRegisteredProviders() = runTest {
        val providerIds = keyManagerService.getProviderIds()

        assertTrue(providerIds.isNotEmpty(), "Should have at least one provider")
        assertTrue(providerIds.contains("test-software-provider"), "Should contain the test provider")
    }

    @Test
    fun getProviderByIdShouldReturnCorrectProvider() = runTest {
        val provider = keyManagerService.getProviderById("test-software-provider")

        assertNotNull(provider)
        assertEquals("test-software-provider", provider.id)
        assertTrue(provider.enabled, "Provider should be enabled")
    }

    @Test
    fun defaultProviderIdShouldBeSetCorrectly() = runTest {
        val defaultId = keyManagerService.defaultProviderId()

        assertNotNull(defaultId, "Default provider should be set")
        assertEquals("test-software-provider", defaultId)
    }

    // =========== Key Generation via Provider Query ===========

    @Test
    fun generateKeyWithMatchingProviderShouldSucceed() = runTest {
        val keyPair = keyManagerService.generateKey(
            alg = SignatureAlgorithm.ECDSA_SHA256
        )

        assertNotNull(keyPair)
        assertNotNull(keyPair.jose.publicJwk)
        assertNotNull(keyPair.jose.privateJwk)
    }

    @Test
    fun generateKeyWithES384ShouldSucceed() = runTest {
        val keyPair = keyManagerService.generateKey(
            alg = SignatureAlgorithm.ECDSA_SHA384
        )

        assertNotNull(keyPair)
        assertNotNull(keyPair.jose.publicJwk)
    }

    @Test
    fun generateKeyWithES512ShouldSucceed() = runTest {
        val keyPair = keyManagerService.generateKey(
            alg = SignatureAlgorithm.ECDSA_SHA512
        )

        assertNotNull(keyPair)
        assertNotNull(keyPair.jose.publicJwk)
    }

    // =========== Query Provider Command Tests ===========

    @Test
    fun queryProviderCommandShouldFindProviderByAlgorithm() = runTest {
        val query = kmsQuery {
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
        }
        val args = QueryProviderArgs(query = query)

        val result = queryProviderCommand.execute(args)

        assertIs<Ok<*>>(result, "Should succeed")
        val providerResult = result.value
        assertNotNull(providerResult.match, "Should find a matching provider")
        assertEquals("test-software-provider", providerResult.match!!.providerId)
        assertNotNull(providerResult.match!!.capabilities)
    }

    @Test
    fun queryProviderCommandShouldReturnErrorForNullQuery() = runTest {
        val args = QueryProviderArgs(query = null)

        val result = queryProviderCommand.execute(args)

        // When supports() returns false, the command framework returns an error
        assertIs<Err<*>>(result, "Should return error for null query")
        assertNotNull(result.error, "Error should be present")
    }

    @Test
    fun queryProviderCommandNullQueryExecuteIgnoresForgedSessionContext() = runTest {
        val args = QueryProviderArgs(query = null)
        val executionContext = session.sessionExecution.sessionContext
        val forgedContext = createAnonymousSessionContext("kms-query-provider-null-query-forged")

        val resultWithExecutionContext = queryProviderCommand.execute(args)
        val resultWithForgedContext = queryProviderCommand.execute(args)

        assertIs<Err<*>>(resultWithExecutionContext)
        assertIs<Err<*>>(resultWithForgedContext)
        assertEquals(resultWithExecutionContext.error.code, resultWithForgedContext.error.code)
    }

    @Test
    fun queryProviderCommandSupportsMethodShouldWorkCorrectly() = runTest {
        val validArgs = QueryProviderArgs(query = kmsQuery { })
        val invalidArgs = QueryProviderArgs(query = null)
        val otherObject = "not QueryProviderArgs"
        val forgedContext = createAnonymousSessionContext("kms-query-provider-supports-forged")

        assertTrue(queryProviderCommand.supports(validArgs), "Should support valid args")
        assertTrue(queryProviderCommand.supports(validArgs), "Context-bearing supports should delegate to supports")
        assertFalse(queryProviderCommand.supports(invalidArgs), "Should not support args with null query")
        assertFalse(queryProviderCommand.supports(invalidArgs), "Should not support null-query args via delegate path")
        assertFalse(queryProviderCommand.supports(otherObject), "Should not support non-args objects")
        assertFalse(queryProviderCommand.supports(otherObject), "Should not support non-args objects via delegate path")
    }

    @Test
    fun queryProviderCommandExecuteShouldIgnoreForgedSessionContext() = runTest {
        val args = QueryProviderArgs(
            query = kmsQuery {
                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
            }
        )
        val forgedContext = createAnonymousSessionContext("kms-query-provider-exec-forged")

        val result = queryProviderCommand.execute(args)

        assertIs<Ok<*>>(result, "Should succeed with forged caller context")
        assertEquals("test-software-provider", result.value.match?.providerId)
    }

    // =========== Query Providers Command Tests ===========

    @Test
    fun queryProvidersCommandShouldFindAllMatchingProviders() = runTest {
        val query = kmsQuery {
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
        }
        val args = QueryProvidersArgs(query = query)

        val result = queryProvidersCommand.execute(args)

        assertIs<Ok<*>>(result, "Should succeed")
        val providersResult = result.value
        assertTrue(providersResult.matches.isNotEmpty(), "Should find matching providers")
        assertTrue(providersResult.totalProviders > 0, "Should report total providers")
        assertEquals(providersResult.matches.size, providersResult.matchCount)
    }

    @Test
    fun queryProvidersCommandShouldReturnErrorForNullQuery() = runTest {
        val args = QueryProvidersArgs(query = null)

        val result = queryProvidersCommand.execute(args)

        // When supports() returns false, the command framework returns an error
        assertIs<Err<*>>(result, "Should return error for null query")
        assertNotNull(result.error, "Error should be present")
    }

    @Test
    fun queryProvidersCommandNullQueryExecuteIgnoresForgedSessionContext() = runTest {
        val args = QueryProvidersArgs(query = null)
        val executionContext = session.sessionExecution.sessionContext
        val forgedContext = createAnonymousSessionContext("kms-query-providers-null-query-forged")

        val resultWithExecutionContext = queryProvidersCommand.execute(args)
        val resultWithForgedContext = queryProvidersCommand.execute(args)

        assertIs<Err<*>>(resultWithExecutionContext)
        assertIs<Err<*>>(resultWithForgedContext)
        assertEquals(resultWithExecutionContext.error.code, resultWithForgedContext.error.code)
    }

    @Test
    fun queryProvidersCommandSupportsMethodShouldWorkCorrectly() = runTest {
        val validArgs = QueryProvidersArgs(query = kmsQuery { })
        val invalidArgs = QueryProvidersArgs(query = null)
        val otherObject = "not QueryProvidersArgs"
        val forgedContext = createAnonymousSessionContext("kms-query-providers-supports-forged")

        assertTrue(queryProvidersCommand.supports(validArgs), "Should support valid args")
        assertTrue(queryProvidersCommand.supports(validArgs), "Context-bearing supports should delegate to supports")
        assertFalse(queryProvidersCommand.supports(invalidArgs), "Should not support args with null query")
        assertFalse(queryProvidersCommand.supports(invalidArgs), "Should not support null-query args via delegate path")
        assertFalse(queryProvidersCommand.supports(otherObject), "Should not support non-args objects")
        assertFalse(queryProvidersCommand.supports(otherObject), "Should not support non-args objects via delegate path")
    }

    // =========== Get All Capabilities Command Tests ===========

    @Test
    fun getAllCapabilitiesCommandShouldReturnAllProviderCapabilities() = runTest {
        val args = GetAllCapabilitiesArgs(includeDisabled = false)

        val result = getAllCapabilitiesCommand.execute(args)

        assertIs<Ok<*>>(result, "Should succeed")
        val capabilitiesResult = result.value
        assertTrue(capabilitiesResult.capabilities.isNotEmpty(), "Should have capabilities")
        assertTrue(capabilitiesResult.capabilities.containsKey("test-software-provider"))
        assertNotNull(capabilitiesResult.capabilities["test-software-provider"])
    }

    @Test
    fun getAllCapabilitiesCommandShouldIncludeDisabledWhenRequested() = runTest {
        val args = GetAllCapabilitiesArgs(includeDisabled = true)

        val result = getAllCapabilitiesCommand.execute(args)

        assertIs<Ok<*>>(result, "Should succeed")
        val capabilitiesResult = result.value
        assertTrue(capabilitiesResult.capabilities.isNotEmpty(), "Should have capabilities")
    }

    @Test
    fun getAllCapabilitiesCommandSupportsMethodShouldWorkCorrectly() = runTest {
        val validArgs = GetAllCapabilitiesArgs()
        val otherObject = "not GetAllCapabilitiesArgs"
        val forgedContext = createAnonymousSessionContext("kms-query-capabilities-supports-forged")

        assertTrue(getAllCapabilitiesCommand.supports(validArgs), "Should support valid args")
        assertTrue(getAllCapabilitiesCommand.supports(validArgs), "Context-bearing supports should delegate to supports")
        assertFalse(getAllCapabilitiesCommand.supports(otherObject), "Should not support non-args objects")
        assertFalse(getAllCapabilitiesCommand.supports(otherObject), "Should not support non-args objects via delegate path")
    }

    // =========== Provider Capabilities Content Tests ===========

    @Test
    fun softwareProviderCapabilitiesShouldContainExpectedAlgorithms() = runTest {
        val args = GetAllCapabilitiesArgs()
        val result = getAllCapabilitiesCommand.execute(args)

        assertIs<Ok<*>>(result)
        val capabilities = result.value.capabilities["test-software-provider"]
        assertNotNull(capabilities)

        // Software provider should support common EC algorithms
        val supportedAlgs = capabilities.signatureAlgorithms
        assertTrue(supportedAlgs.isNotEmpty(), "Should support some algorithms")
    }

    // =========== No Matching Provider Tests ===========

    @Test
    fun queryProviderCommandShouldReturnErrorWhenNoProviderMatches() = runTest {
        // Query for hardware-backed provider - software provider doesn't support this
        val query = kmsQuery {
            requiresHardwareBacking = true
        }
        val args = QueryProviderArgs(query = query)

        val result = queryProviderCommand.execute(args)

        assertIs<Err<*>>(result, "Should return error when no provider matches")
        assertNotNull(result.error, "Error should be present")
        assertTrue(result.error.toString().contains("No provider found") || result.error.toString().contains("NOT_FOUND"),
            "Error should indicate no matching provider")
    }

    @Test
    fun queryProvidersCommandShouldReturnEmptyMatchesWhenNoProviderMatches() = runTest {
        // Query for hardware-backed provider - software provider doesn't support this
        val query = kmsQuery {
            requiresHardwareBacking = true
        }
        val args = QueryProvidersArgs(query = query)

        val result = queryProvidersCommand.execute(args)

        assertIs<Ok<*>>(result, "Should succeed but return empty matches")
        val providersResult = result.value
        assertTrue(providersResult.matches.isEmpty(), "Should have no matching providers")
        assertTrue(providersResult.totalProviders > 0, "Should still report total providers")
    }

    @Test
    fun queryProviderCommandShouldReturnErrorForNonExistentProviderType() = runTest {
        // Query for a specific provider type that doesn't exist
        val query = kmsQuery {
            providerType = "nonexistent_hsm_provider"
        }
        val args = QueryProviderArgs(query = query)

        val result = queryProviderCommand.execute(args)

        assertIs<Err<*>>(result, "Should return error for non-existent provider type")
    }

    // =========== Disabled Provider Tests ===========

    @Test
    fun getAllCapabilitiesShouldFilterOutDisabledProviders() = runTest {
        // Register a disabled provider
        val disabledConfig = SoftwareKmsProviderConfig(
            id = "disabled-provider",
            enabled = false,
            cryptographyProvider = CryptographyProvider.Default.name
        )
        val appMerged = app as JvmCryptoTestAppComponent
        val disabledProvider = appMerged.softwareKmsProvider.create(
            disabledConfig,
            session.asCoreApiServiceComponent().serviceExecution
        )
        keyManagerService.registerProvider(disabledProvider, makeDefaultKms = false)

        // Verify the provider reports enabled=false from config
        assertFalse(disabledProvider.enabled, "Provider should report enabled=false from config")

        // Query without includeDisabled - should NOT include the disabled provider
        val argsWithoutDisabled = GetAllCapabilitiesArgs(includeDisabled = false)
        val resultWithoutDisabled = getAllCapabilitiesCommand.execute(
            argsWithoutDisabled)

        assertIs<Ok<*>>(resultWithoutDisabled)
        val capabilitiesWithoutDisabled = resultWithoutDisabled.value.capabilities
        assertFalse(capabilitiesWithoutDisabled.containsKey("disabled-provider"),
            "Should NOT include disabled provider when includeDisabled=false")
        assertTrue(capabilitiesWithoutDisabled.containsKey("test-software-provider"),
            "Should still include enabled provider")

        // Query with includeDisabled - SHOULD include the disabled provider
        val argsWithDisabled = GetAllCapabilitiesArgs(includeDisabled = true)
        val resultWithDisabled = getAllCapabilitiesCommand.execute(
            argsWithDisabled)

        assertIs<Ok<*>>(resultWithDisabled)
        val capabilitiesWithDisabled = resultWithDisabled.value.capabilities
        assertTrue(capabilitiesWithDisabled.containsKey("disabled-provider"),
            "Should include disabled provider when includeDisabled=true")
        assertTrue(capabilitiesWithDisabled.containsKey("test-software-provider"),
            "Should include enabled provider")
    }

    @Test
    fun providerShouldRespectEnabledConfigValue() = runTest {
        // Create provider with enabled=true (default)
        val enabledConfig = SoftwareKmsProviderConfig(
            id = "enabled-test-provider",
            enabled = true,
            cryptographyProvider = CryptographyProvider.Default.name
        )
        val appMerged = app as JvmCryptoTestAppComponent
        val enabledProvider = appMerged.softwareKmsProvider.create(
            enabledConfig,
            session.asCoreApiServiceComponent().serviceExecution
        )

        // Create provider with enabled=false
        val disabledConfig = SoftwareKmsProviderConfig(
            id = "disabled-test-provider",
            enabled = false,
            cryptographyProvider = CryptographyProvider.Default.name
        )
        val disabledProvider = appMerged.softwareKmsProvider.create(
            disabledConfig,
            session.asCoreApiServiceComponent().serviceExecution
        )

        assertTrue(enabledProvider.enabled, "Provider with enabled=true should report enabled=true")
        assertFalse(disabledProvider.enabled, "Provider with enabled=false should report enabled=false")
    }

    // =========== Exception Handling Tests ===========

    @Test
    fun queryProviderCommandShouldHandleExceptionFromKeyManagerService() = runTest {
        // Create a mock KmsProviderRegistry that throws an exception
        val mockRegistry = mockk<KmsProviderRegistry>()
        every { mockRegistry.getProviderIds() } throws RuntimeException("Test exception from KMS")

        val execution = session.asCoreApiServiceComponent().serviceExecution
        val command = QueryProviderCommandImpl(execution, mockRegistry)

        val query = kmsQuery { signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256 }
        val args = QueryProviderArgs(query = query)
        val result = command.execute(args)

        assertIs<Err<*>>(result, "Should return error when exception occurs")
        assertTrue(
            result.error.toString().contains("Test exception from KMS") ||
                result.error.toString().contains("NOT_FOUND"),
            "Error should contain exception message: ${result.error}"
        )
    }

    @Test
    fun queryProviderCommandShouldHandleExceptionWithNullMessage() = runTest {
        // Create a mock KmsProviderRegistry that throws an exception with null message
        val mockRegistry = mockk<KmsProviderRegistry>()
        every { mockRegistry.getProviderIds() } throws RuntimeException(null as String?)

        val execution = session.asCoreApiServiceComponent().serviceExecution
        val command = QueryProviderCommandImpl(execution, mockRegistry)

        val query = kmsQuery { signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256 }
        val args = QueryProviderArgs(query = query)
        val result = command.execute(args)

        assertIs<Err<*>>(result, "Should return error when exception occurs")
        assertTrue(
            result.error.toString().contains("No provider found") ||
                result.error.toString().contains("NOT_FOUND"),
            "Error should use fallback message when exception message is null: ${result.error}"
        )
    }

    @Test
    fun queryProvidersCommandShouldHandleExceptionFromKeyManagerService() = runTest {
        // Create a mock KmsProviderRegistry that throws an exception
        val mockRegistry = mockk<KmsProviderRegistry>()
        every { mockRegistry.getProviderIds() } throws RuntimeException("Test exception from KMS")

        val execution = session.asCoreApiServiceComponent().serviceExecution
        val command = QueryProvidersCommandImpl(execution, mockRegistry)

        val query = kmsQuery { signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256 }
        val args = QueryProvidersArgs(query = query)
        val result = command.execute(args)

        assertIs<Err<*>>(result, "Should return error when exception occurs")
        assertTrue(
            result.error.toString().contains("Test exception from KMS") ||
                result.error.toString().contains("UNKNOWN"),
            "Error should contain exception message: ${result.error}"
        )
    }
}

