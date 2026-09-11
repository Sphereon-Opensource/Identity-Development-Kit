/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.catalog.persistence.sqlite

import com.sphereon.catalog.model.AttestationCatalog
import com.sphereon.catalog.model.AttestationCatalogStatus
import com.sphereon.catalog.model.AttestationSchemaDocument
import com.sphereon.catalog.model.AttestationSchemaRecord
import com.sphereon.catalog.model.CatalogDocumentKind
import com.sphereon.catalog.model.CatalogSchemaProvenance
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.model.SchemaUriRef
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class SqliteCatalogStoreTest {
    private fun newStore(): SqliteCatalogStore =
        SqliteCatalogDatabases.createStore(
            "jdbc:sqlite:file:catalog_test_${Uuid.random()}?mode=memory&cache=shared",
        )

    private fun publishedCatalog(
        id: String = "cat-1",
        slug: String = "webuild-pid",
    ) = AttestationCatalog(
        id = id,
        slug = slug,
        displayName = "WE BUILD",
        status = AttestationCatalogStatus.PUBLISHED,
        verificationEnabled = true,
        createdAt = Clock.System.now(),
    )

    private fun schemaMeta(id: String) =
        SchemaMeta(
            id = id,
            version = "1.0.0",
            rulebookURI = "https://example.test/rb",
            attestationLoS = "iso_18045_high",
            bindingType = "key",
            supportedFormats = listOf("dc+sd-jwt"),
            schemaURIs = listOf(SchemaUriRef("dc+sd-jwt", "https://example.test/vct")),
        )

    @Test
    fun migratesBooleanListedColumnToListingWindow() =
        runTest {
            val driver =
                SqliteCatalogDatabases.jdbcDriver(
                    "jdbc:sqlite:file:catalog_listed_migrate_${Uuid.random()}?mode=memory&cache=shared",
                )
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
            driver.execute(
                null,
                """
                CREATE TABLE attestation_schema (
                    tenant_id TEXT NOT NULL,
                    catalog_id TEXT NOT NULL,
                    schema_id TEXT NOT NULL,
                    schema_json TEXT NOT NULL,
                    provenance TEXT NOT NULL,
                    listed INTEGER NOT NULL,
                    linked_design_id TEXT,
                    linked_vct_id TEXT,
                    created_at TEXT,
                    updated_at TEXT,
                    PRIMARY KEY (tenant_id, catalog_id, schema_id)
                )
                """.trimIndent(),
                0,
            )
            val schemaJson =
                """
                {"id":"schema-old","version":"1.0.0","rulebookURI":"https://example.test/rb","attestationLoS":"iso_18045_high","bindingType":"key","supportedFormats":["dc+sd-jwt"],"schemaURIs":[{"formatIdentifier":"dc+sd-jwt","uri":"https://example.test/vct"}]}
                """.trimIndent()
            driver.execute(
                null,
                "INSERT INTO attestation_schema VALUES ('acme','cat-1','schema-old', ?, 'AUTHORED', 1, NULL, NULL, NULL, NULL)",
                1,
            ) {
                bindString(0, schemaJson)
            }
            val store = SqliteCatalogDatabases.createStore(driver)
            store.saveCatalog("acme", publishedCatalog())
            val stored = store.findSchema("acme", "cat-1", "schema-old").value
            assertEquals("schema-old", stored?.schema?.id)
            assertTrue(stored!!.listing.includes(Clock.System.now()))
            assertEquals(null, stored.listing.end)
        }

    @Test
    fun isolatesTenantsAndLooksUpBySlug() =
        runTest {
            val store = newStore()
            val catalog = publishedCatalog()
            assertTrue(store.saveCatalog("acme", catalog).isOk)
            assertTrue(store.saveCatalog("other", catalog.copy(id = "cat-2", slug = "other-cat")).isOk)
            assertEquals("cat-1", store.findCatalogBySlug("acme", "webuild-pid").value?.id)
            assertNull(store.findCatalogBySlug("other", "webuild-pid").value)
            assertEquals("cat-1", store.findCatalogById("acme", "cat-1").value?.id)
        }

    @Test
    fun fileBackedStoreSurvivesReopen() =
        runTest {
            val file =
                kotlin.io.path
                    .createTempFile(prefix = "catalog-persist-", suffix = ".db")
                    .toFile()
            val jdbc = "jdbc:sqlite:${file.invariantSeparatorsPath}"
            try {
                val first = SqliteCatalogDatabases.createStore(jdbc)
                val catalog = publishedCatalog(id = "cat-persist", slug = "persist-pid")
                assertTrue(first.saveCatalog("acme", catalog).isOk)
                val second = SqliteCatalogDatabases.createStore(jdbc)
                assertEquals("cat-persist", second.findCatalogBySlug("acme", "persist-pid").value?.id)
            } finally {
                file.delete()
            }
        }

    @Test
    fun listsOnlyPublishedVerificationSchemas() =
        runTest {
            val store = newStore()
            val published = publishedCatalog(id = "cat-pub", slug = "pub")
            val draft = published.copy(id = "cat-draft", slug = "draft", status = AttestationCatalogStatus.DRAFT)
            store.saveCatalog("t", published)
            store.saveCatalog("t", draft)
            store.saveSchema(
                "t",
                AttestationSchemaRecord(
                    catalogId = "cat-pub",
                    schema = schemaMeta("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                    provenance = CatalogSchemaProvenance.AUTHORED,
                ),
            )
            store.saveSchema(
                "t",
                AttestationSchemaRecord(
                    catalogId = "cat-draft",
                    schema = schemaMeta("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                    provenance = CatalogSchemaProvenance.AUTHORED,
                ),
            )
            val listed = store.listPublishedVerificationSchemas("t").value
            assertEquals(1, listed.size)
            assertEquals("cat-pub", listed.single().catalogId)
        }

    @Test
    fun persistsDocumentsAndDeletesSchema() =
        runTest {
            val store = newStore()
            store.saveCatalog("t", publishedCatalog(id = "cat-doc", slug = "docs"))
            val record =
                AttestationSchemaRecord(
                    catalogId = "cat-doc",
                    schema = schemaMeta("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                    provenance = CatalogSchemaProvenance.AUTHORED,
                    linkedDesignId = "design-1",
                    linkedVctId = "vct-1",
                    documents =
                        listOf(
                            AttestationSchemaDocument(
                                kind = CatalogDocumentKind.RULEBOOK,
                                mediaType = "text/markdown",
                                bytes = "# rulebook".encodeToByteArray(),
                            ),
                            AttestationSchemaDocument(
                                kind = CatalogDocumentKind.FORMAT,
                                formatIdentifier = "dc+sd-jwt",
                                mediaType = "application/json",
                                bytes = """{"vct":"x"}""".encodeToByteArray(),
                                integrity = "sha256-abc",
                            ),
                        ),
                )
            assertTrue(store.saveSchema("t", record).isOk)
            val found = store.findSchema("t", "cat-doc", "cccccccc-cccc-cccc-cccc-cccccccccccc").value
            assertEquals(2, found?.documents?.size)
            assertEquals("design-1", found?.linkedDesignId)
            assertEquals("vct-1", found?.linkedVctId)
            assertEquals("text/markdown", found?.documents?.single { it.kind == CatalogDocumentKind.RULEBOOK }?.mediaType)
            assertTrue(store.deleteSchema("t", "cat-doc", "cccccccc-cccc-cccc-cccc-cccccccccccc").value == true)
            assertNull(store.findSchema("t", "cat-doc", "cccccccc-cccc-cccc-cccc-cccccccccccc").value)
            assertTrue(
                store
                    .listSchemas("t", "cat-doc")
                    .value
                    .orEmpty()
                    .isEmpty()
            )
        }

    @Test
    fun filtersCatalogListByStatusAndVerification() =
        runTest {
            val store = newStore()
            store.saveCatalog("t", publishedCatalog(id = "a", slug = "a"))
            store.saveCatalog(
                "t",
                publishedCatalog(id = "b", slug = "b").copy(
                    status = AttestationCatalogStatus.DISABLED,
                    verificationEnabled = false,
                ),
            )
            assertEquals(1, store.listCatalogs("t", status = AttestationCatalogStatus.PUBLISHED).value.size)
            assertEquals(1, store.listCatalogs("t", verificationEnabled = false).value.size)
            assertEquals(2, store.listCatalogs("t").value.size)
        }
}
