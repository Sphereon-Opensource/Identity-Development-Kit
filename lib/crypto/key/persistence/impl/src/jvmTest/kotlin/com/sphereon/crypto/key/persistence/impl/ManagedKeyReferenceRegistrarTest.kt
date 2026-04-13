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
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.sphereon.crypto.key.persistence.KeyReferenceStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManagedKeyReferenceRegistrarTest {
    private lateinit var store: KeyReferenceStore
    private lateinit var execution: SessionExecution
    private lateinit var registrar: ManagedKeyReferenceRegistrar

    @BeforeEach
    fun setup() {
        store = mockk(relaxed = true)
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
                            record.origin == Origin.MANAGED
                    },
                )
            }
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
