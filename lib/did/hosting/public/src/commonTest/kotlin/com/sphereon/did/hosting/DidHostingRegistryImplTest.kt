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

package com.sphereon.did.hosting

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DidHostingRegistryImplTest {
    private class FakeProvider(
        override val method: String,
        override val authorityPriority: Int = 0,
        private val managed: Map<String, HostedDid> = emptyMap(),
        private val fail: Boolean = false,
    ) : DidHostingProvider {
        override suspend fun resolveDidJson(
            tenantId: String?,
            webLocation: String,
        ): IdkResult<HostedDid?, IdkError> =
            if (fail) {
                Err(IdkError.UNKNOWN_ERROR(message = "boom"))
            } else {
                Ok(managed[webLocation])
            }
    }

    @Test
    fun reportsHostableMethodsFromContributedProviders() {
        val registry =
            DidHostingRegistryImpl(
                setOf(FakeProvider("web"), FakeProvider("webvh")),
            )
        assertEquals(setOf("web", "webvh"), registry.hostableMethods())
    }

    @Test
    fun returnsFirstProviderThatClaimsTheLocation() =
        runTest {
            val hosted = HostedDid(json = "{\"id\":\"did:web:example.com\"}", method = "web")
            val registry =
                DidHostingRegistryImpl(
                    setOf(
                        FakeProvider("web", managed = mapOf("example.com" to hosted)),
                        FakeProvider("webvh"),
                    ),
                )
            val result = registry.resolveDidJson(tenantId = "t1", webLocation = "example.com")
            assertTrue(result is Ok)
            assertEquals(hosted, result.value)
        }

    @Test
    fun authoritativeProviderWinsOverSyntheticFallbackRegardlessOfSetOrder() =
        runTest {
            val fallback = HostedDid(json = "{\"id\":\"did:web:example.com#fallback\"}", method = "web")
            val persisted = HostedDid(json = "{\"id\":\"did:web:example.com#persisted\"}", method = "web")
            val registry =
                DidHostingRegistryImpl(
                    linkedSetOf(
                        FakeProvider("web", managed = mapOf("example.com" to fallback)),
                        FakeProvider("web", authorityPriority = 100, managed = mapOf("example.com" to persisted)),
                    ),
                )

            val result = registry.resolveDidJson(tenantId = "t1", webLocation = "example.com")

            assertTrue(result is Ok)
            assertEquals(persisted, result.value)
        }

    @Test
    fun returnsNullWhenNoProviderManagesLocation() =
        runTest {
            val registry = DidHostingRegistryImpl(setOf(FakeProvider("web"), FakeProvider("webvh")))
            val result = registry.resolveDidJson(tenantId = "t1", webLocation = "unmanaged.example.com")
            assertTrue(result is Ok)
            assertNull(result.value)
        }

    @Test
    fun providerErrorShortCircuits() =
        runTest {
            val registry = DidHostingRegistryImpl(setOf(FakeProvider("web", fail = true)))
            val result = registry.resolveDidJson(tenantId = "t1", webLocation = "example.com")
            assertTrue(result is Err)
        }
}
