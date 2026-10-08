/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.store

import com.sphereon.catalog.model.AttestationCatalog
import com.sphereon.catalog.model.AttestationCatalogStatus
import com.sphereon.catalog.model.AttestationSchemaRecord
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

interface AttestationCatalogStore {
    suspend fun saveCatalog(
        tenantId: String,
        catalog: AttestationCatalog
    ): IdkResult<AttestationCatalog, IdkError>

    suspend fun findCatalogById(
        tenantId: String,
        catalogId: String
    ): IdkResult<AttestationCatalog?, IdkError>

    suspend fun findCatalogBySlug(
        tenantId: String,
        slug: String
    ): IdkResult<AttestationCatalog?, IdkError>

    suspend fun listCatalogs(
        tenantId: String,
        status: AttestationCatalogStatus? = null,
        verificationEnabled: Boolean? = null,
    ): IdkResult<List<AttestationCatalog>, IdkError>

    suspend fun saveSchema(
        tenantId: String,
        record: AttestationSchemaRecord
    ): IdkResult<AttestationSchemaRecord, IdkError>

    suspend fun findSchema(
        tenantId: String,
        catalogId: String,
        schemaId: String,
    ): IdkResult<AttestationSchemaRecord?, IdkError>

    suspend fun listSchemas(
        tenantId: String,
        catalogId: String,
    ): IdkResult<List<AttestationSchemaRecord>, IdkError>

    suspend fun deleteSchema(
        tenantId: String,
        catalogId: String,
        schemaId: String,
    ): IdkResult<Boolean, IdkError>

    suspend fun listPublishedVerificationSchemas(tenantId: String): IdkResult<List<AttestationSchemaRecord>, IdkError>
}
