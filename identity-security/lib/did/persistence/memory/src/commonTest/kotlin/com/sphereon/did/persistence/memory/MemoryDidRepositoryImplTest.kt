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

package com.sphereon.did.persistence.memory

import com.sphereon.core.api.Ok
import com.sphereon.did.manager.DidRole
import com.sphereon.did.persistence.DidControllerRecord
import com.sphereon.did.persistence.DidDetail
import com.sphereon.did.persistence.DidKeyMappingRecord
import com.sphereon.did.persistence.DidRecord
import com.sphereon.did.persistence.DidRecordFilter
import com.sphereon.did.persistence.DidServiceRecord
import com.sphereon.did.persistence.DidVerificationMethodRecord
import com.sphereon.did.persistence.DidVerificationRelationshipRecord
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Smoke tests for the in-memory aggregate repository.
 *
 * The in-memory backend is the only [com.sphereon.did.persistence.DidRepository] with
 * multiplatform (JVM + JS) coverage, so its tests stay in `commonTest` rather than extending
 * the JVM-only [com.sphereon.did.persistence.testfixtures.DidRepositoryContract] used by the
 * SQL dialects.
 */
class MemoryDidRepositoryImplTest {
    private val now: Instant = Instant.parse("2026-04-21T10:00:00Z")
    private val tenant = "tenant-1"

    private fun record(
        id: String = "rec-${ids++}",
        did: String = "did:example:123",
        alias: String? = null,
        deactivated: Boolean = false,
        deletedAt: Instant? = null,
    ) = DidRecord(
        id = id,
        tenantId = tenant,
        did = did,
        method = "example",
        alias = alias,
        role = DidRole.MANAGED,
        deactivated = deactivated,
        createdAt = now,
        updatedAt = now,
        deletedAt = deletedAt,
    )

    private var ids = 0

