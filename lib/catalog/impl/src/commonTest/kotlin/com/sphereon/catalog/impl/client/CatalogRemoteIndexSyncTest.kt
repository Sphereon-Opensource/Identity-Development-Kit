/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.catalog.impl.client

import com.sphereon.catalog.client.CatalogRemoteClient
import com.sphereon.catalog.model.CatalogDocument
import com.sphereon.catalog.model.CatalogDocumentKind
import com.sphereon.catalog.model.PaginatedSchemaList
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.model.SchemaUriRef
import com.sphereon.catalog.persistence.memory.InMemoryAttestationCatalogStore
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogRemoteIndexSyncTest {
    @Test
    fun walksEveryPageAndDoesNotWithdrawIdsNeverFetched() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            val remote = PagingRemote()
            val sync = CatalogRemoteIndexSync(remote)
            val source = IndexedCatalogSource("https://remote.test/api/v1", "pid", "PID")
            assertTrue(sync.sync(store, TENANT, source).isOk)
            val schemas = store.listSchemas(TENANT, "wallet-pid").value
            assertEquals(listOf("a", "b", "c"), schemas.mapNotNull { it.schema.id }.sorted())
            assertEquals(listOf(0, 2), remote.offsets)
        }

    @Test
    fun incompleteListingDoesNotWithdrawUnfetchedIds() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            val remote = PagingRemote()
            val sync = CatalogRemoteIndexSync(remote)
            val source = IndexedCatalogSource("https://remote.test/api/v1", "pid", "PID")
            assertTrue(sync.sync(store, TENANT, source).isOk)
            remote.ignoreOffset = true
            assertTrue(sync.sync(store, TENANT, source).isOk)
            val schemas = store.listSchemas(TENANT, "wallet-pid").value
            assertEquals(3, schemas.size)
            assertTrue(schemas.all { !it.listing.isEmpty() })
        }

    @Test
    fun listFailureLeavesThePreviousIndexAndReturnsErr() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            val remote = PagingRemote()
            val sync = CatalogRemoteIndexSync(remote)
            val source = IndexedCatalogSource("https://remote.test/api/v1", "pid", "PID")
            assertTrue(sync.sync(store, TENANT, source).isOk)
            remote.fail = true
            assertTrue(sync.sync(store, TENANT, source).isErr)
            val schemas = store.listSchemas(TENANT, "wallet-pid").value
            assertEquals(3, schemas.size)
            assertTrue(schemas.all { !it.listing.isEmpty() })
        }

    @Test
    fun refetchesWhenAServedTypeIsMissingItsRulebook() =
        runTest {
            val store = InMemoryAttestationCatalogStore()
            val remote = PagingRemote(omitRulebookOnce = true)
            val sync = CatalogRemoteIndexSync(remote)
            val source = IndexedCatalogSource("https://remote.test/api/v1", "pid", "PID")
            assertTrue(sync.sync(store, TENANT, source).isOk)
            val first = store.findSchema(TENANT, "wallet-pid", "a").value!!
            assertTrue(first.documents.none { it.kind == CatalogDocumentKind.RULEBOOK })
            assertTrue(sync.sync(store, TENANT, source).isOk)
            val second = store.findSchema(TENANT, "wallet-pid", "a").value!!
            assertTrue(second.documents.any { it.kind == CatalogDocumentKind.RULEBOOK })
        }

    private class PagingRemote(
        var fail: Boolean = false,
        var omitRulebookOnce: Boolean = false,
        var ignoreOffset: Boolean = false,
    ) : CatalogRemoteClient {
        val offsets = mutableListOf<Int>()
        private val all = listOf(schema("a"), schema("b"), schema("c"))

        override suspend fun listSchemas(
            baseUrl: String,
            limit: Int,
            offset: Int,
        ): IdkResult<PaginatedSchemaList, IdkError> {
            if (fail) return Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "remote down"))
            offsets += offset
            val from = if (ignoreOffset) 0 else offset
            val slice = all.drop(from).take(limit.coerceAtMost(2))
            return Ok(PaginatedSchemaList(total = all.size, limit = 2, offset = from, data = slice))
        }

        override suspend fun getSchema(
            baseUrl: String,
            schemaId: String
        ): IdkResult<SchemaMeta, IdkError> = Ok(all.first { it.id == schemaId })

        override suspend fun fetchDocument(uri: String): IdkResult<CatalogDocument, IdkError> {
            if (omitRulebookOnce && uri.endsWith("rulebook")) {
                omitRulebookOnce = false
                return Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "rulebook missing"))
            }
            return Ok(
                CatalogDocument(
                    mediaType = if (uri.endsWith("rulebook")) "text/markdown" else "application/json",
                    bytes = if (uri.endsWith("rulebook")) "# rb".encodeToByteArray() else """{"vct":"$uri"}""".encodeToByteArray(),
                ),
            )
        }

        private fun schema(id: String) =
            SchemaMeta(
                id = id,
                version = "1.0.0",
                rulebookURI = "https://remote.test/$id/rulebook",
                attestationLoS = "iso_18045_high",
                bindingType = "key",
                supportedFormats = listOf("dc+sd-jwt"),
                schemaURIs = listOf(SchemaUriRef("dc+sd-jwt", "https://remote.test/$id/vct")),
            )
    }

    private companion object {
        const val TENANT = "acme"
    }
}
