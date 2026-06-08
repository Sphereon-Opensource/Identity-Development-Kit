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

package com.sphereon.did.persistence.testfixtures

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.did.manager.DidRole
import com.sphereon.did.persistence.DidControllerRecord
import com.sphereon.did.persistence.DidDetail
import com.sphereon.did.persistence.DidDocumentContextRecord
import com.sphereon.did.persistence.DidKeyMappingRecord
import com.sphereon.did.persistence.DidRecord
import com.sphereon.did.persistence.DidRecordFilter
import com.sphereon.did.persistence.DidRepository
import com.sphereon.did.persistence.DidServiceRecord
import com.sphereon.did.persistence.DidVerificationMethodRecord
import com.sphereon.did.persistence.DidVerificationRelationshipRecord
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Cross-dialect behavioural contract for [DidRepository] implementations. Subclasses wire a
 * live repository and cleanup routine; the inherited `@Test` methods exercise aggregate
 * round-trip, delete-then-insert idempotency, soft-delete and delete semantics, cache upsert,
 * filter queries, and the polymorphic-relationship invariant.
 *
 * Every test begins with [clearRows] so state is deterministic regardless of order.
 * Implementations that depend on external infrastructure (TestContainers, Docker) can skip
 * the suite cleanly by overriding [isInfrastructureAvailable] to return `false`.
 */
abstract class DidRepositoryContract {
    /** Returns the repository under test. Called once per test method. */
    protected abstract fun repository(): DidRepository

    /** Deletes every row across the 9-table aggregate. Called before each test. */
    protected abstract fun clearRows()

    /**
     * Override to skip the whole suite when external infrastructure (e.g., a Docker-backed
     * TestContainer) is unavailable. Default: always run.
     */
    protected open val isInfrastructureAvailable: Boolean
        get() = true

    protected open val tenant: String = "tenant-1"
    protected val now: Instant = Clock.System.now()

    private fun newId(): String = Uuid.random().toString()

    private fun record(
        id: String = newId(),
        did: String = "did:example:123",
        alias: String? = null,
        tenantId: String = tenant,
        deactivated: Boolean = false,
        deletedAt: Instant? = null,
    ) = DidRecord(
        id = id,
        tenantId = tenantId,
        did = did,
        method = "example",
        alias = alias,
        role = DidRole.MANAGED,
        deactivated = deactivated,
        createdAt = now,
        updatedAt = now,
        deletedAt = deletedAt,
    )

    private fun webRecord(
        id: String = newId(),
        did: String,
        method: String,
        webLocation: String,
        tenantId: String = tenant,
        deletedAt: Instant? = null,
    ) = DidRecord(
        id = id,
        tenantId = tenantId,
        did = did,
        method = method,
        role = DidRole.MANAGED,
        webLocation = webLocation,
        createdAt = now,
        updatedAt = now,
        deletedAt = deletedAt,
    )

    @Test
    fun findsByWebLocation() =
        runTest {
            before()
            val repo = repository()
            val rec = webRecord(did = "did:web:example.com:tenants:acme", method = "web", webLocation = "example.com:tenants:acme")
            (repo.save(DidDetail(record = rec)) as? Ok) ?: error("save failed")

            val found = repo.findByWebLocation(tenant, "example.com:tenants:acme")
            assertTrue(found is Ok, "findByWebLocation should succeed")
            assertNotNull(found.value, "expected a record at the web location")
            assertEquals(rec.did, found.value!!.record.did)

            val miss = repo.findByWebLocation(tenant, "other.example.com")
            assertTrue(miss is Ok && miss.value == null, "unmanaged location returns null")
        }

    @Test
    fun rejectsSecondMethodAtSameWebLocation() =
        runTest {
            before()
            val repo = repository()
            // A did:webvh manages example.com (web location strips the SCID).
            val webvh =
                webRecord(
                    did = "did:webvh:QmScid123:example.com",
                    method = "webvh",
                    webLocation = "example.com",
                )
            assertTrue(repo.save(DidDetail(record = webvh)) is Ok, "first save should succeed")

            // A did:web for the SAME location must be rejected — both map to example.com/.well-known.
            val web =
                webRecord(
                    did = "did:web:example.com",
                    method = "web",
                    webLocation = "example.com",
                )
            val clash = repo.save(DidDetail(record = web))
            assertTrue(clash is Err, "second method at the same web location must be rejected")

            // A different web location for the same tenant is allowed.
            val other =
                webRecord(
                    did = "did:web:other.example.com",
                    method = "web",
                    webLocation = "other.example.com",
                )
            assertTrue(repo.save(DidDetail(record = other)) is Ok, "distinct web location should be allowed")
        }

