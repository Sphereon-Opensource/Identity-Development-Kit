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

package com.sphereon.crypto.key.persistence.impl

import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.key.persistence.KeyReferenceHistoryCapability
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.crypto.key.persistence.KeyReferenceStoreErrorCodes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class ManagedKeyReferenceRegistrarTest {
    private lateinit var store: KeyReferenceStore
    private lateinit var execution: SessionExecution
    private lateinit var registrar: ManagedKeyReferenceRegistrar

    @BeforeEach
    fun setup() {
        store = mockk(relaxed = true)
        every { store.ownershipHistoryCapability } returns KeyReferenceHistoryCapability.DURABLE
        execution =
            mockk {
                every { sessionContext } returns
                    mockk {
                        every { context } returns
                            mockk {
                                every { tenant } returns
                                    mockk {
                                        every { tenantId } returns "test-tenant"
                                    }
                            }
                    }
            }
        registrar = ManagedKeyReferenceRegistrar(store, execution)
    }

    private fun mockManagedKey(
        alias: String = "test-alias",
        kid: String? = "test-kid",
        providerId: String = "test-provider",
    ): ManagedKeyInfoType<*> =
        mockk {
            every { this@mockk.alias } returns alias
            every { this@mockk.kid } returns kid
            every { this@mockk.providerId } returns providerId
            every { keyType } returns null
            every { signatureAlgorithm } returns null
            every { keyVisibility } returns null
            every { keyEncoding } returns null
        }

    private fun mockKeyInfo(
        alias: String? = null,
        kid: String? = null,
        providerId: String? = null,
    ): KeyInfoType<*> =
        mockk {
            every { this@mockk.alias } returns alias
            every { this@mockk.kid } returns kid
            every { this@mockk.providerId } returns providerId
        }

    private fun tenantExecution(id: String): SessionExecution =
        mockk {
            every { sessionContext } returns
                mockk {
                    every { context } returns
                        mockk {
                            every { tenant } returns
                                mockk {
                                    every { tenantId } returns id
                                }
                        }
                }
        }

    // -- indexManagedKey --

    @Test
    fun indexManagedKeyCreatesRecord() =
        runTest {
            every { store.isAvailable } returns true
            coEvery { store.upsert(any()) } answers {
                Ok(firstArg<KeyReferenceRecord>())
            }

            val mockKey = mockManagedKey(alias = "test-alias", kid = "test-kid", providerId = "test-provider")
            val result = registrar.indexManagedKey(mockKey)

            assertTrue(result.isOk)
            coVerify {
                store.upsert(
                    match { record ->
                        record.tenantId == "test-tenant" &&
                            record.alias == "test-alias" &&
                            record.kid == "test-kid" &&
                            record.providerId == "test-provider" &&
                            record.origin == Origin.MANAGED
                    },
                )
            }
        }

    @Test
    fun twoTenantsIndexAndReadOnlyTheirOwnManagedKeyReferences() =
        runTest {
            every { store.isAvailable } returns true
            val persisted = mutableListOf<KeyReferenceRecord>()
            coEvery { store.upsert(any()) } answers {
                val record = firstArg<KeyReferenceRecord>()
                persisted.removeIf { it.tenantId == record.tenantId && it.alias == record.alias && it.providerId == record.providerId }
                persisted += record
                Ok(record)
            }
            coEvery { store.findAll(any(), any()) } answers {
                Ok(persisted.filter { it.tenantId == firstArg<String>() })
            }
            coEvery { store.findByAlias(any(), any(), any()) } answers {
                val tenantId = firstArg<String>()
                val alias = secondArg<String>()
                val providerId = thirdArg<String?>()
                Ok(persisted.firstOrNull { it.tenantId == tenantId && it.alias == alias && (providerId == null || it.providerId == providerId) })
            }

            val tenantARegistrar = ManagedKeyReferenceRegistrar(store, tenantExecution("tenant-a"))
            val tenantBRegistrar = ManagedKeyReferenceRegistrar(store, tenantExecution("tenant-b"))
            tenantARegistrar.indexManagedKey(mockManagedKey(alias = "tenant-a-key", kid = "kid-a", providerId = "shared-azure"))
            tenantBRegistrar.indexManagedKey(mockManagedKey(alias = "tenant-b-key", kid = "kid-b", providerId = "shared-azure"))

            val tenantA = store.findAll("tenant-a").getOrElse { error("tenant-a listing failed: ${it.message}") }
            val tenantB = store.findAll("tenant-b").getOrElse { error("tenant-b listing failed: ${it.message}") }
            assertEquals(listOf("tenant-a-key"), tenantA.map { it.alias })
            assertEquals(listOf("tenant-b-key"), tenantB.map { it.alias })
            assertNull(store.findByAlias("tenant-a", "tenant-b-key", "shared-azure").value)
            assertNull(store.findByAlias("tenant-b", "tenant-a-key", "shared-azure").value)
        }

    @Test
    fun indexManagedKeySkipsWhenUnavailable() =
        runTest {
            every { store.isAvailable } returns false

            val mockKey = mockManagedKey()
            val result = registrar.indexManagedKey(mockKey)

            assertTrue(result.isOk)
            assertNull(result.value)
            coVerify(exactly = 0) { store.upsert(any()) }
        }

    @Test
    fun indexManagedKeySkipsWhenNoAlias() =
        runTest {
            every { store.isAvailable } returns true

            // ManagedKeyInfoType.alias is non-null (String), so we use an empty string
            // to simulate the "no meaningful alias" case. The registrar will still create a record
            // because alias is never null on ManagedKeyInfoType.
            val mockKey =
                mockk<ManagedKeyInfoType<*>> {
                    every { alias } returns ""
                    every { kid } returns "test-kid"
                    every { providerId } returns "test-provider"
                    every { keyType } returns null
                    every { signatureAlgorithm } returns null
                    every { keyVisibility } returns null
                    every { keyEncoding } returns null
                }

            coEvery { store.upsert(any()) } answers { Ok(firstArg<KeyReferenceRecord>()) }

            val result = registrar.indexManagedKey(mockKey)

            assertTrue(result.isOk)
            coVerify { store.upsert(match { it.alias == "" }) }
        }

    // -- registerKeyReference --

    @Test
    fun registerKeyReferenceCreatesRecord() =
        runTest {
            every { store.isAvailable } returns true
            coEvery { store.findByAlias("test-tenant", "ext-alias", "ext-provider") } returns Ok(null)
            coEvery { store.findByKid("test-tenant", "ext-kid", "ext-provider") } returns Ok(null)
            coEvery { store.upsert(any()) } answers {
                Ok(firstArg<KeyReferenceRecord>())
            }

            val result =
                registrar.registerKeyReference(
                    providerId = "ext-provider",
                    alias = "ext-alias",
                    kid = "ext-kid",
                )

            assertTrue(result.isOk)
            coVerify {
                store.upsert(
                    match { record ->
                        record.tenantId == "test-tenant" &&
                            record.alias == "ext-alias" &&
                            record.kid == "ext-kid" &&
                            record.providerId == "ext-provider" &&
                            record.origin == Origin.EXTERNAL &&
                            record.controlMode == ResourceControlMode.EXTERNALLY_MANAGED
                    },
                )
            }
        }

    @Test
    fun registerKeyReferencePersistsOnlySuppliedPublicMaterial() =
        runTest {
            every { store.isAvailable } returns true
            coEvery { store.findByAlias("test-tenant", "ext-alias", "ext-provider") } returns Ok(null)
            coEvery { store.findByKid("test-tenant", "ext-kid", "ext-provider") } returns Ok(null)
            coEvery { store.upsert(any()) } answers { Ok(firstArg<KeyReferenceRecord>()) }

            val result =
                registrar.registerKeyReference(
                    providerId = "ext-provider",
                    alias = "ext-alias",
                    kid = "ext-kid",
                    publicKeyJwk = "{\"kty\":\"EC\",\"x\":\"public\"}",
                )

            assertTrue(result.isOk)
            assertEquals("{\"kty\":\"EC\",\"x\":\"public\"}", result.value.publicKeyJwk)
            val persistedPublicKeyJwk = result.value.publicKeyJwk
            assertNotNull(persistedPublicKeyJwk)
            assertFalse(persistedPublicKeyJwk.contains("\"d\""))
            assertFalse(persistedPublicKeyJwk.contains("\"k\""))
        }

    @Test
    fun reRegistrationReclassifiesExistingReferenceAsExternal() =
        runTest {
            every { store.isAvailable } returns true
            coEvery { store.findByAlias("test-tenant", "ext-alias", "ext-provider") } returns
                Ok(
                    KeyReferenceRecord(
                        id = "existing",
                        tenantId = "test-tenant",
                        alias = "ext-alias",
                        kid = "ext-kid",
                        providerId = "ext-provider",
                        origin = Origin.MANAGED,
                        createdAt = Clock.System.now(),
                        updatedAt = Clock.System.now(),
                    ),
                )
            coEvery { store.findByKid("test-tenant", "ext-kid", "ext-provider") } returns
                Ok(
                    KeyReferenceRecord(
                        id = "existing",
                        tenantId = "test-tenant",
                        alias = "ext-alias",
                        kid = "ext-kid",
                        providerId = "ext-provider",
                        origin = Origin.MANAGED,
                        createdAt = Clock.System.now(),
                        updatedAt = Clock.System.now(),
                    ),
                )
            coEvery { store.upsert(any()) } answers { Ok(firstArg<KeyReferenceRecord>()) }

            val result =
                registrar.registerKeyReference(
                    providerId = "ext-provider",
                    alias = "ext-alias",
                    kid = "ext-kid",
                )

            assertTrue(result.isOk)
            assertEquals(Origin.EXTERNAL, result.value.origin)
            assertEquals(ResourceControlMode.EXTERNALLY_MANAGED, result.value.controlMode)
            coVerify {
                store.upsert(
                    match {
                        it.id == "existing" &&
                            it.origin == Origin.EXTERNAL &&
                            it.controlMode == ResourceControlMode.EXTERNALLY_MANAGED
                    },
                )
            }
        }

    @Test
    fun reRegistrationRejectsIdentityConflictBeforeUpsert() =
        runTest {
            every { store.isAvailable } returns true
            coEvery { store.findByAlias("test-tenant", "ext-alias", "ext-provider") } returns
                Ok(
                    KeyReferenceRecord(
                        id = "existing",
                        tenantId = "test-tenant",
                        alias = "ext-alias",
                        kid = "different-kid",
                        providerId = "ext-provider",
                        origin = Origin.MANAGED,
                        createdAt = Clock.System.now(),
                        updatedAt = Clock.System.now(),
                    ),
                )

            val result =
                registrar.registerKeyReference(
                    providerId = "ext-provider",
                    alias = "ext-alias",
                    kid = "requested-kid",
                )

            assertTrue(result.isErr)
            assertEquals("KMS_EXTERNAL_KEY_REGISTRATION_CONFLICT", result.error.code)
            coVerify(exactly = 0) { store.upsert(any()) }
        }

    @Test
    fun registrationRejectsCanonicalKidOwnedByAnotherActiveAlias() =
        runTest {
            every { store.isAvailable } returns true
            coEvery { store.findByAlias("test-tenant", "new-alias", "ext-provider") } returns Ok(null)
            coEvery { store.findByKid("test-tenant", "canonical-kid", "ext-provider") } returns
                Ok(
                    KeyReferenceRecord(
                        id = "other-alias",
                        tenantId = "test-tenant",
                        alias = "other-alias",
                        kid = "canonical-kid",
                        providerId = "ext-provider",
                        origin = Origin.EXTERNAL,
                        controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                        createdAt = Clock.System.now(),
                        updatedAt = Clock.System.now(),
                    ),
                )

            val result =
                registrar.registerKeyReference(
                    providerId = "ext-provider",
                    alias = "new-alias",
                    kid = "canonical-kid",
                )

            assertTrue(result.isErr)
            assertEquals("KMS_EXTERNAL_KEY_REGISTRATION_CONFLICT", result.error.code)
            coVerify(exactly = 0) { store.upsert(any()) }
        }

    @Test
    fun registerKeyReferenceReturnsErrorWhenStoreUnavailable() =
        runTest {
            every { store.isAvailable } returns false

            val result =
                registrar.registerKeyReference(
                    providerId = "ext-provider",
                    alias = "ext-alias",
                    kid = "ext-kid",
                )

            assertTrue(result.isErr)
            coVerify(exactly = 0) { store.upsert(any()) }
        }

    @Test
    fun registerKeyReferenceFailsClosedWhenDurableHistoryIsUnsupported() =
        runTest {
            every { store.isAvailable } returns true
            every { store.ownershipHistoryCapability } returns KeyReferenceHistoryCapability.UNSUPPORTED

            val result =
                registrar.registerKeyReference(
                    providerId = "ext-provider",
                    alias = "ext-alias",
                    kid = "ext-kid",
                )

            assertTrue(result.isErr)
            assertEquals(KeyReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED, result.error.code)
            coVerify(exactly = 0) { store.findByAlias(any(), any(), any()) }
            coVerify(exactly = 0) { store.findByKid(any(), any(), any()) }
            coVerify(exactly = 0) { store.upsert(any()) }
        }

    // -- removeKeyReference --

    @Test
    fun removeKeyReferenceByAliasAndProvider() =
        runTest {
            every { store.isAvailable } returns true
            coEvery { store.delete("test-tenant", "test-alias", "test-provider") } returns Ok(true)

            val keyInfo = mockKeyInfo(alias = "test-alias", kid = "test-kid", providerId = "test-provider")
            val result = registrar.removeKeyReference(keyInfo)

            assertTrue(result.isOk)
            assertTrue(result.value)
            coVerify { store.delete("test-tenant", "test-alias", "test-provider") }
        }

    @Test
    fun removeKeyReferenceByKid() =
        runTest {
            every { store.isAvailable } returns true
            coEvery { store.deleteByKid("test-tenant", "test-kid") } returns Ok(true)

            val keyInfo = mockKeyInfo(alias = null, kid = "test-kid", providerId = null)
            val result = registrar.removeKeyReference(keyInfo)

            assertTrue(result.isOk)
            assertTrue(result.value)
            coVerify { store.deleteByKid("test-tenant", "test-kid") }
        }

    @Test
    fun removeKeyReferenceSkipsWhenUnavailable() =
        runTest {
            every { store.isAvailable } returns false

            val keyInfo = mockKeyInfo(alias = "test-alias", providerId = "test-provider")
            val result = registrar.removeKeyReference(keyInfo)

            assertTrue(result.isOk)
            assertTrue(!result.value)
            coVerify(exactly = 0) { store.delete(any(), any(), any()) }
            coVerify(exactly = 0) { store.deleteByKid(any(), any()) }
        }

    @Test
    fun removeKeyReferenceSkipsWhenNoIdentifiers() =
        runTest {
            every { store.isAvailable } returns true

            val keyInfo = mockKeyInfo(alias = null, kid = null, providerId = null)
            val result = registrar.removeKeyReference(keyInfo)

            assertTrue(result.isOk)
            assertTrue(!result.value)
            coVerify(exactly = 0) { store.delete(any(), any(), any()) }
            coVerify(exactly = 0) { store.deleteByKid(any(), any()) }
        }
}
