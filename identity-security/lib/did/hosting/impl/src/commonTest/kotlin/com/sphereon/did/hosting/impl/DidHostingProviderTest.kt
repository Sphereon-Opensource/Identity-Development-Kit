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

package com.sphereon.did.hosting.impl

import com.sphereon.core.api.Ok
import com.sphereon.did.manager.DidRole
import com.sphereon.did.models.DidDocument
import com.sphereon.did.persistence.DidDetail
import com.sphereon.did.persistence.DidRecord
import com.sphereon.did.persistence.DidRepository
import com.sphereon.did.persistence.memory.MemoryDidRepositoryImpl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class DidHostingProviderTest {
    private val tenant = "tenant-1"
    private val now = Clock.System.now()
    private val parser = Json { ignoreUnknownKeys = true }

    private suspend fun DidRepository.put(
        did: String,
        method: String,
        webLocation: String,
        deactivated: Boolean = false,
    ) {
        val detail =
            DidDetail(
                record =
                    DidRecord(
                        id = "rec-$webLocation-$method",
                        tenantId = tenant,
                        did = did,
                        method = method,
                        role = DidRole.MANAGED,
                        webLocation = webLocation,
                        deactivated = deactivated,
                        createdAt = now,
                        updatedAt = now,
                    ),
            )
        assertTrue(save(detail) is Ok, "save should succeed for $did")
    }

    @Test
    fun webProviderServesStoredDocumentVerbatim() =
        runTest {
            val repo = MemoryDidRepositoryImpl()
            repo.put(did = "did:web:web.example.com", method = "web", webLocation = "web.example.com")
            val provider = WebDidHostingProvider(repo)

            val result = provider.resolveDidJson(tenant, "web.example.com")
            assertTrue(result is Ok)
            val hosted = result.value!!
            val doc = parser.decodeFromString(DidDocument.serializer(), hosted.json)
            assertEquals("did:web:web.example.com", doc.id)
            assertEquals(300L, hosted.cacheMaxAgeSeconds)
        }

    @Test
    fun webvhProviderServesCompanionWithAlsoKnownAs() =
        runTest {
            val repo = MemoryDidRepositoryImpl()
            val webvhDid = "did:webvh:QmScid123:vh.example.com"
            repo.put(did = webvhDid, method = "webvh", webLocation = "vh.example.com")
            val provider = WebvhDidHostingProvider(repo)

            val result = provider.resolveDidJson(tenant, "vh.example.com")
            assertTrue(result is Ok)
            val hosted = result.value!!
            val doc = parser.decodeFromString(DidDocument.serializer(), hosted.json)
            // The companion is the did:web translation, with alsoKnownAs pointing back to the did:webvh.
            assertEquals("did:web:vh.example.com", doc.id)
            assertTrue(
                doc.alsoKnownAs?.contains(webvhDid) == true,
                "companion must carry alsoKnownAs back to the did:webvh (was ${doc.alsoKnownAs})",
            )
            assertEquals(3600L, hosted.cacheMaxAgeSeconds)
        }

    @Test
    fun providerIgnoresLocationsManagedByAnotherMethod() =
        runTest {
            val repo = MemoryDidRepositoryImpl()
            // A did:webvh manages this location; the web provider must NOT claim it.
            repo.put(did = "did:webvh:QmScid:shared.example.com", method = "webvh", webLocation = "shared.example.com")

            val webResult = WebDidHostingProvider(repo).resolveDidJson(tenant, "shared.example.com")
            assertTrue(webResult is Ok)
            assertNull(webResult.value, "web provider must not serve a webvh-managed location")
        }

    @Test
    fun unmanagedLocationReturnsNull() =
        runTest {
            val repo = MemoryDidRepositoryImpl()
            val result = WebDidHostingProvider(repo).resolveDidJson(tenant, "nothing.example.com")
            assertTrue(result is Ok)
            assertNull(result.value)
        }
}
