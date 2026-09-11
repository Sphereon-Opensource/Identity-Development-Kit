/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.client

import com.sphereon.catalog.model.CatalogDocument
import com.sphereon.catalog.model.PaginatedSchemaList
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

interface CatalogRemoteClient {
    suspend fun listSchemas(
        baseUrl: String,
        limit: Int = 100,
        offset: Int = 0,
    ): IdkResult<PaginatedSchemaList, IdkError>

    suspend fun getSchema(
        baseUrl: String,
        schemaId: String
    ): IdkResult<SchemaMeta, IdkError>

    suspend fun fetchDocument(uri: String): IdkResult<CatalogDocument, IdkError>
}
