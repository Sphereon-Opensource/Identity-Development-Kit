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
import com.sphereon.core.api.Err
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

    /**
     * Domain-scoped listing fetch: signed bodies must verify against the trust domain's
     * CATALOG_SIGNER anchors, unsigned bodies only when the domain policy allows them. Clients that
     * cannot verify fail closed.
     */
    suspend fun listSchemasScoped(
        baseUrl: String,
        limit: Int,
        offset: Int,
        trust: CatalogRemoteTrustScope,
    ): IdkResult<VerifiedRemoteBody<PaginatedSchemaList>, IdkError> =
        Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "Remote catalog client cannot verify signatures for a trust domain"))

    suspend fun getSchemaScoped(
        baseUrl: String,
        schemaId: String,
        trust: CatalogRemoteTrustScope,
    ): IdkResult<VerifiedRemoteBody<SchemaMeta>, IdkError> =
        Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "Remote catalog client cannot verify signatures for a trust domain"))

    suspend fun fetchDocument(uri: String): IdkResult<CatalogDocument, IdkError>
}