    @Test
    fun aggregateRoundTrips(): TestResult =
        runTest {
            val repo = MemoryDidRepositoryImpl()
            val rec = record()
            val vmId = "vm-1"
            val vm =
                DidVerificationMethodRecord(
                    id = vmId,
                    didRecordId = rec.id,
                    vmId = "did:example:123#key-1",
                    type = "JsonWebKey2020",
                    controller = "did:example:123",
                    kmsProviderId = "kms",
                    kmsKeyAlias = "alias-1",
                    ordinal = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            val rel =
                DidVerificationRelationshipRecord(
                    id = "rel-1",
                    didRecordId = rec.id,
                    purpose = "authentication",
                    entryRefDidUrl = "did:example:123#key-1",
                    ordinal = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            val svc =
                DidServiceRecord(
                    id = "svc-1",
                    didRecordId = rec.id,
                    serviceId = "did:example:123#svc-1",
                    typeJson = "[\"LinkedDomains\"]",
                    serviceEndpointJson = "\"https://x.test\"",
                    ordinal = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            val km =
                DidKeyMappingRecord(
                    id = "km-1",
                    didRecordId = rec.id,
                    verificationMethodId = vmId,
                    verificationMethodDidUrl = "did:example:123#key-1",
                    kmsProviderId = "kms",
                    kmsKeyAlias = "alias-1",
                    purposesJson = "[\"authentication\"]",
                    createdAt = now,
                    updatedAt = now,
                )
            val ctrl =
                DidControllerRecord(
                    id = "ctrl-1",
                    didRecordId = rec.id,
                    controllerDid = "did:example:ctrl",
                    ordinal = 0,
                    createdAt = now,
                    updatedAt = now,
                )

            val saved =
                repo.save(
                    DidDetail(
                        record = rec,
                        controller = listOf(ctrl),
                        verificationMethod = listOf(vm),
                        verificationRelationship = listOf(rel),
                        service = listOf(svc),
                        keyMapping = listOf(km),
                    )
                )
            assertTrue(saved is Ok)

            val loaded = (repo.findByDid(tenant, "did:example:123") as Ok).value
            assertNotNull(loaded)
            assertEquals(rec.id, loaded.record.id)
            assertEquals(1, loaded.verificationMethod.size)
            assertEquals(vmId, loaded.verificationMethod[0].id)
            assertEquals("did:example:123#key-1", loaded.verificationRelationship[0].entryRefDidUrl)
            assertNull(loaded.verificationRelationship[0].entryEmbeddedVmId)
            assertEquals(1, loaded.service.size)
            assertEquals(1, loaded.keyMapping.size)
            assertEquals(1, loaded.controller.size)
        }

    @Test
    fun saveReplacesChildRows(): TestResult =
        runTest {
            val repo = MemoryDidRepositoryImpl()
            val rec = record()
            val firstVm =
                DidVerificationMethodRecord(
                    id = "vm-1",
                    didRecordId = rec.id,
                    vmId = "did:example:123#k",
                    type = "JsonWebKey2020",
                    controller = "did:example:123",
                    kmsProviderId = "kms",
                    kmsKeyAlias = "a1",
                    ordinal = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            repo.save(DidDetail(record = rec, verificationMethod = listOf(firstVm)))
            val replacement = firstVm.copy(id = "vm-2", kmsKeyAlias = "a2")
            repo.save(DidDetail(record = rec, verificationMethod = listOf(replacement)))

            val loaded = (repo.findByDid(tenant, "did:example:123") as Ok).value!!
            assertEquals(1, loaded.verificationMethod.size)
            assertEquals("a2", loaded.verificationMethod[0].kmsKeyAlias)
        }

    @Test
    fun softDeleteHidesUnlessIncludeDeleted(): TestResult =
        runTest {
            val repo = MemoryDidRepositoryImpl()
            val rec = record()
            repo.save(DidDetail(record = rec))
            assertTrue(repo.softDelete(tenant, rec.did, now, deletedBy = null) is Ok)

            assertNull((repo.findByDid(tenant, rec.did, includeDeleted = false) as Ok).value)
            val visible = (repo.findByDid(tenant, rec.did, includeDeleted = true) as Ok).value
            assertNotNull(visible)
            assertNotNull(visible.record.deletedAt)
        }

    @Test
    fun deleteCascadesChildRows(): TestResult =
        runTest {
            val repo = MemoryDidRepositoryImpl()
            val rec = record()
            val vm =
                DidVerificationMethodRecord(
                    id = "vm-h",
                    didRecordId = rec.id,
                    vmId = "did:example:123#k",
                    type = "JsonWebKey2020",
                    controller = "did:example:123",
                    kmsProviderId = "kms",
                    kmsKeyAlias = "a",
                    ordinal = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            repo.save(DidDetail(record = rec, verificationMethod = listOf(vm)))
            assertTrue(repo.delete(tenant, rec.did) is Ok)
            assertNull((repo.findByDid(tenant, rec.did, includeDeleted = true) as Ok).value)
        }

    @Test
    fun findAllRespectsFilter(): TestResult =
        runTest {
            val repo = MemoryDidRepositoryImpl()
            repo.save(DidDetail(record = record(did = "did:example:a", alias = "alias-a")))
            repo.save(DidDetail(record = record(did = "did:example:b", alias = "alias-b")))
            val filtered = (repo.findAll(DidRecordFilter(tenantId = tenant, alias = "alias-b")) as Ok).value
            assertEquals(1, filtered.size)
            assertEquals("did:example:b", filtered[0].record.did)
        }

    @Test
    fun relationshipInvariantRejectsBothEntryFieldsNull(): TestResult =
        runTest {
            val err =
                runCatching {
                    DidVerificationRelationshipRecord(
                        id = "rel",
                        didRecordId = "r",
                        purpose = "authentication",
                        entryEmbeddedVmId = null,
                        entryRefDidUrl = null,
                        ordinal = 0,
                        createdAt = now,
                        updatedAt = now,
                    )
                }.exceptionOrNull()
            assertNotNull(err)
        }
}
