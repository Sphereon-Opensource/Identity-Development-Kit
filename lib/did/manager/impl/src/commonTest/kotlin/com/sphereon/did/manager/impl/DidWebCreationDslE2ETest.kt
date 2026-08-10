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

package com.sphereon.did.manager.impl

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.did.manager.DidManager
import com.sphereon.did.manager.DidUpdateOptions
import com.sphereon.did.manager.impl.testutil.DidManagerTestContext
import com.sphereon.did.manager.impl.testutil.createDidManagerTestAppGraph
import com.sphereon.did.models.DidService
import com.sphereon.did.models.VerificationPurpose
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * E2E integration tests for did:web DID Creation DSL with full DI stack.
 *
 * These tests verify:
 * 1. Creating did:web with multiple verification methods
 * 2. Creating did:web with services
 * 3. Adding services after creation
 * 4. Updating services (remove and add)
 * 5. Removing services
 */
class DidWebCreationDslE2ETest {
    private lateinit var app: com.sphereon.di.app.AppGraph
    private lateinit var dslProcessor: DidCreationDslProcessor
    private lateinit var didManager: DidManager

    @AfterTest
    fun tearDown() {
        if (::app.isInitialized) {
            app.userContextManager.destroyAll()
        }
    }

    @BeforeTest
    fun setUp() {
        // Configure the software KMS provider via properties
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.softwaretest.type" to "software",
                "kms.providers.softwaretest.id" to "softwaretest",
                "kms.providers.softwaretest.keystore.type" to "memory",
                "kms.providers.softwaretest.keystore.id" to "test-memory-keystore",
                "kms.providers.softwaretest.keystore.keyVisibility" to "private",
                "kms.providers.softwaretest.keystore.overwriteAlias" to "true",
            ),
        )

        // Create the full graph hierarchy via DI
        app = createDidManagerTestAppGraph(testInstance = this)

        // Destroy any existing contexts to ensure config is re-read
        app.userContextManager.destroyAll()

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("did-web-dsl-e2e-test", principalType = com.sphereon.di.context.PrincipalType.USER)

        val sessionGraph = session.graph

        dslProcessor = (sessionGraph as DidCreationDslProcessorImpl.Graph).didCreationDslProcessor
        didManager = (sessionGraph as DidManagerServiceImpl.Graph).didManager
    }

    // ==================== did:web with Multiple Keys ====================

    @Test
    fun testCreateDidWebWithSingleVerificationMethod() =
        runTest {
            val result =
                dslProcessor.create {
                    method("web")
                    domain("example.com")
                    alias("test-single-key-did-web")

                    autoGenerateKey {
                        keyType(KeyTypeMapping.EC)
                        curve(Curve.P_256)
                        kmsProvider("softwaretest")
                        purposes(VerificationPurpose.AUTHENTICATION, VerificationPurpose.ASSERTION_METHOD)
                    }
                }

            assertTrue(result.isOk, "DID creation should succeed: ${result.getErrorOrNull()}")
            val managedDid = result.getOrThrow()

            // Verify DID format
            assertEquals("did:web:example.com", managedDid.did, "DID should be did:web:example.com")
            assertEquals("test-single-key-did-web", managedDid.alias, "Alias should match")

            // Verify verification methods
            val document = managedDid.document
            assertNotNull(document, "DID document should be present")
            val verificationMethods = document.verificationMethod
            assertNotNull(verificationMethods, "Verification methods should be present")
            assertEquals(1, verificationMethods.size, "Should have 1 verification method")

            // Verify purpose mappings
            val authentication = document.authentication
            assertNotNull(authentication, "Authentication should be present")
            assertEquals(1, authentication.size, "Should have 1 authentication reference")

            val assertionMethod = document.assertionMethod
            assertNotNull(assertionMethod, "Assertion method should be present")
            assertEquals(1, assertionMethod.size, "Should have 1 assertion method reference")

            // Verify key mappings are persisted
            assertEquals(1, managedDid.keys.size, "Should have 1 key mapping")

            // Verify persistence
            val persisted = didManager.get(managedDid.did).getOrThrow()
            assertNotNull(persisted, "DID should be retrievable")
            assertEquals(1, persisted.keys.size, "Persisted DID should have 1 key mapping")
        }

    @Test
    fun testCreateDidWebWithMultipleVerificationMethods() =
        runTest {
            val result =
                dslProcessor.create {
                    method("web")
                    domain("multi-key.example.com")
                    alias("test-multi-key-did-web")

                    // First key for authentication and assertion
                    verificationMethod("signing-key") {
                        autoGenerateKey {
                            keyType(KeyTypeMapping.EC)
                            curve(Curve.P_256)
                            kmsProvider("softwaretest")
                        }
                        purposes(VerificationPurpose.AUTHENTICATION, VerificationPurpose.ASSERTION_METHOD)
                    }

                    // Second key for key agreement (encryption)
                    verificationMethod("encryption-key") {
                        autoGenerateKey {
                            keyType(KeyTypeMapping.EC)
                            curve(Curve.P_384)
                            kmsProvider("softwaretest")
                        }
                        purposes(VerificationPurpose.KEY_AGREEMENT)
                    }
                }

            assertTrue(result.isOk, "DID creation should succeed: ${result.getErrorOrNull()}")
            val managedDid = result.getOrThrow()

            // Verify DID format
            assertEquals("did:web:multi-key.example.com", managedDid.did)
            assertEquals("test-multi-key-did-web", managedDid.alias)

            // Verify verification methods
            val document = managedDid.document
            assertNotNull(document, "DID document should be present")
            val verificationMethods = document.verificationMethod
            assertNotNull(verificationMethods, "Verification methods should be present")
            assertEquals(2, verificationMethods.size, "Should have 2 verification methods")

            // Verify the signing key
            val signingKey = verificationMethods.find { it.id.contains("signing-key") }
            assertNotNull(signingKey, "Signing key should be present")
            assertNotNull(signingKey.publicKeyJwk, "Signing key should have JWK")

            // Verify the encryption key
            val encryptionKey = verificationMethods.find { it.id.contains("encryption-key") }
            assertNotNull(encryptionKey, "Encryption key should be present")
            assertNotNull(encryptionKey.publicKeyJwk, "Encryption key should have JWK")

            // Verify purpose mappings
            val authentication = document.authentication
            assertNotNull(authentication, "Authentication should be present")
            assertEquals(1, authentication.size, "Should have 1 authentication reference (signing key)")

            val assertionMethod = document.assertionMethod
            assertNotNull(assertionMethod, "Assertion method should be present")
            assertEquals(1, assertionMethod.size, "Should have 1 assertion method reference (signing key)")

            val keyAgreement = document.keyAgreement
            assertNotNull(keyAgreement, "Key agreement should be present")
            assertEquals(1, keyAgreement.size, "Should have 1 key agreement reference (encryption key)")

            // Verify key mappings are persisted
            assertEquals(2, managedDid.keys.size, "Should have 2 key mappings")

            // Verify persistence
            val persisted = didManager.get(managedDid.did).getOrThrow()
            assertNotNull(persisted, "DID should be retrievable")
            assertEquals(2, persisted.keys.size, "Persisted DID should have 2 key mappings")

            val persistedDoc = persisted.document
            assertNotNull(persistedDoc, "Persisted document should be present")
            assertEquals(2, persistedDoc.verificationMethod?.size, "Persisted should have 2 VMs")
        }

    @Test
    fun testCreateDidWebWithThreeKeysAndServices() =
        runTest {
            val result =
                dslProcessor.create {
                    method("web")
                    domain("enterprise.example.com")
                    alias("test-enterprise-did")

                    // Primary authentication key
                    verificationMethod("auth-key") {
                        autoGenerateKey {
                            keyType(KeyTypeMapping.EC)
                            curve(Curve.P_256)
                            kmsProvider("softwaretest")
                        }
                        purposes(VerificationPurpose.AUTHENTICATION)
                    }

                    // Signing key for credentials
                    verificationMethod("assertion-key") {
                        autoGenerateKey {
                            keyType(KeyTypeMapping.EC)
                            curve(Curve.P_256)
                            kmsProvider("softwaretest")
                        }
                        purposes(VerificationPurpose.ASSERTION_METHOD)
                    }

                    // Encryption key
                    verificationMethod("key-agreement-key") {
                        autoGenerateKey {
                            keyType(KeyTypeMapping.EC)
                            curve(Curve.P_384)
                            kmsProvider("softwaretest")
                        }
                        purposes(VerificationPurpose.KEY_AGREEMENT)
                    }

                    // Services
                    service("linked-domains") {
                        type("LinkedDomains")
                        endpoint("https://enterprise.example.com/.well-known/did-configuration.json")
                    }

                    service("oid4vci") {
                        type("OID4VCI")
                        endpoint("https://enterprise.example.com/oid4vci")
                    }
                }

            assertTrue(result.isOk, "DID creation should succeed: ${result.getErrorOrNull()}")
            val managedDid = result.getOrThrow()

            // Verify DID
            assertEquals("did:web:enterprise.example.com", managedDid.did)

            // Verify all verification methods
            val document = managedDid.document
            assertNotNull(document, "Document should be present")
            assertEquals(3, document.verificationMethod?.size, "Should have 3 verification methods")

            // Verify purpose mappings (each key has one purpose)
            assertEquals(1, document.authentication?.size, "Should have 1 authentication ref")
            assertEquals(1, document.assertionMethod?.size, "Should have 1 assertion method ref")
            assertEquals(1, document.keyAgreement?.size, "Should have 1 key agreement ref")

            // Verify services
            assertEquals(2, document.service?.size, "Should have 2 services")

            // Verify key mappings
            assertEquals(3, managedDid.keys.size, "Should have 3 key mappings")

            // Verify persistence
            val persisted = didManager.get(managedDid.did).getOrThrow()
            assertEquals(3, persisted.keys.size, "Persisted should have 3 key mappings")
            assertEquals(3, persisted.document?.verificationMethod?.size, "Persisted should have 3 VMs")
            assertEquals(2, persisted.document?.service?.size, "Persisted should have 2 services")
        }

    @Test
    fun testCreateDidWebWithPath() =
        runTest {
            val result =
                dslProcessor.create {
                    method("web")
                    domain("example.com")
                    path("users", "alice")
                    alias("test-did-web-with-path")

                    autoGenerateKey {
                        keyType(KeyTypeMapping.EC)
                        curve(Curve.P_256)
                        kmsProvider("softwaretest")
                        purposes(VerificationPurpose.AUTHENTICATION)
                    }
                }

            assertTrue(result.isOk, "DID creation should succeed: ${result.getErrorOrNull()}")
            val managedDid = result.getOrThrow()

            // Verify DID format with path
            assertEquals(
                "did:web:example.com:users:alice",
                managedDid.did,
                "DID should include path segments",
            )
        }

    @Test
    fun testCreateDidWebWithMultipleControllers() =
        runTest {
            val result =
                dslProcessor.create {
                    method("web")
                    domain("controllers.example.com")
                    alias("test-did-web-with-controllers")
                    controllers("did:web:controller-a.example.com", "did:web:controller-b.example.com")

                    autoGenerateKey {
                        keyType(KeyTypeMapping.EC)
                        curve(Curve.P_256)
                        kmsProvider("softwaretest")
                        purposes(VerificationPurpose.AUTHENTICATION)
                    }
                }

            assertTrue(result.isOk, "DID creation should succeed: ${result.getErrorOrNull()}")
            val managedDid = result.getOrThrow()
            val document = managedDid.document
            assertNotNull(document, "DID document should be present")
            assertEquals(
                listOf("did:web:controller-a.example.com", "did:web:controller-b.example.com"),
                document.controller,
            )
            assertEquals(
                "did:web:controller-a.example.com",
                document.verificationMethod?.singleOrNull()?.controller,
                "legacy VM controller field should follow the first requested controller",
            )
        }

    // ==================== did:web with Services ====================

    @Test
    fun testCreateDidWebWithServices() =
        runTest {
            val result =
                dslProcessor.create {
                    method("web")
                    domain("example.com")
                    alias("test-did-web-with-services")

                    autoGenerateKey {
                        keyType(KeyTypeMapping.EC)
                        curve(Curve.P_256)
                        kmsProvider("softwaretest")
                        purposes(VerificationPurpose.AUTHENTICATION)
                    }

                    service("linked-domains") {
                        type("LinkedDomains")
                        endpoint("https://example.com/.well-known/did-configuration.json")
                    }

                    service("messaging") {
                        type("DIDCommMessaging")
                        endpoint("https://example.com/didcomm")
                    }
                }

            assertTrue(result.isOk, "DID creation should succeed: ${result.getErrorOrNull()}")
            val managedDid = result.getOrThrow()

            // Verify services
            val document = managedDid.document
            assertNotNull(document, "DID document should be present")
            val services = document.service
            assertNotNull(services, "Services should be present")
            assertEquals(2, services.size, "Should have 2 services")

            // Verify LinkedDomains service
            val linkedDomains = services.find { it.id == "linked-domains" }
            assertNotNull(linkedDomains, "LinkedDomains service should be present")
            assertEquals(listOf("LinkedDomains"), linkedDomains.type, "Service type should match")
            assertEquals(
                JsonPrimitive("https://example.com/.well-known/did-configuration.json"),
                linkedDomains.serviceEndpoint,
                "Service endpoint should match",
            )

            // Verify DIDCommMessaging service
            val messaging = services.find { it.id == "messaging" }
            assertNotNull(messaging, "Messaging service should be present")
            assertEquals(listOf("DIDCommMessaging"), messaging.type, "Service type should match")
            assertEquals(
                JsonPrimitive("https://example.com/didcomm"),
                messaging.serviceEndpoint,
                "Service endpoint should match",
            )

            // Verify persistence
            val persisted = didManager.get(managedDid.did).getOrThrow()
            assertNotNull(persisted, "DID should be retrievable")
            val persistedDoc = persisted.document
            assertNotNull(persistedDoc, "Persisted document should be present")
            assertNotNull(persistedDoc.service, "Persisted services should be present")
            assertEquals(2, persistedDoc.service?.size, "Persisted DID should have 2 services")
        }

    // ==================== Service Lifecycle Tests ====================

    @Test
    fun testAddServiceAfterCreation() =
        runTest {
            // Create DID without services
            val createResult =
                dslProcessor.create {
                    method("web")
                    domain("example.com")
                    alias("test-add-service-did")

                    autoGenerateKey {
                        keyType(KeyTypeMapping.EC)
                        curve(Curve.P_256)
                        kmsProvider("softwaretest")
                        purposes(VerificationPurpose.AUTHENTICATION)
                    }
                }

            assertTrue(createResult.isOk, "DID creation should succeed: ${createResult.getErrorOrNull()}")
            val managedDid = createResult.getOrThrow()

            // Verify no initial services
            assertNull(managedDid.document?.service, "Should have no services initially")

            // Add a service using update
            val newService =
                DidService(
                    id = "hub",
                    type = listOf("IdentityHub"),
                    serviceEndpoint = JsonPrimitive("https://example.com/hub"),
                )

            val updateResult =
                didManager.update(
                    managedDid.did,
                    DidUpdateOptions(
                        currentDocument = managedDid.document,
                        addServices = listOf(newService),
                    ),
                )

            assertTrue(updateResult.isOk, "Service addition should succeed: ${updateResult.getErrorOrNull()}")
            val updatedDid = updateResult.getOrThrow()

            // Verify service was added
            val updatedDoc = updatedDid.document
            assertNotNull(updatedDoc, "Updated document should be present")
            val services = updatedDoc.service
            assertNotNull(services, "Services should be present after update")
            assertEquals(1, services.size, "Should have 1 service")
            assertEquals("hub", services[0].id, "Service ID should match")
            assertEquals(listOf("IdentityHub"), services[0].type, "Service type should match")
            assertEquals(
                JsonPrimitive("https://example.com/hub"),
                services[0].serviceEndpoint,
                "Service endpoint should match",
            )

            // Verify persistence
            val persisted = didManager.get(managedDid.did).getOrThrow()
            val persistedDoc = persisted.document
            assertNotNull(persistedDoc, "Persisted document should be present")
            assertNotNull(persistedDoc.service, "Persisted services should be present")
            assertEquals(1, persistedDoc.service?.size, "Persisted DID should have 1 service")
        }

    @Test
    fun testRemoveService() =
        runTest {
            // Create DID with services
            val createResult =
                dslProcessor.create {
                    method("web")
                    domain("example.com")
                    alias("test-remove-service-did")

                    autoGenerateKey {
                        keyType(KeyTypeMapping.EC)
                        curve(Curve.P_256)
                        kmsProvider("softwaretest")
                        purposes(VerificationPurpose.AUTHENTICATION)
                    }

                    service("service-to-keep") {
                        type("LinkedDomains")
                        endpoint("https://example.com/keep")
                    }

                    service("service-to-remove") {
                        type("Temporary")
                        endpoint("https://example.com/temp")
                    }
                }

            assertTrue(createResult.isOk, "DID creation should succeed: ${createResult.getErrorOrNull()}")
            val managedDid = createResult.getOrThrow()

            // Verify initial services
            val initialDoc = managedDid.document
            assertNotNull(initialDoc, "Initial document should be present")
            assertEquals(2, initialDoc.service?.size, "Should have 2 services initially")

            // Remove one service
            val updateResult =
                didManager.update(
                    managedDid.did,
                    DidUpdateOptions(
                        currentDocument = managedDid.document,
                        removeServiceIds = listOf("service-to-remove"),
                    ),
                )

            assertTrue(updateResult.isOk, "Service removal should succeed: ${updateResult.getErrorOrNull()}")
            val updatedDid = updateResult.getOrThrow()

            // Verify service was removed
            val updatedDoc = updatedDid.document
            assertNotNull(updatedDoc, "Updated document should be present")
            val services = updatedDoc.service
            assertNotNull(services, "Services should still be present")
            assertEquals(1, services.size, "Should have 1 service after removal")
            assertEquals("service-to-keep", services[0].id, "Remaining service should be the one we kept")

            // Verify persistence
            val persisted = didManager.get(managedDid.did).getOrThrow()
            val persistedDoc = persisted.document
            assertNotNull(persistedDoc, "Persisted document should be present")
            assertEquals(1, persistedDoc.service?.size, "Persisted DID should have 1 service")
            assertEquals("service-to-keep", persistedDoc.service?.get(0)?.id)
        }

    @Test
    fun testUpdateServiceEndpoint() =
        runTest {
            // Create DID with a service
            val createResult =
                dslProcessor.create {
                    method("web")
                    domain("example.com")
                    alias("test-update-service-did")

                    autoGenerateKey {
                        keyType(KeyTypeMapping.EC)
                        curve(Curve.P_256)
                        kmsProvider("softwaretest")
                        purposes(VerificationPurpose.AUTHENTICATION)
                    }

                    service("hub") {
                        type("IdentityHub")
                        endpoint("https://old-endpoint.example.com/hub")
                    }
                }

            assertTrue(createResult.isOk, "DID creation should succeed: ${createResult.getErrorOrNull()}")
            val managedDid = createResult.getOrThrow()

            // Verify initial service
            val initialDoc = managedDid.document
            assertNotNull(initialDoc, "Initial document should be present")
            val initialService = initialDoc.service?.find { it.id == "hub" }
            assertNotNull(initialService, "Initial service should be present")
            assertEquals(JsonPrimitive("https://old-endpoint.example.com/hub"), initialService.serviceEndpoint)

            // First, remove the old service
            val removeResult =
                didManager.update(
                    managedDid.did,
                    DidUpdateOptions(
                        currentDocument = managedDid.document,
                        removeServiceIds = listOf("hub"),
                    ),
                )

            assertTrue(removeResult.isOk, "Service removal should succeed: ${removeResult.getErrorOrNull()}")
            val afterRemove = removeResult.getOrThrow()
            val afterRemoveDoc = afterRemove.document
            assertNotNull(afterRemoveDoc, "Document after remove should be present")
            assertNull(afterRemoveDoc.service, "Services should be null after removal")

            // Then, add the updated service
            val updatedService =
                DidService(
                    id = "hub",
                    type = listOf("IdentityHub"),
                    serviceEndpoint = JsonPrimitive("https://new-endpoint.example.com/hub"),
                )

            val addResult =
                didManager.update(
                    managedDid.did,
                    DidUpdateOptions(
                        currentDocument = afterRemoveDoc,
                        addServices = listOf(updatedService),
                    ),
                )

            assertTrue(addResult.isOk, "Service addition should succeed: ${addResult.getErrorOrNull()}")
            val updatedDid = addResult.getOrThrow()

            // Verify service was updated
            val updatedDoc = updatedDid.document
            assertNotNull(updatedDoc, "Updated document should be present")
            val services = updatedDoc.service
            assertNotNull(services, "Services should be present")
            assertEquals(1, services.size, "Should have 1 service")
            assertEquals("hub", services[0].id, "Service ID should match")
            assertEquals(
                JsonPrimitive("https://new-endpoint.example.com/hub"),
                services[0].serviceEndpoint,
                "Service endpoint should be updated",
            )

            // Verify persistence
            val persisted = didManager.get(managedDid.did).getOrThrow()
            val persistedDoc = persisted.document
            assertNotNull(persistedDoc, "Persisted document should be present")
            assertEquals(
                JsonPrimitive("https://new-endpoint.example.com/hub"),
                persistedDoc.service?.get(0)?.serviceEndpoint,
                "Persisted service endpoint should be updated",
            )
        }

    @Test
    fun testRemoveAllServices() =
        runTest {
            // Create DID with services
            val createResult =
                dslProcessor.create {
                    method("web")
                    domain("example.com")
                    alias("test-remove-all-services-did")

                    autoGenerateKey {
                        keyType(KeyTypeMapping.EC)
                        curve(Curve.P_256)
                        kmsProvider("softwaretest")
                        purposes(VerificationPurpose.AUTHENTICATION)
                    }

                    service("service1") {
                        type("Type1")
                        endpoint("https://example.com/1")
                    }

                    service("service2") {
                        type("Type2")
                        endpoint("https://example.com/2")
                    }
                }

            assertTrue(createResult.isOk, "DID creation should succeed: ${createResult.getErrorOrNull()}")
            val managedDid = createResult.getOrThrow()

            // Verify initial services
            val initialDoc = managedDid.document
            assertNotNull(initialDoc, "Initial document should be present")
            assertEquals(2, initialDoc.service?.size, "Should have 2 services initially")

            // Remove all services
            val updateResult =
                didManager.update(
                    managedDid.did,
                    DidUpdateOptions(
                        currentDocument = managedDid.document,
                        removeServiceIds = listOf("service1", "service2"),
                    ),
                )

            assertTrue(updateResult.isOk, "Service removal should succeed: ${updateResult.getErrorOrNull()}")
            val updatedDid = updateResult.getOrThrow()

            // Verify all services were removed
            val updatedDoc = updatedDid.document
            assertNotNull(updatedDoc, "Updated document should be present")
            assertNull(updatedDoc.service, "All services should be removed")

            // Verify persistence
            val persisted = didManager.get(managedDid.did).getOrThrow()
            val persistedDoc = persisted.document
            assertNotNull(persistedDoc, "Persisted document should be present")
            assertNull(persistedDoc.service, "Persisted DID should have no services")
        }

    // ==================== Combined Keys and Services ====================

    @Test
    fun testDidWebFullLifecycle() =
        runTest {
            // Create did:web with a key and services
            val createResult =
                dslProcessor.create {
                    method("web")
                    domain("enterprise.example.com")
                    alias("test-full-lifecycle-did")

                    autoGenerateKey {
                        keyType(KeyTypeMapping.EC)
                        curve(Curve.P_256)
                        kmsProvider("softwaretest")
                        purposes(VerificationPurpose.AUTHENTICATION, VerificationPurpose.ASSERTION_METHOD)
                    }

                    service("linked-domains") {
                        type("LinkedDomains")
                        endpoint("https://enterprise.example.com/.well-known/did-configuration.json")
                    }

                    service("oid4vci") {
                        type("OID4VCI")
                        endpoint("https://enterprise.example.com/oid4vci")
                    }
                }

            assertTrue(createResult.isOk, "DID creation should succeed: ${createResult.getErrorOrNull()}")
            val managedDid = createResult.getOrThrow()

            // Verify initial state
            assertEquals("did:web:enterprise.example.com", managedDid.did)
            val initialDoc = managedDid.document
            assertNotNull(initialDoc, "Initial document should be present")
            assertEquals(1, initialDoc.verificationMethod?.size, "Should have 1 verification method")
            assertEquals(2, initialDoc.service?.size, "Should have 2 services")
            assertEquals(1, managedDid.keys.size, "Should have 1 key mapping")

            // Add a new service
            val newService =
                DidService(
                    id = "oid4vp",
                    type = listOf("OID4VP"),
                    serviceEndpoint = JsonPrimitive("https://enterprise.example.com/oid4vp"),
                )

            val addServiceResult =
                didManager.update(
                    managedDid.did,
                    DidUpdateOptions(
                        currentDocument = managedDid.document,
                        addServices = listOf(newService),
                    ),
                )

            assertTrue(addServiceResult.isOk, "Add service should succeed")
            val afterAddService = addServiceResult.getOrThrow()
            val afterAddDoc = afterAddService.document
            assertNotNull(afterAddDoc, "Document after add should be present")
            assertEquals(3, afterAddDoc.service?.size, "Should have 3 services")

            // Remove a service
            val removeResult =
                didManager.update(
                    managedDid.did,
                    DidUpdateOptions(
                        currentDocument = afterAddDoc,
                        removeServiceIds = listOf("linked-domains"),
                    ),
                )

            assertTrue(removeResult.isOk, "Remove should succeed")
            val afterRemove = removeResult.getOrThrow()

            // Verify final state
            val finalDoc = afterRemove.document
            assertNotNull(finalDoc, "Final document should be present")
            val finalServices = finalDoc.service
            assertNotNull(finalServices, "Services should be present")
            assertEquals(2, finalServices.size, "Should have 2 services after removal")

            // Verify remaining services
            val oid4vpService = finalServices.find { it.id == "oid4vp" }
            assertNotNull(oid4vpService, "OID4VP service should remain")

            val oid4vciService = finalServices.find { it.id == "oid4vci" }
            assertNotNull(oid4vciService, "OID4VCI service should remain")

            // Verify persistence
            val persisted = didManager.get(managedDid.did).getOrThrow()
            val persistedDoc = persisted.document
            assertNotNull(persistedDoc, "Persisted document should be present")
            assertEquals(2, persistedDoc.service?.size, "Persisted DID should have 2 services")
            assertEquals(1, persisted.keys.size, "Key mappings should be preserved")
        }

    // ==================== Error Cases ====================

    @Test
    fun testCreateDidWebWithoutDomainFails() =
        runTest {
            val result =
                dslProcessor.create {
                    method("web")
                    // Missing domain
                    autoGenerateKey {
                        keyType(KeyTypeMapping.EC)
                        curve(Curve.P_256)
                        kmsProvider("softwaretest")
                        purposes(VerificationPurpose.AUTHENTICATION)
                    }
                }

            assertTrue(result.isErr, "Creation without domain should fail")
            val error = result.getErrorOrNull()
            assertNotNull(error, "Should have error")
            assertTrue(
                error.message.defaultMessage.contains("domain"),
                "Error should mention domain: ${error.message.defaultMessage}",
            )
        }

    @Test
    fun testCreateDidWebWithInvalidDomainFails() =
        runTest {
            val result =
                dslProcessor.create {
                    method("web")
                    domain("https://example.com") // Should not include protocol
                    autoGenerateKey {
                        keyType(KeyTypeMapping.EC)
                        curve(Curve.P_256)
                        kmsProvider("softwaretest")
                        purposes(VerificationPurpose.AUTHENTICATION)
                    }
                }

            assertTrue(result.isErr, "Creation with invalid domain should fail")
            val error = result.getErrorOrNull()
            assertNotNull(error, "Should have error")
            assertTrue(
                error.message.defaultMessage.contains("protocol"),
                "Error should mention protocol: ${error.message.defaultMessage}",
            )
        }
}
