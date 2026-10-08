/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl.client

import com.sphereon.catalog.client.CatalogRemoteClient
import com.sphereon.catalog.model.CatalogDocument
import com.sphereon.catalog.model.PaginatedSchemaList
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class UnimplementedCatalogRemoteClient : CatalogRemoteClient {
    override suspend fun listSchemas(
        baseUrl: String,
        limit: Int,
        offset: Int,
    ): IdkResult<PaginatedSchemaList, IdkError> = Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "Remote TS 11 client is not bound"))

    override suspend fun getSchema(
        baseUrl: String,
        schemaId: String
    ): IdkResult<SchemaMeta, IdkError> = Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "Remote TS 11 client is not bound"))

    override suspend fun fetchDocument(uri: String): IdkResult<CatalogDocument, IdkError> = Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "Remote TS 11 client is not bound"))
}