    private fun assumeInfrastructureAvailable() {
        assumeTrue(isInfrastructureAvailable, "Required repository infrastructure is not available")
    }

    private fun before() {
        assumeInfrastructureAvailable()
        clearRows()
    }

    @Test
    fun aggregateRoundTrips() =
        runTest {
            before()
            val repo = repository()
            val rec = record()
            val vmUuid = newId()
            val vm =
                DidVerificationMethodRecord(
                    id = vmUuid,
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
                    id = newId(),
                    didRecordId = rec.id,
                    purpose = "authentication",
                    entryRefDidUrl = "did:example:123#key-1",
                    ordinal = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            val svc =
                DidServiceRecord(
                    id = newId(),
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
                    id = newId(),
                    didRecordId = rec.id,
                    verificationMethodId = vmUuid,
                    verificationMethodDidUrl = "did:example:123#key-1",
                    kmsProviderId = "kms",
                    kmsKeyAlias = "alias-1",
                    purposesJson = "[\"authentication\"]",
                    createdAt = now,
                    updatedAt = now,
                )
            val ctrl =
                DidControllerRecord(
                    id = newId(),
                    didRecordId = rec.id,
                    controllerDid = "did:example:ctrl",
                    ordinal = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            val aggregate =
                DidDetail(
                    record = rec,
                    controller = listOf(ctrl),
                    verificationMethod = listOf(vm),
                    verificationRelationship = listOf(rel),
                    service = listOf(svc),
                    keyMapping = listOf(km),
                )

            assertTrue(repo.save(aggregate) is Ok, "save failed")

            val loaded = (repo.findByDid(tenant, "did:example:123") as Ok).value
            assertNotNull(loaded)
            assertEquals(rec.id, loaded.record.id)
            assertEquals(1, loaded.verificationMethod.size)
            assertEquals(vmUuid, loaded.verificationMethod[0].id)
            assertEquals(1, loaded.verificationRelationship.size)
            assertEquals("did:example:123#key-1", loaded.verificationRelationship[0].entryRefDidUrl)
            assertNull(loaded.verificationRelationship[0].entryEmbeddedVmId)
            assertEquals(1, loaded.service.size)
            assertEquals(1, loaded.keyMapping.size)
            assertEquals(1, loaded.controller.size)
        }

    @Test
    fun saveIsIdempotentViaDeleteThenInsert() =
        runTest {
            before()
            val repo = repository()
            val rec = record()
            val firstVm =
                DidVerificationMethodRecord(
                    id = newId(),
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
            val replacementVm = firstVm.copy(id = newId(), kmsKeyAlias = "a2")
            repo.save(DidDetail(record = rec, verificationMethod = listOf(replacementVm)))

            val loaded = (repo.findByDid(tenant, "did:example:123") as Ok).value!!
            assertEquals(1, loaded.verificationMethod.size)
            assertEquals("a2", loaded.verificationMethod[0].kmsKeyAlias)
        }

    @Test
    fun softDeleteHidesUnlessIncludeDeleted() =
        runTest {
            before()
            val repo = repository()
            val rec = record()
            repo.save(DidDetail(record = rec))
            assertTrue(repo.softDelete(tenant, rec.did, now, deletedBy = null) is Ok)

            assertNull((repo.findByDid(tenant, rec.did, includeDeleted = false) as Ok).value)
            val visible = (repo.findByDid(tenant, rec.did, includeDeleted = true) as Ok).value
            assertNotNull(visible)
            assertNotNull(visible.record.deletedAt)
        }

    @Test
    fun deleteCascadesChildRows() =
        runTest {
            before()
            val repo = repository()
            val rec = record()
            val vm =
                DidVerificationMethodRecord(
                    id = newId(),
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
    fun findAllRespectsFilter() =
        runTest {
            before()
            val repo = repository()
            repo.save(DidDetail(record = record(did = "did:example:a", alias = "alias-a")))
            repo.save(DidDetail(record = record(did = "did:example:b", alias = "alias-b")))
            val filtered = (repo.findAll(DidRecordFilter(tenantId = tenant, alias = "alias-b")) as Ok).value
            assertEquals(1, filtered.size)
            assertEquals("did:example:b", filtered[0].record.did)
        }

    @Test
    fun duplicateLiveDidInSameTenantRejected() =
        runTest {
            before()
            val repo = repository()
            val first = record(did = "did:example:duplicate-live")
            val second = record(id = newId(), did = first.did)

            assertTrue(repo.save(DidDetail(record = first)) is Ok)
            assertTrue(repo.save(DidDetail(record = second)) is Err, "duplicate live DID should be rejected")
        }

    @Test
    fun tenantIsolation_didFromOneTenantInvisibleToAnother() =
        runTest {
            before()
            val repo = repository()
            val tenantA = tenant
            val tenantB = "tenant-other"
            val didValue = "did:example:shared-identifier"

            // Tenant A owns the DID.
            val recA =
                DidRecord(
                    id = newId(),
                    tenantId = tenantA,
                    did = didValue,
                    method = "example",
                    alias = "alias-a",
                    role = DidRole.MANAGED,
                    deactivated = false,
                    createdAt = now,
                    updatedAt = now,
                )
            assertTrue(repo.save(DidDetail(record = recA)) is Ok)

            // Tenant A can read by DID + by alias; tenant B sees nothing.
            assertNotNull((repo.findByDid(tenantA, didValue) as Ok).value, "owner tenant should see its DID")
            assertNull((repo.findByDid(tenantB, didValue) as Ok).value, "other tenant must not see the DID")
            assertNotNull((repo.findByAlias(tenantA, "alias-a") as Ok).value)
            assertNull((repo.findByAlias(tenantB, "alias-a") as Ok).value)

            // findAll scoped to tenant B returns the empty list even though tenant A has rows.
            val bResults = (repo.findAll(DidRecordFilter(tenantId = tenantB)) as Ok).value
            assertEquals(0, bResults.size, "tenant-scoped list must not leak other tenants' rows")
        }

    @Test
    fun deleteVerificationMethodCascadesStringRefRelationshipsAndKeyMappings() =
        runTest {
            before()
            val repo = repository()
            val rec = record()
            val vmId = newId()
            val vm =
                DidVerificationMethodRecord(
                    id = vmId,
                    didRecordId = rec.id,
                    vmId = "did:example:123#cascade-key",
                    type = "JsonWebKey2020",
                    controller = "did:example:123",
                    kmsProviderId = "kms",
                    kmsKeyAlias = "a-cascade",
                    ordinal = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            // Embedded relationship (FK to vm.id) AND string-ref relationship (entry_ref_did_url
            // matching vm.vmId) — the cascade must drop both flavours.
            val embeddedRel =
                DidVerificationRelationshipRecord(
                    id = newId(),
                    didRecordId = rec.id,
                    purpose = "authentication",
                    entryEmbeddedVmId = vmId,
                    entryRefDidUrl = null,
                    ordinal = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            val stringRefRel =
                DidVerificationRelationshipRecord(
                    id = newId(),
                    didRecordId = rec.id,
                    purpose = "assertionMethod",
                    entryEmbeddedVmId = null,
                    entryRefDidUrl = "did:example:123#cascade-key",
                    ordinal = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            val keyMapping =
                DidKeyMappingRecord(
                    id = newId(),
                    didRecordId = rec.id,
                    // IDK-18 o77: verificationMethodId is the local VM UUID (FK to
                    // did_verification_method.id); the wire DID URL goes in verificationMethodDidUrl.
                    verificationMethodId = vmId,
                    verificationMethodDidUrl = "did:example:123#cascade-key",
                    kmsProviderId = "kms",
                    kmsKeyAlias = "a-cascade",
                    purposesJson = """["authentication"]""",
                    createdAt = now,
                    updatedAt = now,
                )
            repo.save(
                DidDetail(
                    record = rec,
                    verificationMethod = listOf(vm),
                    verificationRelationship = listOf(embeddedRel, stringRefRel),
                    keyMapping = listOf(keyMapping),
                )
            )
            assertTrue(repo.deleteVerificationMethod(tenant, vmId) is Ok)
            val loaded = (repo.findByDid(tenant, rec.did) as Ok).value!!
            assertEquals(0, loaded.verificationMethod.size, "VM should be gone")
            assertEquals(0, loaded.verificationRelationship.size, "both relationship flavours should cascade")
            assertEquals(0, loaded.keyMapping.size, "key mappings should cascade via FK ON DELETE")
        }

    @Test
    fun verificationMethodPreservesAuthoredVmIdForm() =
        runTest {
            before()
            val repo = repository()
            val rec = record()
            val vmRelative =
                DidVerificationMethodRecord(
                    id = newId(),
                    didRecordId = rec.id,
                    vmId = "did:example:123#key-1",
                    vmIdAuthored = "#key-1",
                    type = "JsonWebKey2020",
                    controller = "did:example:123",
                    ordinal = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            val vmAbsolute =
                DidVerificationMethodRecord(
                    id = newId(),
                    didRecordId = rec.id,
                    vmId = "did:example:123#key-2",
                    vmIdAuthored = null, // authored form already absolute
                    type = "JsonWebKey2020",
                    controller = "did:example:123",
                    ordinal = 1,
                    createdAt = now,
                    updatedAt = now,
                )
            repo.save(DidDetail(record = rec, verificationMethod = listOf(vmRelative, vmAbsolute)))
            val loaded = (repo.findByDid(tenant, rec.did) as Ok).value!!
            val byId = loaded.verificationMethod.associateBy { it.vmId }
            assertEquals("#key-1", byId["did:example:123#key-1"]?.vmIdAuthored)
            assertEquals(null, byId["did:example:123#key-2"]?.vmIdAuthored)
        }

    @Test
    fun replaceContextsReplacesEntireListAtomically() =
        runTest {
            before()
            val repo = repository()
            val rec = record()
            val initialContexts =
                listOf(
                    DidDocumentContextRecord(
                        id = newId(),
                        didRecordId = rec.id,
                        contextUri = "https://www.w3.org/ns/did/v1",
                        ordinal = 0,
                        createdAt = now,
                        updatedAt = now,
                    ),
                    DidDocumentContextRecord(
                        id = newId(),
                        didRecordId = rec.id,
                        contextUri = "https://w3id.org/security/suites/ed25519-2020/v1",
                        ordinal = 1,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            repo.save(DidDetail(record = rec, context = initialContexts))
            // Replace with a different ordered list (subset + new entry).
            val newContexts =
                listOf(
                    DidDocumentContextRecord(
                        id = newId(),
                        didRecordId = rec.id,
                        contextUri = "https://www.w3.org/ns/did/v1.1",
                        ordinal = 0,
                        createdAt = now,
                        updatedAt = now,
                    ),
                    DidDocumentContextRecord(
                        id = newId(),
                        didRecordId = rec.id,
                        contextUri = "https://schema.org",
                        ordinal = 1,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            assertTrue(repo.replaceContexts(rec.id, newContexts) is Ok)
            val loaded = (repo.findByDid(tenant, rec.did) as Ok).value!!
            assertEquals(2, loaded.context.size)
            assertEquals("https://www.w3.org/ns/did/v1.1", loaded.context[0].contextUri)
            assertEquals("https://schema.org", loaded.context[1].contextUri)
            // Replace with empty — clears all.
            assertTrue(repo.replaceContexts(rec.id, emptyList()) is Ok)
            val emptied = (repo.findByDid(tenant, rec.did) as Ok).value!!
            assertEquals(0, emptied.context.size, "replaceContexts(emptyList) must clear all rows")
        }

    @Test
    fun findByAliasIsTenantScoped() =
        runTest {
            before()
            val repo = repository()
            val tenantA = "tenant-a"
            val tenantB = "tenant-b"
            val sharedAlias = "collision"
            val recA = record(tenantId = tenantA, alias = sharedAlias, did = "did:example:tenantA")
            val recB = record(tenantId = tenantB, alias = sharedAlias, did = "did:example:tenantB")
            repo.save(DidDetail(record = recA))
            repo.save(DidDetail(record = recB))

            val foundA = (repo.findByAlias(tenantA, sharedAlias) as Ok).value
            val foundB = (repo.findByAlias(tenantB, sharedAlias) as Ok).value
            assertNotNull(foundA)
            assertNotNull(foundB)
            assertEquals(recA.id, foundA.record.id, "tenant-A's alias lookup must not return tenant-B's row")
            assertEquals(recB.id, foundB.record.id, "tenant-B's alias lookup must not return tenant-A's row")
        }

    @Test
    fun relationshipInvariantRejectsBothEntryFieldsNull() =
        runTest {
            assumeInfrastructureAvailable()
            val err =
                runCatching {
                    DidVerificationRelationshipRecord(
                        id = newId(),
                        didRecordId = "x",
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
