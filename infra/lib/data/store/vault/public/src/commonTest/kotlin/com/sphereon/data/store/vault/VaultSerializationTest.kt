/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.vault

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class VaultSerializationTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    @Test
    fun `descriptor round trips with kind authority protection policy and revision`() {
        val descriptor = descriptor()

        val decoded = json.decodeFromString<VaultDescriptor>(json.encodeToString(descriptor))

        assertEquals(descriptor, decoded)
        assertEquals(VaultKind.WALLET, decoded.kind)
        assertEquals(VaultProtectionProfile.OWNER_CONTROLLED_ZERO_ACCESS, decoded.protection.profile)
    }

    @Test
    fun `file request round trips bytes metadata and compare-and-set conditions`() {
        val request =
            PutVaultFileRequest(
                vaultId = VaultId("vault-1"),
                path = VaultPath("/credentials/credential-1.cbor"),
                bytes = byteArrayOf(0, 1, 2, -1),
                metadata =
                    VaultMetadata(
                        contentType = "application/cbor",
                        tags = mapOf("credentialType" to "org.iso.18013.5.1.mDL"),
                    ),
                condition =
                    VaultMutationCondition(
                        existence = VaultExistenceCondition.MUST_NOT_EXIST,
                        expectedVaultRevision = VaultRevision("vault-r7"),
                    ),
            )

        val decoded = json.decodeFromString<PutVaultFileRequest>(json.encodeToString(request))

        assertEquals(request, decoded)
        assertContentEquals(request.bytes, decoded.bytes)
        assertEquals(VaultExistenceCondition.MUST_NOT_EXIST, decoded.condition.existence)
    }

    @Test
    fun `operation and idempotency identities are portable`() {
        val context =
            VaultMutationContext(
                operation =
                    VaultOperationContext(
                        operationId = VaultOperationId("operation-123"),
                        actorRef = "did:example:alice",
                        purpose = "credential-storage",
                    ),
                idempotencyKey = VaultIdempotencyKey("request:mobile:42"),
            )

        assertEquals(context, json.decodeFromString<VaultMutationContext>(json.encodeToString(context)))
    }

    @Test
    fun `object identity remains independent from path binding`() {
        val objectId = VaultObjectId("object-1")
        val before = pathEntry(objectId, "/inbox/item", "path-r1")
        val after = pathEntry(objectId, "/archive/item", "path-r2")

        assertEquals(before.objectId, after.objectId)
        assertEquals(VaultPath("/inbox/item"), before.path)
        assertEquals(VaultPath("/archive/item"), after.path)
    }

    @Test
    fun `portability contract fixes object and hierarchy formats`() {
        val objectManifest =
            exportManifest(
                scope = VaultExportScope.OBJECT,
                format = VaultExportFormat.TDF,
            )
        val vaultManifest =
            exportManifest(
                scope = VaultExportScope.VAULT,
                format = VaultExportFormat.BAGIT_TDF,
            )

        assertEquals(objectManifest, json.decodeFromString(json.encodeToString(objectManifest)))
        assertEquals(vaultManifest, json.decodeFromString(json.encodeToString(vaultManifest)))
        assertFailsWith<IllegalArgumentException> {
            exportManifest(VaultExportScope.VAULT, VaultExportFormat.TDF)
        }
    }

    @Test
    fun `grant search and import contracts round trip without platform types`() {
        val grant =
            VaultGrant(
                grantId = "grant-1",
                vaultId = VaultId("vault-1"),
                principalRef = "did:example:operator",
                effect = VaultGrantEffect.ALLOW,
                actions = setOf(VaultAction.READ, VaultAction.SEARCH),
                scope = VaultGrantScope(pathPrefix = VaultPath("/credentials")),
                purposes = setOf("presentation"),
                revision = VaultRevision("grant-r1"),
            )
        val search =
            VaultSearchQuery(
                vaultId = VaultId("vault-1"),
                pathPrefix = VaultPath("/credentials"),
                objectKinds = setOf(VaultObjectKind.FILE),
                tags = mapOf("credentialType" to "eu.europa.ec.eudi.pid.1"),
            )
        val imported =
            VaultImportManifest(
                importId = "import-1",
                targetVaultId = VaultId("vault-1"),
                importedAt = Instant.parse("2026-07-10T10:00:00Z"),
                sourceDigest = VaultDigest("sha-256", "package-digest"),
                importedObjects =
                    listOf(
                        VaultImportedObject(
                            objectId = VaultObjectId("credential-1"),
                            path = VaultPath("/credentials/credential-1"),
                            keyDisposition = VaultImportKeyDisposition.REISSUANCE_REQUIRED,
                        ),
                    ),
            )

        assertEquals(grant, json.decodeFromString<VaultGrant>(json.encodeToString(grant)))
        assertEquals(search, json.decodeFromString<VaultSearchQuery>(json.encodeToString(search)))
        assertEquals(imported, json.decodeFromString<VaultImportManifest>(json.encodeToString(imported)))
    }

    private fun descriptor() =
        VaultDescriptor(
            vaultId = VaultId("vault-1"),
            kind = VaultKind.WALLET,
            authority = VaultAuthority(VaultAuthorityLocation.CLIENT_DEVICE, "device:primary"),
            protection =
                VaultProtectionDescriptor(
                    profile = VaultProtectionProfile.OWNER_CONTROLLED_ZERO_ACCESS,
                    providerRef = "crypto:client",
                    keyReference = "key-ref:storage",
                ),
            repositoryRef = "repository:local",
            providerRef = "provider:sqlite",
            policyRef = VaultPolicyRef("policy:owner-only"),
            revision = VaultRevision("vault-r1"),
            lifecycleState = VaultLifecycleState.ACTIVE,
            createdAt = Instant.parse("2026-07-10T10:00:00Z"),
            updatedAt = Instant.parse("2026-07-10T10:01:00Z"),
        )

    private fun pathEntry(
        objectId: VaultObjectId,
        path: String,
        revision: String,
    ) = VaultPathEntry(
        vaultId = VaultId("vault-1"),
        path = VaultPath(path),
        objectId = objectId,
        kind = VaultObjectKind.FILE,
        revision = VaultRevision(revision),
    )

    private fun exportManifest(
        scope: VaultExportScope,
        format: VaultExportFormat,
    ) = VaultExportManifest(
        exportId = "export-1",
        vaultId = VaultId("vault-1"),
        scope = scope,
        format = format,
        createdAt = Instant.parse("2026-07-10T10:00:00Z"),
        recipientRef = "did:example:bob",
        objectCount = 1,
        contentDigest = VaultDigest("sha-256", "content-digest"),
        manifestDigest = VaultDigest("sha-256", "manifest-digest"),
    )
}
