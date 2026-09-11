/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.catalog.impl

import com.sphereon.catalog.model.AttestationCatalog
import com.sphereon.catalog.model.AttestationCatalogStatus
import com.sphereon.catalog.model.AttestationSchemaRecord
import com.sphereon.catalog.model.CatalogSchemaProvenance
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.model.SchemaUriRef
import com.sphereon.catalog.persistence.memory.InMemoryAttestationCatalogStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock

class InMemoryAttestationCatalogStoreTest {
    @Test
    fun isolatesTenantsAndLooksUpBySlug() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            val catalog =
                AttestationCatalog(
                    id = "cat-1",
                    slug = "webuild-pid",
                    displayName = "WE BUILD",
                    status = AttestationCatalogStatus.PUBLISHED,
                    verificationEnabled = true,
                    createdAt = Clock.System.now(),
                )
            store.saveCatalog("acme", catalog)
            store.saveCatalog("other", catalog.copy(id = "cat-2", slug = "other-cat"))
            assertEquals("cat-1", store.findCatalogBySlug("acme", "webuild-pid").value?.id)
            assertNull(store.findCatalogBySlug("other", "webuild-pid").value)
        }

    @Test
    fun listsOnlyPublishedVerificationSchemas() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            val published =
                AttestationCatalog(
                    id = "cat-pub",
                    slug = "pub",
                    displayName = "Pub",
                    status = AttestationCatalogStatus.PUBLISHED,
                    verificationEnabled = true,
                )
            val draft =
                published.copy(id = "cat-draft", slug = "draft", status = AttestationCatalogStatus.DRAFT)
            store.saveCatalog("t", published)
            store.saveCatalog("t", draft)
            val schema =
                SchemaMeta(
                    id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    version = "1.0.0",
                    rulebookURI = "https://example.test/rb",
                    attestationLoS = "iso_18045_high",
                    bindingType = "key",
                    supportedFormats = listOf("dc+sd-jwt"),
                    schemaURIs = listOf(SchemaUriRef("dc+sd-jwt", "https://example.test/vct")),
                )
            store.saveSchema(
                "t",
                AttestationSchemaRecord(
                    catalogId = "cat-pub",
                    schema = schema,
                    provenance = CatalogSchemaProvenance.AUTHORED,
                ),
            )
            store.saveSchema(
                "t",
                AttestationSchemaRecord(
                    catalogId = "cat-draft",
                    schema = schema.copy(id = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                    provenance = CatalogSchemaProvenance.AUTHORED,
                ),
            )
            val listed = store.listPublishedVerificationSchemas("t").value
            assertEquals(1, listed?.size)
            assertEquals("cat-pub", listed?.single()?.catalogId)
        }
}
